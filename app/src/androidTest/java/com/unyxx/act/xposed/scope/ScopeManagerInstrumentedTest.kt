package com.unyxx.act.xposed.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScopeManagerInstrumentedTest {

    @Test
    fun `scope list has 28 entries matching queries`() {
        val scopeFile = javaClass.classLoader?.getResource("META-INF/xposed/scope.list")
        scopeFile?.let { file ->
            val lines = file.readText().lines().filter { it.isNotBlank() }
            assertEquals(28, lines.size, "scope.list must have exactly 28 entries")
        } ?: assertTrue(false, "scope.list resource not found")
    }

    @Test
    fun `all scope entries are valid package names`() {
        val scopeFile = javaClass.classLoader?.getResource("META-INF/xposed/scope.list")
        scopeFile?.let { file ->
            val lines = file.readText().lines().filter { it.isNotBlank() }
            lines.forEach { pkg ->
                assertTrue(pkg.matches(Regex("[a-z0-9][a-z0-9_.]*")), "$pkg is not a valid package name")
            }
        }
    }

    @Test
    fun `telegram variants include all known flavors`() {
        val variants = com.unyxx.act.xposed.hooks.telegram.TelegramVariants.ALL
        assertTrue(variants.size >= 5, "At least 5 Telegram variants must be registered")
    }

    @Test
    fun `twitter variants include the main package`() {
        val variants = com.unyxx.act.xposed.hooks.twitter.TwitterVariants.ALL
        assertTrue(variants.contains("com.twitter.android"), "com.twitter.android must be in scope")
    }
}
