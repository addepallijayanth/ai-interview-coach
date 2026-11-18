-- Real accounts (name, country and mobile number, confirmed email) and interview levels with a question plan.

alter table users
    add column first_name        varchar(60),
    add column last_name         varchar(60),
    add column country_code      varchar(2),       -- ISO 3166-1 alpha-2 region the mobile number belongs to
    add column phone_number      varchar(20),      -- E.164, e.g. +919876543210
    add column email_verified_at timestamptz;

-- Accounts created before email confirmation existed keep working.
update users set email_verified_at = created_at where email_verified_at is null;

-- Single-use links sent by email. Only a SHA-256 hash of the token is stored, never the token itself.
create table email_token (
    id         bigserial primary key,
    user_id    bigint      not null references users (id) on delete cascade,
    purpose    varchar(20) not null check (purpose in ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    token_hash varchar(64) not null unique,
    expires_at timestamptz not null,
    used_at    timestamptz,
    created_at timestamptz not null default now()
);
create index idx_email_token_user on email_token (user_id, purpose, created_at desc);

alter table interview_session
    add column level         varchar(20) not null default 'STANDARD'
        check (level in ('EASY', 'STANDARD', 'PROFESSIONAL')),
    add column question_plan jsonb;            -- category of every planned question, in order

alter table interview_turn
    add column category varchar(30);           -- planned category of the question (null for older interviews)
