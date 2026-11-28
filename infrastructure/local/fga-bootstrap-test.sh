#!/usr/bin/env bash
set -euo pipefail

directory=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cases=(fresh reuse version_upgrade reset_db recover_state gradle_offline checksum_mismatch concurrency)
if [[ $# == 0 ]]; then
  failed=0
  for name in "${cases[@]}"; do
    if bash "$0" "$name"; then
      printf 'PASS %s\n' "$name"
    else
      printf 'FAIL %s\n' "$name" >&2
      failed=1
    fi
  done
  exit "$failed"
fi

work=$(mktemp -d)
server_pid=""
cleanup() {
  if [[ -n "$server_pid" ]]; then
    kill "$server_pid" 2>/dev/null || true
    wait "$server_pid" 2>/dev/null || true
  fi
  rm -rf "$work"
}
trap cleanup EXIT

export DEVENV_STATE="$work/state"
export DEVENV_ROOT="$work/root"
mkdir -p "$DEVENV_ROOT/gradle" \
  "$work/maven/dev/penvire/lab/fga-model/0.1.0" \
  "$work/maven/dev/penvire/lab/fga-model/0.2.0"

model_v1='{"schema_version":"1.2","type_definitions":[{"type":"user"},{"type":"platform"}]}'
model_v2='{"schema_version":"1.2","type_definitions":[{"type":"user"},{"type":"client"},{"type":"platform"}]}'
printf '%s' "$model_v1" > "$work/maven/dev/penvire/lab/fga-model/0.1.0/fga-model-0.1.0.json"
sha256sum "$work/maven/dev/penvire/lab/fga-model/0.1.0/fga-model-0.1.0.json" | cut -d' ' -f1 \
  > "$work/maven/dev/penvire/lab/fga-model/0.1.0/fga-model-0.1.0.json.sha256"
printf '%s' "$model_v2" > "$work/maven/dev/penvire/lab/fga-model/0.2.0/fga-model-0.2.0.json"
sha256sum "$work/maven/dev/penvire/lab/fga-model/0.2.0/fga-model-0.2.0.json" | cut -d' ' -f1 \
  > "$work/maven/dev/penvire/lab/fga-model/0.2.0/fga-model-0.2.0.json.sha256"

cat > "$DEVENV_ROOT/gradle/libs.versions.toml" <<'TOML'
[versions]
fga-model = "0.1.0"
TOML

cat > "$work/MockServer.java" <<'JAVA'
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MockServer {
  private static final Pattern NAME_FIELD = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

  private static final List<Map<String, String>> STORES = new ArrayList<>();
  private static final Map<String, List<String>> MODELS = new LinkedHashMap<>();
  private static int storeSeq = 0;
  private static int modelSeq = 0;
  private static int mavenHits = 0;

  public static void main(String[] args) throws Exception {
    Path mavenRoot = Path.of(args[0]).toAbsolutePath().normalize();
    Path portFile = Path.of(args[1]);

    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    server.createContext("/", exchange -> handle(exchange, mavenRoot));
    server.start();
    Files.writeString(portFile, Integer.toString(server.getAddress().getPort()));
  }

  private static void handle(HttpExchange exchange, Path mavenRoot) throws IOException {
    String method = exchange.getRequestMethod();
    String path = exchange.getRequestURI().getPath();
    byte[] body = exchange.getRequestBody().readAllBytes();

    if ("GET".equals(method) && "/healthz".equals(path)) {
      sendJson(exchange, 200, "{\"status\":\"SERVING\"}");
      return;
    }

    if ("GET".equals(method) && "/_stats".equals(path)) {
      synchronized (STORES) {
        StringBuilder modelCounts = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, List<String>> entry : MODELS.entrySet()) {
          if (!first) modelCounts.append(',');
          first = false;
          modelCounts.append('"').append(entry.getKey()).append("\":").append(entry.getValue().size());
        }
        modelCounts.append('}');
        sendJson(
            exchange,
            200,
            "{\"stores\":"
                + STORES.size()
                + ",\"models\":"
                + modelCounts
                + ",\"maven_hits\":"
                + mavenHits
                + "}");
      }
      return;
    }

    if ("POST".equals(method) && "/_reset".equals(path)) {
      synchronized (STORES) {
        STORES.clear();
        MODELS.clear();
      }
      sendJson(exchange, 200, "{\"reset\":true}");
      return;
    }

    if ("GET".equals(method) && path.startsWith("/maven/")) {
      Path target = mavenRoot.resolve(path.substring("/maven/".length())).normalize();
      if (!target.startsWith(mavenRoot) || !Files.isRegularFile(target)) {
        sendBytes(exchange, 404, "text/plain", new byte[0]);
        return;
      }
      synchronized (STORES) {
        mavenHits++;
      }
      sendBytes(exchange, 200, "application/octet-stream", Files.readAllBytes(target));
      return;
    }

    if ("GET".equals(method) && "/stores".equals(path)) {
      synchronized (STORES) {
        StringBuilder items = new StringBuilder("[");
        for (int i = 0; i < STORES.size(); i++) {
          if (i > 0) items.append(',');
          Map<String, String> store = STORES.get(i);
          items
              .append("{\"id\":\"")
              .append(store.get("id"))
              .append("\",\"name\":\"")
              .append(store.get("name"))
              .append("\"}");
        }
        items.append(']');
        sendJson(exchange, 200, "{\"stores\":" + items + ",\"continuation_token\":\"\"}");
      }
      return;
    }

    if ("POST".equals(method) && "/stores".equals(path)) {
      Matcher matcher = NAME_FIELD.matcher(new String(body, StandardCharsets.UTF_8));
      String name = matcher.find() ? matcher.group(1) : "";
      synchronized (STORES) {
        storeSeq++;
        String id = String.format("01STORE%019d", storeSeq);
        STORES.add(Map.of("id", id, "name", name));
        MODELS.put(id, new ArrayList<>());
        sendJson(exchange, 201, "{\"id\":\"" + id + "\",\"name\":\"" + name + "\"}");
      }
      return;
    }

    String[] parts = path.replaceAll("^/+|/+$", "").split("/");
    if (parts.length == 2 && "stores".equals(parts[0]) && "GET".equals(method)) {
      synchronized (STORES) {
        for (Map<String, String> store : STORES) {
          if (store.get("id").equals(parts[1])) {
            sendJson(
                exchange,
                200,
                "{\"id\":\"" + store.get("id") + "\",\"name\":\"" + store.get("name") + "\"}");
            return;
          }
        }
      }
      sendJson(exchange, 404, "{\"code\":\"store_id_not_found\"}");
      return;
    }

    if (parts.length == 3
        && "stores".equals(parts[0])
        && "authorization-models".equals(parts[2])) {
      String storeId = parts[1];
      if ("GET".equals(method)) {
        synchronized (STORES) {
          List<String> ids = MODELS.getOrDefault(storeId, List.of());
          StringBuilder arr = new StringBuilder("[");
          for (int i = ids.size() - 1; i >= 0; i--) {
            if (i < ids.size() - 1) arr.append(',');
            arr.append("{\"id\":\"").append(ids.get(i)).append("\"}");
          }
          arr.append(']');
          sendJson(exchange, 200, "{\"authorization_models\":" + arr + "}");
        }
        return;
      }
      if ("POST".equals(method)) {
        synchronized (STORES) {
          List<String> ids = MODELS.get(storeId);
          if (ids == null) {
            sendJson(exchange, 404, "{\"code\":\"store_id_not_found\"}");
            return;
          }
          modelSeq++;
          String modelId = String.format("01MODEL%019d", modelSeq);
          ids.add(modelId);
          sendJson(exchange, 201, "{\"authorization_model_id\":\"" + modelId + "\"}");
        }
        return;
      }
    }

    sendBytes(exchange, 404, "text/plain", new byte[0]);
  }

  private static void sendJson(HttpExchange exchange, int code, String json) throws IOException {
    sendBytes(exchange, code, "application/json", json.getBytes(StandardCharsets.UTF_8));
  }

  private static void sendBytes(
      HttpExchange exchange, int code, String contentType, byte[] data) throws IOException {
    exchange.getResponseHeaders().set("Content-Type", contentType);
    exchange.sendResponseHeaders(code, data.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(data);
    }
  }
}
JAVA

java "$work/MockServer.java" "$work/maven" "$work/port" &
server_pid=$!
for _ in $(seq 1 100); do
  [[ -s "$work/port" ]] && break
  sleep 0.05
done
port=$(cat "$work/port")

export FGA_API_URL="http://127.0.0.1:$port"
export PENVIRE_MAVEN_URL="http://127.0.0.1:$port/maven"
script="$directory/fga-bootstrap.sh"

run() {
  if bash -euo pipefail "$script" > "$work/output" 2>&1; then
    return 0
  else
    status=$?
    cat "$work/output" >&2
    return "$status"
  fi
}

stats() {
  curl --silent --fail --noproxy '*' "$FGA_API_URL/_stats"
}

case "$1" in
  fresh)
    run
    [[ -s "$DEVENV_STATE/openfga/store-id" ]]
    [[ -s "$DEVENV_STATE/openfga/model-id" ]]
    [[ -s "$DEVENV_STATE/openfga/env" ]]
    [[ $(stat -c %a "$DEVENV_STATE/openfga") == 700 ]]
    # shellcheck disable=SC1091
    source "$DEVENV_STATE/openfga/env"
    [[ "$FGA_STORE_ID" == "$(cat "$DEVENV_STATE/openfga/store-id")" ]]
    [[ "$FGA_MODEL_ID" == "$(cat "$DEVENV_STATE/openfga/model-id")" ]]
    [[ $(stats | jq -r '.stores') == 1 ]]
    [[ $(stats | jq -r --arg s "$FGA_STORE_ID" '.models[$s]') == 1 ]]
    ;;
  reuse)
    run
    first_store=$(cat "$DEVENV_STATE/openfga/store-id")
    first_model=$(cat "$DEVENV_STATE/openfga/model-id")
    hits_before=$(stats | jq -r '.maven_hits')
    run
    [[ "$(cat "$DEVENV_STATE/openfga/store-id")" == "$first_store" ]]
    [[ "$(cat "$DEVENV_STATE/openfga/model-id")" == "$first_model" ]]
    [[ "$(stats | jq -r --arg s "$first_store" '.models[$s]')" == 1 ]]
    [[ "$(stats | jq -r '.maven_hits')" == "$hits_before" ]]
    ;;
  version_upgrade)
    run
    first_store=$(cat "$DEVENV_STATE/openfga/store-id")
    first_model=$(cat "$DEVENV_STATE/openfga/model-id")
    cat > "$DEVENV_ROOT/gradle/libs.versions.toml" <<'TOML'
