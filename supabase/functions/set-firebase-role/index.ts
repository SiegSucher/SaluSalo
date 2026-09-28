// Supabase Edge Function "set-firebase-role".
//
// Gives the calling Firebase account of project "salusalo" the custom claim
// role: "authenticated", which Supabase Third-Party Auth and
// is_salusalo_firebase_jwt() require. The app calls it once, when a signed-in
// account's ID token lacks the claim, then refreshes the token.
//
// - The caller is known only from its own Firebase ID token, verified here.
//   Deploy with JWT verification OFF: the Supabase gateway can't verify
//   Firebase tokens.
// - It sets only role: "authenticated", only on the caller's own account, and
//   keeps every other custom claim. Anonymous accounts are refused.
// - It uses a service account whose only role is a custom one with the
//   permissions firebaseauth.users.get and firebaseauth.users.update. Its JSON
//   key is the Edge Function secret FIREBASE_AUTH_ADMIN_KEY.
// - It never logs or returns the ID token, the key, the Google access token or
//   Google's error bodies. Logs name at most the first 6 characters of the UID,
//   and a refusal only its reason code.

const FIREBASE_PROJECT_ID = "salusalo";
const ISSUER = `https://securetoken.google.com/${FIREBASE_PROJECT_ID}`;
const GOOGLE_KEYS_URL =
  "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
const OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token";
const ACCOUNTS_URL =
  `https://identitytoolkit.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/accounts`;
// The only scope Google lists for accounts:lookup and accounts:update. What
// the token can do is limited by the service account's single IAM role.
const OAUTH_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
export const SECRET_NAME = "FIREBASE_AUTH_ADMIN_KEY";
const ROLE = "authenticated";
const CLOCK_SKEW_SECONDS = 60;
const MAX_TOKEN_LENGTH = 4096;
// A key ID Google doesn't list makes the keys be fetched again at most once a minute.
const KEY_REFETCH_SECONDS = 60;

/** A refused ID token; the message is a short reason code for the log. */
export class InvalidToken extends Error {}
export class SetupError extends Error {}
/** A failed call to Google: only the step and the HTTP status are kept. */
export class GoogleError extends Error {
  constructor(readonly step: string, readonly status: number) {
    super(`${step} failed with HTTP ${status}`);
  }
}

export interface FirebaseClaims {
  sub: string;
  auth_time: number;
  role?: unknown;
  [name: string]: unknown;
}

export interface Deps {
  fetch: typeof fetch;
  nowSeconds: () => number;
  /** The public key for a Firebase ID token's key ID, or undefined. */
  keyFor: (kid: string) => Promise<CryptoKey | undefined>;
  /** The raw FIREBASE_AUTH_ADMIN_KEY secret, or undefined. */
  secret: () => string | undefined;
}

// ---- Firebase ID token ----------------------------------------------------

const encoder = new TextEncoder();
const RSA = { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" };

// No return type: newer TypeScript infers Uint8Array<ArrayBuffer>, which Web Crypto needs.
function base64UrlDecode(text: string) {
  if (!/^[A-Za-z0-9_-]*$/.test(text)) throw new InvalidToken("encoding");
  const base64 = text.replace(/-/g, "+").replace(/_/g, "/");
  const binary = atob(base64 + "=".repeat((4 - (base64.length % 4)) % 4));
  return Uint8Array.from(binary, (c) => c.charCodeAt(0));
}

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function decodeJsonPart(part: string): Record<string, unknown> {
  let value: unknown;
  try {
    value = JSON.parse(new TextDecoder().decode(base64UrlDecode(part)));
  } catch {
    throw new InvalidToken("json");
  }
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new InvalidToken("json");
  }
  return value as Record<string, unknown>;
}

/**
 * Checks a Firebase ID token as Firebase's documentation requires: an RS256
 * signature by one of Google's securetoken keys, issuer and audience of
 * project "salusalo", not expired, not issued or signed in in the future (with
 * a small clock skew), and a non-empty subject. Throws InvalidToken otherwise.
 */
