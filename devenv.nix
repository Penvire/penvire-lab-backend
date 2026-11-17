{ config, pkgs, ... }:
let
  # Same rule as the Conventional Commits check in Penvire/.github.
  conventionalCommit = pkgs.writeShellApplication {
    name = "conventional-commit";
    text = ''
      subject=$(head -n 1 "$1")
      case $subject in
        "Merge "* | "Revert "* | "fixup! "* | "squash! "* | "amend! "*) exit 0 ;;
      esac
      pattern='^(build|chore|ci|docs|feat|fix|perf|refactor|revert|style|test)(\([[:alnum:]._/-]+\))?!?: [^[:space:]].*$'
      if [[ ! "$subject" =~ $pattern ]]; then
        echo "Not a Conventional Commit: $subject" >&2
        echo "Use 'type(scope): description', e.g. 'feat(identity): add token exchange'." >&2
        echo "Types: build chore ci docs feat fix perf refactor revert style test." >&2
        exit 1
      fi
    '';
  };

  local = "${config.devenv.root}/infrastructure/local";

  # --- Gateway: Traefik with a project-local CA ---------------------------
  httpPort = toString config.processes.traefik.ports.http.value;
  httpsPort = toString config.processes.traefik.ports.https.value;
  tls = pkgs.writeShellApplication {
    name = "tls-certs";
    runtimeInputs = [ pkgs.mkcert pkgs.openssl pkgs.coreutils pkgs.util-linux ];
    text = builtins.readFile ./infrastructure/local/tls-certs.sh;
  };
  traefikConfig = pkgs.writeText "traefik.json" (builtins.toJSON {
    entryPoints = {
      web = {
        address = "127.0.0.1:${httpPort}";
        http.redirections.entryPoint = {
          to = "websecure";
          scheme = "https";
          permanent = true;
        };
      };
      websecure = {
        address = "127.0.0.1:${httpsPort}";
        http.tls = { };
      };
    };
    providers.file = {
      filename = "${local}/traefik.yaml";
      watch = true;
    };
    ping = { entryPoint = "websecure"; };
    api.dashboard = false;
    global = { checkNewVersion = false; sendAnonymousUsage = false; };
  });

  # --- Observability: collector -> Tempo, Loki, Prometheus -> Grafana -------
  grafanaIni = pkgs.writeText "grafana.ini" ''
    [server]
    http_addr = 127.0.0.1
    http_port = 3000
    [auth.anonymous]
    enabled = true
    org_role = Admin
    [auth]
    disable_login_form = true
    [analytics]
    reporting_enabled = false
    check_for_updates = false
    check_for_plugin_updates = false
    [news]
    news_feed_enabled = false
  '';
in
{
  languages.java = {
    enable = true;
    jdk.package = pkgs.jdk25;
    # Bootstraps the Gradle wrapper; use ./gradlew once it exists.
    gradle = {
      enable = true;
      package = pkgs.gradle_9.override { java = pkgs.jdk25; };
    };
  };

  packages = [ tls pkgs.mkcert pkgs.openssl pkgs.curl ];

  env.CAROOT = "${config.devenv.state}/tls/ca";
  env.PENVIRE_HTTP_PORT = httpPort;
  env.PENVIRE_HTTPS_PORT = httpsPort;

  tasks."tls:certs" = {
    description = "Create or renew local gateway, identity and mTLS client certificates";
    exec = "tls-certs";
  };

  processes.traefik = {
    ports = {
      http.allocate = 8080;
      https.allocate = 8443;
    };
    after = [ "tls:certs" ];
    exec = "exec ${pkgs.traefik}/bin/traefik --configFile=${traefikConfig}";
    ready.exec = ''
      curl --silent --show-error --fail --max-time 2 --noproxy '*' \
        --cacert "$CAROOT/rootCA.pem" "https://127.0.0.1:$PENVIRE_HTTPS_PORT/ping" > /dev/null
    '';
  };

  services.opentelemetry-collector = {
    enable = true;
    # The contrib build is broken on nixpkgs-unstable; the core collector is enough.
    package = pkgs.opentelemetry-collector;
    settings = {
      receivers.otlp.protocols = {
        grpc.endpoint = "127.0.0.1:4317";
        http.endpoint = "127.0.0.1:4318";
      };
      processors.batch = { };
      exporters = {
        "otlp/tempo" = {
          endpoint = "127.0.0.1:4417";
          tls.insecure = true;
        };
        "otlphttp/loki".endpoint = "http://127.0.0.1:3100/otlp";
        "otlphttp/prometheus".endpoint = "http://127.0.0.1:9090/api/v1/otlp";
      };
      service.pipelines = {
        traces = { receivers = [ "otlp" ]; processors = [ "batch" ]; exporters = [ "otlp/tempo" ]; };
        logs = { receivers = [ "otlp" ]; processors = [ "batch" ]; exporters = [ "otlphttp/loki" ]; };
        metrics = { receivers = [ "otlp" ]; processors = [ "batch" ]; exporters = [ "otlphttp/prometheus" ]; };
      };
    };
  };

  # Own process: devenv's module predates Prometheus 3's OTLP receiver flag.
  processes.prometheus.exec = ''
    exec ${pkgs.prometheus}/bin/prometheus \
      --config.file=${local}/observability/prometheus.yaml \
      --storage.tsdb.path="$DEVENV_STATE/prometheus" \
      --web.listen-address=127.0.0.1:9090 \
      --web.enable-otlp-receiver \
      --enable-feature=exemplar-storage
  '';

  processes.tempo.exec = ''
    exec ${pkgs.tempo}/bin/tempo -config.expand-env=true -config.file=${local}/observability/tempo.yaml
  '';

  processes.loki.exec = ''
    exec ${pkgs.grafana-loki}/bin/loki -config.expand-env=true -config.file=${local}/observability/loki.yaml
  '';

  processes.grafana.exec = ''
    export GF_PATHS_DATA="$DEVENV_STATE/grafana"
    export GF_PATHS_LOGS="$DEVENV_STATE/grafana/log"
    export GF_PATHS_PLUGINS="$DEVENV_STATE/grafana/plugins"
    export GF_PATHS_PROVISIONING="${local}/observability/grafana"
    export PENVIRE_DASHBOARDS="${local}/observability/dashboards"
    exec ${pkgs.grafana}/bin/grafana server \
      --homepath ${pkgs.grafana}/share/grafana --config ${grafanaIni}
  '';

  git-hooks.hooks = {
    conventional-commit = {
      enable = true;
      name = "Conventional Commits";
      entry = "${conventionalCommit}/bin/conventional-commit";
      stages = [ "commit-msg" ];
    };
    actionlint.enable = true;
  };
}
