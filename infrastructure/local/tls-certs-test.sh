#!/usr/bin/env bash
set -euo pipefail

directory=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cases=(fresh reuse policy inputs legacy missing_profile missing_key mismatched_key ca expiry concurrency failure first_failure)
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
trap 'rm -rf "$work"' EXIT
export DEVENV_STATE="$work/state" DEVENV_ROOT="$work/root"
mkdir -p "$DEVENV_ROOT/infrastructure/local" "$work/bin"
cp "$directory/certificates.cnf" "$DEVENV_ROOT/infrastructure/local/certificates.cnf"
profiles="$DEVENV_ROOT/infrastructure/local/certificates.cnf"
script="$directory/tls-certs.sh"
export REAL_OPENSSL REAL_MV
REAL_OPENSSL=$(command -v openssl)
REAL_MV=$(command -v mv)

cat > "$work/bin/fault-command" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
case "${0##*/}" in
  openssl)
    case "${FAIL_STAGE:-}:$*" in
      generation:genpkey*|request:req*|signing:x509\ -req*|verification:verify*\.issuance.*)
        printf 'Injected OpenSSL failure\n' >&2
        exit 73
        ;;
    esac
    exec "$REAL_OPENSSL" "$@"
    ;;
  mv)
    if [[ ${FAIL_STAGE:-} == "${2##*/}" ]]; then
      printf 'Injected publication failure\n' >&2
      exit 73
    fi
    exec "$REAL_MV" "$@"
    ;;
esac
SH
chmod +x "$work/bin/fault-command"
ln -s fault-command "$work/bin/openssl"
ln -s fault-command "$work/bin/mv"
export PATH="$work/bin:$PATH"

run() {
  if bash -euo pipefail "$script" > "$work/output" 2>&1; then
    return 0
  else
    status=$?
    cat "$work/output" >&2
    return "$status"
  fi
}

