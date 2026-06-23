package com.larateam.sshmanager.data.model

/**
 * How a connection authenticates. Per CLAUDE.md §7.7 the desktop "agent" option maps to an
 * in-app key vault on Android. No secret material is associated with these values here —
 * passwords/passphrases/keys are resolved at connect time in later phases.
 */
enum class AuthMethod {
    /** Private key supplied by the user (file/content). */
    KEY,

    /** Password — requested at connect time, never persisted. */
    PASSWORD,

    /** A key held in the in-app Keystore-backed vault (the mobile "agent"). */
    IN_APP_KEY;

    companion object {
        fun fromName(name: String?): AuthMethod = entries.firstOrNull { it.name == name } ?: KEY
    }
}
