// Supabase Edge Function — scheduled (not event-driven) watchdog over
// medication deadlines. Runs every minute via pg_cron .
//
// Each dose has its own giving window: from its scheduled time T until T+30 min (DOSE_WINDOW_MINUTES on
// the Kotlin side; the DB trigger in 02_switch_APPLIED.sql enforces the same). Three checkpoints per
// dose not yet given TODAY, each firing at most once per IST day (dedup via the *_sent_at columns on
// `medications`, so a recurring dose's checkpoints reset daily):
//   - T-15 min          -> reminder to STAFF and SUPERVISOR          (reminder_sent_at)
//   - T (0 to +5 min)   -> "due now" to STAFF and SUPERVISOR         (due_now_sent_at)
//   - T+30 (window closes) -> Missed alert to SUPERVISOR and SUPER_ADMIN (incl. Admins), plus the durable
//                             audit_log row and MISSED history row   (superadmin_alert_sent_at)
// Notification roles: ADMIN is delivered as SUPER_ADMIN, so "SUPER_ADMIN" reaches both.
//
// "Given today" means administered_at is today (IST): a recurring dose's row stays ADMINISTERED
// overnight, so filtering on status alone would silence every alert after a dose's first given day.
//
// All times in `medications` (schedule_time) are wall-clock IST (the app is built for a facility in
// Pune), so this function does its date/time math in IST rather than the runtime's UTC clock.
//
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are auto-injected — no secrets need to be set. The schedule
// calls this without credentials (verify_jwt is off): it only reads/writes through its own service role.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const IST_OFFSET_MS = 5.5 * 60 * 60 * 1000;
const WINDOW_MINUTES = 30;

function nowIst(): Date {
  return new Date(Date.now() + IST_OFFSET_MS);
}
function istDateString(d: Date): string {
  return d.toISOString().slice(0, 10);
}
/** True UTC epoch instant corresponding to an IST wall-clock date + time. */
function istDeadlineMs(dateStr: string, timeStr: string): number {
  return Date.parse(`${dateStr}T${timeStr}Z`) - IST_OFFSET_MS;
}
function sentToday(sentAtIso: string | null, todayIst: string): boolean {
  if (!sentAtIso) return false;
  return istDateString(new Date(new Date(sentAtIso).getTime() + IST_OFFSET_MS)) === todayIst;
}
/** ISO day-of-week (1=Mon..7=Sun) for an IST-shifted Date, matching MedicationEntry.recurringDays' convention on the Kotlin side. */
function isoDayOfWeek(istShiftedDate: Date): number {
  const jsDay = istShiftedDate.getUTCDay(); // 0=Sun..6=Sat
  return jsDay === 0 ? 7 : jsDay;
}
/** Empty/blank means every day, same as the Kotlin app's own MedicationEntry.recurringDays default. */
function parseRecurringDays(csv: string | null): number[] {
  if (!csv) return [];
  return csv.split(",").map((s) => parseInt(s.trim(), 10)).filter((n) => !isNaN(n));
}

/**
 * Writes the dedup timestamp BEFORE the notification goes out, and only proceeds if the write succeeded —
 * if it silently fails (e.g. a DB guard rejecting the column), the alert would otherwise repeat every
 * minute of its window instead of firing once.
 */
// deno-lint-ignore no-explicit-any
async function claim(supabase: any, id: string, col: string): Promise<boolean> {
  const { error } = await supabase.from("medications").update({ [col]: new Date().toISOString() }).eq("id", id);
  if (error) {
    console.error(`dedup write failed for medications.${col} (${id}): ${error.message}`);
    return false;
  }
  return true;
}

