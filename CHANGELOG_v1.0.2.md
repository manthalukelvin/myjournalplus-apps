# MyJournalPlus v1.0.2

## Fixes
- **PIN protection restored**: MainActivity now shows a full-screen lock overlay when PIN is enabled. Lifecycle-aware (locks after timeout when returning from background).
- **AI server**: hardened for `https://kelvin.onrender.com` with clear error codes and `/diag` endpoint for debugging.
- **Display name**: uses Firestore `username` (same as web app), with fallbacks to displayName / firstName.
- **Account settings URL**: https://myjournalplus.com/settings
- **AI Insights keyboard**: Scaffold uses `imePadding()` so the input bar stays above the keyboard.
- **Crashlytics**: Firebase Crashlytics enabled for all crashes.

## Server (independent modules)
- `POST /ai-chat` — AI assistant (Gemini key only on server, 3 free uses)
- `GET /diag` — diagnostics without secrets
- `GET /admin` — admin dashboard (requires `isAdmin: true`)
- Admin APIs: stats, users, grant premium, notifications, promo config
- Admin does **not** interfere with AI routes

## Version
- versionName 1.0.2 / versionCode 3
