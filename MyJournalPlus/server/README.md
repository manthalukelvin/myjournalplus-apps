# Secure AI API on your website (no Firebase Functions)

Host this on **app.myjournalplus.com** (or any PHP/Node host).  
The Android app calls `POST /api/ai-chat` with a Firebase ID token.  
**GEMINI_API_KEY never leaves the server.**

## App config

In `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "AI_API_BASE_URL", "\"https://app.myjournalplus.com/api\"")
```

App requests: `POST https://app.myjournalplus.com/api/ai-chat`

## Choose one stack

| Folder | Use when |
|--------|----------|
| `php/` | Shared hosting / cPanel / existing PHP site |
| `node/` | Node/Express VPS, Railway, Render, etc. |

## Required secrets (server only)

```
GEMINI_API_KEY=AIza...          # from https://aistudio.google.com/apikey
FIREBASE_PROJECT_ID=myjournal-plus
```

Optional Firestore access: service account JSON (see php/.env.example).

## Free limit

- Field on user doc: `aiFreeUsedCount`
- Free users: 1 successful AI reply, then HTTP 402 + `FREE_LIMIT_REACHED`
- Premium: `isPremium: true` on `users/{uid}`

## Deploy checklist

1. Upload API files to your host under `/api/`
2. Set env vars / config with Gemini key
3. Enable HTTPS
4. Test with curl (see below)
5. Rebuild the Android app

### Quick test

```bash
# Get a Firebase ID token from a signed-in device/debug, then:
curl -X POST https://app.myjournalplus.com/api/ai-chat \
  -H "Authorization: Bearer YOUR_FIREBASE_ID_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"question":"How have I been feeling?"}'
```
