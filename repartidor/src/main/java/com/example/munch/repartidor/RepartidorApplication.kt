package com.example.munch.repartidor
import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
class RepartidorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.initializeApp(this) != null) {
            // Do not queue GPS fixes that might reach the server after delivery completion.
            FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(com.google.firebase.firestore.MemoryCacheSettings.newBuilder().build()).build()
        }
    }
}
