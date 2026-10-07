// Supabase Edge Function "send-push".
//
// Sends a new Salu-Salo alert to the phones of its account as Firebase Cloud
// Messaging (FCM HTTP v1) data messages; the app shows them as notifications.
// The database's trigger notifications_push calls it through pg_net for every
// new row in notifications, with the alert, the account's Firebase UID and its
// push tokens (at most 5).
//
// - Deploy with JWT verification OFF: the database's call carries no Supabase
//   JWT. A call is accepted only with the header x-push-secret equal to the
//   secret PUSH_WEBHOOK_SECRET (the same value is in Vault for the trigger).
// - It uses a service account whose only role is a custom one with the
//   permission cloudmessaging.messages.create. Its JSON key is the Edge
//   Function secret FIREBASE_PUSH_KEY.
// - It never logs or returns a token, the alert's text, the key, the Google
//   access token or Google's error bodies; logs hold counts and HTTP statuses.
// - Dead tokens are not deleted here (no database access by design); the
//   trigger sends only tokens used in the last 60 days, at most 5 per account.

const FIREBASE_PROJECT_ID = "salusalo";
const OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token";
const FCM_SEND_URL = `https://fcm.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/messages:send`;
const OAUTH_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
export const KEY_SECRET = "FIREBASE_PUSH_KEY";
export const WEBHOOK_SECRET = "PUSH_WEBHOOK_SECRET";
const MAX_TOKENS = 5;
const MAX_BODY_CHARACTERS = 300;

export class SetupError extends Error {}
/** A failed call to Google: only the step and the HTTP status are kept. */
export class GoogleError extends Error {
  constructor(readonly step: string, readonly status: number) {
    super(`${step} failed with HTTP ${status}`);
  }
}

export interface Deps {
  fetch: typeof fetch;
  nowSeconds: () => number;
  /** An Edge Function secret by name, or undefined. */
  secret: (name: string) => string | undefined;
}

