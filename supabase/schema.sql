-- Kalaza Care — Supabase schema + Row Level Security policies.
-- Run this once, in full, via the Supabase Dashboard → SQL Editor.
-- Mirrors the data model in app/src/main/java/com/kalazacare/app/data/model/Entities.kt
-- and the access rules previously enforced by firestore.rules.

-- ─────────────────────────────────────────────────────────────────────────────
-- Enum types
-- ─────────────────────────────────────────────────────────────────────────────

create type user_role as enum ('SUPER_ADMIN', 'ADMIN', 'STAFF', 'SUPERVISOR');
create type gender_type as enum ('MALE', 'FEMALE', 'OTHER');
create type approval_status as enum ('PENDING', 'APPROVED', 'REJECTED');
create type approval_entity_type as enum ('PATIENT', 'DOCTOR_VISIT', 'VITAL', 'UTILITY', 'CARE_NOTE');
create type approval_action as enum ('EDIT', 'DELETE');
create type med_status as enum ('PENDING', 'ADMINISTERED', 'OVERDUE');
create type allotment_status as enum ('NOT_ALLOTTED', 'ALLOTTED');
create type allotment_request_status as enum ('PENDING', 'FULFILLED');
create type notification_type as enum (
  'APPROVAL_REQUESTED', 'APPROVAL_APPROVED', 'APPROVAL_REJECTED',
  'ALLOTMENT_REQUESTED', 'ALLOTMENT_FULFILLED'
);

-- ─────────────────────────────────────────────────────────────────────────────
-- Tables
-- ─────────────────────────────────────────────────────────────────────────────

-- One row per staff member, keyed by their Supabase Auth user id (mirrors the
-- Firebase Auth UID = Firestore doc id pattern). Login stays "Name + password"
-- client-side: the app looks up auth_email by name, then signs in with it.
create table staff (
  id uuid primary key references auth.users (id) on delete cascade,
  name text not null,
  name_lower text generated always as (lower(name)) stored,
  email text not null default '',
  role user_role not null default 'STAFF',
  phone text not null default '',
  is_active boolean not null default true,
  joined_date date not null default current_date,
  auth_email text not null,
  fcm_token text not null default ''
);
create unique index staff_name_lower_key on staff (name_lower);

create table patients (
  id uuid primary key default gen_random_uuid(),
  name text not null default '',
  age integer not null default 0,
  gender gender_type not null default 'MALE',
  room_no text not null default '',
  medical_history text not null default '',
  current_issues text not null default '',
  allergies text not null default '',
  emergency_contact text not null default '',
  emergency_phone text not null default '',
  admission_date date not null default current_date,
  is_archived boolean not null default false,
  primary_diagnosis text not null default ''
);

create table vitals (
  id uuid primary key default gen_random_uuid(),
  patient_id uuid not null references patients (id) on delete cascade,
  date date not null default current_date,
  time time not null default current_time,
  pulse text not null default '',
  bp text not null default '',
  spo2 text not null default '',
  temperature text not null default '',
  sugar_fasting text not null default '',
  sugar_pp text not null default '',
  signed_by text not null default '',
  created_at timestamptz not null default now()
);

create table medications (
  id uuid primary key default gen_random_uuid(),
  patient_id uuid not null references patients (id) on delete cascade,
  medicine_name text not null default '',
  dose text not null default '',
  quantity text not null default '',
  schedule_time time not null default current_time,
  scheduled_date date not null default current_date,
  status med_status not null default 'PENDING',
  administered_by text not null default '',
  administered_at timestamptz,
  notes text not null default '',
  allotment_status allotment_status not null default 'NOT_ALLOTTED',
  allotted_by_id uuid references staff (id),
  allotted_by_name text not null default '',
  allotted_at timestamptz,
  allotment_photo_url text not null default '',
  allotment_photo_expires_at timestamptz,
  administered_photo_url text not null default '',
  administered_photo_expires_at timestamptz
);

create table allotment_requests (
  id uuid primary key default gen_random_uuid(),
  medication_entry_id uuid not null references medications (id) on delete cascade,
  patient_id uuid not null references patients (id) on delete cascade,
  patient_name text not null default '',
  medicine_name text not null default '',
  dose text not null default '',
  scheduled_time time not null default current_time,
  requested_by_id uuid references staff (id),
  requested_by_name text not null default '',
  status allotment_request_status not null default 'PENDING',
  fulfilled_by_id uuid references staff (id),
  fulfilled_by_name text not null default '',
  timestamp timestamptz not null default now(),
  fulfilled_at timestamptz
);

create table utility_items (
  id uuid primary key default gen_random_uuid(),
  name text not null default '',
  unit text not null default 'pcs',
  display_order integer not null default 0,
  is_active boolean not null default true
);

create table utility_records (
  id uuid primary key default gen_random_uuid(),
  patient_id uuid not null references patients (id) on delete cascade,
  date date not null default current_date,
  time time not null default current_time,
  quantities jsonb not null default '{}'::jsonb,
  issued_to_caregiver text not null default '',
  issued_by_supervisor text not null default '',
  checked_by text not null default '',
  created_at timestamptz not null default now()
);

