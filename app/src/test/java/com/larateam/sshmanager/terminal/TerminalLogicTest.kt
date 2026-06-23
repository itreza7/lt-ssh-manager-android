package com.larateam.sshmanager.terminal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalLogicTest {

    @Test
    fun columns_and_rows_floor_and_clamp() {
        assertEquals(80, TerminalGeometry.columns(widthPx = 800, cellWidthPx = 10f))
        assertEquals(24, TerminalGeometry.rows(heightPx = 480, cellHeightPx = 20f))
        // floors partial cells
        assertEquals(13, TerminalGeometry.columns(widthPx = 135, cellWidthPx = 10f))
        // never zero/negative
        assertEquals(1, TerminalGeometry.columns(widthPx = 5, cellWidthPx = 10f))
        assertEquals(1, TerminalGeometry.rows(heightPx = 0, cellHeightPx = 0f))
    }

    @Test
    fun ctrl_letter_folds_to_control_code() {
        assertArrayEquals(byteArrayOf(0x03), TerminalInput.bytesFor('c', ctrl = true)) // Ctrl-C
        assertArrayEquals(byteArrayOf(0x03), TerminalInput.bytesFor('C', ctrl = true)) // case-insensitive
        assertArrayEquals(byteArrayOf(0x01), TerminalInput.bytesFor('a', ctrl = true)) // Ctrl-A
        assertArrayEquals(byteArrayOf(0x1A), TerminalInput.bytesFor('z', ctrl = true)) // Ctrl-Z = 26
        assertArrayEquals(byteArrayOf(0x00), TerminalInput.bytesFor(' ', ctrl = true)) // Ctrl-Space = NUL
    }

    @Test
    fun alt_prepends_escape() {
        assertArrayEquals(byteArrayOf(0x1B, 'x'.code.toByte()), TerminalInput.bytesFor('x', alt = true))
        // Ctrl+Alt-C -> ESC, 0x03
        assertArrayEquals(byteArrayOf(0x1B, 0x03), TerminalInput.bytesFor('c', ctrl = true, alt = true))
    }

    @Test
    fun plain_char_is_itself() {
        assertArrayEquals(byteArrayOf('q'.code.toByte()), TerminalInput.bytesFor('q'))
        assertEquals(TerminalInput.ESC, 0x1B.toByte())
        assertEquals(TerminalInput.TAB, 0x09.toByte())
    }
}
