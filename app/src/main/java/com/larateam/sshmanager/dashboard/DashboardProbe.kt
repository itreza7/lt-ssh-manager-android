package com.larateam.sshmanager.dashboard

/**
 * The ONE-SHOT vitals probe: a single shell snippet (run over a single exec channel) that emits each
 * datum under a delimiter line so [VitalsParser] can split it. Everything is best-effort — every
 * command is `2>/dev/null` and missing files/commands simply yield empty sections.
 *
 * CPU% needs two /proc/stat samples ~0.3s apart; the snippet takes both within the one invocation.
 */
object DashboardProbe {
    /** Section delimiter prefix; a line equal to MARK + name starts that section. */
    const val MARK = "@@SSHMGR@@"

    val COMMAND: String = buildString {
        appendLine("echo '${MARK}HOSTNAME'; hostname 2>/dev/null")
        appendLine("echo '${MARK}OS'; (. /etc/os-release 2>/dev/null && printf '%s\\n' \"\$PRETTY_NAME\") || uname -s 2>/dev/null")
        appendLine("echo '${MARK}KERNEL'; uname -r 2>/dev/null")
        appendLine("echo '${MARK}UPTIME'; cat /proc/uptime 2>/dev/null")
        appendLine("echo '${MARK}LOADAVG'; cat /proc/loadavg 2>/dev/null")
        appendLine("echo '${MARK}STAT1'; head -n1 /proc/stat 2>/dev/null")
        appendLine("sleep 0.3")
        appendLine("echo '${MARK}STAT2'; head -n1 /proc/stat 2>/dev/null")
        appendLine("echo '${MARK}MEMINFO'; grep -E '^(MemTotal|MemAvailable):' /proc/meminfo 2>/dev/null")
        appendLine("echo '${MARK}DISK'; df -B1 -P 2>/dev/null")
        append("echo '${MARK}END'")
    }
}
