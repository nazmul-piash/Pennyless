package com.example.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.ReceiptAiService
import com.example.data.FinanceRepository
import com.example.model.ExtractedReceiptResult
import com.example.model.FinanceAccount
import com.example.model.FinanceCategory
import com.example.model.FinanceDashboardSnapshot
import com.example.model.FinanceTransaction
import com.example.model.RecurringBill
import com.example.model.TxType
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.util.DateUtils
import com.example.util.YearMonthKey
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MainNavTab { TRANSACTIONS, STATS, ACCOUNTS, MORE }
enum class TransSubTab(val label: String) {
    DAILY("Daily"),
    CALENDAR("Calendar"),
    MONTHLY("Monthly"),
    SUMMARY("Summary"),
    DESCRIPTION("Description")
}

sealed interface ScannerUiState {
    data object Idle : ScannerUiState
    data class VoiceCapture(val autoStartRecording: Boolean = true) : ScannerUiState
    data class Scanning(
        val previewBitmap: Bitmap? = null,
        val isVoice: Boolean = false,
        val statusLabel: String = "Extracting merchant, totals & line items..."
    ) : ScannerUiState
    data class ClarifyingVoice(
        val partial: ExtractedReceiptResult,
        val isSubmittingReply: Boolean = false,
        val errorMessage: String? = null
    ) : ScannerUiState
    data class Review(
        val extracted: ExtractedReceiptResult,
        val thumbnailBase64: String,
        val previewBitmap: Bitmap?
    ) : ScannerUiState
    data class Error(
        val message: String,
        val previewBitmap: Bitmap? = null,
        val isVoice: Boolean = false
    ) : ScannerUiState
}

