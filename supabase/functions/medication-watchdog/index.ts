// Supabase Edge Function — scheduled (not event-driven) watchdog over
// medication deadlines. Meant to run every minute via Database → Cron Jobs
// (or pg_cron), unlike send-push/cleanup-photos which are triggered by a
// webhook/its own schedule respectively for different reasons.
//
// Three checkpoints per still-not-ADMINISTERED dose, each firing at most
// once per day per dose (tracked via the *_sent_at timestamp columns added
// to `medications`, compared by IST calendar date so a recurring dose's
// checkpoints reset every day):
//   - 15 min before the deadline  -> reminder to STAFF and SUPERVISOR
//   - 5 min after the deadline    -> alert to SUPERVISOR
//   - 10 min after the deadline   -> escalation to SUPER_ADMIN
//
// The 5-min tier used to target the restricted photo-audit-only ADMIN role;
// that role was removed from the app entirely (only SUPER_ADMIN/STAFF/
// SUPERVISOR remain), so it was silently notifying nobody — the insert
// succeeded (Postgres never dropped the old enum label) but no staff row
// has had that role in a long time, so no in-app notification and no push
// ever reached anyone. Retargeted to SUPERVISOR, the closest existing role
// to the original mid-tier-escalation intent.
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

Deno.serve(async () => {
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  const { data: meds, error } = await supabase
    .from("medications")
    .select("id, patient_id, medicine_name, schedule_time, scheduled_date, is_recurring, recurring_days, status, reminder_sent_at, admin_alert_sent_at, superadmin_alert_sent_at, patients(name)")
    .in("status", ["PENDING", "OVERDUE"]);
  if (error) return new Response(JSON.stringify({ error: error.message }), { status: 500 });

  const nowIstDate = nowIst();
  const today = istDateString(nowIstDate);
  const todayIsoDay = isoDayOfWeek(nowIstDate);
  const nowMs = Date.now();

  let reminders = 0, adminAlerts = 0, escalations = 0;

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
    const diffMinutes = (nowMs - deadlineMs) / 60_000;
    const patientName = (med as unknown as { patients: { name: string } | null }).patients?.name ?? "Unknown patient";

    if (diffMinutes >= -15 && diffMinutes < 0 && !sentToday(med.reminder_sent_at, today)) {
      await supabase.from("notifications").insert([
        {
          recipient_role: "STAFF", type: "MEDICATION_REMINDER",
          title: "Dose due soon", message: `${med.medicine_name} for ${patientName} is due shortly`,
          target_route: `patient/${med.patient_id}`,
        },
        {
          recipient_role: "SUPERVISOR", type: "MEDICATION_REMINDER",
          title: "Dose due soon", message: `${med.medicine_name} for ${patientName} is due shortly`,
          target_route: `patient/${med.patient_id}`,
        },
      ]);
      await supabase.from("medications").update({ reminder_sent_at: new Date().toISOString() }).eq("id", med.id);
      reminders++;
    }

    if (diffMinutes >= 5 && !sentToday(med.admin_alert_sent_at, today)) {
      await supabase.from("notifications").insert({
        recipient_role: "SUPERVISOR", type: "MEDICATION_MISSED_ALERT",
        title: "Missed dose", message: `${med.medicine_name} for ${patientName} was not given on time`,
        target_route: `patient/${med.patient_id}`,
      });
      await supabase.from("medications").update({ admin_alert_sent_at: new Date().toISOString() }).eq("id", med.id);
      adminAlerts++;
    }

    if (diffMinutes >= 10 && !sentToday(med.superadmin_alert_sent_at, today)) {
      await supabase.from("notifications").insert({
        recipient_role: "SUPER_ADMIN", type: "MEDICATION_MISSED_ESCALATION",
        title: "Escalation: missed dose", message: `${med.medicine_name} for ${patientName} is still not given (10+ min overdue)`,
        target_route: `patient/${med.patient_id}`,
      });
      await supabase.from("medications").update({ superadmin_alert_sent_at: new Date().toISOString() }).eq("id", med.id);
      escalations++;
    }
  }

  return new Response(JSON.stringify({ reminders, adminAlerts, escalations }), { status: 200 });
});
