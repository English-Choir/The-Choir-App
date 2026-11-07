package com.phantominnovation.choir

import java.net.URI
import java.net.URLDecoder

data class PdfSource(val link: DriveLink, val id: String?, val document: Boolean, val resourceKey: String?,
    val positions: List<Int>)

fun pdfSource(link: DriveLink, positions: List<Int>): PdfSource {
    return runCatching {
        val uri = URI(link.url)
        require(uri.scheme == "https" && uri.host in setOf("drive.google.com", "docs.google.com") && uri.userInfo == null)
        val query = uri.rawQuery.orEmpty().split("&").mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0] to URLDecoder.decode(parts[1], "UTF-8") else null
        }.toMap()
        val id = Regex("/(?:file|document)/d/([a-zA-Z0-9_-]+)").find(uri.path)?.groupValues?.get(1) ?: query["id"]
        require(id != null && Regex("[a-zA-Z0-9_-]{10,200}").matches(id))
        val key = query["resourcekey"]?.also { require(Regex("[a-zA-Z0-9_-]{1,200}").matches(it)) }
        PdfSource(link, id, uri.host == "docs.google.com" && uri.path.startsWith("/document/"), key, positions)
    }.getOrElse { PdfSource(link, null, false, null, positions) }
}

fun pdfSources(matches: List<HymnMatch>): List<PdfSource> {
    val found = linkedMapOf<String, PdfSource>()
    matches.forEach { match -> match.selected?.links?.forEach { link ->
        val source = pdfSource(link, listOf(match.request.position))
        val key = source.id ?: link.url
        val previous = found[key]
        found[key] = if (previous == null) source else previous.copy(
            positions = (previous.positions + source.positions).distinct(), resourceKey = previous.resourceKey ?: source.resourceKey)
    } }
    return found.values.toList()
}

fun pdfFilename(title: String): String = title.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().take(70)
    .ifBlank { "Hymn list" } + " - Drive chords.pdf"
