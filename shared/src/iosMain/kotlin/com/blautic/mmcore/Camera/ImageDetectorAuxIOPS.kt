package com.blautic.mmcore.Camera

import Entity.BodyPart
import Entity.KeyPoint
import Entity.ObjetLabel
import Entity.Objeto
import Entity.Person
import Entity.PointF
import Utils.Logger
import cocoapods.MediaPipeTasksVision.MPPBaseOptions
import cocoapods.MediaPipeTasksVision.MPPCategory
import cocoapods.MediaPipeTasksVision.MPPDelegate
import cocoapods.MediaPipeTasksVision.MPPDetection
import cocoapods.MediaPipeTasksVision.MPPImage
import cocoapods.MediaPipeTasksVision.MPPNormalizedLandmark
import cocoapods.MediaPipeTasksVision.MPPObjectDetector
import cocoapods.MediaPipeTasksVision.MPPObjectDetectorLiveStreamDelegateProtocol
import cocoapods.MediaPipeTasksVision.MPPObjectDetectorOptions
import cocoapods.MediaPipeTasksVision.MPPObjectDetectorResult
import cocoapods.MediaPipeTasksVision.MPPPoseLandmarker
import cocoapods.MediaPipeTasksVision.MPPPoseLandmarkerLiveStreamDelegateProtocol
import cocoapods.MediaPipeTasksVision.MPPPoseLandmarkerOptions
import cocoapods.MediaPipeTasksVision.MPPPoseLandmarkerResult
import cocoapods.MediaPipeTasksVision.MPPRunningMode
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.pointed
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreMedia.CMSampleBufferRef
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.NSFileManager
import platform.darwin.NSInteger
import platform.darwin.NSObject
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.temporaryDirectory
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
class ImageDetectorAuxIOPS: NSObject(), MPPObjectDetectorLiveStreamDelegateProtocol, MPPPoseLandmarkerLiveStreamDelegateProtocol {

    private var objectDetector: MPPObjectDetector? = null
    private var poseLandmarker: MPPPoseLandmarker? = null

    private val _errorFlow = MutableStateFlow<Pair<String, Int>?>(null)
    val errorFlow = _errorFlow.asStateFlow()
    private val _personFlow = MutableStateFlow<List<Person>?>(null)
    val personFlow = _personFlow.asStateFlow()
    private val _objectFlow = MutableStateFlow<List<Objeto>?>(null)
    val objectFlow = _objectFlow.asStateFlow()

    fun detectAsync(mpImage: MPPImage, frameTime: Long) {
        objectDetector?.detectAsyncImage(mpImage, frameTime, null)
        poseLandmarker?.detectAsyncImage(mpImage, frameTime, null)
    }
    fun clearObjectDetector() {
        objectDetector?.finalize()
        objectDetector = null
    }
    fun clearPoseDetector() {
        poseLandmarker?.finalize()
        poseLandmarker = null
    }

