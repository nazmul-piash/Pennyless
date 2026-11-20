package com.example.firebase

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.UUID

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        if (BuildConfig.DEBUG) {
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val prefs = context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )

            val intentToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim()
            if (!intentToken.isNullOrEmpty()) {
                // Synchronous commit() ensures the debug token is persisted before provider init
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, intentToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured from launch Intent.")
            } else {
                // On physical devices launched from Android Studio / home screen without Intent extra,
                // pre-seed a stable UUID once so the user can view/copy & register it directly in-app.
                val existing = prefs.getString(DEBUG_SECRET_KEY, null)?.trim()
                if (existing.isNullOrEmpty()) {
                    val generated = UUID.randomUUID().toString()
                    prefs.edit()
                        .putString(DEBUG_SECRET_KEY, generated)
                        .commit()
                    Log.i(TAG, "Generated local App Check debug secret: $generated")
                } else {
                    Log.i(TAG, "Using stored local App Check debug secret: $existing")
                }
            }

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            val resId = context.resources.getIdentifier(
                RECAPTCHA_SITE_KEY_RES,
                "string",
                context.packageName
            )
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    Log.e(TAG, "recaptcha_site_key resource string is blank.")
                }
            } else {
                Log.e(TAG, "recaptcha_site_key string resource not found in package.")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

fun getAppCheckDebugSecret(context: Context): String? {
    if (!BuildConfig.DEBUG) return null
    return try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val prefs = context.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )
        prefs.getString(DEBUG_SECRET_KEY, null)?.trim()?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        Log.w(TAG, "Unable to read App Check debug secret", e)
        null
    }
}

fun saveAppCheckDebugSecret(context: Context, token: String): Boolean {
    if (!BuildConfig.DEBUG) return false
    val cleaned = token.trim()
    if (cleaned.isEmpty()) return false
    return try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val prefs = context.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )
        val ok = prefs.edit().putString(DEBUG_SECRET_KEY, cleaned).commit()
        if (ok) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        }
        ok
    } catch (e: Exception) {
        Log.e(TAG, "Failed to save App Check debug secret", e)
        false
    }
}

suspend fun testAppCheckAttestation(): Result<String> {
    return try {
        val currentUser = com.google.firebase.Firebase.auth.currentUser
            ?: return Result.failure(IllegalStateException("Please sign in with Google first."))
        currentUser.getIdToken(false).await()
        val tokenResult = FirebaseAppCheck.getInstance().getAppCheckToken(true).await()
        if (tokenResult.token.isNotBlank()) {
            Result.success("App Check verified cleanly. AI features are ready.")
        } else {
            Result.failure(IllegalStateException("App Check returned an empty token."))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "App Check test attestation failed", e)
        Result.failure(IllegalStateException(formatAiTokenError(e), e))
    }
}

/**
 * Ensures the signed-in Google session and App Check token are refreshed before Firebase AI calls.
 * Never swallows CancellationException and logs diagnostic hints without leaking token values.
 */
suspend fun verifyAppCheckAndAuthState() {
    try {
        val currentUser = com.google.firebase.Firebase.auth.currentUser
        if (currentUser != null) {
            currentUser.getIdToken(false).await()
        } else {
            Log.w(TAG, "No authenticated Firebase user active prior to AI request.")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not refresh Firebase user ID token cleanly: ${e.message}")
    }

    try {
        FirebaseAppCheck.getInstance().getAppCheckToken(false).await()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "App Check token pre-fetch warning: ${e.message}")
    }
}

fun isAppCheckErrorMessage(message: String?): Boolean {
    if (message.isNullOrBlank()) return false
    val lower = message.lowercase()
    return "app check" in lower ||
        "attestation" in lower ||
        "debug token" in lower ||
        "403" in lower ||
        "permission_denied" in lower
}

fun formatAiTokenError(error: Throwable): String {
    val raw = generateSequence(error) { it.cause }
        .mapNotNull { it.message ?: it.localizedMessage }
        .joinToString(" | ")
    val lower = raw.lowercase()
    return when {
        "app check" in lower ||
            "attestation" in lower ||
            ("firebase" in lower && "token" in lower) ||
            "403" in lower ||
            "permission_denied" in lower ||
            "insufficient" in lower ||
            "unauthenticated" in lower -> {
            "Firebase App Check rejected this debug build (${error.localizedMessage ?: "403"}). " +
                "Register this device's debug token below in Firebase Console → App Check → Manage debug tokens, or install a Release build."
        }
        "quota" in lower || "429" in lower || "resource_exhausted" in lower -> {
            "AI request limit temporarily reached. Please wait a moment and try again."
        }
        "network" in lower || "unable to resolve host" in lower || "timeout" in lower -> {
            "Network error while contacting Gemini AI. Please check your connection and try again."
        }
        else -> error.localizedMessage ?: "Could not process request with Gemini AI."
    }
}
