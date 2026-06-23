package com.larateam.sshmanager.data.model

/** App-wide visual theme preference. */
enum class AppTheme { SYSTEM, LIGHT, DARK }

/**
 * User-configurable global settings (non-secret). Persisted in the preferences DataStore.
 * [batteryPromptDismissed] records that the one-time contextual battery prompt was shown so it
 * never nags again (§7.4).
 */
data class AppSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val terminalFontSizeSp: Int = DEFAULT_TERMINAL_FONT_SP,
    val batteryPromptDismissed: Boolean = false,
    /** The user-editable extra-keys row layout (see TerminalKeys for the mini-syntax). */
    val terminalKeys: String = DEFAULT_TERMINAL_KEYS,
) {
    companion object {
        const val DEFAULT_TERMINAL_FONT_SP = 16
        const val MIN_TERMINAL_FONT_SP = 8
        const val MAX_TERMINAL_FONT_SP = 32

        /**
         * Default extra-keys layout. One key per line:
         *  - `:kbd` / `:ctrl` / `:alt` / `:fn`  → the keyboard toggle and the sticky modifiers
         *  - `Label = sequence`                  → a key that sends a sequence (a macro)
         * Sequences: `C-b` = Ctrl-b, `<esc> <tab> <cr> <up> <down> <left> <right> <space>`,
         * `<home> <end> <pgup> <pgdn>`, or literal text.
         * `⌃b[` sends Ctrl-b then `[` in one tap = tmux scroll/copy mode.
         */
        val DEFAULT_TERMINAL_KEYS = """
            :kbd
            :ctrl
            :alt
            Esc = <esc>
            Tab = <tab>
            ⏎ = <cr>
            ← = <left>
            ↑ = <up>
            ↓ = <down>
            → = <right>
            ⌃C = C-c
            ⌃D = C-d
            ⌃Z = C-z
            ⌃L = C-l
            ⌃R = C-r
            ⌃b[ = C-b [
            Home = <home>
            End = <end>
            PgUp = <pgup>
            PgDn = <pgdn>
            / = /
            - = -
            | = |
            ~ = ~
        """.trimIndent()
    }
}
