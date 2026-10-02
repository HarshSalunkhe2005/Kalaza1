-- ONE-TIME script — run once in the Supabase SQL editor, right when you install the new app build.
--
-- Why: older app builds wrote local IST wall-clock strings with no timezone offset into timestamptz columns,
-- which Postgres stored as if they were UTC (5h30m too late). The new build writes true instants and
-- converts to the device's timezone when reading, so legacy app-written rows must be shifted back 5h30m.
-- Rows written by Edge Functions / now() defaults were already correct and are left alone
-- ('Dose Missed' audit rows, MEDICATION_* notifications).
--
-- Reversible: adding interval '5 hours 30 minutes' back undoes it. Do NOT run it twice.

begin;

alter table medications disable trigger medications_partial_update_guard_trg;
alter table notifications disable trigger notifications_partial_update_guard_trg;

update approval_requests
   set timestamp = timestamp - interval '5 hours 30 minutes',
       reviewed_at = reviewed_at - interval '5 hours 30 minutes';

update audit_log
   set timestamp = timestamp - interval '5 hours 30 minutes'
 where action <> 'Dose Missed';

update notifications
   set timestamp = timestamp - interval '5 hours 30 minutes'
 where type not in ('MEDICATION_REMINDER', 'MEDICATION_MISSED_ALERT', 'MEDICATION_MISSED_ESCALATION');

update care_notes
   set timestamp = timestamp - interval '5 hours 30 minutes';

update medications
   set administered_at = administered_at - interval '5 hours 30 minutes'
 where administered_at is not null;

update medication_evidence_log
   set occurred_at = occurred_at - interval '5 hours 30 minutes';

update medication_administration_log
   set administered_at = administered_at - interval '5 hours 30 minutes'
 where administered_at is not null;

alter table medications enable trigger medications_partial_update_guard_trg;
alter table notifications enable trigger notifications_partial_update_guard_trg;

commit;
