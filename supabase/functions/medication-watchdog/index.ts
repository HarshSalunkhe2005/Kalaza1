// Supabase Edge Function — scheduled (not event-driven) watchdog over
// medication deadlines. Meant to run every minute via Database → Cron Jobs
// (or pg_cron), unlike send-push/cleanup-photos which are triggered by a
// webhook/its own schedule respectively for different reasons.
//
// Four checkpoints per still-not-ADMINISTERED dose, each firing at most once
// per day per dose (tracked via the *_sent_at timestamp columns on
// `medications`, compared by IST calendar date so a recurring dose's
// checkpoints reset every day). The 1-hour giving window (scheduled time to
// +60 min, see DOSE_WINDOW_MINUTES on the Kotlin side) is unchanged by any of
// this — these are early heads-up alerts, not the dose's actual status:
//   - 15 min before  -> reminder to STAFF
//   - at the dose time (0 to +5 min)   -> "due now" to SUPERVISOR
//   - 15 min after   -> "not given yet" heads-up to SUPER_ADMIN (incl. Admins)
//   - 65 min after (window closed +5)  -> actual Missed alert to SUPERVISOR
//     and SUPER_ADMIN, plus the durable audit/history record
//
// All times in `medications` (schedule_time) are wall-clock IST (the app is
// built for a facility in Pune), so this function does its date/time math
// in IST rather than the Edge Function runtime's UTC clock.
//
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are auto-injected — no secrets
// need to be set for this one.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const IST_OFFSET_MS = 5.5 * 60 * 60 * 1000;

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
    .select("id, patient_id, medicine_name, dose, tag, schedule_time, scheduled_date, is_recurring, recurring_days, status, created_at, reminder_sent_at, due_now_sent_at, admin_alert_sent_at, superadmin_alert_sent_at, patients(name)")
    .in("status", ["PENDING", "OVERDUE"]);
  if (error) return new Response(JSON.stringify({ error: error.message }), { status: 500 });

  const nowIstDate = nowIst();
  const today = istDateString(nowIstDate);
  const todayIsoDay = isoDayOfWeek(nowIstDate);
  const nowMs = Date.now();

  let reminders = 0, dueNow = 0, adminAlerts = 0, escalations = 0;

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

    const deadlineMs = istDeadlineMs(effectiveDate, med.schedule_time);
    // A dose entered after today's giving window had already closed (e.g. an 8 AM dose added at 6 PM)
    // was never actually due today — don't fire alerts, audit rows or a MISSED history row for it.
    if (med.created_at && Date.parse(med.created_at) > deadlineMs + 60 * 60_000) continue;
    const diffMinutes = (nowMs - deadlineMs) / 60_000;
    const patientName = (med as unknown as { patients: { name: string } | null }).patients?.name ?? "Unknown patient";

    if (diffMinutes >= -15 && diffMinutes < 0 && !sentToday(med.reminder_sent_at, today)) {
      if (!(await claim(supabase, med.id, "reminder_sent_at"))) continue;
      await supabase.from("notifications").insert({
        recipient_role: "STAFF", type: "MEDICATION_REMINDER",
        title: "Dose due soon", message: `${med.medicine_name} for ${patientName} is due shortly`,
        target_route: `patient/${med.patient_id}?tab=2`,
      });
      reminders++;
    }

    if (diffMinutes >= 0 && diffMinutes < 5 && !sentToday(med.due_now_sent_at, today)) {
      if (!(await claim(supabase, med.id, "due_now_sent_at"))) continue;
      await supabase.from("notifications").insert({
        recipient_role: "SUPERVISOR", type: "MEDICATION_REMINDER",
        title: "Dose due now", message: `${med.medicine_name} for ${patientName} is due now`,
        target_route: `patient/${med.patient_id}?tab=2`,
      });
      dueNow++;
    }

    // Window is scheduled time -> +60 min, so this +15 alert is a heads-up, not the
    // actual Missed status (that's the +65 checkpoint below, once the window closes).
    if (diffMinutes >= 15 && diffMinutes < 65 && !sentToday(med.admin_alert_sent_at, today)) {
      if (!(await claim(supabase, med.id, "admin_alert_sent_at"))) continue;
      await supabase.from("notifications").insert({
        recipient_role: "SUPER_ADMIN", type: "MEDICATION_MISSED_ALERT",
        title: "Dose not given yet", message: `${med.medicine_name} for ${patientName} has not been given yet`,
        target_route: `patient/${med.patient_id}?tab=2`,
      });
      adminAlerts++;
    }

    // At +65 (window closed + 5) the dose is missed: Supervisor and Super Admin
    // are both told. Both share superadmin_alert_sent_at for dedupe.
    if (diffMinutes >= 65 && !sentToday(med.superadmin_alert_sent_at, today)) {
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

  return new Response(JSON.stringify({ reminders, dueNow, adminAlerts, escalations }), { status: 200 });
});
