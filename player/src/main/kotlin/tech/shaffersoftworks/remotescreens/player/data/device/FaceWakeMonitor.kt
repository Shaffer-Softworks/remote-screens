package tech.shaffersoftworks.remotescreens.player.data.device

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

private const val ANALYSIS_WIDTH = 320
private const val ANALYSIS_HEIGHT = 240
private const val MIN_FRAME_INTERVAL_MS = 500L

/**
 * Opens the front camera while the screen is asleep and wakes it when ML Kit sees a face.
 * Soft no-op without camera permission, a front camera, or on low-RAM devices.
 */
@Singleton
class FaceWakeMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build(),
    )

    private var cameraProvider: ProcessCameraProvider? = null
    private var lifecycleOwner: ControlledLifecycleOwner? = null
    private var onFace: (() -> Unit)? = null
    private val isRunning = AtomicBoolean(false)
    private val lastAnalyzedAtMs = AtomicLong(0L)

    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    fun isLowRamDevice(): Boolean =
        context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true

    fun hasFrontCamera(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)

    /** Permission, front camera hardware, and not a low-RAM (Android Go) device. */
    fun isUsable(): Boolean = hasCameraPermission() && hasFrontCamera() && !isLowRamDevice()

    fun start(onFace: () -> Unit) {
        if (!isUsable()) {
            Timber.d("Face wake not usable")
            return
        }
        if (!isRunning.compareAndSet(false, true)) return
        this.onFace = onFace
        ProcessCameraProvider.getInstance(context).addListener(
            {
                if (!isRunning.get()) return@addListener
                try {
                    val provider = ProcessCameraProvider.getInstance(context).get()
                    cameraProvider = provider
                    if (!provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        Timber.d("Face wake: CameraX found no front camera")
                        stop()
                        return@addListener
                    }
                    bindCamera(provider)
                } catch (error: IllegalStateException) {
                    Timber.w(error, "Face wake: provider failed")
                    stop()
                } catch (error: IllegalArgumentException) {
                    Timber.w(error, "Face wake: provider failed")
                    stop()
                } catch (error: java.util.concurrent.ExecutionException) {
                    Timber.w(error, "Face wake: provider failed")
                    stop()
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    Timber.w(error, "Face wake: provider interrupted")
                    stop()
                }
            },
            mainExecutor,
        )
    }

    private fun bindCamera(provider: ProcessCameraProvider) {
        provider.unbindAll()
        val owner = ControlledLifecycleOwner().also {
            lifecycleOwner = it
            it.start()
        }
        val analysisSize = Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT)
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            analysisSize,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        ),
                    )
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { useCase ->
                useCase.setAnalyzer(analysisExecutor) { image -> analyze(image) }
            }
        try {
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            Timber.d("Face wake started")
        } catch (error: IllegalArgumentException) {
            Timber.w(error, "Face wake: bind failed")
            stop()
        } catch (error: IllegalStateException) {
            Timber.w(error, "Face wake: bind failed")
            stop()
        }
    }

    // ImageProxy.image is @ExperimentalGetImage; Kotlin @OptIn is not enough for Android lint.
    @SuppressLint("UnsafeOptInUsageError")
    private fun analyze(imageProxy: ImageProxy) {
        val now = android.os.SystemClock.elapsedRealtime()
        val previous = lastAnalyzedAtMs.get()
        if (now - previous < MIN_FRAME_INTERVAL_MS) {
            imageProxy.close()
            return
        }
        lastAnalyzedAtMs.set(now)
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        detector.process(input)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    mainExecutor.execute { onFace?.invoke() }
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun stop() {
        if (!isRunning.getAndSet(false) && cameraProvider == null && lifecycleOwner == null) return
        onFace = null
        mainExecutor.execute {
            runCatching { cameraProvider?.unbindAll() }
            cameraProvider = null
            lifecycleOwner?.destroy()
            lifecycleOwner = null
            Timber.d("Face wake stopped")
        }
    }

    private class ControlledLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry

        fun start() {
            registry.currentState = Lifecycle.State.STARTED
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
