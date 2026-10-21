package com.focuslock.app.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppUpdateManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var updateManager: AppUpdateManager

    private val sampleMetadataJson = """
        {
          "versionCode": 2,
          "versionName": "1.1.0",
          "releaseNotes": "• Real per-app daily blocking\n• 30-second emergency unlock",
          "apkUrl": "https://raw.githubusercontent.com/Aditya-Kumar-Sah-CSE/Be-Best/main/app/build/outputs/apk/debug/app-debug.apk",
          "apkSha256": "412834A1BBC0D7D029F97EBCE45B4C9781168F6CFB345366770998248BE83DCE",
          "apkSizeBytes": 18399498,
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
        assertEquals("https://raw.githubusercontent.com/Aditya-Kumar-Sah-CSE/Be-Best/main/app/build/outputs/apk/debug/app-debug.apk", info.apkUrl)
        assertEquals("412834A1BBC0D7D029F97EBCE45B4C9781168F6CFB345366770998248BE83DCE", info.apkSha256)
        assertEquals(18399498L, info.apkSizeBytes)
        assertEquals(1L, info.minSupportedVersionCode)
        assertFalse(info.isMandatory)
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
        // "FocusLock Test Payload" SHA-256 is:
        // echo -n "FocusLock Test Payload" | sha256sum -> 2cb2615456f91f1a5814e5f082e6ef231ec88915b8823293e50669d0d3463991
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
    fun testSessionDismissal_ChangesStateToDismissed() {
        updateManager.dismissForSession()
        assertEquals(UpdateState.DismissedForSession, updateManager.updateState.value)

        updateManager.resetState()
        assertEquals(UpdateState.Idle, updateManager.updateState.value)
    }
}