digest() { openssl dgst -sha256 < "$DEVENV_STATE/tls/$1"; }
valid_pair() {
  local name=$1 purpose=$2
  openssl verify -CAfile "$DEVENV_STATE/tls/ca/rootCA.pem" \
    -purpose "$purpose" "$DEVENV_STATE/tls/$name.pem" > /dev/null
  [[ $(openssl x509 -in "$DEVENV_STATE/tls/$name.pem" -pubkey -noout) == \
    "$(openssl pkey -in "$DEVENV_STATE/tls/$name-key.pem" -pubout)" ]]
}
no_temporary_files() {
  local files
  shopt -s nullglob
  files=("$DEVENV_STATE/tls/".issuance.*)
  [[ ${#files[@]} == 0 ]]
}

run
before=$(digest localhost.pem)
case "$1" in
  fresh)
    valid_pair localhost sslserver
    valid_pair identity sslserver
    valid_pair traefik-client sslclient
    openssl x509 -in "$DEVENV_STATE/tls/localhost.pem" -noout -checkhost localhost
    openssl x509 -in "$DEVENV_STATE/tls/localhost.pem" -noout -checkip 127.0.0.1
    openssl x509 -in "$DEVENV_STATE/tls/localhost.pem" -noout -checkip ::1
    openssl x509 -in "$DEVENV_STATE/tls/identity.pem" -noout -checkhost identity
    [[ $(stat -c %a "$DEVENV_STATE/tls") == 700 ]]
    [[ $(stat -c %a "$DEVENV_STATE/tls/localhost-key.pem") == 600 ]]
    no_temporary_files
    ;;
  reuse)
    touch "$profiles"
    run
    [[ $(digest localhost.pem) == "$before" ]]
    ;;
  policy)
    cp -p "$profiles" "$work/original.cnf"
    sed -i 's/DNS:localhost, IP/DNS:localhost, DNS:policy.local, IP/' "$profiles"
    touch -r "$work/original.cnf" "$profiles"
    run
    [[ $(digest localhost.pem) != "$before" ]]
    openssl x509 -in "$DEVENV_STATE/tls/localhost.pem" -noout -checkhost policy.local
    ;;
  inputs)
    cp "$script" "$work/changed.sh"
    # Change issuance inputs without touching the signing profiles.
    sed -i 's/issue localhost gateway localhost sslserver/issue localhost gateway changed sslserver/' "$work/changed.sh"
    script="$work/changed.sh"
    run
    [[ $(openssl x509 -in "$DEVENV_STATE/tls/localhost.pem" -subject -nameopt RFC2253 -noout) == subject=CN=changed ]]
    ;;
  legacy)
    rm "$DEVENV_STATE/tls/localhost.policy"
    run
    [[ $(digest localhost.pem) != "$before" ]]
    valid_pair localhost sslserver
    ;;
  missing_profile)
    rm "$profiles"
    if run; then
      printf 'Missing signing profiles must fail\n' >&2
      exit 1
    fi
    [[ $(digest localhost.pem) == "$before" ]]
    no_temporary_files
    ;;
  missing_key|mismatched_key)
    rm "$DEVENV_STATE/tls/localhost-key.pem"
    if [[ $1 == mismatched_key ]]; then
      openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 \
        -out "$DEVENV_STATE/tls/localhost-key.pem"
    fi
    run
    [[ $(digest localhost.pem) != "$before" ]]
    valid_pair localhost sslserver
    ;;
  ca)
    rm -rf "$DEVENV_STATE/tls/ca"
    run
    [[ $(digest localhost.pem) != "$before" ]]
    valid_pair localhost sslserver
    valid_pair identity sslserver
    valid_pair traefik-client sslclient
    ;;
  expiry)
    openssl req -new -key "$DEVENV_STATE/tls/localhost-key.pem" -subj /CN=localhost -out "$work/short.csr"
    openssl x509 -req -in "$work/short.csr" -CA "$DEVENV_STATE/tls/ca/rootCA.pem" \
      -CAkey "$DEVENV_STATE/tls/ca/rootCA-key.pem" -days 1 \
      -extfile "$profiles" -extensions gateway \
      -out "$DEVENV_STATE/tls/localhost.pem"
    openssl verify -CAfile "$DEVENV_STATE/tls/ca/rootCA.pem" \
      -purpose sslserver "$DEVENV_STATE/tls/localhost.pem"
    short=$(digest localhost.pem)
    run
    [[ $(digest localhost.pem) != "$short" ]]
    openssl x509 -in "$DEVENV_STATE/tls/localhost.pem" -checkend 604800 -noout
    ;;
  concurrency)
    rm -rf "$DEVENV_STATE"
    bash -euo pipefail "$script" > "$work/first.log" 2>&1 &
    first=$!
    bash -euo pipefail "$script" > "$work/second.log" 2>&1 &
    second=$!
    wait "$first"
    wait "$second"
    valid_pair localhost sslserver
    valid_pair identity sslserver
    valid_pair traefik-client sslclient
    no_temporary_files
    ;;
  failure)
    cp "$DEVENV_STATE/tls/localhost-key.pem" "$work/old-key.pem"
    cp "$DEVENV_STATE/tls/localhost.policy" "$work/old.policy"
    for stage in generation request signing verification localhost-key.pem localhost.pem localhost.policy; do
      export FAIL_STAGE="$stage"
      printf '\n# Force renewal %s\n' "$stage" >> "$profiles"
      if run; then
        printf 'Expected failure: %s\n' "$stage" >&2
        exit 1
      fi
      [[ $(digest localhost.pem) == "$before" ]]
      cmp "$DEVENV_STATE/tls/localhost-key.pem" "$work/old-key.pem"
      cmp "$DEVENV_STATE/tls/localhost.policy" "$work/old.policy"
      valid_pair localhost sslserver
      no_temporary_files
    done
    unset FAIL_STAGE
    run
    valid_pair localhost sslserver
    ;;
  first_failure)
    rm -rf "$DEVENV_STATE"
    export FAIL_STAGE=localhost.pem
    if run; then
      printf 'Expected first-issuance publication failure\n' >&2
      exit 1
    fi
    [[ ! -e "$DEVENV_STATE/tls/localhost.pem" ]]
    [[ ! -e "$DEVENV_STATE/tls/localhost-key.pem" ]]
    [[ ! -e "$DEVENV_STATE/tls/localhost.policy" ]]
    no_temporary_files
    unset FAIL_STAGE
    run
    valid_pair localhost sslserver
    ;;
  *) printf 'Unknown test: %s\n' "$1" >&2; exit 2 ;;
esac
