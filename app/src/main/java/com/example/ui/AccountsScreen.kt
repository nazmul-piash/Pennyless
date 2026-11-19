package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.model.AccountType
import com.example.model.FinanceAccount
import com.example.model.FinanceDashboardSnapshot
import com.example.model.formatMoney
import com.example.model.parseHexColor
import com.example.ui.components.accountIconForType
import com.example.ui.theme.AmberSoftBg
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlueIncome
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.DividerHairline
import com.example.ui.theme.MidnightInk
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SlateTertiary
import com.example.ui.theme.SurfaceGroupedBg
import com.example.util.DateUtils
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AccountsScreen(
    snapshot: FinanceDashboardSnapshot,
    onSaveAccount: (FinanceAccount, () -> Unit) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onTransfer: (FinanceAccount, FinanceAccount, Double, String, () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val currency = snapshot.profile.currency
    var editingAccount by remember { mutableStateOf<FinanceAccount?>(null) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }

    val assets = remember(snapshot.accounts) {
        snapshot.accounts.filter { it.balance >= 0.0 }.sumOf { it.balance }
    }
    val liabilities = remember(snapshot.accounts) {
        snapshot.accounts.filter { it.balance < 0.0 }.sumOf { abs(it.balance) }
    }
    val netWorth = assets - liabilities

    val creditCards = remember(snapshot.accounts) {
        snapshot.accounts.filter { it.resolvedType == AccountType.CREDIT_CARD }
    }
    val cashAndBanks = remember(snapshot.accounts) {
        snapshot.accounts.filter { it.resolvedType != AccountType.CREDIT_CARD }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceGroupedBg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .background(Color.White)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Accounts",
                    style = MaterialTheme.typography.titleLarge,
                    color = MidnightInk
                )
                Row {
                    if (snapshot.accounts.size >= 2) {
                        IconButton(
                            onClick = { showTransferDialog = true },
                            modifier = Modifier.testTag("transfer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SwapHoriz,
                                contentDescription = "Transfer or pay card",
                                tint = MidnightInk
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            editingAccount = null
                            showAccountDialog = true
                        },
                        modifier = Modifier.testTag("add_account_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "Add account",
                            tint = CoralPrimary
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = DividerHairline)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 12.dp)
            ) {
                AccountTopMetric("Assets", formatMoney(assets, currency), BlueIncome, Modifier.weight(1f))
                AccountTopMetric("Liabilities", formatMoney(liabilities, currency), CoralPrimary, Modifier.weight(1f))
                AccountTopMetric("Total", formatMoney(netWorth, currency), MidnightInk, Modifier.weight(1f))
            }
            HorizontalDivider(thickness = 1.dp, color = DividerHairline)
        }

        if (snapshot.accounts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().widthIn(max = 720.dp)) {
                EmptyLedgerPlaceholder("No accounts yet. Tap + to add a card or wallet.")
            }
            return
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (creditCards.isNotEmpty()) {
                item(key = "hdr_cards") {
                    Text(
                        text = "CREDIT CARDS & LIMITS",
                        style = MaterialTheme.typography.labelMedium,
                        color = SlateSecondary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                items(creditCards, key = { it.id }) { card ->
                    CreditCardAccountCard(
                        account = card,
                        currency = currency,
                        onClick = {
                            editingAccount = card
                            showAccountDialog = true
                        }
                    )
                }
            }

            if (cashAndBanks.isNotEmpty()) {
                item(key = "hdr_banks") {
                    Text(
                        text = "BANK & CASH WALLETS",
                        style = MaterialTheme.typography.labelMedium,
                        color = SlateSecondary,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
                items(cashAndBanks, key = { it.id }) { acc ->
                    StandardAccountCard(
                        account = acc,
                        currency = currency,
                        onClick = {
                            editingAccount = acc
                            showAccountDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showAccountDialog) {
        AccountEditorDialog(
            initial = editingAccount,
            onDismiss = { showAccountDialog = false },
            onSave = { acc ->
                onSaveAccount(acc) { showAccountDialog = false }
            },
            onDelete = if (editingAccount != null) {
                {
                    onDeleteAccount(editingAccount!!.id)
                    showAccountDialog = false
                }
            } else null
        )
    }

    if (showTransferDialog && snapshot.accounts.size >= 2) {
        AccountTransferDialog(
            accounts = snapshot.accounts,
            currency = currency,
            onDismiss = { showTransferDialog = false },
            onConfirm = { from, to, amount, note ->
                onTransfer(from, to, amount, note) { showTransferDialog = false }
            }
        )
    }
}

@Composable
private fun AccountTopMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = SlateSecondary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun CreditCardAccountCard(
    account: FinanceAccount,
    currency: String,
    onClick: () -> Unit
) {
    val accent = parseHexColor(account.colorHex, CoralPrimary)
    val used = abs(account.balance.coerceAtMost(0.0))
    val limit = account.creditLimit.coerceAtLeast(1.0)
    val ratio = (used / limit).toFloat().coerceIn(0f, 1f)
    val daysUntilDue = DateUtils.daysUntilDue(account.dueDayOfMonth)
    val isDueSoon = account.dueDayOfMonth > 0 && daysUntilDue <= 7

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DividerHairline, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("account_card_${account.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = accountIconForType(account.resolvedType),
                        contentDescription = account.name,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = account.name, style = MaterialTheme.typography.titleMedium, color = MidnightInk)
                    val fourDigits = if (account.lastFourDigits.isNotBlank()) "•••• ${account.lastFourDigits}" else "Credit Card"
                    Text(text = fourDigits, style = MaterialTheme.typography.bodySmall, color = SlateSecondary)
                }
                Text(
                    text = formatMoney(account.balance, currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (account.balance < 0) CoralPrimary else BlueIncome
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "Monthly Limit: ${formatMoney(account.creditLimit, currency)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateSecondary
                    )
                    Text(
                        text = "${(ratio * 100).roundToInt()}% used",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ratio > 0.8f) CoralPrimary else SlateSecondary
                    )
                }
                LinearProgressIndicator(
                    progress = { ratio },
                    color = if (ratio > 0.8f) CoralPrimary else accent,
                    trackColor = SurfaceGroupedBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }

            if (account.dueDayOfMonth > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDueSoon) AmberSoftBg else SurfaceGroupedBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = if (isDueSoon) AmberWarning else SlateSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (daysUntilDue == 0) "Statement due today (Day ${account.dueDayOfMonth})"
                            else "Statement due Day ${account.dueDayOfMonth} · in $daysUntilDue day${if (daysUntilDue == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDueSoon) AmberWarning else SlateSecondary,
                            fontWeight = if (isDueSoon) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StandardAccountCard(
    account: FinanceAccount,
    currency: String,
    onClick: () -> Unit
) {
    val accent = parseHexColor(account.colorHex, BlueIncome)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DividerHairline, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("account_card_${account.id}")
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = accountIconForType(account.resolvedType),
                    contentDescription = account.name,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = account.name, style = MaterialTheme.typography.titleMedium, color = MidnightInk)
                Text(
                    text = account.resolvedType.label + if (account.lastFourDigits.isNotBlank()) " · •••• ${account.lastFourDigits}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateSecondary
                )
            }
            Text(
                text = formatMoney(account.balance, currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (account.balance >= 0) BlueIncome else CoralPrimary
            )
        }
    }
}

@Composable
private fun AccountEditorDialog(
    initial: FinanceAccount?,
    onDismiss: () -> Unit,
    onSave: (FinanceAccount) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.resolvedType ?: AccountType.BANK) }
    var balanceStr by remember { mutableStateOf(initial?.balance?.toString() ?: "0.0") }
    var limitStr by remember { mutableStateOf(initial?.creditLimit?.toString() ?: "2000.0") }
    var dueDayStr by remember { mutableStateOf((initial?.dueDayOfMonth ?: 25).toString()) }
    var lastFour by remember { mutableStateOf(initial?.lastFourDigits ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (initial == null) "New Account" else "Edit Account", style = MaterialTheme.typography.titleLarge)
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete account", tint = CoralPrimary)
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AccountType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = balanceStr,
                    onValueChange = { balanceStr = it },
                    label = { Text("Current Balance (negative for owed)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (type == AccountType.CREDIT_CARD) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = limitStr,
                            onValueChange = { limitStr = it },
                            label = { Text("Limit") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = dueDayStr,
                            onValueChange = { dueDayStr = it },
                            label = { Text("Due Day") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = lastFour,
                    onValueChange = { lastFour = it.take(4) },
                    label = { Text("Last 4 Digits (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val bal = balanceStr.toDoubleOrNull() ?: 0.0
                    val lim = if (type == AccountType.CREDIT_CARD) (limitStr.toDoubleOrNull() ?: 0.0) else 0.0
                    val due = if (type == AccountType.CREDIT_CARD) (dueDayStr.toIntOrNull() ?: 25).coerceIn(1, 31) else 0
                    val defaultColor = when (type) {
                        AccountType.CREDIT_CARD -> "#D97706"
                        AccountType.BANK -> "#3B82F6"
                        AccountType.CASH -> "#10B981"
                        AccountType.SAVINGS -> "#6366F1"
                    }
                    onSave(
                        (initial ?: FinanceAccount()).copy(
                            name = name.ifBlank { "My Account" },
                            accountType = type.name,
                            balance = bal,
                            creditLimit = lim,
                            dueDayOfMonth = due,
                            colorHex = initial?.colorHex ?: defaultColor,
                            lastFourDigits = lastFour
                        )
                    )
                }
            ) {
                Text("Save", color = CoralPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AccountTransferDialog(
    accounts: List<FinanceAccount>,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (FinanceAccount, FinanceAccount, Double, String) -> Unit
) {
    var fromAcc by remember { mutableStateOf(accounts.first()) }
    var toAcc by remember { mutableStateOf(accounts.getOrElse(1) { accounts.first() }) }
    var amountStr by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("Card payment / Transfer") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transfer / Pay Card", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("From", style = MaterialTheme.typography.labelMedium, color = SlateSecondary)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    accounts.forEach { acc ->
                        FilterChip(
                            selected = fromAcc.id == acc.id,
                            onClick = { fromAcc = acc },
                            label = { Text(acc.name) }
                        )
                    }
                }
                Text("To", style = MaterialTheme.typography.labelMedium, color = SlateSecondary)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    accounts.filter { it.id != fromAcc.id }.forEach { acc ->
                        FilterChip(
                            selected = toAcc.id == acc.id,
                            onClick = { toAcc = acc },
                            label = { Text(acc.name) }
                        )
                    }
                }
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount ($currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    if (amt > 0.0 && fromAcc.id != toAcc.id) {
                        onConfirm(fromAcc, toAcc, amt, note)
                    }
                }
            ) {
                Text("Transfer", color = CoralPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
