// Tests for supabase/functions/send-push, with a locally generated key and a
// fake Google. Run from the repository root:
//   deno test supabase/functions/tests/send-push-test.ts
// Nothing here calls Google or Supabase.

import { assert, assertEquals, assertFalse } from "jsr:@std/assert@1.0.19";
import { type Deps, fcmMessage, handle, KEY_SECRET, sameSecret, WEBHOOK_SECRET } from "../send-push/index.ts";

const NOW = 1_790_000_000;
const RSA = { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" };
const WEBHOOK = "test-webhook-secret-0123456789";
const ACCESS_TOKEN = "ya29.test-push-access-token";
const FCM_URL = "https://fcm.googleapis.com/v1/projects/salusalo/messages:send";

const serviceAccountKey = await crypto.subtle.generateKey(
  { ...RSA, modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]) },
  true,
  ["sign", "verify"],
);

// Each test that needs a fresh Google access token uses its own client email:
// the function caches the token per service account.
async function keySecret(clientEmail: string, projectId = "salusalo"): Promise<string> {
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

const ALERT = {
  id: "alert-1",
  type: "MESSAGE",
  title: "New message from Ana",
  body: "Pandesal: Is it still there?",
  conversation_id: "conv-1",
  reservation_id: null,
  food_post_id: "post-1",
};

function pushBody(overrides: Record<string, unknown> = {}) {
  return { uid: "firebase-uid-1", tokens: ["token-a", "token-b"], alert: ALERT, ...overrides };
}

// No return type: newer TypeScript infers Uint8Array<ArrayBuffer>, which Web Crypto needs.
function decodeBase64Url(part: string) {
  const base64 = part.replace(/-/g, "+").replace(/_/g, "/");
  return Uint8Array.from(atob(base64 + "=".repeat((4 - (base64.length % 4)) % 4)), (c) => c.charCodeAt(0));
}

/** A fake Google: the OAuth token endpoint and FCM's messages:send. */
function fakeGoogle(fcmStatus: (token: string) => number = () => 200, oauthStatus = 200) {
  const oauth: Array<Record<string, unknown>> = [];
  const sent: Array<{ auth: string | null; body: { message: { token: string; [key: string]: unknown } } }> = [];
  const fetchFn = async (input: string | URL | Request, init?: RequestInit): Promise<Response> => {
    const url = String(input);
    if (url === "https://oauth2.googleapis.com/token") {
      const assertion = new URLSearchParams(String(init?.body)).get("assertion")!;
      const [head, claims, signature] = assertion.split(".");
      const signed = await crypto.subtle.verify(
        RSA,
        serviceAccountKey.publicKey,
        decodeBase64Url(signature),
        new TextEncoder().encode(`${head}.${claims}`),
      );
      oauth.push({ ...JSON.parse(new TextDecoder().decode(decodeBase64Url(claims))), signed });
      if (oauthStatus !== 200) return new Response(`{"error":"google says no: secret-detail"}`, { status: oauthStatus });
      return Response.json({ access_token: ACCESS_TOKEN, expires_in: 3600 });
    }
    if (url === FCM_URL) {
      const body = JSON.parse(String(init?.body));
      sent.push({ auth: new Headers(init?.headers).get("Authorization"), body });
      const status = fcmStatus(body.message.token);
      return new Response(
        status === 200
          ? `{"name":"projects/salusalo/messages/1"}`
          : `{"error":{"status":"UNREGISTERED","message":"secret-detail"}}`,
        { status },
      );
    }
    throw new Error(`unexpected fetch ${url}`);
  };
  return { fetchFn: fetchFn as typeof fetch, oauth, sent };
}

function deps(fetchFn: typeof fetch, secrets: Record<string, string | undefined>): Deps {
  return { fetch: fetchFn, nowSeconds: () => NOW, secret: (name) => secrets[name] };
}

function request(body: unknown, secret: string | null = WEBHOOK, method = "POST"): Request {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (secret !== null) headers["x-push-secret"] = secret;
  return new Request("http://localhost/send-push", {
    method,
    headers,
    body: method === "POST" ? (typeof body === "string" ? body : JSON.stringify(body)) : undefined,
  });
}

/** Runs [action] with console.log and console.error captured. */
async function captureLogs<T>(action: () => Promise<T>): Promise<{ result: T; logs: string }> {
  const lines: string[] = [];
  const { log, error } = console;
  console.log = (...args: unknown[]) => void lines.push(args.join(" "));
  console.error = (...args: unknown[]) => void lines.push(args.join(" "));
  try {
    return { result: await action(), logs: lines.join("\n") };
  } finally {
    console.log = log;
    console.error = error;
  }
}

Deno.test("only POST", async () => {
  const google = fakeGoogle();
  const response = await handle(
    request(null, WEBHOOK, "GET"),
    deps(google.fetchFn, { [WEBHOOK_SECRET]: WEBHOOK, [KEY_SECRET]: await keySecret("get@salusalo.iam.gserviceaccount.com") }),
  );
  assertEquals(response.status, 405);
  assertEquals(google.oauth.length + google.sent.length, 0);
});

Deno.test("a missing or wrong secret sends nothing", async () => {
  const google = fakeGoogle();
  const d = deps(google.fetchFn, { [WEBHOOK_SECRET]: WEBHOOK, [KEY_SECRET]: await keySecret("wrong@salusalo.iam.gserviceaccount.com") });
  for (const secret of [null, "", "wrong", WEBHOOK + "x", WEBHOOK.slice(0, -1)]) {
    const { result: response, logs } = await captureLogs(() => handle(request(pushBody(), secret), d));
    assertEquals(response.status, 401, String(secret));
    assertFalse(logs.includes(WEBHOOK));
  }
  assertEquals(google.oauth.length + google.sent.length, 0);
});

Deno.test("without the webhook secret set up, nothing is sent (an empty one never matches)", async () => {
  const google = fakeGoogle();
  const key = await keySecret("nowebhook@salusalo.iam.gserviceaccount.com");
  for (const webhook of [undefined, ""]) {
    const response = await handle(request(pushBody(), ""), deps(google.fetchFn, { [WEBHOOK_SECRET]: webhook, [KEY_SECRET]: key }));
    assertEquals(response.status, 500);
    assertEquals(await response.json(), { error: "Server setup incomplete." });
  }
  assertEquals(google.oauth.length + google.sent.length, 0);
});

Deno.test("a body that isn't a push request is refused", async () => {
  const google = fakeGoogle();
  const d = deps(google.fetchFn, { [WEBHOOK_SECRET]: WEBHOOK, [KEY_SECRET]: await keySecret("body@salusalo.iam.gserviceaccount.com") });
  const bodies: unknown[] = [
    "not json",
    {},
    pushBody({ tokens: [] }),
    pushBody({ tokens: ["a", "b", "c", "d", "e", "f"] }),
    pushBody({ tokens: ["a", 5] }),
    pushBody({ tokens: ["a", ""] }),
    pushBody({ uid: "" }),
    pushBody({ alert: null }),
    pushBody({ alert: { ...ALERT, title: 7 } }),
    pushBody({ alert: { id: "a", type: "MESSAGE", body: "no title" } }),
  ];
  for (const body of bodies) {
    const response = await handle(request(body), d);
    assertEquals(response.status, 400, JSON.stringify(body));
  }
  assertEquals(google.oauth.length + google.sent.length, 0);
});

Deno.test("sends one data message per phone, and logs no token, text or UID", async () => {
  const google = fakeGoogle();
  const d = deps(google.fetchFn, { [WEBHOOK_SECRET]: WEBHOOK, [KEY_SECRET]: await keySecret("send@salusalo.iam.gserviceaccount.com") });
  const { result: response, logs } = await captureLogs(() => handle(request(pushBody()), d));
  assertEquals(response.status, 200);
  assertEquals(await response.json(), { sent: 2, failed: 0 });
  assertEquals(google.oauth.length, 1);
  assertEquals(google.oauth[0].scope, "https://www.googleapis.com/auth/firebase.messaging");
  assertEquals(google.oauth[0].iss, "send@salusalo.iam.gserviceaccount.com");
  assert(google.oauth[0].signed);
  assertEquals(google.sent.map((s) => s.body.message.token), ["token-a", "token-b"]);
  assertEquals(google.sent[0].auth, `Bearer ${ACCESS_TOKEN}`);
  assertEquals(google.sent[0].body, {
    message: {
      token: "token-a",
      data: {
        alert_id: "alert-1",
        type: "MESSAGE",
        title: "New message from Ana",
        body: "Pandesal: Is it still there?",
        uid: "firebase-uid-1",
        conversation_id: "conv-1",
        food_post_id: "post-1",
      },
      android: { priority: "HIGH", ttl: "86400s" },
    },
  });
  for (const secret of ["token-a", "token-b", "Pandesal", "Ana", ACCESS_TOKEN, "firebase-uid-1", WEBHOOK]) {
    assertFalse(logs.includes(secret), secret);
  }
});

Deno.test("one failing phone doesn't stop the others; Google's error body isn't logged", async () => {
  const google = fakeGoogle((token) => (token === "token-a" ? 404 : 200));
  const d = deps(google.fetchFn, { [WEBHOOK_SECRET]: WEBHOOK, [KEY_SECRET]: await keySecret("partial@salusalo.iam.gserviceaccount.com") });
  const { result: response, logs } = await captureLogs(() => handle(request(pushBody()), d));
  assertEquals(response.status, 200);
  assertEquals(await response.json(), { sent: 1, failed: 1 });
  assertEquals(google.sent.length, 2);
  assert(logs.includes("404"));
  assertFalse(logs.includes("secret-detail"));
  assertFalse(logs.includes("UNREGISTERED"));
  assertFalse(logs.includes("token-a"));
});

Deno.test("the access token is reused", async () => {
  const google = fakeGoogle();
  const d = deps(google.fetchFn, { [WEBHOOK_SECRET]: WEBHOOK, [KEY_SECRET]: await keySecret("reuse@salusalo.iam.gserviceaccount.com") });
  await handle(request(pushBody()), d);
  await handle(request(pushBody()), d);
  assertEquals(google.oauth.length, 1);
  assertEquals(google.sent.length, 4);
});

Deno.test("a missing or invalid key is a setup error and sends nothing", async () => {
  const keys = [undefined, "", "not json", await keySecret("other@other.iam.gserviceaccount.com", "other-project")];
  for (const key of keys) {
    const google = fakeGoogle();
    const { result: response, logs } = await captureLogs(() =>
      handle(request(pushBody()), deps(google.fetchFn, { [WEBHOOK_SECRET]: WEBHOOK, [KEY_SECRET]: key }))
    );
    assertEquals(response.status, 500);
    assertEquals(await response.json(), { error: "Server setup incomplete." });
    assertEquals(google.sent.length, 0);
    assert(logs.includes(KEY_SECRET));
  }
});

Deno.test("Google refusing the access token: 502, nothing sent, its error body not logged", async () => {
  const google = fakeGoogle(() => 200, 400);
  const d = deps(google.fetchFn, { [WEBHOOK_SECRET]: WEBHOOK, [KEY_SECRET]: await keySecret("refused@salusalo.iam.gserviceaccount.com") });
  const { result: response, logs } = await captureLogs(() => handle(request(pushBody()), d));
  assertEquals(response.status, 502);
  assertEquals(google.sent.length, 0);
  assertFalse(logs.includes("secret-detail"));
});

Deno.test("a long text is cut to 300 characters without breaking an emoji", () => {
  const message = fcmMessage("token-a", { uid: "u", tokens: ["token-a"], alert: { ...ALERT, body: "🍞".repeat(350) } });
  const chars = Array.from(message.message.data.body);
  assertEquals(chars.length, 300);
  assertEquals(chars[299], "…");
  assert(chars.slice(0, 299).every((c) => c === "🍞"));
  const short = fcmMessage("token-a", { uid: "u", tokens: ["token-a"], alert: { ...ALERT, body: "x".repeat(300) } });
  assertEquals(short.message.data.body, "x".repeat(300));
});

Deno.test("alerts without a chat, reservation or listing leave those keys out", () => {
  const message = fcmMessage("t", {
    uid: "u",
    tokens: ["t"],
    alert: { ...ALERT, type: "ANNOUNCEMENT", conversation_id: null, reservation_id: null, food_post_id: null },
  });
  assertEquals(Object.keys(message.message.data).sort(), ["alert_id", "body", "title", "type", "uid"]);
});

Deno.test("sameSecret compares whole strings", () => {
  assert(sameSecret("abc", "abc"));
  assertFalse(sameSecret("abc", "abd"));
  assertFalse(sameSecret("abc", "abcd"));
  assertFalse(sameSecret("abcd", "abc"));
  assertFalse(sameSecret("", "abc"));
});
