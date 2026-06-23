package com.larateam.sshmanager.dashboard

import kotlin.math.roundToInt

/**
 * PURE parser: probe stdout -> [DashboardVitals]. Every field is best-effort — a missing section,
 * a non-Linux host, or unexpected output leaves that field null/omitted instead of throwing.
 */
object VitalsParser {

    private val WS = Regex("\\s+")

    fun parse(probeStdout: String): DashboardVitals {
        val s = splitSections(probeStdout)
        val mem = parseMem(s["MEMINFO"])
        return DashboardVitals(
            hostname = s["HOSTNAME"].firstNonBlank(),
            os = s["OS"].firstNonBlank()?.takeIf { it.isNotBlank() },
            kernel = s["KERNEL"].firstNonBlank(),
            uptimeSeconds = parseUptime(s["UPTIME"]),
            load = parseLoad(s["LOADAVG"]),
            cpuBusyPercent = parseCpu(s["STAT1"], s["STAT2"]),
            memTotalBytes = mem?.first,
            memUsedBytes = mem?.second,
            memUsedPercent = mem?.third,
            disks = parseDisks(s["DISK"]),
        )
    }

    private fun splitSections(text: String): Map<String, List<String>> {
        val map = LinkedHashMap<String, MutableList<String>>()
        var current: MutableList<String>? = null
        for (line in text.lines()) {
            if (line.startsWith(DashboardProbe.MARK)) {
                current = mutableListOf()
                map[line.removePrefix(DashboardProbe.MARK).trim()] = current
            } else {
                current?.add(line)
            }
        }
        return map
    }

    private fun List<String>?.firstNonBlank(): String? = this?.firstOrNull { it.isNotBlank() }?.trim()

    private fun parseUptime(lines: List<String>?): Long? =
        lines.firstNonBlank()?.split(WS)?.firstOrNull()?.toDoubleOrNull()?.toLong()

    private fun parseLoad(lines: List<String>?): LoadAvg? {
        val parts = lines.firstNonBlank()?.split(WS) ?: return null
        if (parts.size < 3) return null
        val a = parts[0].toDoubleOrNull() ?: return null
        val b = parts[1].toDoubleOrNull() ?: return null
        val c = parts[2].toDoubleOrNull() ?: return null
        return LoadAvg(a, b, c)
    }

    private fun parseCpu(s1: List<String>?, s2: List<String>?): Int? {
        val a = cpuTotals(s1?.firstOrNull { it.startsWith("cpu") }) ?: return null
        val b = cpuTotals(s2?.firstOrNull { it.startsWith("cpu") }) ?: return null
        val totalDelta = b.first - a.first
        val idleDelta = b.second - a.second
        if (totalDelta <= 0L) return null
        val busy = (totalDelta - idleDelta).toDouble() / totalDelta.toDouble() * 100.0
        return busy.coerceIn(0.0, 100.0).roundToInt()
    }

    /** @return (total jiffies, idle+iowait jiffies) for an aggregate "cpu ..." line. */
    private fun cpuTotals(line: String?): Pair<Long, Long>? {
        if (line == null) return null
        val nums = line.trim().split(WS).drop(1).mapNotNull { it.toLongOrNull() }
        if (nums.size < 5) return null
        return nums.sum() to (nums[3] + nums[4])
    }

    /** @return (totalBytes, usedBytes, usedPercent) from MemTotal/MemAvailable (kB) lines. */
    private fun parseMem(lines: List<String>?): Triple<Long, Long, Int>? {
        if (lines == null) return null
        var totalKb: Long? = null
        var availKb: Long? = null
        for (line in lines) {
            val parts = line.trim().split(WS)
            if (parts.size < 2) continue
            val kb = parts[1].toLongOrNull() ?: continue
            when {
                parts[0].startsWith("MemTotal") -> totalKb = kb
                parts[0].startsWith("MemAvailable") -> availKb = kb
            }
        }
        val total = (totalKb ?: return null) * 1024
        val avail = (availKb ?: return null) * 1024
        if (total <= 0) return null
        val used = (total - avail).coerceAtLeast(0)
        return Triple(total, used, (used.toDouble() / total.toDouble() * 100.0).roundToInt())
    }

    private val PSEUDO_FS = setOf(
        "tmpfs", "devtmpfs", "devpts", "shm", "mqueue", "proc", "sysfs",
        "cgroup", "cgroup2", "ramfs", "tracefs", "debugfs", "securityfs", "none", "udev",
    )

    private fun parseDisks(lines: List<String>?): List<DiskUsage> {
        if (lines == null) return emptyList()
        val out = mutableListOf<DiskUsage>()
        for (line in lines) {
            if (line.isBlank() || line.startsWith("Filesystem")) continue
            val parts = line.trim().split(WS)
            if (parts.size < 6) continue
            val fs = parts[0]
            val total = parts[1].toLongOrNull() ?: continue
            val used = parts[2].toLongOrNull() ?: continue
            val mount = parts.subList(5, parts.size).joinToString(" ")
            // Keep "/" always; otherwise drop pseudo filesystems and kernel mount points.
            val pseudo = fs in PSEUDO_FS ||
                mount.startsWith("/proc") || mount.startsWith("/sys") ||
                mount.startsWith("/dev") || mount.startsWith("/run")
            if (mount != "/" && pseudo) continue
            val capacity = parts[4].removeSuffix("%").toIntOrNull()
                ?: if (total > 0) (used.toDouble() / total.toDouble() * 100.0).roundToInt() else 0
            out.add(DiskUsage(mount, fs, total, used, capacity))
        }
        return out
    }
}
