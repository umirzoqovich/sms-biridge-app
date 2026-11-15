package uz.smsbridge.pro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import uz.smsbridge.pro.data.preferences.AppPreferences

class SmsBridgeApplication : Application() {

    companion object {
        const val CHANNEL_ID = "sms_bridge_service_channel"
        lateinit var preferences: AppPreferences
            private set
    }

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
