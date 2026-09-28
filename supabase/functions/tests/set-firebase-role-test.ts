// Tests for supabase/functions/set-firebase-role, with locally generated keys.
// Run from the repository root:
//   deno test supabase/functions/tests/set-firebase-role-test.ts
// Nothing here calls Google or Supabase.

import { assert, assertEquals, assertFalse, assertRejects } from "jsr:@std/assert@1.0.19";
import {
  type Deps,
  googleKeyLookup,
  handle,
  InvalidToken,
  maxAgeSeconds,
  SECRET_NAME,
  verifyFirebaseIdToken,
  withSupabaseRole,
} from "../set-firebase-role/index.ts";

const NOW = 1_790_000_000;
const RSA = { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" };
const encoder = new TextEncoder();

async function rsaKeyPair(): Promise<CryptoKeyPair> {
  return await crypto.subtle.generateKey(
    { ...RSA, modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]) },
    true,
    ["sign", "verify"],
  );
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

const firebaseKey = await rsaKeyPair();
const otherKey = await rsaKeyPair();
const serviceAccountKey = await rsaKeyPair();

async function token(
  claims: Record<string, unknown> = {},
  header: Record<string, unknown> = {},
  signWith: CryptoKey = firebaseKey.privateKey,
): Promise<string> {
  const head = base64Url(encoder.encode(JSON.stringify({ alg: "RS256", kid: "key-1", typ: "JWT", ...header })));
  const body = base64Url(encoder.encode(JSON.stringify({
    iss: "https://securetoken.google.com/salusalo",
    aud: "salusalo",
    sub: "firebase-uid-1",
    iat: NOW - 10,
    auth_time: NOW - 10,
    exp: NOW + 3590,
    ...claims,
  })));
  const signature = new Uint8Array(await crypto.subtle.sign(RSA, signWith, encoder.encode(`${head}.${body}`)));
  return `${head}.${body}.${base64Url(signature)}`;
}

const keyFor = (kid: string) => Promise.resolve(kid === "key-1" ? firebaseKey.publicKey : undefined);

async function serviceAccountSecret(
  projectId = "salusalo",
  clientEmail = "salusalo-role-setter@salusalo.iam.gserviceaccount.com",
): Promise<string> {
  const der = new Uint8Array(await crypto.subtle.exportKey("pkcs8", serviceAccountKey.privateKey));
  let binary = "";
  for (const byte of der) binary += String.fromCharCode(byte);
  const pem = `-----BEGIN PRIVATE KEY-----\n${btoa(binary).replace(/(.{64})/g, "$1\n")}\n-----END PRIVATE KEY-----\n`;
  return JSON.stringify({
    type: "service_account",
    project_id: projectId,
    private_key_id: "abc",
    private_key: pem,
    client_email: clientEmail,
  });
}

const ACCESS_TOKEN = "ya29.test-access-token";

/** A fake Google: the OAuth token endpoint and accounts:lookup / accounts:update. */
function fakeGoogle(user: Record<string, unknown> | null, failUpdate = false) {
  const calls: Array<{ url: string; body: unknown }> = [];
  const fetchFn = async (input: string | URL | Request, init?: RequestInit): Promise<Response> => {
    const url = String(input);
    if (url === "https://oauth2.googleapis.com/token") {
      const form = new URLSearchParams(String(init?.body));
      const [head, claims, signature] = form.get("assertion")!.split(".");
      const decoded = JSON.parse(atob(claims.replace(/-/g, "+").replace(/_/g, "/")));
      calls.push({ url, body: decoded });
      const signed = await crypto.subtle.verify(
        RSA,
        serviceAccountKey.publicKey,
        Uint8Array.from(atob(signature.replace(/-/g, "+").replace(/_/g, "/") + "==".slice(0, (4 - signature.length % 4) % 4)), (c) => c.charCodeAt(0)),
        encoder.encode(`${head}.${claims}`),
      );
      if (!signed) return new Response("bad assertion", { status: 400 });
      return Response.json({ access_token: ACCESS_TOKEN, expires_in: 3599, token_type: "Bearer" });
    }
    assertEquals(new Headers(init?.headers).get("Authorization"), `Bearer ${ACCESS_TOKEN}`);
    const body = JSON.parse(String(init?.body));
    calls.push({ url, body });
    if (url.endsWith("/v1/projects/salusalo/accounts:lookup")) {
      return Response.json(user ? { users: [user] } : {});
    }
    if (url.endsWith("/v1/projects/salusalo/accounts:update")) {
      if (failUpdate) return new Response('{"error":{"message":"GOOGLE-RAW-DETAIL"}}', { status: 403 });
      return Response.json({ localId: body.localId });
    }
    throw new Error(`unexpected URL ${url}`);
  };
  return { calls, fetchFn };
}