create table doctor_visits (
  id uuid primary key default gen_random_uuid(),
  patient_id uuid not null references patients (id) on delete cascade,
  doctor_name text not null default '',
  specialty text not null default '',
  date date not null default current_date,
  time time not null default current_time,
  notes text not null default '',
  next_visit_date date,
  prescription_changes text not null default '',
  is_confirmed boolean not null default false,
  is_archived boolean not null default false
);

create table care_notes (
  id uuid primary key default gen_random_uuid(),
  patient_id uuid not null references patients (id) on delete cascade,
  staff_id uuid references staff (id),
  staff_name text not null default '',
  timestamp timestamptz not null default now(),
  note text not null default ''
);

create table approval_requests (
  id uuid primary key default gen_random_uuid(),
  entity_type approval_entity_type not null,
  entity_id text not null default '',
  action approval_action not null default 'EDIT',
  patient_id uuid references patients (id) on delete set null,
  patient_name text not null default '',
  requested_by_id uuid references staff (id),
  requested_by_name text not null default '',
  field_changed text not null default '',
  old_value text not null default '',
  new_value text not null default '',
  status approval_status not null default 'PENDING',
  reviewed_by_id uuid references staff (id),
  reviewed_by_name text not null default '',
  timestamp timestamptz not null default now(),
  reviewed_at timestamptz,
  rejection_reason text not null default ''
);

create table audit_log (
  id uuid primary key default gen_random_uuid(),
  action text not null default '',
  performed_by_id uuid references staff (id),
  performed_by_name text not null default '',
  target_patient_id text not null default '',
  target_patient_name text not null default '',
  details text not null default '',
  timestamp timestamptz not null default now(),
  icon_name text not null default 'edit'
);

create table notifications (
  id uuid primary key default gen_random_uuid(),
  recipient_staff_id uuid references staff (id) on delete cascade,
  recipient_role user_role,
  type notification_type not null,
  title text not null default '',
  message text not null default '',
  timestamp timestamptz not null default now(),
  is_read boolean not null default false,
  target_route text not null default ''
);

-- Helpful indexes for the query patterns the app actually uses.
create index vitals_patient_id_idx on vitals (patient_id);
create index medications_patient_id_idx on medications (patient_id);
create index utility_records_patient_id_idx on utility_records (patient_id);
create index doctor_visits_patient_id_idx on doctor_visits (patient_id);
create index care_notes_patient_id_idx on care_notes (patient_id);
create index notifications_recipient_staff_id_idx on notifications (recipient_staff_id);
create index notifications_recipient_role_idx on notifications (recipient_role);
create index allotment_requests_status_idx on allotment_requests (status);

-- ─────────────────────────────────────────────────────────────────────────────
-- Helper functions (used throughout the RLS policies below)
-- ─────────────────────────────────────────────────────────────────────────────

create function current_staff_role() returns user_role
language sql stable security definer set search_path = public as $$
  select role from staff where id = auth.uid();
$$;

create function is_active_staff() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from staff where id = auth.uid() and is_active);
$$;

create function is_super_admin() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from staff where id = auth.uid() and role = 'SUPER_ADMIN' and is_active);
$$;

create function is_supervisor_or_above() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from staff
    where id = auth.uid() and is_active and role in ('SUPERVISOR', 'SUPER_ADMIN')
  );
$$;

-- Generic guard for "field-restricted partial update" columns: raises unless
-- every column NOT in allowed_cols is unchanged between OLD and NEW. Used by
-- staff.fcm_token self-update, medications allot/administer, doctor_visits
-- confirm, and notifications.is_read.
create function enforce_allowed_columns(old_row jsonb, new_row jsonb, allowed_cols text[])
returns void language plpgsql as $$
declare
  key text;
begin
  for key in select jsonb_object_keys(old_row) loop
    if not (key = any(allowed_cols)) and old_row->key is distinct from new_row->key then
      raise exception 'Column % is not editable in this update', key;
    end if;
  end loop;
end;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- Column-restriction triggers
-- ─────────────────────────────────────────────────────────────────────────────

create function staff_self_update_guard() returns trigger language plpgsql as $$
begin
  if not is_super_admin() then
    perform enforce_allowed_columns(to_jsonb(old), to_jsonb(new), array['fcm_token']);
  end if;
  return new;
end;
$$;
create trigger staff_self_update_guard_trg before update on staff
  for each row execute function staff_self_update_guard();

create function medications_partial_update_guard() returns trigger language plpgsql as $$
begin
  if not is_super_admin() then
    perform enforce_allowed_columns(to_jsonb(old), to_jsonb(new), array[
      'status', 'administered_by', 'administered_at',
      'allotment_status', 'allotted_by_id', 'allotted_by_name', 'allotted_at',
      'allotment_photo_url', 'allotment_photo_expires_at',
      'administered_photo_url', 'administered_photo_expires_at'
    ]);
  end if;
  return new;
end;
$$;
create trigger medications_partial_update_guard_trg before update on medications
  for each row execute function medications_partial_update_guard();

