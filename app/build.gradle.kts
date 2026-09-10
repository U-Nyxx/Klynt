import java.util.Properties

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") version "2.2.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0"
    id("kotlin-kapt")
}

android {
    namespace = "com.unyxx.act"
    // 36 required by io.github.libxposed 101 AAR metadata (targetSdk stays 35)
    compileSdk = 36

    defaultConfig {
        applicationId = "com.unyxx.act"
        minSdk = 30
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0"
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

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
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
        kotlinCompilerExtensionVersion = "2.2.0"
    }
}

dependencies {
    // Xposed API (libxposed 101, modern)
    compileOnly("io.github.libxposed:api:101.0.0")
    // Xposed Service (module-app side: scope + remote prefs via framework binder)
    implementation("io.github.libxposed:service:101.0.0")

    // Liquid Glass (JitPack)
    implementation("com.github.QWEA0:liquidglass:v2.0.8")

    // Compose + Material 3 - Latest stable BOM
    implementation(platform("androidx.compose:compose-bom:2024.08.00"))
    implementation("androidx.compose.material3:material3")
    api("androidx.compose.material:material-icons-core")
    api("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Material Components
    implementation("com.google.android.material:material:1.12.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.2")

    // Coroutines/Flow
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Coil - Async image loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Retrofit + OkHttp + Gson - Update checking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // WorkManager - Background tasks
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-manifest:1.6.1")
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("kapt.incremental.apt", "true")
        arg("kapt.use.worker.api", "true")
        arg("kapt.language.version", "1.9")
    }
}