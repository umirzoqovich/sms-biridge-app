package uz.smsbridge.pro.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PollResponse(
    val hasTask: Boolean,
    val taskId: String? = null,
    val phone: String? = null,
    val message: String? = null,
    val simSlot: Int? = null,
    val waitSeconds: Int = 3
)

data class ReportRequest(
    val taskId: String,
    val phone: String,
    val status: String, // "sent" or "failed"
    val error: String = ""
)

data class PingResult(
    val isSuccess: Boolean,
    val message: String,
    val latencyMs: Long
)

data class SmsLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val phone: String,
    val status: String, // "sent" or "failed"
    val taskId: String?,
    val error: String? = null
) {
    fun toFormattedString(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timeStr = sdf.format(Date(timestamp))
        return if (status == "sent") {
            "[$timeStr] $phone -> Yuborildi (Task #${taskId ?: "-"})"
        } else {
            val err = if (!error.isNullOrBlank()) " ($error)" else ""
            "[$timeStr] $phone -> Xatolik$err (Task #${taskId ?: "-"})"
        }
    }
}
