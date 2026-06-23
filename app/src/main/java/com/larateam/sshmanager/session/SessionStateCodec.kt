package com.larateam.sshmanager.session

/**
 * Pure (JVM-testable, no Android/serialization-plugin deps) codec for [PersistedSessions]. A
 * tab-separated line per view with escaping; line 0 is the selected view id. Holds NO secret fields —
 * by construction there is nowhere to put a credential.
 */
object SessionStateCodec {

    fun encode(sessions: PersistedSessions): String = buildString {
        append(sessions.selectedViewId).append('\n')
        for (v in sessions.views) {
            append(v.viewId).append('\t')
                .append(v.connectionId).append('\t')
                .append(v.kind.name).append('\t')
                .append(esc(v.arg ?: ""))
                .append('\n')
        }
    }

    fun decode(text: String): PersistedSessions {
        val lines = text.split('\n').filter { it.isNotEmpty() }
        if (lines.isEmpty()) return PersistedSessions()
        val selected = lines[0].toLongOrNull() ?: -1L
        val views = lines.drop(1).mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 4) return@mapNotNull null
            val viewId = p[0].toLongOrNull() ?: return@mapNotNull null
            val connectionId = p[1].toLongOrNull() ?: return@mapNotNull null
            val kind = runCatching { ViewKind.valueOf(p[2]) }.getOrNull() ?: return@mapNotNull null
            PersistedView(viewId, connectionId, kind, unesc(p[3]).ifEmpty { null })
        }
        return PersistedSessions(views, selected)
    }

    // Escaping keeps the encoded line free of real tabs/newlines so split() is unambiguous.
    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unesc(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '\\' -> sb.append('\\')
                    't' -> sb.append('\t')
                    'n' -> sb.append('\n')
                    else -> sb.append(s[i + 1])
                }
                i += 2
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }
}
