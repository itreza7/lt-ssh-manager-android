package com.larateam.sshmanager.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VitalsParserTest {

    private val full = """
        @@SSHMGR@@HOSTNAME
        sshbox
        @@SSHMGR@@OS
        Ubuntu 24.04.1 LTS
        @@SSHMGR@@KERNEL
        6.8.0-31-generic
        @@SSHMGR@@UPTIME
        123456.78 234567.89
        @@SSHMGR@@LOADAVG
        0.15 0.10 0.05 1/234 5678
        @@SSHMGR@@STAT1
        cpu  1000 0 500 8000 100 0 0 0 0 0
        @@SSHMGR@@STAT2
        cpu  1100 0 550 8700 110 0 0 0 0 0
        @@SSHMGR@@MEMINFO
        MemTotal:        8000000 kB
        MemAvailable:    6000000 kB
        @@SSHMGR@@DISK
        Filesystem     1B-blocks         Used    Available Capacity Mounted on
        /dev/sda1   100000000000  25000000000  75000000000      25% /
        tmpfs         4000000000            0   4000000000       0% /run
        @@SSHMGR@@END
    """.trimIndent()

    @Test
    fun full_ubuntu_probe_parses_all_fields() {
        val v = VitalsParser.parse(full)
        assertEquals("sshbox", v.hostname)
        assertEquals("Ubuntu 24.04.1 LTS", v.os)
        assertEquals("6.8.0-31-generic", v.kernel)
        assertEquals(123456L, v.uptimeSeconds)
        assertEquals(LoadAvg(0.15, 0.10, 0.05), v.load)
        // busy delta = (totalΔ 860 - idleΔ 710) / 860 * 100 ≈ 17
        assertEquals(17, v.cpuBusyPercent)
        assertEquals(8000000L * 1024, v.memTotalBytes)
        assertEquals(2000000L * 1024, v.memUsedBytes) // total - available
        assertEquals(25, v.memUsedPercent)
        // tmpfs /run filtered out; real root kept
        assertEquals(1, v.disks.size)
        assertEquals("/", v.disks[0].mount)
        assertEquals(25, v.disks[0].usedPercent)
        assertEquals(100000000000L, v.disks[0].totalBytes)
    }

    private val minimal = """
        @@SSHMGR@@HOSTNAME
        minimal-host
        @@SSHMGR@@OS

        @@SSHMGR@@KERNEL
        5.10.0
        @@SSHMGR@@UPTIME
        @@SSHMGR@@LOADAVG
        @@SSHMGR@@STAT1
        @@SSHMGR@@STAT2
        @@SSHMGR@@MEMINFO
        @@SSHMGR@@DISK
        Filesystem    1B-blocks        Used   Available Capacity Mounted on
        /dev/root   50000000000 10000000000 40000000000      20% /
        @@SSHMGR@@END
    """.trimIndent()

    @Test
    fun minimal_missing_fields_degrade_gracefully_without_crash() {
        val v = VitalsParser.parse(minimal)
        assertEquals("minimal-host", v.hostname)
        assertNull(v.os)              // no os-release, no uname output -> unknown
        assertEquals("5.10.0", v.kernel)
        assertNull(v.uptimeSeconds)   // missing /proc/uptime
        assertNull(v.load)
        assertNull(v.cpuBusyPercent)  // missing /proc/stat samples
        assertNull(v.memTotalBytes)
        assertNull(v.memUsedPercent)
        assertEquals(1, v.disks.size) // root still parsed
        assertEquals("/", v.disks[0].mount)
        assertEquals(20, v.disks[0].usedPercent)
    }

    @Test
    fun garbage_input_does_not_crash() {
        val v = VitalsParser.parse("totally unrelated\noutput with no markers\n")
        assertNull(v.hostname)
        assertNull(v.cpuBusyPercent)
        assertTrue(v.disks.isEmpty())
    }
}
