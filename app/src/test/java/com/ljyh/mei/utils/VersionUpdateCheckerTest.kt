package com.ljyh.mei.utils

import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class VersionUpdateCheckerTest {
    @Test
    fun stableChecksKeepTagOrderingAndIgnoreUnrelatedTags() = runBlocking {
        val requests = mutableListOf<String>()
        val tags = JsonParser.parseString(
            """[{"name":"unrelated"},{"name":"v1.54.7-beta.1"},{"name":"v1.54.7"},{"name":"1.54.6"}]""",
        )
        val result = VersionUpdateChecker.checkWithFetcher("1.54.6", UpdateSource.GitHub) { url ->
            requests += url
            tags
        } as VersionUpdateResult.UpdateAvailable

        assertEquals("v1.54.7", result.latestTag)
        assertEquals("https://github.com/Alittlejelly/MeiloB/releases/tag/v1.54.7", result.releaseUrl)
        assertEquals(listOf("https://api.github.com/repos/Alittlejelly/MeiloB/tags?per_page=100"), requests)
        assertEquals(
            VersionUpdateResult.UpToDate,
            VersionUpdateChecker.checkWithFetcher("1.54.7", UpdateSource.GitHub) { tags },
        )
        // A pre-release build is older than the matching official release tag.
        assertEquals(
            VersionUpdateResult.UpdateAvailable(
                latestTag = "v1.54.7",
                releaseUrl = "https://github.com/Alittlejelly/MeiloB/releases/tag/v1.54.7",
            ),
            VersionUpdateChecker.checkWithFetcher("1.54.7-beta.2", UpdateSource.GitHub) { tags },
        )
    }

    @Test
    fun giteeSourceUsesGiteeApiAndTagPageUrls() = runBlocking {
        val requests = mutableListOf<String>()
        val tags = JsonParser.parseString("""[{"name":"1.0.0"},{"name":"1.0.1"}]""")
        val result = VersionUpdateChecker.checkWithFetcher("1.0.0", UpdateSource.Gitee) { url ->
            requests += url
            tags
        } as VersionUpdateResult.UpdateAvailable

        assertEquals("1.0.1", result.latestTag)
        assertEquals("https://gitee.com/littlejelly/MeiloB/tags/1.0.1", result.releaseUrl)
        assertEquals(listOf("https://gitee.com/api/v5/repos/littlejelly/MeiloB/tags?per_page=100"), requests)
    }

    @Test
    fun malformedResponsesAndHttpFailuresBecomeFailed() = runBlocking {
        assertEquals(
            VersionUpdateResult.Failed,
            VersionUpdateChecker.checkWithFetcher("1.54.6", UpdateSource.GitHub) {
                JsonParser.parseString("null")
            },
        )
        assertEquals(
            VersionUpdateResult.Failed,
            VersionUpdateChecker.checkWithFetcher("1.54.6", UpdateSource.GitHub) {
                throw java.io.IOException("HTTP 403")
            },
        )
    }

    @Test
    fun cancellationPropagatesToTheCaller() = runBlocking {
        val cancellation = CancellationException("Update check cancelled")
        try {
            VersionUpdateChecker.checkWithFetcher("1.54.6", UpdateSource.GitHub) { throw cancellation }
            fail("Cancellation must not become an update failure")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    @Test
    fun unparsableCurrentVersionBecomesFailed() = runBlocking {
        assertEquals(
            VersionUpdateResult.Failed,
            VersionUpdateChecker.checkWithFetcher("not-a-version", UpdateSource.GitHub) {
                throw AssertionError("must not request tags for an unparsable version")
            },
        )
    }
}
