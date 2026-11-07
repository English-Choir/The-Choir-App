package com.phantominnovation.choir

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class CatalogRepository(private val context: Context) {
    fun load(): Catalog {
        val json = context.assets.open("registrations.json").bufferedReader().use { JSONObject(it.readText()) }
        val sections = json.getJSONArray("sections").objects().map {
            ChoirSection(it.getString("id"), it.getString("title"), it.getBoolean("other"))
        }
        val registrations = json.getJSONArray("registrations").objects().map {
            Registration(
                it.getString("id"), it.getString("sectionId"), it.getString("name"),
                it.getString("style"), it.getInt("tempo"), it.getInt("transpose"),
                it.getJSONArray("links").toLinks(),
            )
        }
        require(registrations.map { it.id }.distinct().size == registrations.size)
        require(registrations.all { row -> sections.any { it.id == row.sectionId } })
        return Catalog(sections, registrations)
    }
}

/** Read-only compatibility with version 1.0. New writes go through the host-gated store. */
internal class LegacyLinkStore(context: Context, name: String = "drive_links") {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun linksFor(registration: Registration): List<DriveLink> =
        preferences.getString(registration.id, null)?.let { encoded ->
            // A damaged preference must not silently hide the reviewed source links.
            runCatching { JSONArray(encoded).toLinks() }.getOrElse { registration.links }
        } ?: registration.links

}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
private fun JSONArray.toLinks(): List<DriveLink> = objects().map {
    DriveLink(it.getString("title"), requireNotNull(validDriveUrl(it.getString("url"))))
}
