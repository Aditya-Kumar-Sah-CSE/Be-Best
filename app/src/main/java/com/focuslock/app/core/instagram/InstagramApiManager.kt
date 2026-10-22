package com.focuslock.app.core.instagram

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages official Instagram / Meta Graph API integration architecture.
 *
 * OFFICIAL META GRAPH API ARCHITECTURAL SPECIFICATION & LIMITATIONS:
 * 1. Scope & Account Type:
 *    The official Meta Graph API (and legacy Instagram Basic Display API) only supports
 *    Instagram Business and Creator accounts linked to a verified Facebook Page and registered
 *    Meta App ID. Personal accounts do not have access to usage analytics endpoints.
 * 2. Supported Metrics:
 *    Official API endpoints provide published media metrics (impressions, reach, engagement,
 *    audience demographics, follower counts).
 * 3. Unsupported Metrics:
 *    Meta Graph API strictly DOES NOT expose personal in-app activity, personal scrolling history,
 *    private reels watched by the user, private likes/shares, or active screen time.
 * 4. Separation of Concerns:
 *    FocusLock's core wellbeing and quota enforcement operates locally using Android UsageStats
 *    and AccessibilityService. It is 100% independent of Meta API availability and never
 *    substitutes or fabricates fake remote metrics.
 */
object InstagramApiManager {

    private const val PREFS_NAME = "focuslock_instagram_api_prefs"
    private const val KEY_CLIENT_ID = "meta_client_id"
    private const val KEY_ACCESS_TOKEN = "meta_access_token"
    private const val KEY_ACCOUNT_TYPE = "meta_account_type"
    private const val KEY_LAST_SYNC_TIMESTAMP = "meta_last_sync_timestamp"

    enum class ApiStatus(val title: String, val description: String) {
        NOT_CONNECTED(
            "Local Tracking Active (Official API Not Connected)",
            "Device-derived tracking (foreground screen time and sessions) is operating fully on-device. Meta Graph API is optional."
        ),
        AUTHENTICATED(
            "Meta Graph API Connected",
            "Authenticated with official Meta Graph API. Media and public account insights are linked separately from device screen time."
        ),
        TOKEN_EXPIRED(
            "API Token Expired",
            "The OAuth access token has expired. Local device wellbeing tracking remains fully active."
        ),
        UNAVAILABLE(
            "Public Browsing API Not Supported by Meta",
            "Meta does not permit third-party apps to access personal browsing or reels consumption history via API."
        )
    }

    data class OfficialApiDisclosures(
        val isPersonalScrollingSupported: Boolean = false,
        val isPrivateReelsSupported: Boolean = false,
        val isPublishedMediaInsightsSupported: Boolean = true,
        val requiresBusinessOrCreatorAccount: Boolean = true,
        val documentationUrl: String = "https://developers.facebook.com/docs/instagram-api"
    )

    fun getDisclosures(): OfficialApiDisclosures = OfficialApiDisclosures()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getApiStatus(context: Context): ApiStatus {
        val token = getPrefs(context).getString(KEY_ACCESS_TOKEN, null)
        return if (token.isNullOrBlank()) {
            ApiStatus.NOT_CONNECTED
        } else {
            ApiStatus.AUTHENTICATED
        }
    }

    fun saveCredentials(context: Context, clientId: String, accessToken: String, accountType: String) {
        getPrefs(context).edit()
            .putString(KEY_CLIENT_ID, clientId)
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_ACCOUNT_TYPE, accountType)
            .putLong(KEY_LAST_SYNC_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun clearCredentials(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
