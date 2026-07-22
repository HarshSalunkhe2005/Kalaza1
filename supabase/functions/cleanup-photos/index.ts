// Supabase Edge Function — deletes evidence photos older than the 48h
// retention policy from the `evidence` Storage bucket. Meant to run on a
// schedule (Supabase Dashboard → Edge Functions → cleanup-photos → Cron, or
// Integrations → Cron Jobs), not triggered by any table event — see the
// repo's deployment notes for exact setup steps.
//
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are auto-injected by Supabase
// for every Edge Function — no secrets need to be set for this one.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const RETENTION_HOURS = 48;
const BUCKET = "evidence";

Deno.serve(async () => {
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  const { data: files, error } = await supabase.storage.from(BUCKET).list();
  if (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500 });
  }

  const cutoff = Date.now() - RETENTION_HOURS * 3600 * 1000;
  const stale = (files ?? [])
    .filter((f) => {
      const created = f.created_at ? new Date(f.created_at).getTime() : 0;
      return created > 0 && created < cutoff;
    })
    .map((f) => f.name);

  if (stale.length > 0) {
    const { error: removeError } = await supabase.storage.from(BUCKET).remove(stale);
    if (removeError) {
      return new Response(JSON.stringify({ error: removeError.message }), { status: 500 });
    }
  }

  return new Response(JSON.stringify({ deleted: stale.length, names: stale }), { status: 200 });
});
