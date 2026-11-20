package com.example.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.firebase.isAppCheckErrorMessage
import com.example.model.FinanceAccount
import com.example.model.FinanceCategory
import com.example.model.ImportedStatementRow
import com.example.model.TxType
import com.example.model.formatMoney
import com.example.ui.components.AppCheckDebugHelpCard
import com.example.ui.components.categoryColorForName
import com.example.ui.components.categoryIconForName
import com.example.ui.theme.BlueIncome
import com.example.ui.theme.CardBorderSubtle
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.CoralSoftBg
import com.example.ui.theme.DividerHairline
import com.example.ui.theme.EmeraldHealthy
import com.example.ui.theme.EmeraldSoftBg
import com.example.ui.theme.MidnightInk
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SurfaceGroupedBg
import com.example.viewmodel.StatementImportUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementImportModalSheet(
    state: StatementImportUiState,
    accounts: List<FinanceAccount>,
    categories: List<FinanceCategory>,
    defaultCurrency: String,
    onToggleRow: (String) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onSelectAccount: (String) -> Unit,
    onConfirmSave: () -> Unit,
    onDismiss: () -> Unit
) {
    if (state is StatementImportUiState.Idle) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            if (state !is StatementImportUiState.Saving) onDismiss()
        },
        sheetState = sheetState,
        containerColor = SurfaceGroupedBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.testTag("statement_import_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .animateContentSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (state) {
                StatementImportUiState.Idle -> Unit

                is StatementImportUiState.Parsing -> {
                    StatementLoadingContent(
                        title = if (state.isPdf) "Reading PDF Statement with Gemini AI..." else "Parsing CSV Statement...",
                        subtitle = "Pure in-memory parsing — \"${state.fileName}\" will never be cached or stored."
                    )
                }

                is StatementImportUiState.Saving -> {
                    StatementLoadingContent(
                        title = "Saving ${state.totalCount} transactions...",
                        subtitle = "Updating account balance and syncing securely to your ledger."
                    )
                }

                is StatementImportUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 560.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(CoralSoftBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.WarningAmber,
                                    contentDescription = null,
                                    tint = CoralPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = "Could Not Import Statement",
                                style = MaterialTheme.typography.titleLarge,
                                color = MidnightInk
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SlateSecondary
                            )
                            if (isAppCheckErrorMessage(state.message)) {
                                AppCheckDebugHelpCard(showCsvHint = true)
                            }
                            Button(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MidnightSlate),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Close")
                            }
                        }
                    }
                }

                is StatementImportUiState.Preview -> {
                    StatementPreviewContent(
                        preview = state,
                        accounts = accounts,
                        categories = categories,
                        defaultCurrency = defaultCurrency,
                        onToggleRow = onToggleRow,
                        onToggleAll = onToggleAll,
                        onSelectAccount = onSelectAccount,
                        onConfirmSave = onConfirmSave,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun StatementLoadingContent(title: String, subtitle: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(color = CoralPrimary)
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MidnightInk)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = EmeraldHealthy,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = SlateSecondary)
            }
        }
    }
}

@Composable
private fun StatementPreviewContent(
    preview: StatementImportUiState.Preview,
    accounts: List<FinanceAccount>,
    categories: List<FinanceCategory>,
    defaultCurrency: String,
    onToggleRow: (String) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onSelectAccount: (String) -> Unit,
    onConfirmSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val selectedRows = preview.rows.filter { it.selected }
    val allSelected = preview.rows.isNotEmpty() && selectedRows.size == preview.rows.size
    val totalExpenses = selectedRows.filter { it.txType == TxType.EXPENSE.name }.sumOf { it.amount }
    val totalIncome = selectedRows.filter { it.txType == TxType.INCOME.name }.sumOf { it.amount }

    // Title Header + Zero Retention Privacy Banner
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (preview.isPdf) CoralSoftBg else EmeraldSoftBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (preview.isPdf) Icons.Outlined.PictureAsPdf else Icons.Outlined.Description,
                    contentDescription = null,
                    tint = if (preview.isPdf) CoralPrimary else EmeraldHealthy,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Review Extracted Statement",
                    style = MaterialTheme.typography.titleLarge,
                    color = MidnightInk
                )
                Text(
                    text = "${preview.rows.size} entries found · ${preview.fileName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        TextButton(onClick = { onToggleAll(!allSelected) }) {
            Icon(
                imageVector = if (allSelected) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = CoralPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (allSelected) "None" else "All", color = CoralPrimary)
        }
    }

    // Zero Retention Indicator
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = EmeraldSoftBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = EmeraldHealthy,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Zero File Retention: Parsed purely in memory; original file discarded.",
                style = MaterialTheme.typography.labelMedium,
                color = MidnightInk
            )
        }
    }

    // Target Card / Account Picker
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Target Account / Card",
                    style = MaterialTheme.typography.labelLarge,
                    color = MidnightInk
                )
                if (!preview.detectedAccountHint.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = BlueIncome, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = preview.detectedAccountHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = BlueIncome,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                accounts.forEach { acc ->
                    FilterChip(
                        selected = acc.id == preview.selectedAccountId,
                        onClick = { onSelectAccount(acc.id) },
                        label = { Text(acc.name) }
                    )
                }
            }
            HorizontalDivider(color = CardBorderSubtle)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${selectedRows.size} of ${preview.rows.size} selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = SlateSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (totalIncome > 0) {
                        Text(
                            text = "+${formatMoney(totalIncome, defaultCurrency)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = BlueIncome
                        )
                    }
                    if (totalExpenses > 0) {
                        Text(
                            text = "-${formatMoney(totalExpenses, defaultCurrency)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = CoralPrimary
                        )
                    }
                }
            }
        }
    }

    // Scrollable Extracted Rows List
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 340.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(preview.rows, key = { it.id }) { row ->
                StatementPreviewRowItem(
                    row = row,
                    categories = categories,
                    onToggle = { onToggleRow(row.id) }
                )
                HorizontalDivider(
                    color = DividerHairline.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }
        }
    }

    // Action Buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(0.35f)
                .height(52.dp)
        ) {
            Text("Cancel", color = MidnightInk)
        }
        Button(
            onClick = onConfirmSave,
            enabled = selectedRows.isNotEmpty() && accounts.isNotEmpty(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
            modifier = Modifier
                .weight(0.65f)
                .height(52.dp)
                .testTag("confirm_statement_import_button")
        ) {
            Text(
                text = "Save ${selectedRows.size} Transaction${if (selectedRows.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun StatementPreviewRowItem(
    row: ImportedStatementRow,
    categories: List<FinanceCategory>,
    onToggle: () -> Unit
) {
    val catColor = categoryColorForName(row.categoryName, categories)
    val isIncome = row.txType == TxType.INCOME.name

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = row.selected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = CoralPrimary)
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(catColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = categoryIconForName(row.categoryName, categories),
                contentDescription = null,
                tint = catColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.merchant,
                style = MaterialTheme.typography.titleSmall,
                color = if (row.selected) MidnightInk else SlateSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${row.dateIso} · ${row.categoryName}",
                style = MaterialTheme.typography.bodySmall,
                color = SlateSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = (if (isIncome) "+" else "-") + formatMoney(row.amount, row.currency),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (!row.selected) SlateSecondary else if (isIncome) BlueIncome else CoralPrimary
        )
    }
}
