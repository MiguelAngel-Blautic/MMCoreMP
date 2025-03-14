package com.blautic.mmcore.MotionDetector

import MotionDetector.MotionDetector
import android.content.Context
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import okhttp3.OkHttpClient
import okhttp3.Request

class ModelInterpreterAndroid(val context: Context): ModelInterpreter() {
    private var interpreter: Interpreter? = null
    private var modelFile: File? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val client = OkHttpClient()

    private fun downloadModel(url: String): File? {
        val request = Request.Builder().url("https://sinequanon-smartdispensing.com/models/"+url+".tflite").build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) return null

        val inputStream: InputStream = response.body!!.byteStream()
        val modelFile = File(context.cacheDir, "model.tflite")

        FileOutputStream(modelFile).use { output ->
            inputStream.copyTo(output)
        }

        return modelFile
    }

    override fun start(url: String, modelDownloadCallback: MotionDetector.ModelDownloadCallback) {
        scope.launch {
            try {
                val modelFile = downloadModel(url)
                if (modelFile != null) {
                    interpreter = Interpreter(modelFile)
                    withContext(Dispatchers.Main) {
                        modelDownloadCallback.onCorrect()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        modelDownloadCallback.onError("No se pudo descargar el modelo.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    modelDownloadCallback.onError("Error descargando modelo: ${e.message}")
                }
            }
        }
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        modelFile = null
    }

    override fun runForMultipleInputsOutputs(
        inputs: Array<Array<Array<Array<FloatArray>>>>
    ): Map<Int, Array<FloatArray>> {
        if (interpreter == null) throw IllegalStateException("Modelo no inicializado.")

        val outputShape = mapOf(0 to arrayOf(FloatArray(2){0f})) // Ajusta según el modelo

        interpreter?.runForMultipleInputsOutputs(inputs, outputShape)

        return outputShape
    }

}

actual class ModelInterpreterFactory {
    actual companion object {
        actual fun create(context: Any?): ModelInterpreter {
            return ModelInterpreterAndroid(context as Context)
        }
    }
}