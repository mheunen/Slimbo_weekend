package nl.spellenweekend.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Spellen en uitnodigingen",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Uitnodigingen, start van een spel en herinneringen"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "spellen"
    }
}
