package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.firebase.getAppCheckDebugSecret
import com.example.ui.theme.BlueIncome
import com.example.ui.theme.BlueSoftBg
import com.example.ui.theme.CardBorderSubtle
import com.example.ui.theme.EmeraldHealthy
import com.example.ui.theme.MidnightInk
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SurfaceGroupedBg

@Composable
fun AppCheckDebugHelpCard(
    modifier: Modifier = Modifier,
    showCsvHint: Boolean = false
) {
    if (!BuildConfig.DEBUG) return

    val context = LocalContext.current
    val debugSecret = remember(context) { getAppCheckDebugSecret(context) } ?: return
    var copied by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = BlueSoftBg.copy(alpha = 0.7f),
        border = BorderStroke(0.5.dp, BlueIncome.copy(alpha = 0.25f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Key,
                    contentDescription = null,
                    tint = BlueIncome,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Device App Check Debug Token",
                    style = MaterialTheme.typography.labelLarge,
                    color = MidnightInk,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                border = BorderStroke(0.5.dp, CardBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = debugSecret,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MidnightInk,
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("appcheck_debug_secret_text")
                )
            }

            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("App Check Debug Token", debugSecret))
                    copied = true
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = if (copied) EmeraldHealthy else BlueIncome
                ),
                border = BorderStroke(0.75.dp, if (copied) EmeraldHealthy else BlueIncome.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("copy_appcheck_token_button")
            ) {
                Icon(
                    imageVector = if (copied) Icons.Outlined.CheckCircle else Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (copied) "Copied! Add in Firebase Console → App Check" else "Copy Device Debug Token",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "1. Open Firebase Console → App Check → Apps → Penniless (${context.packageName}).\n" +
                    "2. Tap ⋮ → Manage debug tokens → Add debug token, paste this token, and save.\n" +
                    "3. Return here and retry (or run a Release build via reCAPTCHA Enterprise)." +
                    if (showCsvHint) "\n\nTip: CSV bank statements import locally without needing App Check." else "",
                style = MaterialTheme.typography.bodySmall,
                color = SlateSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
            )
        }
    }
}
