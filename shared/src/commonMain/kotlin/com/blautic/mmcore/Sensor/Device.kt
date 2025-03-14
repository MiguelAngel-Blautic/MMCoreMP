package Sensor

import Sensor.BleBytesParser.Companion.FORMAT_UINT8
import Utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.experimental.and
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class Device(val numDevice: Int, var address: String, val typeSensor: TypeSensor) {

    val ACC_SCALE_4G = 4f / 32767f
    val GYR_SCALE_1000 = 1000f / 32767f

    private val _connectionStateFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionStateFlow: StateFlow<ConnectionState> get() = _connectionStateFlow
    private val _groupedDataFlow = MutableStateFlow<List<Pair<Float, TypeData>>>(listOf())
    val groupedDataFlow: StateFlow<List<Pair<Float, TypeData>>> get() = _groupedDataFlow

    val sensorDatas = Array(typeSensor.Sensors.size){ SensorData() }
    var sensorDatos: Array<Pair<Float, TypeData>?> = Array(typeSensor.Sensors.size){ null }
    var sampleAI = 0
    var datasAI = 0
    var sampleHr = 0
    val maxEscale = Array(typeSensor.Sensors.size){ 1f }
    val lastStatusElectrodes = Array(typeSensor.numElectrodes){false}
    var cacheEnvelop: MutableList<Double> = mutableListOf()

    val emgBandStopPermanent: Array<ButterworthMM> = Array(typeSensor.numElectrodes){ ButterworthMM() }
    val emgBandPassPermanent: Array<ButterworthMM> = Array(typeSensor.numElectrodes){ButterworthMM()}
    val emgLowPassEnvelop: Array<ButterworthMM> = Array(typeSensor.numElectrodes){ButterworthMM()}
    val ecgHighPassPermanent: Array<ButterworthMM> = Array(typeSensor.numElectrodes){ ButterworthMM() }
    val ecgBandStopPermanent: Array<ButterworthMM> = Array(typeSensor.numElectrodes){ ButterworthMM() }

    fun activeFilters(){
        for (i in 0 until typeSensor.numElectrodes) {
            emgBandStopPermanent[i].bandStop(2, typeSensor.samplingRateInHz.toDouble(), 50.0, 5.0)
            emgBandPassPermanent[i].bandPass(2, typeSensor.samplingRateInHz.toDouble(), 50.0, 40.0)
            emgLowPassEnvelop[i].lowPass(2, typeSensor.samplingRateInHz.toDouble(), 4.0)
            ecgHighPassPermanent[i].highPass(2, typeSensor.samplingRateInHz.toDouble(), 0.5)
            ecgBandStopPermanent[i].bandPass(2, typeSensor.samplingRateInHz.toDouble(), 50.0, 20.0)
        }
    }


    fun setConnectionState(connectionState: ConnectionState) {
        _connectionStateFlow.value = connectionState
    }
    fun enableCache(sensorNum: Int, enable: Boolean){
        sensorDatas[sensorNum].enableCache = enable
    }
    fun enableCache(tipo: String, enable: Boolean){
        val index = typeSensor.Sensors.indexOfFirst { it.name == tipo }
        if(index < 0){
            return
        }
        sensorDatas[index].enableCache = enable
    }
    fun enableAllCache(enable: Boolean){
        sensorDatas.forEach {
            it.enableCache = enable
        }
    }
    fun getDataCache(sensorNum: Int, cant: Int): List<Pair<Float, Int>> {
        val solicitada = cant * typeSensor.Sensors[sensorNum].fs
        if(solicitada > sensorDatas[sensorNum].DataCache.size){
            return sensorDatas[sensorNum].DataCache.takeLast(sensorDatas[sensorNum].DataCache.size)
        }else{
            return sensorDatas[sensorNum].DataCache.takeLast(solicitada)
        }
    }
    fun getDataCache(tipo: String, cant: Int): List<Pair<Float, Int>> {
        return try {
            val solicitada = cant * typeSensor.Sensors[typeSensor.Sensors.indexOfFirst { it.name == tipo }].fs
            if (solicitada > sensorDatas[typeSensor.Sensors.indexOfFirst { it.name == tipo }].DataCache.size) {
                emptyList()
            } else {
                sensorDatas[typeSensor.Sensors.indexOfFirst { it.name == tipo }].DataCache.takeLast(solicitada)
            }
        }catch(e: Exception){
            listOf()
        }
    }
    fun clearCache(sensorNum: Int){
        sensorDatas[sensorNum].DataCache.clear()
    }
    fun clearCache(tipo: String){
        sensorDatas[typeSensor.Sensors.indexOfFirst { it.name == tipo }].DataCache.clear()
    }

    fun parseMPU(parse: BleBytesParser){
        val accScale = ACC_SCALE_4G / 4
        val gyrScale: Float = GYR_SCALE_1000 / 1000
        val sample = parse.getIntValue(BleBytesParser.FORMAT_SINT16)
        if (parse.getValue().size >= 8) {
            // sample++;
            val accX = (parse.getIntValue(BleBytesParser.FORMAT_SINT16) * accScale)
            val accY = (parse.getIntValue(BleBytesParser.FORMAT_SINT16) * accScale)
            val accZ = (parse.getIntValue(BleBytesParser.FORMAT_SINT16) * accScale)
            sensorDatas[0].add(accX, sample)
            sensorDatos[0] = Pair(accX, TypeData.AccX)
            sensorDatas[1].add(accY, sample)
            sensorDatos[1] = Pair(accY, TypeData.AccY)
            sensorDatas[2].add(accZ, sample)
            sensorDatos[2] = Pair(accZ, TypeData.AccZ)
        }
        if (parse.getValue().size >= 14) {
            val gyrX = (parse.getIntValue(BleBytesParser.FORMAT_SINT16) * gyrScale)
            val gyrY = (parse.getIntValue(BleBytesParser.FORMAT_SINT16) * gyrScale)
            val gyrZ = (parse.getIntValue(BleBytesParser.FORMAT_SINT16) * gyrScale)
            sensorDatas[3].add(gyrX, sample)
            sensorDatos[3] = Pair(gyrX, TypeData.GyrX)
            sensorDatas[4].add(gyrY, sample)
            sensorDatos[4] = Pair(gyrY, TypeData.GyrY)
            sensorDatas[5].add(gyrZ, sample)
            sensorDatos[5] = Pair(gyrZ, TypeData.GyrZ)
        }
        datasAI += 1
        if(datasAI >= 20 && sensorDatas[0].DataCache.size >= 20){
            val ai = calcAi(sensorDatas.slice(0..5).map { it1 -> it1.DataCache.takeLast(20) })
            sensorDatas[6].add(ai, sampleAI)
            sensorDatos[6] = Pair(ai, TypeData.AI)
            sampleAI += 1
            datasAI = 0
        }
    }
    fun parseHR(parse: BleBytesParser){
        val hr = parse.getIntValue(FORMAT_UINT8)
        Logger.log(1, "HR1", "${hr}")
        sensorDatas[12].add(hr.toFloat(), sampleHr)
        sensorDatos[12] = Pair(hr.toFloat(), TypeData.HR)
        sampleHr += 1
    }
    fun calcAi(accList: List<List<Pair<Float, Int>>>): Float {
        if (accList.isEmpty()) {
            return 0f
        }
        val error = 1e-06
        val diX = sqrt(accList[0].sumOf { acc -> (acc.first -  accList[0].map { acc1 -> acc1.first }.average()).pow(2.0) } / (accList[0].size - 1))
        val diY = sqrt(accList[1].sumOf { acc -> (acc.first -  accList[1].map { acc1 -> acc1.first }.average()).pow(2.0) } / (accList[1].size - 1))
        val diZ = sqrt(accList[2].sumOf { acc -> (acc.first -  accList[2].map { acc1 -> acc1.first }.average()).pow(2.0) } / (accList[2].size - 1))
        val sum =
            ((diX * diX - error) / error) + ((diY * diY - error) / error) + ((diZ * diZ - error) / error)
        return sqrt((sum / 3).coerceAtLeast(0.0)).toFloat()
    }
    fun parseECG(parse: BleBytesParser, typeSensor: TypeSensor){
        if(typeSensor == TypeSensor.BIO2){
            var sensor = (parse.getValue()[0] and 0x0C) as Byte
            sensor = (sensor.toInt() shr 2).toByte()
            val nSensor = sensor.toInt()
            val valN = (parse.getValue()[0] and 0x01) as Byte
            val valP = (parse.getValue()[0] and 0x02) as Byte
            val status = (valN.toInt() == 0x00) and (valP.toInt() == 0)
            if(lastStatusElectrodes[nSensor] != status){
                lastStatusElectrodes[nSensor] = status
            }
            for(i in 0 until typeSensor.groupedData){
                var val_sample =
                    ((parse.getValue()[3 * i + 1] and 0xFF.toByte()).toInt() shl 16) or
                            ((parse.getValue()[3 * i + 2] and 0xFF.toByte()).toInt() shl 8) or
                            ((parse.getValue()[3 * i + 3] and 0xFF.toByte()).toInt())
                if (val_sample > 0x7FFFFF) val_sample -= 0xFFFFFF
                val sample = val_sample * 5.96046448e-8 // (3*2)/Math.pow(2,24))/6;
                var dataEcg = (sample)
                dataEcg = ecgHighPassPermanent[nSensor].filter(dataEcg)
                dataEcg = ecgBandStopPermanent[nSensor].filter(dataEcg)
                sensorDatas[11].add(dataEcg.toFloat() * 100f, sample.toInt())
                sensorDatos[11] = Pair(dataEcg.toFloat() * 100f, TypeData.Ecg)
                var data = (sample * 1000).toDouble()
                data = emgBandStopPermanent[nSensor].filter(data)
                data = emgBandPassPermanent[nSensor].filter(data)
                data = emgLowPassEnvelop[nSensor].filter(abs(data) * 2f)
                cacheEnvelop.add(data)
                if(cacheEnvelop.size >= 6){
                    var valor = 0.0
                    cacheEnvelop.forEach { cacheDato ->
                        valor += cacheDato
                    }
                    valor /= cacheEnvelop.size
                    if(maxEscale[nSensor + 7] < valor.toFloat()){
                        maxEscale[nSensor + 7] = valor.toFloat()
                    }
                    var valorFin = valor.toFloat()/maxEscale[nSensor + 6]
                    if(valorFin > 1f){
                        valorFin = 1f
                    }
                    if(valorFin < -1f){
                        valorFin = -1f
                    }
                    sensorDatas[nSensor + 7].add(valorFin, 0)
                    sensorDatos[nSensor + 7] = Pair(valorFin, when(nSensor){
                        0 -> TypeData.Emg1
                        1 -> TypeData.Emg2
                        2 -> TypeData.Emg3
                        3 -> TypeData.Emg4
                        else -> TypeData.Emg1
                    })
                    cacheEnvelop.clear()
                }
            }
        }
    }
    fun characteristicUpdate(
        value: ByteArray,
        characteristic: String,
    ) {
        val parse = BleBytesParser(value)

        //Napier.d("Leido UUID: (${typeSensor.name}) - $characteristic")
        when (typeSensor) {
            TypeSensor.BIO1 -> {
                when {
                    characteristic.equals(TypeSensor.BIO1.UUID_ACCELEROMETER_CHARACTERISTIC, ignoreCase = true) -> {
                        parseMPU(parse)
                    }
                    characteristic.equals(TypeSensor.BIO1.UUID_ECG_CHARACTERISTIC, ignoreCase = true) -> {
                        parseECG(parse, TypeSensor.BIO1)
                    }
                    characteristic.equals(TypeSensor.BIO2.UUID_HR_CHARACTERISTIC, ignoreCase = true) -> {
                        parseHR(parse)
                    }
                }
            }
            TypeSensor.BIO2 -> {
                when {
                    characteristic.equals(TypeSensor.BIO2.UUID_ACCELEROMETER_CHARACTERISTIC, ignoreCase = true) -> {
                        parseMPU(parse)
                    }
                    characteristic.equals(TypeSensor.BIO1.UUID_ECG_CHARACTERISTIC, ignoreCase = true) -> {
                        parseECG(parse, TypeSensor.BIO2)
                    }
                    characteristic.equals(TypeSensor.BIO2.UUID_HR_CHARACTERISTIC, ignoreCase = true) -> {
                        parseHR(parse)
                    }
                }
            }
            TypeSensor.PIKKU -> {
                when {
                    characteristic.equals(TypeSensor.PIKKU.UUID_ACCELEROMETER_CHARACTERISTIC, ignoreCase = true) -> {
                        parseMPU(parse)
                    }
                }
            }
            TypeSensor.CROLL -> {
                when {
                    characteristic.equals(TypeSensor.CROLL.UUID_MPU_CHARACTERISTIC, ignoreCase = true) -> {
                        val id = parse.getIntValue(FORMAT_UINT8) // El primer byte es un id de acelerómetro que no sirve en esta app pero ha de hacerse el parse para avanzarlo dentro del byte array.
                        parseMPU(parse)
                    }
                }
            }
        }
        _groupedDataFlow.value = sensorDatos.filterNotNull().toList()
    }
}