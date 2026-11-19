package com.adcash.app.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SecureStorage(context: Context) {
    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            "adcash_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var authToken: String?
        get() = prefs.getString("auth_token", null)
        set(value) = prefs.edit().putString("auth_token", value).apply()

    var phoneNumber: String
        get() = prefs.getString("phone_number", "") ?: ""
        set(value) = prefs.edit().putString("phone_number", value).apply()

    var earnedCoins: Int
        get() = prefs.getInt("earned_coins", 0)
        set(value) = prefs.edit().putInt("earned_coins", value).apply()

    var withdrawnCoins: Int
        get() = prefs.getInt("withdrawn_coins", 0)
        set(value) = prefs.edit().putInt("withdrawn_coins", value).apply()

    var totalAdsWatched: Int
        get() = prefs.getInt("total_ads_watched", 0)
        set(value) = prefs.edit().putInt("total_ads_watched", value).apply()

    var rewardedAdUnitId: String
        get() = prefs.getString("ad_unit_id", "ca-app-pub-3940256099942544/5224354917")
            ?: "ca-app-pub-3940256099942544/5224354917"
        set(value) = prefs.edit().putString("ad_unit_id", value.trim()).apply()

    var serverUrl: String
        get() = prefs.getString("server_url", "https://coin-earn-api.onrender.com")
            ?: "https://coin-earn-api.onrender.com"
        set(value) = prefs.edit().putString("server_url", value.trim().trimEnd('/')).apply()

    fun getTodayAdsCount(): Int {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val savedDate = prefs.getString("ads_date", "")
        return if (savedDate == today) prefs.getInt("today_ads_count", 0) else 0
    }

    fun incrementTodayAdsCount() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val current = getTodayAdsCount()
        prefs.edit()
            .putString("ads_date", today)
            .putInt("today_ads_count", current + 1)
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
