package com.example.core.network

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private var baseUrl = "https://localhost/api/v1/"
    private var authToken: String? = null
    private var refreshToken: String? = null
    private var securePrefs: android.content.SharedPreferences? = null
    private var currentRetrofit: Retrofit? = null
    private var cachedApiService: ApiService? = null

    fun initialize(context: Context) {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        securePrefs = EncryptedSharedPreferences.create(
            context.applicationContext,
            "xaupilot_secure_session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        authToken = securePrefs?.getString("access_token", null)
        refreshToken = securePrefs?.getString("refresh_token", null)
        securePrefs?.getString("api_base_url", null)?.let { saved ->
            if (saved.startsWith("https://", ignoreCase = true)) {
                baseUrl = if (saved.endsWith("/")) saved else "$saved/"
            }
        }
    }

    fun setAuthToken(token: String?) {
        authToken = token
        securePrefs?.edit()?.putString("access_token", token)?.apply()
    }

    fun setRefreshToken(token: String?) {
        refreshToken = token
        securePrefs?.edit()?.putString("refresh_token", token)?.apply()
    }

    fun getRefreshToken(): String? = refreshToken

    fun clearSession() {
        authToken = null
        refreshToken = null
        securePrefs?.edit()?.remove("access_token")?.remove("refresh_token")?.apply()
    }

    fun getAuthToken(): String? = authToken

    fun setBaseUrl(url: String) {
        val trimmed = url.trim()
        require(trimmed.startsWith("https://", ignoreCase = true)) {
            "Backend URL must use HTTPS. Public plain HTTP endpoints are not accepted."
        }
        require(!trimmed.contains(".example", ignoreCase = true) && !trimmed.contains("your-domain", ignoreCase = true)) {
            "Replace the example backend URL with your deployed API address."
        }
        val sanitized = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        if (this.baseUrl != sanitized) {
            this.baseUrl = sanitized
            securePrefs?.edit()?.putString("api_base_url", sanitized)?.apply()
            currentRetrofit = null
            cachedApiService = null
        }
    }

    fun getBaseUrl(): String = baseUrl

    private fun buildOkHttpClient(): OkHttpClient {
        // Redact sensitive headers in network logs
        val logging = HttpLoggingInterceptor { message ->
            val sanitized = if (message.startsWith("Authorization:", ignoreCase = true)) {
                "Authorization: Bearer [REDACTED_SECURE_TOKEN]"
            } else if (message.contains("password", ignoreCase = true)) {
                "[PAYLOAD REDACTED FOR SECURITY]"
            } else {
                message
            }
            android.util.Log.d("XauPilotNetwork", sanitized)
        }.apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("Accept", "application/json")
                    .header("User-Agent", "XauPilot-Android/2.4.0")

                authToken?.let { token ->
                    requestBuilder.header("Authorization", "Bearer $token")
                }

                chain.proceed(requestBuilder.build())
            }
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // Kotlin data classes need the Kotlin adapter; plain Moshi throws on them at the first response.
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    val apiService: ApiService
        get() {
            if (cachedApiService == null) {
                cachedApiService = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(buildOkHttpClient())
                    .addConverterFactory(MoshiConverterFactory.create(moshi))
                    .build()
                    .create(ApiService::class.java)
            }
            return cachedApiService!!
        }
}
