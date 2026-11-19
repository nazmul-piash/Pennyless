package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.AccountType
import com.example.model.FinanceAccount
import com.example.model.FinanceCategory
import com.example.model.FinanceTransaction
import com.example.model.ReceiptLineItem
import com.example.model.RecurringBill
import com.example.model.TxType
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToLong

class FinanceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)
    private fun accountsCol(uid: String) = userDoc(uid).collection("accounts")
    private fun categoriesCol(uid: String) = userDoc(uid).collection("categories")
    private fun transactionsCol(uid: String) = userDoc(uid).collection("transactions")
    private fun billsCol(uid: String) = userDoc(uid).collection("recurringBills")

    fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "User mismatch" }
        val docRef = userDoc(uid)
        emitAll(
            docRef.snapshots()
                .map { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    handleFlowError(error, OperationType.GET, docRef.path, uid)
                    throw error
                }
        )
    }

    fun observeAccounts(userId: String): Flow<List<FinanceAccount>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "User mismatch" }
        val col = accountsCol(uid)
        emitAll(
            col.snapshots()
                .map { snap ->
                    snap.toObjects(FinanceAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.name }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, col.path, uid)
                    throw error
                }
        )
    }

    fun observeCategories(userId: String): Flow<List<FinanceCategory>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "User mismatch" }
        val col = categoriesCol(uid)
        emitAll(
            col.snapshots()
                .map { snap ->
                    snap.toObjects(FinanceCategory::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.sortOrder }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, col.path, uid)
                    throw error
                }
        )
    }

    fun observeTransactions(userId: String): Flow<List<FinanceTransaction>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "User mismatch" }
        val col = transactionsCol(uid)
        emitAll(
            col.snapshots()
                .map { snap ->
                    snap.toObjects(FinanceTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareByDescending<FinanceTransaction> { it.dateIso }
                                .thenByDescending { it.createdAt?.seconds ?: 0L }
                        )
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, col.path, uid)
                    throw error
                }
        )
    }

    fun observeRecurringBills(userId: String): Flow<List<RecurringBill>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "User mismatch" }
        val col = billsCol(uid)
        emitAll(
            col.snapshots()
                .map { snap ->
                    snap.toObjects(RecurringBill::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.dayOfMonth }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, col.path, uid)
                    throw error
                }
        )
    }

    private fun handleFlowError(error: Throwable, op: OperationType, path: String, uid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }

    suspend fun ensureSeededOnFirstLogin(
        userId: String,
        displayName: String,
        email: String
    ): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId) { "User mismatch" }
        val profileExists = userDoc(uid).get().await().exists()
        if (!profileExists) {
            seedDemoBundle(uid, displayName, email, updateExistingProfile = false)
        } else {
            val profile = userDoc(uid).get().await().toObject(UserProfile::class.java)
            if (profile != null && !profile.seededDemoData) {
                val accountsEmpty = accountsCol(uid).limit(1).get().await().isEmpty
                if (accountsEmpty) {
                    seedDemoBundle(uid, displayName, email, updateExistingProfile = true)
                }
            }
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "users/$userId")
        Result.failure(e)
    }

    suspend fun resetDemoData(userId: String, displayName: String, email: String): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId) { "User mismatch" }
        val subCollections = listOf(accountsCol(uid), categoriesCol(uid), transactionsCol(uid), billsCol(uid))
        for (col in subCollections) {
            val docs = col.get().await().documents
            docs.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }
        val profileExists = userDoc(uid).get().await().exists()
        seedDemoBundle(uid, displayName, email, updateExistingProfile = profileExists)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "users/$userId")
        Result.failure(e)
    }

    private suspend fun seedDemoBundle(
        uid: String,
        displayName: String,
        email: String,
        updateExistingProfile: Boolean
    ) {
        val safeName = displayName.ifBlank { "Alex Mercer" }.take(60)
        val safeEmail = email.take(120)
        val batch = db.batch()

        if (updateExistingProfile) {
            batch.update(
                userDoc(uid),
                mapOf(
                    "displayName" to safeName,
                    "currency" to "EUR",
                    "monthlyBudget" to 2400.0,
                    "seededDemoData" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        } else {
            batch.set(
                userDoc(uid),
                mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "currency" to "EUR",
                    "monthlyBudget" to 2400.0,
                    "seededDemoData" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        val demoAccounts = listOf(
            "acc_tf_gold" to mapOf(
                "userId" to uid,
                "name" to "TF Bank Mastercard Gold",
                "accountType" to AccountType.CREDIT_CARD.name,
                "balance" to -486.40,
                "creditLimit" to 3000.0,
                "dueDayOfMonth" to 25L,
                "colorHex" to "#D97706",
                "lastFourDigits" to "4821",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "acc_extrakarte" to mapOf(
                "userId" to uid,
                "name" to "Extrakarte",
                "accountType" to AccountType.CREDIT_CARD.name,
                "balance" to -192.80,
                "creditLimit" to 1500.0,
                "dueDayOfMonth" to 21L,
                "colorHex" to "#7C3AED",
                "lastFourDigits" to "9034",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "acc_checking" to mapOf(
                "userId" to uid,
                "name" to "Checking Account",
                "accountType" to AccountType.BANK.name,
                "balance" to 3420.50,
                "creditLimit" to 0.0,
                "dueDayOfMonth" to 0L,
                "colorHex" to "#3B82F6",
                "lastFourDigits" to "1190",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "acc_cash" to mapOf(
                "userId" to uid,
                "name" to "Cash",
                "accountType" to AccountType.CASH.name,
                "balance" to 185.00,
                "creditLimit" to 0.0,
                "dueDayOfMonth" to 0L,
                "colorHex" to "#10B981",
                "lastFourDigits" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        demoAccounts.forEach { (id, payload) ->
            batch.set(accountsCol(uid).document(id), payload)
        }

        val demoCategories = listOf(
            "cat_food" to Triple("Food & Dining", TxType.EXPENSE.name, Pair("restaurant", "#FF5A5F")),
            "cat_transport" to Triple("Transport", TxType.EXPENSE.name, Pair("directions_car", "#3B82F6")),
            "cat_shopping" to Triple("Shopping", TxType.EXPENSE.name, Pair("shopping_bag", "#8B5CF6")),
            "cat_bills" to Triple("Bills & Utilities", TxType.EXPENSE.name, Pair("receipt_long", "#F59E0B")),
            "cat_health" to Triple("Health & Fitness", TxType.EXPENSE.name, Pair("favorite", "#10B981")),
            "cat_entertain" to Triple("Entertainment", TxType.EXPENSE.name, Pair("movie", "#EC4899")),
            "cat_other" to Triple("Other", TxType.EXPENSE.name, Pair("category", "#6B7280")),
            "cat_salary" to Triple("Transfer / Income", TxType.INCOME.name, Pair("account_balance_wallet", "#3B82F6")),
            "cat_freelance" to Triple("Freelance", TxType.INCOME.name, Pair("work", "#10B981")),
            "cat_refund" to Triple("Cashback & Refund", TxType.INCOME.name, Pair("redeem", "#6366F1"))
        )
        demoCategories.forEachIndexed { idx, (id, data) ->
            batch.set(
                categoriesCol(uid).document(id),
                mapOf(
                    "userId" to uid,
                    "name" to data.first,
                    "txType" to data.second,
                    "iconKey" to data.third.first,
                    "colorHex" to data.third.second,
                    "sortOrder" to idx.toLong(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        val demoTransactions = listOf(
            mapOf(
                "userId" to uid, "txType" to TxType.EXPENSE.name, "amount" to 54.80, "currency" to "EUR",
                "dateIso" to "2026-11-19", "merchant" to "REWE Markt", "categoryName" to "Food & Dining",
                "subcategory" to "Supermarket", "accountId" to "acc_tf_gold", "accountName" to "TF Bank Mastercard Gold",
                "note" to "Fresh groceries & espresso", "needsReview" to false, "aiConfidence" to 0.97,
                "lineItems" to listOf(
                    mapOf("itemName" to "Organic Produce & Dairy", "amount" to 38.90, "suggestedCategory" to "Food & Dining"),
                    mapOf("itemName" to "USB-C Charging Cable", "amount" to 15.90, "suggestedCategory" to "Shopping")
                ),
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            ),
            mapOf(
                "userId" to uid, "txType" to TxType.EXPENSE.name, "amount" to 6.40, "currency" to "EUR",
                "dateIso" to "2026-11-19", "merchant" to "The Barn Coffee", "categoryName" to "Food & Dining",
                "subcategory" to "Coffee", "accountId" to "acc_cash", "accountName" to "Cash",
                "note" to "Morning flat white & croissant", "needsReview" to false,
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            ),
            mapOf(
                "userId" to uid, "txType" to TxType.EXPENSE.name, "amount" to 49.00, "currency" to "EUR",
                "dateIso" to "2026-11-17", "merchant" to "DB Deutschland-Ticket", "categoryName" to "Transport",
                "subcategory" to "Public Transit", "accountId" to "acc_checking", "accountName" to "Checking Account",
                "note" to "Monthly regional rail pass", "needsReview" to false,
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            ),
            mapOf(
                "userId" to uid, "txType" to TxType.EXPENSE.name, "amount" to 129.00, "currency" to "EUR",
                "dateIso" to "2026-11-16", "merchant" to "Uniqlo Berlin", "categoryName" to "Shopping",
                "subcategory" to "Clothing", "accountId" to "acc_extrakarte", "accountName" to "Extrakarte",
                "note" to "Merino wool sweater", "needsReview" to false,
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            ),
            mapOf(
                "userId" to uid, "txType" to TxType.INCOME.name, "amount" to 420.00, "currency" to "EUR",
                "dateIso" to "2026-11-15", "merchant" to "Studio Client Retainer", "categoryName" to "Freelance",
                "subcategory" to "Design Audit", "accountId" to "acc_checking", "accountName" to "Checking Account",
                "note" to "UI architecture milestone payout", "needsReview" to false,
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            ),
            mapOf(
                "userId" to uid, "txType" to TxType.EXPENSE.name, "amount" to 38.50, "currency" to "EUR",
                "dateIso" to "2026-11-12", "merchant" to "Apotheke am Markt", "categoryName" to "Health & Fitness",
                "subcategory" to "Pharmacy", "accountId" to "acc_tf_gold", "accountName" to "TF Bank Mastercard Gold",
                "note" to "Vitamins & cold relief", "needsReview" to false,
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            ),
            mapOf(
                "userId" to uid, "txType" to TxType.EXPENSE.name, "amount" to 18.99, "currency" to "EUR",
                "dateIso" to "2026-11-10", "merchant" to "UCI Luxe Cinema", "categoryName" to "Entertainment",
                "subcategory" to "Movies", "accountId" to "acc_extrakarte", "accountName" to "Extrakarte",
                "note" to "Evening IMAX screening ticket", "needsReview" to false,
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            ),
            mapOf(
                "userId" to uid, "txType" to TxType.EXPENSE.name, "amount" to 84.20, "currency" to "EUR",
                "dateIso" to "2026-11-06", "merchant" to "Vattenfall Energy", "categoryName" to "Bills & Utilities",
                "subcategory" to "Electricity", "accountId" to "acc_checking", "accountName" to "Checking Account",
                "note" to "November home electricity bill", "needsReview" to false,
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            ),
            mapOf(
                "userId" to uid, "txType" to TxType.EXPENSE.name, "amount" to 980.00, "currency" to "EUR",
                "dateIso" to "2026-11-02", "merchant" to "City Living GmbH", "categoryName" to "Bills & Utilities",
                "subcategory" to "Rent", "accountId" to "acc_checking", "accountName" to "Checking Account",
                "note" to "November apartment lease payment", "needsReview" to false,
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            ),
            mapOf(
                "userId" to uid, "txType" to TxType.INCOME.name, "amount" to 3250.00, "currency" to "EUR",
                "dateIso" to "2026-11-01", "merchant" to "Nordic Tech GmbH", "categoryName" to "Transfer / Income",
                "subcategory" to "Salary", "accountId" to "acc_checking", "accountName" to "Checking Account",
                "note" to "Monthly net payroll deposit", "needsReview" to false,
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            ),
            mapOf(
                "userId" to uid, "txType" to TxType.INCOME.name, "amount" to 3250.00, "currency" to "EUR",
                "dateIso" to "2026-10-01", "merchant" to "Nordic Tech GmbH", "categoryName" to "Transfer / Income",
                "subcategory" to "Salary", "accountId" to "acc_checking", "accountName" to "Checking Account",
                "note" to "October payroll deposit", "needsReview" to false,
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            ),
            mapOf(
                "userId" to uid, "txType" to TxType.EXPENSE.name, "amount" to 1142.30, "currency" to "EUR",
                "dateIso" to "2026-10-05", "merchant" to "October Living & Bills", "categoryName" to "Bills & Utilities",
                "subcategory" to "Rent & Utilities", "accountId" to "acc_checking", "accountName" to "Checking Account",
                "note" to "October rent and fiber", "needsReview" to false,
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        demoTransactions.forEachIndexed { idx, payload ->
            batch.set(transactionsCol(uid).document("tx_demo_$idx"), payload)
        }

        val demoBills = listOf(
            "bill_rent" to mapOf(
                "userId" to uid, "title" to "Apartment Rent", "amount" to 980.0,
                "categoryName" to "Bills & Utilities", "accountId" to "acc_checking",
                "accountName" to "Checking Account", "dayOfMonth" to 1L, "autoPay" to true,
                "lastPaidMonth" to "2026-11",
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            ),
            "bill_extrakarte" to mapOf(
                "userId" to uid, "title" to "Extrakarte Statement Auto-Pay", "amount" to 192.80,
                "categoryName" to "Bills & Utilities", "accountId" to "acc_checking",
                "accountName" to "Checking Account", "dayOfMonth" to 21L, "autoPay" to true,
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            ),
            "bill_tf_gold" to mapOf(
                "userId" to uid, "title" to "TF Bank Mastercard Gold Due", "amount" to 486.40,
                "categoryName" to "Bills & Utilities", "accountId" to "acc_checking",
                "accountName" to "Checking Account", "dayOfMonth" to 25L, "autoPay" to false,
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        demoBills.forEach { (id, payload) ->
            batch.set(billsCol(uid).document(id), payload)
        }

        batch.commit().await()
    }

    suspend fun updateProfile(
        userId: String,
        displayName: String,
        currency: String,
        monthlyBudget: Double
    ): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId) { "User mismatch" }
        val cleanName = displayName.trim().ifBlank { "Penniless User" }.take(60)
        val cleanCurrency = currency.trim().uppercase().ifBlank { "EUR" }.take(8)
        val cleanBudget = monthlyBudget.coerceIn(0.0, 100_000_000.0)
        userDoc(uid).update(
            mapOf(
                "displayName" to cleanName,
                "currency" to cleanCurrency,
                "monthlyBudget" to cleanBudget,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "users/$userId")
        Result.failure(e)
    }

    suspend fun saveTransactionsAndAdjustAccount(
        userId: String,
        transactions: List<FinanceTransaction>,
        account: FinanceAccount,
        existingTransactionToReplace: FinanceTransaction? = null,
        previousAccount: FinanceAccount? = null
    ): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId) { "User mismatch" }
        require(transactions.isNotEmpty()) { "No transactions to save" }

        val batch = db.batch()
        var runningNewBalance = account.balance

        if (existingTransactionToReplace != null && existingTransactionToReplace.id.isNotBlank()) {
            val prevDelta = when (existingTransactionToReplace.resolvedTxType) {
                TxType.EXPENSE -> existingTransactionToReplace.amount
                TxType.INCOME -> -existingTransactionToReplace.amount
                TxType.TRANSFER -> 0.0
            }
            if (previousAccount != null && previousAccount.id != account.id) {
                batch.update(
                    accountsCol(uid).document(previousAccount.id),
                    mapOf(
                        "balance" to roundCurrency(previousAccount.balance + prevDelta),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                runningNewBalance += prevDelta
            }

            val firstTx = transactions.first()
            val txRef = transactionsCol(uid).document(existingTransactionToReplace.id)
            batch.update(txRef, buildTransactionUpdateMap(firstTx))
            runningNewBalance += balanceDeltaFor(firstTx)
        } else {
            for (tx in transactions) {
                val txRef = transactionsCol(uid).document()
                batch.set(txRef, buildTransactionCreateMap(uid, tx))
                runningNewBalance += balanceDeltaFor(tx)
            }
        }

        batch.update(
            accountsCol(uid).document(account.id),
            mapOf(
                "balance" to roundCurrency(runningNewBalance),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        batch.commit().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "users/$userId/transactions")
        Result.failure(e)
    }

    suspend fun deleteTransactionAndRevertAccount(
        userId: String,
        transaction: FinanceTransaction,
        account: FinanceAccount?
    ): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId) { "User mismatch" }
        require(transaction.id.isNotBlank()) { "Transaction ID required" }

        val batch = db.batch()
        batch.delete(transactionsCol(uid).document(transaction.id))
        if (account != null) {
            val revertDelta = -balanceDeltaFor(transaction)
            batch.update(
                accountsCol(uid).document(account.id),
                mapOf(
                    "balance" to roundCurrency(account.balance + revertDelta),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "users/$userId/transactions/${transaction.id}")
        Result.failure(e)
    }

    suspend fun saveAccount(userId: String, account: FinanceAccount): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId) { "User mismatch" }
        val cleanName = account.name.trim().ifBlank { "Account" }.take(60)
        val cleanFour = account.lastFourDigits.trim().take(8)
        val cleanHex = account.colorHex.ifBlank { "#3B82F6" }.take(12)
        val limit = account.creditLimit.coerceIn(0.0, 100_000_000.0)
        val balance = account.balance.coerceIn(-100_000_000.0, 100_000_000.0)
        val dueDay = account.dueDayOfMonth.coerceIn(0, 31).toLong()

        if (account.id.isBlank()) {
            val payload = mapOf(
                "userId" to uid,
                "name" to cleanName,
                "accountType" to account.resolvedType.name,
                "balance" to balance,
                "creditLimit" to limit,
                "dueDayOfMonth" to dueDay,
                "colorHex" to cleanHex,
                "lastFourDigits" to cleanFour,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            accountsCol(uid).document().set(payload).await()
        } else {
            accountsCol(uid).document(account.id).update(
                mapOf(
                    "name" to cleanName,
                    "accountType" to account.resolvedType.name,
                    "balance" to balance,
                    "creditLimit" to limit,
                    "dueDayOfMonth" to dueDay,
                    "colorHex" to cleanHex,
                    "lastFourDigits" to cleanFour,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "users/$userId/accounts")
        Result.failure(e)
    }

    suspend fun deleteAccount(userId: String, accountId: String): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId) { "User mismatch" }
        require(accountId.isNotBlank()) { "Account ID required" }
        accountsCol(uid).document(accountId).delete().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "users/$userId/accounts/$accountId")
        Result.failure(e)
    }

    suspend fun transferBetweenAccounts(
        userId: String,
        fromAccount: FinanceAccount,
        toAccount: FinanceAccount,
        amount: Double,
        currency: String,
        dateIso: String,
        note: String
    ): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId) { "User mismatch" }
        require(amount > 0.0) { "Transfer amount must be positive" }

        val batch = db.batch()
        batch.update(
            accountsCol(uid).document(fromAccount.id),
            mapOf(
                "balance" to roundCurrency(fromAccount.balance - amount),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            accountsCol(uid).document(toAccount.id),
            mapOf(
                "balance" to roundCurrency(toAccount.balance + amount),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val tx = FinanceTransaction(
            userId = uid,
            txType = TxType.TRANSFER.name,
            amount = amount,
            currency = currency,
            dateIso = dateIso,
            merchant = "${fromAccount.name} → ${toAccount.name}",
            categoryName = "Transfer / Income",
            subcategory = "Card Payment / Transfer",
            accountId = fromAccount.id,
            accountName = fromAccount.name,
            note = note.ifBlank { "Transfer to ${toAccount.name}" }.take(200)
        )
        batch.set(transactionsCol(uid).document(), buildTransactionCreateMap(uid, tx))
        batch.commit().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "users/$userId/accounts")
        Result.failure(e)
    }

    suspend fun saveCategory(userId: String, category: FinanceCategory): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId) { "User mismatch" }
        val cleanName = category.name.trim().ifBlank { "Category" }.take(40)
        val cleanType = if (category.txType == TxType.INCOME.name) TxType.INCOME.name else TxType.EXPENSE.name
        val cleanIcon = category.iconKey.ifBlank { "category" }.take(40)
        val cleanColor = category.colorHex.ifBlank { "#FF5A5F" }.take(12)
        val sortOrder = category.sortOrder.coerceIn(0, 1000).toLong()

        if (category.id.isBlank()) {
            categoriesCol(uid).document().set(
                mapOf(
                    "userId" to uid,
                    "name" to cleanName,
                    "txType" to cleanType,
                    "iconKey" to cleanIcon,
                    "colorHex" to cleanColor,
                    "sortOrder" to sortOrder,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            categoriesCol(uid).document(category.id).update(
                mapOf(
                    "name" to cleanName,
                    "txType" to cleanType,
                    "iconKey" to cleanIcon,
                    "colorHex" to cleanColor,
                    "sortOrder" to sortOrder,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "users/$userId/categories")
        Result.failure(e)
    }

    suspend fun deleteCategory(userId: String, categoryId: String): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId) { "User mismatch" }
        require(categoryId.isNotBlank()) { "Category ID required" }
        categoriesCol(uid).document(categoryId).delete().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "users/$userId/categories/$categoryId")
        Result.failure(e)
    }

    suspend fun saveRecurringBill(userId: String, bill: RecurringBill): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId) { "User mismatch" }
        val cleanTitle = bill.title.trim().ifBlank { "Bill" }.take(60)
        val cleanAmt = bill.amount.coerceIn(0.01, 100_000_000.0)
        val cleanCat = bill.categoryName.ifBlank { "Bills & Utilities" }.take(50)
        val cleanDay = bill.dayOfMonth.coerceIn(1, 31).toLong()

        if (bill.id.isBlank()) {
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "amount" to cleanAmt,
                "categoryName" to cleanCat,
                "accountId" to bill.accountId.take(128),
                "accountName" to bill.accountName.ifBlank { "Account" }.take(60),
                "dayOfMonth" to cleanDay,
                "autoPay" to bill.autoPay,
                "lastPaidMonth" to bill.lastPaidMonth?.take(10),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            billsCol(uid).document().set(payload).await()
        } else {
            val updates = mapOf(
                "title" to cleanTitle,
                "amount" to cleanAmt,
                "categoryName" to cleanCat,
                "accountId" to bill.accountId.take(128),
                "accountName" to bill.accountName.ifBlank { "Account" }.take(60),
                "dayOfMonth" to cleanDay,
                "autoPay" to bill.autoPay,
                "lastPaidMonth" to bill.lastPaidMonth?.take(10),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            billsCol(uid).document(bill.id).update(updates).await()
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "users/$userId/recurringBills")
        Result.failure(e)
    }

    suspend fun deleteRecurringBill(userId: String, billId: String): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId) { "User mismatch" }
        require(billId.isNotBlank()) { "Bill ID required" }
        billsCol(uid).document(billId).delete().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "users/$userId/recurringBills/$billId")
        Result.failure(e)
    }

    suspend fun payRecurringBill(
        userId: String,
        bill: RecurringBill,
        account: FinanceAccount,
        currency: String,
        dateIso: String,
        yearMonth: String
    ): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId) { "User mismatch" }
        val batch = db.batch()
        val tx = FinanceTransaction(
            userId = uid,
            txType = TxType.EXPENSE.name,
            amount = bill.amount,
            currency = currency,
            dateIso = dateIso,
            merchant = bill.title,
            categoryName = bill.categoryName,
            subcategory = "Recurring Bill",
            accountId = account.id,
            accountName = account.name,
            note = "Paid ${bill.title}"
        )
        batch.set(transactionsCol(uid).document(), buildTransactionCreateMap(uid, tx))
        batch.update(
            accountsCol(uid).document(account.id),
            mapOf(
                "balance" to roundCurrency(account.balance - bill.amount),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            billsCol(uid).document(bill.id),
            mapOf(
                "lastPaidMonth" to yearMonth.take(10),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "users/$userId/recurringBills/${bill.id}")
        Result.failure(e)
    }

    private fun balanceDeltaFor(tx: FinanceTransaction): Double = when (tx.resolvedTxType) {
        TxType.EXPENSE -> -tx.amount
        TxType.INCOME -> tx.amount
        TxType.TRANSFER -> 0.0
    }

    private fun roundCurrency(value: Double): Double = (value * 100.0).roundToLong() / 100.0

    private fun serializeLineItems(items: List<ReceiptLineItem>?): List<Map<String, Any>>? {
        if (items.isNullOrEmpty()) return null
        return items.take(50).map { item ->
            mapOf(
                "itemName" to item.itemName.take(60),
                "amount" to item.amount.coerceAtLeast(0.0),
                "suggestedCategory" to item.suggestedCategory.take(50)
            )
        }
    }

    private fun buildTransactionCreateMap(uid: String, tx: FinanceTransaction): Map<String, Any> {
        val validDate = if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(tx.dateIso)) tx.dateIso else "2026-11-19"
        return mapOf(
            "userId" to uid,
            "txType" to tx.resolvedTxType.name,
            "amount" to tx.amount.coerceIn(0.01, 100_000_000.0),
            "currency" to tx.currency.ifBlank { "EUR" }.take(8),
            "dateIso" to validDate,
            "merchant" to tx.merchant.trim().take(80),
            "categoryName" to tx.categoryName.ifBlank { "Other" }.take(50),
            "subcategory" to tx.subcategory.trim().take(50),
            "accountId" to tx.accountId.ifBlank { "unknown" }.take(128),
            "accountName" to tx.accountName.ifBlank { "Account" }.take(60),
            "note" to tx.note.trim().take(200),
            "needsReview" to tx.needsReview,
            "receiptThumbnail" to tx.receiptThumbnail?.take(150_000),
            "aiConfidence" to tx.aiConfidence?.coerceIn(0.0, 1.0),
            "lineItems" to serializeLineItems(tx.lineItems),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }

    private fun buildTransactionUpdateMap(tx: FinanceTransaction): Map<String, Any> {
        val validDate = if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(tx.dateIso)) tx.dateIso else "2026-11-19"
        return mapOf(
            "txType" to tx.resolvedTxType.name,
            "amount" to tx.amount.coerceIn(0.01, 100_000_000.0),
            "currency" to tx.currency.ifBlank { "EUR" }.take(8),
            "dateIso" to validDate,
            "merchant" to tx.merchant.trim().take(80),
            "categoryName" to tx.categoryName.ifBlank { "Other" }.take(50),
            "subcategory" to tx.subcategory.trim().take(50),
            "accountId" to tx.accountId.ifBlank { "unknown" }.take(128),
            "accountName" to tx.accountName.ifBlank { "Account" }.take(60),
            "note" to tx.note.trim().take(200),
            "needsReview" to tx.needsReview,
            "receiptThumbnail" to tx.receiptThumbnail?.take(150_000),
            "aiConfidence" to tx.aiConfidence?.coerceIn(0.0, 1.0),
            "lineItems" to serializeLineItems(tx.lineItems),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }
}