class MainViewModel(
    private val repository: FinanceRepository,
    private val currentUserId: String,
    private val initialDisplayName: String,
    private val initialEmail: String
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(MainNavTab.TRANSACTIONS)
    val selectedTab: StateFlow<MainNavTab> = _selectedTab.asStateFlow()

    private val _selectedSubTab = MutableStateFlow(TransSubTab.DAILY)
    val selectedSubTab: StateFlow<TransSubTab> = _selectedSubTab.asStateFlow()

    private val _selectedMonth = MutableStateFlow(DateUtils.currentYearMonth())
    val selectedMonth: StateFlow<YearMonthKey> = _selectedMonth.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterAccountId = MutableStateFlow<String?>(null)
    val filterAccountId: StateFlow<String?> = _filterAccountId.asStateFlow()

    private val _filterTxType = MutableStateFlow<TxType?>(null)
    val filterTxType: StateFlow<TxType?> = _filterTxType.asStateFlow()

    private val _statsTxType = MutableStateFlow(TxType.EXPENSE)
    val statsTxType: StateFlow<TxType> = _statsTxType.asStateFlow()

    private val _scannerState = MutableStateFlow<ScannerUiState>(ScannerUiState.Idle)
    val scannerState: StateFlow<ScannerUiState> = _scannerState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    val dashboardState: StateFlow<UiState<FinanceDashboardSnapshot>> = combine(
        repository.observeProfile(currentUserId),
        repository.observeAccounts(currentUserId),
        repository.observeCategories(currentUserId),
        repository.observeTransactions(currentUserId),
        repository.observeRecurringBills(currentUserId)
    ) { profile, accounts, categories, transactions, bills ->
        val resolvedProfile = profile ?: UserProfile(
            userId = currentUserId,
            displayName = initialDisplayName.ifBlank { "Alex Mercer" },
            email = initialEmail
        )
        UiState.Success(
            FinanceDashboardSnapshot(
                profile = resolvedProfile,
                accounts = accounts,
                categories = categories,
                transactions = transactions,
                recurringBills = bills
            )
        ) as UiState<FinanceDashboardSnapshot>
    }.catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing dashboard streams", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load financial data."))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading
    )

    init {
        viewModelScope.launch {
            val res = repository.ensureSeededOnFirstLogin(
                userId = currentUserId,
                displayName = initialDisplayName,
                email = initialEmail
            )
            res.onFailure { e ->
                _actionMessage.value = e.localizedMessage ?: "Failed to initialize account."
            }
        }
    }

    fun selectTab(tab: MainNavTab) { _selectedTab.value = tab }
    fun selectSubTab(subTab: TransSubTab) { _selectedSubTab.value = subTab }

    fun shiftMonth(delta: Int) {
        _selectedMonth.value = _selectedMonth.value.plusMonths(delta)
    }

    fun shiftYear(delta: Int) {
        _selectedMonth.value = _selectedMonth.value.plusMonths(delta * 12)
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setFilters(accountId: String?, txType: TxType?) {
        _filterAccountId.value = accountId
        _filterTxType.value = txType
    }

    fun setStatsType(type: TxType) { _statsTxType.value = type }
    fun clearActionMessage() { _actionMessage.value = null }

    fun startReceiptScan(bitmap: Bitmap, defaultCurrency: String) {
        _scannerState.value = ScannerUiState.Scanning(
            previewBitmap = bitmap,
            isVoice = false,
            statusLabel = "Extracting merchant, totals & line items..."
        )
        viewModelScope.launch {
            val result = ReceiptAiService.analyzeReceipt(
                bitmap = bitmap,
                defaultCurrency = defaultCurrency,
                fallbackDateIso = DateUtils.todayIso()
            )
            result.fold(
                onSuccess = { (extracted, thumbBase64) ->
                    _scannerState.value = ScannerUiState.Review(
                        extracted = extracted,
                        thumbnailBase64 = thumbBase64,
                        previewBitmap = bitmap
                    )
                },
                onFailure = { error ->
                    _scannerState.value = ScannerUiState.Error(
                        message = error.localizedMessage ?: "Could not scan receipt.",
                        previewBitmap = bitmap,
                        isVoice = false
                    )
                }
            )
        }
    }

    fun openVoiceCapture(autoStartRecording: Boolean = true) {
        _scannerState.value = ScannerUiState.VoiceCapture(autoStartRecording = autoStartRecording)
    }

    fun startVoiceAudioScan(
        audioBytes: ByteArray,
        defaultCurrency: String,
        accountNames: List<String>
    ) {
        _scannerState.value = ScannerUiState.Scanning(
            previewBitmap = null,
            isVoice = true,
            statusLabel = "Understanding multilingual speech & extracting entry..."
        )
        viewModelScope.launch {
            val result = ReceiptAiService.analyzeVoiceAudio(
                audioBytes = audioBytes,
                defaultCurrency = defaultCurrency,
                todayIso = DateUtils.todayIso(),
                yesterdayIso = DateUtils.yesterdayIso(),
                accountNames = accountNames
            )
            handleVoiceResult(result)
        }
    }

    fun startVoiceTextScan(
        spokenText: String,
        defaultCurrency: String,
        accountNames: List<String>
    ) {
        if (spokenText.isBlank()) return
        _scannerState.value = ScannerUiState.Scanning(
            previewBitmap = null,
            isVoice = true,
            statusLabel = "Analyzing phrase & categorizing transaction..."
        )
        viewModelScope.launch {
            val result = ReceiptAiService.analyzeVoiceTranscript(
                spokenText = spokenText,
                defaultCurrency = defaultCurrency,
                todayIso = DateUtils.todayIso(),
                yesterdayIso = DateUtils.yesterdayIso(),
                accountNames = accountNames
            )
            handleVoiceResult(result)
        }
    }

    fun submitVoiceClarification(
        currentPartial: ExtractedReceiptResult,
        replyText: String?,
        followUpAudioBytes: ByteArray?,
        defaultCurrency: String,
        accountNames: List<String>
    ) {
        _scannerState.value = ScannerUiState.ClarifyingVoice(
            partial = currentPartial,
            isSubmittingReply = true,
            errorMessage = null
        )
        viewModelScope.launch {
            val result = ReceiptAiService.refineVoiceExtraction(
                previous = currentPartial,
                replyText = replyText,
                followUpAudioBytes = followUpAudioBytes,
                defaultCurrency = defaultCurrency,
                todayIso = DateUtils.todayIso(),
                yesterdayIso = DateUtils.yesterdayIso(),
                accountNames = accountNames
            )
            result.fold(
                onSuccess = { updated ->
                    if (updated.missingFields.isEmpty() && updated.totalAmount > 0.0) {
                        _scannerState.value = ScannerUiState.Review(
                            extracted = updated,
                            thumbnailBase64 = "",
                            previewBitmap = null
                        )
                    } else {
                        _scannerState.value = ScannerUiState.ClarifyingVoice(
                            partial = updated,
                            isSubmittingReply = false
                        )
                    }
                },
                onFailure = { error ->
                    _scannerState.value = ScannerUiState.ClarifyingVoice(
                        partial = currentPartial,
                        isSubmittingReply = false,
                        errorMessage = error.localizedMessage ?: "Could not process clarification."
                    )
                }
            )
        }
    }

    fun skipClarificationToReview(partial: ExtractedReceiptResult) {
        _scannerState.value = ScannerUiState.Review(
            extracted = partial.copy(
                totalAmount = partial.totalAmount.coerceAtLeast(0.01),
                missingFields = emptyList(),
                clarificationQuestions = emptyList()
            ),
            thumbnailBase64 = "",
            previewBitmap = null
        )
    }

    private fun handleVoiceResult(result: Result<ExtractedReceiptResult>) {
        result.fold(
            onSuccess = { extracted ->
                if (extracted.missingFields.isNotEmpty() || extracted.totalAmount <= 0.0) {
                    _scannerState.value = ScannerUiState.ClarifyingVoice(
                        partial = extracted,
                        isSubmittingReply = false
                    )
                } else {
                    _scannerState.value = ScannerUiState.Review(
                        extracted = extracted,
                        thumbnailBase64 = "",
                        previewBitmap = null
                    )
                }
            },
            onFailure = { error ->
                _scannerState.value = ScannerUiState.Error(
                    message = error.localizedMessage ?: "Could not understand voice entry.",
                    previewBitmap = null,
                    isVoice = true
                )
            }
        )
    }

    fun resetScanner() {
        _scannerState.value = ScannerUiState.Idle
    }

    fun saveScannedTransactions(
        transactions: List<FinanceTransaction>,
        account: FinanceAccount,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.saveTransactionsAndAdjustAccount(
                userId = currentUserId,
                transactions = transactions,
                account = account
            )
            result.fold(
                onSuccess = {
                    _scannerState.value = ScannerUiState.Idle
                    _actionMessage.value = if (transactions.size > 1) {
                        "Saved ${transactions.size} split entries to ${account.name}"
                    } else {
                        "Saved receipt to ${account.name}"
                    }
                    onComplete()
                },
                onFailure = { e ->
                    _actionMessage.value = e.localizedMessage ?: "Failed to save scanned receipt."
                }
            )
        }
    }

    fun saveManualTransaction(
        tx: FinanceTransaction,
        account: FinanceAccount,
        existingToReplace: FinanceTransaction? = null,
        previousAccount: FinanceAccount? = null,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.saveTransactionsAndAdjustAccount(
                userId = currentUserId,
                transactions = listOf(tx),
                account = account,
                existingTransactionToReplace = existingToReplace,
                previousAccount = previousAccount
            )
            res.fold(
                onSuccess = {
                    _actionMessage.value = if (existingToReplace != null) "Transaction updated" else "Transaction saved"
                    onComplete()
                },
                onFailure = { e ->
                    _actionMessage.value = e.localizedMessage ?: "Failed to save transaction."
                }
            )
        }
    }

    fun deleteTransaction(tx: FinanceTransaction, account: FinanceAccount?) {
        viewModelScope.launch {
            repository.deleteTransactionAndRevertAccount(currentUserId, tx, account)
                .onSuccess { _actionMessage.value = "Transaction deleted" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Delete failed" }
        }
    }

    fun saveAccount(account: FinanceAccount, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.saveAccount(currentUserId, account)
                .onSuccess {
                    _actionMessage.value = "Account saved"
                    onComplete()
                }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to save account" }
        }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            repository.deleteAccount(currentUserId, accountId)
                .onSuccess { _actionMessage.value = "Account deleted" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to delete account" }
        }
    }

    fun transferBetweenAccounts(
        fromAccount: FinanceAccount,
        toAccount: FinanceAccount,
        amount: Double,
        currency: String,
        note: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            repository.transferBetweenAccounts(
                userId = currentUserId,
                fromAccount = fromAccount,
                toAccount = toAccount,
                amount = amount,
                currency = currency,
                dateIso = DateUtils.todayIso(),
                note = note
            ).onSuccess {
                _actionMessage.value = "Transferred to ${toAccount.name}"
                onComplete()
            }.onFailure {
                _actionMessage.value = it.localizedMessage ?: "Transfer failed"
            }
        }
    }

    fun saveCategory(category: FinanceCategory, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.saveCategory(currentUserId, category)
                .onSuccess {
                    _actionMessage.value = "Category saved"
                    onComplete()
                }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to save category" }
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            repository.deleteCategory(currentUserId, categoryId)
                .onSuccess { _actionMessage.value = "Category removed" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to remove category" }
        }
    }

    fun saveRecurringBill(bill: RecurringBill, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.saveRecurringBill(currentUserId, bill)
                .onSuccess {
                    _actionMessage.value = "Recurring bill saved"
                    onComplete()
                }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to save bill" }
        }
    }

    fun deleteRecurringBill(billId: String) {
        viewModelScope.launch {
            repository.deleteRecurringBill(currentUserId, billId)
                .onSuccess { _actionMessage.value = "Bill removed" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to remove bill" }
        }
    }

    fun payRecurringBill(bill: RecurringBill, account: FinanceAccount, currency: String) {
        viewModelScope.launch {
            repository.payRecurringBill(
                userId = currentUserId,
                bill = bill,
                account = account,
                currency = currency,
                dateIso = DateUtils.todayIso(),
                yearMonth = DateUtils.currentYearMonth().toIsoPrefix()
            ).onSuccess {
                _actionMessage.value = "Paid ${bill.title}"
            }.onFailure {
                _actionMessage.value = it.localizedMessage ?: "Bill payment failed"
            }
        }
    }

    fun updateProfile(displayName: String, currency: String, monthlyBudget: Double, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.updateProfile(currentUserId, displayName, currency, monthlyBudget)
                .onSuccess {
                    _actionMessage.value = "Profile updated"
                    onComplete()
                }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Update failed" }
        }
    }

    fun resetDemoData() {
        viewModelScope.launch {
            repository.resetDemoData(currentUserId, initialDisplayName, initialEmail)
                .onSuccess { _actionMessage.value = "Demo data restored" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Reset failed" }
        }
    }

    private companion object {
        const val TAG = "MainViewModel"
    }
}
