package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.ImportedStatementRow
import com.example.model.TxType
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.abs

data class InMemoryDocument(
    val bytes: ByteArray,
    val displayName: String,
    val isPdf: Boolean
)

object StatementParser {

    const val MAX_STATEMENT_BYTES = 18 * 1024 * 1024 // Keep well under 20MB inline AI limit

    fun readUriBytesInMemory(context: Context, uri: Uri): Result<InMemoryDocument> = runCatching {
        val resolver = context.contentResolver
        val displayName = resolveDisplayName(context, uri)
        val mimeType = resolver.getType(uri)?.lowercase(Locale.US).orEmpty()

        val buffer = ByteArray(16 * 1024)
        val out = ByteArrayOutputStream()
        var totalRead = 0
        try {
            val stream = resolver.openInputStream(uri)
                ?: throw IllegalStateException("Could not open the selected file.")
            stream.use { input ->
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    totalRead += count
                    if (totalRead > MAX_STATEMENT_BYTES) {
                        throw IllegalArgumentException("Statement exceeds the 18 MB in-memory processing limit.")
                    }
                    out.write(buffer, 0, count)
                }
            }
        } finally {
            buffer.fill(0)
        }

        val bytes = out.toByteArray()
        out.reset()
        if (bytes.isEmpty()) {
            throw IllegalStateException("The selected file is empty.")
        }

        val isPdfMagic = bytes.size >= 4 &&
            bytes[0] == '%'.code.toByte() &&
            bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'D'.code.toByte() &&
            bytes[3] == 'F'.code.toByte()
        val isPdf = isPdfMagic ||
            mimeType.contains("pdf") ||
            displayName.lowercase(Locale.US).endsWith(".pdf")