Deno.serve(async () => {
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  const { data: meds, error } = await supabase
    .from("medications")
    .select("id, patient_id, medicine_name, dose, tag, schedule_time, scheduled_date, is_recurring, recurring_days, status, administered_at, created_at, reminder_sent_at, due_now_sent_at, superadmin_alert_sent_at, patients(name)");
  if (error) return new Response(JSON.stringify({ error: error.message }), { status: 500 });

  const nowIstDate = nowIst();
  const today = istDateString(nowIstDate);
  const todayIsoDay = isoDayOfWeek(nowIstDate);
  const nowMs = Date.now();

  let reminders = 0, dueNow = 0, escalations = 0;

  for (const med of meds ?? []) {
    const effectiveDate = med.is_recurring ? today : med.scheduled_date;
    if (effectiveDate !== today) continue; // one-off dose not due today

    // A recurring dose restricted to specific weekdays (recurring_days
    // non-empty) shouldn't be nagged about on days it's not actually due —
    // matches MedicationRepository's own isDueOn logic on the Kotlin side.
    if (med.is_recurring) {
      const recurringDays = parseRecurringDays(med.recurring_days);
      if (recurringDays.length > 0 && !recurringDays.includes(todayIsoDay)) continue;
    }

    // The row's status is never reset overnight, so a recurring dose given on any earlier day stays
    // ADMINISTERED forever. Filtering on status alone silenced every alert after a dose's first given day
    // (reminders and Missed stopped for all recurring doses). "Given" must mean given TODAY; a one-off
    // dose that was given is done for good. Mirrors the app's own withComputedStatus daily reset.
    if (med.status === "ADMINISTERED") {
      const givenDay = med.administered_at ? istDateString(new Date(Date.parse(med.administered_at) + IST_OFFSET_MS)) : "";
      if (!med.is_recurring || givenDay === effectiveDate) continue;
    }

    const deadlineMs = istDeadlineMs(effectiveDate, med.schedule_time);
    const windowEndMs = deadlineMs + WINDOW_MINUTES * 60_000;
    // A dose entered after today's giving window had already closed (e.g. an 8 AM dose added at 6 PM)
    // was never actually due today — don't fire alerts, audit rows or a MISSED history row for it.
    if (med.created_at && Date.parse(med.created_at) > windowEndMs) continue;
    const diffMinutes = (nowMs - deadlineMs) / 60_000;
    const patientName = (med as unknown as { patients: { name: string } | null }).patients?.name ?? "Unknown patient";

    if (diffMinutes >= -15 && diffMinutes < 0 && !sentToday(med.reminder_sent_at, today)) {
      if (!(await claim(supabase, med.id, "reminder_sent_at"))) continue;
      await supabase.from("notifications").insert(
        ["STAFF", "SUPERVISOR"].map((role) => ({
          recipient_role: role, type: "MEDICATION_REMINDER",
          title: "Dose due soon", message: `${med.medicine_name} for ${patientName} is due in 15 minutes`,
          target_route: `patient/${med.patient_id}?tab=2`,
        })),
      );
      reminders++;
    }

    if (diffMinutes >= 0 && diffMinutes < 5 && !sentToday(med.due_now_sent_at, today)) {
      if (!(await claim(supabase, med.id, "due_now_sent_at"))) continue;
      await supabase.from("notifications").insert(
        ["STAFF", "SUPERVISOR"].map((role) => ({
          recipient_role: role, type: "MEDICATION_REMINDER",
          title: "Dose due now", message: `${med.medicine_name} for ${patientName} is due now`,
          target_route: `patient/${med.patient_id}?tab=2`,
        })),
      );
      dueNow++;
    }

    // Once the dose's window has closed it is missed: Supervisor and Super Admin/Admin are both told.
    // Both share superadmin_alert_sent_at for dedupe. Not retroactively: if the window closed over 2h ago
    // (this check was down, or just deployed), a "missed" ping now would only be late noise.
    if (nowMs >= windowEndMs && nowMs - windowEndMs <= 2 * 60 * 60_000 && !sentToday(med.superadmin_alert_sent_at, today)) {
      if (!(await claim(supabase, med.id, "superadmin_alert_sent_at"))) continue;
      await supabase.from("notifications").insert([
        {
          recipient_role: "SUPERVISOR", type: "MEDICATION_MISSED_ALERT",
          title: "Missed dose", message: `${med.medicine_name} for ${patientName} was missed (window closed)`,
          target_route: `patient/${med.patient_id}?tab=2`,
        },
        {
          recipient_role: "SUPER_ADMIN", type: "MEDICATION_MISSED_ESCALATION",
          title: "Missed dose", message: `${med.medicine_name} for ${patientName} was missed (window closed)`,
          target_route: `patient/${med.patient_id}?tab=2`,
        },
      ]);
      // Durable trail: the window has closed without the dose being given.
      await supabase.from("audit_log").insert({
        action: "Dose Missed",
        target_patient_id: med.patient_id,
        target_patient_name: patientName,
        details: `${med.medicine_name} was not administered within its scheduled window`,
        icon_name: "cancel",
      });
      // Per-day history ledger row; a race with markAdministered() is harmless (unique on medication_id,date).
      await supabase.from("medication_administration_log").insert({
        medication_id: med.id,
        patient_id: med.patient_id,
        medicine_name: med.medicine_name,
        dose: med.dose,
        tag: med.tag,
        date: effectiveDate,
        status: "MISSED",
      }, { onConflict: "medication_id,date", ignoreDuplicates: true });
      escalations++;
    }
  }

  return new Response(JSON.stringify({ reminders, dueNow, escalations }), { status: 200 });
});
