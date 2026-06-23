package com.larateam.sshmanager.terminal

import com.larateam.sshmanager.data.model.AppSettings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalKeysTest {

    @Test
    fun ctrl_letter_maps_to_control_byte() {
        assertArrayEquals(byteArrayOf(2), TerminalKeys.parseSequence("C-b"))
        assertArrayEquals(byteArrayOf(3), TerminalKeys.parseSequence("C-c"))
        assertArrayEquals(byteArrayOf(1), TerminalKeys.parseSequence("C-a"))
        assertArrayEquals(byteArrayOf(26), TerminalKeys.parseSequence("C-z"))
    }

    @Test
    fun tmux_scroll_combo_is_ctrl_b_then_bracket() {
        // The headline use case: one tap sends Ctrl-b then '[' = tmux copy/scroll mode.
        assertArrayEquals(byteArrayOf(2, '['.code.toByte()), TerminalKeys.parseSequence("C-b ["))
    }

    @Test
    fun named_tokens_map_to_control_codes() {
        assertArrayEquals(byteArrayOf(27), TerminalKeys.parseSequence("<esc>"))
        assertArrayEquals(byteArrayOf(9), TerminalKeys.parseSequence("<tab>"))
        assertArrayEquals(byteArrayOf(0x0D), TerminalKeys.parseSequence("<cr>"))
        assertArrayEquals(byteArrayOf(0x20), TerminalKeys.parseSequence("<space>"))
    }

    @Test
    fun arrows_are_csi_sequences() {
        assertArrayEquals(byteArrayOf(27, '['.code.toByte(), 'A'.code.toByte()), TerminalKeys.parseSequence("<up>"))
        assertArrayEquals(byteArrayOf(27, '['.code.toByte(), 'D'.code.toByte()), TerminalKeys.parseSequence("<left>"))
    }

    @Test
    fun nav_keys_are_vt_tilde_sequences() {
        val esc = 27.toByte(); val br = '['.code.toByte(); val tilde = '~'.code.toByte()
        assertArrayEquals(byteArrayOf(esc, br, '1'.code.toByte(), tilde), TerminalKeys.parseSequence("<home>"))
        assertArrayEquals(byteArrayOf(esc, br, '4'.code.toByte(), tilde), TerminalKeys.parseSequence("<end>"))
        assertArrayEquals(byteArrayOf(esc, br, '5'.code.toByte(), tilde), TerminalKeys.parseSequence("<pgup>"))
        assertArrayEquals(byteArrayOf(esc, br, '6'.code.toByte(), tilde), TerminalKeys.parseSequence("<pgdn>"))
    }

    @Test
    fun literal_text_is_sent_verbatim() {
        assertArrayEquals("ls".toByteArray(), TerminalKeys.parseSequence("ls"))
        assertArrayEquals(byteArrayOf('/'.code.toByte()), TerminalKeys.parseSequence("/"))
    }

    @Test
    fun layout_parses_specials_and_macros() {
        val keys = TerminalKeys.parseLayout(
            """
            :kbd
            :ctrl
            scroll = C-b [
            # a comment

            Esc = <esc>
            """.trimIndent(),
        )
        assertEquals(4, keys.size)
        assertTrue(keys[0] is ExtraKey.Kbd)
        assertTrue(keys[1] is ExtraKey.Mod && (keys[1] as ExtraKey.Mod).kind == ModKind.CTRL)
        val scroll = keys[2] as ExtraKey.Send
        assertEquals("scroll", scroll.label)
        assertArrayEquals(byteArrayOf(2, '['.code.toByte()), scroll.sequence)
        assertTrue(keys[3] is ExtraKey.Send)
    }

    @Test
    fun default_layout_is_valid_and_has_the_tmux_combo() {
        val keys = TerminalKeys.parseLayout(AppSettings.DEFAULT_TERMINAL_KEYS)
        assertTrue("has a keyboard key", keys.any { it is ExtraKey.Kbd })
        assertTrue("has ctrl + alt", keys.count { it is ExtraKey.Mod } >= 2)
        val combo = keys.filterIsInstance<ExtraKey.Send>().firstOrNull { it.sequence.contentEquals(byteArrayOf(2, '['.code.toByte())) }
        assertTrue("default ships the tmux scroll combo", combo != null)
        val enter = keys.filterIsInstance<ExtraKey.Send>().firstOrNull { it.sequence.contentEquals(byteArrayOf(0x0D)) }
        assertTrue("default ships an Enter key", enter != null)
    }
}
