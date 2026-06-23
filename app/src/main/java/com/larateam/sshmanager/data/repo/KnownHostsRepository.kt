package com.larateam.sshmanager.data.repo

import com.larateam.sshmanager.data.db.KnownHostDao
import com.larateam.sshmanager.data.db.KnownHostEntity
import com.larateam.sshmanager.ssh.KnownHostStore
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed pinned host-key store. Implements the synchronous [KnownHostStore] used by the
 * verifier on sshj's blocking IO thread (runBlocking here is off the main thread).
 */
@Singleton
class KnownHostsRepository @Inject constructor(
    private val dao: KnownHostDao,
) : KnownHostStore {

    override fun pinnedFingerprint(host: String, port: Int): String? =
        runBlocking { dao.get(key(host, port))?.fingerprintSha256 }

    override fun pin(host: String, port: Int, fingerprintSha256: String) {
        runBlocking { dao.insert(KnownHostEntity(key(host, port), fingerprintSha256)) }
    }

    suspend fun all(): List<KnownHostEntity> = dao.all()

    /** Live list of pinned hosts for the Settings → Known hosts screen. */
    fun observeAll() = dao.observeAll()

    /** Test/debug affordance: simulate a key change by corrupting the stored pin. */
    suspend fun tamperFingerprint(host: String, port: Int) =
        dao.updateFingerprint(key(host, port), "SHA256:TAMPEREDxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")

    /** Explicit, deliberate re-pin of a changed key (never automatic). */
    suspend fun repin(host: String, port: Int, fingerprintSha256: String) =
        dao.updateFingerprint(key(host, port), fingerprintSha256)

    suspend fun forget(host: String, port: Int) = dao.deleteByHostPort(key(host, port))

    /** Forget a legitimately-rotated host so the next connect re-TOFUs (Settings → Known hosts). */
    suspend fun forgetByKey(hostPort: String) = dao.deleteByHostPort(hostPort)

    private fun key(host: String, port: Int) = "$host:$port"
}