export async function verifyFirebaseIdToken(
  token: string,
  keyFor: (kid: string) => Promise<CryptoKey | undefined>,
  nowSeconds: number,
): Promise<FirebaseClaims> {
  const parts = token.split(".");
  if (parts.length !== 3 || token.length > MAX_TOKEN_LENGTH) throw new InvalidToken("format");
  const header = decodeJsonPart(parts[0]);
  if (header.alg !== "RS256" || typeof header.kid !== "string") throw new InvalidToken("header");
  const key = await keyFor(header.kid);
  if (!key) throw new InvalidToken("unknown_key");
  const signed = await crypto.subtle.verify(
    RSA,
    key,
    base64UrlDecode(parts[2]),
    encoder.encode(`${parts[0]}.${parts[1]}`),
  );
  if (!signed) throw new InvalidToken("signature");

  const claims = decodeJsonPart(parts[1]);
  const number = (name: string) => typeof claims[name] === "number" && Number.isFinite(claims[name]);
  if (claims.iss !== ISSUER || claims.aud !== FIREBASE_PROJECT_ID) throw new InvalidToken("project");
  if (typeof claims.sub !== "string" || claims.sub.length === 0 || claims.sub.length > 128) {
    throw new InvalidToken("subject");
  }
  if (!number("exp") || (claims.exp as number) + CLOCK_SKEW_SECONDS <= nowSeconds) {
    throw new InvalidToken("expired");
  }
  for (const name of ["iat", "auth_time"]) {
    if (!number(name) || (claims[name] as number) > nowSeconds + CLOCK_SKEW_SECONDS) {
      throw new InvalidToken("time");
    }
  }
  return claims as FirebaseClaims;
}

/** Seconds from a Cache-Control header's max-age, or 0. */
export function maxAgeSeconds(cacheControl: string | null): number {
  const match = /(?:^|,)\s*max-age=(\d+)/i.exec(cacheControl ?? "");
  return match ? Number(match[1]) : 0;
}

interface KeySet {
  keys: Map<string, CryptoKey>;
  fetchedAt: number;
  expiresAt: number;
}

let googleKeys: KeySet | null = null;

async function fetchGoogleKeys(fetchFn: typeof fetch, now: number): Promise<KeySet> {
  const response = await fetchFn(GOOGLE_KEYS_URL);
  if (!response.ok) {
    await response.body?.cancel();
    throw new GoogleError("public keys", response.status);
  }
  const body = await response.json() as { keys?: Array<Record<string, unknown>> };
  const keys = new Map<string, CryptoKey>();
  for (const jwk of body.keys ?? []) {
    if (jwk.kty !== "RSA" || typeof jwk.kid !== "string") continue;
    const key = await crypto.subtle.importKey(
      "jwk",
      { kty: "RSA", n: jwk.n as string, e: jwk.e as string, alg: "RS256", ext: true },
      RSA,
      false,
      ["verify"],
    );
    keys.set(jwk.kid, key);
  }
  // Cached for as long as Google's Cache-Control allows.
  return { keys, fetchedAt: now, expiresAt: now + maxAgeSeconds(response.headers.get("cache-control")) };
}

/** Google's keys, cached as their response headers allow. */
export function googleKeyLookup(fetchFn: typeof fetch, nowSeconds: () => number) {
  return async (kid: string): Promise<CryptoKey | undefined> => {
    const now = nowSeconds();
    if (!googleKeys || googleKeys.expiresAt <= now ||
      (!googleKeys.keys.has(kid) && now - googleKeys.fetchedAt >= KEY_REFETCH_SECONDS)) {
      googleKeys = await fetchGoogleKeys(fetchFn, now);
    }
    return googleKeys.keys.get(kid);
  };
}

// ---- Service account and Identity Toolkit ----------------------------------

interface ServiceAccount {
  clientEmail: string;
  privateKey: string;
}

