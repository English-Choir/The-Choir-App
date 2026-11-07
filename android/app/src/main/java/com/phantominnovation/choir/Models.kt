package com.phantominnovation.choir

import java.net.URI
import java.text.Normalizer
import java.util.Locale
import java.security.MessageDigest

data class ChoirSection(val id: String, val title: String, val other: Boolean)
data class DriveLink(val title: String, val url: String)
data class Registration(
    val id: String,
    val sectionId: String,
    val name: String,
    val style: String,
    val tempo: Int,
    val transpose: Int,
    val links: List<DriveLink>,
) {
    val transposeLabel: String get() = if (transpose > 0) "+$transpose" else transpose.toString()
}
data class SectionSetting(val title: String, val removed: Boolean = false, val moveTo: String = "", val revision: Long = 0)
data class Catalog(val sections: List<ChoirSection>, val registrations: List<Registration>,
    val sectionSettings: Map<String, SectionSetting> = emptyMap())

fun resolveSection(id: String, settings: Map<String, SectionSetting>): String {
    var result = id
    val seen = mutableSetOf<String>()
    while (settings[result]?.removed == true) {
        require(seen.add(result)) { "Section moves contain a loop." }
        result = settings.getValue(result).moveTo
    }
    return result
}

fun Catalog.withSectionSettings(settings: Map<String, SectionSetting>): Catalog = copy(
    sections = sections.filterNot { settings[it.id]?.removed == true }
        .map { it.copy(title = settings[it.id]?.title ?: it.title) },
    registrations = registrations.map { it.copy(sectionId = resolveSection(it.sectionId, settings)) },
    sectionSettings = settings,
)

fun renamedSection(section: ChoirSection, title: String, sections: List<ChoirSection>): ChoirSection {
    require(section.other) { "Only sections under Other registrations can be changed." }
    return section.copy(title = newOtherSection(title, sections.filterNot { it.id == section.id }).title)
}
enum class LinkFilter(val title: String) { ALL("All"), LINKED("With links"), MISSING("Needs link") }
enum class ChordFilter(val title: String) { ALL("All hymns"), AVAILABLE("With Offline Chords"), MISSING("Without Offline Chords") }

fun newOtherSection(title: String, sections: List<ChoirSection>): ChoirSection {
    val clean = title.trim().replace(Regex("\\s+"), " ")
    require(clean.isNotBlank() && clean.length <= 60) { "Enter a section name up to 60 characters." }
    require(sections.none { it.title.trim().replace(Regex("\\s+"), " ").equals(clean, ignoreCase = true) }) {
        "A section with this name already exists."
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(clean.lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return ChoirSection("section-$digest", clean, true)
}

data class RegistrationInput(
    val name: String = "", val sectionId: String = "entrance", val style: String = "",
    val tempo: String = "120", val transpose: String = "0", val links: List<DriveLink> = emptyList(),
) {
    fun toRegistration(id: String, catalog: Catalog): Registration {
        val cleanName = name.trim()
        val cleanStyle = style.trim()
        require(cleanName.isNotEmpty() && cleanName.length <= 150) { "Enter a registration name (up to 150 characters)." }
        require(catalog.sections.any { it.id == sectionId }) { "Choose a section." }
        require(cleanStyle.isNotEmpty() && cleanStyle.length <= 150) { "Enter a style (up to 150 characters)." }
        val bpm = tempo.trim().toIntOrNull()
        require(bpm != null && bpm in 5..500) { "Tempo must be a whole number from 5 to 500 BPM." }
        val semitones = transpose.trim().toIntOrNull()
        require(semitones != null && semitones in -12..12) { "Transpose must be a whole number from -12 to +12." }
        require(catalog.registrations.none { it.id != id && it.sectionId == sectionId && it.name.trim().equals(cleanName, ignoreCase = true) }) {
            "A registration with this name already exists in that section."
        }
        val files = links.filterNot { it.title.isBlank() && it.url.isBlank() }.map {
            require(it.title.trim().length <= 150 && it.url.trim().length <= 2048) { "Use a file name up to 150 characters and a link up to 2,048 characters." }
            val url = validDriveUrl(it.url)
            require(url != null) { "Every Drive file needs a valid HTTPS Google Drive or Google Docs link." }
            DriveLink(it.title.trim().ifEmpty { cleanName }, url)
        }
        require(files.size <= 10) { "Attach up to 10 Drive files to a registration." }
        require(files.map { it.url }.distinct().size == files.size) { "The same Drive link is attached more than once." }
        return Registration(id, sectionId, cleanName, cleanStyle, bpm, semitones, files)
    }

    companion object {
        fun from(row: Registration) = RegistrationInput(row.name, row.sectionId, row.style, row.tempo.toString(), row.transpose.toString(), row.links)
    }
}

/** Accept file/document and shared-folder links; never launch arbitrary schemes or lookalike hosts. */
fun validDriveUrl(input: String): String? {
    val value = input.trim()
    return runCatching {
        val uri = URI(value)
        val host = uri.host?.lowercase(Locale.ROOT)
        value.takeIf {
            uri.scheme.equals("https", ignoreCase = true) &&
                host in setOf("drive.google.com", "docs.google.com") &&
                uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
                !uri.path.isNullOrBlank() && uri.path != "/"
        }
    }.getOrNull()
}

private fun String.searchKey() = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

fun filterRegistrations(
    registrations: List<Registration>, query: String, filter: LinkFilter,
    sectionTitles: Map<String, String>, chords: ChordState = ChordState(), chordFilter: ChordFilter = ChordFilter.ALL,
): List<Registration> {
    val terms = query.searchKey().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return registrations.filter { registration ->
        val linkMatches = when (filter) {
            LinkFilter.ALL -> true
            LinkFilter.LINKED -> registration.links.isNotEmpty()
            LinkFilter.MISSING -> registration.links.isEmpty()
        }
        val haystack = "${registration.name} ${registration.style} ${sectionTitles[registration.sectionId].orEmpty()}".searchKey()
        val chordMatches = when (chordFilter) {
            ChordFilter.ALL -> true
            ChordFilter.AVAILABLE -> chords.available(registration)
            ChordFilter.MISSING -> !chords.available(registration)
        }
        linkMatches && chordMatches && terms.all { it in haystack }
    }
}
