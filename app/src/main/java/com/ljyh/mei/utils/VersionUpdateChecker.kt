package com.ljyh.mei.utils

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ljyh.mei.constants.UserAgent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

sealed interface VersionUpdateResult {
    data class UpdateAvailable(
        val latestTag: String,
        val releaseUrl: String,
    ) : VersionUpdateResult

    data object UpToDate : VersionUpdateResult

    data object Failed : VersionUpdateResult
}

/** Checks the GitHub release tags off the Compose thread. */
object VersionUpdateChecker {
    private const val Repository = "Alittlejelly/MeiloB"
    private const val RepositoryUrl = "https://github.com/$Repository"
    private const val ApiUrl = "https://api.github.com/repos/$Repository"
    private const val TagsEndpoint = "$ApiUrl/tags?per_page=100"
    private const val ConnectTimeoutMillis = 8_000
    private const val ReadTimeoutMillis = 8_000

    suspend fun check(currentVersion: String): VersionUpdateResult = withContext(Dispatchers.IO) {
        withTimeoutOrNull(30_000) {
            checkWithFetcher(currentVersion, ::requestJson)
        } ?: VersionUpdateResult.Failed
    }

    internal suspend fun checkWithFetcher(
        currentVersion: String,
        fetchJson: suspend (String) -> JsonElement,
    ): VersionUpdateResult = try {
        val current = parseVersion(currentVersion) ?: return VersionUpdateResult.Failed
        val tags = fetchJson(TagsEndpoint).asJsonArray.mapNotNull { element ->
            val name = element.objectOrNull()?.string("name")?.trim() ?: return@mapNotNull null
            parseVersion(name)?.let { version -> VersionTag(name, version) }
        }
        val latest = tags.maxWithOrNull { left, right ->
            compareVersions(left.version, right.version)
        } ?: return VersionUpdateResult.Failed

        if (compareVersions(latest.version, current) > 0) {
            VersionUpdateResult.UpdateAvailable(
                latestTag = latest.name,
                releaseUrl = "$RepositoryUrl/releases/tag/${URLEncoder.encode(latest.name, "UTF-8")}",
            )
        } else {
            VersionUpdateResult.UpToDate
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        VersionUpdateResult.Failed
    }

    private suspend fun requestJson(endpoint: String): JsonElement {
        currentCoroutineContext().ensureActive()
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = ConnectTimeoutMillis
            readTimeout = ReadTimeoutMillis
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", UserAgent)
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        return try {
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) throw java.io.IOException("GitHub update request failed: HTTP $statusCode")
            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                JsonParser.parseReader(reader)
            }
            currentCoroutineContext().ensureActive()
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun JsonElement.objectOrNull(): JsonObject? = takeIf { isJsonObject }?.asJsonObject

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private data class VersionTag(
        val name: String,
        val version: ComparableVersion,
    )

    private data class ComparableVersion(
        val parts: List<Int>,
        val preRelease: Boolean,
    )

    private fun parseVersion(raw: String): ComparableVersion? {
        val normalized = raw.trim().removePrefix("refs/tags/")
        val match = VERSION_PATTERN.matchEntire(normalized) ?: return null
        val parts = match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
        return ComparableVersion(
            parts = parts,
            preRelease = match.groupValues[2].isNotEmpty(),
        )
    }

    private fun compareVersions(left: ComparableVersion, right: ComparableVersion): Int {
        val size = maxOf(left.parts.size, right.parts.size)
        for (index in 0 until size) {
            val difference = left.parts.getOrElse(index) { 0 }
                .compareTo(right.parts.getOrElse(index) { 0 })
            if (difference != 0) return difference
        }
        return when {
            left.preRelease == right.preRelease -> 0
            left.preRelease -> -1
            else -> 1
        }
    }

    private val VERSION_PATTERN = Regex(
        "^[vV]?(\\d+(?:\\.\\d+)*)(-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?$",
    )
}
