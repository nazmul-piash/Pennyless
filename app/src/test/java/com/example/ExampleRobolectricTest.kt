package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.ai.ReceiptAiService
import com.example.firebase.configureAppCheck
import com.example.firebase.getAppCheckDebugSecret
import com.example.firebase.isAppCheckErrorMessage
import com.example.firebase.saveAppCheckDebugSecret
import com.example.model.TxType
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

  @Test
  fun `parses lean streamed pdf json without note property`() {
    val json = """
      {
        "detected_account_hint": "TF Bank Mastercard Gold",
        "transactions": [
          {
            "date": "14.11.2026",
            "merchant": "REWE SAGT DANKE",
            "amount": 28.45,
            "currency": "EUR",
            "type": "Expense",
            "category": "Food & Dining"
          },
          {
            "date": "2026-11-15",
            "merchant": "Lastschrift Einzug",
            "amount": 150.0,
            "currency": "EUR",
            "type": "Income",
            "category": "Transfer / Income"
          }
        ]
      }
    """.trimIndent()

    val (hint, rows) = ReceiptAiService.parseStatementResponseJson(
      rawJson = json,
      defaultCurrency = "EUR",
      fallbackDateIso = "2026-11-19"
    )

    assertEquals("TF Bank Mastercard Gold", hint)
    assertEquals(2, rows.size)
    assertEquals("2026-11-14", rows[0].dateIso)
    assertEquals("REWE SAGT DANKE", rows[0].merchant)
    assertEquals("PDF statement import", rows[0].note)
    assertEquals(TxType.INCOME.name, rows[1].txType)
  }

  @Test
  fun `recovers completed transactions from truncated streamed json`() {
    val truncated = """
      {
        "detected_account_hint": "Extrakarte",
        "transactions": [
          {
            "date": "2026-11-01",
            "merchant": "Cafe {Central} \"Berlin\"",
            "amount": 9.80,
            "currency": "EUR",
            "type": "Expense",
            "category": "Food & Dining"
          },
          {
            "date": "2026-11-03",
            "merchant": "BVG",
            "amount": 3.50,
            "currency": "EUR",
            "type": "Expense",
            "category": "Transport"
          },
          {
            "date": "2026-11-04",
            "merchant": "Incomplete trailing item
    """.trimIndent()

    val recovered = ReceiptAiService.recoverPartialStatementJson(
      partialText = truncated,
      defaultCurrency = "EUR",
      fallbackDateIso = "2026-11-19"
    )

    assertNotNull(recovered)
    assertEquals("Extrakarte", recovered!!.first)
    assertEquals(2, recovered.second.size)
    assertEquals("Cafe {Central} \"Berlin\"", recovered.second[0].merchant)
    assertEquals("BVG", recovered.second[1].merchant)
  }
}
