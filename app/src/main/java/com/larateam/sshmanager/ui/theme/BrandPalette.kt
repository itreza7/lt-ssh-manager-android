package com.larateam.sshmanager.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.larateam.sshmanager.data.model.ConnState

/** The four states every live thing in the app can be in. Drives the status spine + dot. */
enum class StatusKind { LIVE, BUSY, DOWN, IDLE }

/**
 * Theme-aware extras that Material's [androidx.compose.material3.ColorScheme] has no slot for:
 * the status vocabulary, hairline grid color, and the monospace eyebrow accent. Provided through
 * [LocalBrand] so composables read the right variant for light/dark without threading a flag.
 */
data class BrandPalette(
    val live: Color,
    val busy: Color,
    val down: Color,
    val idle: Color,
    val gridLine: Color,
    val eyebrow: Color,
) {
    fun color(kind: StatusKind): Color = when (kind) {
        StatusKind.LIVE -> live
        StatusKind.BUSY -> busy
        StatusKind.DOWN -> down
        StatusKind.IDLE -> idle
    }
}

val DarkBrand = BrandPalette(
    live = StatusLiveDark,
    busy = StatusBusyDark,
    down = StatusDownDark,
    idle = StatusIdleDark,
    gridLine = GridLineDark,
    eyebrow = EyebrowDark,
)

val LightBrand = BrandPalette(
    live = StatusLiveLight,
    busy = StatusBusyLight,
    down = StatusDownLight,
    idle = StatusIdleLight,
    gridLine = GridLineLight,
    eyebrow = EyebrowLight,
)

val LocalBrand = staticCompositionLocalOf { DarkBrand }

/** Convenience accessor mirroring `MaterialTheme.colorScheme`. */
object Brand {
    val palette: BrandPalette
        @Composable @ReadOnlyComposable get() = LocalBrand.current
}

/** Maps a live [ConnState] to its status vocabulary so every screen agrees on meaning. */
fun ConnState.statusKind(): StatusKind = when (this) {
    is ConnState.Connected -> StatusKind.LIVE
    ConnState.Connecting -> StatusKind.BUSY
    ConnState.Disconnected -> StatusKind.IDLE
    is ConnState.Error -> StatusKind.DOWN
}
