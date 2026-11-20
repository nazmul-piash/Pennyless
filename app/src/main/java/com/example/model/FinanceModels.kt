package com.example.model

import androidx.annotation.Keep
import androidx.compose.ui.graphics.Color
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class AccountType(val label: String) {
    CREDIT_CARD("Credit Card"),
    BANK("Bank Account"),
    CASH("Cash"),
    SAVINGS("Savings");

    companion object {
        fun fromRaw(raw: String): AccountType =
            entries.firstOrNull { it.name == raw } ?: BANK
    }
}

enum class TxType(val label: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    TRANSFER("Transfer");

    companion object {
        fun fromRaw(raw: String): TxType =
            entries.firstOrNull { it.name == raw } ?: EXPENSE
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Alex Mercer",
    val email: String = "",
    val currency: String = "EUR",
    val monthlyBudget: Double = 2500.0,
    val seededDemoData: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FinanceAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val accountType: String = AccountType.BANK.name,
    val balance: Double = 0.0,
    val creditLimit: Double = 0.0,
    val dueDayOfMonth: Int = 0,
    val colorHex: String = "#3B82F6",
    val lastFourDigits: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val resolvedType: AccountType get() = AccountType.fromRaw(accountType)
}

@Keep
@IgnoreExtraProperties
data class FinanceCategory(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val txType: String = TxType.EXPENSE.name,
    val iconKey: String = "category",
    val colorHex: String = "#FF5A5F",
    val sortOrder: Int = 0,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val resolvedTxType: TxType get() = TxType.fromRaw(txType)
}

@Keep
@IgnoreExtraProperties
data class ReceiptLineItem(
    val itemName: String = "",
    val amount: Double = 0.0,
    val suggestedCategory: String = "Other"
)

@Keep
@IgnoreExtraProperties
data class FinanceTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val txType: String = TxType.EXPENSE.name,
    val amount: Double = 0.0,
    val currency: String = "EUR",
    val dateIso: String = "2026-11-19",
    val merchant: String = "",
    val categoryName: String = "Other",
    val subcategory: String = "",
    val accountId: String = "",
    val accountName: String = "",
    val note: String = "",
    val needsReview: Boolean = false,
    val receiptThumbnail: String? = null,
    val aiConfidence: Double? = null,
    val lineItems: List<ReceiptLineItem>? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val resolvedTxType: TxType get() = TxType.fromRaw(txType)
}

@Keep
@IgnoreExtraProperties
data class RecurringBill(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val categoryName: String = "Bills & Utilities",
    val accountId: String = "",
    val accountName: String = "",
    val dayOfMonth: Int = 1,
    val autoPay: Boolean = true,
    val lastPaidMonth: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class ClarificationQuestion(
    val fieldKey: String,
    val question: String,
    val quickOptions: List<String> = emptyList(),
    val inputType: String = "text"
)

data class ExtractedReceiptResult(
    val merchant: String,
    val date: String,
    val totalAmount: Double,
    val currency: String,
    val primaryCategory: String,
    val subcategory: String,
    val paymentMethodDetected: String,
    val note: String,
    val confidenceScore: Double,
    val needsManualReview: Boolean,
    val reviewReason: String?,
    val lineItems: List<ReceiptLineItem>,
    val transactionType: String = "Expense",
    val spokenTranscript: String? = null,
    val missingFields: List<String> = emptyList(),
    val clarificationQuestions: List<ClarificationQuestion> = emptyList()
)

data class ImportedStatementRow(
    val id: String,
    val dateIso: String,
    val merchant: String,
    val amount: Double,
    val currency: String,
    val txType: String = TxType.EXPENSE.name,
    val categoryName: String = "Other",
    val subcategory: String = "Statement Import",
    val note: String = "",
    val selected: Boolean = true
)

data class FinanceDashboardSnapshot(
    val profile: UserProfile = UserProfile(),
    val accounts: List<FinanceAccount> = emptyList(),
    val categories: List<FinanceCategory> = emptyList(),
    val transactions: List<FinanceTransaction> = emptyList(),
    val recurringBills: List<RecurringBill> = emptyList()
)

fun currencySymbol(code: String): String = when (code.uppercase(Locale.US)) {
    "EUR" -> "€"
    "USD" -> "$"
    "GBP" -> "£"
    "BDT" -> "৳"
    "JPY" -> "¥"
    "CHF" -> "CHF "
    else -> "$code "
}

fun formatMoney(amount: Double, currencyCode: String, showSymbol: Boolean = true): String {
    val symbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ','
        decimalSeparator = '.'
    }
    val formatter = DecimalFormat("#,##0.00", symbols)
    val absFormatted = formatter.format(kotlin.math.abs(amount))
    val sign = if (amount < -0.004) "-" else ""
    val prefix = if (showSymbol) currencySymbol(currencyCode) else ""
    return "$sign$prefix$absFormatted"
}

fun formatCompactMoney(amount: Double): String {
    val symbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ','
        decimalSeparator = '.'
    }
    return if (amount >= 1000.0) {
        DecimalFormat("#,##0", symbols).format(amount)
    } else {
        DecimalFormat("0.##", symbols).format(amount)
    }
}

fun parseHexColor(hex: String, fallback: Color = Color(0xFFFF5A5F)): Color {
    return try {
        val cleaned = hex.removePrefix("#")
        val parsed = cleaned.toLong(16)
        when (cleaned.length) {
            6 -> Color((0xFF000000L or parsed).toInt())
            8 -> Color(parsed.toInt())
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}
