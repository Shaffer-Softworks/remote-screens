plugins {
    alias(libs.plugins.android.application)
}

val addonVersion = rootProject.file("addon/VERSION").readText().trim()
val (major, minor, patch) = addonVersion.split('.').map { it.toInt() }

android {
    namespace = "dev.remotescreens.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.remotescreens.player"
        minSdk = 26
        targetSdk = 36
        versionCode = major * 10000 + minor * 100 + patch
        versionName = addonVersion
    }

    val keystore = System.getenv("PLAYER_KEYSTORE")?.let(::file)?.takeIf { it.isFile }
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = keystore
                storePassword = System.getenv("PLAYER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PLAYER_KEY_ALIAS")
                keyPassword = System.getenv("PLAYER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Tablets only take an update signed with the key they were installed with, so releases need the same
            // key every time. Without PLAYER_KEYSTORE the debug key keeps `assembleRelease` working locally.
            signingConfig = signingConfigs.getByName(if (keystore != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties")
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.remote.core)
    implementation(libs.remote.player.core)
    implementation(libs.remote.player.view)
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity)
    implementation(libs.coroutines.android)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)

    testImplementation(libs.junit)
}
