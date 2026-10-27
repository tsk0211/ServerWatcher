package dev.tsk.server.watcher.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tsk.server.watcher.ui.theme.sw

/**
 * Page background: the base color with two soft colored glows (brand green top-left, blue
 * bottom-right) so the screen has atmosphere instead of being one flat fill.
 */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val base = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val strength = if (MaterialTheme.sw.isDark) 0.20f else 0.11f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(base)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(primary.copy(alpha = strength), Color.Transparent),
                        center = Offset(size.width * 0.05f, -size.height * 0.02f),
                        radius = size.width * 1.1f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(secondary.copy(alpha = strength * 0.8f), Color.Transparent),
                        center = Offset(size.width * 1.0f, size.height * 0.92f),
                        radius = size.width * 1.0f,
                    ),
                )
            },
        content = {
            // Outside a Surface, text would fall back to black; give everything a readable default.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                content()
            }
        },
    )
}

/** Top bar that lets [AppBackground] show through instead of painting its own slab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun transparentTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
)

/**
 * The app's card. A tonal fill with a faint top-lit gradient, a hairline border that fades from
 * highlight to shadow, and a shadow. With [accent] the shadow becomes a colored glow and a wash of
 * that color bleeds in from the top-left, so state (healthy, failing, running) is felt at a glance.
 */
@Composable
fun SwCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = MaterialTheme.sw.isDark

    val fill = if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest
    val fillBrush = Brush.verticalGradient(
        colors = if (dark) {
            listOf(Color.White.copy(alpha = 0.045f).compositeOver(fill), fill)
        } else {
            listOf(fill, scheme.surfaceContainerLow)
        },
    )
    val borderBrush = Brush.verticalGradient(
        colors = listOf(
            (accent ?: if (dark) Color.White else scheme.outline).copy(alpha = if (dark) 0.28f else 0.30f),
            scheme.outlineVariant,
        ),
    )
    val shadowTint = accent?.copy(alpha = if (dark) 0.55f else 0.35f)
        ?: Color.Black.copy(alpha = if (dark) 0.8f else 0.30f)

    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Column(
        modifier = modifier
            .shadow(
                elevation = if (dark) 10.dp else 9.dp,
                shape = shape,
                ambientColor = shadowTint,
                spotColor = shadowTint,
            )
            .clip(shape)
            .background(fillBrush)
            .drawBehind {
                if (accent != null) {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = if (dark) 0.22f else 0.14f), Color.Transparent),
                            center = Offset(0f, 0f),
                            radius = size.maxDimension * 0.9f,
                        ),
                    )
                }
            }
            .border(1.dp, borderBrush, shape)
            .then(clickable),
        content = content,
    )
}

/** A rounded-square icon on a tinted gradient, used to give list rows and sections an anchor. */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    contentDescription: String? = null,
) {
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(tint.copy(alpha = 0.30f), tint.copy(alpha = 0.10f)),
                ),
            )
            .border(1.dp, tint.copy(alpha = 0.28f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Small uppercase caption that introduces a group of cards. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
    )
}

/** A big number with a caption, tinted by [color]. */
@Composable
fun StatTile(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    SwCard(modifier = modifier, accent = color) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A status color usable as text. Bright state colors (amber, grey, green) vanish on a pale tint in
 * the light theme, so they are pushed toward black there; the dark theme uses them as they are.
 */
@Composable
fun Color.readable(): Color =
    if (MaterialTheme.sw.isDark) this else androidx.compose.ui.graphics.lerp(this, Color.Black, 0.45f)

/** Title block for the top of a bottom sheet: an icon badge, a heading and a one-line hint. */
@Composable
fun SheetHeader(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(icon = icon, tint = tint, size = 46.dp)
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Placeholder cards with a gentle pulse, shown while a list is loading. */
@Composable
fun SkeletonList(count: Int = 3, modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.14f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(900),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    val bar = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(count) {
            SwCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.fillMaxWidth(0.55f).height(18.dp).clip(CircleShape).background(bar))
                    Box(Modifier.fillMaxWidth(0.3f).height(12.dp).clip(CircleShape).background(bar))
                }
            }
        }
    }
}

/** Round, softly filled icon button for top bars (back, menu, settings). */
@Composable
fun TonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        content = content,
    )
}
