package com.larateam.sshmanager.data.model

/**
 * Non-secret connection metadata.
 *
 * Passwords, passphrases, and private-key bytes are NEVER part of this model or persisted
 * (CLAUDE.md §4). [keyAlias] is only a reference to a key that is resolved at connect time
 * in a later phase.
 */
data class Connection(
    val id: Long = 0L,
    val name: String,
    val host: String,
    val port: Int = DEFAULT_PORT,
    val username: String,
    val authMethod: AuthMethod = AuthMethod.KEY,
    val keyAlias: String? = null,
) {
    /** "user@host:port" endpoint label for the UI. */
    val endpoint: String get() = "$username@$host:$port"

    /** List title: the explicit name, or the host when unnamed. */
    val displayName: String get() = name.ifBlank { host }

    companion object {
        const val DEFAULT_PORT = 22
        const val MIN_PORT = 1
        const val MAX_PORT = 65535
    }
}