const encoder = new TextEncoder();
const RSA = { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" };

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** Compares two strings in a time that depends only on their lengths. */
export function sameSecret(given: string, expected: string): boolean {
  const a = encoder.encode(given);
  const b = encoder.encode(expected);
  let difference = a.length ^ b.length;
  for (let i = 0; i < Math.max(a.length, b.length); i++) difference |= (a[i] ?? 0) ^ (b[i] ?? 0);
  return difference === 0;
}

// ---- Service account (copied from set-firebase-role) -----------------------

interface ServiceAccount {
  clientEmail: string;
  privateKey: string;
}

/** The service account from the secret, or SetupError if it is missing or unusable. */
function readServiceAccount(raw: string | undefined): ServiceAccount {
  if (!raw || raw.trim() === "") throw new SetupError();
  let key: Record<string, unknown>;
  try {
    key = JSON.parse(raw);
  } catch {
    throw new SetupError();
  }
  if (
    key?.type !== "service_account" || key.project_id !== FIREBASE_PROJECT_ID ||
    typeof key.client_email !== "string" || typeof key.private_key !== "string"
  ) {
    throw new SetupError();
  }
  return { clientEmail: key.client_email, privateKey: key.private_key };
}

let accessTokenCache: { email: string; token: string; expiresAt: number } | null = null;

async function googleAccessToken(account: ServiceAccount, deps: Deps): Promise<string> {
  const now = deps.nowSeconds();
  if (accessTokenCache && accessTokenCache.email === account.clientEmail &&
    accessTokenCache.expiresAt - 300 > now) {
    return accessTokenCache.token;
  }
  let signingKey: CryptoKey;
  try {
    const pem = account.privateKey.replace(/-----[A-Z ]+-----/g, "").replace(/\s+/g, "");
    const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
    signingKey = await crypto.subtle.importKey("pkcs8", der, RSA, false, ["sign"]);
  } catch {
    throw new SetupError();
  }
  const header = base64UrlEncode(encoder.encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claims = base64UrlEncode(encoder.encode(JSON.stringify({
    iss: account.clientEmail,
    scope: OAUTH_SCOPE,
    aud: OAUTH_TOKEN_URL,
    iat: now,
    exp: now + 3600,
  })));
  const signature = new Uint8Array(
    await crypto.subtle.sign(RSA, signingKey, encoder.encode(`${header}.${claims}`)),
  );
  const response = await deps.fetch(OAUTH_TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${header}.${claims}.${base64UrlEncode(signature)}`,
    }),
  });
  if (!response.ok) {
    await response.body?.cancel();
    throw new GoogleError("access token", response.status);
  }
  const body = await response.json() as { access_token?: unknown; expires_in?: unknown };
  if (typeof body.access_token !== "string") throw new GoogleError("access token", response.status);
  const lifetime = typeof body.expires_in === "number" ? body.expires_in : 3600;
  accessTokenCache = { email: account.clientEmail, token: body.access_token, expiresAt: now + lifetime };
  return body.access_token;
}

// ---- The push ---------------------------------------------------------------

export interface PushAlert {
  id: string;
  type: string;
  title: string;
  body: string;
  conversation_id: string | null;
  reservation_id: string | null;
  food_post_id: string | null;
}

export interface PushRequest {
  uid: string;
  tokens: string[];
  alert: PushAlert;
}

/** The trigger's request body, or null when it isn't one. */
function readPushRequest(body: unknown): PushRequest | null {
  if (body === null || typeof body !== "object" || Array.isArray(body)) return null;
  const { uid, tokens, alert } = body as Record<string, unknown>;
  if (typeof uid !== "string" || uid === "") return null;
  if (
    !Array.isArray(tokens) || tokens.length === 0 || tokens.length > MAX_TOKENS ||
    !tokens.every((token) => typeof token === "string" && token !== "")
  ) {
    return null;
  }
  if (alert === null || typeof alert !== "object" || Array.isArray(alert)) return null;
  const fields = alert as Record<string, unknown>;
  for (const name of ["id", "type", "title", "body"]) {
    if (typeof fields[name] !== "string") return null;
  }
  const optional = (name: string) => (typeof fields[name] === "string" ? fields[name] as string : null);
  return {
    uid,
    tokens: tokens as string[],
    alert: {
      id: fields.id as string,
      type: fields.type as string,
      title: fields.title as string,
      body: fields.body as string,
      conversation_id: optional("conversation_id"),
      reservation_id: optional("reservation_id"),
      food_post_id: optional("food_post_id"),
    },
  };
}

/** The text cut to 300 characters (code points, so an emoji is never split). */
function shortText(text: string): string {
  const characters = Array.from(text);
  if (characters.length <= MAX_BODY_CHARACTERS) return text;
  return characters.slice(0, MAX_BODY_CHARACTERS - 1).join("") + "…";
}

/**
 * The FCM v1 message for one phone: data only (the app builds the
 * notification), high priority, kept by FCM for a day at most. IDs the alert
 * doesn't have are left out.
 */
export function fcmMessage(token: string, push: PushRequest) {
  const { alert } = push;
  const data: Record<string, string> = {
    alert_id: alert.id,
    type: alert.type,
    title: alert.title,
    body: shortText(alert.body),
    uid: push.uid,
  };
  if (alert.conversation_id) data.conversation_id = alert.conversation_id;
  if (alert.reservation_id) data.reservation_id = alert.reservation_id;
  if (alert.food_post_id) data.food_post_id = alert.food_post_id;
  return { message: { token, data, android: { priority: "HIGH", ttl: "86400s" } } };
}

// ---- Handler ---------------------------------------------------------------

function reply(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

const SETUP_INCOMPLETE = { error: "Server setup incomplete." };

export async function handle(request: Request, deps: Deps): Promise<Response> {
  if (request.method !== "POST") return reply(405, { error: "Use POST." });

  const expected = deps.secret(WEBHOOK_SECRET);
  if (!expected) {
    console.error(`send-push: the ${WEBHOOK_SECRET} secret is missing`);
    return reply(500, SETUP_INCOMPLETE);
  }
  if (!sameSecret(request.headers.get("x-push-secret") ?? "", expected)) {
    console.log("send-push: refused (secret)");
    return reply(401, { error: "Not allowed." });
  }

  let push: PushRequest | null;
  try {
    push = readPushRequest(await request.json());
  } catch {
    push = null;
  }
  if (!push) {
    console.log("send-push: refused (body)");
    return reply(400, { error: "Not a push request." });
  }

  try {
    const account = readServiceAccount(deps.secret(KEY_SECRET));
    const accessToken = await googleAccessToken(account, deps);
    const pushRequest = push;
    const statuses = await Promise.all(pushRequest.tokens.map(async (token) => {
      try {
        const response = await deps.fetch(FCM_SEND_URL, {
          method: "POST",
          headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
          body: JSON.stringify(fcmMessage(token, pushRequest)),
        });
        // Google's answer is never read, logged or returned.
        await response.body?.cancel();
        return response.status;
      } catch {
        return 0;
      }
    }));
    const sent = statuses.filter((status) => status === 200).length;
    const failed = statuses.length - sent;
    const failures = statuses.filter((status) => status !== 200);
    console.log(`send-push: ${sent} sent, ${failed} failed${failed ? ` (HTTP ${failures.join(", ")})` : ""}`);
    return reply(200, { sent, failed });
  } catch (error) {
    if (error instanceof SetupError) {
      console.error(`send-push: the ${KEY_SECRET} secret is missing or invalid`);
      return reply(500, SETUP_INCOMPLETE);
    }
    if (error instanceof GoogleError) {
      console.error(`send-push: ${error.message}`);
      return reply(502, { error: "Could not send." });
    }
    // Only the error's type: its text could contain request details.
    console.error(`send-push: unexpected ${error instanceof Error ? error.name : typeof error}`);
    return reply(500, { error: "Could not send." });
  }
}

const nowSeconds = () => Math.floor(Date.now() / 1000);

export default {
  fetch: (request: Request) =>
    handle(request, { fetch, nowSeconds, secret: (name) => Deno.env.get(name) }),
};
