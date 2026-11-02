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

version = providers.fileContents(
    rootProject.layout.projectDirectory.file("addon/VERSION"))
    .asText.map { it.trim() }.getOrElse("0.0.0")

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
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.coroutines.test)
}

// Preview documents are produced on the JVM; Android never loads the server's dependencies.
val preview = sourceSets.create("preview") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[preview.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[preview.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())

tasks.register<JavaExec>("generatePreviews") {
    group = "verification"
    description = "Render the server UI catalogue for Android Studio previews."
    classpath = preview.runtimeClasspath
    mainClass.set("tech.shaffersoftworks.remotescreens.server.preview.GeneratePreviewsKt")
    val destination = rootProject.layout.projectDirectory.dir("preview/build/generated/serverPreviews")
    val filter = providers.gradleProperty("previewFilter").getOrElse("")
    args(destination.asFile.absolutePath, filter)
    inputs.property("previewFilter", filter)
    inputs.files(sourceSets.main.get().allSource, preview.allSource)
    outputs.dir(destination)
}

dependencies { testImplementation(preview.output) }

// The standard Detekt task does not include custom JVM source sets.
tasks.named("detekt") { dependsOn("detektPreview") }

tasks.test {
    systemProperty("fixtures.dir", rootProject.file("tests/fixtures").absolutePath)
    inputs.dir(rootProject.file("tests/fixtures")).withPropertyName("fixtures")
}

/** One runnable jar for the add-on image. */
tasks.register<Jar>("fatJar") {
    description = ""
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
