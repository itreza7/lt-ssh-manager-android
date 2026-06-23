package com.larateam.sshmanager.terminal

/**
 * Pure mapping from a key + Ctrl/Alt modifiers to the bytes sent to the remote shell. This is the
 * load-bearing extra-keys logic (Ctrl-C -> 0x03, Alt-x -> ESC x, etc.) and is unit-tested.
 */
object TerminalInput {
    const val ESC: Byte = 0x1B
    const val TAB: Byte = 0x09

    /** Control byte for a character, or null if it has no control mapping. Ctrl-A..Z -> 1..26. */
    fun ctrlByte(c: Char): Int? = when (c) {
        in 'a'..'z' -> c.code - 'a'.code + 1
        in 'A'..'Z' -> c.code - 'A'.code + 1
        ' ', '@' -> 0
        '[' -> 27
        '\\' -> 28
        ']' -> 29
        '^' -> 30
        '_' -> 31
        '?' -> 127
        else -> null
    }

    /** Bytes for a printable [char] with optional Ctrl/Alt. Alt prepends ESC; Ctrl folds to a control code. */
    fun bytesFor(char: Char, ctrl: Boolean = false, alt: Boolean = false): ByteArray {
        val key = if (ctrl) (ctrlByte(char) ?: char.code) else char.code
        return if (alt) byteArrayOf(ESC, key.toByte()) else byteArrayOf(key.toByte())
    }
}
