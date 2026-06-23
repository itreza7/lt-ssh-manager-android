package com.larateam.sshmanager.dashboard

/**
 * PURE parser for `tmux ls` output. Normal lines look like:
 *   `work: 1 windows (created Thu ...) `        -> TmuxSession("work", 1, attached=false)
 *   `logs: 2 windows (created ...) (attached)`  -> attached=true
 * "no server running on ..." (usually on stderr; empty stdout) -> empty list, never a crash.
 */
object TmuxParser {
    private val LINE = Regex("^([^:]+):\\s*(\\d+)\\s+windows?\\b.*$")

    fun parse(stdout: String): List<TmuxSession> {
        val out = mutableListOf<TmuxSession>()
        for (raw in stdout.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("no server running") || line.startsWith("error")) continue
            val m = LINE.find(line)
            when {
                m != null -> out.add(
                    TmuxSession(
                        name = m.groupValues[1].trim(),
                        windows = m.groupValues[2].toIntOrNull(),
                        attached = line.contains("(attached)"),
                    ),
                )
                line.indexOf(':') > 0 -> { // unexpected format but a "name: ..." line — keep the name
                    out.add(TmuxSession(line.substringBefore(':').trim(), null, line.contains("(attached)")))
                }
            }
        }
        return out
    }
}
