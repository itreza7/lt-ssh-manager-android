package com.larateam.sshmanager.data.repo

import com.larateam.sshmanager.data.db.ConnectionDao
import com.larateam.sshmanager.data.db.SecretDao
import com.larateam.sshmanager.data.db.toDomain
import com.larateam.sshmanager.data.db.toEntity
import com.larateam.sshmanager.data.model.Connection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for saved connections. Exposes domain [Connection]s as a [Flow];
 * the UI never touches Room entities directly.
 */
@Singleton
class ConnectionRepository @Inject constructor(
    private val dao: ConnectionDao,
    private val secretDao: SecretDao,
) {
    val connections: Flow<List<Connection>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun getConnection(id: Long): Connection? = dao.getById(id)?.toDomain()

    /** Inserts when [Connection.id] is 0, otherwise updates. Returns the row id. */
    suspend fun save(connection: Connection): Long =
        if (connection.id == 0L) {
            dao.insert(connection.toEntity())
        } else {
            dao.update(connection.toEntity())
            connection.id
        }

    /** Deletes the connection and any encrypted secrets it owns (stored key + saved password). */
    suspend fun delete(connection: Connection) {
        dao.delete(connection.toEntity())
        connection.keyAlias?.let { secretDao.deleteByRef(it) }
        secretDao.deleteByRef(SecretRepository.passwordRef(connection.id))
    }
}
