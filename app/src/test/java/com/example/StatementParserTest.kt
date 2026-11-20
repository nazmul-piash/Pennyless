package com.example

import com.example.model.TxType
import com.example.util.StatementParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementParserTest {

    @Test
    fun `parses european semicolon csv with dd_mm_yyyy dates and comma decimals`() {
        val csv = """
            Kontoauszug;TF Bank Mastercard Gold
            IBAN;DE1234567890

            Buchungstag;Empfänger;Verwendungszweck;Betrag;Währung
            14.11.2026;REWE Markt GmbH;Weekly groceries;-42,85;EUR
            15.11.2026;DB Fernverkehr AG;ICE Ticket Berlin;-1.234,50;EUR
            16.11.2026;TechCorp GmbH;Salary Refund / Bonus;2.500,00;EUR
        """.trimIndent()

        val rows = StatementParser.parseCsvText(
            csvText = csv,
            defaultCurrency = "EUR",
            fallbackDateIso = "2026-11-19"
        )

        assertEquals(3, rows.size)

        assertEquals("2026-11-14", rows[0].dateIso)
        assertEquals(42.85, rows[0].amount, 0.001)
        assertEquals("EUR", rows[0].currency)
        assertEquals(TxType.EXPENSE.name, rows[0].txType)
        assertEquals("Food & Dining", rows[0].categoryName)
        assertTrue(rows[0].merchant.contains("REWE"))

        assertEquals("2026-11-15", rows[1].dateIso)
        assertEquals(1234.50, rows[1].amount, 0.001)
        assertEquals(TxType.EXPENSE.name, rows[1].txType)
        assertEquals("Transport", rows[1].categoryName)

        assertEquals("2026-11-16", rows[2].dateIso)
        assertEquals(2500.00, rows[2].amount, 0.001)
        assertEquals(TxType.INCOME.name, rows[2].txType)
        assertEquals("Transfer / Income", rows[2].categoryName)
    }

    @Test
    fun `parses comma delimited csv with quoted fields and parenthesized negatives`() {
        val csv = """
            Transaction Date,Merchant,Amount,Currency
            2026-11-10,"Coffee, Roastery & Bakery",(12.50),EUR
            2026-11-11,Netflix.com,-17.99,EUR
            2026-11-12,Amazon EU S.a.r.l.,-89.95,USD
        """.trimIndent()

        val rows = StatementParser.parseCsvText(
            csvText = csv,
            defaultCurrency = "EUR",
            fallbackDateIso = "2026-11-19"
        )

        assertEquals(3, rows.size)
        assertEquals("Coffee, Roastery & Bakery", rows[0].merchant)
        assertEquals(12.50, rows[0].amount, 0.001)
        assertEquals(TxType.EXPENSE.name, rows[0].txType)
        assertEquals("Food & Dining", rows[0].categoryName)

        assertEquals("Entertainment", rows[1].categoryName)
        assertEquals(17.99, rows[1].amount, 0.001)

        assertEquals("Shopping", rows[2].categoryName)
        assertEquals("USD", rows[2].currency)
    }

    @Test
    fun `parses headerless csv fallback`() {
        val csv = """
            01/11/2026;Vodafone GmbH;-39,99
            02/11/2026;Apotheke am Markt;-14,20
        """.trimIndent()

        val rows = StatementParser.parseCsvText(
            csvText = csv,
            defaultCurrency = "EUR",
            fallbackDateIso = "2026-11-19"
        )

        assertEquals(2, rows.size)
        assertEquals("2026-11-01", rows[0].dateIso)
        assertEquals("Bills & Utilities", rows[0].categoryName)
        assertEquals(39.99, rows[0].amount, 0.001)

        assertEquals("2026-11-02", rows[1].dateIso)
        assertEquals("Health & Fitness", rows[1].categoryName)
        assertEquals(14.20, rows[1].amount, 0.001)
    }

    @Test
    fun `parseSignedAmount handles european and us formats`() {
        assertEquals(-12.50, StatementParser.parseSignedAmount("-12,50")!!, 0.001)
        assertEquals(1234.56, StatementParser.parseSignedAmount("1.234,56 EUR")!!, 0.001)
        assertEquals(-1250.75, StatementParser.parseSignedAmount("-1,250.75")!!, 0.001)
        assertEquals(-89.99, StatementParser.parseSignedAmount("(89.99)")!!, 0.001)
    }
}
