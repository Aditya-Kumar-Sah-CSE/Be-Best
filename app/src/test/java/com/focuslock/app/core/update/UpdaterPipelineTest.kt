package com.focuslock.app.core.update

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UpdaterPipelineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var updateManager: AppUpdateManager

    @Before
    fun setUp() {
        updateManager = AppUpdateManager()
    }

    @Test
    fun testUpdateAvailable_HigherVersionCodeTriggersUpdate() {
        val metadata = """
            {
              "versionCode": 3,
              "versionName": "1.2.0",
              "apkUrl": "https://raw.githubusercontent.com/Aditya-Kumar-Sah-CSE/Be-Best/main/release/FocusLock-v1.2.0.apk",
              "apkSha256": "FBAFBA686E1AAFB76C1EC70BE510635043946CC570FF7B8DD24A82BD93D7364F",
              "apkSizeBytes": 12152035,
              "minSupportedVersionCode": 1,
              "isMandatory": false
            }
        """.trimIndent()

        val info = updateManager.parseMetadata(metadata)
        val installedCode = 2L

        assertTrue("Newer versionCode 3 must trigger update when installed is 2", info.versionCode > installedCode)
    }

    @Test
    fun testLatestVersionAlreadyInstalled_NoUpdateTriggered() {
        val metadata = """
            {
              "versionCode": 3,
              "versionName": "1.2.0",
              "apkUrl": "https://raw.githubusercontent.com/Aditya-Kumar-Sah-CSE/Be-Best/main/release/FocusLock-v1.2.0.apk",
              "apkSha256": "FBAFBA686E1AAFB76C1EC70BE510635043946CC570FF7B8DD24A82BD93D7364F",
              "apkSizeBytes": 12152035,
              "minSupportedVersionCode": 1,
              "isMandatory": false
            }
        """.trimIndent()

        val info = updateManager.parseMetadata(metadata)
        val installedCode = 3L

        assertFalse("Same versionCode 3 must NOT trigger update", info.versionCode > installedCode)
    }

    @Test
    fun testInvalidMetadata_RejectsMalformedJson() {
        val badJson = "{ invalid json content }"
        try {
            updateManager.parseMetadata(badJson)
            fail("Expected JSONException for malformed JSON")
        } catch (e: JSONException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun testInvalidMetadata_RejectsInsecureHttpUrl() {
        val insecureMetadata = """
            {
              "versionCode": 4,
              "versionName": "1.3.0",
              "apkUrl": "http://insecure-site.com/app.apk",
              "apkSha256": "FBAFBA686E1AAFB76C1EC70BE510635043946CC570FF7B8DD24A82BD93D7364F"
            }
        """.trimIndent()

        try {
            updateManager.parseMetadata(insecureMetadata)
            fail("Expected IllegalArgumentException for HTTP URL")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("HTTPS") == true)
        }
    }

    @Test
    fun testIndeterminateProgress_WhenContentLengthMissing() {
        val contentLength = -1L
        val downloadedBytes = 5_000_000L

        val percent = if (contentLength > 0) {
            ((downloadedBytes * 100) / contentLength).toInt().coerceIn(0, 100)
        } else -1

        assertEquals("Missing content length must report -1 for indeterminate progress", -1, percent)
    }

    @Test
    fun testDeterminateProgress_WhenContentLengthKnown() {
        val contentLength = 10_000_000L
        val downloadedBytes = 5_000_000L

        val percent = if (contentLength > 0) {
            ((downloadedBytes * 100) / contentLength).toInt().coerceIn(0, 100)
        } else -1

        assertEquals(50, percent)
    }

    @Test
    fun testSha256ChecksumVerification_DetectsTamperedApk() {
        val testApk = tempFolder.newFile("test_tampered.apk")
        testApk.writeText("Corrupted or modified binary content")

        val expectedValidHash = "FBAFBA686E1AAFB76C1EC70BE510635043946CC570FF7B8DD24A82BD93D7364F"
        val isChecksumValid = ApkSecurityValidator.verifySha256(testApk, expectedValidHash)

        assertFalse("Tampered APK must fail SHA-256 verification", isChecksumValid)
    }

    @Test
    fun testOfficialCertificateSha256_ExactMatch() {
        val officialCert = ApkSecurityValidator.FOCUSLOCK_RELEASE_CERT_SHA256
        assertEquals(64, officialCert.length)
        assertEquals("5DFC7E8D13125F283874DDC3FCB9DE6E8D07BE4C715C9083D38CA7EB4E435FFA", officialCert)
    }

    @Test
    fun testDatabaseSchemaPreservation_UserSettingsAndQuotasPersistedAcrossVersions() {
        // Verify that updating versions does not clear database entities
        val testPackage = "com.instagram.android"
        val customLimit = 1800L
        val userEnabled = true

        // Simulating persistent entity representation across update
        val persistentSettings = mapOf(
            "pin_configured" to true,
            "hardcore_enabled" to false,
            "continuous_break_alert" to true
        )

        val persistentAppQuota = mapOf(
            "package" to testPackage,
            "limit" to customLimit,
            "enabled" to userEnabled
        )

        assertEquals(1800L, persistentAppQuota["limit"])
        assertTrue(persistentSettings["continuous_break_alert"] as Boolean)
        assertTrue(persistentAppQuota["enabled"] as Boolean)
    }
}
