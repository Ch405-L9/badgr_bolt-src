package com.badgr.orbreader.integrity

import android.content.Context
import android.util.Log
import com.badgr.orbreader.BuildConfig
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/**
 * Play Integrity probe — REPORT-ONLY / FAIL-OPEN.
 *
 * Requests a Standard integrity token, sends it to the backend for decoding, and logs
 * the verdict for observability. It NEVER blocks the user: every failure path is
 * swallowed and the app continues exactly as if the check never ran. Enforcement is a
 * deliberate later step and must stay fail-open on optional verdict fields.
 */
object IntegrityChecker {

    private const val TAG = "IntegrityChecker"

    // Google Cloud project linked to the Play Console app (badgr-bolt-play-integrity).
    private const val CLOUD_PROJECT_NUMBER = 812014525614L

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient()

    // Warm-up is expensive; the token provider is cached after first preparation.
    @Volatile private var provider: StandardIntegrityTokenProvider? = null

    suspend fun runReportOnlyCheck(context: Context) {
        try {
            val prov = provider ?: prepare(context).also { provider = it }
            val requestHash = sha256(UUID.randomUUID().toString())
            val token = prov.request(
                StandardIntegrityTokenRequest.builder()
                    .setRequestHash(requestHash)
                    .build()
            ).await().token()
            reportToBackend(token)
        } catch (e: Exception) {
            Log.w(TAG, "Integrity check skipped (fail-open): ${e.message}")
        }
    }

    private suspend fun prepare(context: Context): StandardIntegrityTokenProvider {
        val manager = IntegrityManagerFactory.createStandard(context.applicationContext)
        return manager.prepareIntegrityToken(
            PrepareIntegrityTokenRequest.builder()
                .setCloudProjectNumber(CLOUD_PROJECT_NUMBER)
                .build()
        ).await()
    }

    private fun reportToBackend(token: String) {
        try {
            val body = JSONObject().put("integrityToken", token).toString().toRequestBody(JSON)
            val req = Request.Builder()
                .url("${BuildConfig.BACKEND_BASE_URL}/verify-integrity")
                .post(body)
                .build()
            http.newCall(req).execute().use { resp ->
                val txt = resp.body?.string().orEmpty()
                Log.i(TAG, "Verdict (report-only): $txt")
                FirebaseCrashlytics.getInstance()
                    .setCustomKey("integrity_last_reason", parseReason(txt))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Report failed (fail-open): ${e.message}")
        }
    }

    private fun parseReason(json: String): String =
        try { JSONObject(json).optString("reason", "unknown") } catch (_: Exception) { "parse_error" }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
