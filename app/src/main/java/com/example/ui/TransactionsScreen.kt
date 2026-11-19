package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FinanceAccount
import com.example.model.FinanceCategory
import com.example.model.FinanceDashboardSnapshot
import com.example.model.FinanceTransaction
import com.example.model.RecurringBill
import com.example.model.TxType
import com.example.model.formatMoney
import com.example.ui.components.categoryColorForName
import com.example.ui.components.categoryIconForName
import com.example.ui.components.rememberBase64ImageBitmap
import com.example.ui.theme.AmberSoftBg
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlueIncome
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.CoralSoftBg
import com.example.ui.theme.DividerHairline
import com.example.ui.theme.MidnightInk
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SlateTertiary
import com.example.ui.theme.SurfaceGroupedBg
import com.example.util.DateUtils
import com.example.util.YearMonthKey
import com.example.viewmodel.TransSubTab
import kotlin.math.roundToInt

@Composable
fun TransactionsScreen(
    snapshot: FinanceDashboardSnapshot,
    selectedSubTab: TransSubTab,
    selectedMonth: YearMonthKey,
    searchQuery: String,
    filterAccountId: String?,
    filterTxType: TxType?,
    onSelectSubTab: (TransSubTab) -> Unit,
    onShiftMonth: (Int) -> Unit,
    onShiftYear: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onApplyFilters: (String?, TxType?) -> Unit,
    onOpenRecurringBills: () -> Unit,
    onOpenAddManualForDate: (String) -> Unit,
    onEditTransaction: (FinanceTransaction) -> Unit,
    onDeleteTransaction: (FinanceTransaction) -> Unit,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSearchBar by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var inspectingTx by remember { mutableStateOf<FinanceTransaction?>(null) }

    val currency = snapshot.profile.currency
    val monthPrefix = selectedMonth.toIsoPrefix()
    val yearPrefix = "${selectedMonth.year}-"

    val filteredMonthTxs = remember(snapshot.transactions, monthPrefix, searchQuery, filterAccountId, filterTxType) {
        snapshot.transactions.filter { tx ->
            val matchesTime = tx.dateIso.startsWith(monthPrefix)
            val matchesAccount = filterAccountId == null || tx.accountId == filterAccountId
            val matchesType = filterTxType == null || tx.resolvedTxType == filterTxType
            val q = searchQuery.trim()
            val matchesQuery = q.isEmpty() ||
                tx.merchant.contains(q, ignoreCase = true) ||
                tx.note.contains(q, ignoreCase = true) ||
                tx.categoryName.contains(q, ignoreCase = true) ||
                tx.subcategory.contains(q, ignoreCase = true)
            matchesTime && matchesAccount && matchesType && matchesQuery
        }
    }

    val yearTxs = remember(snapshot.transactions, yearPrefix, filterAccountId, filterTxType) {
        snapshot.transactions.filter { tx ->
            tx.dateIso.startsWith(yearPrefix) &&
                (filterAccountId == null || tx.accountId == filterAccountId) &&
                (filterTxType == null || tx.resolvedTxType == filterTxType)
        }
    }

    val activePeriodTxs = if (selectedSubTab == TransSubTab.MONTHLY) yearTxs else filteredMonthTxs
    val totalIncome = remember(activePeriodTxs) {
        activePeriodTxs.filter { it.resolvedTxType == TxType.INCOME }.sumOf { it.amount }
    }
    val totalExpense = remember(activePeriodTxs) {
        activePeriodTxs.filter { it.resolvedTxType == TxType.EXPENSE }.sumOf { it.amount }
    }
    val netTotal = totalIncome - totalExpense

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
            // Top App Header (Money Manager style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { showSearchBar = !showSearchBar },
                    modifier = Modifier.testTag("search_toggle_button")
                ) {
                    Icon(
                        imageVector = if (showSearchBar) Icons.Outlined.Close else Icons.Outlined.Search,
                        contentDescription = "Search transactions",
                        tint = MidnightInk
                    )
                }

                Text(
                    text = "Trans.",
                    style = MaterialTheme.typography.titleLarge,
                    color = MidnightInk
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenRecurringBills,
                        modifier = Modifier.testTag("recurring_bills_shortcut")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.StarOutline,
                            contentDescription = "Recurring bills and reminders",
                            tint = MidnightInk
                        )
                    }
                    IconButton(
                        onClick = { showFilterDialog = true },
                        modifier = Modifier.testTag("filter_button")
                    ) {
                        val hasFilter = filterAccountId != null || filterTxType != null
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = "Filter transactions",
                            tint = if (hasFilter) CoralPrimary else MidnightInk
                        )
                    }
                }
            }

            AnimatedVisibility(visible = showSearchBar) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search merchant, note, or category...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("search_input")
                )
            }

            // Month / Year Selector (< Nov 2026 > or < 2026 >)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        if (selectedSubTab == TransSubTab.MONTHLY) onShiftYear(-1) else onShiftMonth(-1)
                    },
                    modifier = Modifier.testTag("prev_period_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = "Previous period",
                        tint = MidnightInk
                    )
                }

                Text(
                    text = if (selectedSubTab == TransSubTab.MONTHLY) "${selectedMonth.year}" else selectedMonth.shortLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MidnightInk,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("period_header_label")
                )

                IconButton(
                    onClick = {
                        if (selectedSubTab == TransSubTab.MONTHLY) onShiftYear(1) else onShiftMonth(1)
                    },
                    modifier = Modifier.testTag("next_period_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = "Next period",
                        tint = MidnightInk
                    )
                }
            }

            // 5 Sub-Tabs Row (Daily | Calendar | Monthly | Summary | Description)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TransSubTab.entries.forEach { tab ->
                    val isSelected = tab == selectedSubTab
                    Column(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable { onSelectSubTab(tab) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("subtab_${tab.name.lowercase()}"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isSelected) MidnightInk else SlateSecondary,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(42.dp)
                                .height(2.5.dp)
                                .background(if (isSelected) CoralPrimary else Color.Transparent)
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = DividerHairline)

            // Live 3-Column Summary Strip (Income | Exp. | Total)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SummaryStripCell(
                    label = "Income",
                    amountText = formatMoney(totalIncome, currency),
                    amountColor = BlueIncome,
                    modifier = Modifier.weight(1f)
                )
                SummaryStripCell(
                    label = "Exp.",
                    amountText = formatMoney(totalExpense, currency),
                    amountColor = CoralPrimary,
                    modifier = Modifier.weight(1f)
                )
                SummaryStripCell(
                    label = "Total",
                    amountText = formatMoney(netTotal, currency),
                    amountColor = MidnightInk,
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider(thickness = 1.dp, color = DividerHairline)
        }

        // Sub-Tab Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
        ) {
            when (selectedSubTab) {
                TransSubTab.DAILY -> DailyTransactionsContent(
                    transactions = filteredMonthTxs,
                    categories = snapshot.categories,
                    bills = snapshot.recurringBills,
                    currency = currency,
                    monthPrefix = monthPrefix,
                    onOpenRecurringBills = onOpenRecurringBills,
                    onClickTransaction = { inspectingTx = it }
                )
                TransSubTab.CALENDAR -> CalendarTransactionsView(
                    yearMonth = selectedMonth,
                    transactions = filteredMonthTxs,
                    categories = snapshot.categories,
                    currency = currency,
                    onClickTransaction = { inspectingTx = it },
                    onAddForDate = onOpenAddManualForDate
                )
                TransSubTab.MONTHLY -> MonthlySummaryView(
                    year = selectedMonth.year,
                    yearTransactions = yearTxs,
                    currency = currency,
                    onSelectMonth = { m ->
                        val delta = (m - selectedMonth.month)
                        onShiftMonth(delta)
                        onSelectSubTab(TransSubTab.DAILY)
                    }
                )
                TransSubTab.SUMMARY -> SummaryOverviewView(
                    snapshot = snapshot,
                    monthTransactions = filteredMonthTxs,
                    selectedMonth = selectedMonth,
                    onExportCsv = onExportCsv
                )
                TransSubTab.DESCRIPTION -> DescriptionTransactionsView(
                    transactions = filteredMonthTxs,
                    categories = snapshot.categories,
                    currency = currency,
                    onClickTransaction = { inspectingTx = it }
                )
            }
        }
    }

    if (showFilterDialog) {
        TransactionFilterDialog(
            accounts = snapshot.accounts,
            selectedAccountId = filterAccountId,
            selectedTxType = filterTxType,
            onDismiss = { showFilterDialog = false },
            onApply = { accId, txType ->
                onApplyFilters(accId, txType)
                showFilterDialog = false
            }
        )
    }

    val txDetail = inspectingTx
    if (txDetail != null) {
        TransactionDetailDialog(
            transaction = txDetail,
            categories = snapshot.categories,
            onDismiss = { inspectingTx = null },
            onEdit = {
                inspectingTx = null
                onEditTransaction(txDetail)
            },
            onDelete = {
                inspectingTx = null
                onDeleteTransaction(txDetail)
            }
        )
    }
}