create function doctor_visits_partial_update_guard() returns trigger language plpgsql as $$
begin
  if not is_super_admin() then
    perform enforce_allowed_columns(to_jsonb(old), to_jsonb(new), array['is_confirmed', 'is_archived']);
  end if;
  return new;
end;
$$;
create trigger doctor_visits_partial_update_guard_trg before update on doctor_visits
  for each row execute function doctor_visits_partial_update_guard();

create function notifications_partial_update_guard() returns trigger language plpgsql as $$
begin
  if not is_super_admin() then
    perform enforce_allowed_columns(to_jsonb(old), to_jsonb(new), array['is_read']);
  end if;
  return new;
end;
$$;
create trigger notifications_partial_update_guard_trg before update on notifications
  for each row execute function notifications_partial_update_guard();

-- ─────────────────────────────────────────────────────────────────────────────
-- Row Level Security
-- ─────────────────────────────────────────────────────────────────────────────

alter table staff enable row level security;
alter table patients enable row level security;
alter table vitals enable row level security;
alter table medications enable row level security;
alter table allotment_requests enable row level security;
alter table utility_items enable row level security;
alter table utility_records enable row level security;
alter table doctor_visits enable row level security;
alter table care_notes enable row level security;
alter table approval_requests enable row level security;
alter table audit_log enable row level security;
alter table notifications enable row level security;

-- staff
create policy staff_select on staff for select
  using (auth.uid() = id or is_super_admin());
create policy staff_insert on staff for insert
  with check (is_super_admin());
create policy staff_delete on staff for delete
  using (is_super_admin());
create policy staff_update on staff for update
  using (is_super_admin() or auth.uid() = id);

-- patients — any active staff reads, only Super Admin writes
create policy patients_select on patients for select
  using (is_active_staff());
create policy patients_insert on patients for insert
  with check (is_super_admin());
create policy patients_update on patients for update
  using (is_super_admin());
create policy patients_delete on patients for delete
  using (is_super_admin());

-- vitals / utility_records / care_notes — any active staff read/write, no delete
create policy vitals_select on vitals for select using (is_active_staff());
create policy vitals_insert on vitals for insert with check (is_active_staff());
create policy vitals_update on vitals for update using (is_active_staff());

create policy utility_records_select on utility_records for select using (is_active_staff());
create policy utility_records_insert on utility_records for insert with check (is_active_staff());
create policy utility_records_update on utility_records for update using (is_active_staff());

create policy care_notes_select on care_notes for select using (is_active_staff());
create policy care_notes_insert on care_notes for insert with check (is_active_staff());
create policy care_notes_update on care_notes for update using (is_active_staff());

-- utility_items — read by all active staff, write by Super Admin only
create policy utility_items_select on utility_items for select using (is_active_staff());
create policy utility_items_insert on utility_items for insert with check (is_super_admin());
create policy utility_items_update on utility_items for update using (is_super_admin());
create policy utility_items_delete on utility_items for delete using (is_super_admin());

-- doctor_visits — read all; Super Admin full edit/delete; any active staff may
-- confirm/archive via the partial-update trigger above
create policy doctor_visits_select on doctor_visits for select using (is_active_staff());
create policy doctor_visits_insert on doctor_visits for insert with check (is_super_admin());
create policy doctor_visits_update on doctor_visits for update
  using (is_super_admin() or is_active_staff());
create policy doctor_visits_delete on doctor_visits for delete using (is_super_admin());

-- medications — read all; Super Admin full CRUD; any active staff may
-- allot/administer via the partial-update trigger above
create policy medications_select on medications for select using (is_active_staff());
create policy medications_insert on medications for insert with check (is_super_admin());
create policy medications_update on medications for update
  using (is_super_admin() or is_active_staff());
create policy medications_delete on medications for delete using (is_super_admin());

-- allotment_requests — any active staff may request; Supervisor+ may fulfill
create policy allotment_requests_select on allotment_requests for select using (is_active_staff());
create policy allotment_requests_insert on allotment_requests for insert with check (is_active_staff());
create policy allotment_requests_update on allotment_requests for update using (is_supervisor_or_above());

-- approval_requests — any active staff may raise their own request; only
-- Super Admin reviews (reads the queue, updates status)
create policy approval_requests_select on approval_requests for select using (is_super_admin());
create policy approval_requests_insert on approval_requests for insert
  with check (is_active_staff() and requested_by_id = auth.uid());
create policy approval_requests_update on approval_requests for update using (is_super_admin());

-- audit_log — append-only; any active staff may log an action, only Super
-- Admin reads it; no update/delete policy exists, so both are denied
create policy audit_log_select on audit_log for select using (is_super_admin());
create policy audit_log_insert on audit_log for insert with check (is_active_staff());

-- notifications — scoped to the named recipient or their role; is_read is the
-- only field a recipient may flip themselves (enforced by the trigger above)
create policy notifications_select on notifications for select
  using (
    recipient_staff_id = auth.uid()
    or recipient_role = current_staff_role()
  );
create policy notifications_insert on notifications for insert with check (is_active_staff());
create policy notifications_update on notifications for update
  using (
    recipient_staff_id = auth.uid()
    or recipient_role = current_staff_role()
    or is_super_admin()
  );
