package com.larateam.sshmanager.data.repo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.larateam.sshmanager.data.model.AppSettings
import com.larateam.sshmanager.data.model.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * DataStore round-trip. Each repo uses a FRESH single-write store: DataStore's atomic write does a
 * .tmp→file rename, which on the Windows JVM can't replace an already-existing target — so each case
 * writes once to a new file (the production code only ever has one instance per file anyway).
 */
class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun freshRepo(): SettingsRepository =
        SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.newFolder(), "settings.preferences_pb") })

    @Test
    fun defaults_when_unset() = runBlocking {
        freshRepo().current().let {
            assertEquals(AppTheme.SYSTEM, it.theme)
            assertEquals(AppSettings.DEFAULT_TERMINAL_FONT_SP, it.terminalFontSizeSp)
            assertFalse(it.batteryPromptDismissed)
        }
        scope.cancel()
    }

    @Test
    fun theme_round_trips() = runBlocking {
        val repo = freshRepo()
        repo.setTheme(AppTheme.DARK)
        assertEquals(AppTheme.DARK, repo.current().theme)
        scope.cancel()
    }

    @Test
    fun font_round_trips() = runBlocking {
        val repo = freshRepo()
        repo.setTerminalFontSize(20)
        assertEquals(20, repo.current().terminalFontSizeSp)
        scope.cancel()
    }

    @Test
    fun font_out_of_range_is_clamped() = runBlocking {
        val repo = freshRepo()
        repo.setTerminalFontSize(999)
        assertEquals(AppSettings.MAX_TERMINAL_FONT_SP, repo.current().terminalFontSizeSp)
        scope.cancel()
    }

    @Test
    fun battery_dismissed_round_trips() = runBlocking {
        val repo = freshRepo()
        repo.setBatteryPromptDismissed(true)
        assertTrue(repo.current().batteryPromptDismissed)
        scope.cancel()
    }
}
