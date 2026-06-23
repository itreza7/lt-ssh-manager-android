package com.larateam.sshmanager.terminal

import kotlin.math.floor

/**
 * Pure cols×rows computation from the view size and font cell metrics. Kept separate so it is
 * unit-testable without any View/Android dependency.
 */
object TerminalGeometry {
    /** @return columns for the given usable width and per-cell width (both in px). At least 1. */
    fun columns(widthPx: Int, cellWidthPx: Float): Int =
        if (cellWidthPx <= 0f) 1 else floor(widthPx / cellWidthPx).toInt().coerceAtLeast(1)

    /** @return rows for the given usable height and per-cell height (both in px). At least 1. */
    fun rows(heightPx: Int, cellHeightPx: Float): Int =
        if (cellHeightPx <= 0f) 1 else floor(heightPx / cellHeightPx).toInt().coerceAtLeast(1)
}
