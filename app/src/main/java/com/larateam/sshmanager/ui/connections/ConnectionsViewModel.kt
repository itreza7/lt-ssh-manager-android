package com.larateam.sshmanager.ui.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larateam.sshmanager.data.model.Connection
import com.larateam.sshmanager.data.repo.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ConnectionsUiState {
    data object Loading : ConnectionsUiState
    data object Empty : ConnectionsUiState
    data class Content(val connections: List<Connection>) : ConnectionsUiState
}

@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val repository: ConnectionRepository,
) : ViewModel() {

    val uiState: StateFlow<ConnectionsUiState> =
        repository.connections
            .map { list ->
                if (list.isEmpty()) ConnectionsUiState.Empty else ConnectionsUiState.Content(list)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ConnectionsUiState.Loading,
            )

    fun delete(connection: Connection) {
        viewModelScope.launch { repository.delete(connection) }
    }
}
