-- User accounts and their password credentials. Credentials live in their own table so accounts
-- never carry secret material and future factors (passkeys, TOTP) add separate tables.
create table accounts (
    id         uuid        primary key,
    email      text        not null,
    username   text        not null,
    enabled    boolean     not null default true,
    created_at timestamptz not null,

    constraint accounts_email_normalized
        check (email = lower(btrim(email)) and email <> '' and position('@' in email) > 1),
    constraint accounts_username_normalized
        check (username = lower(btrim(username)) and username <> '' and position('@' in username) = 0),
    constraint accounts_email_unique unique (email),
    constraint accounts_username_unique unique (username)
);

create table password_credentials (
    id            uuid        primary key,
    account_id    uuid        not null references accounts (id) on delete cascade,
    password_hash text        not null,
    created_at    timestamptz not null,
    revoked_at    timestamptz,

    constraint password_credentials_hash_not_blank
        check (btrim(password_hash) <> ''),
    constraint password_credentials_revoked_not_before_created
        check (revoked_at is null or revoked_at >= created_at)
);

-- At most one active (unrevoked) password per account.
create unique index password_credentials_one_active_per_account
    on password_credentials (account_id)
    where revoked_at is null;

-- Supports password-reuse checks ordered from newest to oldest.
create index password_credentials_account_history
    on password_credentials (account_id, created_at desc);