    fun setupObjectDetector(objectLabels: MutableList<ObjetLabel>) {
        // Set general detection options, including number of used threads
        val baseOptionsBuilder = MPPBaseOptions()

        // Use the specified hardware for running the model. Default to CPU
        baseOptionsBuilder.setDelegate(MPPDelegate.MPPDelegateCPU)
        val modelName = downloadModel("efficientdet-lite0.tflite")

        baseOptionsBuilder.setModelAssetPath(modelName?: "")

        try {
            var options = MPPObjectDetectorOptions()
            options.setBaseOptions(baseOptionsBuilder)
            options.setScoreThreshold(0.5F)
            options.setRunningMode(MPPRunningMode.MPPRunningModeLiveStream)
            options.setCategoryAllowlist(objectLabels.map { it.label })
            options.setMaxResults(3)
            options.setRunningMode(MPPRunningMode.MPPRunningModeLiveStream)
            options.objectDetectorLiveStreamDelegate = this
            objectDetector = MPPObjectDetector(options, null)
        } catch (e: Exception) {
            _errorFlow.value = Pair(
                "Object detector failed to initialize. See error logs for " + "details",
                1
            )
            /*Logger.log(1, "OBJECT_DETECTOR", "Object detector failed to load model with error: $e"
            )*/
        }
    }
    private fun downloadModel(modelType: String): String? {
        return try {
            val urlObj = NSURL.URLWithString("https://sinequanon-smartdispensing.com/models/$modelType")
            val data = NSData.dataWithContentsOfURL(urlObj!!)

            val fileManager = NSFileManager.defaultManager
            val tempDir = fileManager.temporaryDirectory.path
            val filePath = tempDir + "/$modelType"

            data?.writeToFile(filePath, atomically = true)
            Logger.log(1, "DESCARGADO", "Descargado en $filePath")
            filePath
        } catch (e: Exception) {
            Logger.log(2, "ERROR DESCARGA", "${e.message}")
            null
        }
    }
    fun setupPoseDetector() {
        // Set general pose landmarker options
        val baseOptionBuilder = MPPBaseOptions()

        // Use the specified hardware for running the model. Default to CPU
        baseOptionBuilder.setDelegate(MPPDelegate.MPPDelegateCPU)

        val modelName = downloadModel("pose_landmarker_lite.task")
        val fileExists = NSFileManager.defaultManager.fileExistsAtPath(modelName?: "")
        if (!fileExists) {
            println("Error: El archivo del modelo no se encuentra en la ruta: $modelName")
        } else {
            println("Modelo encontrado en: $modelName")
        }
        baseOptionBuilder.setModelAssetPath(modelName?:"")

        try {
            //Logger.log(1, "POSE_DETECTOR", "Initializing base option $baseOptionBuilder")
            var options = MPPPoseLandmarkerOptions()
            //Logger.log(1, "POSE_DETECTOR", "Initializing options $options")
            options.setBaseOptions(baseOptionBuilder)
            options.setMinPoseDetectionConfidence(0.3F)
            options.setMinTrackingConfidence(0.3F)
            options.setMinPosePresenceConfidence(0.3F)
            options.setRunningMode(MPPRunningMode.MPPRunningModeLiveStream)
            options.setNumPoses(1)
            //Logger.log(1, "POSE_DETECTOR", "Options configured")
            options.setPoseLandmarkerLiveStreamDelegate(this)
            //Logger.log(1, "POSE_DETECTOR", "Set delegate: $options")
            poseLandmarker = MPPPoseLandmarker(options, null)
            //Logger.log(1, "POSE_DETECTOR", "pose landmarker initialized")
        } catch (e: Exception) {
            Logger.log(1, "IMAGE_DETECTOR",
                "Image classifier failed to load model with error: $e"
            )
        }
    }

    override fun poseLandmarker(
        poseLandmarker: MPPPoseLandmarker,
        didFinishDetectionWithResult: MPPPoseLandmarkerResult?,
        timestampInMilliseconds: NSInteger,
        error: NSError?
    ) {
        val keypoints = mutableListOf<KeyPoint>()
        val res = mutableListOf<Person>()
        var score = 0f
        didFinishDetectionWithResult.let { it1 ->
            if(it1?.landmarks()?.isNotEmpty() == true) {
                it1.landmarks().forEach { it2 ->
                    val lm = it2 as List<*>
                    lm.forEachIndexed { index, landmarkt ->
                        val point = landmarkt as MPPNormalizedLandmark
                        val part = BodyPart.fromMediaPipe(index)
                        if (part != null) {
                            keypoints.add(
                                KeyPoint(
                                    part,
                                    PointF(point.x * 480, point.y * 640),
                                    (point.visibility ?: NSNumber(0)).floatValue
                                )
                            )
                            score += (point.visibility ?: NSNumber(0)).floatValue
                        }
                    }
                    if(keypoints.isNotEmpty()) {
                        score /= keypoints.size
                    }
                    res.add(Person(keypoints.sortedBy{ it.bodyPart.position }, score))
                }
            }
        }
        _personFlow.value = res
    }

    override fun objectDetector(
        objectDetector: MPPObjectDetector,
        didFinishDetectionWithResult: MPPObjectDetectorResult?,
        timestampInMilliseconds: NSInteger,
        error: NSError?
    ) {
        val res = mutableListOf<Objeto>()
        didFinishDetectionWithResult.let { it1 ->
            it1?.detections()?.map{
                val obj = it as MPPDetection
                val boxRect = obj.boundingBox.useContents {
                    Pair(
                        PointF(origin.y.toFloat(), (480 -  origin.x).toFloat()),
                        PointF((origin.y + size.height).toFloat(), (480 - (origin.x + size.width)).toFloat())
                    )
                }
                Pair(boxRect, it.categories()[0] as MPPCategory)
            }?.forEach {
                res.add(Objeto(ObjetLabel.fromLabel(it.second.categoryName ?: ""), it.first, it.second.score()))
            }
        }
        _objectFlow.value = res
    }

}
