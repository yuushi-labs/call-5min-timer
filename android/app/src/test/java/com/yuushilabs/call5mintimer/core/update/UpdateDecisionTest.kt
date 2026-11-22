package com.yuushilabs.call5mintimer.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateDecisionTest {

    private fun release(
        tag: String,
        body: String = "",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: List<GithubReleaseAsset> = listOf(
            GithubReleaseAsset(
                name = "call-5min-timer-$tag.apk",
                downloadUrl = "https://github.com/yuushi-labs/call-5min-timer/releases/download/$tag/app.apk",
                sizeBytes = 3_000_000,
            ),
        ),
    ) = GithubRelease(
        tagName = tag,
        body = body,
        draft = draft,
        prerelease = prerelease,
        htmlUrl = "https://github.com/yuushi-labs/call-5min-timer/releases/tag/$tag",
        assets = assets,
    )

    @Test
    fun `newer tag is offered`() {
        val info = UpdateDecision.decide("0.1.0", 1, release("v0.2.0", body = "versionCode: 2"))
        assertNotNull(info)
        assertEquals("0.2.0", info?.versionName)
        assertEquals(2, info?.versionCode)
    }

    @Test
    fun `same tag is not offered`() {
        assertNull(UpdateDecision.decide("0.1.0", 1, release("v0.1.0", body = "versionCode: 1")))
    }

    @Test
    fun `same tag with a higher version code is offered`() {
        val info = UpdateDecision.decide("0.1.0", 1, release("v0.1.0", body = "versionCode: 2"))
        assertNotNull(info)
    }

    @Test
    fun `same tag with an older version code is not offered`() {
        assertNull(UpdateDecision.decide("0.1.0", 3, release("v0.1.0", body = "versionCode: 2")))
    }

    @Test
    fun `older tag is not offered`() {
        assertNull(UpdateDecision.decide("0.2.0", 2, release("v0.1.9")))
    }

    @Test
    fun `multi digit components compare numerically`() {
        assertEquals(1, UpdateDecision.compareVersions("0.10.0", "0.9.9"))
        assertEquals(-1, UpdateDecision.compareVersions("0.9.9", "0.10.0"))
        assertEquals(0, UpdateDecision.compareVersions("v1.0", "1.0.0"))
    }

    @Test
    fun `draft and prerelease are ignored`() {
        assertNull(UpdateDecision.decide("0.1.0", 1, release("v0.2.0", draft = true)))
        assertNull(UpdateDecision.decide("0.1.0", 1, release("v0.2.0", prerelease = true)))
    }

    @Test
    fun `release without an apk asset is ignored`() {
        val noApk = listOf(
            GithubReleaseAsset(name = "notes.txt", downloadUrl = "https://example.com/notes.txt"),
        )
        assertNull(UpdateDecision.decide("0.1.0", 1, release("v0.2.0", assets = noApk)))
    }

    @Test
    fun `release asset must be https`() {
        val insecure = listOf(
            GithubReleaseAsset(name = "app.apk", downloadUrl = "http://example.com/app.apk"),
        )
        assertNull(UpdateDecision.decide("0.1.0", 1, release("v0.2.0", assets = insecure)))
    }

    @Test
    fun `release json from github parses with unknown fields`() {
        val json = """
            {
              "tag_name": "v0.2.0",
              "name": "v0.2.0",
              "body": "versionCode: 2\n- 修正",
              "draft": false,
              "prerelease": false,
              "html_url": "https://example.com",
              "published_at": "2026-11-22T00:00:00Z",
              "unknown_field": {"nested": true},
              "assets": [
                {"name": "call-5min-timer-v0.2.0.apk", "browser_download_url": "https://example.com/a.apk", "size": 123, "extra": 1}
              ]
            }
        """.trimIndent()
        val parsed = GithubRelease.parse(json)
        assertEquals("v0.2.0", parsed.tagName)
        assertEquals(1, parsed.assets.size)
        assertEquals(123L, parsed.assets.first().sizeBytes)
        assertNotNull(UpdateDecision.decide("0.1.0", 1, parsed))
    }
}