/** The service account from the secret, or SetupError if it is missing or unusable. */
export function readServiceAccount(raw: string | undefined): ServiceAccount {
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

async function callAccounts(
  deps: Deps,
  accessToken: string,
  method: "lookup" | "update",
  body: unknown,
): Promise<Record<string, unknown>> {
  const response = await deps.fetch(`${ACCOUNTS_URL}:${method}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    // Google's error body is never read, logged or returned.
    await response.body?.cancel();
    throw new GoogleError(`accounts:${method}`, response.status);
  }
  return await response.json();
}

/**
 * The account's custom claims with role: "authenticated" added, every other
 * claim kept. `changed` is false when the role is already there.
 */
export function withSupabaseRole(customAttributes: unknown): { attributes: string; changed: boolean } {
  let claims: Record<string, unknown> = {};
  if (typeof customAttributes === "string" && customAttributes.trim() !== "") {
    const parsed = JSON.parse(customAttributes);
    if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new GoogleError("custom claims", 200);
    }
    claims = parsed;
  }
  if (claims.role === ROLE) return { attributes: JSON.stringify(claims), changed: false };
  return { attributes: JSON.stringify({ ...claims, role: ROLE }), changed: true };
}

// ---- Handler ---------------------------------------------------------------

function reply(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

const INVALID_TOKEN = { error: "Invalid or missing Firebase ID token." };

/** A refusal: only its reason code is logged, never the caller. */
function refuse(status: number, reason: string, body: Record<string, unknown>): Response {
  console.log(`set-firebase-role: refused (${reason})`);
  return reply(status, body);
}

/** At most the first 6 characters of a UID, for the log. */
function uidHint(uid: string): string {
  return `${uid.slice(0, 6)}…`;
}

export async function handle(request: Request, deps: Deps): Promise<Response> {
  if (request.method !== "POST") return refuse(405, "method", { error: "Use POST." });

  // The request body is never read: the account is the verified token's own.
  const authorization = request.headers.get("Authorization") ?? "";
  const match = /^Bearer ([A-Za-z0-9_.-]+)$/.exec(authorization);
  if (!match) return refuse(401, "no_token", INVALID_TOKEN);

  try {
    let claims: FirebaseClaims;
    try {
      claims = await verifyFirebaseIdToken(match[1], deps.keyFor, deps.nowSeconds());
    } catch (error) {
      if (error instanceof InvalidToken) return refuse(401, `token_${error.message}`, INVALID_TOKEN);
      throw error;
    }

    const firebase = claims.firebase as { sign_in_provider?: unknown } | undefined;
    if (firebase?.sign_in_provider === "anonymous") {
      return refuse(403, "anonymous", { error: "Anonymous accounts can't use Salu-Salo." });
    }

    // Already there: nothing to do, and no call to Google.
    if (claims.role === ROLE) {
      console.log(`set-firebase-role: role already in the token (uid ${uidHint(claims.sub)})`);
      return reply(200, { role: ROLE, changed: false });
    }

    const account = readServiceAccount(deps.secret());
    const accessToken = await googleAccessToken(account, deps);
    const found = await callAccounts(deps, accessToken, "lookup", { localId: [claims.sub] });
    const user = (Array.isArray(found.users) ? found.users[0] : undefined) as
      | Record<string, unknown>
      | undefined;
    if (!user) return refuse(401, "account_not_found", INVALID_TOKEN);
    // A token issued before the account's sessions were revoked.
    if (Number(user.validSince ?? 0) > claims.auth_time) return refuse(401, "session_revoked", INVALID_TOKEN);
    if (user.disabled === true) return refuse(403, "account_disabled", { error: "This account is disabled." });

    const { attributes, changed } = withSupabaseRole(user.customAttributes);
    if (changed) {
      await callAccounts(deps, accessToken, "update", { localId: claims.sub, customAttributes: attributes });
    }
    console.log(`set-firebase-role: ${changed ? "role set" : "role was already set"} (uid ${uidHint(claims.sub)})`);
    return reply(200, { role: ROLE, changed });
  } catch (error) {
    if (error instanceof SetupError) {
      console.error(`set-firebase-role: the ${SECRET_NAME} secret is missing or invalid`);
      return reply(500, {
        error: `Server setup incomplete: the ${SECRET_NAME} secret is missing or invalid.`,
      });
    }
    if (error instanceof GoogleError) {
      console.error(`set-firebase-role: ${error.message}`);
      return reply(502, { error: "Could not update the account. Try again later." });
    }
    // Only the error's type: its text could contain request details.
    console.error(`set-firebase-role: unexpected ${error instanceof Error ? error.name : typeof error}`);
    return reply(500, { error: "Could not update the account. Try again later." });
  }
}

const nowSeconds = () => Math.floor(Date.now() / 1000);

export default {
  fetch: (request: Request) =>
    handle(request, {
      fetch,
      nowSeconds,
      keyFor: googleKeyLookup(fetch, nowSeconds),
      secret: () => Deno.env.get(SECRET_NAME),
    }),
};
