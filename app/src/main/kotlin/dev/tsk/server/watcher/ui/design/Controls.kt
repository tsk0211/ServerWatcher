package dev.tsk.server.watcher.ui.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class SwTone { Primary, Danger, Tonal }

/**
 * Full-width call-to-action. Primary and Danger are filled gradients with a colored glow; Tonal is
 * the quieter secondary action. Shows a spinner in place of the label while [loading].
 */
@Composable
fun SwButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    tone: SwTone = SwTone.Primary,
    icon: ImageVector? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val active = enabled && !loading

    val base = when (tone) {
        SwTone.Primary -> scheme.primary
        SwTone.Danger -> scheme.error
        SwTone.Tonal -> scheme.surfaceContainerHighest
    }
    val onBase = when (tone) {
        SwTone.Primary -> scheme.onPrimary
        SwTone.Danger -> scheme.onError
        SwTone.Tonal -> scheme.primary
    }
    val container = when {
        !enabled -> Brush.verticalGradient(listOf(scheme.onSurface.copy(alpha = 0.10f), scheme.onSurface.copy(alpha = 0.10f)))
        tone == SwTone.Tonal -> Brush.verticalGradient(listOf(base, scheme.surfaceContainerHigh))
        else -> Brush.verticalGradient(listOf(lerp(base, Color.White, 0.14f), base))
    }
    val content = if (enabled) onBase else scheme.onSurface.copy(alpha = 0.38f)
    val glow = base.copy(alpha = 0.55f)

    Surface(
        onClick = onClick,
        enabled = active,
        shape = shape,
        color = Color.Transparent,
        contentColor = content,
        modifier = modifier
            .heightIn(min = 52.dp)
            .shadow(
                elevation = if (enabled && tone != SwTone.Tonal) 10.dp else 0.dp,
                shape = shape,
                ambientColor = glow,
                spotColor = glow,
            )
            .background(container, shape),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = content)
            } else {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Text field with the app's look: a faint tinted fill, a soft outline that turns brand-green on
 * focus, and rounded corners. Same parameters as the Material field for the subset the app uses.
 */
@Composable
fun SwTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = scheme.onSurface.copy(alpha = 0.07f),
            unfocusedContainerColor = scheme.onSurface.copy(alpha = 0.04f),
            disabledContainerColor = scheme.onSurface.copy(alpha = 0.02f),
            errorContainerColor = scheme.error.copy(alpha = 0.06f),
            focusedBorderColor = scheme.primary,
            unfocusedBorderColor = scheme.outline.copy(alpha = 0.55f),
            focusedLabelColor = scheme.primary,
            cursorColor = scheme.primary,
        ),
    )
}

/** A status dot with a soft halo; with [pulse] the halo breathes outward. */
@Composable
fun StatusOrb(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
    pulse: Boolean = false,
) {
    val progress by if (pulse) {
        rememberInfiniteTransition(label = "orb").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Restart),
            label = "orbProgress",
        )
    } else {
        rememberUpdatedState(0f)
    }

    // The canvas is 2.6x the dot so the halo has room and never gets clipped by the layout.
    val dot = size
    Box(modifier = modifier.size(dot * 2.6f), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(dot * 2.6f)) {
            val core = dot.toPx() / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            if (pulse) {
                val radius = core + (core * 0.8f) * progress * 2f
                drawCircle(color.copy(alpha = 0.45f * (1f - progress)), radius = radius, center = center)
            }
            drawCircle(color.copy(alpha = 0.22f), radius = core * 1.9f, center = center)
            drawCircle(color, radius = core, center = center)
        }
    }
}
