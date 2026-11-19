package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.audio.VoiceRecorderHelper
import com.example.model.ExtractedReceiptResult
import com.example.ui.theme.AmberSoftBg
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlueIncome
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.CoralSoftBg
import com.example.ui.theme.DividerHairline
import com.example.ui.theme.MidnightInk
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SurfaceSubtle
import com.example.viewmodel.ScannerUiState
import kotlinx.coroutines.delay

@Composable
fun VoiceCaptureView(
    autoStartRecording: Boolean,
    defaultCurrency: String,
    onAudioCaptured: (ByteArray) -> Unit,
    onSubmitTypedUtterance: (String) -> Unit
) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorderHelper() }

    var isRecording by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var permissionDenied by remember { mutableStateOf(false) }
    var recorderError by remember { mutableStateOf<String?>(null) }
    var typedPhrase by remember { mutableStateOf("") }

    val bars = remember {
        mutableStateListOf<Float>().apply {
            repeat(20) { add(0.15f) }
        }
    }

    fun beginRecording() {
        recorderError = null
        recorder.startRecording(context).fold(
            onSuccess = {
                isRecording = true
                elapsedSeconds = 0
            },
            onFailure = { err ->
                isRecording = false
                recorderError = err.localizedMessage ?: "Unable to start microphone recording."
            }
        )
    }

    fun stopAndProcess() {
        isRecording = false
        recorder.stopAndReadBytes().fold(
            onSuccess = { bytes -> onAudioCaptured(bytes) },
            onFailure = { err ->
                recorderError = err.localizedMessage ?: "Could not capture audio."
            }
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDenied = false
            beginRecording()
        } else {
            permissionDenied = true
            isRecording = false
        }
    }

    LaunchedEffect(autoStartRecording) {
        if (autoStartRecording) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                beginRecording()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    LaunchedEffect(isRecording) {
        var tick = 0
        while (isRecording) {
            delay(80L)
            tick++
            val amp = recorder.sampleAmplitudeNormalized()
            bars.removeAt(0)
            bars.add(amp)
            if (tick % 12 == 0) {
                elapsedSeconds++
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder.cancelAndRelease()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // Language badge
        Surface(
            color = CoralSoftBg,
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = CoralPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Any Language · Dialect · Mixed Speech",
                    style = MaterialTheme.typography.labelMedium,
                    color = CoralPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Text(
            text = if (isRecording) "Listening..." else "Speak Your Transaction",
            style = MaterialTheme.typography.headlineSmall,
            color = MidnightInk
        )
        Text(
            text = "Try: \"I bought 1 kilo kacha morich for 80 taka\" or \"Sold 2 items for 500 $defaultCurrency\".",
            style = MaterialTheme.typography.bodySmall,
            color = SlateSecondary
        )

        // Waveform + Timer Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = if (isRecording) 1.5.dp else 1.dp,
                    color = if (isRecording) CoralPrimary.copy(alpha = 0.55f) else DividerHairline,
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Live waveform bars
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    bars.forEach { level ->
                        val heightDp = (10f + level * 48f).dp
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(heightDp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (isRecording) CoralPrimary else SlateSecondary.copy(alpha = 0.25f)
                                )
                        )
                    }
                }

                // Timer
                val mm = elapsedSeconds / 60
                val ss = elapsedSeconds % 60
                Text(
                    text = String.format("%02d:%02d", mm, ss),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isRecording) CoralPrimary else MidnightInk,
                    modifier = Modifier.testTag("voice_timer_text")
                )

                if (isRecording) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                recorder.cancelAndRelease()
                                isRecording = false
                                elapsedSeconds = 0
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(0.35f)
                                .height(50.dp)
                        ) {
                            Text("Cancel", color = MidnightInk)
                        }
                        Button(
                            onClick = { stopAndProcess() },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                            modifier = Modifier
                                .weight(0.65f)
                                .height(50.dp)
                                .testTag("voice_stop_process_button")
                        ) {
                            Icon(Icons.Outlined.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop & Process", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                beginRecording()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("voice_start_record_button")
                    ) {
                        Icon(Icons.Outlined.Mic, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tap to Record Audio", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (permissionDenied) {
            Surface(
                color = AmberSoftBg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.MicOff, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.mic_permission_rationale),
                        style = MaterialTheme.typography.bodySmall,
                        color = MidnightInk
                    )
                }
            }
        }

        recorderError?.let { err ->
            Surface(
                color = CoralSoftBg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = CoralPrimary,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Quick spoken-phrase fallback input
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DividerHairline, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Or type a spoken phrase (any language)",
                    style = MaterialTheme.typography.labelMedium,
                    color = SlateSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = typedPhrase,
                        onValueChange = { typedPhrase = it },
                        placeholder = { Text("e.g. Bought 1 kilo kacha morich") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("voice_text_fallback_input")
                    )
                    IconButton(
                        onClick = {
                            if (typedPhrase.isNotBlank()) {
                                if (isRecording) {
                                    recorder.cancelAndRelease()
                                    isRecording = false
                                }
                                onSubmitTypedUtterance(typedPhrase)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (typedPhrase.isNotBlank()) MidnightSlate else SurfaceSubtle)
                            .testTag("voice_text_fallback_submit")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = "Process phrase",
                            tint = if (typedPhrase.isNotBlank()) Color.White else SlateSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceProcessingView(statusLabel: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
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
                .size((84 * pulse).dp)
                .clip(CircleShape)
                .background(CoralSoftBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.GraphicEq,
                contentDescription = null,
                tint = CoralPrimary,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MidnightInk
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Powered by Gemini Multilingual Audio",
            style = MaterialTheme.typography.bodySmall,
            color = SlateSecondary
        )
    }
}

@Composable
fun VoiceClarificationView(
    state: ScannerUiState.ClarifyingVoice,
    onSubmitReply: (replyText: String?, followUpAudio: ByteArray?) -> Unit,
    onSkipToReview: (ExtractedReceiptResult) -> Unit,
    onReRecord: () -> Unit
) {
    val context = LocalContext.current
    val partial = state.partial
    val primaryQuestion = partial.clarificationQuestions.firstOrNull()
    val recorder = remember { VoiceRecorderHelper() }

    var replyInput by remember(partial) { mutableStateOf("") }
    var isRecordingReply by remember { mutableStateOf(false) }
    var micError by remember { mutableStateOf<String?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            recorder.startRecording(context).fold(
                onSuccess = { isRecordingReply = true },
                onFailure = { micError = it.localizedMessage }
            )
        } else {
            micError = "Microphone permission is required for voice replies."
        }
    }

    DisposableEffect(Unit) {
        onDispose { recorder.cancelAndRelease() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Captured Context Summary
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DividerHairline, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = BlueIncome,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Voice Captured",
                        style = MaterialTheme.typography.titleSmall,
                        color = BlueIncome
                    )
                }
                if (!partial.spokenTranscript.isNullOrBlank()) {
                    Text(
                        text = "\"${partial.spokenTranscript}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MidnightInk,
                        fontWeight = FontWeight.Medium
                    )
                }
                HorizontalDivider(color = DividerHairline)
                Text(
                    text = "Detected: ${partial.note} · ${partial.primaryCategory} (${partial.paymentMethodDetected})",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateSecondary
                )
            }
        }

        // Conversational Quick Clarification Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = AmberSoftBg,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AmberWarning.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                .testTag("voice_clarification_card")
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.QuestionAnswer,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Quick Clarification Needed",
                        style = MaterialTheme.typography.titleSmall,
                        color = MidnightInk
                    )
                }

                Text(
                    text = primaryQuestion?.question ?: "Please provide the missing transaction details below:",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MidnightInk
                )

                val quickOptions = primaryQuestion?.quickOptions.orEmpty()
                if (quickOptions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickOptions.forEach { option ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White,
                                modifier = Modifier
                                    .border(1.dp, CoralPrimary.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                    .clickable(enabled = !state.isSubmittingReply) {
                                        onSubmitReply(option, null)
                                    }
                            ) {
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CoralPrimary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Reply text input + Send + Mic button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = replyInput,
                        onValueChange = { replyInput = it },
                        placeholder = {
                            Text(
                                if (primaryQuestion?.inputType == "amount") "Enter amount (${partial.currency})..."
                                else "Type your answer..."
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (primaryQuestion?.inputType == "amount") KeyboardType.Decimal else KeyboardType.Text
                        ),
                        singleLine = true,
                        enabled = !state.isSubmittingReply && !isRecordingReply,
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .testTag("clarification_reply_input")
                    )

                    IconButton(
                        onClick = {
                            if (isRecordingReply) {
                                isRecordingReply = false
                                recorder.stopAndReadBytes().fold(
                                    onSuccess = { bytes -> onSubmitReply(null, bytes) },
                                    onFailure = { micError = it.localizedMessage }
                                )
                            } else {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    recorder.startRecording(context).fold(
                                        onSuccess = { isRecordingReply = true },
                                        onFailure = { micError = it.localizedMessage }
                                    )
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        enabled = !state.isSubmittingReply,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isRecordingReply) CoralPrimary else MidnightSlate)
                            .testTag("clarification_mic_button")
                    ) {
                        Icon(
                            imageVector = if (isRecordingReply) Icons.Outlined.Stop else Icons.Outlined.Mic,
                            contentDescription = if (isRecordingReply) "Stop voice reply" else "Answer by voice",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            if (replyInput.isNotBlank()) {
                                onSubmitReply(replyInput.trim(), null)
                            }
                        },
                        enabled = !state.isSubmittingReply && replyInput.isNotBlank(),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (replyInput.isNotBlank()) CoralPrimary else SurfaceSubtle)
                            .testTag("clarification_send_button")
                    ) {
                        if (state.isSubmittingReply) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = CoralPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Send,
                                contentDescription = "Send answer",
                                tint = if (replyInput.isNotBlank()) Color.White else SlateSecondary
                            )
                        }
                    }
                }

                if (isRecordingReply) {
                    Text(
                        text = "Recording follow-up answer... Tap stop to submit.",
                        style = MaterialTheme.typography.labelMedium,
                        color = CoralPrimary
                    )
                }

                (state.errorMessage ?: micError)?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = CoralPrimary
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onReRecord) {
                Text("Re-record Voice", color = SlateSecondary)
            }
            TextButton(onClick = { onSkipToReview(partial) }) {
                Text("Edit Full Form Instead", color = CoralPrimary)
            }
        }
    }
}
