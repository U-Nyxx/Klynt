package com.unyxx.act.xposed.hooks.tiktok

object TikTokVariants {
    val ALL = setOf(
        "com.zhiliaoapp.musically",        // Official TikTok
        "com.ss.android.ugc.trill"         // TikTok Lite / Trill (some regions)
    )

    fun isTikTok(pkg: String): Boolean = pkg in ALL
}