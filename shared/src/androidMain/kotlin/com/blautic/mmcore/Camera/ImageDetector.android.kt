package com.blautic.mmcore.Camera

import Entity.ObjetLabel
import Entity.Objeto
import Entity.PointF
import Entity.*
import Utils.Logger
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import android.graphics.RectF
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult


class ImageDetectorAndroid (val context: Context, callback: ImageDetectorCallback): ImageDetector(callback){
    override fun detectLiveStreamImage(bitmap: Any, rotation: Float){
        var finalBitmap = rotateBitmap(bitmap as Bitmap, rotation, true)
        val frameTime = SystemClock.uptimeMillis()
        val mpImage = BitmapImageBuilder(finalBitmap).build()
        detectAsync(mpImage, frameTime)
    }
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float, isFrontCamera: Boolean): Bitmap{
        val rotateMatrix = Matrix().apply {
            postRotate(degrees)
            if (isFrontCamera) {
                // Si es la cámara frontal, aplica una transformación de espejo horizontal.
                postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
            }
        }

        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, rotateMatrix, false)
        bitmap.recycle()
        return rotatedBitmap
    }

    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        objectDetector?.detectAsync(mpImage, imageProcessingOptions, frameTime)
        poseLandmarker?.detectAsync(mpImage, frameTime)
    }


    private var objectDetector: ObjectDetector? = null
    private var poseLandmarker: PoseLandmarker? = null
    private lateinit var imageProcessingOptions: ImageProcessingOptions

    override fun clearObjectDetector() {
        objectDetector?.close()
        objectDetector = null
    }
    override fun clearPoseDetector() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    private fun returnLivestreamError(error: RuntimeException) {
        callback.onError(
            error.message ?: "An unknown error has occurred", 2
        )
    }
    private fun returnLivestreamResult(result: ObjectDetectorResult, input: MPImage) {
        val res = mutableListOf<Objeto>()
        result.let { it1 ->
            it1.detections()?.map{
                val boxRect = RectF(
                    480 - it.boundingBox().left,
                    it.boundingBox().top,
                    480 - it.boundingBox().right,
                    it.boundingBox().bottom
                )
                Pair(boxRect, it.categories()[0])
            }?.forEach {
                res.add(Objeto(ObjetLabel.fromLabel(it.second.categoryName()), Pair(PointF(it.first.top, it.first.left), PointF(it.first.bottom, it.first.right)), it.second.score()))
            }
        }
        callback.onResultsObject(res)
    }
    private fun returnLivestreamResult(result: PoseLandmarkerResult, input: MPImage) {
        val keypoints = mutableListOf<KeyPoint>()
        val res = mutableListOf<Person>()
        var score = 0f
        Log.d("PERSON", "$result")
        result.let { it1 ->
            if(it1.landmarks().isNotEmpty()) {
                it1.landmarks().forEach { lm ->
                    lm.forEachIndexed { index, landmarkt ->
                        val point = landmarkt
                        val part = BodyPart.fromMediaPipe(index)
                        if (part != null) {
                            keypoints.add(
                                KeyPoint(
                                    part,
                                    PointF(point.x() * 480, point.y() * 640),
                                    point.visibility().orElse(0f)
                                )
                            )
                            score += point.visibility().orElse(0f)
                        }
                    }
                    if(keypoints.isNotEmpty()) {
                        score /= keypoints.size
                    }
                    res.add(Person(keypoints.sortedBy{ it.bodyPart.position }, score))
                }
            }
        }
        callback.onResultsPersons(res)
    }

    override fun setupObjectDetector() {
        // Set general detection options, including number of used threads
        val baseOptionsBuilder = BaseOptions.builder()

        // Use the specified hardware for running the model. Default to CPU
        baseOptionsBuilder.setDelegate(Delegate.CPU)

        val modelName = "efficientdet-lite0.tflite"

        baseOptionsBuilder.setModelAssetPath(modelName)

        try {
            var optionsAux = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setScoreThreshold(0.5F).setRunningMode(RunningMode.LIVE_STREAM).setCategoryAllowlist(objectLabels.map { it.label })
                .setMaxResults(3).setRunningMode(RunningMode.LIVE_STREAM)
            optionsAux = optionsAux.setResultListener(this::returnLivestreamResult)
                .setErrorListener(this::returnLivestreamError)
            val options = optionsAux.build()

            imageProcessingOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(imageRotation).build()

            objectDetector = ObjectDetector.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            callback.onError(
                "Object detector failed to initialize. See error logs for details", 0
            )
            Logger.log(1, "OBJECT_DETECTOR", "TFLite failed to load model with error: " + e.message)
        } catch (e: RuntimeException) {
            callback.onError(
                "Object detector failed to initialize. See error logs for " + "details",
                1
            )
            Logger.log(1, "OBJECT_DETECTOR", "Object detector failed to load model with error: " + e.message
            )
        }
    }
    override fun setupPoseDetector() {
        // Set general pose landmarker options
        val baseOptionBuilder = BaseOptions.builder()

        // Use the specified hardware for running the model. Default to CPU
        baseOptionBuilder.setDelegate(Delegate.CPU)

        val modelName = "pose_landmarker_lite.task"

        baseOptionBuilder.setModelAssetPath(modelName)

        try {
            val baseOptions = baseOptionBuilder.build()
            var optionsBuilder =
                PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinPoseDetectionConfidence(0.3F)
                    .setMinTrackingConfidence(0.3F)
                    .setMinPosePresenceConfidence(0.3F)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumPoses(1)
            optionsBuilder = optionsBuilder.setResultListener(this::returnLivestreamResult)
                .setErrorListener(this::returnLivestreamError)
            val options = optionsBuilder.build()
            poseLandmarker =
                PoseLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            Logger.log(1, "IMAGE_DETECTOR", "MediaPipe failed to load the task with error: " + e
                .message
            )
        } catch (e: RuntimeException) {
            Logger.log(1, "IMAGE_DETECTOR",
                "Image classifier failed to load model with error: " + e.message
            )
        }
    }
}

actual class ImageDetectorFactory {
    actual companion object {
        actual fun create(context: Any?, callback: ImageDetectorCallback): ImageDetector {
            return ImageDetectorAndroid(context as Context, callback)
        }
    }
}