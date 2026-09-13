pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // mavenCentral 403 from this network: try Indonesian/APAC mirrors
        // + Google for androidx. Order: mirrors -> Google -> JetBrains -> Xposed
        maven("https://maven.aliyun.com/repository/public/")
        maven("https://repo1.maven.org/maven2/")
        google()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/maven")
        maven("https://api.xposed.info/")
    }
}

rootProject.name = "Klynt"
include(":app")