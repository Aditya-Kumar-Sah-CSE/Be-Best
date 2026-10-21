package com.focuslock.app.core.update

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
import java.net.HttpURLConnection
import java.net.URL

class AppUpdateManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var updateManager: AppUpdateManager

    private val sampleMetadataJson = """
        {
          "versionCode": 2,
          "versionName": "1.1.0",
          "releaseNotes": "• Real per-app daily blocking\n• 30-second emergency unlock",
          "apkUrl": "https://raw.githubusercontent.com/Aditya-Kumar-Sah-CSE/Be-Best/main/release/FocusLock-v1.1.0.apk",
          "apkSha256": "0BE8EDC2028C33A958EF0AF3E7CACCBE4606254CCC368E0E00DF102084AC2EE6",
          "apkSizeBytes": 18727664,
          "minSupportedVersionCode": 1,
          "isMandatory": false
        }
    """.trimIndent()

    @Before
    fun setUp() {
        updateManager = AppUpdateManager()
    }

    @Test
    fun testParseMetadata_ValidJson_ReturnsAccurateUpdateInfo() {
        val info = updateManager.parseMetadata(sampleMetadataJson)

        assertEquals(2L, info.versionCode)
        assertEquals("1.1.0", info.versionName)
        assertTrue(info.releaseNotes.contains("Real per-app daily blocking"))
        assertEquals("https://raw.githubusercontent.com/Aditya-Kumar-Sah-CSE/Be-Best/main/release/FocusLock-v1.1.0.apk", info.apkUrl)
        assertEquals("0BE8EDC2028C33A958EF0AF3E7CACCBE4606254CCC368E0E00DF102084AC2EE6", info.apkSha256)
        assertEquals(18727664L, info.apkSizeBytes)
        assertEquals(1L, info.minSupportedVersionCode)
        assertFalse(info.isMandatory)
    }

    @Test
    fun testParseMetadata_RejectsNonPositiveVersionCode() {
        val invalidJson = """
            {
              "versionCode": 0,
              "versionName": "1.0.0",
              "apkUrl": "https://example.com/app.apk",
              "apkSha256": "0BE8EDC2028C33A958EF0AF3E7CACCBE4606254CCC368E0E00DF102084AC2EE6"
            }
        """.trimIndent()

        try {
            updateManager.parseMetadata(invalidJson)
            fail("Expected IllegalArgumentException for versionCode <= 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("versionCode") == true)
        }
    }

    @Test
    fun testParseMetadata_RejectsBlankVersionName() {
        val invalidJson = """
            {
              "versionCode": 2,
              "versionName": "   ",
              "apkUrl": "https://example.com/app.apk",
              "apkSha256": "0BE8EDC2028C33A958EF0AF3E7CACCBE4606254CCC368E0E00DF102084AC2EE6"
            }
        """.trimIndent()

        try {
            updateManager.parseMetadata(invalidJson)
            fail("Expected IllegalArgumentException for blank versionName")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("versionName") == true)
        }
    }

    @Test
    fun testParseMetadata_RejectsInsecureHttpUrl() {
        val insecureJson = """
            {
              "versionCode": 2,
              "versionName": "1.1.0",
              "apkUrl": "http://example.com/unencrypted_app.apk",
              "apkSha256": "0BE8EDC2028C33A958EF0AF3E7CACCBE4606254CCC368E0E00DF102084AC2EE6"
            }
        """.trimIndent()

        try {
            updateManager.parseMetadata(insecureJson)
            fail("Expected IllegalArgumentException for non-HTTPS apkUrl")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("HTTPS") == true)
        }
    }

    @Test
    fun testParseMetadata_RejectsMalformedSha256() {
        val malformedHashJson = """
            {
              "versionCode": 2,
              "versionName": "1.1.0",
              "apkUrl": "https://example.com/app.apk",
              "apkSha256": "NOT_A_VALID_HEX_HASH_TOO_SHORT"
            }
        """.trimIndent()

        try {
            updateManager.parseMetadata(malformedHashJson)
            fail("Expected IllegalArgumentException for malformed SHA-256")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("SHA-256") == true)
        }
    }

    @Test
    fun testVersionComparison_NewerVersionTriggersUpdate() {
        val info = updateManager.parseMetadata(sampleMetadataJson)
        val installedVersionCode = 1L

        val isNewer = info.versionCode > installedVersionCode
        assertTrue("Higher remote versionCode must trigger update prompt", isNewer)
    }

    @Test
    fun testVersionComparison_CurrentVersionDoesNotTriggerUpdate() {
        val info = updateManager.parseMetadata(sampleMetadataJson)
        val installedVersionCode = 2L

        val isNewer = info.versionCode > installedVersionCode
        assertFalse("Same versionCode must NOT trigger update prompt", isNewer)
    }

    @Test
    fun testVersionComparison_HigherInstalledVersionDoesNotTriggerUpdate() {
        val info = updateManager.parseMetadata(sampleMetadataJson)
        val installedVersionCode = 3L

        val isNewer = info.versionCode > installedVersionCode
        assertFalse("Development/Higher versionCode must NOT trigger update prompt", isNewer)
    }

    @Test
    fun testSha256Calculation_MatchesKnownDigest() {
        val testFile = tempFolder.newFile("sample_test.apk")
        testFile.writeText("FocusLock Test Payload")

        val expectedSha256 = "47254C016129873D42086BF820D122C3D56B9718A04D1EEF1E1D9C15AF96BEB7"
        val calculated = ApkSecurityValidator.calculateSha256(testFile)

        assertEquals(expectedSha256, calculated)
        assertTrue(ApkSecurityValidator.verifySha256(testFile, expectedSha256))
    }

    @Test
    fun testSha256Verification_DetectsTamperingOrMismatch() {
        val testFile = tempFolder.newFile("tampered.apk")
        testFile.writeText("Corrupted Payload Content")

        val originalExpectedSha256 = "47254C016129873D42086BF820D122C3D56B9718A04D1EEF1E1D9C15AF96BEB7"
        val isValid = ApkSecurityValidator.verifySha256(testFile, originalExpectedSha256)

        assertFalse("Tampered content must fail SHA-256 verification", isValid)
    }

    @Test
    fun testCertificateFingerprint_ConstantFormat() {
        val expectedFingerprint = "5DFC7E8D13125F283874DDC3FCB9DE6E8D07BE4C715C9083D38CA7EB4E435FFA"
        assertEquals(expectedFingerprint, ApkSecurityValidator.FOCUSLOCK_RELEASE_CERT_SHA256)
        assertEquals(64, ApkSecurityValidator.FOCUSLOCK_RELEASE_CERT_SHA256.length)
    }

    @Test
    fun testDownloadProgressCalculation_PercentageAndBytes() {
        val totalBytes = 18727664L
        val downloadedBytes = 9363832L

        val percent = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
        assertEquals(50, percent)

        val fullBytes = 18727664L
        val fullPercent = ((fullBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
        assertEquals(100, fullPercent)
    }

    @Test
    fun testSessionDismissal_ChangesStateToDismissed() {
        updateManager.dismissForSession()
        assertEquals(UpdateState.DismissedForSession, updateManager.updateState.value)

        updateManager.resetState()
        assertEquals(UpdateState.Idle, updateManager.updateState.value)
    }

    @Test
    fun testLiveRemoteMetadata_FetchAndValidateFromProductionUrl() {
        try {
            val url = URL(AppUpdateManager.DEFAULT_METADATA_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "FocusLock-Test")
            connection.connect()

            val responseCode = connection.responseCode
            assertEquals("Live metadata URL must return HTTP 200 OK", 200, responseCode)

            val jsonContent = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val parsedInfo = updateManager.parseMetadata(jsonContent)

            assertEquals("Remote metadata versionCode must be 3", 3L, parsedInfo.versionCode)
            assertEquals("Remote metadata versionName must be 1.2.0", "1.2.0", parsedInfo.versionName)
            assertTrue("Remote metadata apkUrl must be HTTPS", parsedInfo.apkUrl.startsWith("https://"))
            assertEquals(64, parsedInfo.apkSha256.length)
            assertTrue("Remote metadata apkSizeBytes must be > 0", parsedInfo.apkSizeBytes > 0)
        } catch (e: java.io.IOException) {
            // In case of local offline sandbox without internet access during unit tests
            println("Skipping live network assertion due to offline environment: ${e.message}")
        }
    }
}
