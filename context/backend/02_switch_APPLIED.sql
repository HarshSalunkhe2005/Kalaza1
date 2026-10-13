-- 02_switch.sql — PART A was APPLIED on 2026-10-13 (migration `switch_30min_window_rules`), after the
-- medication-watchdog v11 deploy (medication-watchdog_DEPLOYED_v11.ts). Phones still on an older app are
-- told 'Outside the 30-minute giving window' if they tick after T+30. PART B (the wipe) was APPLIED later the same day.

-- ───────────── PART A: new rules ─────────────

-- 1. The server stamps and window-checks every LIVE tick. A tick replayed from an offline queue carries the
--    flag recorded_offline = true: the server can't verify its time, so it keeps the phone-recorded one.
--    The Admin override (is_super_admin(), which includes Admins) may fall outside the window.
--    Window = [T, T+30 min) in IST, with the after-midnight part of a window that crosses midnight counted
--    against the previous day's T — the same rule as ScanScreen.eligibility().
create or replace function public.medications_window_guard() returns trigger
language plpgsql set search_path = public as $$
declare
  v_now_min int;
  v_due_min int;
  v_since   int;
begin
  -- A "tick" = becoming ADMINISTERED, or a fresh administered_at (a recurring dose given again on a new day).
  -- The >1s test keeps an unrelated edit of an already-given dose from being mistaken for one.
  if new.status = 'ADMINISTERED' and (
       old.status is distinct from 'ADMINISTERED'
       or old.administered_at is null
       or abs(extract(epoch from (new.administered_at - old.administered_at))) > 1
     ) then
    -- Offline replay: flagged AND the recorded time is clearly in the past. The flag alone is not enough
    -- (an older app never clears it on later live ticks).
    if coalesce(new.recorded_offline, false) and new.administered_at < clock_timestamp() - interval '1 minute' then
      return new;
    end if;
    new.administered_at := clock_timestamp();
    new.recorded_offline := false;
    if not is_super_admin() then
      v_now_min := extract(hour from (clock_timestamp() at time zone 'Asia/Kolkata'))::int * 60
                 + extract(minute from (clock_timestamp() at time zone 'Asia/Kolkata'))::int;
      v_due_min := extract(hour from new.schedule_time)::int * 60 + extract(minute from new.schedule_time)::int;
      v_since := v_now_min - v_due_min;
      if v_since < 0 and v_due_min + 30 > 1440 and v_now_min < v_due_min + 30 - 1440 then
        v_since := v_since + 1440;
      end if;
      if v_since < 0 or v_since >= 30 then
        raise exception 'Outside the 30-minute giving window' using errcode = 'P0001';
      end if;
    end if;
  end if;
  return new;
end;
$$;
revoke execute on function public.medications_window_guard() from public, anon, authenticated;
-- Runs after medications_partial_update_guard_trg (BEFORE triggers fire in name order).
drop trigger if exists medications_window_guard_trg on public.medications;
create trigger medications_window_guard_trg before update on public.medications
  for each row execute function public.medications_window_guard();

-- 2. The admin "not given yet" heads-up is gone, so its dedup column is dead.
alter table public.medications drop column if exists admin_alert_sent_at;
create or replace function public.medications_partial_update_guard() returns trigger
language plpgsql set search_path to 'public' as $$
begin
  if not is_super_admin() then
    perform enforce_allowed_columns(to_jsonb(old), to_jsonb(new), array[
      'status', 'administered_by', 'administered_at',
      'administered_scanned_code', 'recorded_offline',
      'reminder_sent_at', 'due_now_sent_at', 'superadmin_alert_sent_at'
    ]);
  end if;
  return new;
end;
$$;

-- 3. Retention: notifications keep 14 days (was 30).
select cron.schedule('notification-retention', '30 21 * * *',
  $$delete from public.notifications where "timestamp" < now() - interval '14 days'$$);

-- 4. The watchdogs don't verify a JWT (verify_jwt = false), so the schedule no longer carries a
--    service-role key in its command text.
select cron.schedule('medication-watchdog', '* * * * *',
  $$select net.http_post(url := 'https://acafqfjpbilakvnaxamb.supabase.co/functions/v1/medication-watchdog')$$);
select cron.schedule('daily-task-watchdog', '* * * * *',
  $$select net.http_post(url := 'https://acafqfjpbilakvnaxamb.supabase.co/functions/v1/daily-task-watchdog')$$);

-- ───────────── PART B: fresh start (IRREVERSIBLE — only on explicit go-ahead) ─────────────
-- Clears the alert feed, the audit log and the QR-scan evidence log. The per-day dose history table
-- (medication_administration_log) is kept.
-- delete from public.notifications;
-- delete from public.audit_log;
-- delete from public.medication_evidence_log;
-- insert into public.audit_log (action, details, icon_name)
--   values ('Records reset', 'Alerts, audit log and QR evidence log cleared for go-live', 'archive');
