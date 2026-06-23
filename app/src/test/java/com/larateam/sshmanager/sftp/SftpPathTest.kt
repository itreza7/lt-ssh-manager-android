package com.larateam.sshmanager.sftp

import org.junit.Assert.assertEquals
import org.junit.Test

class SftpPathTest {

    @Test
    fun normalize_resolves_dotdot_and_collapses_slashes() {
        assertEquals("/a/c", SftpPath.normalize("/a/b/../c"))
        assertEquals("/a/b", SftpPath.normalize("/a//b/"))
        assertEquals("/a/b", SftpPath.normalize("/a/./b"))
        assertEquals("/", SftpPath.normalize("/a/../.."))   // clamp at root
        assertEquals("/", SftpPath.normalize("/"))
        assertEquals("a/b", SftpPath.normalize("a/b"))
        assertEquals("..", SftpPath.normalize("a/../.."))   // relative may go up
    }

    @Test
    fun join_resolves_against_base() {
        assertEquals("/home/user/file", SftpPath.join("/home/user", "file"))
        assertEquals("/home/user/file", SftpPath.join("/home/user/", "file"))
        assertEquals("/etc", SftpPath.join("/home/user", "/etc"))       // absolute name replaces base
        assertEquals("/home", SftpPath.join("/home/user", ".."))
    }

    @Test
    fun spaces_and_unicode_in_names_are_preserved() {
        assertEquals("/home/a b c", SftpPath.join("/home", "a b c"))
        assertEquals("/srv/résumé .txt", SftpPath.join("/srv", "résumé .txt"))
        assertEquals("a b c", SftpPath.name("/home/a b c"))
        assertEquals("файл.txt", SftpPath.name("/данные/файл.txt"))
    }

    @Test
    fun parent_and_name() {
        assertEquals("/home", SftpPath.parent("/home/user"))
        assertEquals("/", SftpPath.parent("/home"))
        assertEquals("/", SftpPath.parent("/"))
        assertEquals("user", SftpPath.name("/home/user/"))
        assertEquals("/", SftpPath.name("/"))
    }
}
