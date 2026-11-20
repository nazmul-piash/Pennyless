package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.firebase.isAppCheckErrorMessage
import com.example.model.AccountType
import com.example.model.ExtractedReceiptResult
import com.example.model.FinanceAccount
import com.example.model.FinanceCategory
import com.example.model.FinanceTransaction
import com.example.model.ReceiptLineItem
import com.example.model.TxType
import com.example.model.formatMoney
import com.example.ui.components.AppCheckDebugHelpCard
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
import com.example.ui.theme.SurfaceSubtle
import com.example.viewmodel.ScannerUiState
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ReceiptScannerModal(
    scannerState: ScannerUiState,
    accounts: List<FinanceAccount>,
    categories: List<FinanceCategory>,
    defaultCurrency: String,
    onStartScan: (Bitmap) -> Unit,
    onVoiceAudioCaptured: (ByteArray) -> Unit,
    onVoiceTextSubmitted: (String) -> Unit,
    onSubmitVoiceClarification: (ExtractedReceiptResult, String?, ByteArray?) -> Unit,
    onSkipClarificationToReview: (ExtractedReceiptResult) -> Unit,
    onOpenVoiceRetry: () -> Unit,
    onSaveTransactions: (List<FinanceTransaction>, FinanceAccount) -> Unit,
    onRetryReset: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    var cameraPermissionDenied by remember { mutableStateOf(false) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            decodeBitmapFromUri(context, uri)?.let { onStartScan(it) }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCaptureUri
        if (success && uri != null) {
            decodeBitmapFromUri(context, uri)?.let { onStartScan(it) }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraPermissionDenied = false
            val uri = createTempReceiptUri(context)
            pendingCaptureUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionDenied = true
        }
    }

    fun launchCamera() {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            val uri = createTempReceiptUri(context)
            pendingCaptureUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SurfaceGroupedBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val isVoiceFlow = scannerState is ScannerUiState.VoiceCapture ||
                scannerState is ScannerUiState.ClarifyingVoice ||
                (scannerState is ScannerUiState.Scanning && scannerState.isVoice) ||
                (scannerState is ScannerUiState.Error && scannerState.isVoice) ||
                (scannerState is ScannerUiState.Review && scannerState.previewBitmap == null)

            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("scanner_close_button")) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = MidnightInk)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isVoiceFlow) Icons.Outlined.Mic else Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = CoralPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isVoiceFlow) "AI Voice Entry" else "AI Receipt Scanner",
                        style = MaterialTheme.typography.titleMedium,
                        color = MidnightInk
                    )
                }
                Spacer(modifier = Modifier.size(48.dp))
            }
            HorizontalDivider(color = DividerHairline)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
            ) {
                when (scannerState) {
                    ScannerUiState.Idle -> {
                        ScannerSourcePicker(
                            cameraPermissionDenied = cameraPermissionDenied,
                            onCaptureClick = { launchCamera() },
                            onPickImageClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                    }

                    is ScannerUiState.VoiceCapture -> {
                        VoiceCaptureView(
                            autoStartRecording = scannerState.autoStartRecording,
                            defaultCurrency = defaultCurrency,
                            onAudioCaptured = onVoiceAudioCaptured,
                            onSubmitTypedUtterance = onVoiceTextSubmitted
                        )
                    }

                    is ScannerUiState.Scanning -> {
                        val bmp = scannerState.previewBitmap
                        if (bmp != null) {
                            ScanningProgressView(bitmap = bmp, statusLabel = scannerState.statusLabel)
                        } else {
                            VoiceProcessingView(statusLabel = scannerState.statusLabel)
                        }
                    }

                    is ScannerUiState.ClarifyingVoice -> {
                        VoiceClarificationView(
                            state = scannerState,
                            onSubmitReply = { replyText, audioBytes ->
                                onSubmitVoiceClarification(scannerState.partial, replyText, audioBytes)
                            },
                            onSkipToReview = onSkipClarificationToReview,
                            onReRecord = onOpenVoiceRetry
                        )
                    }

                    is ScannerUiState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            scannerState.previewBitmap?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Receipt preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(1.dp, DividerHairline, RoundedCornerShape(16.dp))
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                            Surface(
                                color = CoralSoftBg,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (scannerState.isVoice) "Could not process voice entry" else "Could not extract receipt",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = CoralPrimary
                                    )
                                    Text(
                                        text = scannerState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MidnightInk
                                    )
                                }
                            }
                            if (isAppCheckErrorMessage(scannerState.message)) {
                                Spacer(modifier = Modifier.height(12.dp))
                                AppCheckDebugHelpCard()
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    if (scannerState.isVoice) onOpenVoiceRetry() else onRetryReset()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MidnightSlate),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) {
                                Text(if (scannerState.isVoice) "Try Recording Again" else "Try Another Image")
                            }
                        }
                    }

                    is ScannerUiState.Review -> {
                        ReceiptVerificationForm(
                            state = scannerState,
                            accounts = accounts,
                            categories = categories,
                            defaultCurrency = defaultCurrency,
                            onSave = onSaveTransactions,
                            onRescan = {
                                if (scannerState.previewBitmap == null) onOpenVoiceRetry() else onRetryReset()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerSourcePicker(
    cameraPermissionDenied: Boolean,
    onCaptureClick: () -> Unit,
    onPickImageClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(CoralSoftBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.CameraAlt,
                contentDescription = null,
                tint = CoralPrimary,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Snap or Upload a Receipt",
            style = MaterialTheme.typography.headlineSmall,
            color = MidnightInk
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Gemini Vision extracts merchant, date, currency, line items, and automatically suggests categories or splits.",
            style = MaterialTheme.typography.bodyMedium,
            color = SlateSecondary
        )
        Spacer(modifier = Modifier.height(28.dp))

        if (cameraPermissionDenied) {
            Surface(
                color = AmberSoftBg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
            ) {
                Text(
                    text = "Camera permission was denied. You can still upload any receipt photo from your Photo Library below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MidnightInk,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Button(
            onClick = onCaptureClick,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("scanner_take_photo_button")
        ) {
            Icon(Icons.Outlined.CameraAlt, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Take Photo with Camera", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onPickImageClick,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("scanner_pick_image_button")
        ) {
            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = MidnightInk)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Upload Receipt Image (JPG/PNG)", style = MaterialTheme.typography.labelLarge, color = MidnightInk)
        }
    }
}

@Composable
private fun ScanningProgressView(bitmap: Bitmap, statusLabel: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan_line")
    val lineFraction by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.5.dp, CoralPrimary.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Scanning receipt",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val y = size.height * lineFraction
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            CoralPrimary.copy(alpha = 0.28f),
                            Color.Transparent
                        ),
                        startY = (y - 40.dp.toPx()).coerceAtLeast(0f),
                        endY = (y + 40.dp.toPx()).coerceAtMost(size.height)
                    )
                )
                drawLine(
                    color = CoralPrimary,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }
        Spacer(modifier = Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = CoralPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MidnightInk
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Powered by Gemini Vision",
            style = MaterialTheme.typography.bodySmall,
            color = SlateSecondary
        )
    }
}

