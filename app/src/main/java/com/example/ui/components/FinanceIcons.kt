package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.model.AccountType
import com.example.model.FinanceCategory
import com.example.model.parseHexColor

fun categoryIconForKey(key: String): ImageVector = when (key.lowercase()) {
    "restaurant", "food & dining" -> Icons.Outlined.Restaurant
    "coffee" -> Icons.Outlined.LocalCafe
    "directions_car", "transport" -> Icons.Outlined.DirectionsCar
    "shopping_bag", "shopping" -> Icons.Outlined.ShoppingBag
    "receipt_long", "bills & utilities" -> Icons.AutoMirrored.Outlined.ReceiptLong
    "favorite", "health & fitness" -> Icons.Outlined.FavoriteBorder
    "movie", "entertainment" -> Icons.Outlined.Movie
    "account_balance_wallet", "transfer / income" -> Icons.Outlined.AccountBalanceWallet
    "work", "freelance" -> Icons.Outlined.WorkOutline
    "redeem", "cashback & refund" -> Icons.Outlined.Redeem
    else -> Icons.Outlined.Category
}

fun categoryColorForName(name: String, categories: List<FinanceCategory>): Color {
    val match = categories.firstOrNull { it.name.equals(name, ignoreCase = true) }
    if (match != null) return parseHexColor(match.colorHex)
    return when (name) {
        "Food & Dining" -> Color(0xFFFF5A5F)
        "Transport" -> Color(0xFF3B82F6)
        "Shopping" -> Color(0xFF8B5CF6)
        "Bills & Utilities" -> Color(0xFFF59E0B)
        "Health & Fitness" -> Color(0xFF10B981)
        "Entertainment" -> Color(0xFFEC4899)
        "Transfer / Income" -> Color(0xFF3B82F6)
        else -> Color(0xFF6B7280)
    }
}

fun categoryIconForName(name: String, categories: List<FinanceCategory>): ImageVector {
    val match = categories.firstOrNull { it.name.equals(name, ignoreCase = true) }
    if (match != null) return categoryIconForKey(match.iconKey)
    return categoryIconForKey(name)
}

fun accountIconForType(type: AccountType): ImageVector = when (type) {
    AccountType.CREDIT_CARD -> Icons.Outlined.CreditCard
    AccountType.BANK -> Icons.Outlined.AccountBalance
    AccountType.CASH -> Icons.Outlined.Payments
    AccountType.SAVINGS -> Icons.Outlined.Savings
}

@Composable
fun rememberBase64ImageBitmap(base64: String?): ImageBitmap? {
    return remember(base64) {
        if (base64.isNullOrBlank()) null
        else try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}