async function call(
  deps: Partial<Deps>,
  authorization?: string,
  method = "POST",
  requestBody?: string,
): Promise<{ status: number; body: Record<string, unknown>; text: string; logs: string }> {
  const logs: string[] = [];
  const { log, error } = console;
  console.log = (...args: unknown[]) => logs.push(args.join(" "));
  console.error = (...args: unknown[]) => logs.push(args.join(" "));
  try {
    const headers = authorization ? { Authorization: authorization } : undefined;
    const response = await handle(
      new Request("https://example.test/functions/v1/set-firebase-role", { method, headers, body: requestBody }),
      {
        fetch: () => Promise.reject(new Error("no network in this test")),
        nowSeconds: () => NOW,
        keyFor,
        secret: () => undefined,
        ...deps,
      },
    );
    const text = await response.text();
    return { status: response.status, body: JSON.parse(text), text, logs: logs.join("\n") };
  } finally {
    console.log = log;
    console.error = error;
  }
}

// ---- Token checks -----------------------------------------------------------

Deno.test("a good token is accepted", async () => {
  const claims = await verifyFirebaseIdToken(await token(), keyFor, NOW);
  assertEquals(claims.sub, "firebase-uid-1");
});

Deno.test("a wrong audience is refused", async () => {
  const bad = await token({ aud: "another-project" });
  await assertRejects(() => verifyFirebaseIdToken(bad, keyFor, NOW), InvalidToken);
});

Deno.test("a wrong issuer is refused", async () => {
  const bad = await token({ iss: "https://securetoken.google.com/another-project" });
  await assertRejects(() => verifyFirebaseIdToken(bad, keyFor, NOW), InvalidToken);
});

Deno.test("an expired token is refused, within the clock skew it is not", async () => {
  await assertRejects(() => token({ exp: NOW - 61 }).then((t) => verifyFirebaseIdToken(t, keyFor, NOW)), InvalidToken);
  await verifyFirebaseIdToken(await token({ exp: NOW - 30 }), keyFor, NOW);
});

Deno.test("a token issued or signed in in the future is refused", async () => {
  await assertRejects(() => token({ iat: NOW + 120 }).then((t) => verifyFirebaseIdToken(t, keyFor, NOW)), InvalidToken);
  await assertRejects(() => token({ auth_time: NOW + 120 }).then((t) => verifyFirebaseIdToken(t, keyFor, NOW)), InvalidToken);
  await verifyFirebaseIdToken(await token({ iat: NOW + 30, auth_time: NOW + 30 }), keyFor, NOW);
});

Deno.test("a bad signature is refused", async () => {
  const forged = await token({}, {}, otherKey.privateKey);
  await assertRejects(() => verifyFirebaseIdToken(forged, keyFor, NOW), InvalidToken);
  const good = await token();
  const [head, , signature] = good.split(".");
  const changedBody = base64Url(encoder.encode(JSON.stringify({
    iss: "https://securetoken.google.com/salusalo", aud: "salusalo", sub: "someone-else",
    iat: NOW, auth_time: NOW, exp: NOW + 3600,
  })));
  await assertRejects(() => verifyFirebaseIdToken(`${head}.${changedBody}.${signature}`, keyFor, NOW), InvalidToken);
});

Deno.test("an unknown key, another algorithm or an empty subject is refused", async () => {
  await assertRejects(() => token({}, { kid: "unknown" }).then((t) => verifyFirebaseIdToken(t, keyFor, NOW)), InvalidToken);
  await assertRejects(() => token({}, { alg: "HS256" }).then((t) => verifyFirebaseIdToken(t, keyFor, NOW)), InvalidToken);
  await assertRejects(() => token({}, { alg: "none" }).then((t) => verifyFirebaseIdToken(t, keyFor, NOW)), InvalidToken);
  await assertRejects(() => token({ sub: "" }).then((t) => verifyFirebaseIdToken(t, keyFor, NOW)), InvalidToken);
  await assertRejects(() => verifyFirebaseIdToken("not-a-token", keyFor, NOW), InvalidToken);
});

// ---- The handler ------------------------------------------------------------

Deno.test("no token, a malformed header or GET gets no work done", async () => {
  const noToken = await call({});
  assertEquals(noToken.status, 401);
  assertEquals(noToken.body, { error: "Invalid or missing Firebase ID token." });
  assertEquals(noToken.logs, "set-firebase-role: refused (no_token)");
  assertEquals((await call({}, "Basic abc")).status, 401);
  assertEquals((await call({}, `Bearer ${await token()}`, "GET")).status, 405);
});