@Composable
private fun ReceiptVerificationForm(
    state: ScannerUiState.Review,
    accounts: List<FinanceAccount>,
    categories: List<FinanceCategory>,
    defaultCurrency: String,
    onSave: (List<FinanceTransaction>, FinanceAccount) -> Unit,
    onRescan: () -> Unit
) {
    val ext = state.extracted
    var merchant by remember(ext) { mutableStateOf(ext.merchant) }
    var amountStr by remember(ext) { mutableStateOf(String.format("%.2f", ext.totalAmount)) }
    var currencyCode by remember(ext) { mutableStateOf(ext.currency.ifBlank { defaultCurrency }) }
    var dateIso by remember(ext) { mutableStateOf(ext.date) }
    var txTypeSelection by remember(ext) {
        mutableStateOf(
            if (ext.transactionType == "Income" || ext.primaryCategory == "Transfer / Income") {
                TxType.INCOME
            } else {
                TxType.EXPENSE
            }
        )
    }
    var categoryName by remember(ext) { mutableStateOf(ext.primaryCategory) }
    var subcategory by remember(ext) { mutableStateOf(ext.subcategory) }
    var note by remember(ext) { mutableStateOf(ext.note) }

    val initialAccount = remember(accounts, ext.paymentMethodDetected) {
        when (ext.paymentMethodDetected) {
            "Credit Card" -> accounts.firstOrNull { it.resolvedType == AccountType.CREDIT_CARD }
            "Cash" -> accounts.firstOrNull { it.resolvedType == AccountType.CASH }
            "Debit" -> accounts.firstOrNull { it.resolvedType == AccountType.BANK }
            else -> null
        } ?: accounts.firstOrNull()
    }
    var selectedAccount by remember { mutableStateOf(initialAccount) }

    var editableLineItems by remember(ext) { mutableStateOf(ext.lineItems) }
    var editingItemIndex by remember { mutableStateOf<Int?>(null) }

    val distinctCategories = remember(editableLineItems) {
        editableLineItems.map { it.suggestedCategory }.distinct()
    }
    val canSplit = distinctCategories.size > 1
    var splitByCategory by remember { mutableStateOf(false) }
    val effectiveSplit = splitByCategory && canSplit

    val allCategoryNames = remember(categories) {
        val base = categories.map { it.name }
        (base + listOf(
            "Food & Dining", "Transport", "Shopping", "Bills & Utilities",
            "Health & Fitness", "Entertainment", "Transfer / Income", "Other"
        )).distinct()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Preview + Confidence Header (Receipt Bitmap or Voice Transcript)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DividerHairline, RoundedCornerShape(16.dp))
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val bmp = state.previewBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Receipt thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(74.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, DividerHairline, RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CoralSoftBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.GraphicEq,
                            contentDescription = "Voice transaction",
                            tint = CoralPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val confPct = (ext.confidenceScore * 100).roundToInt()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (ext.needsManualReview) Icons.Outlined.WarningAmber else Icons.Outlined.CheckCircleOutline,
                            contentDescription = null,
                            tint = if (ext.needsManualReview) AmberWarning else EmeraldHealthy,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (ext.needsManualReview) "Verify Entry ($confPct%)" else "Verified · $confPct% Match",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (ext.needsManualReview) AmberWarning else EmeraldHealthy
                        )
                    }
                    if (!ext.spokenTranscript.isNullOrBlank()) {
                        Text(
                            text = "\"${ext.spokenTranscript}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MidnightInk
                        )
                    }
                    if (!ext.reviewReason.isNullOrBlank()) {
                        Text(
                            text = ext.reviewReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateSecondary
                        )
                    }
                    Text(
                        text = "Detected Payment: ${ext.paymentMethodDetected}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateSecondary
                    )
                }
            }
        }

        // Split Detection Card
        if (canSplit) {
            val groupedPreview = remember(editableLineItems) {
                editableLineItems.groupBy { it.suggestedCategory }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CoralSoftBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CoralPrimary.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Outlined.CallSplit, contentDescription = null, tint = CoralPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Multi-Category Receipt Detected (${distinctCategories.size} categories)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MidnightInk
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .padding(4.dp)
                    ) {
                        listOf(false to "Save as Single Entry", true to "Split by Category").forEach { (isSplit, label) ->
                            val selected = splitByCategory == isSplit
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) CoralPrimary else Color.Transparent)
                                    .clickable { splitByCategory = isSplit }
                                    .padding(vertical = 8.dp)
                                    .testTag(if (isSplit) "split_mode_button" else "single_mode_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) Color.White else SlateSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    if (effectiveSplit) {
                        HorizontalDivider(color = CoralPrimary.copy(alpha = 0.15f))
                        groupedPreview.forEach { (cat, items) ->
                            val catSum = items.sumOf { it.amount }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$cat (${items.size} item${if (items.size == 1) "" else "s"})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MidnightInk
                                )
                                Text(
                                    text = formatMoney(catSum, ext.currency.ifBlank { defaultCurrency }),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CoralPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Editable Fields Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DividerHairline, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceSubtle)
                        .padding(4.dp)
                ) {
                    listOf(TxType.EXPENSE to "Expense", TxType.INCOME to "Income / Sale").forEach { (type, label) ->
                        val selected = txTypeSelection == type
                        val activeBg = if (type == TxType.INCOME) BlueIncome else CoralPrimary
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) activeBg else Color.Transparent)
                                .clickable { txTypeSelection = type }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.White else SlateSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant / Item") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("scan_merchant_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Total ($currencyCode)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("scan_amount_input")
                    )
                    OutlinedTextField(
                        value = dateIso,
                        onValueChange = { dateIso = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Account / Card", style = MaterialTheme.typography.labelMedium, color = SlateSecondary)
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

                if (!effectiveSplit) {
                    Text("Category", style = MaterialTheme.typography.labelMedium, color = SlateSecondary)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allCategoryNames.forEach { cat ->
                            FilterChip(
                                selected = categoryName == cat,
                                onClick = { categoryName = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = subcategory,
                        onValueChange = { subcategory = it },
                        label = { Text("Subcategory") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Ledger Note") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Extracted Line Items Card (Interactive)
        if (editableLineItems.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DividerHairline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Extracted Line Items (${editableLineItems.size})", style = MaterialTheme.typography.titleSmall, color = MidnightInk)
                        Text("Tap item to edit", style = MaterialTheme.typography.labelSmall, color = SlateSecondary)
                    }
                    HorizontalDivider(color = DividerHairline)
                    editableLineItems.forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { editingItemIndex = idx }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.itemName, style = MaterialTheme.typography.bodySmall, color = MidnightInk)
                                Text(
                                    text = item.suggestedCategory,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CoralPrimary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatMoney(item.amount, ext.currency.ifBlank { defaultCurrency }),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MidnightInk
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Edit line item",
                                    tint = SlateSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onRescan,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(0.35f).height(52.dp)
            ) {
                Text("Retake", color = MidnightInk)
            }
            Button(
                onClick = {
                    val acc = selectedAccount ?: return@Button
                    val total = amountStr.toDoubleOrNull()?.coerceAtLeast(0.01) ?: ext.totalAmount.coerceAtLeast(0.01)
                    val finalCurrency = currencyCode.ifBlank { defaultCurrency }
                    val thumbOrNull = state.thumbnailBase64.takeIf { it.isNotBlank() }

                    val entries = if (effectiveSplit) {
                        val grouped = editableLineItems.groupBy { it.suggestedCategory }
                        grouped.map { (cat, items) ->
                            val sum = items.sumOf { it.amount }.coerceAtLeast(0.01)
                            FinanceTransaction(
                                txType = if (cat == "Transfer / Income") TxType.INCOME.name else txTypeSelection.name,
                                amount = sum,
                                currency = finalCurrency,
                                dateIso = dateIso,
                                merchant = merchant,
                                categoryName = cat,
                                subcategory = "Split Receipt",
                                accountId = acc.id,
                                accountName = acc.name,
                                note = items.joinToString(", ") { it.itemName }.take(180),
                                needsReview = ext.needsManualReview,
                                receiptThumbnail = thumbOrNull,
                                aiConfidence = ext.confidenceScore,
                                lineItems = items
                            )
                        }
                    } else {
                        listOf(
                            FinanceTransaction(
                                txType = txTypeSelection.name,
                                amount = total,
                                currency = finalCurrency,
                                dateIso = dateIso,
                                merchant = merchant,
                                categoryName = categoryName,
                                subcategory = subcategory,
                                accountId = acc.id,
                                accountName = acc.name,
                                note = note,
                                needsReview = ext.needsManualReview,
                                receiptThumbnail = thumbOrNull,
                                aiConfidence = ext.confidenceScore,
                                lineItems = editableLineItems.takeIf { it.isNotEmpty() }
                            )
                        )
                    }
                    onSave(entries, acc)
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                modifier = Modifier
                    .weight(0.65f)
                    .height(52.dp)
                    .testTag("save_scanned_receipt_button")
            ) {
                Text(
                    text = if (effectiveSplit) "Save ${distinctCategories.size} Split Entries" else "Save to Ledger",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }

    val editIdx = editingItemIndex
    if (editIdx != null && editIdx in editableLineItems.indices) {
        val currentItem = editableLineItems[editIdx]
        var editItemName by remember(editIdx) { mutableStateOf(currentItem.itemName) }
        var editItemAmount by remember(editIdx) { mutableStateOf(String.format("%.2f", currentItem.amount)) }
        var editItemCat by remember(editIdx) { mutableStateOf(currentItem.suggestedCategory) }

        AlertDialog(
            onDismissRequest = { editingItemIndex = null },
            title = { Text("Edit Line Item", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editItemName,
                        onValueChange = { editItemName = it },
                        label = { Text("Item Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editItemAmount,
                        onValueChange = { editItemAmount = it },
                        label = { Text("Amount (${ext.currency.ifBlank { defaultCurrency }})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Item Category", style = MaterialTheme.typography.labelMedium, color = SlateSecondary)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allCategoryNames.forEach { cat ->
                            FilterChip(
                                selected = editItemCat == cat,
                                onClick = { editItemCat = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val updatedAmt = editItemAmount.toDoubleOrNull()?.coerceAtLeast(0.01) ?: currentItem.amount
                    val updatedItem = ReceiptLineItem(
                        itemName = editItemName.trim().ifBlank { currentItem.itemName },
                        amount = updatedAmt,
                        suggestedCategory = editItemCat
                    )
                    editableLineItems = editableLineItems.toMutableList().also { list ->
                        list[editIdx] = updatedItem
                    }
                    editingItemIndex = null
                }) {
                    Text("Update", color = CoralPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItemIndex = null }) { Text("Cancel") }
            }
        )
    }
}

private fun createTempReceiptUri(context: Context): Uri {
    val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
    val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    } catch (_: Exception) {
        null
    }
}
