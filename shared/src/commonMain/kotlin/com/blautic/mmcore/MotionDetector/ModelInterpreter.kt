package com.blautic.mmcore.MotionDetector

import MotionDetector.MotionDetector

abstract class ModelInterpreter {
    abstract fun start(url: String, modelDownloadCallback: MotionDetector.ModelDownloadCallback)
    abstract fun close()
    abstract fun runForMultipleInputsOutputs(
        inputs: Array<Array<Array<Array<FloatArray>>>>
    ): Map<Int, Array<FloatArray>>
}

expect class ModelInterpreterFactory {
    companion object {
        fun create(context: Any?): ModelInterpreter
    }
}