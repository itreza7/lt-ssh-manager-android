package com.larateam.sshmanager.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// "Operator's console" palette. Hand-built (dynamic color is OFF) so the app has
// one identity on every device: an ink-slate ground, a phosphor-cyan structural
// accent, and a warm amber "live" signal. Machine data is rendered in monospace
// (see Type.kt); these tokens carry the chrome.
// ─────────────────────────────────────────────────────────────────────────────

// --- Dark (the hero surface) -------------------------------------------------
val InkBackground = Color(0xFF0C0F14)
val InkSurface = Color(0xFF12171F)
val InkContainer = Color(0xFF161C26)
val InkContainerHigh = Color(0xFF1B2330)
val InkContainerHighest = Color(0xFF212B3A)
val InkSurfaceVariant = Color(0xFF1C2430)
val InkOutline = Color(0xFF2A3441)
val InkOutlineVariant = Color(0xFF1E2733)
val OnInk = Color(0xFFE7ECF3)
val OnInkVariant = Color(0xFF93A0B4)

val Cyan = Color(0xFF38CBD6)
val OnCyan = Color(0xFF00363B)
val CyanContainer = Color(0xFF00474E)
val OnCyanContainer = Color(0xFFA6F0F5)

val Amber = Color(0xFFF2B14C)
val OnAmber = Color(0xFF281A00)
val AmberContainer = Color(0xFF463205)
val OnAmberContainer = Color(0xFFFFDDA8)

val SkyTertiary = Color(0xFF8FB7EA)
val OnSkyTertiary = Color(0xFF0A2742)

val Rose = Color(0xFFFF6B6B)
val OnRose = Color(0xFF3A0A0C)
val RoseContainer = Color(0xFF5A1418)
val OnRoseContainer = Color(0xFFFFD9D9)

// --- Light (cool paper — deliberately not cream) -----------------------------
val PaperBackground = Color(0xFFF1F4F8)
val PaperSurface = Color(0xFFFFFFFF)
val PaperContainer = Color(0xFFEAEFF4)
val PaperContainerHigh = Color(0xFFE3EAF1)
val PaperContainerHighest = Color(0xFFDCE4ED)
val PaperSurfaceVariant = Color(0xFFE2E8EF)
val PaperOutline = Color(0xFFC5CEDA)
val PaperOutlineVariant = Color(0xFFD8DFE8)
val OnPaper = Color(0xFF0E141B)
val OnPaperVariant = Color(0xFF54616F)

val DeepTeal = Color(0xFF00696F)
val OnDeepTeal = Color(0xFFFFFFFF)
val TealContainer = Color(0xFF9CF1F6)
val OnTealContainer = Color(0xFF002023)

val DeepAmber = Color(0xFF8A5300)
val OnDeepAmber = Color(0xFFFFFFFF)
val AmberContainerLight = Color(0xFFFFDDB0)
val OnAmberContainerLight = Color(0xFF2B1700)

val SkyTertiaryLight = Color(0xFF1F5F9E)
val OnSkyTertiaryLight = Color(0xFFFFFFFF)

val RoseLight = Color(0xFFB3261E)
val OnRoseLight = Color(0xFFFFFFFF)
val RoseContainerLight = Color(0xFFFFDAD5)
val OnRoseContainerLight = Color(0xFF410002)

// --- Status semantics (the signature spine/dot colors) -----------------------
// One vocabulary across connections, sessions, and the dashboard.
val StatusLiveDark = Color(0xFF38CBD6)
val StatusBusyDark = Color(0xFFF2B14C)
val StatusDownDark = Color(0xFFFF6B6B)
val StatusIdleDark = Color(0xFF5E6E86)
val GridLineDark = Color(0xFF26313F)
val EyebrowDark = Color(0xFF7FB6BC)

val StatusLiveLight = Color(0xFF00767E)
val StatusBusyLight = Color(0xFF9A6300)
val StatusDownLight = Color(0xFFB3261E)
val StatusIdleLight = Color(0xFF7A8696)
val GridLineLight = Color(0xFFD2DAE3)
val EyebrowLight = Color(0xFF2F6A6F)
