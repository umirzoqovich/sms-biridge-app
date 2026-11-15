package uz.smsbridge.pro.data.preferences

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import uz.smsbridge.pro.data.model.SmsLogEntry

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sms_bridge_pro_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SERVER_URL = "key_server_url"
        private const val KEY_SIM_SLOT = "key_sim_slot"
        private const val KEY_IS_RUNNING = "key_is_running"
        private const val KEY_TOTAL_COUNT = "key_total_count"
        private const val KEY_SUCCESS_COUNT = "key_success_count"
        private const val KEY_FAILED_COUNT = "key_failed_count"
        private const val KEY_LOGS_JSON = "key_logs_json"

        const val SIM_AUTO = 0
        const val SIM_1 = 1
        const val SIM_2 = 2
        private const val MAX_LOGS_LIMIT = 15
    }

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "http://192.168.1.105:8000")?.trim() ?: "http://192.168.1.105:8000"
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value.trim()).apply()

    var selectedSimSlot: Int
        get() = prefs.getInt(KEY_SIM_SLOT, SIM_1)
        set(value) = prefs.edit().putInt(KEY_SIM_SLOT, value).apply()

    var isServiceRunning: Boolean
        get() = prefs.getBoolean(KEY_IS_RUNNING, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_RUNNING, value).apply()

    var totalSentCount: Int
        get() = prefs.getInt(KEY_TOTAL_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_COUNT, value).apply()

    var successCount: Int
        get() = prefs.getInt(KEY_SUCCESS_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_SUCCESS_COUNT, value).apply()

    var failedCount: Int
        get() = prefs.getInt(KEY_FAILED_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_FAILED_COUNT, value).apply()

    fun incrementSuccess() {
        prefs.edit()
            .putInt(KEY_TOTAL_COUNT, totalSentCount + 1)
            .putInt(KEY_SUCCESS_COUNT, successCount + 1)
            .apply()
    }

    fun incrementFailed() {
        prefs.edit()
            .putInt(KEY_FAILED_COUNT, failedCount + 1)
            .apply()
    }

    fun addLogEntry(entry: SmsLogEntry) {
        val current = getRecentLogs().toMutableList()
        current.add(0, entry) // Add to top
        while (current.size > MAX_LOGS_LIMIT) {
            current.removeAt(current.size - 1)
        }

        val jsonArray = JSONArray()
        for (item in current) {
            val obj = JSONObject().apply {
                put("timestamp", item.timestamp)
                put("phone", item.phone)
                put("status", item.status)
                put("taskId", item.taskId ?: "")
                put("error", item.error ?: "")
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_LOGS_JSON, jsonArray.toString()).apply()
    }

    fun getRecentLogs(): List<SmsLogEntry> {
        val jsonString = prefs.getString(KEY_LOGS_JSON, null) ?: return emptyList()
        val list = mutableListOf<SmsLogEntry>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    SmsLogEntry(
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        phone = obj.optString("phone", ""),
                        status = obj.optString("status", "sent"),
                        taskId = obj.optString("taskId", "").takeIf { it.isNotBlank() },
                        error = obj.optString("error", "").takeIf { it.isNotBlank() }
                    )
                )
            }
        } catch (_: Exception) {
            // Ignore parse errors
        }
        return list
    }

    fun clearLogs() {
        prefs.edit().remove(KEY_LOGS_JSON).apply()
    }
}
