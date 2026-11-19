package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import kotlin.math.min

class VoiceRecorderHelper {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false

    fun startRecording(context: Context): Result<Unit> = runCatching {
        cancelAndRelease()
        val dir = File(context.cacheDir, "voice").apply { mkdirs() }
        val file = File(dir, "voice_tx_${System.currentTimeMillis()}.m4a")
        outputFile = file

        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)
            setAudioEncodingBitRate(64000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mediaRecorder
        isRecording = true
    }.onFailure {
        cancelAndRelease()
    }

    fun sampleAmplitudeNormalized(): Float {
        if (!isRecording) return 0.1f
        val amp = try {
            recorder?.maxAmplitude ?: 0
        } catch (_: Exception) {
            0
        }
        return (0.12f + min(amp, 24000) / 24000f * 0.88f).coerceIn(0.12f, 1.0f)
    }

    fun stopAndReadBytes(): Result<ByteArray> = runCatching {
        val currentRecorder = recorder ?: throw IllegalStateException("Recorder is not active.")
        val file = outputFile ?: throw IllegalStateException("No audio recording file found.")
        try {
            if (isRecording) {
                currentRecorder.stop()
            }
        } finally {
            isRecording = false
            try {
                currentRecorder.reset()
                currentRecorder.release()
            } catch (_: Exception) {
            }
            recorder = null
        }

        val bytes = file.takeIf { it.exists() }?.readBytes()
            ?: throw IllegalStateException("Recorded audio file is missing.")
        file.delete()
        outputFile = null
        if (bytes.size < 256) {
            throw IllegalStateException("Recording was too short. Please speak clearly and try again.")
        }
        bytes
    }.onFailure {
        cancelAndRelease()
    }

    fun cancelAndRelease() {
        isRecording = false
        try {
            recorder?.apply {
                try {
                    stop()
                } catch (_: Exception) {
                }
                reset()
                release()
            }
        } catch (_: Exception) {
        }
        recorder = null
        outputFile?.let { file ->
            if (file.exists()) file.delete()
        }
        outputFile = null
    }
}
