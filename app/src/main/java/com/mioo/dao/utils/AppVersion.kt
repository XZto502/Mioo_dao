package com.mioo.dao.utils

private val channelVersionPattern = Regex("^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:-[a-z0-9]+(?:[.-][a-z0-9]+)*)?(?:\\+[a-z0-9.-]+)?$", RegexOption.IGNORE_CASE)
private val betaNumberPattern = Regex("-beta(?:[.]?(\\d+))?", RegexOption.IGNORE_CASE)

fun channelVersionCode(versionName: String): Int? {
    val raw = versionName.trim().removePrefix("v").removePrefix("V")
    val match = channelVersionPattern.matchEntire(raw) ?: return null
    val major = match.groupValues[1].toLongOrNull() ?: return null
    val minor = match.groupValues[2].ifEmpty { "0" }.toLongOrNull() ?: return null
    val patch = match.groupValues[3].ifEmpty { "0" }.toLongOrNull() ?: return null
    if (major > 2100 || minor !in 0..99 || patch !in 0..99) return null
    val beta = betaNumberPattern.find(raw)
    val suffix = if (beta == null) 99L else beta.groupValues[1].ifEmpty { "1" }.toLongOrNull()?.coerceIn(1L, 98L) ?: 98L
    val code = major * 1_000_000L + minor * 10_000L + patch * 100L + suffix
    return code.takeIf { it in 1..2_100_000_000L }?.toInt()
}

/** Shared 99-tail ordering for startup and manual update checks. */
fun isNewerVersion(currentVersion: String, latestVersion: String): Boolean {
    val local = channelVersionCode(currentVersion) ?: return false
    val remote = channelVersionCode(latestVersion) ?: return false
    return remote > local
}