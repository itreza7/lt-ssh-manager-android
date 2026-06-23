package com.larateam.sshmanager.dashboard

/** A single mounted filesystem's usage. */
data class DiskUsage(
    val mount: String,
    val filesystem: String,
    val totalBytes: Long,
    val usedBytes: Long,
    val usedPercent: Int,
)

/** 1 / 5 / 15-minute load averages. */
data class LoadAvg(val one: Double, val five: Double, val fifteen: Double)

/**
 * Parsed host vitals. Every field is optional: a non-Linux host, a missing /proc file, or
 * unexpected output yields null / empty here rather than a crash (graceful degradation).
 */
data class DashboardVitals(
    val hostname: String? = null,
    val os: String? = null,
    val kernel: String? = null,
    val uptimeSeconds: Long? = null,
    val load: LoadAvg? = null,
    val cpuBusyPercent: Int? = null,
    val memTotalBytes: Long? = null,
    val memUsedBytes: Long? = null,
    val memUsedPercent: Int? = null,
    val disks: List<DiskUsage> = emptyList(),
)

/** A tmux session as reported by `tmux ls`. */
data class TmuxSession(
    val name: String,
    val windows: Int?,
    val attached: Boolean,
)
