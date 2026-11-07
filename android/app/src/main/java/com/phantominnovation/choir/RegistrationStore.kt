package com.phantominnovation.choir

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.UUID

/** Local edits overlay the immutable backup. This boundary must become server-enforced with Firebase. */
class RegistrationStore(
    context: Context,
    private val access: HostAccess,
    preferenceName: String = "registration_edits",
    legacyPreferenceName: String = "drive_links",
) {
    private val source = CatalogRepository(context)
    private val legacyLinks = LegacyLinkStore(context, legacyPreferenceName)
    private val preferences = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)

    private fun savedState(): JSONObject {
        val raw = preferences.getString("catalog_edits", null) ?: return JSONObject()
        // Fail visibly on damaged edits instead of silently discarding them or resurrecting deleted rows.
        return JSONObject(raw).also { require(it.getInt("version") == 1) { "Unsupported saved library version." } }
    }

    @Synchronized
    fun load(): Catalog = merge(savedState())

    private fun merge(saved: JSONObject): Catalog {
        val base = source.load()
        val added = saved.optJSONArray("sections") ?: JSONArray()
        val sections = (0 until added.length()).map { added.getJSONObject(it).let { section ->
            ChoirSection(section.getString("id"), section.getString("title"), true)
        } }
        val catalog = base.copy(sections = base.sections + sections.sortedBy { it.title.lowercase(Locale.ROOT) })
        val rows = catalog.registrations.associateByTo(linkedMapOf()) { it.id }
        rows.replaceAll { _, row -> row.copy(links = legacyLinks.linksFor(row)) }
        val overrides = saved.optJSONObject("rows") ?: JSONObject()
        overrides.keys().forEach { id -> rows[id] = overrides.getJSONObject(id).toRegistration().also { require(it.id == id) } }
        val deleted = saved.optJSONArray("deleted") ?: JSONArray()
        for (i in 0 until deleted.length()) rows.remove(deleted.getString(i))
        val order = catalog.sections.mapIndexed { index, section -> section.id to index }.toMap()
        require(rows.values.all { it.sectionId in order }) { "A saved registration has an unknown section." }
        val settings = saved.optJSONObject("sectionSettings") ?: JSONObject()
        return catalog.copy(registrations = rows.values.sortedWith(compareBy({ order.getValue(it.sectionId) }, { it.name.lowercase(Locale.ROOT) })))
            .withSectionSettings(settings.keys().asSequence().associateWith { id ->
                val value = settings.getJSONObject(id)
                SectionSetting(value.getString("title"), value.optBoolean("removed"), value.optString("moveTo"), value.optLong("revision"))
            })
    }

    private fun requireHost() {
        if (!access.session.value.canEdit) throw SecurityException("Host sign-in is required to make changes.")
    }

    private fun commit(saved: JSONObject) {
        requireHost()
        saved.put("version", 1)
        if (!preferences.edit().putString("catalog_edits", saved.toString()).commit()) {
            throw IOException("Changes couldn't be saved. Please try again.")
        }
    }

    @Synchronized
    fun addSection(title: String): Catalog {
        requireHost()
        val saved = savedState()
        val section = newOtherSection(title, merge(saved).sections)
        val sections = saved.optJSONArray("sections") ?: JSONArray()
        sections.put(JSONObject().put("id", section.id).put("title", section.title))
        saved.put("sections", sections)
        val next = merge(saved)
        commit(saved)
        return next
    }

    @Synchronized
    fun changeSection(section: ChoirSection, title: String, moveTo: String?): Catalog {
        requireHost()
        val saved = savedState()
        val catalog = merge(saved)
        require(catalog.sections.find { it.id == section.id } == section) { "This section changed. Reopen its controls." }
        val renamed = renamedSection(section, title, catalog.sections)
        require(moveTo == null || catalog.sections.any { it.id == moveTo && it.id != section.id }) { "Choose a destination section." }
        val settings = saved.optJSONObject("sectionSettings") ?: JSONObject()
        settings.put(section.id, JSONObject().put("title", renamed.title).put("removed", moveTo != null)
            .put("moveTo", moveTo.orEmpty()).put("revision", (catalog.sectionSettings[section.id]?.revision ?: 0) + 1))
        saved.put("sectionSettings", settings)
        val next = merge(saved)
        commit(saved)
        return next
    }

    @Synchronized
    fun add(input: RegistrationInput): Catalog {
        requireHost()
        val saved = savedState()
        val registration = input.toRegistration("custom-${UUID.randomUUID()}", merge(saved))
        val overrides = saved.optJSONObject("rows") ?: JSONObject()
        overrides.put(registration.id, registration.toJson())
        saved.put("rows", overrides)
        val next = merge(saved)
        commit(saved)
        return next
    }

    @Synchronized
    fun update(id: String, input: RegistrationInput): Catalog {
        requireHost()
        val saved = savedState()
        val current = merge(saved)
        require(current.registrations.any { it.id == id }) { "This registration no longer exists." }
        val registration = input.toRegistration(id, current)
        val overrides = saved.optJSONObject("rows") ?: JSONObject()
        overrides.put(id, registration.toJson())
        saved.put("rows", overrides)
        val next = merge(saved)
        commit(saved)
        return next
    }

    @Synchronized
    fun saveLinks(id: String, links: List<DriveLink>): Catalog {
        requireHost()
        val current = load().registrations.singleOrNull { it.id == id }
            ?: throw IllegalArgumentException("This registration no longer exists.")
        return update(id, RegistrationInput.from(current).copy(links = links))
    }

    @Synchronized
    fun delete(id: String): Catalog {
        requireHost()
        val saved = savedState()
        require(merge(saved).registrations.any { it.id == id }) { "This registration no longer exists." }
        val overrides = saved.optJSONObject("rows") ?: JSONObject()
        overrides.remove(id)
        saved.put("rows", overrides)
        val deleted = saved.optJSONArray("deleted") ?: JSONArray()
        deleted.put(id)
        saved.put("deleted", deleted)
        val next = merge(saved)
        commit(saved)
        return next
    }
}

private fun Registration.toJson() = JSONObject()
    .put("id", id).put("sectionId", sectionId).put("name", name).put("style", style)
    .put("tempo", tempo).put("transpose", transpose).put("links", JSONArray().apply {
        links.forEach { put(JSONObject().put("title", it.title).put("url", it.url)) }
    })

private fun JSONObject.toRegistration(): Registration {
    val files = getJSONArray("links")
    return Registration(getString("id"), getString("sectionId"), getString("name"), getString("style"),
        getInt("tempo"), getInt("transpose"), (0 until files.length()).map {
            files.getJSONObject(it).let { link -> DriveLink(link.getString("title"), requireNotNull(validDriveUrl(link.getString("url")))) }
        })
}
