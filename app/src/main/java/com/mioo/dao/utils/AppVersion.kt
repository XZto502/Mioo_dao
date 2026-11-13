package com.mioo.dao.utils

import java.util.Locale

private val versionPattern = Regex("^(\\d+(?:\\.\\d+)*)(?:-([a-z0-9]+(?:[.-][a-z0-9]+)*))?(?:\\+[a-z0-9.-]+)?$")
private val suffixTokenPattern = Regex("[a-z]+|[0-9]+")

private data class AppVersion(val core: List<String>, val suffix: List<String>)

private fun parseVersion(raw: String): AppVersion? {
    val match = versionPattern.matchEntire(raw.trim().lowercase(Locale.ROOT).removePrefix("v")) ?: return null
    return AppVersion(match.groupValues[1].split('.'),
        suffixTokenPattern.findAll(match.groupValues[2]).map { it.value }.toList())
}

private fun compareNumber(a: String, b: String): Int {
    val left = a.trimStart('0').ifEmpty { "0" }
    val right = b.trimStart('0').ifEmpty { "0" }
    return left.length.compareTo(right.length).takeIf { it != 0 } ?: left.compareTo(right)
}

private fun stage(tokens: List<String>): Int = when (tokens.firstOrNull()) {
    "dev", "snapshot" -> -50
    "alpha" -> -40
    "beta" -> -30
    "rc" -> -10
    null -> 0
    "hotfix" -> 10
    else -> -20 // Unknown named previews remain below a stable release.
}

/** Shared ordering for startup and manual checks. Invalid labels never trigger an update. */
fun isNewerVersion(currentVersion: String, latestVersion: String): Boolean {
    val current = parseVersion(currentVersion) ?: return false
    val latest = parseVersion(latestVersion) ?: return false
    for (index in 0 until maxOf(current.core.size, latest.core.size)) {
        val comparison = compareNumber(latest.core.getOrElse(index) { "0" }, current.core.getOrElse(index) { "0" })
        if (comparison != 0) return comparison > 0
    }
    val stageComparison = stage(latest.suffix).compareTo(stage(current.suffix))
    if (stageComparison != 0) return stageComparison > 0
    for (index in 0 until minOf(current.suffix.size, latest.suffix.size)) {
        val a = latest.suffix[index]
        val b = current.suffix[index]
        val aNumeric = a.all(Char::isDigit)
        val bNumeric = b.all(Char::isDigit)
        val comparison = when {
            aNumeric && bNumeric -> compareNumber(a, b)
            aNumeric != bNumeric -> if (aNumeric) -1 else 1
            else -> a.compareTo(b)
        }
        if (comparison != 0) return comparison > 0
    }
    return latest.suffix.size > current.suffix.size
}
