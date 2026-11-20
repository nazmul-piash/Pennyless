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
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                // Synchronous commit() ensures the debug token is persisted before provider init
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
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

fun formatAiTokenError(error: Throwable): String {
    val raw = generateSequence(error) { it.cause }
        .mapNotNull { it.message ?: it.localizedMessage }
        .joinToString(" | ")
    val lower = raw.lowercase()
    return when {
        "app check" in lower ||
            "attestation" in lower ||
            "firebase" in lower && "token" in lower ||
            "403" in lower ||
            "permission_denied" in lower ||
            "insufficient" in lower ||
            "unauthenticated" in lower -> {
            "Firebase App Check / token verification was rejected (${error.localizedMessage ?: "403"}). " +
                "Verify you are signed in with Google and connected to the internet. " +
                "If testing a local Android Studio debug build, ensure your debug keystore matches Firebase or register the DebugAppCheckProvider token from Logcat in Firebase Console → App Check."
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
