package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.FinanceDashboardSnapshot
import com.example.model.FinanceTransaction
import com.example.model.UiState
import com.example.ui.theme.BlueIncome
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.DividerHairline
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SurfaceGroupedBg
import com.example.util.DateUtils
import com.example.viewmodel.MainNavTab
import com.example.viewmodel.MainViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@Composable
fun MainAppScaffold(viewModel: MainViewModel) {
    val context = LocalContext.current
    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val selectedSubTab by viewModel.selectedSubTab.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterAccountId by viewModel.filterAccountId.collectAsStateWithLifecycle()
    val filterTxType by viewModel.filterTxType.collectAsStateWithLifecycle()
    val statsTxType by viewModel.statsTxType.collectAsStateWithLifecycle()
    val scannerState by viewModel.scannerState.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    var showScannerModal by remember { mutableStateOf(false) }
    var showManualSheet by remember { mutableStateOf(false) }
    var editingTx by remember { mutableStateOf<FinanceTransaction?>(null) }
    var manualInitialDate by remember { mutableStateOf(DateUtils.todayIso()) }
    var openBillsFromShortcut by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    Scaffold(
        containerColor = SurfaceGroupedBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            MoneyManagerBottomBar(
                selectedTab = selectedTab,
                onSelectTab = { viewModel.selectTab(it) }
            )
        },
        floatingActionButton = {
            if (selectedTab == MainNavTab.TRANSACTIONS && dashboardState is UiState.Success) {
                // Unified Quick-Add Cluster: Secondary (+) above side-by-side Mic (Voice) + Camera (Receipt Scan)
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            editingTx = null
                            manualInitialDate = DateUtils.todayIso()
                            showManualSheet = true
                        },
                        shape = CircleShape,
                        containerColor = MidnightSlate,
                        contentColor = Color.White,
                        elevation = FloatingActionButtonDefaults.elevation(4.dp),
                        modifier = Modifier.testTag("manual_add_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "Manual entry"
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FloatingActionButton(
                            onClick = {
                                viewModel.openVoiceCapture(autoStartRecording = true)
                                showScannerModal = true
                            },
                            shape = CircleShape,
                            containerColor = BlueIncome,
                            contentColor = Color.White,
                            elevation = FloatingActionButtonDefaults.elevation(6.dp),
                            modifier = Modifier
                                .size(56.dp)
                                .testTag("voice_record_fab")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Mic,
                                contentDescription = "Voice transaction input",
                                modifier = Modifier.size(25.dp)
                            )
                        }

                        FloatingActionButton(
                            onClick = {
                                viewModel.resetScanner()
                                showScannerModal = true
                            },
                            shape = CircleShape,
                            containerColor = CoralPrimary,
                            contentColor = Color.White,
                            elevation = FloatingActionButtonDefaults.elevation(6.dp),
                            modifier = Modifier
                                .size(60.dp)
                                .testTag("scan_receipt_fab")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CameraAlt,
                                contentDescription = "Scan receipt with AI",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (val state = dashboardState) {
                UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CoralPrimary)
                    }
                }

                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = CoralPrimary
                        )
                    }
                }

                is UiState.Success -> {
                    val snapshot = state.data
                    when (selectedTab) {
                        MainNavTab.TRANSACTIONS -> TransactionsScreen(
                            snapshot = snapshot,
                            selectedSubTab = selectedSubTab,
                            selectedMonth = selectedMonth,
                            searchQuery = searchQuery,
                            filterAccountId = filterAccountId,
                            filterTxType = filterTxType,
                            onSelectSubTab = { viewModel.selectSubTab(it) },
                            onShiftMonth = { viewModel.shiftMonth(it) },
                            onShiftYear = { viewModel.shiftYear(it) },
                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                            onApplyFilters = { accId, t -> viewModel.setFilters(accId, t) },
                            onOpenRecurringBills = {
                                openBillsFromShortcut = true
                                viewModel.selectTab(MainNavTab.MORE)
                            },
                            onOpenAddManualForDate = { date ->
                                editingTx = null
                                manualInitialDate = date
                                showManualSheet = true
                            },
                            onEditTransaction = { tx ->
                                editingTx = tx
                                manualInitialDate = tx.dateIso
                                showManualSheet = true
                            },
                            onDeleteTransaction = { tx ->
                                val acc = snapshot.accounts.firstOrNull { it.id == tx.accountId }
                                viewModel.deleteTransaction(tx, acc)
                            },
                            onExportCsv = { shareExportFile(context, snapshot, asJson = false) }
                        )

                        MainNavTab.STATS -> StatsScreen(
                            snapshot = snapshot,
                            selectedMonth = selectedMonth,
                            statsTxType = statsTxType,
                            onSelectStatsType = { viewModel.setStatsType(it) },
                            onShiftMonth = { viewModel.shiftMonth(it) }
                        )

                        MainNavTab.ACCOUNTS -> AccountsScreen(
                            snapshot = snapshot,
                            onSaveAccount = { acc, done -> viewModel.saveAccount(acc, done) },
                            onDeleteAccount = { viewModel.deleteAccount(it) },
                            onTransfer = { from, to, amt, note, done ->
                                viewModel.transferBetweenAccounts(from, to, amt, snapshot.profile.currency, note, done)
                            }
                        )

                        MainNavTab.MORE -> MoreScreen(
                            snapshot = snapshot,
                            openBillsInitially = openBillsFromShortcut,
                            onClearOpenBillsFlag = { openBillsFromShortcut = false },
                            onUpdateProfile = { name, cur, budget, done ->
                                viewModel.updateProfile(name, cur, budget, done)
                            },
                            onSaveCategory = { cat, done -> viewModel.saveCategory(cat, done) },
                            onDeleteCategory = { viewModel.deleteCategory(it) },
                            onSaveBill = { bill, done -> viewModel.saveRecurringBill(bill, done) },
                            onDeleteBill = { viewModel.deleteRecurringBill(it) },
                            onPayBill = { bill, acc ->
                                viewModel.payRecurringBill(bill, acc, snapshot.profile.currency)
                            },
                            onExportCsv = { shareExportFile(context, snapshot, asJson = false) },
                            onExportJson = { shareExportFile(context, snapshot, asJson = true) },
                            onResetDemoData = { viewModel.resetDemoData() }
                        )
                    }
                }
            }
        }
    }

    val currentSnapshot = (dashboardState as? UiState.Success)?.data
    if (showScannerModal && currentSnapshot != null) {
        val accountNames = remember(currentSnapshot.accounts) {
            currentSnapshot.accounts.map { it.name }
        }
        ReceiptScannerModal(
            scannerState = scannerState,
            accounts = currentSnapshot.accounts,
            categories = currentSnapshot.categories,
            defaultCurrency = currentSnapshot.profile.currency,
            onStartScan = { bmp ->
                viewModel.startReceiptScan(bmp, currentSnapshot.profile.currency)
            },
            onVoiceAudioCaptured = { bytes ->
                viewModel.startVoiceAudioScan(
                    audioBytes = bytes,
                    defaultCurrency = currentSnapshot.profile.currency,
                    accountNames = accountNames
                )
            },
            onVoiceTextSubmitted = { spokenText ->
                viewModel.startVoiceTextScan(
                    spokenText = spokenText,
                    defaultCurrency = currentSnapshot.profile.currency,
                    accountNames = accountNames
                )
            },
            onSubmitVoiceClarification = { partial, replyText, followUpAudio ->
                viewModel.submitVoiceClarification(
                    currentPartial = partial,
                    replyText = replyText,
                    followUpAudioBytes = followUpAudio,
                    defaultCurrency = currentSnapshot.profile.currency,
                    accountNames = accountNames
                )
            },
            onSkipClarificationToReview = { partial ->
                viewModel.skipClarificationToReview(partial)
            },
            onOpenVoiceRetry = {
                viewModel.openVoiceCapture(autoStartRecording = false)
            },
            onSaveTransactions = { txs, acc ->
                viewModel.saveScannedTransactions(txs, acc) {
                    showScannerModal = false
                }
            },
            onRetryReset = { viewModel.resetScanner() },
            onClose = {
                viewModel.resetScanner()
                showScannerModal = false
            }
        )
    }

    if (showManualSheet && currentSnapshot != null) {
        ManualTransactionSheet(
            initialTransaction = editingTx,
            initialDateIso = manualInitialDate,
            accounts = currentSnapshot.accounts,
            categories = currentSnapshot.categories,
            currency = currentSnapshot.profile.currency,
            onSave = { tx, acc ->
                val prevAcc = editingTx?.let { prev ->
                    currentSnapshot.accounts.firstOrNull { it.id == prev.accountId }
                }
                viewModel.saveManualTransaction(
                    tx = tx,
                    account = acc,
                    existingToReplace = editingTx,
                    previousAccount = prevAcc
                ) {
                    editingTx = null
                    showManualSheet = false
                }
            },
            onClose = {
                editingTx = null
                showManualSheet = false
            }
        )
    }
}

