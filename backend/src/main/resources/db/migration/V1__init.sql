-- AI Interview Coach schema. Every user-owned row carries user_id so ownership checks are a single indexed lookup.

create table users (
    id            bigserial primary key,
    email         varchar(255) not null unique,
    password_hash varchar(100) not null,
    created_at    timestamptz  not null default now()
);

create table resume (
    id            bigserial primary key,
    user_id       bigint       not null references users (id) on delete cascade,
    file_name     varchar(255) not null,
    content_type  varchar(100),
    redacted_text text         not null,          -- PII is masked before the text is stored or sent to the AI
    profile_json  jsonb,                          -- cached structured extraction, reused across analyses
    created_at    timestamptz  not null default now()
);
create index idx_resume_user on resume (user_id, created_at desc);

create table job_description (
    id                bigserial primary key,
    user_id           bigint      not null references users (id) on delete cascade,
    title             varchar(200),
    company           varchar(200),
    text              text        not null,
    requirements_json jsonb,                      -- cached structured extraction
    created_at        timestamptz not null default now()
);
create index idx_jd_user on job_description (user_id, created_at desc);

create table analysis (
    id          bigserial primary key,
    user_id     bigint        not null references users (id) on delete cascade,
    resume_id   bigint        not null references resume (id) on delete cascade,
    jd_id       bigint        not null references job_description (id) on delete cascade,
    match_score integer       not null check (match_score between 0 and 100),
    result_json jsonb         not null,
    model_used  varchar(200)  not null,
    tokens_in   integer       not null default 0,
    tokens_out  integer       not null default 0,
    cost_usd    numeric(10,6) not null default 0,
    created_at  timestamptz   not null default now()
);
create index idx_analysis_user on analysis (user_id, created_at desc);

create table interview_session (
    id              bigserial primary key,
    user_id         bigint       not null references users (id) on delete cascade,
    analysis_id     bigint       not null references analysis (id) on delete cascade,
    status          varchar(20)  not null check (status in ('ACTIVE', 'ENDED')),
    question_target integer      not null,
    overall_score   numeric(4,1),
    summary         text,
    transcript_json jsonb        not null default '[]'::jsonb,  -- full agent conversation, replayed append-only
    end_requested   boolean      not null default false,
    study_plan_json jsonb,
    created_at      timestamptz  not null default now(),
    ended_at        timestamptz
);
create index idx_session_user on interview_session (user_id, created_at desc);
create index idx_session_analysis on interview_session (analysis_id);

create table interview_turn (
    id            bigserial primary key,
    session_id    bigint      not null references interview_session (id) on delete cascade,
    question_no   integer     not null,
    question      text        not null,
    answer        text,
    score         integer     check (score between 0 and 10),
    feedback_json jsonb,
    created_at    timestamptz not null default now(),
    answered_at   timestamptz,
    unique (session_id, question_no)
);

-- One row per Claude API call: powers the daily spend cap and the cost-per-session numbers.
create table ai_usage (
    id                 bigserial primary key,
    user_id            bigint        references users (id) on delete set null,
    session_id         bigint        references interview_session (id) on delete set null,
    task               varchar(40)   not null,
    model              varchar(60)   not null,
    input_tokens       integer       not null,
    output_tokens      integer       not null,
    cache_read_tokens  integer       not null default 0,
    cache_write_tokens integer       not null default 0,
    cost_usd           numeric(10,6) not null,
    created_at         timestamptz   not null default now()
);
create index idx_ai_usage_created on ai_usage (created_at);
create index idx_ai_usage_user on ai_usage (user_id, created_at);
create index idx_ai_usage_session on ai_usage (session_id);