        InMemoryDocument(
            bytes = bytes,
            displayName = displayName,
            isPdf = isPdf
        )
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "statement"
        } catch (_: Exception) {
            "statement"
        }
    }

    /**
     * Parses CSV bank/card statement purely in memory (supports ',' and ';' delimiters,
     * DD.MM.YYYY / YYYY-MM-DD / DD/MM/YYYY dates, and European comma decimals like 12,50 or 1.234,50).
     */
    fun parseCsvStatementInMemory(
        rawBytes: ByteArray,
        defaultCurrency: String,
        fallbackDateIso: String
    ): Result<List<ImportedStatementRow>> = runCatching {
        val text = decodeUtf8Text(rawBytes)
        parseCsvText(text, defaultCurrency, fallbackDateIso)
    }

    fun parseCsvText(
        csvText: String,
        defaultCurrency: String,
        fallbackDateIso: String
    ): List<ImportedStatementRow> {
        val rawLines = csvText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        require(rawLines.isNotEmpty()) { "CSV statement is empty." }

        val delimiter = detectDelimiter(rawLines.take(10))
        val parsedLines = rawLines.map { splitCsvLine(it, delimiter) }
            .filter { cols -> cols.any { it.isNotBlank() } }

        // Skip leading bank metadata lines until we hit a header or structured table row
        val headerIdx = findHeaderRowIndex(parsedLines)
        val dataStartIdx: Int
        val colMap: CsvColumnMapping

        if (headerIdx >= 0) {
            colMap = mapHeaderColumns(parsedLines[headerIdx])
            dataStartIdx = headerIdx + 1
        } else {
            // Headerless CSV fallback
            colMap = inferPositionalColumns(parsedLines)
            dataStartIdx = 0
        }

        val rows = mutableListOf<ImportedStatementRow>()
        for (i in dataStartIdx until parsedLines.size) {
            val cols = parsedLines[i]
            if (cols.size < 2) continue

            val rawDate = colMap.dateIdx?.let { cols.getOrNull(it) }.orEmpty()
            val isoDate = normalizeDateToIso(rawDate, fallbackDateIso) ?: continue

            val parsedSignedAmount = resolveRowSignedAmount(cols, colMap) ?: continue
            if (abs(parsedSignedAmount) < 0.005) continue

            val rawMerchant = colMap.merchantIndices
                .mapNotNull { idx -> cols.getOrNull(idx)?.trim()?.takeIf { it.isNotEmpty() } }
                .distinct()
                .joinToString(" · ")
                .ifBlank { "Bank Transaction" }
                .take(80)

            val rawCurrency = colMap.currencyIdx?.let { cols.getOrNull(it)?.trim() }
                ?.uppercase(Locale.US)
                ?.takeIf { it.length in 3..5 && it.all { ch -> ch.isLetter() } }
                ?: defaultCurrency.ifBlank { "EUR" }

            val rawType = colMap.typeIdx?.let { cols.getOrNull(it)?.trim()?.lowercase(Locale.US) }.orEmpty()
            val txType = when {
                "income" in rawType || "credit" in rawType || "gutschrift" in rawType || "einnahme" in rawType -> TxType.INCOME
                "expense" in rawType || "debit" in rawType || "ausgabe" in rawType || "lastschrift" in rawType -> TxType.EXPENSE
                parsedSignedAmount < 0.0 -> TxType.EXPENSE
                colMap.preferPositiveAsExpense && colMap.creditIdx == null -> TxType.EXPENSE
                else -> TxType.INCOME
            }

            val rawCatHint = colMap.categoryIdx?.let { cols.getOrNull(it)?.trim() }.orEmpty()
            val categoryName = inferCategory(rawMerchant, rawCatHint, txType)

            rows.add(
                ImportedStatementRow(
                    id = "csv_${i}_${isoDate}_${abs(parsedSignedAmount)}",
                    dateIso = isoDate,
                    merchant = rawMerchant,
                    amount = abs(parsedSignedAmount),
                    currency = rawCurrency,
                    txType = txType.name,
                    categoryName = categoryName,
                    subcategory = "CSV Statement",
                    note = "Imported from statement",
                    selected = true
                )
            )
        }

        require(rows.isNotEmpty()) {
            "No valid transaction rows could be parsed from this CSV file."
        }
        return rows
    }

    private fun decodeUtf8Text(bytes: ByteArray): String {
        val text = bytes.toString(Charsets.UTF_8)
        return text.removePrefix("\uFEFF")
    }

    private fun detectDelimiter(sampleLines: List<String>): Char {
        var semicolons = 0
        var commas = 0
        for (line in sampleLines) {
            var inQuotes = false
            for (ch in line) {
                when {
                    ch == '"' -> inQuotes = !inQuotes
                    !inQuotes && ch == ';' -> semicolons++
                    !inQuotes && ch == ',' -> commas++
                }
            }
        }
        return if (semicolons >= commas && semicolons > 0) ';' else ','
    }

    fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == delimiter && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.setLength(0)
                }
                else -> current.append(ch)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    private data class CsvColumnMapping(
        val dateIdx: Int?,
        val merchantIndices: List<Int>,
        val amountIdx: Int?,
        val debitIdx: Int?,
        val creditIdx: Int?,
        val currencyIdx: Int?,
        val categoryIdx: Int?,
        val typeIdx: Int?,
        val preferPositiveAsExpense: Boolean
    )

    private fun findHeaderRowIndex(lines: List<List<String>>): Int {
        val keywords = listOf(
            "date", "datum", "buchung", "valuta",
            "amount", "betrag", "umsatz", "debit", "credit", "wert",
            "merchant", "payee", "description", "verwendungszweck", "empfänger", "empfaenger", "auftraggeber"
        )
        return lines.take(15).indexOfFirst { cols ->
            if (cols.size < 2) return@indexOfFirst false
            val matches = cols.count { cell ->
                val lower = cell.lowercase(Locale.US)
                keywords.any { kw -> kw in lower }
            }
            matches >= 2
        }
    }

    private fun mapHeaderColumns(header: List<String>): CsvColumnMapping {
        val normalized = header.map { it.lowercase(Locale.US).trim() }

        fun findFirst(vararg tokens: String): Int? =
            normalized.indexOfFirst { h -> tokens.any { t -> t in h } }.takeIf { it >= 0 }

        val dateIdx = findFirst("buchungstag", "transaction date", "booking date", "date", "datum", "valuta", "wertstellung")
        val merchantIndices = normalized.mapIndexedNotNull { idx, h ->
            if (
                "merchant" in h || "payee" in h || "empfänger" in h || "empfaenger" in h ||
                "auftraggeber" in h || "begünstigter" in h || "beguenstigter" in h ||
                "description" in h || "verwendungszweck" in h || "buchungstext" in h ||
                "beschreibung" in h || "vorgang" in h || "partner" in h || "memo" in h || "note" in h || "title" in h
            ) idx else null
        }.ifEmpty {
            listOfNotNull(normalized.indices.firstOrNull { it != dateIdx })
        }

        val amountIdx = normalized.indexOfFirst { h ->
            ("betrag" in h || "amount" in h || "umsatz" in h || "summe" in h || "wert" in h) &&
                "saldo" !in h && "balance" !in h
        }.takeIf { it >= 0 }
        val debitIdx = findFirst("debit", "ausgabe", "belastung", "soll")
        val creditIdx = findFirst("credit", "einnahme", "gutschrift", "haben")
        val currencyIdx = findFirst("währung", "waehrung", "currency", "ccy")
        val categoryIdx = findFirst("kategorie", "category")
        val typeIdx = findFirst("typ", "type", "umsatzart")

        // Credit card CSVs sometimes list charges as positive numbers without negative signs
        val headerBlob = normalized.joinToString(" ")
        val preferPositiveAsExpense = debitIdx != null || "soll" in headerBlob

        return CsvColumnMapping(
            dateIdx = dateIdx,
            merchantIndices = merchantIndices,
            amountIdx = amountIdx,
            debitIdx = debitIdx,
            creditIdx = creditIdx,
            currencyIdx = currencyIdx,
            categoryIdx = categoryIdx,
            typeIdx = typeIdx,
            preferPositiveAsExpense = preferPositiveAsExpense
        )
    }

    private fun inferPositionalColumns(lines: List<List<String>>): CsvColumnMapping {
        val sample = lines.firstOrNull { it.size >= 3 } ?: lines.first()
        val dateIdx = sample.indexOfFirst { normalizeDateToIso(it, "") != null }.takeIf { it >= 0 } ?: 0
        val amountIdx = sample.indexOfLast { idxStr -> parseSignedAmount(idxStr) != null && normalizeDateToIso(idxStr, "") == null }
            .takeIf { it >= 0 } ?: (sample.size - 1)
        val merchantIndices = sample.indices.filter { it != dateIdx && it != amountIdx }.take(2)
        return CsvColumnMapping(
            dateIdx = dateIdx,
            merchantIndices = merchantIndices,
            amountIdx = amountIdx,
            debitIdx = null,
            creditIdx = null,
            currencyIdx = null,
            categoryIdx = null,
            typeIdx = null,
            preferPositiveAsExpense = false
        )
    }

    private fun resolveRowSignedAmount(cols: List<String>, map: CsvColumnMapping): Double? {
        map.amountIdx?.let { idx ->
            cols.getOrNull(idx)?.let { raw ->
                parseSignedAmount(raw)?.let { return it }
            }
        }
        val debitVal = map.debitIdx?.let { cols.getOrNull(it) }?.let { parseSignedAmount(it) }
        val creditVal = map.creditIdx?.let { cols.getOrNull(it) }?.let { parseSignedAmount(it) }
        return when {
            debitVal != null && abs(debitVal) > 0.0 -> -abs(debitVal)
            creditVal != null && abs(creditVal) > 0.0 -> abs(creditVal)
            else -> null
        }
    }

    /**
     * Parses European and US numeric strings such as:
     * "-12,50", "1.234,50", "+450,00 EUR", "(89.99)", "-1,250.75"
     */
    fun parseSignedAmount(raw: String): Double? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return null

        val isParenthesizedNeg = cleaned.startsWith("(") && cleaned.endsWith(")")
        val hasMinus = '-' in cleaned || isParenthesizedNeg
        val numericChars = cleaned.filter { it.isDigit() || it == '.' || it == ',' }
        if (numericChars.none { it.isDigit() }) return null

        val lastComma = numericChars.lastIndexOf(',')
        val lastDot = numericChars.lastIndexOf('.')

        val normalized = when {
            lastComma >= 0 && lastDot >= 0 -> {
                if (lastComma > lastDot) {
                    // European: 1.234,56 -> remove dots, replace comma with dot
                    numericChars.replace(".", "").replace(',', '.')
                } else {
                    // US/UK: 1,234.56 -> remove commas
                    numericChars.replace(",", "")
                }
            }
            lastComma >= 0 -> {
                val commaCount = numericChars.count { it == ',' }
                val digitsAfter = numericChars.length - lastComma - 1
                if (commaCount == 1 && digitsAfter in 1..2) {
                    // European decimal: 12,50 or 12,5 -> 12.50
                    numericChars.replace(',', '.')
                } else if (commaCount == 1 && digitsAfter == 3 && numericChars.substring(0, lastComma).length <= 3) {
                    // Ambiguous 1,250 -> treat as thousands separator unless prefixed? Safe default: decimal if small or thousands? Standard CSVs use decimal comma if only comma.
                    // Wait: e.g. "12,500" rare in EUR; if all other separators are absent and 3 digits after, treat as thousands.
                    numericChars.replace(",", "")
                } else if (commaCount == 1) {
                    numericChars.replace(',', '.')
                } else {
                    numericChars.replace(",", "")
                }
            }
            else -> {
                val dotCount = numericChars.count { it == '.' }
                if (dotCount > 1) {
                    // e.g. 1.234.567
                    val idx = numericChars.lastIndexOf('.')
                    val before = numericChars.substring(0, idx).replace(".", "")
                    val after = numericChars.substring(idx)
                    if (after.length in 2..3) before + after else numericChars.replace(".", "")
                } else {
                    numericChars
                }
            }
        }

        val value = normalized.toDoubleOrNull() ?: return null
        return if (hasMinus) -abs(value) else abs(value)
    }

    /**
     * Converts DD.MM.YYYY, YYYY-MM-DD, DD/MM/YYYY, DD-MM-YYYY, YYYY/MM/DD to YYYY-MM-DD.
     * Strictly uses integer parsing (no java.time) for minSdk 24 safety.
     */
    fun normalizeDateToIso(raw: String, fallbackIso: String): String? {
        val token = raw.trim().substringBefore(' ').substringBefore('T')
        if (token.isEmpty()) return null

        // YYYY-MM-DD or YYYY/MM/DD
        Regex("^(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})$").matchEntire(token)?.let { m ->
            val y = m.groupValues[1].toIntOrNull() ?: return null
            val mo = m.groupValues[2].toIntOrNull() ?: return null
            val d = m.groupValues[3].toIntOrNull() ?: return null
            if (mo in 1..12 && d in 1..31 && y in 1990..2100) {
                return String.format(Locale.US, "%04d-%02d-%02d", y, mo, d)
            }
        }

        // DD.MM.YYYY or DD/MM/YYYY or DD-MM-YYYY
        Regex("^(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})$").matchEntire(token)?.let { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return null
            val mo = m.groupValues[2].toIntOrNull() ?: return null
            var y = m.groupValues[3].toIntOrNull() ?: return null
            if (y < 100) y += 2000
            if (mo in 1..12 && d in 1..31 && y in 1990..2100) {
                return String.format(Locale.US, "%04d-%02d-%02d", y, mo, d)
            }
        }

        return fallbackIso.takeIf { it.isNotBlank() && Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(it) }
    }

    fun inferCategory(merchant: String, categoryHint: String, txType: TxType): String {
        val known = listOf(
            "Food & Dining",
            "Transport",
            "Shopping",
            "Bills & Utilities",
            "Health & Fitness",
            "Entertainment",
            "Transfer / Income",
            "Other"
        )
        known.firstOrNull { it.equals(categoryHint, ignoreCase = true) }?.let { return it }

        if (txType == TxType.INCOME) return "Transfer / Income"

        val m = "$merchant $categoryHint".lowercase(Locale.US)
        return when {
            listOf("rewe", "edeka", "aldi", "lidl", "netto", "penny", "kaufland", "supermarkt", "grocery", "cafe", "coffee", "starbucks", "restaurant", "bistro", "bakery", "bäcker", "lieferando", "uber eats", "wolt", "mcdonald").any { it in m } -> "Food & Dining"
            listOf("db ", "bahn", "bvg", "mvg", "hvv", "uber", "bolt", "taxi", "flight", "lufthansa", "ryanair", "shell", "aral", "esso", "total", "tankstelle", "parking", "parkhaus", "transit").any { it in m } -> "Transport"
            listOf("amazon", "zalando", "uniqlo", "h&m", "zara", "ikea", "mediamarkt", "saturn", "apple", "otto", "dm ", "rossmann", "shop").any { it in m } -> "Shopping"
            listOf("vattenfall", "strom", "energy", "telekom", "vodafone", "o2 ", "internet", "miete", "rent", "stadtwerke", "insurance", "versicherung", "fee", "gebühr").any { it in m } -> "Bills & Utilities"
            listOf("apotheke", "pharmacy", "arzt", "doctor", "fitness", "mcfit", "urban sports", "gym", "clinic", "dental").any { it in m } -> "Health & Fitness"
            listOf("netflix", "spotify", "disney", "cinema", "kino", "uci", "steam", "playstation", "nintendo", "ticket", "eventim").any { it in m } -> "Entertainment"
            listOf("gehalt", "lohn", "salary", "payroll", "refund", "erstattung", "transfer", "überweisung").any { it in m } -> "Transfer / Income"
            else -> "Other"
        }
    }
}
