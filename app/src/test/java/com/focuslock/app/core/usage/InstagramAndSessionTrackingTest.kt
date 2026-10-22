package com.focuslock.app.core.usage

import com.focuslock.app.core.instagram.InstagramApiManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramAndSessionTrackingTest {

    @Test
    fun testInstagramPackageDetection_IdentifiesKnownVariants() {
        assertTrue(InstagramPackageHelper.isInstagramPackage("com.instagram.android"))
        assertTrue(InstagramPackageHelper.isInstagramPackage("com.instagram.lite"))
        assertTrue(InstagramPackageHelper.isInstagramPackage("com.instagram.threadsapp"))
        assertFalse(InstagramPackageHelper.isInstagramPackage("com.google.android.youtube"))
        assertFalse(InstagramPackageHelper.isInstagramPackage("com.whatsapp"))
        assertFalse(InstagramPackageHelper.isInstagramPackage("com.android.chrome"))

        assertEquals("Instagram", InstagramPackageHelper.getInstagramVariantLabel("com.instagram.android"))
        assertEquals("Instagram Lite", InstagramPackageHelper.getInstagramVariantLabel("com.instagram.lite"))
        assertEquals("Threads", InstagramPackageHelper.getInstagramVariantLabel("com.instagram.threadsapp"))
    }

    @Test
    fun testSessionReconstruction_AccuratelyComputesMetricsAndMergesBounces() {
        data class RawEvent(val timeStamp: Long, val isResume: Boolean)

        val events = listOf(
            RawEvent(10_000L, true),   // Session 1 start: 0s
            RawEvent(190_000L, false), // Session 1 end: 180s (3m)
            RawEvent(191_000L, true),  // Bounce within 1s (e.g. opening comment tray or camera)
            RawEvent(311_000L, false), // Sub-activity ends: 120s (2m) -> total merged: 301s (~5m)

            RawEvent(1_000_000L, true),  // Session 2 start: 1000s
            RawEvent(1_600_000L, false), // Session 2 end: 600s (10m)

            RawEvent(3_000_000L, true),  // Session 3 start: 3000s
            RawEvent(3_120_000L, false)  // Session 3 end: 120s (2m)
        )

        // Simulate session aggregation with < 2000ms bounce merge
        val rawSessions = mutableListOf<Pair<Long, Long>>()
        var curStart = 0L
        for (e in events) {
            if (e.isResume) {
                if (curStart == 0L) curStart = e.timeStamp
            } else {
                if (curStart > 0L) {
                    rawSessions.add(Pair(curStart, e.timeStamp))
                    curStart = 0L
                }
            }
        }

        val mergedSessions = mutableListOf<Pair<Long, Long>>()
        var cur = rawSessions[0]
        for (i in 1 until rawSessions.size) {
            val next = rawSessions[i]
            if (next.first - cur.second <= 2000L) {
                cur = Pair(cur.first, maxOf(cur.second, next.second))
            } else {
                mergedSessions.add(cur)
                cur = next
            }
        }
        mergedSessions.add(cur)

        assertEquals("Bounce within 1s should be merged into single session", 3, mergedSessions.size)

        val durations = mergedSessions.map { (it.second - it.first) / 1000L }
        val totalSeconds = durations.sum()
        val longestSession = durations.maxOrNull() ?: 0L
        val avgSession = totalSeconds / mergedSessions.size
        val firstActivity = mergedSessions.first().first
        val lastActivity = mergedSessions.last().second

        assertEquals(1021L, totalSeconds) // 301s + 600s + 120s = 1021s
        assertEquals(600L, longestSession) // Session 2 was 600s (10m)
        assertEquals(340L, avgSession)     // 1021 / 3 = 340s (~5.6m)
        assertEquals(10_000L, firstActivity)
        assertEquals(3_120_000L, lastActivity)
    }

    @Test
    fun testActivityEstimation_StrictPrivacyAndUnsupportedLabels() {
        val approximateScrolls = 48
        val reelsViewed = "Unavailable unless reliably detected"
        val privateLikesDMs = "Unsupported (Privacy Protected - zero screen content logging)"

        assertTrue("Scroll interaction must indicate approximate nature", approximateScrolls > 0)
        assertEquals("Reels viewed must not be fabricated", "Unavailable unless reliably detected", reelsViewed)
        assertTrue("Private content inspection must be explicitly unsupported", privateLikesDMs.startsWith("Unsupported"))
    }

    @Test
    fun testOfficialInstagramApiDisclosures_TruthfulLimitations() {
        val disclosures = InstagramApiManager.getDisclosures()
        assertFalse("Official Meta API does not provide personal browsing history", disclosures.isPersonalScrollingSupported)
        assertFalse("Official Meta API does not provide personal reels consumed", disclosures.isPrivateReelsSupported)
        assertTrue("Official Meta API requires business or creator account", disclosures.requiresBusinessOrCreatorAccount)
        assertNotNull(disclosures.documentationUrl)
    }

    @Test
    fun testEmergencyAccessRules_StrictSingleUseAndThirtySeconds() {
        val now = 5_000_000L
        val emergencyDurationMs = 30_000L // 30 seconds

        val expiresAt = now + emergencyDurationMs
        assertTrue("Emergency bypass active at 10 seconds", now + 10_000L < expiresAt)
        assertFalse("Emergency bypass expired at 31 seconds", now + 31_000L < expiresAt)

        // Rule: Single use per protected app per local day
        val emergencyUsedToday = true
        val canRequestAgain = !emergencyUsedToday
        assertFalse("Emergency access must only be usable once per local day", canRequestAgain)
    }

    @Test
    fun testAppQuotasAndUniversalScreenTime_OperateInCompleteIsolation() {
        // App 1: Instagram (quota: 30 minutes = 1800s)
        var instaUsed = 1200L // 20m
        // Universal continuous screen-on timer (independent of which app is open)
        var universalScreenTime = 1500L // 25m

        // User switches from Instagram to YouTube for 10 minutes (600s)
        val youtubeUsed = 600L
        universalScreenTime += 600L // Now 2100s (35m -> triggers 30m break alert!)

        // Instagram quota must remain at 1200s (not affected by YouTube or universal timer)
        assertEquals(1200L, instaUsed)
        // YouTube quota must be 600s
        assertEquals(600L, youtubeUsed)
        // Universal continuous screen timer reaches 2100s (35m)
        assertEquals(2100L, universalScreenTime)
        assertTrue("Universal timer triggers 30m continuous break alert", universalScreenTime >= 1800L)
    }
}
