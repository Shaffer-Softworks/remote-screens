plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    `java-library`
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(libs.serialization.json)
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
}

tasks.test {
    systemProperty("fixtures.dir", rootProject.file("tests/fixtures").absolutePath)
    inputs.dir(rootProject.file("tests/fixtures")).withPropertyName("fixtures")
    if (providers.gradleProperty("updateFixtures").isPresent) {
        systemProperty("fixtures.update", "true")
        outputs.upToDateWhen { false }
    }
}
