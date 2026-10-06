package com.nexora.git.core.releases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasesJsonParserTest {

    private val parser = ReleasesJsonParser()

    @Test
    fun parsesTags() {
        val tags = parser.tags(
            """
            [
              {
                "name":"v1.0.0",
                "zipball_url":"https://example/zip",
                "tarball_url":"https://example/tar",
                "commit":{
                  "sha":"abcdef123456",
                  "url":"https://api.github.com/commit"
                },
                "node_id":"TAG_1"
              }
            ]
            """.trimIndent(),
        )

        assertEquals(1, tags.size)
        assertEquals("v1.0.0", tags.single().name)
        assertEquals(
            "abcdef123456",
            tags.single().commitSha,
        )
    }

    @Test
    fun parsesReleaseDraftPrereleaseAndAssets() {
        val release = parser.release(
            """
            {
              "id":10,
              "node_id":"REL_10",
              "tag_name":"v2.0.0-beta",
              "target_commitish":"main",
              "name":"2.0 Beta",
              "body":"Notes",
              "draft":false,
              "prerelease":true,
              "immutable":false,
              "created_at":"now",
              "published_at":"later",
              "html_url":"https://github.com/example/repo/releases/tag/v2.0.0-beta",
              "upload_url":"https://uploads.github.com/repos/example/repo/releases/10/assets{?name,label}",
              "author":{"login":"ghost"},
              "assets":[
                {
                  "id":20,
                  "node_id":"ASSET_20",
                  "name":"app.apk",
                  "label":"Android",
                  "state":"uploaded",
                  "content_type":"application/vnd.android.package-archive",
                  "size":2048,
                  "download_count":42,
                  "digest":"sha256:abc",
                  "created_at":"now",
                  "updated_at":"later",
                  "browser_download_url":"https://github.com/example/repo/releases/download/v2.0.0-beta/app.apk",
                  "uploader":{"login":"ghost"}
                }
              ]
            }
            """.trimIndent(),
        )

        assertTrue(release.prerelease)
        assertFalse(release.draft)
        assertEquals("2.0 Beta", release.name)
        assertEquals(1, release.assets.size)
        assertEquals("app.apk", release.assets.single().name)
        assertEquals(42L, release.assets.single().downloadCount)
        assertEquals("sha256:abc", release.assets.single().digest)
    }

    @Test
    fun parsesCreatedLightweightTagReference() {
        val tag = parser.tagFromRef(
            """
            {
              "ref":"refs/tags/v3.0.0",
              "node_id":"REF_3",
              "object":{
                "type":"commit",
                "sha":"deadbeef1234",
                "url":"https://api.github.com/commit"
              }
            }
            """.trimIndent(),
            "v3.0.0",
        )

        assertEquals("v3.0.0", tag.name)
        assertEquals("deadbeef1234", tag.commitSha)
    }
}
