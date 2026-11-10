import java.util.concurrent.TimeUnit

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.detekt)
    alias(libs.plugins.sentry.android)
}

// Generate the mapping UUID in the APK; each user's add-on uploads to their own Sentry project.
sentry {
    includeProguardMapping.set(true)
    autoUploadProguardMapping.set(false)
    includeSourceContext.set(false)
    includeDependenciesReport.set(false)
    telemetry.set(false)
    autoInstallation { enabled.set(false) }
    tracingInstrumentation { enabled.set(false) }
    ignoredBuildTypes.set(setOf("debug"))
}

val addonVersion = rootProject.file("addon/VERSION").readText().trim()
val (major, minor, patch) = addonVersion.split('.').map { it.toInt() }

android {
    namespace = "tech.shaffersoftworks.remotescreens.player"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "tech.shaffersoftworks.remotescreens.player"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
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
            isMinifyEnabled = true
            isShrinkResources = true
            // x86 libraries are for emulators. A tablet update is read into memory, and WebRTC for every ABI
            // pushed that file past the heap limit, so the player died while installing it.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        compose = true
    }

    packaging {
        resources.excludes += setOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    jvmToolchain(21)
}

// SDK ADB, available from Android Studio's Gradle tool window under "adb".
val sdkAdb = androidComponents.sdkComponents.adb
val adbSerial = providers.gradleProperty("adbSerial")
val adbServer = providers.gradleProperty("adbServer").orElse("127.0.0.1:18100")
val adbPort = providers.gradleProperty("adbPort").orElse("18100")
val adbCaptureDir = layout.buildDirectory.dir("adb")

fun registerAdbTask(name: String, descriptionText: String, action: String) =
    tasks.register(name) {
        group = "adb"
        description = descriptionText
        if (action == "deploy") dependsOn("assembleDebug")
        doLast {
            val executable = sdkAdb.get().asFile.absolutePath
            fun runAdb(arguments: List<String>, output: File? = null): String {
                val command = mutableListOf(executable)
                if (action != "devices") {
                    val serial = adbSerial.orNull
                        ?: throw GradleException("Select a device with -PadbSerial=<serial>; use adbDevices to list devices")
                    command.addAll(listOf("-s", serial))
                }
                command.addAll(arguments)
                val report = output ?: File.createTempFile("remote-screens-adb-", ".txt")
                val errors = File.createTempFile("remote-screens-adb-errors-", ".txt")
                report.parentFile.mkdirs()
                val process = ProcessBuilder(command).redirectOutput(report).redirectError(errors).start()
                try {
                    if (!process.waitFor(120, TimeUnit.SECONDS)) {
                        throw GradleException("ADB command timed out")
                    }
                    val errorText = errors.readText().takeLast(4000)
                    if (errorText.isNotBlank()) logger.lifecycle(errorText)
                    if (process.exitValue() != 0) {
                        val detail = if (output == null) report.readText().takeLast(4000) else errorText
                        throw GradleException("ADB failed: ${arguments.first()}: $detail")
                    }
                    if (output == null) logger.lifecycle(report.readText())
                    return output?.absolutePath.orEmpty()
                } finally {
                    if (process.isAlive) process.destroyForcibly()
                    errors.delete()
                    if (output == null) report.delete()
                }
            }
            if (action == "deploy") {
                runAdb(listOf("install", "-r", layout.buildDirectory.file("outputs/apk/debug/player-debug.apk").get().asFile.absolutePath))
            }
            val packageName = "tech.shaffersoftworks.remotescreens.player"
            when (action) {
                "devices" -> runAdb(listOf("devices", "-l"))
                "reverse" -> runAdb(listOf("reverse", "tcp:${adbPort.get()}", "tcp:${adbPort.get()}"))
                "deploy", "launch" -> runAdb(
                    listOf("shell", "am", "start", "-W", "-n", "$packageName/.MainActivity",
                        "--es", "server", adbServer.get()),
                )
                "screenshot" -> logger.lifecycle(runAdb(
                    listOf("exec-out", "screencap", "-p"),
                    adbCaptureDir.get().file("screenshot.png").asFile,
                ))
                "logs" -> logger.lifecycle(runAdb(
                    listOf("logcat", "-d", "-t", "500", "-v", "threadtime"),
                    adbCaptureDir.get().file("logcat.txt").asFile,
                ))
            }
        }
    }

registerAdbTask("adbDevices", "List ADB devices and serials", "devices")
registerAdbTask("adbLaunch", "Launch the player with -PadbSerial and optional -PadbServer", "launch")
registerAdbTask("adbDeploy", "Install debug player and launch on the selected device", "deploy")
registerAdbTask("adbReverse", "Forward the tablet's local player port to this computer", "reverse")
registerAdbTask("adbScreenshot", "Save a screenshot in player/build/adb", "screenshot")
registerAdbTask("adbLogs", "Save the latest 500 log lines in player/build/adb", "logs")


detekt {
    baseline.set(file("detekt-baseline.xml"))
}

dependencies {
    implementation(project(":shared"))

    // Remote Compose
    implementation(libs.remote.core)
    implementation(libs.remote.player.core)
    implementation(libs.remote.player.view)

    // Cameras' live video
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)

    // Interactive map cards (osmdroid, same stack as RPI)
    implementation(libs.osm.android)
    implementation(libs.osm.compose)

    // Front-camera face wake
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.mlkit.face.detection)

    // Turns on Android's Location accuracy setting. Positions still come from LocationManager.
    implementation(libs.play.services.location)

    // Intercom calls. Audio only; the page's signaling stays on the add-on.
    implementation(libs.stream.webrtc)

    // AndroidX
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    ksp(libs.hilt.android.compiler)

    // Kotlin Coroutines
    implementation(libs.coroutines.android)

    // Ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)

    // Coil
    implementation(libs.io.coil.kt.coil.compose)
    implementation(libs.io.coil.kt.coil.network.ktor)
    implementation(libs.io.coil.kt.coil.network.cache.control)

    // Tools
    implementation(libs.timber)
    implementation(libs.sentry.android)

    // Testing
    testImplementation(libs.remote.creation.core)
    testImplementation(libs.remote.creation.jvm)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
}
