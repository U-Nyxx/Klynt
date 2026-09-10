package com.unyxx.act.xposed.hooks.twitter

object TwitterVariants {
    val ALL = setOf(
        "com.twitter.android"
    )

    fun isTwitter(pkg: String): Boolean = pkg in ALL
}