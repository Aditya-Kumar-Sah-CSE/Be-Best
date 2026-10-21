package com.focuslock.app.domain.model

import android.content.pm.ApplicationInfo
import android.os.Build

enum class AppCategory(val title: String, val isSuggestedDistraction: Boolean) {
    SOCIAL("Social Media", true),
    VIDEO("Video & Entertainment", true),
    DISCUSSION("Discussion & Communities", true),
    GAMING("Games", true),
    BROWSING("Browsing", false),
    OTHER("Other Applications", false);

    companion object {
        fun fromString(value: String): AppCategory {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OTHER
        }

        fun inferCategory(packageName: String, appInfo: ApplicationInfo? = null): AppCategory {
            val pkg = packageName.lowercase()

            // Check system game category if available on Android 8.0+ (API 26+)
            if (appInfo != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (appInfo.category == ApplicationInfo.CATEGORY_GAME) {
                        return GAMING
                    }
                }
                // Fallback flag for older builds
                if ((appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0) {
                    return GAMING
                }
            }

            return when {
                // Social Media
                pkg.contains("instagram") || pkg.contains("facebook") || pkg.contains("katana") ||
                pkg.contains("twitter") || pkg.contains(".x.") || pkg.endsWith(".x") ||
                pkg.contains("snapchat") || pkg.contains("threads") || pkg.contains("pinterest") ||
                pkg.contains("linkedin") || pkg.contains("tiktok") -> SOCIAL

                // Video & Entertainment
                pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("primevideo") ||
                pkg.contains("twitch") || pkg.contains("disney") || pkg.contains("hulu") ||
                pkg.contains("hotstar") || pkg.contains("crunchyroll") || pkg.contains("vlc") -> VIDEO

                // Discussion & Communities
                pkg.contains("reddit") || pkg.contains("discord") || pkg.contains("telegram") ||
                pkg.contains("whatsapp") || pkg.contains("messenger") || pkg.contains("slack") -> DISCUSSION

                // Browsing
                pkg.contains("chrome") || pkg.contains("firefox") || pkg.contains("msedge") ||
                pkg.contains("brave") || pkg.contains("opera") || pkg.contains("browser") -> BROWSING

                // Games fallback
                pkg.contains("game") || pkg.contains("pubg") || pkg.contains("freefire") ||
                pkg.contains("supercell") || pkg.contains("roblox") || pkg.contains("minecraft") ||
                pkg.contains("candycrush") || pkg.contains("subwaysurf") || pkg.contains("clash") -> GAMING

                else -> OTHER
            }
        }
    }
}
