package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

interface YimlySessionService {
    suspend fun createNewSession(): Result<YimlySessionInfo>
}

data class YimlySessionInfo(
    val sessionId: String,
    val hostRoomUrl: String
)

class YimlySessionServiceImpl(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
    private val baseUrl: String = BASE_URL
) : YimlySessionService {

    companion object {
        const val TAG = "YimlySessionService"
        const val BASE_URL = "https://yimly.robinhort.link"
        const val SESSIONS_ENDPOINT = "/api/karaoke/sessions"
    }

    override suspend fun createNewSession(): Result<YimlySessionInfo> = withContext(Dispatchers.IO) {
        val endpointUrl = "$baseUrl$SESSIONS_ENDPOINT"
        Log.d(TAG, "Creating new karaoke session at: $endpointUrl")

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val emptyBody = "{}".toRequestBody(mediaType)

        val request = Request.Builder()
            .url(endpointUrl)
            .post(emptyBody)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "YimlyTV/1.0 (Android TV; Leanback)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "HTTP Response code: ${response.code}, body: $responseBody")

                if (!response.isSuccessful) {
                    val errorMsg = "Server returned error ${response.code}: $responseBody"
                    Log.e(TAG, errorMsg)
                    return@withContext Result.failure(IOException(errorMsg))
                }

                val sessionId = extractSessionId(responseBody)
                val hostRoomUrl = buildHostRoomUrl(sessionId)
                Log.i(TAG, "Successfully created session: $sessionId -> URL: $hostRoomUrl")

                Result.success(
                    YimlySessionInfo(
                        sessionId = sessionId,
                        hostRoomUrl = hostRoomUrl
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create new karaoke session: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun extractSessionId(responseBody: String): String {
        val trimmed = responseBody.trim()

        // 1. Try parsing JSON Object
        try {
            val json = JSONObject(trimmed)
            val candidateKeys = listOf(
                "id", "sessionId", "session_id", "sessionIdStr",
                "roomId", "room_id", "code", "sessionKey", "token"
            )
            for (key in candidateKeys) {
                if (json.has(key) && !json.isNull(key)) {
                    val value = json.optString(key, "").trim()
                    if (value.isNotBlank()) return value
                }
            }

            // Check nested objects: data, session, room, result
            val nestedKeys = listOf("data", "session", "room", "result", "payload")
            for (nested in nestedKeys) {
                if (json.has(nested) && !json.isNull(nested)) {
                    val obj = json.optJSONObject(nested)
                    if (obj != null) {
                        for (key in candidateKeys) {
                            if (obj.has(key) && !obj.isNull(key)) {
                                val value = obj.optString(key, "").trim()
                                if (value.isNotBlank()) return value
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Not a standard JSON object, fallback to regex search
        }

        // 2. Search for UUID pattern (standard format: 8-4-4-4-12 hex)
        val uuidRegex = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        val uuidMatch = uuidRegex.find(trimmed)
        if (uuidMatch != null) {
            return uuidMatch.value
        }

        // 3. Fallback: Clean string token
        val clean = trimmed.replace("\"", "").replace("{", "").replace("}", "").trim()
        if (clean.length in 4..128 && clean.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            return clean
        }

        throw IllegalStateException("Unable to parse session ID from response: $responseBody")
    }

    fun buildHostRoomUrl(sessionId: String): String {
        return "$baseUrl/rooms/$sessionId?role=host"
    }
}
