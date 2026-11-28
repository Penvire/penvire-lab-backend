# Wrapped by writeShellApplication, which enables strict Bash error handling.
umask 077
: "${DEVENV_STATE:?Run inside devenv}"
: "${DEVENV_ROOT:?Run inside devenv}"

api_url="${FGA_API_URL:-http://127.0.0.1:8090}"
api_url="${api_url%/}"
store_name="${PENVIRE_FGA_STORE_NAME:-penvire-lab}"
maven_url="${PENVIRE_MAVEN_URL:-https://git.qf0xb.de/api/packages/penvire/maven}"
maven_url="${maven_url%/}"

state_dir="$DEVENV_STATE/openfga"
models_dir="$state_dir/models"
mkdir -p "$models_dir"
chmod 700 "$state_dir" "$models_dir"

# Serialize concurrent bootstrap invocations.
exec 9> "$state_dir/.bootstrap.lock"
flock 9

work=$(mktemp -d "$state_dir/.bootstrap.XXXXXX")
trap 'rm -rf "$work"' EXIT

versions_file="$DEVENV_ROOT/gradle/libs.versions.toml"
if [[ ! -r "$versions_file" ]]; then
  printf 'Missing version catalog: %s\n' "$versions_file" >&2
  exit 1
fi

model_version=$(
  sed -nE 's/^[[:space:]]*fga-model[[:space:]]*=[[:space:]]*"([^"]+)"[[:space:]]*(#.*)?$/\1/p' "$versions_file" \
    | head -n 1
)
if [[ -z "$model_version" || ! "$model_version" =~ ^[0-9A-Za-z._+-]+$ || "$model_version" == *".."* ]]; then
  printf 'Invalid or missing fga-model version in %s\n' "$versions_file" >&2
  exit 1
fi

valid_model_json() {
  jq -e 'type == "object" and (.schema_version | type == "string") and (.type_definitions | type == "array")' "$1" > /dev/null 2>&1
}

resolve_model() {
  local cached="$models_dir/fga-model-$model_version.json"
  if [[ -s "$cached" ]] && valid_model_json "$cached"; then
    printf '%s\n' "$cached"
    return
  fi

  local gradle_model="$DEVENV_ROOT/libs/fga/build/generated/fga-model/fga/model.json"
  local gradle_meta="$DEVENV_ROOT/libs/fga/build/generated/fga-model/fga/model.properties"
  if [[ -s "$gradle_model" && -s "$gradle_meta" ]]; then
    local gradle_version
    gradle_version=$(sed -nE 's/^[[:space:]]*version=([^[:space:]]+)[[:space:]]*$/\1/p' "$gradle_meta" | head -n 1)
    if [[ "$gradle_version" == "$model_version" ]] && valid_model_json "$gradle_model"; then
      cp "$gradle_model" "$work/model.json"
      mv "$work/model.json" "$cached"
      printf '%s\n' "$cached"
      return
    fi
  fi

  local artifact_url="$maven_url/dev/penvire/lab/fga-model/$model_version/fga-model-$model_version.json"
  curl --silent --show-error --fail --location --max-time 30 \
    "$artifact_url" -o "$work/model.json"
  curl --silent --show-error --fail --location --max-time 30 \
    "$artifact_url.sha256" -o "$work/model.json.sha256"

  local expected_sha actual_sha
  expected_sha=$(tr -d '[:space:]' < "$work/model.json.sha256")
  actual_sha=$(sha256sum "$work/model.json" | cut -d' ' -f1)
  if [[ ! "$expected_sha" =~ ^[0-9a-fA-F]{64}$ || "${expected_sha,,}" != "${actual_sha,,}" ]]; then
    printf 'SHA-256 mismatch for %s (expected %s, got %s)\n' "$artifact_url" "$expected_sha" "$actual_sha" >&2
    exit 1
  fi
  if ! valid_model_json "$work/model.json"; then
    printf 'Downloaded fga-model %s is not a valid OpenFGA model JSON\n' "$model_version" >&2
    exit 1
  fi

  mv "$work/model.json" "$cached"
  printf '%s\n' "$cached"
}

fga_request() {
  local method=$1 path=$2 body_file=${3:-}
  local response_file="$work/response.json"
  local -a args=(
    --silent --show-error --max-time 10 --noproxy '*'
    -X "$method"
    -o "$response_file"
    -w '%{http_code}'
  )
  if [[ -n "$body_file" ]]; then
    args+=(-H 'Content-Type: application/json' --data-binary "@$body_file")
  fi
  curl "${args[@]}" "$api_url$path"
}

wait_for_openfga() {
  local attempt
  for attempt in $(seq 1 25); do
    if curl --silent --fail --max-time 2 --noproxy '*' "$api_url/healthz" > /dev/null 2>&1; then
      return 0
    fi
    if [[ "$attempt" -lt 25 ]]; then
      sleep 0.2
    fi
  done
  printf 'OpenFGA is not reachable at %s\n' "$api_url" >&2
  exit 1
}

