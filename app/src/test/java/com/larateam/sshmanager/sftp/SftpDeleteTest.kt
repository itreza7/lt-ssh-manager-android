package com.larateam.sshmanager.sftp

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpDeleteTest {

    private class FakeFs(
        private val types: Map<String, SftpEntryType>,
        private val children: Map<String, List<String>>,
    ) : SftpDeleteOps {
        val removed = mutableListOf<String>()
        val listed = mutableListOf<String>()
        override suspend fun entryType(path: String) = types[path] ?: error("no type for $path")
        override suspend fun children(path: String): List<String> { listed += path; return children[path].orEmpty() }
        override suspend fun removeFile(path: String) { removed += path }
        override suspend fun removeDir(path: String) { removed += path }
    }

    @Test
    fun recursive_delete_removes_files_then_dirs() = runTest {
        val fs = FakeFs(
            types = mapOf(
                "/d" to SftpEntryType.DIRECTORY,
                "/d/a.txt" to SftpEntryType.FILE,
                "/d/sub" to SftpEntryType.DIRECTORY,
                "/d/sub/b.txt" to SftpEntryType.FILE,
            ),
            children = mapOf("/d" to listOf("/d/a.txt", "/d/sub"), "/d/sub" to listOf("/d/sub/b.txt")),
        )
        deleteRecursive(fs, "/d")
        assertTrue(fs.removed.containsAll(listOf("/d/a.txt", "/d/sub/b.txt", "/d/sub", "/d")))
        // children removed before their parent dir
        assertTrue(fs.removed.indexOf("/d/sub/b.txt") < fs.removed.indexOf("/d/sub"))
        assertTrue(fs.removed.indexOf("/d/sub") < fs.removed.indexOf("/d"))
    }

    @Test
    fun symlink_is_removed_as_link_and_never_traversed() {
        runTest {
            // /tree (dir) -> [ link (symlink -> /target), sub (dir -> [file]) ]
            // /target (dir) -> [ sentinel ]  -- MUST survive (never reached through the symlink)
            val fs = FakeFs(
                types = mapOf(
                    "/tree" to SftpEntryType.DIRECTORY,
                    "/tree/link" to SftpEntryType.SYMLINK,
                    "/tree/sub" to SftpEntryType.DIRECTORY,
                    "/tree/sub/file" to SftpEntryType.FILE,
                    "/target" to SftpEntryType.DIRECTORY,
                    "/target/sentinel" to SftpEntryType.FILE,
                ),
                children = mapOf(
                    "/tree" to listOf("/tree/link", "/tree/sub"),
                    "/tree/sub" to listOf("/tree/sub/file"),
                    "/tree/link" to listOf("/target/sentinel"), // would be visited ONLY if the link were followed
                    "/target" to listOf("/target/sentinel"),
                ),
            )

            deleteRecursive(fs, "/tree")

            // The symlink itself is removed, and the rest of the tree is gone.
            assertTrue("/tree/link" in fs.removed)
            assertTrue(fs.removed.containsAll(listOf("/tree/sub/file", "/tree/sub", "/tree")))
            // SAFETY: the symlink was never descended into, and the target + its file survive.
            assertFalse("symlink must not be listed/descended", "/tree/link" in fs.listed)
            assertFalse("/target" in fs.removed)
            assertFalse("/target/sentinel" in fs.removed)
        }
    }
}
