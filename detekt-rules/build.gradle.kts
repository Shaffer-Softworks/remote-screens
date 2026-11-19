plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly(libs.detekt.api)
    compileOnly(libs.detekt.analysis.api)
    testImplementation(libs.detekt.test)
    testImplementation(kotlin("test-junit"))
}
