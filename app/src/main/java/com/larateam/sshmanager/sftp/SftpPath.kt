package com.larateam.sshmanager.sftp

/**
 * Pure POSIX-style remote path utilities. No assumptions about the home path; spaces / unicode in
 * names are preserved verbatim (they are valid path characters).
 */
object SftpPath {

    /** Collapse `//`, drop `.`, resolve `..` (clamped at root for absolute paths). */
    fun normalize(path: String): String {
        if (path.isEmpty()) return "."
        val absolute = path.startsWith("/")
        val stack = ArrayDeque<String>()
        for (seg in path.split('/')) {
            when (seg) {
                "", "." -> {}
                ".." -> {
                    if (stack.isNotEmpty() && stack.last() != "..") stack.removeLast()
                    else if (!absolute) stack.addLast("..")
                    // for absolute paths, ".." at root is a no-op (cannot escape "/")
                }
                else -> stack.addLast(seg)
            }
        }
        val joined = stack.joinToString("/")
        return if (absolute) "/$joined" else joined.ifEmpty { "." }
    }

    /** Resolve [name] against [base] (an absolute name replaces base). */
    fun join(base: String, name: String): String {
        if (name.startsWith("/")) return normalize(name)
        val b = if (base.endsWith("/")) base else "$base/"
        return normalize(b + name)
    }

    /** Parent directory, clamped at "/". */
    fun parent(path: String): String {
        val n = normalize(path)
        if (n == "/" || n == ".") return if (n == ".") "." else "/"
        val trimmed = n.trimEnd('/')
        val idx = trimmed.lastIndexOf('/')
        return when {
            idx < 0 -> "."
            idx == 0 -> "/"
            else -> trimmed.substring(0, idx)
        }
    }

    /** Final path segment ("/" for root). */
    fun name(path: String): String {
        val n = normalize(path).trimEnd('/')
        return if (n.isEmpty() || n == "/") "/" else n.substringAfterLast('/')
    }
}