Deno.test("a refusal logs only its reason code", async () => {
  const idToken = await token({ aud: "x" });
  const result = await call({}, `Bearer ${idToken}`);
  assertEquals(result.status, 401);
  assertEquals(result.logs, "set-firebase-role: refused (token_project)");
  const expired = await call({}, `Bearer ${await token({ exp: NOW - 3600 })}`);
  assertEquals(expired.logs, "set-firebase-role: refused (token_expired)");
  const forged = await call({}, `Bearer ${await token({}, {}, otherKey.privateKey)}`);
  assertEquals(forged.logs, "set-firebase-role: refused (token_signature)");
});

Deno.test("an anonymous account is refused without calling Google", async () => {
  const google = fakeGoogle({ localId: "firebase-uid-1" });
  const anonymous = await token({ firebase: { sign_in_provider: "anonymous", identities: {} } });
  const result = await call({ fetch: google.fetchFn, secret: await serviceAccountSecret().then((s) => () => s) }, `Bearer ${anonymous}`);
  assertEquals(result.status, 403);
  assertEquals(result.body, { error: "Anonymous accounts can't use Salu-Salo." });
  assertEquals(result.logs, "set-firebase-role: refused (anonymous)");
  assertEquals(google.calls.length, 0);
});

Deno.test("the account is the token's own, whatever the request body says", async () => {
  const google = fakeGoogle({ localId: "firebase-uid-1" });
  const result = await call(
    { fetch: google.fetchFn, secret: await serviceAccountSecret().then((s) => () => s) },
    `Bearer ${await token({ firebase: { sign_in_provider: "password" } })}`,
    "POST",
    JSON.stringify({ localId: "someone-else", uid: "someone-else", role: "admin", customAttributes: '{"admin":true}' }),
  );
  assertEquals(result.status, 200);
  const lookup = google.calls.find((c) => c.url.endsWith("accounts:lookup"))!;
  const update = google.calls.find((c) => c.url.endsWith("accounts:update"))!;
  assertEquals(lookup.body, { localId: ["firebase-uid-1"] });
  assertEquals(update.body, { localId: "firebase-uid-1", customAttributes: '{"role":"authenticated"}' });
});

Deno.test("a token that already has the role is answered without calling Google", async () => {
  const google = fakeGoogle(null);
  const result = await call({ fetch: google.fetchFn }, `Bearer ${await token({ role: "authenticated" })}`);
  assertEquals(result.status, 200);
  assertEquals(result.body, { role: "authenticated", changed: false });
  assertEquals(google.calls.length, 0);
});

Deno.test("the role is added and every other claim is kept", async () => {
  // Its own service account email, so a fresh Google access token is requested.
  const secret = await serviceAccountSecret("salusalo", "claims-test@salusalo.iam.gserviceaccount.com");
  const idToken = await token();
  const google = fakeGoogle({
    localId: "firebase-uid-1",
    customAttributes: JSON.stringify({ admin: true, tier: "gold", nested: { a: 1 } }),
    validSince: String(NOW - 1000),
  });
  const result = await call({ fetch: google.fetchFn, secret: () => secret }, `Bearer ${idToken}`);
  assertEquals(result.status, 200);
  assertEquals(result.body, { role: "authenticated", changed: true });

  const byUrl = (end: string) => google.calls.find((c) => c.url.endsWith(end))!;
  const oauth = byUrl("oauth2.googleapis.com/token").body as Record<string, unknown>;
  assertEquals(oauth.scope, "https://www.googleapis.com/auth/cloud-platform");
  assertEquals(oauth.iss, "claims-test@salusalo.iam.gserviceaccount.com");
  assertEquals(oauth.aud, "https://oauth2.googleapis.com/token");
  assertEquals(byUrl("accounts:lookup").body, { localId: ["firebase-uid-1"] });
  const sent = byUrl("accounts:update").body as { localId: string; customAttributes: string };
  assertEquals(sent.localId, "firebase-uid-1");
  assertEquals(JSON.parse(sent.customAttributes), { admin: true, tier: "gold", nested: { a: 1 }, role: "authenticated" });

  // The log names at most the UID's first 6 characters; nothing secret is logged or returned.
  assertEquals(result.logs, "set-firebase-role: role set (uid fireba…)");
  for (const secretText of [idToken, ACCESS_TOKEN, "PRIVATE KEY", JSON.parse(secret).private_key.slice(40, 80), "firebase-uid-1"]) {
    assertFalse(result.logs.includes(secretText));
    assertFalse(result.text.includes(secretText));
  }
});

Deno.test("a second call is cheap: the stored access token is reused", async () => {
  const secret = await serviceAccountSecret("salusalo", "reuse-test@salusalo.iam.gserviceaccount.com");
  const google = fakeGoogle({ localId: "firebase-uid-1" });
  await call({ fetch: google.fetchFn, secret: () => secret }, `Bearer ${await token()}`);
  await call({ fetch: google.fetchFn, secret: () => secret }, `Bearer ${await token()}`);
  assertEquals(google.calls.filter((c) => c.url.endsWith("/token")).length, 1);
});

