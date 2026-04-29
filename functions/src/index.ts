import * as admin from "firebase-admin";
import {setGlobalOptions} from "firebase-functions";
import {onRequest} from "firebase-functions/v2/https";
import {defineSecret} from "firebase-functions/params";
import fetch from "node-fetch";

admin.initializeApp();
setGlobalOptions({maxInstances: 10, region: "us-central1"});

const anthropicApiKey = defineSecret("ANTHROPIC_API_KEY");
const stravaClientSecret = defineSecret("STRAVA_CLIENT_SECRET");

/**
 * Firestore-based per-user rate limit.
 * @param {string} uid - Firebase user ID.
 * @param {string} functionName - Function name used as key prefix.
 * @param {number} requestsPerWindow - Max requests allowed per window.
 * @param {number} windowMs - Window duration in milliseconds.
 */
async function checkRateLimit(
  uid: string,
  functionName: string,
  requestsPerWindow: number,
  windowMs: number
): Promise<void> {
  const db = admin.firestore();
  const windowKey = Math.floor(Date.now() / windowMs);
  const bucket = `${functionName}_${windowKey}`;
  const ref = db.doc(`_rateLimits/${uid}/functions/${bucket}`);

  const result = await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const count = snap.exists ? (snap.data()?.count ?? 0) : 0;
    if (count >= requestsPerWindow) return false;
    const expiry = Date.now() + windowMs;
    tx.set(ref, {count: count + 1, expiresAt: expiry}, {merge: true});
    return true;
  });

  if (!result) {
    throw new Error("Rate limit exceeded.");
  }
}

// Verifies Firebase ID token, proxies to Anthropic, rate-limits per user.
// ANTHROPIC_API_KEY stays server-side — never ships in the APK.
export const anthropicMessages = onRequest(
  {secrets: [anthropicApiKey]},
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({error: "Method not allowed"});
      return;
    }

    const authHeader = req.headers.authorization ?? "";
    if (!authHeader.startsWith("Bearer ")) {
      res.status(401).json({error: "Missing or invalid Authorization header"});
      return;
    }

    let uid: string;
    try {
      const decoded = await admin.auth().verifyIdToken(
        authHeader.slice(7)
      );
      uid = decoded.uid;
    } catch {
      res.status(401).json({error: "Invalid Firebase ID token"});
      return;
    }

    try {
      await checkRateLimit(uid, "anthropicMessages", 20, 60 * 60 * 1000);
    } catch {
      res.status(429).json({error: "Rate limit exceeded. Try again later."});
      return;
    }

    const upstream = await fetch(
      "https://api.anthropic.com/v1/messages",
      {
        method: "POST",
        headers: {
          "x-api-key": anthropicApiKey.value(),
          "anthropic-version": "2023-06-01",
          "content-type": "application/json",
        },
        body: JSON.stringify(req.body),
      }
    );

    const data = await upstream.json();
    res.status(upstream.status).json(data);
  }
);

/**
 * Verifies Firebase ID token then exchanges a Strava auth code for tokens.
 * Keeps STRAVA_CLIENT_SECRET server-side.
 * Body: { clientId, code }
 */
export const stravaTokenExchange = onRequest(
  {secrets: [stravaClientSecret]},
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({error: "Method not allowed"});
      return;
    }

    const authHeader = req.headers.authorization ?? "";
    if (!authHeader.startsWith("Bearer ")) {
      res.status(401).json({error: "Missing or invalid Authorization header"});
      return;
    }

    try {
      await admin.auth().verifyIdToken(authHeader.slice(7));
    } catch {
      res.status(401).json({error: "Invalid Firebase ID token"});
      return;
    }

    const {clientId, code} = req.body as {
      clientId: string;
      code: string;
    };

    if (!clientId || !code) {
      res.status(400).json({error: "clientId and code are required"});
      return;
    }

    const params = new URLSearchParams({
      client_id: clientId,
      client_secret: stravaClientSecret.value(),
      code,
      grant_type: "authorization_code",
    });

    const upstream = await fetch("https://www.strava.com/oauth/token", {
      method: "POST",
      headers: {"content-type": "application/x-www-form-urlencoded"},
      body: params.toString(),
    });

    const data = await upstream.json();
    res.status(upstream.status).json(data);
  }
);

/**
 * Verifies Firebase ID token then refreshes a Strava access token.
 * Keeps STRAVA_CLIENT_SECRET server-side.
 * Body: { clientId, refreshToken }
 */
export const stravaTokenRefresh = onRequest(
  {secrets: [stravaClientSecret]},
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({error: "Method not allowed"});
      return;
    }

    const authHeader = req.headers.authorization ?? "";
    if (!authHeader.startsWith("Bearer ")) {
      res.status(401).json({error: "Missing or invalid Authorization header"});
      return;
    }

    try {
      await admin.auth().verifyIdToken(authHeader.slice(7));
    } catch {
      res.status(401).json({error: "Invalid Firebase ID token"});
      return;
    }

    const {clientId, refreshToken} = req.body as {
      clientId: string;
      refreshToken: string;
    };

    if (!clientId || !refreshToken) {
      res.status(400).json({error: "clientId and refreshToken are required"});
      return;
    }

    const params = new URLSearchParams({
      client_id: clientId,
      client_secret: stravaClientSecret.value(),
      refresh_token: refreshToken,
      grant_type: "refresh_token",
    });

    const upstream = await fetch("https://www.strava.com/oauth/token", {
      method: "POST",
      headers: {"content-type": "application/x-www-form-urlencoded"},
      body: params.toString(),
    });

    const data = await upstream.json();
    res.status(upstream.status).json(data);
  }
);
