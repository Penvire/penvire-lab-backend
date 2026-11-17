# Wrapped by writeShellApplication, which enables strict Bash error handling.
umask 077
: "${DEVENV_STATE:?Run inside devenv}"
: "${DEVENV_ROOT:?Run inside devenv}"
export CAROOT="$DEVENV_STATE/tls/ca"
mkdir -p "$CAROOT"
chmod 700 "$DEVENV_STATE/tls" "$CAROOT"

# Serialize CA creation and serial-number updates, including manual invocations.
exec 9> "$DEVENV_STATE/tls/.lock"
flock 9

work=$(mktemp -d "$DEVENV_STATE/tls/.issuance.XXXXXX")
trap 'rm -rf "$work"' EXIT
profiles="$work/profiles.cnf"
cp "$DEVENV_ROOT/infrastructure/local/certificates.cnf" "$profiles"
profile_digest=$(openssl dgst -sha256 < "$profiles")
algorithm=EC
curve=P-256
lifetime_days=30

# Create a project-local CA without modifying the system trust store.
if [[ ! -s "$CAROOT/rootCA.pem" || ! -s "$CAROOT/rootCA-key.pem" ]]; then
  mkcert -cert-file "$work/bootstrap.pem" \
    -key-file "$work/bootstrap-key.pem" localhost
fi

publish() {
  local name=$1 filename restore
  local backup="$work/$name-backup"
  local files=("$name-key.pem" "$name.pem" "$name.policy")
  mkdir "$backup"
  for filename in "${files[@]}"; do
    if [[ -e "$DEVENV_STATE/tls/$filename" ]]; then
      cp -p "$DEVENV_STATE/tls/$filename" "$backup/$filename"
    fi
  done
  for filename in "${files[@]}"; do
    if ! mv "$work/$filename" "$DEVENV_STATE/tls/$filename"; then
      printf 'Publication failed for %s; restoring previous files\n' "$name" >&2
      for restore in "${files[@]}"; do
        if [[ -e "$backup/$restore" ]]; then
          cp -p "$backup/$restore" "$DEVENV_STATE/tls/$restore"
        else
          rm -f "$DEVENV_STATE/tls/$restore"
        fi
      done
      return 1
    fi
  done
}

issue() {
  local name=$1 profile=$2 subject=$3 purpose=$4
  local cert="$DEVENV_STATE/tls/$name.pem"
  local key="$DEVENV_STATE/tls/$name-key.pem"
  local cert_public key_public fingerprint previous=''
  fingerprint=$(printf '%s\n' "$profile_digest" "$name" "$profile" "$subject" "$purpose" \
    "$algorithm" "$curve" "$lifetime_days" | openssl dgst -sha256)

  # Reuse only valid, matching pairs issued by this CA and the current profile.
  if [[ -s "$cert" && -s "$key" && -s "$DEVENV_STATE/tls/$name.policy" ]] &&
    IFS= read -r previous < "$DEVENV_STATE/tls/$name.policy" &&
    [[ "$previous" == "$fingerprint" ]] &&
    openssl x509 -in "$cert" -checkend 604800 -noout > /dev/null 2>&1 &&
    openssl verify -CAfile "$CAROOT/rootCA.pem" -purpose "$purpose" "$cert" > /dev/null 2>&1; then
    cert_public=$(openssl x509 -in "$cert" -pubkey -noout)
    if key_public=$(openssl pkey -in "$key" -pubout 2>/dev/null) && [[ "$cert_public" == "$key_public" ]]; then
      return
    fi
  fi

  openssl genpkey -algorithm "$algorithm" -pkeyopt "ec_paramgen_curve:$curve" -out "$work/$name-key.pem"
  openssl req -new -key "$work/$name-key.pem" -subj "/CN=$subject" -out "$work/request.csr"
  openssl x509 -req -in "$work/request.csr" \
    -CA "$CAROOT/rootCA.pem" -CAkey "$CAROOT/rootCA-key.pem" \
    -CAcreateserial -days "$lifetime_days" -sha256 \
    -extfile "$profiles" -extensions "$profile" -out "$work/$name.pem"
  openssl verify -CAfile "$CAROOT/rootCA.pem" -purpose "$purpose" "$work/$name.pem"
  cert_public=$(openssl x509 -in "$work/$name.pem" -pubkey -noout)
  key_public=$(openssl pkey -in "$work/$name-key.pem" -pubout)
  [[ "$cert_public" == "$key_public" ]]
  printf '%s\n' "$fingerprint" > "$work/$name.policy"
  publish "$name"
  printf 'Issued %s (%s days; renews with less than 7 days remaining)\n' "$name" "$lifetime_days"
}

issue localhost gateway localhost sslserver
issue identity identity identity sslserver
issue traefik-client traefik-client traefik sslclient