find_store_by_name() {
  local token=""
  while :; do
    local query=""
    if [[ -n "$token" ]]; then
      local encoded
      encoded=$(jq -rn --arg t "$token" '$t | @uri')
      query="?continuation_token=$encoded"
    fi
    local status
    status=$(fga_request GET "/stores$query")
    if [[ "$status" != "200" ]]; then
      printf 'Failed to list OpenFGA stores (HTTP %s): %s\n' "$status" "$(cat "$work/response.json")" >&2
      exit 1
    fi
    local matched
    matched=$(jq -r --arg name "$store_name" '.stores // [] | map(select(.name == $name)) | .[0].id // empty' "$work/response.json")
    if [[ -n "$matched" ]]; then
      printf '%s\n' "$matched"
      return 0
    fi
    token=$(jq -r '.continuation_token // empty' "$work/response.json")
    if [[ -z "$token" ]]; then
      return 0
    fi
  done
}

model_file=$(resolve_model)
model_sha256=$(sha256sum "$model_file" | cut -d' ' -f1)

wait_for_openfga

state_file="$state_dir/state.json"
recorded_store_id=""
recorded_model_id=""
recorded_model_version=""
recorded_model_sha256=""
if [[ -s "$state_file" ]] && jq -e 'type == "object"' "$state_file" > /dev/null 2>&1; then
  recorded_store_id=$(jq -r '.store_id // empty' "$state_file")
  recorded_model_id=$(jq -r '.model_id // empty' "$state_file")
  recorded_model_version=$(jq -r '.model_version // empty' "$state_file")
  recorded_model_sha256=$(jq -r '.model_sha256 // empty' "$state_file")
fi

store_id=""
if [[ -n "$recorded_store_id" ]]; then
  status=$(fga_request GET "/stores/$recorded_store_id")
  if [[ "$status" == "200" ]]; then
    existing_name=$(jq -r '.name // empty' "$work/response.json")
    if [[ "$existing_name" == "$store_name" ]]; then
      store_id="$recorded_store_id"
    fi
  elif [[ "$status" != "404" ]]; then
    printf 'Failed to verify OpenFGA store %s (HTTP %s): %s\n' "$recorded_store_id" "$status" "$(cat "$work/response.json")" >&2
    exit 1
  fi
fi

if [[ -z "$store_id" ]]; then
  store_id=$(find_store_by_name)
fi

if [[ -z "$store_id" ]]; then
  jq -nc --arg name "$store_name" '{name: $name}' > "$work/create-store.json"
  status=$(fga_request POST "/stores" "$work/create-store.json")
  if [[ "$status" != "201" ]]; then
    printf 'Failed to create OpenFGA store %s (HTTP %s): %s\n' "$store_name" "$status" "$(cat "$work/response.json")" >&2
    exit 1
  fi
  store_id=$(jq -r '.id // empty' "$work/response.json")
  if [[ -z "$store_id" ]]; then
    printf 'OpenFGA store creation response missing id: %s\n' "$(cat "$work/response.json")" >&2
    exit 1
  fi
fi

status=$(fga_request GET "/stores/$store_id/authorization-models?page_size=1")
if [[ "$status" != "200" ]]; then
  printf 'Failed to inspect OpenFGA models for store %s (HTTP %s): %s\n' "$store_id" "$status" "$(cat "$work/response.json")" >&2
  exit 1
fi
latest_model_id=$(jq -r '.authorization_models[0].id // empty' "$work/response.json")

model_id=""
action="already up to date with"
if [[ -n "$latest_model_id" &&
  "$recorded_store_id" == "$store_id" &&
  "$recorded_model_id" == "$latest_model_id" &&
  "$recorded_model_version" == "$model_version" &&
  "$recorded_model_sha256" == "$model_sha256" ]]; then
  model_id="$latest_model_id"
else
  status=$(fga_request POST "/stores/$store_id/authorization-models" "$model_file")
  if [[ "$status" != "201" ]]; then
    printf 'Failed to upload fga-model %s to store %s (HTTP %s): %s\n' \
      "$model_version" "$store_id" "$status" "$(cat "$work/response.json")" >&2
    exit 1
  fi
  model_id=$(jq -r '.authorization_model_id // empty' "$work/response.json")
  if [[ -z "$model_id" ]]; then
    printf 'OpenFGA model upload response missing authorization_model_id: %s\n' "$(cat "$work/response.json")" >&2
    exit 1
  fi
  action="bootstrapped with"
fi

jq -n \
  --arg store_name "$store_name" \
  --arg store_id "$store_id" \
  --arg model_id "$model_id" \
  --arg model_version "$model_version" \
  --arg model_sha256 "$model_sha256" \
  '{
    store_name: $store_name,
    store_id: $store_id,
    model_id: $model_id,
    model_version: $model_version,
    model_sha256: $model_sha256
  }' > "$work/state.json"

printf '%s\n' "$store_id" > "$work/store-id"
printf '%s\n' "$model_id" > "$work/model-id"
{
  printf 'export FGA_API_URL=%s\n' "$(jq -rn --arg v "$api_url" '$v | @sh')"
  printf 'export FGA_STORE_ID=%s\n' "$(jq -rn --arg v "$store_id" '$v | @sh')"
  printf 'export FGA_MODEL_ID=%s\n' "$(jq -rn --arg v "$model_id" '$v | @sh')"
} > "$work/env"

mv "$work/state.json" "$state_file"
mv "$work/store-id" "$state_dir/store-id"
mv "$work/model-id" "$state_dir/model-id"
mv "$work/env" "$state_dir/env"

printf 'OpenFGA store %s (%s) %s fga-model %s (%s)\n' \
  "$store_name" "$store_id" "$action" "$model_version" "$model_id"
