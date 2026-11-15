package uz.smsbridge.pro.sms

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log

class SmsSender(private val context: Context) {

    companion object {
        private const val TAG = "SmsSender"
    }

    /**
     * Sends an SMS message to the specified recipient phone number.
     * Supports long/multipart SMS (Uzbek Latin and Cyrillic text).
     *
     * @param phone The recipient phone number in international format (e.g. +998901234567)
     * @param message The SMS body text
     * @param simSlot Target SIM slot: 1 for SIM 1, 2 for SIM 2, or null/0 for default/auto
     */
    fun sendSms(phone: String, message: String, simSlot: Int? = null): Result<Unit> {
        return try {
            val cleanPhone = phone.trim().replace(" ", "").replace("-", "")
            if (cleanPhone.isBlank()) {
                return Result.failure(IllegalArgumentException("Telefon raqami bo'sh"))
            }
            if (message.isBlank()) {
                return Result.failure(IllegalArgumentException("SMS matni bo'sh"))
            }

            val smsManager = getSmsManagerForSlot(simSlot)

            // Uzbek text often contains Cyrillic or special characters (o', g') which use UCS-2 (70 chars)
            // divideMessage handles segmentation automatically
            val parts = smsManager.divideMessage(message)
            Log.d(TAG, "Sending message to $cleanPhone in ${parts.size} part(s) via SIM slot $simSlot")

            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(cleanPhone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(cleanPhone, null, message, null, null)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "SMS yuborishda xatolik: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Resolves the appropriate SmsManager instance based on the target SIM slot.
     */
    @SuppressLint("MissingPermission")
    private fun getSmsManagerForSlot(simSlot: Int?): SmsManager {
        if (simSlot == null || simSlot <= 0) {
            return getDefaultSmsManager()
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val subscriptionManager =
                    context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val activeSubscriptions: List<SubscriptionInfo>? =
                    subscriptionManager?.activeSubscriptionInfoList

                if (!activeSubscriptions.isNullOrEmpty()) {
                    // Look for SIM matching slot index (0 for SIM 1, 1 for SIM 2)
                    val targetIndex = simSlot - 1
                    val matchingInfo = activeSubscriptions.find { it.simSlotIndex == targetIndex }
                        ?: activeSubscriptions.getOrNull(targetIndex)
                        ?: activeSubscriptions.firstOrNull()

                    if (matchingInfo != null) {
                        val subId = matchingInfo.subscriptionId
                        Log.d(TAG, "Using Subscription ID: $subId for SIM slot: $simSlot")
                        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
                        } else {
                            @Suppress("DEPRECATION")
                            SmsManager.getSmsManagerForSubscriptionId(subId)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SIM kartani aniqlashda xatolik, standart SmsManager ishlatiladi: ${e.message}")
        }

        return getDefaultSmsManager()
    }

    private fun getDefaultSmsManager(): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }
}
