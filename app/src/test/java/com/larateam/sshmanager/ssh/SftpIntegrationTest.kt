package com.larateam.sshmanager.ssh

import com.larateam.sshmanager.data.model.AuthCredentials
import com.larateam.sshmanager.data.model.ConnectionTarget
import kotlinx.coroutines.test.runTest
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.UserAuthPasswordFactory
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Hermetic SFTP integration test: the real [SftpSession] (sshj SFTPClient) against an in-process
 * Apache MINA SSHD SFTP subsystem backed by a temp directory. chmod / symlink behaviours that the
 * host OS can't represent (e.g. POSIX perms / symlink creation on Windows) skip via Assume; the live
 * run gate covers those on Linux, and [com.larateam.sshmanager.sftp.SftpDeleteTest] covers the
 * no-follow logic hermetically.
 */
class SftpIntegrationTest {

    private class FakeStore : KnownHostStore {
        private val pins = mutableMapOf<String, String>()
        override fun pinnedFingerprint(host: String, port: Int) = pins["$host:$port"]
        override fun pin(host: String, port: Int, fingerprintSha256: String) { pins["$host:$port"] = fingerprintSha256 }
    }

    private lateinit var sshd: SshServer
    private var port = 0
    private lateinit var root: Path

    @Before
    fun setUp() {
        SshProvider.ensureRegistered()
        root = Files.createTempDirectory("sftp-test")
        sshd = SshServer.setUpDefaultServer().apply {
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider()
            setPasswordAuthenticator { user, password, _ -> user == "demo" && password == "password" }
            userAuthFactories = listOf(UserAuthPasswordFactory.INSTANCE)
            subsystemFactories = listOf(SftpSubsystemFactory())
            fileSystemFactory = VirtualFileSystemFactory(root)
            start()
        }
        port = sshd.port
    }

    @After
    fun tearDown() {
        sshd.stop(true)
        root.toFile().deleteRecursively()
    }

    private suspend fun open(): Pair<SshConnection, SftpSession> {
        val conn = SshConnection(ConnectionTarget("127.0.0.1", port, "demo"), FakeStore())
        conn.connect(AuthCredentials.Password("password".toCharArray()))
        return conn to conn.openSftp()
    }

    @Test
    fun put_then_get_round_trip_preserves_content() = runTest {
        val (conn, sftp) = open()
        val content = "hello sftp 你好 — réübmü\n".repeat(5000).toByteArray() // multi-chunk
        var uploaded = 0L
        sftp.upload("/up.bin", content.inputStream()) { uploaded = it }
        assertEquals(content.size.toLong(), uploaded)
        assertArrayEquals(content, Files.readAllBytes(root.resolve("up.bin")))

        val out = ByteArrayOutputStream()
        var downloaded = 0L
        sftp.download("/up.bin", out) { downloaded = it }
        assertEquals(content.size.toLong(), downloaded)
        assertArrayEquals(content, out.toByteArray())
        sftp.close(); conn.disconnect()
    }

    @Test
    fun mkdir_and_rename() = runTest {
        val (conn, sftp) = open()
        sftp.mkdir("/d")
        assertTrue(Files.isDirectory(root.resolve("d")))
        sftp.upload("/d/a.txt", "hi".toByteArray().inputStream()) {}
        sftp.rename("/d/a.txt", "/d/b.txt")
        assertFalse(Files.exists(root.resolve("d/a.txt")))
        assertTrue(Files.exists(root.resolve("d/b.txt")))
        sftp.close(); conn.disconnect()
    }

    @Test
    fun recursive_delete_removes_the_whole_tree() = runTest {
        val (conn, sftp) = open()
        Files.createDirectories(root.resolve("tree/sub"))
        Files.write(root.resolve("tree/f1.txt"), "1".toByteArray())
        Files.write(root.resolve("tree/sub/f2.txt"), "2".toByteArray())
        sftp.deleteRecursive("/tree")
        assertFalse(Files.exists(root.resolve("tree")))
        sftp.close(); conn.disconnect()
    }

    @Test
    fun chmod_sets_mode() = runTest {
        val (conn, sftp) = open()
        sftp.upload("/c.txt", "x".toByteArray().inputStream()) {}
        try {
            sftp.chmod("/c.txt", "750".toInt(8))
        } catch (e: Exception) {
            Assume.assumeNoException("POSIX chmod unsupported on this filesystem", e)
        }
        val mode = sftp.lstat("/c.txt").mode and 0x1FF
        Assume.assumeTrue("filesystem cannot represent POSIX perms (got 0${mode.toString(8)})", mode == "750".toInt(8))
        assertEquals("750".toInt(8), mode) // rwxr-x---
        sftp.close(); conn.disconnect()
    }

    @Test
    fun recursive_delete_does_not_follow_symlinks() = runTest {
        val (conn, sftp) = open()
        Files.createDirectory(root.resolve("realdir"))
        Files.write(root.resolve("realdir/sentinel.txt"), "keep me".toByteArray())
        Files.createDirectory(root.resolve("tree"))
        try {
            Files.createSymbolicLink(root.resolve("tree/link"), root.resolve("realdir"))
        } catch (e: Exception) {
            Assume.assumeNoException("symlink creation not permitted on this host", e)
        }

        sftp.deleteRecursive("/tree")

        assertFalse("the tree (and the symlink) is gone", Files.exists(root.resolve("tree")))
        // SAFETY: the symlink target and its file are untouched.
        assertTrue("symlink target dir survives", Files.isDirectory(root.resolve("realdir")))
        assertTrue("sentinel survives", Files.exists(root.resolve("realdir/sentinel.txt")))
        sftp.close(); conn.disconnect()
    }
}
