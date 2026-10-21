package com.focuslock.app.domain.model

enum class AppCategory(val title: String, val iconRes: String) {
    SOCIAL("Social Media", "ic_social"),
    MESSAGING("Messaging", "ic_message"),
    VIDEO("Video & Streaming", "ic_video"),
    GAMING("Gaming", "ic_game"),
    CUSTOM("Custom", "ic_custom");

    companion object {
        fun fromString(value: String): AppCategory {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CUSTOM
        }

        fun inferCategory(packageName: String): AppCategory {
            val pkg = packageName.lowercase()
            return when {
                pkg.contains("instagram") || pkg.contains("facebook") || pkg.contains("twitter") || pkg.contains("tiktok") || pkg.contains("reddit") || pkg.contains("snapchat") -> SOCIAL
                pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("messenger") || pkg.contains("discord") -> MESSAGING
                pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("twitch") || pkg.contains("primevideo") -> VIDEO
                pkg.contains("game") || pkg.contains("pubg") || pkg.contains("freefire") || pkg.contains("supercell") || pkg.contains("roblox") || pkg.contains("minecraft") -> GAMING
                else -> CUSTOM
            }
        }
    }
}
