# MyJournal+ Native Android v2.2 (Kotlin + Compose)

## Fixes in 2.2
- **Auth text visibility** — light field backgrounds + dark text on login/signup
- **Google Sign-In** — navigates to Home after success (auth state + success event)
- **Sign up fields** — display name, first name, surname, gender, age, email, password
- **Edit entry** — removed “How are you feeling” mood chips
- **Mood tracker** — free for everyone
- **Home** — subtitle under MyJournal+: “Reflect · Track · Grow”
- **Crashes with existing entries** — safer realtime listeners + offline persistence
- **Offline** — Firestore persistence enabled; read/write while offline, sync when online
- **Premium popup** — beautiful dialog → https://app.myjournalplus.com/confirm

## Build
1. Replace `app/google-services.json` from Firebase
2. Set `default_web_client_id` in `strings.xml` (Web client ID from Firebase)
3. Open in Android Studio → Sync → Run
