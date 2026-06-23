package com.larateam.sshmanager.ssh

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveSessionTrackerTest {

    @Test
    fun first_activation_fires_onBecameActive_exactly_once() {
        var fired = 0
        val tracker = ActiveSessionTracker(onBecameActive = { fired++ })

        tracker.activate(1)
        tracker.activate(2)
        tracker.activate(1) // duplicate

        assertEquals(1, fired) // only on 0 -> 1
        assertEquals(2, tracker.count.value)
    }

    @Test
    fun count_drops_to_zero_then_reactivation_fires_again() {
        var fired = 0
        val tracker = ActiveSessionTracker(onBecameActive = { fired++ })

        tracker.activate(1)
        tracker.deactivate(1)
        assertEquals(0, tracker.count.value)

        tracker.activate(2) // 0 -> 1 again
        assertEquals(2, fired)
        assertEquals(1, tracker.count.value)
    }

    @Test
    fun clear_resets_to_zero() {
        val tracker = ActiveSessionTracker()
        tracker.activate(1); tracker.activate(2); tracker.activate(3)
        tracker.clear()
        assertEquals(0, tracker.count.value)
    }
}
