# MyJournal+ v3.0 — Keys & setup

Package: **com.celmatech.myjournalplus** (matches your google-services.json)

## 1. google-services.json
Already placed in `app/google-services.json`.


## 2. Gemini (AI Insights) — secure (recommended)

**Do not put the API key in the Android app.**

1. Get a key: https://aistudio.google.com/apikey
2. Deploy Cloud Function (see `functions/README.md`):
   ```bash
   firebase functions:secrets:set GEMINI_API_KEY
   cd functions && npm install
   firebase deploy --only functions:aiChat
   ```
3. App calls callable `aiChat` in region `asia-southeast1`.
4. Free users: **1 free question**, then Premium required (enforced on server via `aiFreeUsedCount`).
5. Chats saved under `users/{uid}/aiChats/{chatId}/messages`.

## 3. AdMob (banner for free users)
Currently **test** IDs in `build.gradle.kts` and AndroidManifest.

For production:
1. https://admob.google.com → create app with package `com.celmatech.myjournalplus`
2. Create Banner ad unit
3. Replace:
   - `ADMOB_APP_ID` in Manifest meta-data
   - `ADMOB_BANNER_ID` in build.gradle.kts BuildConfig
   - `ADMOB_APP_ID` BuildConfig if used

## 4. Admin push notifications (all users)
**Best option for all Android versions:** Firebase Cloud Messaging + topic `all_users`.

App already:
- Subscribes to topic `all_users` on launch when logged in
- Saves FCM token to `users/{uid}.fcmTokens`
- Shows notification via `MjFirebaseMessagingService`

### Send from Firebase Console
1. Firebase → Messaging → “Create your first campaign” / New campaign → Notifications
2. Target: Topic `all_users` (or specific user token)
3. Write title/body → Send

### Send from Admin SDK (server)
```js
await admin.messaging().send({
  topic: "all_users",
  notification: { title: "Hello", body: "From MyJournal+ admin" }
});
```

No need for different code per Android version if you use FCM data/notification payloads.

## 5. Premium unlock
Firestore `users/{AuthUID}` → `isPremium: true` (boolean).  
App listens live — badge & features unlock after refresh/reopen or automatically via snapshot.

## 6. Reminder
Default **18:00** daily. User can change time in Settings. Uses AlarmManager; reschedules on boot.

## 7. SHA-1 for Google Sign-In
In Firebase Console → Project settings → your Android app → add SHA-1 from:
```
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```
