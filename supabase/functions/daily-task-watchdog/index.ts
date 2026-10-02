// Supabase Edge Function — scheduled (every minute via pg_cron) watchdog over the two
// once-a-day-per-patient tasks that aren't tied to an individual medication's own schedule:
// Vitals (AM deadline) and Utilities (PM deadline). Separate from medication-watchdog
// because these are "has this been done for this patient today" checks, not per-dose.
//
// Two checkpoints per task, each firing at most once per IST calendar day per patient
// (tracked via the *_alert_sent_at / *_escalation_sent_at timestamp columns on `patients`):
//   Vitals:    09:00 IST -> "not recorded yet" to SUPERVISOR
//              09:30 IST -> "still not recorded" to SUPER_ADMIN (incl. Admins)
//   Utilities: 21:30 IST -> "not logged yet" to SUPERVISOR
//              22:00 IST -> "still not logged" to SUPER_ADMIN (incl. Admins)
//
// A checkpoint is skipped for a patient who was added AFTER that checkpoint's time today (see
// `createdAfter`) — otherwise a patient admitted at 3 PM would get an instant "not recorded" alert
// (and escalation) for a 9 AM deadline they never had.
//
// All times are wall-clock IST (the app is built for a facility in Pune), so this function
// does its date/time math in IST rather than the Edge Function runtime's UTC clock.
//
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are auto-injected — no secrets need to be set.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const IST_OFFSET_MS = 5.5 * 60 * 60 * 1000;

function nowIst(): Date {
  return new Date(Date.now() + IST_OFFSET_MS);
}
function istDateString(d: Date): string {
  return d.toISOString().slice(0, 10);
}
function sentToday(sentAtIso: string | null, todayIst: string): boolean {
  if (!sentAtIso) return false;
  return istDateString(new Date(new Date(sentAtIso).getTime() + IST_OFFSET_MS)) === todayIst;
}
/** Minutes elapsed in the IST day so far, e.g. 09:30 -> 570. */
function minutesIntoDay(d: Date): number {
  return d.getUTCHours() * 60 + d.getUTCMinutes();
}

/** True if the patient record was created after today's IST checkpoint at [minuteOfDay] (so it never applied to them today). */
function createdAfter(createdAtIso: string | null, todayIst: string, minuteOfDay: number): boolean {
  if (!createdAtIso) return false;
  const checkpointMs = Date.parse(`${todayIst}T00:00:00Z`) - IST_OFFSET_MS + minuteOfDay * 60_000;
  return Date.parse(createdAtIso) > checkpointMs;
}

// deno-lint-ignore no-explicit-any
async function claim(supabase: any, id: string, col: string): Promise<boolean> {
  const { error } = await supabase.from("patients").update({ [col]: new Date().toISOString() }).eq("id", id);
  if (error) {
    console.error(`dedup write failed for patients.${col} (${id}): ${error.message}`);
    return false;
  }
  return true;
}

const VITALS_ALERT_MIN = 9 * 60;        // 09:00
const VITALS_ESCALATION_MIN = 9 * 60 + 30; // 09:30
const UTILITY_ALERT_MIN = 21 * 60 + 30;    // 21:30
const UTILITY_ESCALATION_MIN = 22 * 60;    // 22:00

Deno.serve(async () => {
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  const nowIstDate = nowIst();
  const today = istDateString(nowIstDate);
  const minuteOfDay = minutesIntoDay(nowIstDate);

  // Nothing to do before the earliest checkpoint of the day.
  if (minuteOfDay < VITALS_ALERT_MIN) {
    return new Response(JSON.stringify({ skipped: "before first checkpoint" }), { status: 200 });
  }

  const { data: patients, error } = await supabase
    .from("patients")
    .select("id, name, created_at, vitals_alert_sent_at, vitals_escalation_sent_at, utility_alert_sent_at, utility_escalation_sent_at")
    .eq("is_archived", false);
  if (error) return new Response(JSON.stringify({ error: error.message }), { status: 500 });
  if (!patients || patients.length === 0) return new Response(JSON.stringify({ vitalsAlerts: 0, vitalsEscalations: 0, utilityAlerts: 0, utilityEscalations: 0 }), { status: 200 });

  const { data: vitalsToday } = await supabase.from("vitals").select("patient_id").eq("date", today);
  const { data: utilityToday } = await supabase.from("utility_records").select("patient_id").eq("date", today);
  const vitalsDone = new Set((vitalsToday ?? []).map((r) => r.patient_id));
  const utilityDone = new Set((utilityToday ?? []).map((r) => r.patient_id));

  let vitalsAlerts = 0, vitalsEscalations = 0, utilityAlerts = 0, utilityEscalations = 0;

  for (const p of patients) {
    if (!vitalsDone.has(p.id)) {
      if (minuteOfDay >= VITALS_ALERT_MIN && !sentToday(p.vitals_alert_sent_at, today) && !createdAfter(p.created_at, today, VITALS_ALERT_MIN)) {
        if (!(await claim(supabase, p.id, "vitals_alert_sent_at"))) continue;
        await supabase.from("notifications").insert({
          recipient_role: "SUPERVISOR", type: "MEDICATION_REMINDER",
          title: "Vitals not recorded yet", message: `Today's vitals for ${p.name} haven't been recorded yet`,
          target_route: `patient/${p.id}?tab=1`,
        });
        vitalsAlerts++;
      }
      if (minuteOfDay >= VITALS_ESCALATION_MIN && !sentToday(p.vitals_escalation_sent_at, today) && !createdAfter(p.created_at, today, VITALS_ESCALATION_MIN)) {
        if (!(await claim(supabase, p.id, "vitals_escalation_sent_at"))) continue;
        await supabase.from("notifications").insert({
          recipient_role: "SUPER_ADMIN", type: "MEDICATION_MISSED_ALERT",
          title: "Vitals still not recorded", message: `Today's vitals for ${p.name} are still not recorded`,
          target_route: `patient/${p.id}?tab=1`,
        });
        vitalsEscalations++;
      }
    }
    if (!utilityDone.has(p.id)) {
      if (minuteOfDay >= UTILITY_ALERT_MIN && !sentToday(p.utility_alert_sent_at, today) && !createdAfter(p.created_at, today, UTILITY_ALERT_MIN)) {
        if (!(await claim(supabase, p.id, "utility_alert_sent_at"))) continue;
        await supabase.from("notifications").insert({
          recipient_role: "SUPERVISOR", type: "MEDICATION_REMINDER",
          title: "Utilities not logged yet", message: `Today's utilities for ${p.name} haven't been logged yet`,
          target_route: `patient/${p.id}?tab=3`,
        });
        utilityAlerts++;
      }
      if (minuteOfDay >= UTILITY_ESCALATION_MIN && !sentToday(p.utility_escalation_sent_at, today) && !createdAfter(p.created_at, today, UTILITY_ESCALATION_MIN)) {
        if (!(await claim(supabase, p.id, "utility_escalation_sent_at"))) continue;
        await supabase.from("notifications").insert({
          recipient_role: "SUPER_ADMIN", type: "MEDICATION_MISSED_ALERT",
          title: "Utilities still not logged", message: `Today's utilities for ${p.name} are still not logged`,
          target_route: `patient/${p.id}?tab=3`,
        });
        utilityEscalations++;
      }
    }
  }

  return new Response(JSON.stringify({ vitalsAlerts, vitalsEscalations, utilityAlerts, utilityEscalations }), { status: 200 });
});
