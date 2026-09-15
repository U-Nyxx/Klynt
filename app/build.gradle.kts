import java.util.Properties

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.unyxx.act"
    compileSdk = 37
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        applicationId = "com.unyxx.act"
        minSdk = 33
        targetSdk = 35
        versionCode = 15
        versionName = "1.0.13"
        val codename = rootProject.file("release-codename.txt")
            .takeIf { it.exists() }?.readText()?.filter { it.isLetterOrDigit() || it == '-' || it == '_' }?.takeIf { it.isNotEmpty() } ?: "Dev"
        buildConfigField("String", "BUILD_CODENAME", "\"$codename\"")
        resourceConfigurations += listOf("en", "in")
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("klynt-release.jks")
            storePassword = localProps.getProperty("STORE_PASSWORD", System.getenv("STORE_PASSWORD") ?: "")
            keyAlias = localProps.getProperty("KEY_ALIAS", System.getenv("KEY_ALIAS") ?: "")
            keyPassword = localProps.getProperty("KEY_PASSWORD", System.getenv("KEY_PASSWORD") ?: "")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            optIn.add("kotlin.RequiresOptIn")
            optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
            optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,LICENSE,NOTICE}"
        }
        jniLibs {
            pickFirsts += "lib/**/*"
        }
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.kotlin.get()
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)

    val composeBom = libs.compose.bom
    implementation(platform(composeBom))
    implementation(libs.material3)
    api(libs.material.icons.core)
    api(libs.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.foundation)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.viewmodel.savedstate)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.timber)

    testImplementation(libs.test.junit.jupiter)
    testRuntimeOnly(libs.test.junit.launcher)
    testImplementation(libs.test.mockk)
    testImplementation(libs.test.turbine)
    androidTestImplementation(libs.android.test.espresso)
    androidTestImplementation(libs.android.test.compose)
    androidTestImplementation(libs.android.test.compose.manifest)
}