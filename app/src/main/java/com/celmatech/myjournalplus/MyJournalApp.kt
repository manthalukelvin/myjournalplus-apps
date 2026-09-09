package com.celmatech.myjournalplus

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class MyJournalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        try {
            FirebaseFirestore.getInstance().firestoreSettings =
                FirebaseFirestoreSettings.Builder().setPersistenceEnabled(true).build()
        } catch (_: Exception) { }
        try { MobileAds.initialize(this) } catch (_: Exception) { }
    }
}
