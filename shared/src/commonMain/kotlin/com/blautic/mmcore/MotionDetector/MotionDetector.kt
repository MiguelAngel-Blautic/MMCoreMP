package MotionDetector

import Model
import Utils.Logger
import com.blautic.mmcore.MotionDetector.ModelInterpreterFactory
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.locks.SynchronizedObject
import io.ktor.utils.io.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.datetime.LocalDateTime
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.max



@OptIn(InternalAPI::class)
class MotionDetector(private val model: Model, val context: Any?) {
    interface MotionDetectorListener {
        fun onCorrectMotionRecognized(correctProb: Float, datasList: Array<Array<Array<Array<FloatArray>>>>)
        fun onOutputScores(outputScores: FloatArray, datasInferencia: List<Triple<Int, List<Pair<Int, Float>>, Int>>)
        fun onIncorrectMotionRecognized(mensaje: String)
        fun onTimeCorrect(time: Float)
        fun onIntentRecognized()
    }
    interface ModelDownloadCallback {
        fun onCorrect()
        fun onError(error: String)
    }

    private var motionDetectorListener: MotionDetectorListener? = null
    var ubralObjetivo = 80
    var zonaA = 65
    var zonaB = 60
    var zonaC = 50
    var contador_reintentos = 0
    private var isStarted = false
    private var inferenceInterface = ModelInterpreterFactory.create(context)
    private val lock = SynchronizedObject()
    private var estado = 1
    private var inicioT: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    fun setMotionDetectorListener(motionDetectorListener: MotionDetectorListener?) {
        this.motionDetectorListener = motionDetectorListener
    }

    fun start(){
        inferenceInterface.start(model.id.toString(), object :ModelDownloadCallback {
            override fun onCorrect() {
                Logger.log(1, "MMCORE ERROR", "MODEL DOWNLOAD: EXITO")
                isStarted = true
                estado = 1
            }
            override fun onError(error: String) {
                Logger.log(2, "MMCORE ERROR", "MODEL DOWNLOAD: ${error}")
                contador_reintentos += 1
                if(contador_reintentos <= 4){
                    start()
                }else{
                    motionDetectorListener?.onIncorrectMotionRecognized("Download Model from Firebase Error")
                }
            }
        })
    }
    fun stop(){
        contador_reintentos = 0
        synchronized(lock) {
            isStarted = false
        }
        inferenceInterface?.let {
            it.close()
        }
    }

    fun calcularDuracionSegundos(inicioT: LocalDateTime): Float {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        val inicioInstant = inicioT.toInstant(TimeZone.currentSystemDefault())
        val nowInstant = now.toInstant(TimeZone.currentSystemDefault())

        val durationMillis = nowInstant.toEpochMilliseconds() - inicioInstant.toEpochMilliseconds()

        return durationMillis / 1000f
    }