@Composable
private fun MoneyManagerBottomBar(
    selectedTab: MainNavTab,
    onSelectTab: (MainNavTab) -> Unit
) {
    val todayLabel = remember { DateUtils.todayTabLabel() }
    Surface(color = Color.White) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(thickness = 1.dp, color = DividerHairline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(
                    label = todayLabel,
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    selected = selectedTab == MainNavTab.TRANSACTIONS,
                    onClick = { onSelectTab(MainNavTab.TRANSACTIONS) },
                    testTag = "nav_transactions",
                    modifier = Modifier.weight(1f)
                )
                BottomNavItem(
                    label = "Stats",
                    icon = Icons.Outlined.BarChart,
                    selected = selectedTab == MainNavTab.STATS,
                    onClick = { onSelectTab(MainNavTab.STATS) },
                    testTag = "nav_stats",
                    modifier = Modifier.weight(1f)
                )
                BottomNavItem(
                    label = "Accounts",
                    icon = Icons.Outlined.AccountBalanceWallet,
                    selected = selectedTab == MainNavTab.ACCOUNTS,
                    onClick = { onSelectTab(MainNavTab.ACCOUNTS) },
                    testTag = "nav_accounts",
                    modifier = Modifier.weight(1f)
                )
                BottomNavItem(
                    label = "More",
                    icon = Icons.Outlined.MoreHoriz,
                    selected = selectedTab == MainNavTab.MORE,
                    onClick = { onSelectTab(MainNavTab.MORE) },
                    testTag = "nav_more",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val tint = if (selected) CoralPrimary else SlateSecondary
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable { onClick() }
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

private fun shareExportFile(context: Context, snapshot: FinanceDashboardSnapshot, asJson: Boolean) {
    try {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = if (asJson) "penniless_backup.json" else "penniless_ledger.csv"
        val file = File(dir, fileName)
        val content = if (asJson) buildJsonExport(snapshot) else buildCsvExport(snapshot)
        file.writeText(content)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (asJson) "application/json" else "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Penniless Export ($fileName)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export Penniless Data"))
    } catch (_: Exception) {
    }
}

private fun buildCsvExport(snapshot: FinanceDashboardSnapshot): String {
    val sb = StringBuilder("Date,Type,Category,Subcategory,Merchant,Account,Amount,Currency,Note\n")
    snapshot.transactions.forEach { tx ->
        fun esc(s: String) = "\"${s.replace("\"", "\"\"")}\""
        sb.append(
            listOf(
                esc(tx.dateIso),
                esc(tx.txType),
                esc(tx.categoryName),
                esc(tx.subcategory),
                esc(tx.merchant),
                esc(tx.accountName),
                tx.amount.toString(),
                esc(tx.currency),
                esc(tx.note)
            ).joinToString(",")
        ).append('\n')
    }
    return sb.toString()
}

private fun buildJsonExport(snapshot: FinanceDashboardSnapshot): String {
    val root = JSONObject()
    root.put("currency", snapshot.profile.currency)
    root.put("monthlyBudget", snapshot.profile.monthlyBudget)
    val txArray = JSONArray()
    snapshot.transactions.forEach { tx ->
        txArray.put(
            JSONObject().apply {
                put("date", tx.dateIso)
                put("type", tx.txType)
                put("amount", tx.amount)
                put("currency", tx.currency)
                put("category", tx.categoryName)
                put("subcategory", tx.subcategory)
                put("merchant", tx.merchant)
                put("account", tx.accountName)
                put("note", tx.note)
            }
        )
    }
    root.put("transactions", txArray)
    return root.toString(2)
}
