package com.celmatech.myjournalplus

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

class MyJournalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        // Report all uncaught crashes to Firebase Crashlytics
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
    }
}
