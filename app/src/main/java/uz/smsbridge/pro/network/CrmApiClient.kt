package uz.smsbridge.pro.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import uz.smsbridge.pro.data.model.PingResult
import uz.smsbridge.pro.data.model.PollResponse
import uz.smsbridge.pro.data.model.ReportRequest
import java.util.concurrent.TimeUnit

class CrmApiClient {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun normalizeUrl(url: String): String {
        var trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://$trimmed"
        }
        return if (trimmed.endsWith("/")) trimmed.substring(0, trimmed.length - 1) else trimmed
    }

    suspend fun ping(serverUrl: String): PingResult = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(serverUrl)
        val requestUrl = "$normalized/api/mobile/ping"
        val startTime = System.currentTimeMillis()

        try {
            val request = Request.Builder()
                .url(requestUrl)
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    var message = "Server faol"
                    try {
                        val json = JSONObject(bodyString)
                        message = json.optString("message", message)
                    } catch (_: Exception) {
                        // Plain text or other format
                    }
                    PingResult(isSuccess = true, message = message, latencyMs = elapsed)
                } else {
                    PingResult(
                        isSuccess = false,
                        message = "HTTP xatolik: ${response.code}",
                        latencyMs = elapsed
                    )
                }
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            PingResult(
                isSuccess = false,
                message = e.localizedMessage ?: "Serverga ulanib bo'lmadi",
                latencyMs = elapsed
            )
        }
    }

    suspend fun poll(serverUrl: String, simSlot: Int): PollResponse = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(serverUrl)
        val requestUrl = "$normalized/api/mobile/poll?sim_slot=$simSlot"

        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .header("Accept", "application/json")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }

            val body = response.body?.string() ?: throw Exception("Bo'sh javob keldi")
            val json = JSONObject(body)
            val hasTask = json.optBoolean("has_task", false)

            if (hasTask) {
                val taskId = json.optString("task_id", "").ifBlank {
                    json.optLong("task_id", 0L).toString()
                }
                val phone = json.optString("phone", "")
                val message = json.optString("message", "")
                val slot = if (json.has("sim_slot")) json.optInt("sim_slot") else null

                PollResponse(
                    hasTask = true,
                    taskId = taskId,
                    phone = phone,
                    message = message,
                    simSlot = slot,
                    waitSeconds = 3
                )
            } else {
                val waitSeconds = json.optInt("wait_seconds", 3)
                PollResponse(
                    hasTask = false,
                    waitSeconds = if (waitSeconds > 0) waitSeconds else 3
                )
            }
        }
    }

    suspend fun report(serverUrl: String, reportRequest: ReportRequest): Boolean =
        withContext(Dispatchers.IO) {
            val normalized = normalizeUrl(serverUrl)
            val requestUrl = "$normalized/api/mobile/report"

            val jsonBody = JSONObject().apply {
                put("task_id", reportRequest.taskId)
                put("phone", reportRequest.phone)
                put("status", reportRequest.status)
                put("error", reportRequest.error)
            }

            val request = Request.Builder()
                .url(requestUrl)
                .post(jsonBody.toString().toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }
}
