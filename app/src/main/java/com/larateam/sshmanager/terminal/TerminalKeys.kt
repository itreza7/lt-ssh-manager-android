package com.larateam.sshmanager.terminal

/** Which sticky modifier an extra-key arms. */
enum class ModKind { CTRL, ALT, FN }

/** One button in the extra-keys row. */
sealed interface ExtraKey {
    val label: String
    /** Sends a fixed byte [sequence] straight to the shell (works regardless of IME focus). */
    class Send(override val label: String, val sequence: ByteArray) : ExtraKey
    /** A sticky modifier applied to the next key from the soft keyboard. */
    class Mod(override val label: String, val kind: ModKind) : ExtraKey
    /** Pops the soft keyboard. */
    class Kbd(override val label: String) : ExtraKey
}

/**
 * Parses the user-editable extra-keys layout (see [com.larateam.sshmanager.data.model.AppSettings]).
 *
 * Layout: one key per line.
 *  - `:kbd` / `:ctrl` / `:alt` / `:fn`  → keyboard toggle + sticky modifiers
 *  - `Label = sequence`                  → a macro key that sends [sequence]
 *  - a bare `token`                      → label and sequence are the same
 *  - blank lines and `#` comments are ignored
 *
 * Sequence tokens (space-separated):
 *  - `C-x`  → Ctrl-x  (letters → 0x01..0x1a; `[ \ ] ^ _ /` → their control codes)
 *  - `<esc> <tab> <cr>/<enter> <lf> <space> <bs>/<del> <up> <down> <left> <right>`
 *  - `<home> <end> <ins> <pgup> <pgdn>`
 *  - anything else → literal UTF-8 text
 */
object TerminalKeys {

    fun parseLayout(config: String): List<ExtraKey> = config.lines().mapNotNull { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@mapNotNull null
        when (line.lowercase()) {
            ":kbd" -> ExtraKey.Kbd("⌨")
            ":ctrl" -> ExtraKey.Mod("Ctrl", ModKind.CTRL)
            ":alt" -> ExtraKey.Mod("Alt", ModKind.ALT)
            ":fn" -> ExtraKey.Mod("Fn", ModKind.FN)
            else -> {
                val eq = line.indexOf('=')
                val label = (if (eq >= 0) line.substring(0, eq) else line).trim()
                val seq = (if (eq >= 0) line.substring(eq + 1) else line).trim()
                if (seq.isEmpty()) null else ExtraKey.Send(label.ifEmpty { seq }, parseSequence(seq))
            }
        }
    }

    fun parseSequence(seq: String): ByteArray {
        val out = ArrayList<Byte>()
        for (tok in seq.trim().split(Regex("\\s+"))) {
            if (tok.isEmpty()) continue
            when {
                tok.length >= 2 && tok.startsWith("<") && tok.endsWith(">") ->
                    appendNamed(out, tok.substring(1, tok.length - 1).lowercase())
                tok.length == 3 && (tok.startsWith("C-") || tok.startsWith("c-")) ->
                    out.add(ctrlByte(tok[2]))
                else ->
                    for (b in tok.toByteArray(Charsets.UTF_8)) out.add(b)
            }
        }
        return out.toByteArray()
    }

    private fun appendNamed(out: MutableList<Byte>, name: String) {
        when (name) {
            "esc" -> out.add(0x1B)
            "tab" -> out.add(0x09)
            "cr", "enter", "return" -> out.add(0x0D)
            "lf", "nl" -> out.add(0x0A)
            "space", "sp" -> out.add(0x20)
            "bs", "del", "backspace" -> out.add(0x7F)
            "up" -> csi(out, 'A')
            "down" -> csi(out, 'B')
            "right" -> csi(out, 'C')
            "left" -> csi(out, 'D')
            "home" -> tilde(out, 1)
            "ins", "insert" -> tilde(out, 2)
            "end" -> tilde(out, 4)
            "pgup", "pageup" -> tilde(out, 5)
            "pgdn", "pgdown", "pagedown" -> tilde(out, 6)
            else -> Unit // unknown named token → ignored
        }
    }

    /** Arrow / cursor key: ESC [ <final>. */
    private fun csi(out: MutableList<Byte>, finalChar: Char) {
        out.add(0x1B)
        out.add('['.code.toByte())
        out.add(finalChar.code.toByte())
    }

    /** VT-style navigation key: ESC [ <n> ~  (Home=1, Ins=2, End=4, PgUp=5, PgDn=6). */
    private fun tilde(out: MutableList<Byte>, n: Int) {
        out.add(0x1B)
        out.add('['.code.toByte())
        for (c in n.toString()) out.add(c.code.toByte())
        out.add('~'.code.toByte())
    }

    private fun ctrlByte(c: Char): Byte = when (c) {
        in 'a'..'z' -> (c - 'a' + 1)
        in 'A'..'Z' -> (c - 'A' + 1)
        '[' -> 27
        '\\' -> 28
        ']' -> 29
        '^' -> 30
        '_', '/' -> 31
        '@', ' ' -> 0
        '?' -> 127
        else -> c.code
    }.toByte()
}
