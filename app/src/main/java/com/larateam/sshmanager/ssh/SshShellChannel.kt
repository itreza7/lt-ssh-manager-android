package com.larateam.sshmanager.ssh

import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.SessionChannel
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors

/**
 * A live interactive shell over an sshj PTY channel. Exposes the remote stdout/stdin streams and a
 * resize hook so the terminal layer can drive [Session.changeWindowDimensions] (CLAUDE.md §7.1).
 * sshj stays confined to this class (the terminal layer sees only streams + a resize callback).
 *
 * Both stdin writes and window-change requests do blocking socket I/O, so they must NOT run on the
 * main thread (sshj would trip NetworkOnMainThreadException mid-packet and corrupt the transport).
 * Writes already happen on the terminal's writer thread; resize is dispatched to [resizeExecutor].
 * Both serialize on [ioLock] so a resize can never interleave the bytes of a data packet.
 */
class SshShellChannel internal constructor(
    private val session: Session,
    private val shell: Session.Shell,
) : ShellIo {
    private val ioLock = Any()
    private val resizeExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "ssh-pty-resize") }

    override val inputStream: InputStream get() = shell.inputStream

    /** stdin to the remote, serialized against [resize]. Written from the terminal's writer thread. */
    override val outputStream: OutputStream = object : OutputStream() {
        override fun write(b: Int) = synchronized(ioLock) { shell.outputStream.write(b) }
        override fun write(b: ByteArray, off: Int, len: Int) = synchronized(ioLock) { shell.outputStream.write(b, off, len) }
        override fun flush() = synchronized(ioLock) { shell.outputStream.flush() }
        override fun close() { runCatching { shell.outputStream.close() } }
    }

    /**
     * Track the remote PTY size to the view (cols × rows; pixel sizes are advisory). Dispatched off
     * the caller's (main) thread because changeWindowDimensions writes to the socket.
     */
    override fun resize(columns: Int, rows: Int, widthPx: Int, heightPx: Int) {
        runCatching {
            resizeExecutor.execute {
                runCatching {
                    synchronized(ioLock) { (session as SessionChannel).changeWindowDimensions(columns, rows, widthPx, heightPx) }
                }
            }
        }
    }

    override fun close() {
        runCatching { resizeExecutor.shutdownNow() }
        runCatching { shell.close() }
        runCatching { session.close() }
    }
}
