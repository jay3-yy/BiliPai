package com.android.purebilibili.feature.settings.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavBackupPolicyTest {

    @Test
    fun `normalize base url trims and removes trailing slash`() {
        assertEquals(
            "https://dav.example.com",
            normalizeWebDavBaseUrl("  https://dav.example.com/  ")
        )
    }

    @Test
    fun `normalize remote dir ensures single leading slash and no trailing slash`() {
        assertEquals("/BiliPai/backups", normalizeWebDavRemoteDir("BiliPai/backups/"))
        assertEquals("/BiliPai", normalizeWebDavRemoteDir("//BiliPai//"))
    }

    @Test
    fun `collection url should keep trailing slash`() {
        assertEquals(
            "https://dav.example.com/remote.php/dav/files/test/",
            ensureWebDavCollectionUrl("https://dav.example.com/remote.php/dav/files/test")
        )
        assertEquals(
            "https://dav.example.com/remote.php/dav/files/test/",
            ensureWebDavCollectionUrl("https://dav.example.com/remote.php/dav/files/test/")
        )
    }

    @Test
    fun `propfind body should be valid xml without escaped quotes`() {
        val body = buildWebDavPropfindBody()
        assertTrue(body.startsWith("<?xml version=\"1.0\" encoding=\"utf-8\"?>"))
        assertTrue(body.contains("<d:propfind xmlns:d=\"DAV:\">"))
        assertFalse(body.contains("\\\""))
    }

    @Test
    fun `build backup file name follows stable pattern`() {
        val fileName = buildWebDavBackupFileName(0L)
        assertTrue(fileName.startsWith("bilipai-backup-19700101-"))
        assertTrue(fileName.endsWith(".zip"))
    }

    @Test
    fun `parse propfind xml extracts backup entries`() {
        val xml = """
            <?xml version=\"1.0\" encoding=\"utf-8\"?>
            <d:multistatus xmlns:d=\"DAV:\">
              <d:response>
                <d:href>/remote.php/dav/files/user/BiliPai/backups/</d:href>
                <d:propstat><d:prop><d:getlastmodified>Mon, 22 Feb 2026 09:00:00 GMT</d:getlastmodified></d:prop></d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/user/BiliPai/backups/bilipai-backup-20260222-090000.zip</d:href>
                <d:propstat><d:prop>
                  <d:getcontentlength>2048</d:getcontentlength>
                  <d:getlastmodified>Mon, 22 Feb 2026 09:00:00 GMT</d:getlastmodified>
                </d:prop></d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/user/BiliPai/backups/bilipai-backup-20260223-100000.zip</d:href>
                <d:propstat><d:prop>
                  <d:getcontentlength>4096</d:getcontentlength>
                  <d:getlastmodified>Tue, 23 Feb 2026 10:00:00 GMT</d:getlastmodified>
                </d:prop></d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val entries = parseWebDavBackupEntries(xml)
        assertEquals(2, entries.size)
        assertEquals("bilipai-backup-20260223-100000.zip", entries[0].fileName)
        assertEquals(4096L, entries[0].sizeBytes)
    }

    @Test
    fun `select latest backup prefers last modified descending`() {
        val entries = listOf(
            WebDavBackupEntry(
                fileName = "bilipai-backup-20260222-090000.zip",
                href = "/a.zip",
                sizeBytes = 2048,
                lastModifiedEpochMs = 1000
            ),
            WebDavBackupEntry(
                fileName = "bilipai-backup-20260223-100000.zip",
                href = "/b.zip",
                sizeBytes = 4096,
                lastModifiedEpochMs = 2000
            )
        )

        val latest = selectLatestWebDavBackup(entries)
        assertNotNull(latest)
        assertEquals("bilipai-backup-20260223-100000.zip", latest?.fileName)
    }

    @Test
    fun `resolve download url should use origin when href is absolute path`() {
        val url = resolveWebDavDownloadUrl(
            baseUrl = "https://dav.example.com/remote.php/dav/files/test",
            hrefOrPath = "/remote.php/dav/files/test/BiliPai/backups/a.zip"
        )

        assertEquals(
            "https://dav.example.com/remote.php/dav/files/test/BiliPai/backups/a.zip",
            url
        )
    }

    @Test
    fun `directory action skips existing dirs and only creates missing ones`() {
        assertEquals(WebDavDirectoryAction.EXISTS, resolveWebDavDirectoryAction(200))
        assertEquals(WebDavDirectoryAction.EXISTS, resolveWebDavDirectoryAction(207))
        assertEquals(WebDavDirectoryAction.CREATE, resolveWebDavDirectoryAction(404))
        // 认证/权限错误或探测异常:回退幂等 MKCOL + 再确认
        assertEquals(WebDavDirectoryAction.CREATE_OR_VERIFY, resolveWebDavDirectoryAction(401))
        assertEquals(WebDavDirectoryAction.CREATE_OR_VERIFY, resolveWebDavDirectoryAction(403))
        assertEquals(WebDavDirectoryAction.CREATE_OR_VERIFY, resolveWebDavDirectoryAction(null))
        assertEquals(WebDavDirectoryAction.CREATE_OR_VERIFY, resolveWebDavDirectoryAction(0))
    }

    @Test
    fun `directory probe error message is actionable`() {
        assertTrue(resolveWebDavDirectoryProbeError(401, "https://dav.example.com/x").contains("认证失败"))
        assertTrue(resolveWebDavDirectoryProbeError(403, "https://dav.example.com/x").contains("无权限"))
        assertTrue(resolveWebDavDirectoryProbeError(500, "https://dav.example.com/x").contains("HTTP 500"))
    }

    @Test
    fun `retention pruning keeps newest snapshots and drops oldest beyond limit`() {
        val entries = listOf(
            WebDavBackupEntry("a.zip", "/a.zip", 100, 1000L),
            WebDavBackupEntry("b.zip", "/b.zip", 100, 3000L),
            WebDavBackupEntry("c.zip", "/c.zip", 100, 2000L),
            WebDavBackupEntry("d.zip", "/d.zip", 100, 4000L),
        )
        val pruned = selectWebDavBackupsToPrune(entries, maxRetained = 2)
        assertEquals(listOf("c.zip", "a.zip"), pruned.map { it.fileName })
        assertTrue(selectWebDavBackupsToPrune(entries, maxRetained = 0).isEmpty())
        assertTrue(selectWebDavBackupsToPrune(entries, maxRetained = 5).isEmpty())
    }
}
