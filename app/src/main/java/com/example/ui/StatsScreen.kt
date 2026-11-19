package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.FinanceDashboardSnapshot
import com.example.model.TxType
import com.example.model.formatMoney
import com.example.ui.components.categoryColorForName
import com.example.ui.components.categoryIconForName
import com.example.ui.theme.BlueIncome
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.DividerHairline
import com.example.ui.theme.MidnightInk
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SurfaceGroupedBg
import com.example.ui.theme.SurfaceSubtle
import com.example.util.YearMonthKey
import kotlin.math.roundToInt

private data class CategoryStatItem(
    val categoryName: String,
    val total: Double,
    val percentage: Float,
    val color: Color,
    val count: Int
)

@Composable
fun StatsScreen(
    snapshot: FinanceDashboardSnapshot,
    selectedMonth: YearMonthKey,
    statsTxType: TxType,
    onSelectStatsType: (TxType) -> Unit,
    onShiftMonth: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val currency = snapshot.profile.currency
    val prefix = selectedMonth.toIsoPrefix()
    var highlightedCategory by remember { mutableStateOf<String?>(null) }

    val periodTxs = remember(snapshot.transactions, prefix, statsTxType) {
        snapshot.transactions.filter {
            it.dateIso.startsWith(prefix) && it.resolvedTxType == statsTxType
        }
    }

    val grandTotal = remember(periodTxs) { periodTxs.sumOf { it.amount } }
    val breakdown = remember(periodTxs, snapshot.categories, grandTotal) {
        periodTxs.groupBy { it.categoryName }
            .map { (name, txs) ->
                val sum = txs.sumOf { it.amount }
                val pct = if (grandTotal > 0.0) ((sum / grandTotal) * 100.0).toFloat() else 0f
                CategoryStatItem(
                    categoryName = name,
                    total = sum,
                    percentage = pct,
                    color = categoryColorForName(name, snapshot.categories),
                    count = txs.size
                )
            }
            .sortedByDescending { it.total }
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
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { onShiftMonth(-1) }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    text = "Stats · ${selectedMonth.shortLabel()}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MidnightInk
                )
                IconButton(onClick = { onShiftMonth(1) }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next month")
                }
            }

            // Segmented iOS-style toggle: Expense | Income
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceSubtle)
                    .padding(4.dp)
            ) {
                listOf(TxType.EXPENSE, TxType.INCOME).forEach { type ->
                    val selected = statsTxType == type
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) Color.White else Color.Transparent)
                            .clickable {
                                highlightedCategory = null
                                onSelectStatsType(type)
                            }
                            .padding(vertical = 8.dp)
                            .testTag("stats_toggle_${type.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = type.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected) {
                                if (type == TxType.EXPENSE) CoralPrimary else BlueIncome
                            } else SlateSecondary,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = DividerHairline)
        }

        if (breakdown.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().widthIn(max = 720.dp)) {
                EmptyLedgerPlaceholder("No ${statsTxType.label.lowercase()} records for ${selectedMonth.shortLabel()}.")
            }
            return
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item(key = "donut_chart") {
                Surface(
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 22.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val animProgress by animateFloatAsState(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 500),
                            label = "donut_anim"
                        )
                        val selectedItem = breakdown.firstOrNull { it.categoryName == highlightedCategory }

                        Box(
                            modifier = Modifier.size(210.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeWidth = 34.dp.toPx()
                                val diameter = size.minDimension - strokeWidth
                                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                                val arcSize = Size(diameter, diameter)
                                var startAngle = -90f

                                breakdown.forEach { item ->
                                    val sweep = (item.percentage / 100f) * 360f * animProgress
                                    val isDimmed = highlightedCategory != null && highlightedCategory != item.categoryName
                                    drawArc(
                                        color = if (isDimmed) item.color.copy(alpha = 0.25f) else item.color,
                                        startAngle = startAngle,
                                        sweepAngle = (sweep - 2.2f).coerceAtLeast(1f),
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = arcSize,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                    )
                                    startAngle += sweep
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = selectedItem?.categoryName ?: "Total ${statsTxType.label}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SlateSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatMoney(selectedItem?.total ?: grandTotal, currency),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (statsTxType == TxType.EXPENSE) CoralPrimary else BlueIncome
                                )
                                if (selectedItem != null) {
                                    Text(
                                        text = "${selectedItem.percentage.roundToInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SlateSecondary
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = DividerHairline)
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(thickness = 1.dp, color = DividerHairline)
            }

            itemsIndexed(breakdown, key = { _, item -> item.categoryName }) { _, item ->
                val isSelected = highlightedCategory == item.categoryName
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) item.color.copy(alpha = 0.08f) else Color.White)
                        .clickable {
                            highlightedCategory = if (isSelected) null else item.categoryName
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = item.color,
                        modifier = Modifier.width(50.dp)
                    ) {
                        Text(
                            text = "${item.percentage.roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(item.color.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = categoryIconForName(item.categoryName, snapshot.categories),
                            contentDescription = null,
                            tint = item.color,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.categoryName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MidnightInk
                        )
                        Text(
                            text = "${item.count} transaction${if (item.count == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateSecondary
                        )
                    }
                    Text(
                        text = formatMoney(item.total, currency),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MidnightInk
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = DividerHairline)
            }
        }
    }
}
