pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        alias(libs.plugins.android.application) apply false
        alias(libs.plugins.kotlin.compose) apply false
        alias(libs.plugins.kotlin.serialization) apply false
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/public/")
        maven("https://repo1.maven.org/maven2/")
        google()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/maven")
        maven("https://api.xposed.info/")
    }
}

rootProject.name = "Klynt"
include(":app")