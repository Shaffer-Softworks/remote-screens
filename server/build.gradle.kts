plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("tech.shaffersoftworks.remotescreens.server.ApplicationKt")
}

/** Pre-existing findings in the RemoteCompose render layer; new code must stay clean. */
detekt {
    baseline.set(file("detekt-baseline.xml"))
}

version = providers.fileContents(rootProject.layout.projectDirectory.file("addon/VERSION")).asText.map { it.trim() }.getOrElse("0.0.0")

dependencies {
    implementation(project(":shared"))
    implementation(libs.remote.core)
    implementation(libs.remote.creation.core)
    implementation(libs.remote.creation.jvm)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.coroutines.core)
    implementation(libs.logback)
    implementation(libs.jmdns)

    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.coroutines.test)
}

tasks.test {
    systemProperty("fixtures.dir", rootProject.file("tests/fixtures").absolutePath)
    inputs.dir(rootProject.file("tests/fixtures")).withPropertyName("fixtures")
}

/** One runnable jar for the add-on image. */
tasks.register<Jar>("fatJar") {
    group = "build"
    archiveFileName.set("remote-screens-server.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = "tech.shaffersoftworks.remotescreens.server.ApplicationKt" }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/INDEX.LIST")
    }
    val version = project.version.toString()
    manifest { attributes["Implementation-Version"] = version }
}