[versions]
fga-model = "0.2.0"
TOML
    run
    [[ "$(cat "$DEVENV_STATE/openfga/store-id")" == "$first_store" ]]
    [[ "$(cat "$DEVENV_STATE/openfga/model-id")" != "$first_model" ]]
    [[ "$(stats | jq -r --arg s "$first_store" '.models[$s]')" == 2 ]]
    ;;
  reset_db)
    run
    first_store=$(cat "$DEVENV_STATE/openfga/store-id")
    curl --silent --fail --noproxy '*' -X POST "$FGA_API_URL/_reset" > /dev/null
    run
    new_store=$(cat "$DEVENV_STATE/openfga/store-id")
    [[ "$new_store" != "$first_store" ]]
    [[ "$(stats | jq -r '.stores')" == 1 ]]
    [[ "$(stats | jq -r --arg s "$new_store" '.models[$s]')" == 1 ]]
    ;;
  recover_state)
    run
    first_store=$(cat "$DEVENV_STATE/openfga/store-id")
    rm -f "$DEVENV_STATE/openfga/state.json" "$DEVENV_STATE/openfga/store-id" "$DEVENV_STATE/openfga/model-id" "$DEVENV_STATE/openfga/env"
    run
    [[ "$(cat "$DEVENV_STATE/openfga/store-id")" == "$first_store" ]]
    [[ "$(stats | jq -r '.stores')" == 1 ]]
    ;;
  gradle_offline)
    mkdir -p "$DEVENV_ROOT/libs/fga/build/generated/fga-model/fga"
    printf '%s' "$model_v1" > "$DEVENV_ROOT/libs/fga/build/generated/fga-model/fga/model.json"
    printf 'version=0.1.0\n' > "$DEVENV_ROOT/libs/fga/build/generated/fga-model/fga/model.properties"
    export PENVIRE_MAVEN_URL="http://127.0.0.1:1/unreachable"
    run
    [[ "$(stats | jq -r '.stores')" == 1 ]]
    [[ "$(stats | jq -r '.maven_hits')" == 0 ]]
    ;;
  checksum_mismatch)
    printf '%s\n' "0000000000000000000000000000000000000000000000000000000000000000" \
      > "$work/maven/dev/penvire/lab/fga-model/0.1.0/fga-model-0.1.0.json.sha256"
    if run; then
      printf 'Checksum mismatch must fail\n' >&2
      exit 1
    fi
    [[ ! -e "$DEVENV_STATE/openfga/models/fga-model-0.1.0.json" ]]
    [[ "$(stats | jq -r '.stores')" == 0 ]]
    ;;
  concurrency)
    bash -euo pipefail "$script" > "$work/first.log" 2>&1 &
    first=$!
    bash -euo pipefail "$script" > "$work/second.log" 2>&1 &
    second=$!
    wait "$first"
    wait "$second"
    store_id=$(cat "$DEVENV_STATE/openfga/store-id")
    [[ "$(stats | jq -r '.stores')" == 1 ]]
    [[ "$(stats | jq -r --arg s "$store_id" '.models[$s]')" == 1 ]]
    ;;
  *) printf 'Unknown test: %s\n' "$1" >&2; exit 2 ;;
esac
