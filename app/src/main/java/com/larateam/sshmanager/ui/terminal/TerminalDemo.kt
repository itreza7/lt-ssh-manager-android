package com.larateam.sshmanager.ui.terminal

import androidx.compose.runtime.Composable
import com.larateam.sshmanager.terminal.SshTerminalSessionClient
import com.termux.terminal.TerminalSession
import java.io.ByteArrayInputStream
import java.io.OutputStream

/** STEP 0 proof: feed static ANSI + 24-bit-color bytes to the emulator (no SSH) to confirm rendering. */
object TerminalDemo {
    private const val E = ""

    fun ansiBytes(): ByteArray {
        val sb = StringBuilder()
        sb.append("$E[1mlarateam terminal - static render check$E[0m\r\n\r\n")
        sb.append("16-color:  ")
        for (c in 31..36) sb.append("$E[1;${c}m##$E[0m ")
        sb.append("\r\n256-color: $E[38;5;208morange$E[0m $E[38;5;45mcyan$E[0m $E[38;5;201mmagenta$E[0m\r\n")
        sb.append("24-bit fg: $E[38;2;255;140;0mTrueColor #FF8C00$E[0m $E[38;2;120;200;255m#78C8FF$E[0m\r\n")
        sb.append("24-bit bg: $E[48;2;40;0;80m$E[38;2;255;220;0m  yellow on deep purple  $E[0m\r\n")
        sb.append("attrs:     $E[4munderline$E[0m $E[7minverse$E[0m $E[2mdim$E[0m\r\n")
        sb.append("gradient:  ")
        var r = 0
        while (r <= 255) {
            sb.append("$E[48;2;$r;0;${255 - r}m $E[0m")
            r += 6
        }
        sb.append("\r\n\r\ntrue-color works\r\n")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    fun staticSession(client: SshTerminalSessionClient): TerminalSession =
        TerminalSession(
            ByteArrayInputStream(ansiBytes()),
            object : OutputStream() { override fun write(b: Int) {} },
            2000,
            client,
            null,
        )
}

@Composable
fun StaticTerminalDemoScreen() {
    TerminalScreen(sessionFactory = { client -> TerminalDemo.staticSession(client) })
}
