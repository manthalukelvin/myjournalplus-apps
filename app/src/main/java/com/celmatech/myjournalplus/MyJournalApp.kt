package com.celmatech.myjournalplus

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.celmatech.myjournalplus.util.Analytics
import com.celmatech.myjournalplus.util.CrashReporting
import com.celmatech.myjournalplus.util.NotificationChannels

class MyJournalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        NotificationChannels.createAll(this)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        CrashReporting.log("app_start")
        Analytics.appOpen()
    }
}
