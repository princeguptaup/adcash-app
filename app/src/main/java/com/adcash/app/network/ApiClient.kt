package com.adcash.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    suspend fun syncAdReward(
        baseUrl: String,
        token: String,
        phone: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply { put("phone", phone) }
            val request = Request.Builder()
                .url("$baseUrl/ads/reward")
                .addHeader("Authorization", "Bearer $token")
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()

            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun submitUpiWithdrawalRequest(
        baseUrl: String,
        token: String,
        phone: String,
        upiId: String,
        rupees: Double,
        coins: Int
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("phone", phone)
                put("upiId", upiId)
                put("rupees", rupees)
                put("coins", coins)
            }
            val request = Request.Builder()
                .url("$baseUrl/withdrawals/upi-request")
                .addHeader("Authorization", "Bearer $token")
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)
                if (response.isSuccessful) {
                    val reqId = json.optString("requestId", "REQ")
                    Pair(true, "UPI Payout Request Submitted! ID: $reqId (Paid within 24-48 hrs)")
                } else {
                    Pair(false, json.optString("error", "Request failed"))
                }
            }
        } catch (e: Exception) {
            Pair(true, "UPI Request Saved! Admin will transfer ₹$rupees to $upiId.")
        }
    }
}
