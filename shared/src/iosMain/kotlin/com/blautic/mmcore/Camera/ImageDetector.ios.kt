package com.blautic.mmcore.Camera

import Entity.*
import Utils.Logger
import cocoapods.MediaPipeTasksVision.MPPImage
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.UIKit.UIImage
import kotlinx.datetime.Clock
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFGetRetainCount
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGDataProviderCopyData
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetBitsPerPixel
import platform.CoreGraphics.CGImageGetDataProvider
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreImage.CIImage
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVImageBufferRef
import platform.CoreVideo.*
import platform.CoreImage.*
import platform.UIKit.UIColor
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
class ImageDetectorIOS (callback: ImageDetectorCallback): ImageDetector(callback) {
    private val imageDetectorAux = ImageDetectorAuxIOPS()
    private var scope = CoroutineScope(Dispatchers.Main)
    private var lastTime = 0L

    override fun detectLiveStreamImage(bitmap: Any, rotation: Float){
        //var finalBitmap = rotateImage(bitmap as UIImage, rotation.toDouble())
        val frameTime = Clock.System.now().toEpochMilliseconds()
        if(frameTime - lastTime >= 100) {
            lastTime = frameTime
            //Logger.log(1, "CAMARA INFERENCE", "UIImage: $uiImage")
            Logger.log(1, "CAMARA INFERENCE", "Inicio TRANSFORMACION")
            try {
                val mpImage = MPPImage(bitmap as CMSampleBufferRef, null)
            }catch (e: Exception){
                Logger.log(1, "CAMARA INFERENCE", "Error: ${e.message}")
            }
            //detectAsync( mpImage, frameTime)
            Logger.log(1, "CAMARA INFERENCE", "FIN TRANSFORMACION")
        }
    }

    init{
        observeAux()
    }
    fun observeAux(){
        scope.launch(Dispatchers.Main) {
            imageDetectorAux.errorFlow.collect{item ->
                if(item != null){
                    callback.onError(item.first, item.second)
                }
            }
        }
        scope.launch(Dispatchers.Main) {
            imageDetectorAux.personFlow.collect{item ->
                if(item != null){
                    Logger.log(1, "CAMARA", "PERSONA DETECTED ${item.map { it.score }}")
                    callback.onResultsPersons(item)
                }
            }
        }
        scope.launch(Dispatchers.Main) {
            imageDetectorAux.objectFlow.collect{item ->
                if(item != null){
                    callback.onResultsObject(item)
                }
            }
        }
    }

    fun detectAsync(mpImage: MPPImage, frameTime: Long) {
        imageDetectorAux.detectAsync(mpImage, frameTime)
    }

    override fun clearObjectDetector() {
        imageDetectorAux.clearObjectDetector()
    }
    override fun clearPoseDetector() {
        imageDetectorAux.clearPoseDetector()
    }

    override fun setupObjectDetector() {
        imageDetectorAux.setupObjectDetector(objectLabels)
    }
    override fun setupPoseDetector() {
        imageDetectorAux.setupPoseDetector()
    }
}

actual class ImageDetectorFactory {
    actual companion object {
        actual fun create(context: Any?, callback: ImageDetectorCallback): ImageDetector {
            return ImageDetectorIOS(callback)
        }
    }
}