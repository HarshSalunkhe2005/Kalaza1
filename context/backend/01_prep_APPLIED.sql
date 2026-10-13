-- 01_prep.sql — ADDITIVE changes, safe to apply BEFORE the new app reaches the phones.
-- (Applied 2026-10-13 via the Supabase MCP as migration `prep_trusted_clock_indexes_cron_purge`.)

-- 1. The server's clock, for the app's trusted clock (util/TrustedClock.kt).
create or replace function public.server_time() returns timestamptz
language sql stable set search_path = public as $$ select clock_timestamp() $$;
revoke execute on function public.server_time() from public, anon;
grant execute on function public.server_time() to authenticated;

-- 2. A live tick is stamped + window-checked by the server (see 02_switch.sql); a tick that was made
--    offline and replayed later can't be verified, so the app flags it with this column.
alter table public.medications add column if not exists recorded_offline boolean not null default false;

create or replace function public.medications_partial_update_guard() returns trigger
language plpgsql set search_path to 'public' as $$
begin
  if not is_super_admin() then
    perform enforce_allowed_columns(to_jsonb(old), to_jsonb(new), array[
      'status', 'administered_by', 'administered_at',
      'administered_scanned_code', 'recorded_offline',
      'reminder_sent_at', 'due_now_sent_at', 'admin_alert_sent_at', 'superadmin_alert_sent_at'
    ]);
  end if;
  return new;
end;
$$;

-- 3. Indexes for the "last 30 days, newest first" queries the app now makes.
create index if not exists notifications_role_ts_idx   on public.notifications (recipient_role, "timestamp" desc);
create index if not exists notifications_staff_ts_idx  on public.notifications (recipient_staff_id, "timestamp" desc);
create index if not exists audit_log_ts_idx            on public.audit_log ("timestamp" desc);
create index if not exists med_evidence_occurred_idx   on public.medication_evidence_log (occurred_at desc);
create index if not exists med_history_patient_date_idx on public.medication_administration_log (patient_id, date desc);
create index if not exists vitals_patient_date_idx     on public.vitals (patient_id, date desc);
create index if not exists utility_patient_date_idx    on public.utility_records (patient_id, date desc);
create index if not exists visits_patient_date_idx     on public.doctor_visits (patient_id, date desc);
create index if not exists care_notes_patient_ts_idx   on public.care_notes (patient_id, "timestamp" desc);
create index if not exists approvals_ts_idx            on public.approval_requests ("timestamp" desc);

-- 4. pg_cron keeps a row per run forever (3 jobs x 1/min — 135k rows by Oct 2026). Purge daily.
delete from cron.job_run_details where end_time < now() - interval '2 days';
select cron.schedule('cron-log-purge', '15 3 * * *', $$delete from cron.job_run_details where end_time < now() - interval '2 days'$$);
