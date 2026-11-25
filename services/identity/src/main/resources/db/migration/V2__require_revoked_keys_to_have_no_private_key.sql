alter table signing_keys add constraint signing_keys_revoked_without_private_key
    check (revoked_at is null or encrypted_private_key is null);
