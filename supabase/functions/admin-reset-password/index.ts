// Supabase Edge Function — lets a Super Admin set a staff member's password
// (their own included). This is the one password-management action that
// genuinely needs the service_role key: the client SDK's own
// auth.updateUser() can only change the CURRENTLY signed-in user's own
// password, so an admin setting someone else's password has no purely
// client-side path — Supabase's Auth Admin API (admin.updateUserById) is
// the only way, and that API requires service_role.
//
// Invoked from the app via SupabaseClient.functions("admin-reset-password"),
// which automatically attaches the caller's own session JWT as the
// Authorization header — that JWT is what's used below to verify who's
// actually calling, before anything happens with the elevated client.
//
// SUPABASE_URL, SUPABASE_ANON_KEY, and SUPABASE_SERVICE_ROLE_KEY are all
// auto-injected by Supabase for every Edge Function — no secrets to set.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const MIN_PASSWORD_LENGTH = 8;

Deno.serve(async (req) => {
  const authHeader = req.headers.get("Authorization");
  if (!authHeader) {
    return new Response(JSON.stringify({ error: "Missing Authorization header" }), { status: 401 });
  }

  // Scoped to the CALLER's own JWT — used only to find out who's calling.
  // Never used to touch the staff table's data or any Auth account.
  const callerClient = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
    { global: { headers: { Authorization: authHeader } } },
  );
  const { data: { user }, error: userErr } = await callerClient.auth.getUser();
  if (userErr || !user) {
    return new Response(JSON.stringify({ error: "Invalid or expired session" }), { status: 401 });
  }

  const admin = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  // Caller must be an active Super Admin — checked against the real staff
  // table with the elevated client, not trusted from anything the app sent.
  const { data: callerStaff } = await admin
    .from("staff")
    .select("role, is_active")
    .eq("id", user.id)
    .maybeSingle();
  if (!callerStaff || callerStaff.role !== "SUPER_ADMIN" || !callerStaff.is_active) {
    return new Response(JSON.stringify({ error: "Only an active Super Admin can reset passwords" }), { status: 403 });
  }

  let body: { targetStaffId?: string; newPassword?: string };
  try {
    body = await req.json();
  } catch {
    return new Response(JSON.stringify({ error: "Invalid request body" }), { status: 400 });
  }
  const { targetStaffId, newPassword } = body;
  if (!targetStaffId || typeof newPassword !== "string" || newPassword.length < MIN_PASSWORD_LENGTH) {
    return new Response(
      JSON.stringify({ error: `targetStaffId and a password of at least ${MIN_PASSWORD_LENGTH} characters are required` }),
      { status: 400 },
    );
  }

  // staff.id IS the Supabase Auth user id (set that way at staff creation —
  // see StaffRepository.addStaff), so it's usable directly here.
  const { error: updateErr } = await admin.auth.admin.updateUserById(targetStaffId, { password: newPassword });
  if (updateErr) {
    return new Response(JSON.stringify({ error: updateErr.message }), { status: 500 });
  }

  return new Response(JSON.stringify({ success: true }), { status: 200 });
});
