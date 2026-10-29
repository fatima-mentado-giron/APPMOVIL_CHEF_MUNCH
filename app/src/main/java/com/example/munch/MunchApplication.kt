package com.example.munch
import android.app.Application
import com.google.firebase.FirebaseApp
class MunchApplication : Application() {
    override fun onCreate() { super.onCreate(); FirebaseApp.initializeApp(this) }
}
