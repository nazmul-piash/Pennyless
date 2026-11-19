package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FinanceCategory
import com.example.model.FinanceDashboardSnapshot
import com.example.model.FinanceTransaction
import com.example.model.TxType
import com.example.model.formatCompactMoney
import com.example.model.formatMoney
import com.example.ui.theme.BlueIncome
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.CoralSoftBg
import com.example.ui.theme.DividerHairline
import com.example.ui.theme.MidnightInk
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SlateTertiary
import com.example.ui.theme.SurfaceGroupedBg
import com.example.ui.theme.SurfaceSubtle
import com.example.util.DateUtils
import com.example.util.YearMonthKey
import kotlin.math.roundToInt

@Composable
fun CalendarTransactionsView(
    yearMonth: YearMonthKey,
    transactions: List<FinanceTransaction>,
    categories: List<FinanceCategory>,
    currency: String,
    onClickTransaction: (FinanceTransaction) -> Unit,
    onAddForDate: (String) -> Unit
) {
    var selectedDateIso by remember { mutableStateOf<String?>(null) }

    val daysInMonth = yearMonth.daysInMonth()
    val firstDow = yearMonth.firstDayOfWeekIndex() // 0 = Sun
    val todayIso = remember { DateUtils.todayIso() }

    val txByDay = remember(transactions) {
        transactions.groupBy { DateUtils.parseDayOfMonth(it.dateIso) }
    }

    val totalCells = ((firstDow + daysInMonth + 6) / 7) * 7
    val weeksCount = totalCells / 7

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Day of week header (Sun..Sat)
        val headers = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(vertical = 6.dp)
        ) {
            headers.forEachIndexed { idx, label ->
                val color = when (idx) {
                    0 -> CoralPrimary
                    6 -> BlueIncome
                    else -> SlateSecondary
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = DividerHairline)

        // Calendar Grid
        Column(modifier = Modifier.fillMaxSize()) {
            for (week in 0 until weeksCount) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    for (col in 0 until 7) {
                        val cellIndex = week * 7 + col
                        val dayNumber = cellIndex - firstDow + 1
                        val isValidDay = dayNumber in 1..daysInMonth
                        val dateIso = if (isValidDay) DateUtils.isoFor(yearMonth.year, yearMonth.month, dayNumber) else ""
                        val isToday = isValidDay && dateIso == todayIso
                        val dayTxs = if (isValidDay) txByDay[dayNumber].orEmpty() else emptyList()
                        val dayInc = dayTxs.filter { it.resolvedTxType == TxType.INCOME }.sumOf { it.amount }
                        val dayExp = dayTxs.filter { it.resolvedTxType == TxType.EXPENSE }.sumOf { it.amount }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .border(0.5.dp, DividerHairline)
                                .background(if (isValidDay) Color.White else SurfaceGroupedBg.copy(alpha = 0.45f))
                                .clickable(enabled = isValidDay) {
                                    if (isValidDay) selectedDateIso = dateIso
                                }
                                .padding(3.dp)
                        ) {
                            if (isValidDay) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (isToday) MidnightSlate else Color.Transparent)
                                            .padding(horizontal = 3.dp, vertical = 1.dp)
                                    ) {
                                        val numColor = when {
                                            isToday -> Color.White
                                            col == 0 -> CoralPrimary
                                            col == 6 -> BlueIncome
                                            else -> MidnightInk
                                        }
                                        Text(
                                            text = if (dayNumber == 1) "1.${yearMonth.month}." else "$dayNumber",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 11.sp,
                                            color = numColor,
                                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        if (dayInc > 0.0) {
                                            Text(
                                                text = formatCompactMoney(dayInc),
                                                fontSize = 9.5.sp,
                                                lineHeight = 11.sp,
                                                color = BlueIncome,
                                                maxLines = 1,
                                                overflow = TextOverflow.Clip
                                            )
                                        }
                                        if (dayExp > 0.0) {
                                            Text(
                                                text = formatCompactMoney(dayExp),
                                                fontSize = 9.5.sp,
                                                lineHeight = 11.sp,
                                                color = CoralPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Clip
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val inspectingDate = selectedDateIso
    if (inspectingDate != null) {
        val dayTxs = transactions.filter { it.dateIso == inspectingDate }
        AlertDialog(
            onDismissRequest = { selectedDateIso = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$inspectingDate (${DateUtils.dayOfWeekShort(inspectingDate)})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = {
                        selectedDateIso = null
                        onAddForDate(inspectingDate)
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "Add transaction for $inspectingDate",
                            tint = CoralPrimary
                        )
                    }
                }
            },
            text = {
                if (dayTxs.isEmpty()) {
                    Text(
                        text = "No transactions logged on this day. Tap + to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateSecondary
                    )
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dayTxs.forEach { tx ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceGroupedBg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedDateIso = null
                                        onClickTransaction(tx)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tx.merchant.ifBlank { tx.categoryName },
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MidnightInk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${tx.categoryName} · ${tx.accountName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SlateSecondary
                                        )
                                    }
                                    Text(
                                        text = formatMoney(tx.amount, currency),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (tx.resolvedTxType == TxType.INCOME) BlueIncome else CoralPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDateIso = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun MonthlySummaryView(
    year: Int,
    yearTransactions: List<FinanceTransaction>,
    currency: String,
    onSelectMonth: (Int) -> Unit
) {
    val currentYm = remember { DateUtils.currentYearMonth() }
    var expandedMonth by remember(year) {
        mutableStateOf(if (year == currentYm.year) currentYm.month else 12)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        items((12 downTo 1).toList(), key = { it }) { month ->
            val ym = YearMonthKey(year, month)
            val prefix = ym.toIsoPrefix()
            val monthTxs = yearTransactions.filter { it.dateIso.startsWith(prefix) }
            val inc = monthTxs.filter { it.resolvedTxType == TxType.INCOME }.sumOf { it.amount }
            val exp = monthTxs.filter { it.resolvedTxType == TxType.EXPENSE }.sumOf { it.amount }
            val net = inc - exp
            val daysInM = ym.daysInMonth()
            val isExpanded = expandedMonth == month

            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedMonth = if (isExpanded) -1 else month
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.1f)) {
                        Text(
                            text = ym.monthNameOnly(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MidnightInk
                        )
                        Text(
                            text = "1.$month. ~ $daysInM.$month.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateTertiary
                        )
                    }
                    Text(
                        text = formatMoney(inc, currency),
                        style = MaterialTheme.typography.bodyMedium,
                        color = BlueIncome,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = formatMoney(exp, currency),
                            style = MaterialTheme.typography.bodyMedium,
                            color = CoralPrimary
                        )
                        Text(
                            text = "Total ${formatMoney(net, currency)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateTertiary
                        )
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = DividerHairline)

                if (isExpanded) {
                    val weekBuckets = listOf(22..daysInM, 15..21, 8..14, 1..7)
                    val todayDay = DateUtils.currentDayOfMonth()
                    weekBuckets.forEach { range ->
                        val weekTxs = monthTxs.filter { DateUtils.parseDayOfMonth(it.dateIso) in range }
                        val wInc = weekTxs.filter { it.resolvedTxType == TxType.INCOME }.sumOf { it.amount }
                        val wExp = weekTxs.filter { it.resolvedTxType == TxType.EXPENSE }.sumOf { it.amount }
                        val isCurrentWeek = year == currentYm.year && month == currentYm.month && todayDay in range

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isCurrentWeek) CoralSoftBg else SurfaceGroupedBg.copy(alpha = 0.4f))
                                .clickable { onSelectMonth(month) }
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${range.first}.$month. ~ ${range.last}.$month.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MidnightInk,
                                modifier = Modifier.weight(1.1f)
                            )
                            Text(
                                text = formatMoney(wInc, currency),
                                style = MaterialTheme.typography.bodySmall,
                                color = BlueIncome,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = formatMoney(wExp, currency),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CoralPrimary
                                )
                                Text(
                                    text = formatMoney(wInc - wExp, currency),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SlateTertiary
                                )
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = DividerHairline)
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryOverviewView(
    snapshot: FinanceDashboardSnapshot,
    monthTransactions: List<FinanceTransaction>,
    selectedMonth: YearMonthKey,
    onExportCsv: () -> Unit
) {
    val currency = snapshot.profile.currency
    val totalExpense = monthTransactions.filter { it.resolvedTxType == TxType.EXPENSE }.sumOf { it.amount }
    val totalBudget = snapshot.profile.monthlyBudget
    val budgetRatio = if (totalBudget > 0) (totalExpense / totalBudget).toFloat().coerceIn(0f, 1f) else 0f
    val budgetPercent = if (totalBudget > 0) ((totalExpense / totalBudget) * 100.0).roundToInt() else 0
    val remaining = (totalBudget - totalExpense).coerceAtLeast(0.0)

    val daysInMonth = selectedMonth.daysInMonth().coerceAtLeast(1)
    val todayFraction = (DateUtils.currentDayOfMonth().toFloat() / daysInMonth.toFloat()).coerceIn(0.05f, 0.95f)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceGroupedBg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DividerHairline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MidnightInk,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Accounts",
                            style = MaterialTheme.typography.titleMedium,
                            color = MidnightInk
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceGroupedBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, DividerHairline, RoundedCornerShape(10.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Exp. (Cash, Accounts)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SlateSecondary
                                )
                                Text(
                                    text = formatMoney(totalExpense, currency),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MidnightInk
                                )
                            }
                            snapshot.accounts.forEach { acc ->
                                val accExp = monthTransactions
                                    .filter { it.accountId == acc.id && it.resolvedTxType == TxType.EXPENSE }
                                    .sumOf { it.amount }
                                if (accExp > 0.0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = acc.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SlateTertiary
                                        )
                                        Text(
                                            text = formatMoney(accExp, currency),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CoralPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DividerHairline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.EditNote,
                            contentDescription = null,
                            tint = MidnightInk,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Budget",
                            style = MaterialTheme.typography.titleMedium,
                            color = MidnightInk
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(0.36f)) {
                            Text(
                                text = "Total Budget",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatMoney(totalBudget, currency),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MidnightInk
                            )
                        }

                        Column(modifier = Modifier.weight(0.64f)) {
                            // "Today" callout marker matching Money Manager Summary screenshot
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Spacer(modifier = Modifier.weight(todayFraction.coerceAtLeast(0.05f)))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = SlateSecondary
                                ) {
                                    Text(
                                        text = "Today",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.weight((1f - todayFraction).coerceAtLeast(0.05f)))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SurfaceSubtle)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(budgetRatio)
                                        .height(22.dp)
                                        .background(if (budgetPercent > 90) CoralPrimary else BlueIncome)
                                )
                                Text(
                                    text = "$budgetPercent%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MidnightInk,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 8.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatMoney(totalExpense, currency),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BlueIncome,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = formatMoney(remaining, currency),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MidnightInk
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onExportCsv,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("summary_export_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Export data to CSV",
                    style = MaterialTheme.typography.labelLarge,
                    color = MidnightInk
                )
            }
            Spacer(modifier = Modifier.height(90.dp))
        }
    }
}
