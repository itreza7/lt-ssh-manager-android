package com.larateam.sshmanager.ssh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks the set of active connection ids and exposes the count. Fires [onBecameActive] exactly
 * on the 0 -> 1 transition (when the foreground service must be started). Pure/synchronized so the
 * start/stop policy is unit-testable off-device.
 */
class ActiveSessionTracker(private val onBecameActive: () -> Unit = {}) {
    private val ids = LinkedHashSet<Long>()
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    @Synchronized
    fun activate(id: Long) {
        val wasEmpty = ids.isEmpty()
        val added = ids.add(id)
        _count.value = ids.size
        if (added && wasEmpty) onBecameActive()
    }

    @Synchronized
    fun deactivate(id: Long) {
        if (ids.remove(id)) _count.value = ids.size
    }

    @Synchronized
    fun clear() {
        ids.clear()
        _count.value = 0
    }
}
