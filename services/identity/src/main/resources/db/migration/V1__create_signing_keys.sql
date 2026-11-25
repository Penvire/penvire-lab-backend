-- Signing keys (ADR 0007, ADR 0009). The private key is encrypted under a KEK outside the
-- database; row_mac protects every other column against tampering.
create extension if not exists btree_gist;

create table signing_keys (
    id                    uuid        primary key,
    signing_key_algorithm text        not null check (signing_key_algorithm in ('ML_DSA_65', 'RS256')),
    public_key            bytea       not null,
    encrypted_private_key bytea,
    private_key_nonce     bytea       check (octet_length(private_key_nonce) = 12),
    kek_id                text        not null,
    row_mac               bytea       not null check (octet_length(row_mac) = 32),
    publish_from          timestamptz not null,
    sign_from             timestamptz not null,
    sign_until            timestamptz not null,
    publish_until         timestamptz not null,
    revoked_at            timestamptz,

    constraint signing_keys_lifetime_in_order
        check (publish_from <= sign_from and sign_from < sign_until and sign_until <= publish_until),
    -- A key can be revoked before it is published, never after it expired.
    constraint signing_keys_revoked_before_expiry
        check (revoked_at is null or revoked_at < publish_until),
    constraint signing_keys_private_key_with_nonce
        check ((encrypted_private_key is null) = (private_key_nonce is null)),
    constraint signing_keys_revoked_without_private_key
        check (revoked_at is null or encrypted_private_key is null),
    -- At most one unrevoked key per algorithm signs at any instant.
    constraint signing_keys_one_signer_per_algorithm
        exclude using gist (
            signing_key_algorithm with =,
            tstzrange(sign_from, sign_until) with &&
        ) where (revoked_at is null)
);

-- Cleanup deletes expired rows.
create index signing_keys_publish_until on signing_keys (publish_until);
