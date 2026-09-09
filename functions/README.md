# MyJournal+ AI Cloud Function

Keeps the Gemini API key **off the phone**. The Android app calls `aiChat` with the user’s Firebase Auth token.

## 1. One-time setup

```bash
# From project root (needs Firebase CLI + login)
npm install -g firebase-tools
firebase login
firebase use myjournal-plus

cd functions
npm install

# Store the key as a secret (never commit it)
firebase functions:secrets:set GEMINI_API_KEY
# paste your AIza... key from https://aistudio.google.com/apikey
```

## 2. Deploy

```bash
firebase deploy --only functions:aiChat
```

Region: `asia-southeast1` (matches your Firebase region).

## 3. Firebase Console

- Authentication must be enabled (already used by the app).
- Firestore rules: only the signed-in user can read/write their `aiChats` (see `firestore.rules` sample in repo).

## 4. Website alternative

If you prefer your own site (`app.myjournalplus.com`), mirror the same logic in a server route:
- Verify Firebase ID token (`Authorization: Bearer <idToken>`).
- Read `isPremium` / `aiFreeUsedCount` from Firestore.
- Call Gemini with server-side env `GEMINI_API_KEY`.
- Return `{ answer, chatId }`.

Point the app’s `GeminiService` base URL at that route if you skip Cloud Functions.
