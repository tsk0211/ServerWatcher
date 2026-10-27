package dev.tsk.server.watcher.feature.auth

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.tsk.server.watcher.ui.design.SwButton
import dev.tsk.server.watcher.ui.design.SwTextField

/**
 * Shows a freshly generated TOTP secret and asks for the first code to prove the authenticator
 * app works. Used both while signing in (pool requires MFA) and from Settings.
 *
 * There is no QR code: the secret is shown for manual entry, and "Open authenticator app" hands an
 * otpauth:// link to whichever authenticator is installed, which fills everything in.
 */
@Composable
fun TotpSetupForm(
    secret: String,
    accountName: String,
    isLoading: Boolean,
    onSubmit: (code: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var code by remember { mutableStateOf("") }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var linkFailed by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Add this key to your authenticator app (Google Authenticator, Authy, 1Password…), " +
                "then enter the 6-digit code it shows.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SelectionContainer {
                Text(
                    text = secret.chunked(4).joinToString(" "),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(secret)) }, modifier = Modifier.weight(1f)) { Text("Copy key") }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    val uri = Uri.parse(
                        "otpauth://totp/ServerWatcher:${Uri.encode(accountName)}" +
                            "?secret=$secret&issuer=ServerWatcher"
                    )
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: ActivityNotFoundException) {
                        linkFailed = true
                    }
                },
            ) { Text("Open app", maxLines = 1) }
        }
        if (linkFailed) {
            Text(
                text = "No authenticator app found. Install one, or copy the key and add it manually.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        SwTextField(
            value = code,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) code = it },
            label = { Text("6-digit code") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (code.length == 6 && !isLoading) onSubmit(code) }),
            singleLine = true,
        )
        SwButton(
            text = "Verify and enable",
            onClick = { onSubmit(code) },
            enabled = code.length == 6,
            loading = isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
