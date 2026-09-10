package com.unyxx.act.xposed.hooks.telegram

object TelegramVariants {
    // 20+ packages from Telegami/Killergram/TeleVip compat lists
    val ALL = setOf(
        "org.telegram.messenger",           // Official
        "org.telegram.messenger.beta",      // Beta
        "org.telegram.messenger.web",       // Web
        "org.telegram.plus",                // Plus Messenger
        "tw.nekomimi.nekogram",             // Nekogram (Play Store)
        "org.nekogram",                     // Nekogram (F-Droid/GitHub)
        "nekox.messenger",                  // NekoX
        "xyz.nextalone.nagram",             // Nagram
        "nu.gpu.nagram",                    // Nagram (alt)
        "nu.gpu.nagramx",                   // NagramX
        "uz.unnarsx.cherrygram",            // Cherrygram
        "org.forkgram.messenger",           // Forkgram
        "org.forkclient.messenger",         // Forkgram beta
        "org.telegram.group",               // Turrit
        "it.octogram.android",              // Octogram
        "it.belloworld.mercurygram",        // Mercurygram
        "top.qwq2333.nullgram",             // Nullgram
        "com.iMe.android",                  // iMe Messenger
        "com.exteragram.messenger",         // exteraGram
        "telega.messenger",                 // Telega
        "com.yukigram"                      // Yukigram
    )

    fun isTelegram(pkg: String): Boolean = pkg in ALL
}