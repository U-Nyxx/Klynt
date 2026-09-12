import java.util.Properties

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10"
}

android {
    namespace = "com.unyxx.act"
    // 37 required by io.github.libxposed 102 AAR metadata (targetSdk stays 35)
    compileSdk = 37

    defaultConfig {
        applicationId = "com.unyxx.act"
        minSdk = 30
        targetSdk = 35
        versionCode = 5
        versionName = "1.0.3"
        // Strip locales bundled by AARs (we ship en + in only).
        resourceConfigurations += listOf("en", "in")
        // Real devices on minSdk 30 are arm64; shipping x86/32-bit .so
        // only bloats the APK (no emulator tests run in CI).
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildFeatures {
        buildConfig = true
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
        kotlinCompilerExtensionVersion = "2.2.10"
    }
}

// JUnit5 tests must run on the JUnit Platform (else Gradle silently
// discovers zero tests and the suite is theater).
tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    // Xposed API + Service (libxposed 101; runs on 101+ frameworks).
    compileOnly("io.github.libxposed:api:101.0.0")
    implementation("io.github.libxposed:service:101.0.0")

    // Liquid Glass (JitPack)
    implementation("com.github.QWEA0:liquidglass:v2.0.8")

    // Compose + Material 3 - Latest stable BOM
    implementation(platform("androidx.compose:compose-bom:2024.08.00"))
    implementation("androidx.compose.material3:material3")
    api("androidx.compose.material:material-icons-core")
    api("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.core:core-ktx:1.13.1")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.2")

    // Coroutines/Flow (-android pulls -core transitively)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // NOTE (diet): coil, work-runtime, navigation-compose,
    // core-splashscreen, material (MDC views), retrofit + converter-gson
    // were removed — zero usages in main source (verified). Update check
    // uses OkHttp + Gson directly.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Testing (launcher must be explicit: the jupiter aggregator
    // does not pull it, and without it the executor fails to start)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-manifest:1.6.1")
}
