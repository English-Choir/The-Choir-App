package com.phantominnovation.choir

import org.junit.Assert.*
import org.junit.Test

class AppReleaseTest {
    private val release = mapOf("enabled" to true, "packageName" to "com.phantominnovation.choir",
        "versionCode" to 5L, "versionName" to "1.4", "downloadUrl" to "https://drive.google.com/file/d/verified-id/view")

    @Test fun versionsCompareNumericallyAndUnavailableReleasesAreNotOffered() {
        val parsed = requireNotNull(parseAppRelease(release))
        assertTrue(parsed.isNewerThan(4))
        assertFalse(parsed.isNewerThan(5))
        assertFalse(parsed.isNewerThan(6))
        assertNull(parseAppRelease(null))
        assertNull(parseAppRelease(release + ("enabled" to false)))
    }

    @Test fun invalidMetadataCannotOfferAnotherPackageOrUntrustedDownload() {
        val changes = listOf(
            "packageName" to "another.app", "versionCode" to 5.5, "versionCode" to 0L,
            "versionName" to " ", "enabled" to "true",
            "downloadUrl" to "http://drive.google.com/file/d/id/view",
            "downloadUrl" to "https://drive.google.com.evil.test/file/d/id/view",
            "downloadUrl" to "https://drive.google.com@evil.test/file/d/id/view",
            "downloadUrl" to "https://drive.google.com/drive/folders/id",
            "downloadUrl" to "https://drive.google.com/file/d/id/view?redirect=evil",
            "downloadUrl" to "https://drive.google.com:1234/file/d/id/view",
        )
        changes.forEach { change -> assertTrue(change.toString(), runCatching { parseAppRelease(release + change) }.isFailure) }
    }
}