    fun inference(datasList: Array<Array<Array<Array<FloatArray>>>>, inferenceCounter: Long, datosExpl: List<Triple<Int, List<Pair<Int, Float>>, Int>> = listOf()){
        synchronized(lock) {
            Logger.log(1, "MMCORE", "inferencia activa... $isStarted")
            inferenceInterface.takeIf { isStarted }?.let { interpreter ->
                Logger.log(1, "MMCORE", "Calculando inferencia $inferenceCounter")
                val mapOfIndicesToOutputs = interpreter.runForMultipleInputsOutputs(datasList)
                if(mapOfIndicesToOutputs.isNotEmpty()) {
                    //Log.d("Resultados", "${mapOfIndicesToOutputs[0]?.get(0)?.get(0)} || ${mapOfIndicesToOutputs[0]?.get(0)?.get(1)}")
                    var totalProb = 0f
                    mapOfIndicesToOutputs[0]?.get(0)?.forEach { prob -> totalProb += prob }
                    val scores = FloatArray(model.movements.size)
                    for (i in 0 until model.movements.size) {
                        scores[i] =
                            ((mapOfIndicesToOutputs[0]?.get(0)?.get(i)
                                ?: 0f) * 100f) / totalProb
                    }
                    val nombres = model.movements.sortedBy { it1 -> it1.fldSLabel }
                    Logger.log(
                        1,
                        "MMCORE",
                        "Resultados inferencia $inferenceCounter: ${nombres.mapIndexed { index1, nom -> "${nom.fldSLabel}:${scores[index1]} " }}"
                    )
                    var indiceCorrect = model.movements.sortedBy { it1 -> it1.fldSLabel }
                        .indexOfFirst { it1 -> it1.fldSLabel == model.fldSName }
                    if (indiceCorrect < 0) {
                        indiceCorrect = 0
                    }
                    var indiceOther = model.movements.sortedBy { it1 -> it1.fldSLabel }
                        .indexOfFirst { it1 -> it1.fldSLabel == "Other" || it1.fldSLabel == "other" }
                    if (indiceOther < 0) {
                        indiceOther = 0
                    }
                    Logger.log(
                        1,
                        "MMCORE",
                        "Resultado inferencia $inferenceCounter = ${scores[indiceCorrect]}"
                    )
                    /*if(model.fldSName > "Other"){
                        scores = scores.reversedArray()
                    }*/
                    val maximo = scores.maxOrNull() ?: 0f
                    val indiceMaximo = scores.indexOfFirst { it1 -> it1 == maximo }
                    val segundoMax =
                        scores.filterIndexed { index, fl -> index != indiceMaximo }.maxOrNull()
                            ?: 0f
                    val noCorrectMax =
                        scores.filterIndexed { index, fl -> index != indiceCorrect }.maxOrNull()
                            ?: 0f
                    val resultado = ((scores[indiceCorrect] - noCorrectMax) / 2f) + 50
                    if (indiceMaximo != indiceOther && indiceMaximo != indiceCorrect) {
                        val valorEtiqueta = ((scores[indiceMaximo] - segundoMax) / 2f) + 50
                        if (valorEtiqueta > ubralObjetivo) {
                            motionDetectorListener?.onIncorrectMotionRecognized(model.movements.sortedBy { it1 -> it1.fldSLabel }[indiceMaximo].fldSLabel)
                        }
                    }
                    val A = zonaA / 80f
                    val B = zonaB / 80f
                    val C = zonaC / 80f
                    when {
                        resultado >= ubralObjetivo -> { // > 80 E
                            if (estado != 3) {
                                inicioT = Clock.System.now()
                                    .toLocalDateTime(TimeZone.currentSystemDefault())
                            }
                            estado = 3
                        }

                        ubralObjetivo > resultado && resultado >= (ubralObjetivo * A) -> { // 80 - 65 D
                            estado = max(estado, 2)
                        }

                        (ubralObjetivo * A) > resultado && resultado >= (ubralObjetivo * B) -> { // 65 - 60 C
                            if (estado == 3) {
                                estado = 4
                                motionDetectorListener?.onTimeCorrect(
                                    calcularDuracionSegundos(
                                        inicioT
                                    )
                                )
                            }
                            estado = max(estado, 2)
                        }

                        (ubralObjetivo * B) > resultado && resultado >= (ubralObjetivo * C) -> { // 60 - 50 B
                            if (estado == 3) {
                                estado = 4
                                motionDetectorListener?.onTimeCorrect(
                                    calcularDuracionSegundos(
                                        inicioT
                                    )
                                )
                            }
                        }

                        else -> { // < 50 A
                            if (estado == 2) {
                                motionDetectorListener?.onIntentRecognized()
                            }
                            if (estado == 3) {
                                motionDetectorListener?.onTimeCorrect(
                                    calcularDuracionSegundos(
                                        inicioT
                                    )
                                )
                            }
                            estado = 1
                        }
                    }
                    motionDetectorListener?.onOutputScores(floatArrayOf(resultado), datosExpl)
                }else{
                    Logger.log(2, "Inferencia", "Datos de resultado vacios")
                }
            }
        }
    }
}