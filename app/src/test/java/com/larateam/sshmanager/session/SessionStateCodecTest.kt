package com.larateam.sshmanager.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SessionStateCodecTest {

    @Test
    fun round_trips_views_and_selection() {
        val sessions = PersistedSessions(
            views = listOf(
                PersistedView(1, 100, ViewKind.TMUX, "work"),
                PersistedView(2, 100, ViewKind.SHELL, null),
                PersistedView(PersistedView.dashboardViewId(100), 100, ViewKind.DASHBOARD, null),
                PersistedView(PersistedView.sftpViewId(200), 200, ViewKind.SFTP, "/var/log/my dir"),
            ),
            selectedViewId = 2,
        )
        assertEquals(sessions, SessionStateCodec.decode(SessionStateCodec.encode(sessions)))
    }

    @Test
    fun handles_args_with_tabs_newlines_and_unicode() {
        val sessions = PersistedSessions(
            listOf(PersistedView(1, 1, ViewKind.SFTP, "/weird\tpath\nwith/файл résumé")),
            selectedViewId = 1,
        )
        assertEquals(sessions, SessionStateCodec.decode(SessionStateCodec.encode(sessions)))
    }

    @Test
    fun empty_decodes_to_empty() {
        assertEquals(PersistedSessions(), SessionStateCodec.decode(""))
    }

    @Test
    fun persisted_metadata_carries_no_credential_fields() {
        // (1) The model has nowhere to put a secret.
        val fields = PersistedView::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(
            "PersistedView must not declare any secret-bearing field",
            fields.any { it.contains("pass") || it.contains("secret") || it.contains("cred") || it.endsWith("key") },
        )
        // (2) The encoded form never contains auth material.
        val encoded = SessionStateCodec.encode(
            PersistedSessions(listOf(PersistedView(1, 1, ViewKind.SHELL, null)), 1),
        )
        assertFalse(encoded.contains("password", ignoreCase = true))
    }
}
