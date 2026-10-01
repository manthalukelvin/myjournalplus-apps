# MyJournalPlus AI Server (v1.0.1)

Deploy this folder to **https://my24hrs.onrender.com** (Render Web Service).

## Why this exists
- Gemini API key **never** ships inside the Android APK.
- Free users get **exactly 3** successful AI answers. The counter lives in Firestore (`users/{uid}.aiFreeUsedCount`) and is incremented **only after** a successful Gemini response.
- Client cannot bypass the limit: the server returns HTTP 402 + `FREE_LIMIT` when exceeded.
- Premium users (`users/{uid}.isPremium == true`) have unlimited access.

## Render setup
1. New Web Service → connect this repo/folder.
2. Runtime: Node.
3. Build: `npm install`
4. Start: `npm start`
5. Environment variables:
   - `GEMINI_API_KEY` = your Google AI Studio key
   - `FIREBASE_SERVICE_ACCOUNT` = full service-account JSON (one line)
6. After deploy, the Android app already points to `https://my24hrs.onrender.com` (BuildConfig.AI_API_BASE_URL).

## Local test
```bash
cp .env.example .env
# fill keys
npm install
npm start
```

## Security checklist
- [ ] GEMINI_API_KEY only in Render env (never in git)
- [ ] Firebase service account has minimal roles (Auth + Firestore)
- [ ] CORS is open for the app; rate-limit if needed later
- [ ] Free counter is server-authoritative
