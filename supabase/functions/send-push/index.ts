// Supabase Edge Function — fires an FCM push whenever a new row lands in the
// `notifications` table. Wired up via a Database Webhook (Dashboard →
// Database → Webhooks → INSERT on `notifications` → this function), not a
// cron job — see the repo's deployment notes for exact setup steps.
//
// Requires one secret, set via `supabase secrets set`:
//   FCM_SERVICE_ACCOUNT_JSON — a Firebase service account JSON (ideally one
//   scoped to just the "Firebase Cloud Messaging API Admin" role, not a
//   broad admin key) for the kalaza-care project, used to mint an OAuth
//   token for the FCM HTTP v1 send API.
//
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are auto-injected by Supabase
// for every Edge Function — no need to set those as secrets.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const serviceAccount = JSON.parse(Deno.env.get("FCM_SERVICE_ACCOUNT_JSON")!);

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const b64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s/g, "");
  const raw = atob(b64);
  const buf = new ArrayBuffer(raw.length);
  const view = new Uint8Array(buf);
  for (let i = 0; i < raw.length; i++) view[i] = raw.charCodeAt(i);
  return buf;
}

function base64UrlEncode(input: string | ArrayBuffer): string {
  const bytes = typeof input === "string"
    ? new TextEncoder().encode(input)
    : new Uint8Array(input);
  let str = "";
  for (const b of bytes) str += String.fromCharCode(b);
  return btoa(str).replace(/=+$/, "").replace(/\+/g, "-").replace(/\//g, "_");
}

async function getFcmAccessToken(): Promise<string> {
  const header = { alg: "RS256", typ: "JWT" };
  const now = Math.floor(Date.now() / 1000);
  const claim = {
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };
  const unsigned = `${base64UrlEncode(JSON.stringify(header))}.${base64UrlEncode(JSON.stringify(claim))}`;

  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(serviceAccount.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned),
  );
  const jwt = `${unsigned}.${base64UrlEncode(signature)}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  const data = await res.json();
  if (!data.access_token) throw new Error(`Failed to mint FCM access token: ${JSON.stringify(data)}`);
  return data.access_token;
}

Deno.serve(async (req) => {
  const payload = await req.json();
  const record = payload.record;
  if (!record) return new Response("no record in payload", { status: 400 });

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  let tokens: string[] = [];
  if (record.recipient_staff_id) {
    const { data } = await supabase
      .from("staff")
      .select("fcm_token")
      .eq("id", record.recipient_staff_id)
      .maybeSingle();
    if (data?.fcm_token) tokens = [data.fcm_token];
  } else if (record.recipient_role) {
    const { data } = await supabase
      .from("staff")
      .select("fcm_token")
      .eq("role", record.recipient_role)
      .eq("is_active", true);
    tokens = (data ?? []).map((r) => r.fcm_token).filter((t): t is string => !!t);
  }

  if (tokens.length === 0) {
    return new Response(JSON.stringify({ sent: 0, reason: "no recipient tokens" }), { status: 200 });
  }

  const accessToken = await getFcmAccessToken();
  const projectId = serviceAccount.project_id;

  let sent = 0;
  for (const token of tokens) {
    const res = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token,
          // Data-only payload — KalazaMessagingService.onMessageReceived
          // always builds and displays the notification itself, consistent
          // across foreground/background/killed states.
          data: {
            title: record.title ?? "",
            message: record.message ?? "",
            targetRoute: record.target_route ?? "",
          },
        },
      }),
    });
    if (res.ok) sent++;
  }

  return new Response(JSON.stringify({ sent, total: tokens.length }), { status: 200 });
});
