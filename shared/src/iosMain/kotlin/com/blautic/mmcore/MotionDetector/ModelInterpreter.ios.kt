package com.blautic.mmcore.MotionDetector

import MotionDetector.MotionDetector
import Utils.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.*
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.CoreML.MLFeatureProviderProtocol
import platform.CoreML.MLFeatureType
import platform.CoreML.MLFeatureTypeInt64
import platform.CoreML.MLFeatureValue
import platform.CoreML.MLModel
import platform.CoreML.MLModelConfiguration
import platform.CoreML.MLMultiArray
import platform.CoreML.MLMultiArrayConstraint
import platform.CoreML.MLMultiArrayDataTypeFloat
import platform.CoreML.MLMultiArrayDataTypeFloat32
import platform.CoreML.compileModelAtURL
import platform.CoreML.create
import platform.CoreML.getMutableBytesWithHandler
import platform.Foundation.NSCoder
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSKeyedArchiver
import platform.Foundation.NSKeyedUnarchiver
import platform.Foundation.NSMutableData
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.appendBytes
import platform.Foundation.create
import platform.Foundation.data
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.temporaryDirectory
import platform.Foundation.writeToFile
import platform.darwin.Float32
import platform.darwin.NSObject

class ModelInterpreterIOS(): ModelInterpreter() {
    var model: MLModel? = null

