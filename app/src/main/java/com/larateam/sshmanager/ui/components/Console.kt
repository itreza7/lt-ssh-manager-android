package com.larateam.sshmanager.ui.components

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.larateam.sshmanager.ui.theme.Brand
import com.larateam.sshmanager.ui.theme.BrandType
import com.larateam.sshmanager.ui.theme.StatusKind

/** True when the user has asked the system to remove animations. */
@Composable
private fun reduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Tiny uppercase monospace marker — the structural label of the console. */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Brand.palette.eyebrow,
) {
    Text(text.uppercase(), modifier = modifier, color = color, style = BrandType.eyebrow)
}

/** A monospace pill. Quiet by default; pass a [color] to make it carry meaning. */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = color,
        style = BrandType.tag,
    )
}

/**
 * The status vocabulary as a dot. [StatusKind.BUSY] breathes (unless motion is reduced); the rest
 * are steady. [StatusKind.LIVE] gets a faint halo so a glance separates "connected" from "idle".
 */
@Composable
fun StatusDot(kind: StatusKind, modifier: Modifier = Modifier, size: Int = 9) {
    val color = Brand.palette.color(kind)
    val alpha = if (kind == StatusKind.BUSY && !reduceMotion()) {
        val transition = rememberInfiniteTransition(label = "pulse")
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
            label = "pulseAlpha",
        ).value
    } else 1f

    Box(modifier = modifier.size((size + 6).dp), contentAlignment = Alignment.Center) {
        if (kind == StatusKind.LIVE) {
            Box(Modifier.size((size + 5).dp).clip(CircleShape).background(color.copy(alpha = 0.22f)))
        }
        Box(Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = alpha)))
    }
}

/**
 * The recurring surface: a hairline-bordered container with an optional status [spine] on its left
 * edge. The spine is the app's signature — connection state rendered as structure, not decoration.
 */
@Composable
fun ConsoleCard(
    modifier: Modifier = Modifier,
    spine: StatusKind? = null,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val spineColor = spine?.let { Brand.palette.color(it) }

    val inner: @Composable () -> Unit = {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(spineColor ?: Color.Transparent),
            )
            Box(Modifier.weight(1f)) { content() }
        }
    }

    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, border = border, content = inner)
    } else {
        Surface(modifier = modifier, shape = shape, color = color, border = border, content = inner)
    }
}

/** Branded landing header: eyebrow, wordmark with a blinking cursor block, and a one-line subtitle. */
@Composable
fun BrandHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Eyebrow(eyebrow)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Cursor(Modifier.padding(start = 6.dp))
        }
        Text(
            subtitle,
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A terminal cursor block that blinks — the wordmark's one piece of motion. */
@Composable
private fun Cursor(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val alpha = if (reduceMotion()) 1f else {
        val t = rememberInfiniteTransition(label = "cursor")
        t.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
            label = "cursorAlpha",
        ).value
    }
    Box(
        modifier
            .padding(bottom = 4.dp)
            .size(width = 22.dp, height = 30.dp)
            .drawBehind {
                drawRoundRect(
                    color = color.copy(alpha = alpha),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            },
    )
}

/**
 * A labelled meter: label + reading on one line, a thin track + fill below. The fill color is the
 * caller's call so the dashboard can shade by load (cyan → amber → rose).
 */
@Composable
fun MetricBar(
    label: String,
    reading: String,
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // The label is the unbounded one (a disk mount can be long): let it take the slack and
            // ellipsize. The reading stays on one line so a long label can never squeeze it to a
            // per-character column.
            Text(
                label.uppercase(),
                style = BrandType.eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                reading,
                style = BrandType.data,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        val track = MaterialTheme.colorScheme.outlineVariant
        Box(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(track),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
    }
}
