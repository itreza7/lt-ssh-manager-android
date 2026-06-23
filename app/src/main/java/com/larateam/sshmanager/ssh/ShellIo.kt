package com.larateam.sshmanager.ssh

import java.io.InputStream
import java.io.OutputStream

/**
 * Generic interactive-shell I/O that the terminal layer consumes. [SshShellChannel] is the real
 * (sshj-backed) implementation; abstracting it here keeps sshj confined to the `ssh/` package and
 * lets the session registry be tested with an in-memory fake.
 */
interface ShellIo {
    val inputStream: InputStream
    val outputStream: OutputStream
    fun resize(columns: Int, rows: Int, widthPx: Int, heightPx: Int)
    fun close()
}
