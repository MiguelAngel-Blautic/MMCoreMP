package com.blautic.mmcore.Camera

import Utils.Logger
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureSessionPresetPhoto
import platform.AVFoundation.AVCaptureStillImageOutput
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVVideoCodecJPEG
import platform.AVFoundation.AVVideoCodecKey
import platform.AVFoundation.position
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreImage.CIImage
import platform.UIKit.UIImage
import platform.CoreMedia.*
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreVideo.CVImageBufferRef
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIColor
import platform.UIKit.UIScreen
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.IO
import platform.AVFoundation.AVCaptureSessionPreset640x480
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGDataProviderCopyData
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetBitsPerPixel
import platform.CoreGraphics.CGImageGetDataProvider
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.CoreImage.CIContext
import platform.CoreImage.createCGImage
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.kCVReturnSuccess
import platform.darwin.dispatch_get_global_queue

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CameraComposable(
    modifier: Modifier,
    onImageAnalyzed: (Any?) -> Unit,
    content: @Composable BoxScope.() -> Unit
){
    Box(modifier = modifier.fillMaxSize()) {
        CameraSetupView(
            modifier = Modifier.fillMaxSize(),
            onImageAnalyzed = {
                if(it != null) {
                    onImageAnalyzed(it)
                }
            }
        )
        content()
    }
}


@OptIn(ExperimentalForeignApi::class)
@Composable
fun CameraSetupView(
    modifier: Modifier,
    onImageAnalyzed: (CMSampleBufferRef?) -> Unit,
) {
    val scope = rememberCoroutineScope()

    val device = AVCaptureDevice.devicesWithMediaType(AVMediaTypeVideo).firstOrNull {
        (it as AVCaptureDevice).position == AVCaptureDevicePositionBack
    } as? AVCaptureDevice

    val input =
        device?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, null) } as AVCaptureDeviceInput

    val output = AVCaptureStillImageOutput().apply {
        outputSettings = mapOf(AVVideoCodecKey to AVVideoCodecJPEG)
    }
    val session = AVCaptureSession()
    device.lockForConfiguration(null)
    device.activeVideoMinFrameDuration = CMTimeMakeWithSeconds(0.1, 1000)
    device.activeVideoMaxFrameDuration = CMTimeMakeWithSeconds(0.1, 1000)
    device.unlockForConfiguration()
    session.sessionPreset = AVCaptureSessionPreset640x480
    session.addInput(input)
    session.addOutput(output)
    val videoOutput = AVCaptureVideoDataOutput()
    videoOutput.videoSettings = mapOf(kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_32BGRA)
    videoOutput.alwaysDiscardsLateVideoFrames = true
    session.addOutput(videoOutput)
    UIKitViewController(
        onRelease = {
            session.stopRunning()
        },
        factory = {
            setupCamera(
                session = session,
                videoOutput = videoOutput,
                onImageAnalyzed = { onImageAnalyzed(it) },
                scope
            )
        },
        modifier = modifier,
        properties = UIKitInteropProperties(
            isInteractive = true,
            isNativeAccessibilityEnabled = true
        )
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun setupCamera(
    session: AVCaptureSession,
    videoOutput: AVCaptureVideoDataOutput,
    onImageAnalyzed: (CMSampleBufferRef?) -> Unit,
    scope: CoroutineScope
): UIViewCapturer {
    val cameraPreviewLayer = AVCaptureVideoPreviewLayer(session = session)
    val controller = UIViewCapturer(
        onImageAnalyzed = { onImageAnalyzed(it) },
        scope
    )
    val container = UIView(frame = CGRectMake(0.0, 0.0, 1.0, 1.0))
    controller.setView(container)
    container.layer.addSublayer(cameraPreviewLayer)
    dispatch_async(dispatch_get_main_queue()) {
        cameraPreviewLayer.setFrame(container.bounds)
    }
    cameraPreviewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill

    session.startRunning()

    videoOutput.setSampleBufferDelegate(
            controller,
            queue = dispatch_get_global_queue(0, 0u)
        )
    container.layer.addSublayer(cameraPreviewLayer)
    //controller.startFrameProcessing()
    return controller
}

@OptIn(ExperimentalForeignApi::class)
class UIViewCapturer(
    private val onImageAnalyzed: (CMSampleBufferRef?) -> Unit,
    private val scope: CoroutineScope
) : UIViewController("UIViewCapturer", null), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection
    ) {
        if (didOutputSampleBuffer == null) return
        onImageAnalyzed(didOutputSampleBuffer)
    }
}

