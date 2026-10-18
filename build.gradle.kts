plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
}

subprojects {
    pluginManager.withPlugin("dev.detekt") {
        extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
            buildUponDefaultConfig.set(true)
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        }
        dependencies {
            add("detektPlugins", rootProject.libs.detekt.rules.ktlint.wrapper)
        }
    }
}
