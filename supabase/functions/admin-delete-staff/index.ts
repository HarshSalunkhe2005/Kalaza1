// Supabase Edge Function — Super-Admin-only staff deletion that actually
// cleans up the linked Supabase Auth account, not just the `staff` row.
//
// StaffRepository.deleteStaff() used to only run `delete from staff`, since
// the client SDK's Auth Admin API needs service_role — deleting an
// arbitrary OTHER user's Auth account has no purely client-side path, same
// class of problem as admin-reset-password. Left the auth.users row
// permanently orphaned (email/identity stays taken forever, can't re-add a
// staff member with the same name+generated email combo cleanly).
//
// staff.id IS the Supabase Auth user id (set that way at staff creation —
// see StaffRepository.addStaff), so once the caller's authorized, this can
// delete both in one request: the staff row explicitly, then the Auth user
// (which would cascade-delete the staff row anyway per the staff_id_fkey
// ON DELETE CASCADE constraint — deleting explicitly first just keeps the
// two steps independently visible/debuggable instead of relying only on
// the cascade).
//
// SUPABASE_URL, SUPABASE_ANON_KEY, and SUPABASE_SERVICE_ROLE_KEY are all
// auto-injected by Supabase for every Edge Function — no secrets to set.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

Deno.serve(async (req) => {
  const authHeader = req.headers.get("Authorization");
  if (!authHeader) {
    return new Response(JSON.stringify({ error: "Missing Authorization header" }), { status: 401 });
  }

  // Scoped to the CALLER's own JWT — used only to find out who's calling.
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

  const { data: callerStaff } = await admin
    .from("staff")
    .select("role, is_active")
    .eq("id", user.id)
    .maybeSingle();
  if (!callerStaff || callerStaff.role !== "SUPER_ADMIN" || !callerStaff.is_active) {
    return new Response(JSON.stringify({ error: "Only an active Super Admin can delete staff" }), { status: 403 });
  }

  let body: { targetStaffId?: string };
  try {
    body = await req.json();
  } catch {
    return new Response(JSON.stringify({ error: "Invalid request body" }), { status: 400 });
  }
  const { targetStaffId } = body;
  if (!targetStaffId) {
    return new Response(JSON.stringify({ error: "targetStaffId is required" }), { status: 400 });
  }
  if (targetStaffId === user.id) {
    return new Response(JSON.stringify({ error: "Cannot delete your own account" }), { status: 400 });
  }

  const { error: staffDeleteErr } = await admin.from("staff").delete().eq("id", targetStaffId);
  if (staffDeleteErr) {
    return new Response(JSON.stringify({ error: staffDeleteErr.message }), { status: 500 });
  }

  const { error: authDeleteErr } = await admin.auth.admin.deleteUser(targetStaffId);
  if (authDeleteErr) {
    // Staff row is already gone at this point (the important part for the app's
    // own behavior — a deleted staff row can never log in again regardless).
    // The Auth account failing to delete here is a lesser, still-worth-surfacing
    // problem rather than something to silently swallow.
    return new Response(
      JSON.stringify({ error: `Staff record deleted, but the Auth account could not be removed: ${authDeleteErr.message}` }),
      { status: 500 },
    );
  }

  return new Response(JSON.stringify({ success: true }), { status: 200 });
});
