-- Modelo de datos completo. Las duraciones se guardan en minutos (int) y las horas como time.
-- Nunca se guardan totales derivados (día, mes, periodo): los calculan los servicios.

create extension if not exists btree_gist;

-- ---------------------------------------------------------------- usuarios y sesiones
create table users (
    id              uuid primary key,
    email           varchar(254) not null,
    password_hash   varchar(255) not null,
    name            varchar(100) not null,
    company         varchar(100),
    timezone        varchar(64)  not null default 'Europe/Madrid',
    role            varchar(10)  not null default 'USER' check (role in ('USER', 'ADMIN')),
    enabled         boolean      not null default true,
    failed_logins   int          not null default 0,
    locked_until    timestamptz,
    created_at      timestamptz  not null default now()
);
create unique index ux_users_email on users (lower(email));

create table refresh_tokens (
    id           uuid primary key,
    user_id      uuid        not null references users (id) on delete cascade,
    family_id    uuid        not null,
    token_hash   varchar(64) not null unique,          -- SHA-256 en hex; el token nunca se guarda
    expires_at   timestamptz not null,
    revoked_at   timestamptz,
    replaced_by  uuid references refresh_tokens (id) on delete set null,
    user_agent   varchar(255),
    ip           varchar(45),
    created_at   timestamptz not null default now()
);
create index ix_refresh_tokens_user on refresh_tokens (user_id);
create index ix_refresh_tokens_family on refresh_tokens (family_id);

-- ---------------------------------------------------------------- periodo anual (hoja Horas)
create table work_periods (
    id                     uuid primary key,
    user_id                uuid         not null references users (id) on delete cascade,
    name                   varchar(100) not null,
    start_date             date         not null,
    end_date               date         not null,
    agreement_minutes      int          not null check (agreement_minutes > 0),
    vacation_days          int          not null check (vacation_days between 0 and 366),
    normal_day_minutes     int          not null check (normal_day_minutes between 1 and 1440),
    intensive_day_minutes  int          not null check (intensive_day_minutes between 1 and 1440),
    breakfast_tolerance_min int         not null check (breakfast_tolerance_min between 0 and 240),
    min_lunch_min          int          not null check (min_lunch_min between 0 and 240),
    rounding_step_min      int          not null check (rounding_step_min between 1 and 60),
    max_remote_pct         int          not null check (max_remote_pct between 0 and 100),
    max_remote_days_month  int          not null check (max_remote_days_month between 0 and 31),
    opening_balance_min    int          not null default 0,
    version                bigint       not null default 0,
    created_at             timestamptz  not null default now(),
    constraint ck_work_periods_dates check (start_date < end_date),
    constraint ex_work_periods_overlap exclude using gist (
        user_id with =, daterange(start_date, end_date, '[]') with &&)
);

create table intensive_ranges (
    id          uuid primary key,
    period_id   uuid not null references work_periods (id) on delete cascade,
    start_date  date not null,
    end_date    date not null,
    constraint ck_intensive_ranges_dates check (start_date <= end_date)
);
create index ix_intensive_ranges_period on intensive_ranges (period_id);

create table holidays (
    id         uuid primary key,
    period_id  uuid         not null references work_periods (id) on delete cascade,
    date       date         not null,
    name       varchar(100) not null,
    scope      varchar(12)  not null check (scope in ('NACIONAL', 'AUTONOMICO', 'LOCAL', 'EMPRESA')),
    constraint ux_holidays_period_date unique (period_id, date)
);

-- ---------------------------------------------------------------- ausencias y registro diario
create table absences (
    id        uuid primary key,
    user_id   uuid        not null references users (id) on delete cascade,
    date      date        not null,
    type      varchar(12) not null check (type in ('VACACIONES', 'PUENTE', 'PERMISO', 'BAJA')),
    half_day  boolean     not null default false,
    note      varchar(500),
    constraint ux_absences_user_date unique (user_id, date)
);

create table workdays (
    id               uuid primary key,
    user_id          uuid        not null references users (id) on delete cascade,
    date             date        not null,
    start_time       time        not null,
    end_time         time        not null,
    location         varchar(8)  not null check (location in ('OFICINA', 'CASA', 'MIXTO')),
    remote_minutes   int check (remote_minutes >= 0),
    jira_minutes     int check (jira_minutes >= 0),
    izertia_minutes  int check (izertia_minutes >= 0),
    notes            varchar(500),
    version          bigint      not null default 0,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    constraint ux_workdays_user_date unique (user_id, date),
    constraint ck_workdays_times check (end_time > start_time)
);

create table workday_breaks (
    id          uuid primary key,
    workday_id  uuid        not null references workdays (id) on delete cascade,
    type        varchar(10) not null check (type in ('DESAYUNO', 'COMIDA', 'OTRA')),
    start_time  time        not null,
    end_time    time        not null,
    constraint ck_workday_breaks_times check (end_time > start_time)
);
create index ix_workday_breaks_workday on workday_breaks (workday_id);

-- ---------------------------------------------------------------- auditoría (solo inserción)
create table audit_log (
    id         bigserial primary key,
    user_id    uuid,
    action     varchar(50) not null,
    entity     varchar(50),
    entity_id  varchar(64),
    at         timestamptz not null default now(),
    ip         varchar(45)
);
create index ix_audit_log_user on audit_log (user_id, at);
