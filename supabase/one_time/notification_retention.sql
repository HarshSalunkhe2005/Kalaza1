-- ONE-TIME script — run once in the Supabase SQL editor.
--
-- Why: the medication/vitals/utilities watchdogs generate dozens of notifications a day and nothing ever
-- removed them (433 rows, 316 unread, after a few weeks). The app now only lists/counts the last 7 days;
-- this adds a nightly job that permanently deletes notifications older than 30 days so the table stays small.
--
-- Safe to re-run: it unschedules any previous copy of the job first.

select cron.unschedule(jobid) from cron.job where jobname = 'notification-retention';

select cron.schedule(
  'notification-retention',
  '30 21 * * *',   -- 21:30 UTC = 03:00 IST, every night
  $$ delete from notifications where timestamp < now() - interval '30 days' $$
);

-- Optional one-off: clear what has already piled up, keeping the last 30 days (the nightly job does this by itself
-- from tonight, so you can skip this line).
-- delete from notifications where timestamp < now() - interval '30 days';