@Composable
private fun SummaryStripCell(
    label: String,
    amountText: String,
    amountColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = SlateSecondary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = amountText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = amountColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DailyTransactionsContent(
    transactions: List<FinanceTransaction>,
    categories: List<FinanceCategory>,
    bills: List<RecurringBill>,
    currency: String,
    monthPrefix: String,
    onOpenRecurringBills: () -> Unit,
    onClickTransaction: (FinanceTransaction) -> Unit
) {
    val upcomingBills = remember(bills, monthPrefix) {
        bills.filter { bill ->
            bill.lastPaidMonth != monthPrefix && DateUtils.daysUntilDue(bill.dayOfMonth) <= 14
        }.sortedBy { DateUtils.daysUntilDue(it.dayOfMonth) }
    }

    if (transactions.isEmpty() && upcomingBills.isEmpty()) {
        EmptyLedgerPlaceholder()
        return
    }

    val groupedByDate = remember(transactions) {
        transactions.groupBy { it.dateIso }
            .entries
            .sortedByDescending { it.key }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        if (upcomingBills.isNotEmpty()) {
            item(key = "upcoming_bill_alert") {
                val totalDue = upcomingBills.sumOf { it.amount }
                Surface(
                    color = AmberSoftBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenRecurringBills() }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("upcoming_bills_banner"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .border(1.dp, AmberWarning.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsActive,
                                contentDescription = null,
                                tint = AmberWarning,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${upcomingBills.size} Upcoming Bill${if (upcomingBills.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MidnightInk,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Due ${formatMoney(totalDue, currency)} · Manage",
                                style = MaterialTheme.typography.labelMedium,
                                color = AmberWarning,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        HorizontalDivider(color = AmberWarning.copy(alpha = 0.2f))
                        upcomingBills.take(4).forEach { bill ->
                            val daysLeft = DateUtils.daysUntilDue(bill.dayOfMonth)
                            val dueLabel = if (daysLeft == 0) "Due today" else "In $daysLeft day${if (daysLeft == 1) "" else "s"}"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${bill.title} · $dueLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = formatMoney(bill.amount, currency),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MidnightInk
                                )
                            }
                        }
                    }
                }
            }
        }

        groupedByDate.forEach { (dateIso, dayTxs) ->
            item(key = "header_$dateIso") {
                DaySectionHeader(dateIso = dateIso, dayTxs = dayTxs, currency = currency)
            }
            items(items = dayTxs, key = { it.id.ifBlank { "${it.dateIso}_${it.merchant}_${it.amount}" } }) { tx ->
                TransactionRowItem(
                    tx = tx,
                    categories = categories,
                    onClick = { onClickTransaction(tx) }
                )
                HorizontalDivider(thickness = 1.dp, color = DividerHairline)
            }
            item(key = "spacer_$dateIso") {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun DaySectionHeader(
    dateIso: String,
    dayTxs: List<FinanceTransaction>,
    currency: String
) {
    val dayNum = DateUtils.parseDayOfMonth(dateIso)
    val dayOfWeek = DateUtils.dayOfWeekShort(dateIso)
    val monthYear = DateUtils.monthYearLabel(dateIso)
    val dayIncome = dayTxs.filter { it.resolvedTxType == TxType.INCOME }.sumOf { it.amount }
    val dayExpense = dayTxs.filter { it.resolvedTxType == TxType.EXPENSE }.sumOf { it.amount }
    val isToday = dateIso == DateUtils.todayIso()

    Column(modifier = Modifier.background(Color.White)) {
        HorizontalDivider(thickness = 1.dp, color = DividerHairline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = String.format("%02d", dayNum),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MidnightInk
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isToday) MidnightSlate else Color(0xFFF3F4F6))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = dayOfWeek,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) Color.White else SlateSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = monthYear,
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (dayIncome > 0.0) {
                    Text(
                        text = formatMoney(dayIncome, currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = BlueIncome
                    )
                }
                if (dayExpense > 0.0) {
                    Text(
                        text = formatMoney(dayExpense, currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = CoralPrimary
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = DividerHairline)
    }
}

@Composable
fun TransactionRowItem(
    tx: FinanceTransaction,
    categories: List<FinanceCategory>,
    onClick: () -> Unit
) {
    val catColor = categoryColorForName(tx.categoryName, categories)
    val catIcon = categoryIconForName(tx.categoryName, categories)
    val amountColor = when (tx.resolvedTxType) {
        TxType.INCOME -> BlueIncome
        TxType.EXPENSE -> CoralPrimary
        TxType.TRANSFER -> MidnightInk
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(catColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = catIcon,
                contentDescription = tx.categoryName,
                tint = catColor,
                modifier = Modifier.size(19.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tx.categoryName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MidnightInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (tx.subcategory.isNotBlank()) {
                    Text(
                        text = " · ${tx.subcategory}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            val titleLine = tx.merchant.ifBlank { tx.note.ifBlank { "Transaction" } }
            Text(
                text = if (tx.note.isNotBlank() && tx.merchant.isNotBlank()) "$titleLine — ${tx.note}" else titleLine,
                style = MaterialTheme.typography.bodySmall,
                color = SlateSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = tx.accountName,
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateTertiary
                )
                if (tx.receiptThumbnail != null || !tx.lineItems.isNullOrEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CoralSoftBg)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                            contentDescription = "Scanned receipt",
                            tint = CoralPrimary,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Receipt",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = CoralPrimary
                        )
                    }
                }
                if (tx.needsReview) {
                    Text(
                        text = "Review",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = AmberWarning,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AmberSoftBg)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatMoney(tx.amount, tx.currency),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = amountColor
        )
    }
}

@Composable
private fun DescriptionTransactionsView(
    transactions: List<FinanceTransaction>,
    categories: List<FinanceCategory>,
    currency: String,
    onClickTransaction: (FinanceTransaction) -> Unit
) {
    if (transactions.isEmpty()) {
        EmptyLedgerPlaceholder()
        return
    }
    val grouped = remember(transactions) {
        transactions.groupBy { it.merchant.ifBlank { it.note.ifBlank { it.categoryName } } }
            .entries
            .sortedByDescending { entry -> entry.value.sumOf { it.amount } }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        items(grouped, key = { it.key }) { (label, txs) ->
            val sumExpense = txs.filter { it.resolvedTxType == TxType.EXPENSE }.sumOf { it.amount }
            val sumIncome = txs.filter { it.resolvedTxType == TxType.INCOME }.sumOf { it.amount }
            Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClickTransaction(txs.first()) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            val firstCat = txs.first().categoryName
                            val color = categoryColorForName(firstCat, categories)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(color.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = categoryIconForName(firstCat, categories),
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MidnightInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${txs.size} entr${if (txs.size == 1) "y" else "ies"} · ${txs.first().categoryName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateSecondary
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (sumIncome > 0) {
                                Text(
                                    text = formatMoney(sumIncome, currency),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = BlueIncome
                                )
                            }
                            if (sumExpense > 0) {
                                Text(
                                    text = formatMoney(sumExpense, currency),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CoralPrimary
                                )
                            }
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = DividerHairline)
                }
            }
        }
    }
}

@Composable
fun EmptyLedgerPlaceholder(message: String = "No data available.") {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Savings,
                contentDescription = null,
                tint = SlateTertiary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = SlateTertiary
            )
        }
    }
}

@Composable
private fun TransactionFilterDialog(
    accounts: List<FinanceAccount>,
    selectedAccountId: String?,
    selectedTxType: TxType?,
    onDismiss: () -> Unit,
    onApply: (String?, TxType?) -> Unit
) {
    var tempAccountId by remember { mutableStateOf(selectedAccountId) }
    var tempType by remember { mutableStateOf(selectedTxType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter Transactions", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Type", style = MaterialTheme.typography.labelLarge, color = SlateSecondary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = tempType == null,
                        onClick = { tempType = null },
                        label = { Text("All") }
                    )
                    TxType.entries.forEach { t ->
                        FilterChip(
                            selected = tempType == t,
                            onClick = { tempType = t },
                            label = { Text(t.label) }
                        )
                    }
                }
                Text("Account", style = MaterialTheme.typography.labelLarge, color = SlateSecondary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = tempAccountId == null,
                        onClick = { tempAccountId = null },
                        label = { Text("All Accounts") }
                    )
                    accounts.forEach { acc ->
                        FilterChip(
                            selected = tempAccountId == acc.id,
                            onClick = { tempAccountId = acc.id },
                            label = { Text(acc.name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(tempAccountId, tempType) }) {
                Text("Apply", color = CoralPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun TransactionDetailDialog(
    transaction: FinanceTransaction,
    categories: List<FinanceCategory>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val thumb = rememberBase64ImageBitmap(transaction.receiptThumbnail)
    val catColor = categoryColorForName(transaction.categoryName, categories)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(catColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIconForName(transaction.categoryName, categories),
                        contentDescription = null,
                        tint = catColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.merchant.ifBlank { transaction.categoryName },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${transaction.dateIso} · ${transaction.accountName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateSecondary
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = formatMoney(transaction.amount, transaction.currency),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (transaction.resolvedTxType == TxType.INCOME) BlueIncome else CoralPrimary
                )
                Text(
                    text = "${transaction.categoryName}${if (transaction.subcategory.isNotBlank()) " · ${transaction.subcategory}" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MidnightInk
                )
                if (transaction.note.isNotBlank()) {
                    Text(
                        text = transaction.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateSecondary
                    )
                }
                if (transaction.aiConfidence != null) {
                    Text(
                        text = "AI Confidence: ${(transaction.aiConfidence * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = SlateSecondary
                    )
                }
                if (!transaction.lineItems.isNullOrEmpty()) {
                    HorizontalDivider(color = DividerHairline)
                    Text("Line Items", style = MaterialTheme.typography.labelLarge, color = MidnightInk)
                    transaction.lineItems.take(6).forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${item.itemName} (${item.suggestedCategory})",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateSecondary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatMoney(item.amount, transaction.currency),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MidnightInk
                            )
                        }
                    }
                }
                if (thumb != null) {
                    Image(
                        bitmap = thumb,
                        contentDescription = "Attached receipt thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, DividerHairline, RoundedCornerShape(12.dp))
                    )
                }
            }
        },
        confirmButton = {
            Row {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete transaction",
                        tint = CoralPrimary
                    )
                }
                TextButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
