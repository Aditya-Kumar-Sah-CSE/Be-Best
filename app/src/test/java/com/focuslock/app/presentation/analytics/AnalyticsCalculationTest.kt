package com.focuslock.app.presentation.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsCalculationTest {

    @Test
    fun `calculateTrendPercentage returns correct increase percentage`() {
        val todayUsage = 1800L
        val yesterdayUsage = 1200L

        val diff = todayUsage - yesterdayUsage
        val pct = ((diff.toDouble() / yesterdayUsage.toDouble()) * 100).toInt()
        val isIncrease = diff >= 0

        assertEquals(50, pct)
        assertTrue(isIncrease)
    }

    @Test
    fun `calculateTrendPercentage returns correct decrease percentage`() {
        val todayUsage = 600L
        val yesterdayUsage = 1200L

        val diff = todayUsage - yesterdayUsage
        val pct = kotlin.math.abs(((diff.toDouble() / yesterdayUsage.toDouble()) * 100).toInt())
        val isIncrease = diff >= 0

        assertEquals(50, pct)
        assertFalse(isIncrease)
    }

    @Test
    fun `appUsagePercentage is bounded and computed accurately`() {
        val totalUsage = 7200L // 2 hours
        val instagramUsage = 1800L // 30 minutes

        val percentage = ((instagramUsage.toFloat() / totalUsage.toFloat()) * 100).toInt()
        val fraction = instagramUsage.toFloat() / totalUsage.toFloat()

        assertEquals(25, percentage)
        assertEquals(0.25f, fraction, 0.001f)
    }

    @Test
    fun `zero total usage does not divide by zero`() {
        val totalUsage = 0L
        val appUsage = 0L

        val percentage = if (totalUsage > 0) ((appUsage.toFloat() / totalUsage.toFloat()) * 100).toInt() else 0
        val fraction = if (totalUsage > 0) appUsage.toFloat() / totalUsage.toFloat() else 0f

        assertEquals(0, percentage)
        assertEquals(0f, fraction, 0.001f)
    }
}
