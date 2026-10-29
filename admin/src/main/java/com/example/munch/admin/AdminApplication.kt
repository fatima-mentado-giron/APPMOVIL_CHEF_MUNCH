package com.example.munch.admin
import android.app.Application
import com.google.firebase.FirebaseApp
class AdminApplication : Application() {
    override fun onCreate() { super.onCreate(); FirebaseApp.initializeApp(this) }
}
