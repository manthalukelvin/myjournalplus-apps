/**
 * MyJournal+ — secure AI proxy
 * Deploy: set secret GEMINI_API_KEY then firebase deploy --only functions
 *
 * firebase functions:secrets:set GEMINI_API_KEY
 */
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");
const fetch = require("node-fetch");

admin.initializeApp();
const db = admin.firestore();
const GEMINI_API_KEY = defineSecret("GEMINI_API_KEY");
const MODEL = "gemini-2.0-flash";

exports.aiChat = onCall(
  {
    secrets: [GEMINI_API_KEY],
    region: "asia-southeast1",
    timeoutSeconds: 60,
    memory: "256MiB",
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in required");
    }
    const uid = request.auth.uid;
    const question = (request.data?.question || "").toString().trim();
    if (!question || question.length > 2000) {
      throw new HttpsError("invalid-argument", "Please enter a question (max 2000 chars)");
    }

    const userRef = db.collection("users").doc(uid);
    const userSnap = await userRef.get();
    const user = userSnap.data() || {};
    const isPremium = user.isPremium === true;
    const freeUsed = Number(user.aiFreeUsedCount || 0);
    const pending = Number(user.aiFreePendingCount || 0);

    if (!isPremium && freeUsed + pending >= 3) {
      throw new HttpsError(
        "resource-exhausted",
        "FREE_LIMIT_REACHED",
        { code: "FREE_LIMIT_REACHED", message: "Upgrade to Premium for unlimited AI chat" }
      );
    }
    if (!isPremium) {
      await db.runTransaction(async (tx) => {
        const fresh = await tx.get(userRef);
        const data = fresh.data() || {};
        const used = Number(data.aiFreeUsedCount || 0);
        const inFlight = Number(data.aiFreePendingCount || 0);
        if (used + inFlight >= 3) {
          throw new HttpsError("resource-exhausted", "FREE_LIMIT_REACHED");
        }
        tx.set(
          userRef,
          { aiFreePendingCount: admin.firestore.FieldValue.increment(1) },
          { merge: true }
        );
      });
    }
    const releasePending = async () => {
      if (!isPremium) {
        await userRef.set(
          { aiFreePendingCount: admin.firestore.FieldValue.increment(-1) },
          { merge: true }
        );
      }
    };

    // Load recent journal context (server-side — never trust client-only)
    const [entriesSnap, moodsSnap] = await Promise.all([
      userRef.collection("entries").orderBy("createdAt", "desc").limit(8).get(),
      userRef.collection("moods").orderBy("createdAt", "desc").limit(10).get(),
    ]);

    const entriesCtx = entriesSnap.docs
      .map((d) => {
        const e = d.data();
        return `- ${(e.title || "Untitled")}: ${String(e.content || "").slice(0, 220)}`;
      })
      .join("\n");
    const moodsCtx = moodsSnap.docs
      .map((d) => {
        const m = d.data();
        return `- ${m.mood || "?"} (${m.score ?? "?"}/5)${m.note ? ": " + m.note : ""}`;
      })
      .join("\n");

    const systemPrompt = `You are a warm, supportive journaling companion for MyJournal+.
You help the user reflect on their journal entries and moods only.
Rules:
- Be empathetic, practical, and concise (under 220 words unless they ask for more).
- No medical diagnoses, no crisis instructions beyond suggesting professional help if they express severe distress.
- If they seem in crisis, gently encourage reaching local emergency services or a trusted person.
- Base answers on the journal context when relevant; if context is empty, still answer helpfully about general reflection.

User journal context:
Entries:
${entriesCtx || "(none yet)"}

Moods:
${moodsCtx || "(none yet)"}

User question:
${question}`;

    const key = GEMINI_API_KEY.value();
    if (!key) {
      await releasePending();
      throw new HttpsError("failed-precondition", "AI is not configured on the server");
    }

    const url = `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent?key=${key}`;
    const resp = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        contents: [{ role: "user", parts: [{ text: systemPrompt }] }],
        generationConfig: { temperature: 0.7, maxOutputTokens: 512 },
      }),
    });
    const raw = await resp.json();
    if (!resp.ok) {
      const msg = raw?.error?.message || JSON.stringify(raw).slice(0, 180);
      await releasePending();
      throw new HttpsError("internal", `AI error: ${msg}`);
    }
    const answer =
      raw?.candidates?.[0]?.content?.parts?.map((p) => p.text).join("") ||
      "";
    if (!answer.trim()) {
      await releasePending();
      throw new HttpsError("internal", "AI returned an empty response");
    }

    // Persist chat + message pair
    const chatId = (request.data?.chatId || "").toString().trim() || null;
    let chatRef;
    if (chatId) {
      chatRef = userRef.collection("aiChats").doc(chatId);
    } else {
      chatRef = userRef.collection("aiChats").doc();
      await chatRef.set({
        title: question.slice(0, 60),
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    }

    const batch = db.batch();
    const userMsg = chatRef.collection("messages").doc();
    const aiMsg = chatRef.collection("messages").doc();
    batch.set(userMsg, {
      role: "user",
      text: question,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    batch.set(aiMsg, {
      role: "assistant",
      text: answer,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    batch.update(chatRef, {
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      title: question.slice(0, 60),
    });
    if (!isPremium) {
      batch.set(
        userRef,
        {
          aiFreeUsedCount: admin.firestore.FieldValue.increment(1),
          aiFreePendingCount: admin.firestore.FieldValue.increment(-1),
          aiFreeUsedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      );
    }
    await batch.commit();

    return {
      answer,
      chatId: chatRef.id,
      freeLimitReached: !isPremium,
      isPremium,
    };
  }
);
