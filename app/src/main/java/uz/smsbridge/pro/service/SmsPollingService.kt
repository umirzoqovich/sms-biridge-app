package uz.smsbridge.pro.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uz.smsbridge.pro.R
import uz.smsbridge.pro.SmsBridgeApplication
import uz.smsbridge.pro.data.model.ReportRequest
import uz.smsbridge.pro.data.model.SmsLogEntry
import uz.smsbridge.pro.network.CrmApiClient
import uz.smsbridge.pro.sms.SmsSender
import uz.smsbridge.pro.ui.MainActivity
import uz.smsbridge.pro.util.SmsBridgeEvents

class SmsPollingService : Service() {

    companion object {
        private const val TAG = "SmsPollingService"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "uz.smsbridge.pro.action.START"
        const val ACTION_STOP = "uz.smsbridge.pro.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, SmsPollingService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SmsPollingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null

    private lateinit var apiClient: CrmApiClient
    private lateinit var smsSender: SmsSender
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        apiClient = CrmApiClient()
        smsSender = SmsSender(this)
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "Service stop requested")
                stopForegroundService()
                return START_NOT_STICKY
            }
            else -> {
                Log.d(TAG, "Service started")
                startInForeground()
                startPollingLoop()
                return START_STICKY
            }
        }
    }

    private fun startInForeground() {
        val notification = buildNotification(getString(R.string.notification_text_waiting))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        SmsBridgeApplication.preferences.isServiceRunning = true
        SmsBridgeEvents.setServiceRunning(true)
    }

    private fun startPollingLoop() {
        if (pollingJob?.isActive == true) return

        pollingJob = serviceScope.launch {
            Log.d(TAG, "SMS Polling loop started")
            while (isActive) {
                val prefs = SmsBridgeApplication.preferences
                val serverUrl = prefs.serverUrl
                val simSlot = prefs.selectedSimSlot

                try {
                    val pollResponse = apiClient.poll(serverUrl, simSlot)
                    SmsBridgeEvents.setConnected(true)

                    if (pollResponse.hasTask && !pollResponse.phone.isNullOrBlank() && !pollResponse.message.isNullOrBlank()) {
                        val taskId = pollResponse.taskId ?: "unknown"
                        val phone = pollResponse.phone
                        val message = pollResponse.message
                        val effectiveSlot = pollResponse.simSlot ?: simSlot

                        updateNotification(getString(R.string.notification_text_sending, phone))

                        // Send SMS
                        val sendResult = smsSender.sendSms(phone, message, effectiveSlot)

                        if (sendResult.isSuccess) {
                            Log.i(TAG, "SMS muvaffaqiyatli yuborildi: $phone (Task: $taskId)")
                            prefs.incrementSuccess()

                            val logEntry = SmsLogEntry(
                                phone = phone,
                                status = "sent",
                                taskId = taskId
                            )
                            prefs.addLogEntry(logEntry)
                            SmsBridgeEvents.emitNewLog(logEntry)
                            SmsBridgeEvents.notifyStatsUpdated()

                            // Report success to CRM
                            try {
                                apiClient.report(
                                    serverUrl,
                                    ReportRequest(
                                        taskId = taskId,
                                        phone = phone,
                                        status = "sent",
                                        error = ""
                                    )
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "Report yuborishda xatolik: ${e.message}")
                            }
                        } else {
                            val errorMsg = sendResult.exceptionOrNull()?.localizedMessage ?: "SMS yuborib bo'lmadi"
                            Log.e(TAG, "SMS yuborishda xatolik: $phone ($errorMsg)")
                            prefs.incrementFailed()

                            val logEntry = SmsLogEntry(
                                phone = phone,
                                status = "failed",
                                taskId = taskId,
                                error = errorMsg
                            )
                            prefs.addLogEntry(logEntry)
                            SmsBridgeEvents.emitNewLog(logEntry)
                            SmsBridgeEvents.notifyStatsUpdated()

                            // Report failure to CRM
                            try {
                                apiClient.report(
                                    serverUrl,
                                    ReportRequest(
                                        taskId = taskId,
                                        phone = phone,
                                        status = "failed",
                                        error = errorMsg
                                    )
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "Report yuborishda xatolik: ${e.message}")
                            }
                        }

                        updateNotification(getString(R.string.notification_text_waiting))
                        // Short pause after sending task
                        delay(500)
                    } else {
                        // No task in queue, wait specified seconds (default 3s)
                        val waitTime = (pollResponse.waitSeconds.coerceAtLeast(3)) * 1000L
                        delay(waitTime)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Polling xatosi: ${e.message}")
                    SmsBridgeEvents.setConnected(false)
                    // Retry after standard 3 seconds
                    delay(3000)
                }
            }
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SmsPollingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SmsBridgeApplication.CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_delete,
                getString(R.string.notification_action_stop),
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        notificationManager?.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SmsBridgePro::PollingWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // 12 hours safety timeout
            }
            Log.d(TAG, "Partial WakeLock acquired")
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock olishda xatolik: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Partial WakeLock released")
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock qo'yib yuborishda xatolik: ${e.message}")
        }
    }

    private fun stopForegroundService() {
        pollingJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()

        SmsBridgeApplication.preferences.isServiceRunning = false
        SmsBridgeEvents.setServiceRunning(false)
        SmsBridgeEvents.setConnected(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForegroundService()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
