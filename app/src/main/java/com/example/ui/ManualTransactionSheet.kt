package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.FinanceAccount
import com.example.model.FinanceCategory
import com.example.model.FinanceTransaction
import com.example.model.TxType
import com.example.ui.theme.BlueIncome
import com.example.ui.theme.CardBorderSubtle
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.DividerHairline
import com.example.ui.theme.MidnightInk
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SurfaceGroupedBg
import com.example.ui.theme.SurfaceSubtle
import com.example.util.DateUtils

@Composable
fun ManualTransactionSheet(
    initialTransaction: FinanceTransaction?,
    initialDateIso: String,
    accounts: List<FinanceAccount>,
    categories: List<FinanceCategory>,
    currency: String,
    onSave: (FinanceTransaction, FinanceAccount) -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    var txType by remember { mutableStateOf(initialTransaction?.resolvedTxType ?: TxType.EXPENSE) }
    var amountStr by remember { mutableStateOf(initialTransaction?.amount?.toString() ?: "") }
    var dateIso by remember { mutableStateOf(initialTransaction?.dateIso ?: initialDateIso.ifBlank { DateUtils.todayIso() }) }
    var merchant by remember { mutableStateOf(initialTransaction?.merchant ?: "") }
    var subcategory by remember { mutableStateOf(initialTransaction?.subcategory ?: "") }
    var note by remember { mutableStateOf(initialTransaction?.note ?: "") }
    var selectedAccount by remember {
        mutableStateOf(
            accounts.firstOrNull { it.id == initialTransaction?.accountId } ?: accounts.firstOrNull()
        )
    }

    val filteredCategories = remember(categories, txType) {
        val target = if (txType == TxType.INCOME) TxType.INCOME else TxType.EXPENSE
        val list = categories.filter { it.resolvedTxType == target }.map { it.name }
        if (list.isNotEmpty()) list else listOf("Food & Dining", "Transport", "Shopping", "Bills & Utilities", "Other")
    }
    var selectedCategory by remember(txType) {
        mutableStateOf(
            initialTransaction?.categoryName?.takeIf { it in filteredCategories }
                ?: filteredCategories.firstOrNull()
                ?: "Other"
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = SurfaceGroupedBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("manual_close_button")) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = MidnightInk)
                }
                Text(
                    text = if (initialTransaction == null) "Add Transaction" else "Edit Transaction",
                    style = MaterialTheme.typography.titleMedium,
                    color = MidnightInk
                )
                Spacer(modifier = Modifier.size(48.dp))
            }
            HorizontalDivider(color = DividerHairline)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Expense | Income | Transfer segmented toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceSubtle)
                        .padding(4.dp)
                ) {
                    TxType.entries.forEach { type ->
                        val selected = txType == type
                        val activeColor = when (type) {
                            TxType.EXPENSE -> CoralPrimary
                            TxType.INCOME -> BlueIncome
                            TxType.TRANSFER -> MidnightInk
                        }
                        val tabBg by animateColorAsState(
                            targetValue = if (selected) Color.White else Color.Transparent,
                            animationSpec = tween(180),
                            label = "manual_type_bg"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(tabBg)
                                .clickable { txType = type }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = type.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (selected) activeColor else SlateSecondary,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    tonalElevation = 1.dp,
                    shadowElevation = 2.dp,
                    border = BorderStroke(0.5.dp, CardBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = amountStr,
                            onValueChange = { amountStr = it },
                            label = { Text("Amount ($currency)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("manual_amount_input")
                        )

                        OutlinedTextField(
                            value = dateIso,
                            onValueChange = { dateIso = it },
                            label = { Text("Date (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Account", style = MaterialTheme.typography.labelMedium, color = SlateSecondary)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            accounts.forEach { acc ->
                                FilterChip(
                                    selected = selectedAccount?.id == acc.id,
                                    onClick = { selectedAccount = acc },
                                    label = { Text(acc.name) }
                                )
                            }
                        }

                        Text("Category", style = MaterialTheme.typography.labelMedium, color = SlateSecondary)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            filteredCategories.forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = merchant,
                            onValueChange = { merchant = it },
                            label = { Text("Merchant / Payee") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("manual_merchant_input")
                        )
                        OutlinedTextField(
                            value = subcategory,
                            onValueChange = { subcategory = it },
                            label = { Text("Subcategory (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("Note") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                val isSaveEnabled = (amountStr.toDoubleOrNull() ?: 0.0) > 0.0 && selectedAccount != null
                Button(
                    onClick = {
                        val acc = selectedAccount ?: return@Button
                        val amt = amountStr.toDoubleOrNull() ?: return@Button
                        val tx = (initialTransaction ?: FinanceTransaction()).copy(
                            txType = txType.name,
                            amount = amt,
                            currency = currency,
                            dateIso = dateIso,
                            merchant = merchant.ifBlank { selectedCategory },
                            categoryName = selectedCategory,
                            subcategory = subcategory,
                            accountId = acc.id,
                            accountName = acc.name,
                            note = note
                        )
                        onSave(tx, acc)
                    },
                    enabled = isSaveEnabled,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (txType == TxType.INCOME) BlueIncome else CoralPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("manual_save_button")
                ) {
                    Text(
                        text = if (initialTransaction == null) "Save Transaction" else "Update Transaction",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
