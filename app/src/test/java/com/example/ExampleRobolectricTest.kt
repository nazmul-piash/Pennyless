package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.firebase.configureAppCheck
import com.example.firebase.getAppCheckDebugSecret
import com.example.firebase.isAppCheckErrorMessage
import com.example.firebase.saveAppCheckDebugSecret
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Penniless", appName)
  }

  @Test
  fun `configureAppCheck persists and reads debug secret cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    configureAppCheck(context, Intent())
    val generated = getAppCheckDebugSecret(context)
    assertNotNull(generated)
    assertTrue(generated!!.isNotBlank())

    val custom = "11111111-2222-3333-4444-555555555555"
    assertTrue(saveAppCheckDebugSecret(context, custom))
    assertEquals(custom, getAppCheckDebugSecret(context))
  }

  @Test
  fun `isAppCheckErrorMessage detects app check and token errors`() {
    assertTrue(isAppCheckErrorMessage("Firebase App Check token is invalid."))
    assertTrue(isAppCheckErrorMessage("HTTP 403 attestation failed"))
    assertFalse(isAppCheckErrorMessage("PDF statement is empty."))
  }
}
