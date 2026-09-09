-- AM STUDIO Music Distribution — canonical PostgreSQL schema foundation v1
-- Provider/DSP identifiers are references; AM STUDIO IDs remain canonical.

create extension if not exists pgcrypto;

create type release_status as enum (
  'DRAFT','PREFLIGHT','RIGHTS_CONFIRMED','READY_FOR_QC','QC_REVIEW','NEEDS_CHANGES',
  'APPROVED','QUEUED_FOR_DELIVERY','DELIVERING','DSP_PROCESSING','PARTIALLY_LIVE','LIVE',
  'REJECTED','DELIVERY_FAILED','UPDATE_PENDING','TAKEDOWN_PENDING','TAKEN_DOWN','FROZEN_RISK_REVIEW'
);
create type payout_status as enum ('PAYOUT_REQUESTED','VALIDATING','ON_HOLD','APPROVED','PROCESSING','PAID','FAILED','REVERSED');

create table app_user (
  id uuid primary key default gen_random_uuid(),
  email text not null unique,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now()
);
create table organization (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  legal_name text,
  country_code char(2),
  created_at timestamptz not null default now()
);
create table membership (
  organization_id uuid not null references organization(id),
  user_id uuid not null references app_user(id),
  role text not null,
  primary key (organization_id,user_id)
);
create table artist (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references organization(id),
  display_name text not null,
  created_at timestamptz not null default now()
);
create table label (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references organization(id),
  name text not null,
  created_at timestamptz not null default now()
);
create table release (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references organization(id),
  label_id uuid references label(id),
  primary_artist_id uuid not null references artist(id),
  title text not null,
  release_type text not null,
  genre text,
  language_code text,
  release_date date,
  upc text,
  status release_status not null default 'DRAFT',
  current_version integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create table release_version (
  release_id uuid not null references release(id),
  version integer not null,
  snapshot jsonb not null,
  created_by uuid references app_user(id),
  created_at timestamptz not null default now(),
  primary key (release_id,version)
);
create table track (
  id uuid primary key default gen_random_uuid(),
  release_id uuid not null references release(id) on delete cascade,
  position integer not null,
  title text not null,
  isrc text,
  explicit boolean not null default false,
  duration_ms integer,
  unique (release_id,position)
);
create table contributor (
  id uuid primary key default gen_random_uuid(),
  track_id uuid not null references track(id) on delete cascade,
  name text not null,
  role text not null,
  share_bps integer check (share_bps between 0 and 10000)
);
create table asset (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references organization(id),
  release_id uuid references release(id),
  track_id uuid references track(id),
  kind text not null,
  storage_key text not null unique,
  original_filename text,
  content_type text,
  byte_size bigint,
  sha256 text,
  created_at timestamptz not null default now()
);
create table rights_declaration (
  id uuid primary key default gen_random_uuid(),
  release_id uuid not null references release(id),
  release_version integer not null,
  master_control boolean not null,
  composition_permission boolean not null,
  no_artificial_streaming boolean not null,
  rights_holder text not null,
  declared_by uuid not null references app_user(id),
  declared_at timestamptz not null default now(),
  unique (release_id,release_version)
);
create table distribution_intent (
  id uuid primary key default gen_random_uuid(),
  release_id uuid not null references release(id),
  release_version integer not null,
  dsps jsonb not null,
  territories jsonb not null,
  created_at timestamptz not null default now(),
  unique (release_id,release_version)
);
create table distribution_request (
  id uuid primary key default gen_random_uuid(),
  release_id uuid not null references release(id),
  release_version integer not null,
  idempotency_key text not null unique,
  status release_status not null,
  submitted_at timestamptz not null default now()
);
create table provider_reference (
  id uuid primary key default gen_random_uuid(),
  distribution_request_id uuid not null references distribution_request(id),
  provider_key text not null,
  provider_release_id text,
  provider_submission_id text,
  metadata jsonb not null default '{}'::jsonb,
  unique (provider_key,provider_release_id)
);
create table delivery_event (
  id uuid primary key default gen_random_uuid(),
  distribution_request_id uuid not null references distribution_request(id),
  provider_key text not null,
  dsp_key text,
  canonical_status release_status not null,
  provider_status text,
  live_url text,
  payload_hash text,
  happened_at timestamptz not null,
  received_at timestamptz not null default now()
);
create table royalty_statement (
  id uuid primary key default gen_random_uuid(),
  provider_key text not null,
  external_statement_id text not null,
  source_hash text not null,
  currency char(3) not null,
  period_start date not null,
  period_end date not null,
  imported_at timestamptz not null default now(),
  unique (provider_key,external_statement_id)
);
create table royalty_line (
  id uuid primary key default gen_random_uuid(),
  statement_id uuid not null references royalty_statement(id),
  track_id uuid references track(id),
  dsp_key text not null,
  territory_code text,
  usage_type text,
  quantity numeric(20,6),
  gross_amount numeric(20,8) not null,
  raw jsonb not null default '{}'::jsonb
);
-- Immutable accounting: no UPDATE/DELETE by application role. Corrections are new ADJUSTMENT entries.
create table ledger_entry (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references organization(id),
  royalty_line_id uuid references royalty_line(id),
  entry_type text not null,
  currency char(3) not null,
  amount numeric(20,8) not null,
  commission_rule_version text,
  related_entry_id uuid references ledger_entry(id),
  hold_reason text,
  created_at timestamptz not null default now()
);
create table payout_account (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references organization(id),
  provider_key text not null,
  external_token text not null,
  verified boolean not null default false,
  created_at timestamptz not null default now()
);
create table payout (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references organization(id),
  payout_account_id uuid not null references payout_account(id),
  idempotency_key text not null unique,
  currency char(3) not null,
  amount numeric(20,8) not null check (amount > 0),
  status payout_status not null default 'PAYOUT_REQUESTED',
  requested_at timestamptz not null default now(),
  processed_at timestamptz
);
create table risk_case (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references organization(id),
  release_id uuid references release(id),
  payout_id uuid references payout(id),
  kind text not null,
  severity text not null,
  status text not null default 'OPEN',
  evidence jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);
create table audit_event (
  id bigserial primary key,
  actor_user_id uuid references app_user(id),
  organization_id uuid references organization(id),
  action text not null,
  entity_type text not null,
  entity_id text not null,
  before_state jsonb,
  after_state jsonb,
  created_at timestamptz not null default now()
);

create index idx_release_org_status on release(organization_id,status);
create index idx_track_isrc on track(isrc);
create index idx_delivery_request_time on delivery_event(distribution_request_id,happened_at desc);
create index idx_ledger_org_currency_time on ledger_entry(organization_id,currency,created_at);
create index idx_risk_org_status on risk_case(organization_id,status);
