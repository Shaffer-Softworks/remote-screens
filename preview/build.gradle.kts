plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// A development-only Android host. No dependency on the server or production player.
android {
    namespace = "tech.shaffersoftworks.remotescreens.preview"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "tech.shaffersoftworks.remotescreens.preview"
        minSdk = 29 // remote-tooling-preview uses the Compose player, which requires API 29.
        targetSdk = libs.versions.targetSdk.get().toInt()
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets["main"].apply {
        kotlin.srcDir(layout.buildDirectory.dir("generated/serverPreviews/kotlin").get().asFile)
        assets.srcDir(layout.buildDirectory.dir("generated/serverPreviews/assets").get().asFile)
    }
}

kotlin { jvmToolchain(21) }
tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn(":server:generatePreviews")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.remote.tooling.preview)
    implementation(libs.remote.player.view)
    implementation(libs.remote.player.core)
    implementation(libs.remote.core)
    debugImplementation(libs.compose.ui.tooling)
}

tasks.named("preBuild") { dependsOn(":server:generatePreviews") }
