package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.firebase.formatAiTokenError
import com.example.firebase.verifyAppCheckAndAuthState
import com.example.model.ClarificationQuestion
import com.example.model.ExtractedReceiptResult
import com.example.model.ImportedStatementRow
import com.example.model.ReceiptLineItem
import com.example.model.TxType
import com.example.util.StatementParser
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "ReceiptAiService"

object ReceiptAiService {

    private val primaryCategories = listOf(
        "Food & Dining",
        "Transport",
        "Shopping",
        "Bills & Utilities",
        "Health & Fitness",
        "Entertainment",
        "Transfer / Income",
        "Other"
    )

    private val paymentMethods = listOf(
        "Credit Card",
        "Debit",
        "Cash",
        "Unknown"
    )

    private val transactionTypes = listOf(
        "Expense",
        "Income"
    )

    private val receiptSchema = Schema.obj(
        properties = mapOf(
            "merchant" to Schema.string("Name of the merchant, store, or vendor"),
            "date" to Schema.string("Transaction date in YYYY-MM-DD format"),
            "total_amount" to Schema.double("Total transaction amount as a positive decimal number"),
            "currency" to Schema.string("3-letter ISO currency code; default to EUR if ambiguous"),
            "primary_category" to Schema.enumeration(
                values = primaryCategories,
                description = "Exact primary expense/income category"
            ),
            "subcategory" to Schema.string("Concise subcategory like Supermarket, Coffee, Fuel, Credit Card Bill"),
            "payment_method_detected" to Schema.enumeration(
                values = paymentMethods,
                description = "Payment method shown on the receipt"
            ),
            "note" to Schema.string("Short 3-5 word summary note for the ledger"),
            "confidence_score" to Schema.double("Confidence score from 0.0 to 1.0"),
            "needs_manual_review" to Schema.boolean("True if blurry, cut off, or any field is uncertain"),
            "review_reason" to Schema.string(
                description = "Short explanation of which field is uncertain when needs_manual_review is true",
                nullable = true
            ),
            "line_items" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "item_name" to Schema.string("Item description"),
                        "amount" to Schema.double("Line item price"),
                        "suggested_category" to Schema.enumeration(
                            values = primaryCategories,
                            description = "Category best matching this specific item"
                        )
                    )
                ),
                description = "Individual line items visible on the receipt"
            )
        ),
        optionalProperties = listOf("review_reason")
    )

    private val voiceExtractionSchema = Schema.obj(
        properties = mapOf(
            "transaction_type" to Schema.enumeration(
                values = transactionTypes,
                description = "Expense for purchases/payments/bills, or Income for sales/salary/refunds"
            ),
            "merchant" to Schema.string("Normalized item/merchant name, e.g. Local Market, Green Chili, Customer Sale"),
            "date" to Schema.string("Transaction date in YYYY-MM-DD format"),
            "total_amount" to Schema.double("Total amount as decimal; 0.0 if omitted by the speaker"),
            "currency" to Schema.string("3-letter ISO currency code inferred from speech (e.g. BDT, INR, ETB, EUR, USD) or user default"),
            "primary_category" to Schema.enumeration(
                values = primaryCategories,
                description = "Exact primary expense/income category"
            ),
            "subcategory" to Schema.string("Concise subcategory like Groceries / Produce, Coffee, Sales Revenue, Transport"),
            "payment_method_detected" to Schema.enumeration(
                values = paymentMethods,
                description = "Detected payment method; default to Cash when spoken without specifying a card"
            ),
            "note" to Schema.string("Normalized English label + original local term/quantity, e.g. Green Chili (Kacha Morich) - 1 kg"),
            "transcript" to Schema.string("Short transcript or translation of what was spoken"),
            "confidence_score" to Schema.double("Confidence score from 0.0 to 1.0"),
            "needs_manual_review" to Schema.boolean("True if audio was unclear or key fields are missing"),
            "review_reason" to Schema.string(
                description = "Short note on any uncertainty or missing detail",
                nullable = true
            ),
            "missing_fields" to Schema.array(
                items = Schema.string("Field key such as total_amount, transaction_type, primary_category, or payment_method_detected"),
                description = "Empty if all required details were spoken; otherwise list exact missing or ambiguous field keys"
            ),
            "clarification_questions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "field_key" to Schema.string("Matches missing_fields entry e.g. total_amount, transaction_type, primary_category, payment_method_detected"),
                        "question" to Schema.string("Natural, friendly single-sentence follow-up question referencing the item"),
                        "quick_options" to Schema.array(
                            items = Schema.string(),
                            description = "2 to 4 concise quick-tap answers relevant to the question"
                        ),
                        "input_type" to Schema.enumeration(
                            values = listOf("amount", "choice", "text"),
                            description = "Expected answer type"
                        )
                    )
                ),
                description = "Targeted follow-up questions for each entry in missing_fields; empty when nothing is missing"
            ),
            "line_items" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "item_name" to Schema.string("Normalized item description"),
                        "amount" to Schema.double("Line item amount (0.0 if unknown)"),
                        "suggested_category" to Schema.enumeration(
                            values = primaryCategories,
                            description = "Category best matching this item"
                        )
                    )
                ),
                description = "Individual items mentioned by the speaker"
            )
        ),
        optionalProperties = listOf("review_reason")
    )

    private val statementSchema = Schema.obj(
        properties = mapOf(
            "detected_account_hint" to Schema.string(
                description = "Bank or card name printed on the statement (e.g. TF Bank Mastercard Gold, Extrakarte)",
                nullable = true
            ),
            "transactions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "date" to Schema.string("Transaction date in YYYY-MM-DD format"),
                        "merchant" to Schema.string("Clean merchant, vendor, or transaction description"),
                        "amount" to Schema.double("Positive transaction amount magnitude"),
                        "currency" to Schema.string("3-letter ISO currency code such as EUR or USD"),
                        "type" to Schema.enumeration(
                            values = transactionTypes,
                            description = "Expense for charges/purchases/fees or Income for payments/refunds/deposits"
                        ),
                        "category" to Schema.enumeration(
                            values = primaryCategories,
                            description = "Best matching primary category"
                        ),
                        "note" to Schema.string("Optional concise 3-6 word ledger note", nullable = true)
                    ),
                    optionalProperties = listOf("note")
                ),
                description = "All line-item financial transactions extracted from the PDF statement"
            )
        ),
        optionalProperties = listOf("detected_account_hint")
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = receiptSchema
            }
        )
    }

    private val voiceModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = voiceExtractionSchema
            }
        )
    }

    private val statementModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = statementSchema
            }
        )
    }

    suspend fun analyzeReceipt(
        bitmap: Bitmap,
        defaultCurrency: String,
        fallbackDateIso: String
    ): Result<Pair<ExtractedReceiptResult, String>> = withContext(Dispatchers.IO) {
        var jpegBytes: ByteArray? = null
        try {
            verifyAppCheckAndAuthState()
            val softwareBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }
            val scaledForAi = scaleBitmap(softwareBitmap, maxDimension = 1280)
            jpegBytes = compressBitmapToJpegBytes(scaledForAi)
            val thumbnailBase64 = createThumbnailBase64(softwareBitmap)

            val prompt = """
                You are the financial vision and data-extraction engine for a personal finance app.
                Analyze this receipt, invoice, payment screenshot, or handwritten bill and extract structured transaction data.
                Rules:
                1. Identify merchant/vendor name, date (YYYY-MM-DD, fallback to $fallbackDateIso if missing), currency (default to $defaultCurrency if ambiguous), total amount, and visible line items.
                2. Classify into one of the exact primary_category enum values.
                3. Provide a concise subcategory (e.g. Supermarket, Coffee, Transit, Pharmacy) and a short 3-5 word ledger note.
                4. For multi-category receipts (e.g. groceries + electronics), tag each item in line_items with its appropriate suggested_category so the user can split them.
                5. Set confidence_score (0.0 to 1.0). If any text is blurry, cropped, or uncertain, set needs_manual_review to true and explain in review_reason.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        inlineData(jpegBytes!!, "image/jpeg")
                        text(prompt)
                    }
                )
            }

            val rawJson = response.text
                ?: return@withContext Result.failure(IllegalStateException("Gemini returned an empty response."))

            val json = JSONObject(rawJson)
            val lineItems = parseLineItems(json.optJSONArray("line_items"))
            val parsedCategory = json.optString("primary_category", "Other")
            val safeDate = json.optString("date", fallbackDateIso).let {
                if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(it)) it else fallbackDateIso
            }

            val extracted = ExtractedReceiptResult(
                merchant = json.optString("merchant", "Unknown Merchant").ifBlank { "Unknown Merchant" }.take(80),
                date = safeDate,
                totalAmount = json.optDouble("total_amount", 0.0).coerceAtLeast(0.01),
                currency = json.optString("currency", defaultCurrency).ifBlank { defaultCurrency }.take(8),
                primaryCategory = if (parsedCategory in primaryCategories) parsedCategory else "Other",
                subcategory = json.optString("subcategory", "General").ifBlank { "General" }.take(50),
                paymentMethodDetected = json.optString("payment_method_detected", "Unknown"),
                note = json.optString("note", "Scanned receipt").ifBlank { "Scanned receipt" }.take(200),
                confidenceScore = json.optDouble("confidence_score", 0.85).coerceIn(0.0, 1.0),
                needsManualReview = json.optBoolean("needs_manual_review", false),
                reviewReason = if (json.isNull("review_reason")) null else json.optString("review_reason").takeIf { it.isNotBlank() },
                lineItems = lineItems.take(50),
                transactionType = if (parsedCategory == "Transfer / Income") "Income" else "Expense"
            )
            Result.success(extracted to thumbnailBase64)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Receipt scan timed out", e)
            Result.failure(IllegalStateException("Receipt scan timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Receipt scan failed", e)
            Result.failure(IllegalStateException(formatAiTokenError(e), e))
        } finally {
            jpegBytes?.fill(0)
        }
    }

    suspend fun analyzeStatementPdf(
        pdfBytes: ByteArray,
        defaultCurrency: String,
        fallbackDateIso: String
    ): Result<Pair<String?, List<ImportedStatementRow>>> = withContext(Dispatchers.IO) {
        try {
            require(pdfBytes.isNotEmpty()) { "PDF statement is empty." }
            require(pdfBytes.size <= StatementParser.MAX_STATEMENT_BYTES) {
                "PDF exceeds 18 MB limit for inline statement parsing."
            }
            verifyAppCheckAndAuthState()

            val prompt = """
                You are a financial statement extraction engine for a personal finance app.
                Extract every financial transaction row from this bank or credit card PDF statement.
                Rules:
                1. Ignore opening/closing balance summary lines, headers, and legal footers; extract actual transactions only.
                2. Format every transaction date strictly as YYYY-MM-DD (convert European DD.MM.YYYY accurately; fallback to $fallbackDateIso only if year/month missing).
                3. Output amount as a positive decimal number and set type to "Expense" for purchases/charges/fees or "Income" for deposits/refunds/card repayments.
                4. Normalize currency to a 3-letter code (default to $defaultCurrency if omitted).
                5. Map each row to the closest primary category enum and include the issuer/card name in detected_account_hint if visible (e.g. TF Bank Mastercard Gold, Extrakarte).
            """.trimIndent()

            val response = withTimeout(60_000L) {
                statementModel.generateContent(
                    content {
                        inlineData(pdfBytes, "application/pdf")
                        text(prompt)
                    }
                )
            }

            val rawJson = response.text
                ?: return@withContext Result.failure(IllegalStateException("Gemini returned an empty response for the PDF statement."))

            val json = JSONObject(rawJson)
            val accountHint = if (json.isNull("detected_account_hint")) null else {
                json.optString("detected_account_hint", "").trim().takeIf { it.isNotEmpty() }
            }
            val arr = json.optJSONArray("transactions") ?: JSONArray()
            val rows = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val amount = abs(obj.optDouble("amount", 0.0))
                    if (amount.isNaN() || amount < 0.005) continue
                    val rawDate = obj.optString("date", fallbackDateIso)
                    val dateIso = StatementParser.normalizeDateToIso(rawDate, fallbackDateIso) ?: fallbackDateIso
                    val merchant = obj.optString("merchant", "Statement Entry").trim().ifBlank { "Statement Entry" }.take(80)
                    val currency = obj.optString("currency", defaultCurrency).trim().ifBlank { defaultCurrency }.take(8)
                    val rawType = obj.optString("type", "Expense")
                    val txType = if (rawType.equals("Income", ignoreCase = true)) TxType.INCOME else TxType.EXPENSE
                    val rawCat = obj.optString("category", "Other")
                    val category = if (rawCat in primaryCategories) rawCat else StatementParser.inferCategory(merchant, rawCat, txType)
                    val note = if (obj.isNull("note")) "PDF statement import" else {
                        obj.optString("note", "PDF statement import").trim().ifBlank { "PDF statement import" }.take(180)
                    }

                    add(
                        ImportedStatementRow(
                            id = "pdf_${i}_${dateIso}_$amount",
                            dateIso = dateIso,
                            merchant = merchant,
                            amount = amount,
                            currency = currency,
                            txType = txType.name,
                            categoryName = category,
                            subcategory = "PDF Statement",
                            note = note,
                            selected = true
                        )
                    )
                }
            }

            if (rows.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("No transactions could be extracted from this PDF statement.")
                )
            }
            Result.success(accountHint to rows)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "PDF statement parsing timed out", e)
            Result.failure(IllegalStateException("PDF statement extraction timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "PDF statement extraction failed", e)
            Result.failure(IllegalStateException(formatAiTokenError(e), e))
        }
    }

    suspend fun analyzeVoiceAudio(
        audioBytes: ByteArray,
        defaultCurrency: String,
        todayIso: String,
        yesterdayIso: String,
        accountNames: List<String>
    ): Result<ExtractedReceiptResult> = withContext(Dispatchers.IO) {
        try {
            require(audioBytes.isNotEmpty()) { "Audio recording is empty." }
            verifyAppCheckAndAuthState()
            val prompt = buildVoiceSystemPrompt(defaultCurrency, todayIso, yesterdayIso, accountNames)
            val response = withTimeout(60_000L) {
                voiceModel.generateContent(
                    content {
                        inlineData(audioBytes, "audio/mp4")
                        text(prompt)
                    }
                )
            }
            val rawJson = response.text
                ?: return@withContext Result.failure(IllegalStateException("Gemini returned an empty response."))
            Result.success(parseVoiceExtraction(JSONObject(rawJson), defaultCurrency, todayIso))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Voice audio processing timed out", e)
            Result.failure(IllegalStateException("Voice processing timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Voice audio processing failed", e)
            Result.failure(IllegalStateException(formatAiTokenError(e), e))
        } finally {
            audioBytes.fill(0)
        }
    }

    suspend fun analyzeVoiceTranscript(
        spokenText: String,
        defaultCurrency: String,
        todayIso: String,
        yesterdayIso: String,
        accountNames: List<String>
    ): Result<ExtractedReceiptResult> = withContext(Dispatchers.IO) {
        try {
            verifyAppCheckAndAuthState()
            val prompt = buildVoiceSystemPrompt(defaultCurrency, todayIso, yesterdayIso, accountNames) +
                "\n\nUser utterance text:\n\"${spokenText.trim()}\""
            val response = withTimeout(60_000L) {
                voiceModel.generateContent(prompt)
            }
            val rawJson = response.text
                ?: return@withContext Result.failure(IllegalStateException("Gemini returned an empty response."))
            Result.success(parseVoiceExtraction(JSONObject(rawJson), defaultCurrency, todayIso))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Voice text processing timed out", e)
            Result.failure(IllegalStateException("Processing timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Voice text processing failed", e)
            Result.failure(IllegalStateException(formatAiTokenError(e), e))
        }
    }

    suspend fun refineVoiceExtraction(
        previous: ExtractedReceiptResult,
        replyText: String?,
        followUpAudioBytes: ByteArray?,
        defaultCurrency: String,
        todayIso: String,
        yesterdayIso: String,
        accountNames: List<String>
    ): Result<ExtractedReceiptResult> = withContext(Dispatchers.IO) {
        try {
            verifyAppCheckAndAuthState()
            val contextJson = JSONObject().apply {
                put("transaction_type", previous.transactionType)
                put("merchant", previous.merchant)
                put("date", previous.date)
                put("total_amount", previous.totalAmount)
                put("currency", previous.currency)
                put("primary_category", previous.primaryCategory)
                put("subcategory", previous.subcategory)
                put("payment_method_detected", previous.paymentMethodDetected)
                put("note", previous.note)
                put("transcript", previous.spokenTranscript ?: "")
                put("previously_missing_fields", JSONArray(previous.missingFields))
            }
            val prompt = """
                ${buildVoiceSystemPrompt(defaultCurrency, todayIso, yesterdayIso, accountNames)}

                You are resolving missing or ambiguous fields from a previous voice transaction extraction.
                Previous extraction JSON:
                $contextJson

                User's follow-up clarification:
                ${replyText?.takeIf { it.isNotBlank() } ?: "(See attached follow-up audio clip)"}

                Merge the user's answer into the transaction fields, keep everything else intact, and remove any resolved field from missing_fields and clarification_questions. If everything required (especially total_amount > 0, transaction_type, primary_category) is now clear, return empty missing_fields and empty clarification_questions.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                if (followUpAudioBytes != null) {
                    voiceModel.generateContent(
                        content {
                            inlineData(followUpAudioBytes, "audio/mp4")
                            text(prompt)
                        }
                    )
                } else {
                    voiceModel.generateContent(prompt)
                }
            }
            val rawJson = response.text
                ?: return@withContext Result.failure(IllegalStateException("Gemini returned an empty response."))
            Result.success(parseVoiceExtraction(JSONObject(rawJson), defaultCurrency, todayIso))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Voice clarification timed out", e)
            Result.failure(IllegalStateException("Clarification timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Voice clarification failed", e)
            Result.failure(IllegalStateException(formatAiTokenError(e), e))
        } finally {
            followUpAudioBytes?.fill(0)
        }
    }

    private fun buildVoiceSystemPrompt(
        defaultCurrency: String,
        todayIso: String,
        yesterdayIso: String,
        accountNames: List<String>
    ): String {
        val accountsHint = if (accountNames.isNotEmpty()) accountNames.joinToString(", ") else "Cash, Checking Account, Credit Card"
        return """
            You are the multilingual voice transaction extraction engine for a Money Manager personal finance app.
            Listen carefully to the user's spoken audio (or read their utterance) in ANY language, dialect, or code-switched speech (e.g. Bengali, Hindi, Arabic, Amharic, Mandarin, German, Spanish, English, or mixed sentences such as "I bought 1 kilo kacha morich for 80 taka" or "Sold 2 items for 500").
            
            Extraction Rules:
            1. transaction_type: Classify as "Expense" (buying, paying, spending, bill) or "Income" (selling, salary, received payment, refund). If genuinely ambiguous (e.g. "500 green chili" with no buy/sell verb), add "transaction_type" to missing_fields.
            2. merchant & note: Normalize item/merchant clearly in English while preserving the original local term and quantity in `note` (e.g., merchant="Local Market", note="Green Chili (Kacha Morich) - 1 kg").
            3. total_amount & currency: Infer currency from spoken currency words (Taka -> BDT, Rupee/Rs -> INR, Birr -> ETB, Euro -> EUR, Dollar -> USD, Pound -> GBP, Yuan/RMB -> CNY) or fall back to "$defaultCurrency". If no price/amount was stated, set total_amount to 0.0 and include "total_amount" in missing_fields.
            4. primary_category & subcategory: Map to one of the exact primary_category values (e.g. groceries/vegetables -> "Food & Dining" with subcategory "Groceries / Produce"; sales/income -> "Transfer / Income" with subcategory "Sales Revenue").
            5. payment_method_detected: Default to "Cash" if spoken casually without mentioning a card/bank/wallet; otherwise match "Credit Card", "Debit", or "Cash". User accounts: [$accountsHint].
            6. date: Today is $todayIso and yesterday was $yesterdayIso. Default to $todayIso unless the speaker mentioned yesterday or a specific date.
            7. Smart Clarification: Whenever a required field is missing or ambiguous (especially total_amount == 0, unclear buy vs. sale, or unclear category), populate `missing_fields` and create crisp, conversational `clarification_questions` with 2-4 helpful `quick_options`. When total_amount > 0 and transaction intent is clear, return empty `missing_fields` and empty `clarification_questions`.
        """.trimIndent()
    }

    private fun parseLineItems(itemsJson: JSONArray?): List<ReceiptLineItem> = buildList {
        if (itemsJson != null) {
            for (i in 0 until itemsJson.length()) {
                val obj = itemsJson.optJSONObject(i) ?: continue
                val name = obj.optString("item_name", "").trim()
                val amt = obj.optDouble("amount", 0.0)
                val cat = obj.optString("suggested_category", "Other")
                if (name.isNotEmpty() && amt >= 0.0) {
                    add(
                        ReceiptLineItem(
                            itemName = name.take(60),
                            amount = amt,
                            suggestedCategory = if (cat in primaryCategories) cat else "Other"
                        )
                    )
                }
            }
        }
    }

    private fun parseVoiceExtraction(
        json: JSONObject,
        defaultCurrency: String,
        todayIso: String
    ): ExtractedReceiptResult {
        val rawAmount = json.optDouble("total_amount", 0.0).let { if (it.isNaN() || it < 0.0) 0.0 else it }
        val currency = json.optString("currency", defaultCurrency).ifBlank { defaultCurrency }.take(8)
        val parsedCategory = json.optString("primary_category", "Other").let {
            if (it in primaryCategories) it else "Other"
        }
        val txType = json.optString("transaction_type", "Expense").let {
            if (it in transactionTypes) it else if (parsedCategory == "Transfer / Income") "Income" else "Expense"
        }
        val safeDate = json.optString("date", todayIso).let {
            if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(it)) it else todayIso
        }
        val paymentMethod = json.optString("payment_method_detected", "Cash").let {
            if (it == "Unknown") "Cash" else if (it in paymentMethods) it else "Cash"
        }
        val merchant = json.optString("merchant", "Voice Entry").ifBlank { "Voice Entry" }.take(80)
        val note = json.optString("note", "Voice transaction").ifBlank { "Voice transaction" }.take(200)

        val missingJson = json.optJSONArray("missing_fields")
        val missingSet = linkedSetOf<String>()
        if (missingJson != null) {
            for (i in 0 until missingJson.length()) {
                val f = missingJson.optString(i, "").trim()
                if (f.isNotEmpty()) missingSet.add(f)
            }
        }
        if (rawAmount <= 0.0) {
            missingSet.add("total_amount")
        }

        val questionsJson = json.optJSONArray("clarification_questions")
        val questions = buildList {
            if (questionsJson != null) {
                for (i in 0 until questionsJson.length()) {
                    val qObj = questionsJson.optJSONObject(i) ?: continue
                    val fieldKey = qObj.optString("field_key", "").trim()
                    val qText = qObj.optString("question", "").trim()
                    val inputType = qObj.optString("input_type", "text")
                    val optsJson = qObj.optJSONArray("quick_options")
                    val opts = buildList {
                        if (optsJson != null) {
                            for (j in 0 until optsJson.length()) {
                                val opt = optsJson.optString(j, "").trim()
                                if (opt.isNotEmpty()) add(opt)
                            }
                        }
                    }
                    if (fieldKey.isNotEmpty() && qText.isNotEmpty()) {
                        add(
                            ClarificationQuestion(
                                fieldKey = fieldKey,
                                question = qText,
                                quickOptions = opts,
                                inputType = inputType
                            )
                        )
                    }
                }
            }
        }.toMutableList()

        if ("total_amount" in missingSet && questions.none { it.fieldKey == "total_amount" }) {
            questions.add(
                0,
                ClarificationQuestion(
                    fieldKey = "total_amount",
                    question = "How much did \"$note\" cost ($currency)?",
                    quickOptions = listOf("50", "100", "250", "500"),
                    inputType = "amount"
                )
            )
        }

        return ExtractedReceiptResult(
            merchant = merchant,
            date = safeDate,
            totalAmount = rawAmount,
            currency = currency,
            primaryCategory = parsedCategory,
            subcategory = json.optString("subcategory", "General").ifBlank { "General" }.take(50),
            paymentMethodDetected = paymentMethod,
            note = note,
            confidenceScore = json.optDouble("confidence_score", 0.90).coerceIn(0.0, 1.0),
            needsManualReview = json.optBoolean("needs_manual_review", missingSet.isNotEmpty()) || missingSet.isNotEmpty(),
            reviewReason = if (json.isNull("review_reason")) null else json.optString("review_reason").takeIf { it.isNotBlank() },
            lineItems = parseLineItems(json.optJSONArray("line_items")).filter { it.amount > 0.0 }.take(50),
            transactionType = txType,
            spokenTranscript = json.optString("transcript", "").takeIf { it.isNotBlank() },
            missingFields = missingSet.toList(),
            clarificationQuestions = questions
        )
    }

    private fun scaleBitmap(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width
        val height = source.height
        val largest = max(width, height)
        if (largest <= maxDimension) return source
        val scale = maxDimension.toFloat() / largest.toFloat()
        return Bitmap.createScaledBitmap(
            source,
            (width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1),
            true
        )
    }

    private fun compressBitmapToJpegBytes(bitmap: Bitmap): ByteArray {
        var quality = 85
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 15
        } while (bytes.size > 4 * 1024 * 1024 && quality >= 40)
        return bytes
    }

    private fun createThumbnailBase64(source: Bitmap): String {
        val thumb = scaleBitmap(source, maxDimension = 240)
        val out = ByteArrayOutputStream()
        thumb.compress(Bitmap.CompressFormat.JPEG, 70, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}

