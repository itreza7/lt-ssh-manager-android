package com.larateam.sshmanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larateam.sshmanager.data.db.KnownHostEntity
import com.larateam.sshmanager.data.model.AppSettings
import com.larateam.sshmanager.data.model.AppTheme
import com.larateam.sshmanager.data.repo.KnownHostsRepository
import com.larateam.sshmanager.data.repo.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val knownHosts: KnownHostsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val pinnedHosts: StateFlow<List<KnownHostEntity>> =
        knownHosts.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTheme(theme: AppTheme) = viewModelScope.launch { settingsRepo.setTheme(theme) }

    fun setTerminalFontSize(sp: Int) = viewModelScope.launch { settingsRepo.setTerminalFontSize(sp) }

    /** Persist the user-edited extra-keys layout (validated/parsed lazily where it's rendered). */
    fun setTerminalKeys(layout: String) = viewModelScope.launch { settingsRepo.setTerminalKeys(layout) }

    /** Forget a legitimately-rotated host so the next connect re-TOFUs (distinct from the in-session block). */
    fun forgetHost(hostPort: String) = viewModelScope.launch { knownHosts.forgetByKey(hostPort) }

    /** Record that the battery exemption was handled/declined so the contextual prompt never nags again. */
    fun markBatteryPromptDismissed() = viewModelScope.launch { settingsRepo.setBatteryPromptDismissed(true) }
}
