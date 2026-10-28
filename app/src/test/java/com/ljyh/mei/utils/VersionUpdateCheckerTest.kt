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
        val result = VersionUpdateChecker.checkWithFetcher("1.54.6") { url ->
            requests += url
            tags
        } as VersionUpdateResult.UpdateAvailable

        assertEquals("v1.54.7", result.latestTag)
        assertEquals("https://github.com/Alittlejelly/MeiloB/releases/tag/v1.54.7", result.releaseUrl)
        assertEquals(listOf("https://api.github.com/repos/Alittlejelly/MeiloB/tags?per_page=100"), requests)
        assertEquals(
            VersionUpdateResult.UpToDate,
            VersionUpdateChecker.checkWithFetcher("1.54.7") { tags },
        )
        // A pre-release build is older than the matching official release tag.
        assertEquals(
            VersionUpdateResult.UpdateAvailable(
                latestTag = "v1.54.7",
                releaseUrl = "https://github.com/Alittlejelly/MeiloB/releases/tag/v1.54.7",
            ),
            VersionUpdateChecker.checkWithFetcher("1.54.7-beta.2") { tags },
        )
    }

    @Test
    fun malformedResponsesAndHttpFailuresBecomeFailed() = runBlocking {
        assertEquals(
            VersionUpdateResult.Failed,
            VersionUpdateChecker.checkWithFetcher("1.54.6") {
                JsonParser.parseString("null")
            },
        )
        assertEquals(
            VersionUpdateResult.Failed,
            VersionUpdateChecker.checkWithFetcher("1.54.6") {
                throw java.io.IOException("HTTP 403")
            },
        )
    }

    @Test
    fun cancellationPropagatesToTheCaller() = runBlocking {
        val cancellation = CancellationException("Update check cancelled")
        try {
            VersionUpdateChecker.checkWithFetcher("1.54.6") { throw cancellation }
            fail("Cancellation must not become an update failure")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    @Test
    fun unparsableCurrentVersionBecomesFailed() = runBlocking {
        assertEquals(
            VersionUpdateResult.Failed,
            VersionUpdateChecker.checkWithFetcher("not-a-version") {
                throw AssertionError("must not request tags for an unparsable version")
            },
        )
    }
}
