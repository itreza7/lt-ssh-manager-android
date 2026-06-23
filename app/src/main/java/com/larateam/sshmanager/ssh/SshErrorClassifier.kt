package com.larateam.sshmanager.ssh

import com.larateam.sshmanager.data.model.ConnError
import net.schmizz.sshj.userauth.UserAuthException
import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Maps a (possibly wrapped) failure to a [ConnError]. The cause chain is walked because sshj
 * wraps verifier/auth failures (e.g. a thrown [HostKeyChangedException] arrives inside a
 * TransportException).
 */
object SshErrorClassifier {

    fun classify(t: Throwable): ConnError = when {
        find<HostKeyChangedException>(t) != null -> ConnError.HOST_KEY_CHANGED
        find<UserAuthException>(t) != null -> ConnError.AUTH_FAILED
        find<MissingKeyException>(t) != null || find<FileNotFoundException>(t) != null -> ConnError.MISSING_KEY
        find<SocketTimeoutException>(t) != null -> ConnError.TIMEOUT
        find<ConnectException>(t) != null ||
            find<UnknownHostException>(t) != null ||
            find<SocketException>(t) != null -> ConnError.NETWORK
        else -> ConnError.UNKNOWN
    }

    fun isPermanent(t: Throwable): Boolean = classify(t).permanent

    private inline fun <reified E : Throwable> find(t: Throwable): E? {
        var cur: Throwable? = t
        while (cur != null) {
            if (cur is E) return cur
            if (cur.cause === cur) break
            cur = cur.cause
        }
        return null
    }
}
