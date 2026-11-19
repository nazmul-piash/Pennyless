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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.firebase.signOutUser
import com.example.model.FinanceAccount
import com.example.model.FinanceCategory
import com.example.model.FinanceDashboardSnapshot
import com.example.model.RecurringBill
import com.example.model.TxType
import com.example.model.formatMoney
import com.example.model.parseHexColor
import com.example.ui.components.categoryIconForKey
import com.example.ui.theme.AmberSoftBg
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlueIncome
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.CoralSoftBg
import com.example.ui.theme.DividerHairline
import com.example.ui.theme.EmeraldHealthy
import com.example.ui.theme.EmeraldSoftBg
import com.example.ui.theme.MidnightInk
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SurfaceGroupedBg
import com.example.util.DateUtils

private enum class LegalModalType { PRIVACY, TERMS, HELP }

@Composable
fun MoreScreen(
    snapshot: FinanceDashboardSnapshot,
    openBillsInitially: Boolean,
    onClearOpenBillsFlag: () -> Unit,
    onUpdateProfile: (String, String, Double, () -> Unit) -> Unit,
    onSaveCategory: (FinanceCategory, () -> Unit) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onSaveBill: (RecurringBill, () -> Unit) -> Unit,
    onDeleteBill: (String) -> Unit,
    onPayBill: (RecurringBill, FinanceAccount) -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onResetDemoData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var showProfileDialog by remember { mutableStateOf(false) }
    var showCategoryManager by remember { mutableStateOf(false) }
    var showBillsManager by remember(openBillsInitially) { mutableStateOf(openBillsInitially) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var activeLegalModal by remember { mutableStateOf<LegalModalType?>(null) }

    val profile = snapshot.profile

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
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "More & Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = MidnightInk
                )
            }
            HorizontalDivider(thickness = 1.dp, color = DividerHairline)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // User Profile Card
            item(key = "profile_card") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DividerHairline, RoundedCornerShape(16.dp))
                        .clickable { showProfileDialog = true }
                        .testTag("profile_card")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MidnightSlate),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = profile.displayName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MidnightInk
                            )
                            if (profile.email.isNotBlank()) {
                                Text(
                                    text = profile.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateSecondary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Currency: ${profile.currency} · Budget: ${formatMoney(profile.monthlyBudget, profile.currency)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = BlueIncome
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit profile",
                            tint = SlateSecondary
                        )
                    }
                }
            }

            // Management Section
            item(key = "mgmt_section") {
                SectionLabel("CONFIGURATION")
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DividerHairline, RoundedCornerShape(16.dp))
                ) {
                    Column {
                        SettingsRowItem(
                            icon = Icons.Outlined.Category,
                            iconTint = CoralPrimary,
                            iconBg = CoralSoftBg,
                            title = "Category Manager",
                            subtitle = "${snapshot.categories.size} income & expense categories",
                            onClick = { showCategoryManager = true },
                            testTag = "category_manager_row"
                        )
                        HorizontalDivider(thickness = 1.dp, color = DividerHairline)
                        SettingsRowItem(
                            icon = Icons.Outlined.EventRepeat,
                            iconTint = AmberWarning,
                            iconBg = AmberSoftBg,
                            title = "Recurring Bills & Reminders",
                            subtitle = "${snapshot.recurringBills.size} monthly scheduled bills",
                            onClick = { showBillsManager = true },
                            testTag = "recurring_bills_row"
                        )
                    }
                }
            }

            // Data & Export Section
            item(key = "data_section") {
                SectionLabel("DATA & BACKUP")
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DividerHairline, RoundedCornerShape(16.dp))
                ) {
                    Column {
                        SettingsRowItem(
                            icon = Icons.Outlined.Description,
                            iconTint = EmeraldHealthy,
                            iconBg = EmeraldSoftBg,
                            title = "Export Transactions to CSV",
                            subtitle = "Compatible with Excel and Google Sheets",
                            onClick = onExportCsv,
                            testTag = "export_csv_row"
                        )
                        HorizontalDivider(thickness = 1.dp, color = DividerHairline)
                        SettingsRowItem(
                            icon = Icons.Outlined.DataObject,
                            iconTint = BlueIncome,
                            iconBg = Color(0xFFEFF6FF),
                            title = "Export Full Backup to JSON",
                            subtitle = "Includes accounts, bills, and ledger entries",
                            onClick = onExportJson,
                            testTag = "export_json_row"
                        )
                        HorizontalDivider(thickness = 1.dp, color = DividerHairline)
                        SettingsRowItem(
                            icon = Icons.Outlined.RestartAlt,
                            iconTint = CoralPrimary,
                            iconBg = CoralSoftBg,
                            title = "Reset Demo Data",
                            subtitle = "Restore sample cards, categories, and November entries",
                            onClick = { showResetConfirm = true },
                            testTag = "reset_demo_row"
                        )
                    }
                }
            }

            // Policies & Help Section
            item(key = "policies_section") {
                SectionLabel("ABOUT & POLICIES")
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DividerHairline, RoundedCornerShape(16.dp))
                ) {
                    Column {
                        SettingsRowItem(
                            icon = Icons.Outlined.PrivacyTip,
                            iconTint = MidnightSlate,
                            iconBg = SurfaceGroupedBg,
                            title = "Privacy Policy",
                            subtitle = "How Penniless handles receipts and account data",
                            onClick = { activeLegalModal = LegalModalType.PRIVACY },
                            testTag = "privacy_policy_row"
                        )
                        HorizontalDivider(thickness = 1.dp, color = DividerHairline)
                        SettingsRowItem(
                            icon = Icons.Outlined.Gavel,
                            iconTint = MidnightSlate,
                            iconBg = SurfaceGroupedBg,
                            title = "Terms of Use",
                            subtitle = "Usage terms and AI extraction disclaimer",
                            onClick = { activeLegalModal = LegalModalType.TERMS },
                            testTag = "terms_row"
                        )
                        HorizontalDivider(thickness = 1.dp, color = DividerHairline)
                        SettingsRowItem(
                            icon = Icons.AutoMirrored.Outlined.HelpOutline,
                            iconTint = MidnightSlate,
                            iconBg = SurfaceGroupedBg,
                            title = "App Version & Help",
                            subtitle = "Penniless v1.0 · Gemini 3.8 Flash Receipt Vision",
                            onClick = { activeLegalModal = LegalModalType.HELP },
                            testTag = "help_row"
                        )
                    }
                }
            }

            // Sign Out
            item(key = "sign_out") {
                Button(
                    onClick = { signOutUser(credentialManager, scope) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = CoralPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .border(1.dp, DividerHairline, RoundedCornerShape(14.dp))
                        .testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    if (showProfileDialog) {
        ProfileEditorDialog(
            initialName = profile.displayName,
            initialCurrency = profile.currency,
            initialBudget = profile.monthlyBudget,
            onDismiss = { showProfileDialog = false },
            onSave = { name, cur, budget ->
                onUpdateProfile(name, cur, budget) { showProfileDialog = false }
            }
        )
    }

    if (showCategoryManager) {
        CategoryManagerDialog(
            categories = snapshot.categories,
            onDismiss = { showCategoryManager = false },
            onSave = onSaveCategory,
            onDelete = onDeleteCategory
        )
    }

    if (showBillsManager) {
        RecurringBillsDialog(
            bills = snapshot.recurringBills,
            accounts = snapshot.accounts,
            categories = snapshot.categories,
            currency = profile.currency,
            onDismiss = {
                showBillsManager = false
                onClearOpenBillsFlag()
            },
            onSaveBill = onSaveBill,
            onDeleteBill = onDeleteBill,
            onPayBill = onPayBill
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Demo Data?") },
            text = { Text("This will replace your current accounts, categories, bills, and transactions with the sample dataset.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    onResetDemoData()
                }) {
                    Text("Reset", color = CoralPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }

    val legal = activeLegalModal
    if (legal != null) {
        LegalInfoDialog(
            type = legal,
            onDismiss = { activeLegalModal = null }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = SlateSecondary,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = MidnightInk)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = SlateSecondary)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = SlateSecondary
        )
    }
}

@Composable
private fun ProfileEditorDialog(
    initialName: String,
    initialCurrency: String,
    initialBudget: Double,
    onDismiss: () -> Unit,
    onSave: (String, String, Double) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var currency by remember { mutableStateOf(initialCurrency) }
    var budgetStr by remember { mutableStateOf(initialBudget.toString()) }
    val currencies = listOf("EUR", "USD", "GBP", "BDT", "CHF", "JPY")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profile & Budget", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Default Currency", style = MaterialTheme.typography.labelMedium, color = SlateSecondary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    currencies.forEach { code ->
                        FilterChip(
                            selected = currency == code,
                            onClick = { currency = code },
                            label = { Text(code) }
                        )
                    }
                }
                OutlinedTextField(
                    value = budgetStr,
                    onValueChange = { budgetStr = it },
                    label = { Text("Monthly Budget Target ($currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(name, currency, budgetStr.toDoubleOrNull() ?: initialBudget)
            }) {
                Text("Save", color = CoralPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CategoryManagerDialog(
    categories: List<FinanceCategory>,
    onDismiss: () -> Unit,
    onSave: (FinanceCategory, () -> Unit) -> Unit,
    onDelete: (String) -> Unit
) {
    var showForm by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<FinanceCategory?>(null) }
    var formName by remember { mutableStateOf("") }
    var formType by remember { mutableStateOf(TxType.EXPENSE) }
    var formColor by remember { mutableStateOf("#FF5A5F") }
    val palette = listOf("#FF5A5F", "#3B82F6", "#8B5CF6", "#F59E0B", "#10B981", "#EC4899")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Categories", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = {
                    if (showForm && editingCategory == null) {
                        showForm = false
                    } else {
                        editingCategory = null
                        formName = ""
                        formType = TxType.EXPENSE
                        formColor = "#FF5A5F"
                        showForm = true
                    }
                }) {
                    Icon(Icons.Outlined.Add, contentDescription = "New category", tint = CoralPrimary)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showForm) {
                    Surface(
                        color = SurfaceGroupedBg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (editingCategory != null) "Edit Category" else "New Category",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MidnightInk
                                )
                                TextButton(onClick = {
                                    editingCategory = null
                                    formName = ""
                                    showForm = false
                                }) {
                                    Text("Cancel", color = SlateSecondary)
                                }
                            }
                            OutlinedTextField(
                                value = formName,
                                onValueChange = { formName = it },
                                label = { Text("Category Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(TxType.EXPENSE, TxType.INCOME).forEach { t ->
                                    FilterChip(selected = formType == t, onClick = { formType = t }, label = { Text(t.label) })
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                palette.forEach { hex ->
                                    val c = parseHexColor(hex)
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                            .border(if (formColor == hex) 2.5.dp else 0.dp, MidnightInk, CircleShape)
                                            .clickable { formColor = hex }
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    if (formName.isNotBlank()) {
                                        val existing = editingCategory
                                        val payload = if (existing != null) {
                                            existing.copy(
                                                name = formName.trim(),
                                                txType = formType.name,
                                                colorHex = formColor
                                            )
                                        } else {
                                            FinanceCategory(
                                                name = formName.trim(),
                                                txType = formType.name,
                                                iconKey = "category",
                                                colorHex = formColor,
                                                sortOrder = categories.size
                                            )
                                        }
                                        onSave(payload) {
                                            editingCategory = null
                                            formName = ""
                                            showForm = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (editingCategory != null) "Update Category" else "Add Category")
                            }
                        }
                    }
                }

                categories.forEach { cat ->
                    val col = parseHexColor(cat.colorHex)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editingCategory = cat
                                formName = cat.name
                                formType = cat.resolvedTxType
                                formColor = cat.colorHex
                                showForm = true
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(col.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = categoryIconForKey(cat.iconKey),
                                contentDescription = null,
                                tint = col,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = cat.name, style = MaterialTheme.typography.titleSmall, color = MidnightInk)
                            Text(text = cat.resolvedTxType.label, style = MaterialTheme.typography.labelSmall, color = SlateSecondary)
                        }
                        IconButton(onClick = {
                            editingCategory = cat
                            formName = cat.name
                            formType = cat.resolvedTxType
                            formColor = cat.colorHex
                            showForm = true
                        }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit category", tint = SlateSecondary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onDelete(cat.id) }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete category", tint = SlateSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(color = DividerHairline)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun RecurringBillsDialog(
    bills: List<RecurringBill>,
    accounts: List<FinanceAccount>,
    categories: List<FinanceCategory>,
    currency: String,
    onDismiss: () -> Unit,
    onSaveBill: (RecurringBill, () -> Unit) -> Unit,
    onDeleteBill: (String) -> Unit,
    onPayBill: (RecurringBill, FinanceAccount) -> Unit
) {
    var showAddForm by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var dayStr by remember { mutableStateOf("1") }
    var autoPay by remember { mutableStateOf(true) }
    var selectedAccount by remember { mutableStateOf(accounts.firstOrNull()) }
    var selectedCat by remember { mutableStateOf("Bills & Utilities") }
    val currentYm = remember { DateUtils.currentYearMonth().toIsoPrefix() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recurring Bills", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { showAddForm = !showAddForm }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add bill", tint = CoralPrimary)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (showAddForm) {
                    Surface(
                        color = SurfaceGroupedBg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("Bill Title (e.g. Netflix, Rent)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = amountStr,
                                    onValueChange = { amountStr = it },
                                    label = { Text("Amount") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = dayStr,
                                    onValueChange = { dayStr = it },
                                    label = { Text("Day (1-31)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = autoPay, onCheckedChange = { autoPay = it })
                                Text("Auto-pay enabled", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = {
                                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                                    val acc = selectedAccount ?: accounts.firstOrNull()
                                    if (title.isNotBlank() && amt > 0.0 && acc != null) {
                                        onSaveBill(
                                            RecurringBill(
                                                title = title.trim(),
                                                amount = amt,
                                                categoryName = selectedCat,
                                                accountId = acc.id,
                                                accountName = acc.name,
                                                dayOfMonth = (dayStr.toIntOrNull() ?: 1).coerceIn(1, 31),
                                                autoPay = autoPay
                                            )
                                        ) {
                                            title = ""
                                            amountStr = ""
                                            showAddForm = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Bill")
                            }
                        }
                    }
                }

                if (bills.isEmpty()) {
                    Text("No recurring bills configured.", style = MaterialTheme.typography.bodyMedium, color = SlateSecondary)
                }

                bills.forEach { bill ->
                    val isPaidThisMonth = bill.lastPaidMonth == currentYm
                    val matchingAcc = accounts.firstOrNull { it.id == bill.accountId } ?: accounts.firstOrNull()
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceGroupedBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = bill.title, style = MaterialTheme.typography.titleSmall, color = MidnightInk)
                                Text(
                                    text = "Day ${bill.dayOfMonth} · ${formatMoney(bill.amount, currency)} · ${if (bill.autoPay) "Auto-Pay" else "Manual"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateSecondary
                                )
                            }
                            if (isPaidThisMonth) {
                                Text("Paid", style = MaterialTheme.typography.labelMedium, color = EmeraldHealthy, fontWeight = FontWeight.SemiBold)
                            } else if (matchingAcc != null) {
                                TextButton(onClick = { onPayBill(bill, matchingAcc) }) {
                                    Text("Pay", color = CoralPrimary)
                                }
                            }
                            IconButton(onClick = { onDeleteBill(bill.id) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete bill", tint = SlateSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun LegalInfoDialog(type: LegalModalType, onDismiss: () -> Unit) {
    val (title, body) = when (type) {
        LegalModalType.PRIVACY -> "Privacy Policy" to
            "Penniless stores your transactions, accounts, categories, and compressed receipt thumbnails strictly within your private, user-authenticated Firebase Firestore space. Receipt images submitted for AI scanning are processed over TLS via Firebase AI Logic (protected by App Check) solely to extract merchant, date, line items, and totals, and are never shared with third-party advertisers."
        LegalModalType.TERMS -> "Terms of Use" to
            "Penniless is a personal finance tracking and AI receipt parsing assistant. While Gemini Vision strives for high extraction accuracy, always verify extracted amounts, dates, and tax/category splits in the verification card before saving to your ledger. You retain full ownership and export rights (CSV/JSON) over all your recorded data."
        LegalModalType.HELP -> "Penniless v1.0 · Help" to
            "• Quick Scan: Tap the Coral Camera button on Transactions to capture or upload any receipt or payment screenshot.\n• Split Receipts: When a receipt contains multiple categories (e.g. groceries + electronics), toggle 'Split by Category' before saving.\n• Credit Cards: Tap any card on the Accounts tab to update limits and statement due dates.\n• Backups: Export CSV or JSON anytime from Summary or More."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium, color = SlateSecondary) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = CoralPrimary) }
        }
    )
}