Deno.test("an account whose claim is already stored is not written again", async () => {
  const google = fakeGoogle({ localId: "firebase-uid-1", customAttributes: '{"role":"authenticated"}' });
  const result = await call({ fetch: google.fetchFn, secret: await serviceAccountSecret().then((s) => () => s) }, `Bearer ${await token()}`);
  assertEquals(result.body, { role: "authenticated", changed: false });
  assertEquals(result.logs, "set-firebase-role: role was already set (uid fireba…)");
  assertFalse(google.calls.some((c) => c.url.endsWith("accounts:update")));
});

Deno.test("an account without custom claims gets only the role", async () => {
  const google = fakeGoogle({ localId: "firebase-uid-1" });
  await call({ fetch: google.fetchFn, secret: await serviceAccountSecret().then((s) => () => s) }, `Bearer ${await token()}`);
  const update = google.calls.find((c) => c.url.endsWith("accounts:update"))!.body as { customAttributes: string };
  assertEquals(JSON.parse(update.customAttributes), { role: "authenticated" });
});

Deno.test("a disabled, deleted or revoked account is refused and not changed", async () => {
  const secret = await serviceAccountSecret();
  for (const [user, status, reason] of [
    [{ localId: "firebase-uid-1", disabled: true }, 403, "account_disabled"],
    [null, 401, "account_not_found"],
    [{ localId: "firebase-uid-1", validSince: String(NOW) }, 401, "session_revoked"],
  ] as const) {
    const google = fakeGoogle(user as Record<string, unknown> | null);
    const result = await call({ fetch: google.fetchFn, secret: () => secret }, `Bearer ${await token()}`);
    assertEquals(result.status, status);
    assertEquals(result.logs, `set-firebase-role: refused (${reason})`);
    assertFalse(google.calls.some((c) => c.url.endsWith("accounts:update")));
  }
});

Deno.test("a missing or wrong secret names the setup, not the key", async () => {
  const idToken = await token();
  for (const secret of [undefined, "", "not json", await serviceAccountSecret("another-project")]) {
    const result = await call({ fetch: fakeGoogle(null).fetchFn, secret: () => secret }, `Bearer ${idToken}`);
    assertEquals(result.status, 500);
    assertEquals(result.body.error, `Server setup incomplete: the ${SECRET_NAME} secret is missing or invalid.`);
    assertFalse(result.text.includes("BEGIN"));
    assertFalse(result.logs.includes("BEGIN"));
  }
});

Deno.test("Google's error body is neither returned nor logged", async () => {
  const google = fakeGoogle({ localId: "firebase-uid-1" }, true);
  const result = await call({ fetch: google.fetchFn, secret: await serviceAccountSecret().then((s) => () => s) }, `Bearer ${await token()}`);
  assertEquals(result.status, 502);
  assertFalse(result.text.includes("GOOGLE-RAW-DETAIL"));
  assertFalse(result.logs.includes("GOOGLE-RAW-DETAIL"));
  assert(result.logs.includes("accounts:update failed with HTTP 403"));
});

// ---- Helpers ----------------------------------------------------------------

Deno.test("merging claims keeps the others and is idempotent", () => {
  assertEquals(withSupabaseRole(undefined), { attributes: '{"role":"authenticated"}', changed: true });
  const once = withSupabaseRole('{"a":1}');
  assertEquals(JSON.parse(once.attributes), { a: 1, role: "authenticated" });
  assertEquals(withSupabaseRole(once.attributes).changed, false);
});

Deno.test("Google's keys are cached for Cache-Control's max-age", async () => {
  assertEquals(maxAgeSeconds("public, max-age=19617, must-revalidate"), 19617);
  assertEquals(maxAgeSeconds(null), 0);
  const jwk = await crypto.subtle.exportKey("jwk", firebaseKey.publicKey);
  let fetches = 0;
  let now = NOW;
  const lookup = googleKeyLookup(() => {
    fetches++;
    return Promise.resolve(Response.json(
      { keys: [{ ...jwk, kid: "key-1", alg: "RS256", use: "sig" }] },
      { headers: { "Cache-Control": "public, max-age=600" } },
    ));
  }, () => now);
  assert(await lookup("key-1"));
  assert(await lookup("key-1"));
  assertEquals(fetches, 1);
  // An unknown key ID fetches again at most once a minute.
  assertEquals(await lookup("unknown"), undefined);
  assertEquals(fetches, 1);
  now += 61;
  assertEquals(await lookup("unknown"), undefined);
  assertEquals(fetches, 2);
  // After max-age the keys are fetched again.
  now += 600;
  await lookup("key-1");
  assertEquals(fetches, 3);
});