    // Función para descargar el modelo desde la URL
    private fun downloadModel(url: String): String? {
        return try {
            val urlObj = NSURL.URLWithString("https://sinequanon-smartdispensing.com/models/"+url+".mlmodel")
            val data = NSData.dataWithContentsOfURL(urlObj!!)

            val fileManager = NSFileManager.defaultManager
            val tempDir = fileManager.temporaryDirectory.path
            val filePath = tempDir + "/model.mlmodel"

            data?.writeToFile(filePath, atomically = true)
            Logger.log(1, "DESCARGADO", "Descargado en $filePath")
            filePath
        } catch (e: Exception) {
            Logger.log(2, "ERROR DESCARGA", "${e.message}")
            null
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun start(url: String, modelDownloadCallback: MotionDetector.ModelDownloadCallback) {
        val modelPath = downloadModel(url)
        if (modelPath != null) {
            try {
                Logger.log(1, "DESCARGADO", "Guardado en $modelPath")
                val fileUrl = NSURL.fileURLWithPath(modelPath)
                val configuration = MLModelConfiguration()
                Logger.log(1, "DESCARGADO", "URL en $fileUrl")
                // Compilar el modelo si es necesario
                val compiledUrl = MLModel.compileModelAtURL(fileUrl, null)
                Logger.log(1, "DESCARGADO", "URL compilada en $compiledUrl")
                model = MLModel.modelWithContentsOfURL(compiledUrl!!, configuration,null)

                if (model != null) {
                    modelDownloadCallback.onCorrect()
                } else {
                    modelDownloadCallback.onError("Error al cargar el modelo")
                }
            } catch (e: Exception) {
                modelDownloadCallback.onError(e.message ?: "Error al leer el modelo")
            }
        } else {
            modelDownloadCallback.onError("Error al descargar el modelo")
        }
    }

    override fun close() {
        model = null
    }

    @OptIn(ExperimentalForeignApi::class)
    fun toNSData(input: List<Float>): NSData? {
        return memScoped {
            val count = input.size
            val sizeInBytes = count * sizeOf<FloatVar>()
            val ptr = allocArray<FloatVar>(count)
            for (i in 0 until count) {
                ptr[i] = input[i]
            }
            NSData.dataWithBytes(ptr, sizeInBytes.convert())
        }
    }

    private fun toNSCoder(datos: List<Triple<String, Any, Int>>): NSCoder{
        val mutableData = NSMutableData.data() as NSMutableData
        val archiver = NSKeyedArchiver(forWritingWithMutableData = mutableData)
        datos.forEach {
            when(it.third){
                1 -> { archiver.encodeInt(it.second as Int, forKey = it.first) }
                2 -> { archiver.encodeBool(it.second as Boolean, forKey = it.first) }
                3 -> { archiver.encodeFloat(it.second as Float, forKey = it.first) }
                else -> { archiver.encodeObject(it.second, forKey = it.first) }
            }
        }
        archiver.finishEncoding()
        var unarchived = NSKeyedUnarchiver(forReadingWithData = mutableData)
        unarchived.requiresSecureCoding = false
        return unarchived
    }
    @OptIn(BetaInteropApi::class)
    private fun convertListToNSCoder(input: Array<Array<Array<FloatArray>>>): NSCoder {
        val flatArray = input.flatMap { it1 -> it1.flatMap { it2 -> it2.flatMap { it3 -> it3.map { it4 -> it4 } } } }
        val shape = listOf(NSNumber(input.size), NSNumber(input[0].size), NSNumber(input[0][0].size), NSNumber(input[0][0][0].size))
        val strides = listOf(
            NSNumber((input[0].size * input[0][0].size * input[0][0][0].size)),
            NSNumber((input[0][0].size * input[0][0][0].size)),
            NSNumber(input[0][0][0].size),
            NSNumber(1)
        )
        val multiArray = MLMultiArray.create(shape = shape, dataType = MLMultiArrayDataTypeFloat, strides = strides)
        fillMLMultiArray(multiArray, flatArray)
        return toNSCoder(listOf(Triple("data", multiArray, 0)))
    }

    private fun convertListToMultiArray(input: Array<Array<Array<FloatArray>>>): MLMultiArray {
        val flatArray = input.flatMap { it1 -> it1.flatMap { it2 -> it2.flatMap { it3 -> it3.map { it4 -> it4 } } } }
        val shape = listOf(NSNumber(input.size), NSNumber(input[0].size), NSNumber(input[0][0].size), NSNumber(input[0][0][0].size))
        val strides = listOf(
            NSNumber((input[0].size * input[0][0].size * input[0][0][0].size)),
            NSNumber((input[0][0].size * input[0][0][0].size)),
            NSNumber(input[0][0][0].size),
            NSNumber(1)
        )
        val multiArray = MLMultiArray.create(shape = shape, dataType = MLMultiArrayDataTypeFloat, strides = strides)
        fillMLMultiArray(multiArray, flatArray)
        return multiArray
    }

    @OptIn(ExperimentalForeignApi::class)
    fun fillMLMultiArray(multiArray: MLMultiArray, data: List<Float>) {
        // Verifica que la cantidad de datos sea la esperada
        if (data.size != multiArray.count.toInt()) {
            error("El tamaño de los datos no coincide con la cantidad de elementos del multiarray")
        }
        multiArray.getMutableBytesWithHandler { pointer, size, strides ->
            // Se asume que los datos están en formato Float (4 bytes cada uno)
            val floatPtr = pointer!!.reinterpret<FloatVar>()
            for (i in data.indices) {
                floatPtr[i] = data[i]
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun runForMultipleInputsOutputs(
        inputs: Array<Array<Array<Array<FloatArray>>>>
    ): Map<Int, Array<FloatArray>> {
        var results = mapOf<Int, Array<FloatArray>>()
        if (model == null) {
            return results
        }
        try {
            /*val inputDict = inputArray.mapIndexed { index: Int, nsNumbers: List<NSNumber> ->
                "capa_$index" to MLFeatureValue(convertListToNSCoder(nsNumbers))
            }.toMap()*/
            //val feature = MLFeatureValue(convertListToNSCoder(inputs[0]))
            val inputDict = inputs.mapIndexed { index: Int, nsNumbers ->
                "capa_$index" to MLFeatureValue.featureValueWithMultiArray(convertListToMultiArray(nsNumbers))
            }.toMap()
            val featuresProvider = CustomFeatureProvider(inputDict)
            val outputMLArray = model!!.predictionFromFeatures(featuresProvider, null)
            outputMLArray?.let {
                results = mlMultiArrayToMap(it.featureValueForName("Identity")!!.multiArrayValue!!)
                Logger.log(1, "Features", "${results[0]!![0]}")
            }
        } catch (e: Exception) {
            Logger.log(2,"Error", "Error en la inferencia: ${e.message}")
        }

        return results
    }
}

@OptIn(ExperimentalForeignApi::class)
fun mlMultiArrayToMap(mlMultiArray: MLMultiArray): Map<Int, Array<FloatArray>> {
    val resultMap = mutableMapOf<Int, Array<FloatArray>>()
    val count = mlMultiArray.count.toInt()
    val resultArray = arrayOf(FloatArray(count){0f})
    val pointer = mlMultiArray.dataPointer?.reinterpret<FloatVar>()

    if (pointer != null) {
        for (i in 0 until count) {
            val value = pointer[i]
            resultArray[0][i] = value
        }
        resultMap[0] = resultArray
    } else {
        Logger.log(2, "Error", "dataPointer es nulo.")
    }

    return resultMap
}

class CustomFeatureProvider(private val inputDict: Map<String, MLFeatureValue>) : NSObject(), MLFeatureProviderProtocol {
    override fun featureNames(): Set<*> {
        return inputDict.keys
    }

    override fun featureValueForName(featureName: String): MLFeatureValue? {
        return inputDict[featureName]
    }
}

actual class ModelInterpreterFactory {
    actual companion object {
        actual fun create(context: Any?): ModelInterpreter {
            return ModelInterpreterIOS()
        }
    }
}