package com.celmatech.myjournalplus

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
<<<<<<< HEAD
import com.celmatech.myjournalplus.util.Analytics
import com.celmatech.myjournalplus.util.CrashReporting
=======
>>>>>>> bb2e8564354b35039192d34a53ed32a370b8b3f9

class MyJournalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
<<<<<<< HEAD
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        CrashReporting.log("app_start")
        Analytics.appOpen()
=======
        // Report all uncaught crashes to Firebase Crashlytics
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
>>>>>>> bb2e8564354b35039192d34a53ed32a370b8b3f9
    }
}
