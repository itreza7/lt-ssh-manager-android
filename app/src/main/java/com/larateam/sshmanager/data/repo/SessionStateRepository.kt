package com.larateam.sshmanager.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.larateam.sshmanager.session.PersistedSessions
import com.larateam.sshmanager.session.PersistedView
import com.larateam.sshmanager.session.SessionStateCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the set of open views (terminals / dashboards / SFTP) as METADATA only (§4 — no passwords,
 * passphrases, or key bytes). Backed by the preferences DataStore via the pure [SessionStateCodec].
 */
@Singleton
class SessionStateRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val sessions: Flow<PersistedSessions> = dataStore.data.map { SessionStateCodec.decode(it[KEY].orEmpty()) }

    suspend fun load(): PersistedSessions = sessions.first()

    suspend fun upsert(view: PersistedView) = mutate { it.filter { v -> v.viewId != view.viewId } + view }

    suspend fun remove(viewId: Long) = mutate { it.filter { v -> v.viewId != viewId } }

    suspend fun setSelected(viewId: Long) {
        dataStore.edit { p ->
            val cur = SessionStateCodec.decode(p[KEY].orEmpty())
            p[KEY] = SessionStateCodec.encode(cur.copy(selectedViewId = viewId))
        }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    private suspend fun mutate(transform: (List<PersistedView>) -> List<PersistedView>) {
        dataStore.edit { p ->
            val cur = SessionStateCodec.decode(p[KEY].orEmpty())
            p[KEY] = SessionStateCodec.encode(cur.copy(views = transform(cur.views)))
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("open_sessions_v1")
    }
}
