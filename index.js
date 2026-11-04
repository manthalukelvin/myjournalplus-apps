/**
 * MyJournalPlus AI Proxy Server
 * Deploy to: https://my24hrs.onrender.com
 *
 * SECURITY:
 * - Gemini API key is ONLY in environment variable GEMINI_API_KEY (never in client)
 * - Every request must include a valid Firebase ID token
 * - Free users are limited to exactly 3 successful AI calls (counted in Firestore)
 * - Limit is enforced server-side and cannot be bypassed by the client
 *
 * Required env vars on Render:
 *   GEMINI_API_KEY=your_gemini_key
 *   FIREBASE_SERVICE_ACCOUNT=JSON string of service account (or use GOOGLE_APPLICATION_CREDENTIALS)
 *   PORT=10000 (Render sets this automatically)
 */

require("dotenv").config();
const express = require("express");
const cors = require("cors");
const admin = require("firebase-admin");
const { GoogleGenerativeAI } = require("@google/generative-ai");

const app = express();
app.use(cors({ origin: true }));
app.use(express.json({ limit: "32kb" }));

const FREE_LIMIT = 3;
const GEMINI_API_KEY = process.env.GEMINI_API_KEY;
if (!GEMINI_API_KEY) {
  console.error("FATAL: GEMINI_API_KEY environment variable is required");
  process.exit(1);
}

// Firebase Admin init
try {
  if (process.env.FIREBASE_SERVICE_ACCOUNT) {
    const sa = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
    admin.initializeApp({ credential: admin.credential.cert(sa) });
  } else if (process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    admin.initializeApp();
  } else {
    // Try default (for local with ADC)
    admin.initializeApp({ credential: admin.credential.applicationDefault() });
  }
} catch (e) {
  console.error("Firebase Admin init failed:", e.message);
  process.exit(1);
}

const db = admin.firestore();
const genAI = new GoogleGenerativeAI(GEMINI_API_KEY);
const model = genAI.getGenerativeModel({ model: "gemini-1.5-flash" });

async function verifyToken(req) {
  const auth = req.headers.authorization || "";
  if (!auth.startsWith("Bearer ")) return null;
  const token = auth.slice(7);
  try {
    return await admin.auth().verifyIdToken(token);
  } catch {
    return null;
  }
}

app.get("/", (_req, res) => {
  res.json({ ok: true, service: "MyJournalPlus AI", version: "1.0.1" });
});

app.get("/health", (_req, res) => res.json({ status: "ok" }));

/**
 * POST /ai-chat
 * Body: { question: string, chatId?: string }
 * Headers: Authorization: Bearer <Firebase ID token>
 *
 * Response: { answer, chatId, freeLimitReached, isPremium, freeUsedCount }
 * Status 402 when free limit exceeded.
 */
app.post("/ai-chat", async (req, res) => {
  try {
    const decoded = await verifyToken(req);
    if (!decoded) {
      return res.status(401).json({ error: "Unauthorized — valid Firebase token required" });
    }
    const uid = decoded.uid;
    const question = (req.body?.question || "").trim();
    if (!question || question.length > 4000) {
      return res.status(400).json({ error: "question is required (max 4000 chars)" });
    }

    const userRef = db.collection("users").doc(uid);
    const userSnap = await userRef.get();
    const userData = userSnap.exists ? userSnap.data() : {};
    const isPremium = !!userData.isPremium;
    let freeUsedCount = Number(userData.aiFreeUsedCount || 0);

    if (!isPremium && freeUsedCount >= FREE_LIMIT) {
      return res.status(402).json({
        error: "FREE_LIMIT",
        freeLimitReached: true,
        isPremium: false,
        freeUsedCount,
        message: `Free limit of ${FREE_LIMIT} AI questions reached. Upgrade to Premium.`
      });
    }

    // Call Gemini
    const systemHint =
      "You are a supportive, concise journaling companion for the MyJournalPlus app. " +
      "Give empathetic, practical insights. Keep answers under 300 words unless asked for more. " +
      "Do not mention you are an AI model unless asked.";
    const prompt = `${systemHint}\n\nUser: ${question}`;
    const result = await model.generateContent(prompt);
    const answer = result.response?.text?.() || "I could not generate a response right now.";

    // Increment free counter ONLY after a successful generation (strict)
    if (!isPremium) {
      freeUsedCount += 1;
      await userRef.set(
        { aiFreeUsedCount: freeUsedCount, lastAiAt: admin.firestore.FieldValue.serverTimestamp() },
        { merge: true }
      );
    }

    // Optional: store chat message
    let chatId = req.body?.chatId || null;
    try {
      const chatsRef = userRef.collection("aiChats");
      if (!chatId) {
        const chatDoc = await chatsRef.add({
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
          updatedAt: admin.firestore.FieldValue.serverTimestamp()
        });
        chatId = chatDoc.id;
      }
      await chatsRef.doc(chatId).collection("messages").add({
        role: "user",
        text: question,
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      });
      await chatsRef.doc(chatId).collection("messages").add({
        role: "assistant",
        text: answer,
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      });
      await chatsRef.doc(chatId).set(
        { updatedAt: admin.firestore.FieldValue.serverTimestamp() },
        { merge: true }
      );
    } catch (chatErr) {
      console.warn("Chat store warning:", chatErr.message);
    }

    const freeLimitReached = !isPremium && freeUsedCount >= FREE_LIMIT;
    return res.json({
      answer,
      chatId: chatId || "",
      freeLimitReached,
      isPremium,
      freeUsedCount
    });
  } catch (e) {
    console.error("ai-chat error:", e);
    return res.status(500).json({ error: e.message || "AI request failed" });
  }
});

const PORT = process.env.PORT || 10000;
app.listen(PORT, () => {
  console.log(`MyJournalPlus AI server listening on port ${PORT}`);
  console.log(`Free limit: ${FREE_LIMIT} questions per non-premium user`);
});
