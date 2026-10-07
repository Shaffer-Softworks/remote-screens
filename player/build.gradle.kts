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

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed in CI with the release key; locally the debug key keeps `assembleRelease` working.
            signingConfig = signingConfigs.getByName("debug")
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
