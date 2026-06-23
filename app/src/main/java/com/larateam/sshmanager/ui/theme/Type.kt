package com.larateam.sshmanager.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Two voices. `Sans` (the platform grotesk) is human chrome; `Mono` is the machine —
// hostnames, ports, fingerprints, status, and the eyebrow labels are all set in it.
private val Sans = FontFamily.Default
val Mono = FontFamily.Monospace

private val base = Typography()

/**
 * Material type scale, tuned. Display/title slots are tightened and weighted so headings read
 * as deliberate rather than default-Roboto; body/label slots keep Material's proven metrics.
 */
val Typography = base.copy(
    displaySmall = base.displaySmall.copy(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp,
    ),
    headlineMedium = base.headlineMedium.copy(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp,
    ),
    headlineSmall = base.headlineSmall.copy(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp,
    ),
    titleLarge = base.titleLarge.copy(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp,
    ),
    titleMedium = base.titleMedium.copy(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
    ),
    labelLarge = base.labelLarge.copy(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
    ),
)

/** Custom styles outside Material's slots — applied by hand where the "machine voice" belongs. */
object BrandType {
    /** Tiny uppercase monospace marker. Set text in UPPERCASE at the call site. */
    val eyebrow = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 2.2.sp,
    )

    /** Monospace data line: endpoints, fingerprints, key/value readouts. */
    val data = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    )

    /** Small monospace pill/chip text (auth method, counts). */
    val tag = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp,
    )
}
