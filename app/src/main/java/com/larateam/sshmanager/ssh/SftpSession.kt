package com.larateam.sshmanager.ssh

import com.larateam.sshmanager.sftp.SftpDeleteOps
import com.larateam.sshmanager.sftp.SftpEntry
import com.larateam.sshmanager.sftp.SftpEntryType
import com.larateam.sshmanager.sftp.SftpErrorReason
import com.larateam.sshmanager.sftp.SftpException
import com.larateam.sshmanager.sftp.SftpPath
import com.larateam.sshmanager.sftp.deleteRecursive as runDeleteRecursive
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.SFTPException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet
import kotlin.coroutines.coroutineContext

/**
 * Wraps ONE sshj [SFTPClient] channel. sshj's SFTPClient is NOT safe for concurrent calls, so every
 * metadata op is serialized on [lock] and runs on [io]. sshj stays confined to this class; callers
 * see only the domain [SftpEntry]. Long transfers should use a SEPARATE [SftpSession] (own channel)
 * so a big put/get never blocks interactive browsing on the metadata channel.
 */
class SftpSession internal constructor(
    private val sftp: SFTPClient,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : SftpDeleteOps {
    private val lock = Mutex()

    suspend fun canonicalize(path: String): String = guarded { sftp.canonicalize(path) }

    suspend fun list(path: String): List<SftpEntry> = guarded {
        sftp.ls(path).mapNotNull { info ->
            if (info.name == "." || info.name == "..") null
            else toEntry(SftpPath.join(path, info.name), info.name, info.attributes)
        }
    }

    /** No-follow stat (symlinks reported as SYMLINK). */
    suspend fun lstat(path: String): SftpEntry = guarded { toEntry(path, SftpPath.name(path), sftp.lstat(path)) }

    suspend fun mkdir(path: String) = guarded { sftp.mkdir(path) }
    suspend fun rename(from: String, to: String) = guarded { sftp.rename(from, to) }
    suspend fun chmod(path: String, mode: Int) = guarded { sftp.chmod(path, mode) }
    suspend fun size(path: String): Long = guarded { sftp.stat(path).size }

    /** Recursive delete with no-follow-symlink safety (see [com.larateam.sshmanager.sftp.deleteRecursive]). */
    suspend fun deleteRecursive(path: String) = runDeleteRecursive(this, path)

    // SftpDeleteOps — entryType MUST be no-follow (lstat) so symlinks are never traversed.
    override suspend fun entryType(path: String): SftpEntryType = lstat(path).type
    override suspend fun children(path: String): List<String> = list(path).map { it.path }
    override suspend fun removeFile(path: String) = guarded { sftp.rm(path) }
    override suspend fun removeDir(path: String) = guarded { sftp.rmdir(path) }

    suspend fun download(remote: String, sink: OutputStream, onBytes: (Long) -> Unit) = withContext(io) {
        mapped {
            sftp.open(remote, EnumSet.of(OpenMode.READ)).use { rf ->
                rf.RemoteFileInputStream().use { input -> copy(input, sink, onBytes) }
            }
        }
    }

    suspend fun upload(remote: String, source: InputStream, onBytes: (Long) -> Unit) = withContext(io) {
        mapped {
            sftp.open(remote, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)).use { rf ->
                rf.RemoteFileOutputStream().use { output -> copy(source, output, onBytes) }
            }
        }
    }

    fun close() {
        runCatching { sftp.close() }
    }

    private suspend fun copy(input: InputStream, output: OutputStream, onBytes: (Long) -> Unit) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            coroutineContext.ensureActive() // cancellation aborts the transfer cleanly (.use closes streams)
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            total += read
            onBytes(total)
        }
        output.flush()
    }

    private suspend fun <T> guarded(block: () -> T): T = lock.withLock { withContext(io) { mapped(block) } }

    /** Map sshj's SFTPException / IOException to a classified domain [SftpException] (no sshj leaks). */
    private inline fun <T> mapped(block: () -> T): T = try {
        block()
    } catch (e: SftpException) {
        throw e
    } catch (e: SFTPException) {
        val reason = when (e.statusCode) {
            Response.StatusCode.PERMISSION_DENIED -> SftpErrorReason.PERMISSION_DENIED
            Response.StatusCode.NO_SUCH_FILE -> SftpErrorReason.NOT_FOUND
            else -> SftpErrorReason.REJECTED
        }
        throw SftpException(reason, e.message ?: "SFTP error")
    } catch (e: IOException) {
        throw SftpException(SftpErrorReason.IO, e.message ?: "I/O error")
    }

    private fun toEntry(path: String, name: String, attrs: FileAttributes): SftpEntry {
        val type = when (attrs.mode.type) {
            FileMode.Type.DIRECTORY -> SftpEntryType.DIRECTORY
            FileMode.Type.REGULAR -> SftpEntryType.FILE
            FileMode.Type.SYMLINK -> SftpEntryType.SYMLINK
            else -> SftpEntryType.OTHER
        }
        return SftpEntry(name, path, type, attrs.size, attrs.mode.permissionsMask, attrs.mtime)
    }
}
