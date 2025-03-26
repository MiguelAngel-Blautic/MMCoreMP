import Sensor.AdapterState
import Sensor.ConnectionState
import Sensor.ScanResult
import Sensor.Device
import Sensor.TypeSensor
import Utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DevicesManager(context: Any?): BluetoothManagerCallback {
    private val bluetoothManager = BluetoothManagerFactory.create(this, context)
    var devices : MutableList<Device> = mutableListOf()
    private val _scanResultFlow = MutableStateFlow<ScanResult?>(null)
    private val _connectionChange = MutableStateFlow<Pair<Int, ConnectionState>?>(null)
    private val _scanFailureFlow = MutableStateFlow<String?>(null)
    val scanResultFlow get() = _scanResultFlow.asStateFlow()
    val scanFailureFlow get() = _scanFailureFlow.asStateFlow()
    val connectionChange get() = _connectionChange.asStateFlow()
    var buscando = false
    var conectando = false
    var sensores: List<TypeSensor> = listOf()
    var indexConn: Int? = null
    var enableSensors = EnableSensors(true, true, true)
    data class EnableSensors(
        val emg: Boolean = false,
        val mpu: Boolean = false,
        val hr: Boolean = false
    )

    private val UUID_TR_PERIOD = "0000ff3b-0000-1000-8000-00805f9b34fb"
    private val UUID_SCORE_CFGMPU = "0000ff3c-0000-1000-8000-00805f9b34fb"
    private val ENABLE_REPORT_SENSORS = 0x01

    fun setListSize(size: Int){
        if(devices.size != size) {
            devices = MutableList(size) {
                Device(
                    numDevice = 0,
                    address = "",
                    typeSensor = TypeSensor.PIKKU
                )
            }
        }
    }
    fun addSensor(){
        devices.add(Device(numDevice = 0, address = "", typeSensor = TypeSensor.PIKKU))
    }
    fun getSensorNum(address: String): Device? {
        return devices.find { it.address == address}
    }
    fun getSensorType(index: Int): TypeSensor {
        return getSensorNum(index)!!.typeSensor
    }
    fun getSensorNum(numDevice: Int): Device? {
        return devices.find { it.numDevice == numDevice}
    }
    fun startScan(){
        bluetoothManager.startScan()
    }
    fun stopScan(){
        bluetoothManager.stopScan()
    }
    fun connect(numSensor: Int, peripheral: String, typeSensor: TypeSensor){
        stopScan()
        conectando = true
        val sens = Device(numDevice = numSensor,
            address = peripheral,
            typeSensor = typeSensor)
        sens.activeFilters()
        bluetoothManager.connectPeripheral(sens.numDevice, sens.address, sens)
        sens.setConnectionState(ConnectionState.CONNECTING)
        devices = devices.apply { set(numSensor, sens) }
        buscando = false
    }
    fun disconnectAll(){
        devices.forEach {
            disconnect(it.numDevice)
        }
    }
    fun disconnect(index: Int){
        val sens = getSensorNum(index)
        if (sens != null) {
            disconnect(sens)
        }
        devices[devices.indexOf(sens)] = Device(numDevice = 0, address = "", typeSensor = TypeSensor.PIKKU)
    }
    private fun disconnect(sensor: Device) {
        try {
            if(sensor.address != "") {
                bluetoothManager.cancelConnection(sensor.address)
            }
            sensor.setConnectionState(ConnectionState.DISCONNECTED)
            _connectionChange.value = Pair(sensor.numDevice, ConnectionState.DISCONNECTED)
        }catch(e: Exception){}
    }
    fun isConnected(sensor: Device): Boolean {
        return sensor.connectionStateFlow.value == ConnectionState.CONNECTED
    }
    fun isConnected(address: String): Int? {
        val sens = getSensorNum(address)
        return sens?.numDevice
    }
    fun conectar(peripheral: String, typeSensor: TypeSensor, index: Int) {
        stopScan()
        buscando = false
        sensores = listOf()
        indexConn = null
        connect(
            numSensor = index,
            peripheral = peripheral,
            typeSensor = typeSensor
        )
    }

    override fun onConnectedPeripheral(peripheral: String) {
        conectando = false
        buscando = false
        sensores = listOf()
        indexConn = null
        val sens = getSensorNum(peripheral)
        if(sens != null) {
            sens.setConnectionState(ConnectionState.CONNECTED)
            _connectionChange.value = Pair(sens.numDevice, ConnectionState.CONNECTED)
        }
    }

    override fun onConnectionFailed(peripheral: String) {
        conectando = false
        buscando = false
        sensores = listOf()
        indexConn = null
        val sens = getSensorNum(peripheral)
        sens!!.setConnectionState(ConnectionState.FAILED)
        _connectionChange.value = Pair(sens.numDevice, ConnectionState.FAILED)
    }

    override fun onDisconnectedPeripheral(peripheral: String) {
        val sens = getSensorNum(peripheral)
        sens?.setConnectionState(ConnectionState.DISCONNECTED)
        if (sens != null) {
            _connectionChange.value = Pair(sens.numDevice, ConnectionState.DISCONNECTED)
            devices[devices.indexOf(sens)]!!.address = ""
        }
    }

    override fun onDiscoveredPeripheral(peripheral: String, scanResult: ScanResult) {
        if(buscando){
            if(scanResult.button){
                val sens = isConnected(scanResult.address)
                if(sens != null){
                    onScanFailed("Sensor ya conectado en ${sens + 1}")
                }else{
                    if(scanResult.typeSensor in sensores){
                        if(conectando) {
                            onScanFailed("Ya se esta realizando una conexion")
                        }else{
                            stopScan()
                            conectar(peripheral, scanResult.typeSensor, indexConn!!)
                        }
                    }else{
                        onScanFailed("Tipo de sensor no valido")
                    }
                }
            }
        }
        _scanResultFlow.value = scanResult
    }

    override fun onScanFailed(scanFailure: String) {
        _scanFailureFlow.value = scanFailure
        _scanFailureFlow.value = null
    }

    override fun onBluetoothAdapterStateChanged(state: AdapterState) {

    }

    override fun onCharacteristicUpdate(
        peripheral: String,
        value: ByteArray,
        characteristic: String
    ) {
        //Logger.log(1, "Characteristic: $peripheral", "$characteristic")
        getSensorNum(peripheral)?.characteristicUpdate(value, characteristic)
    }

    fun enableServices() {
        devices.forEach {
            val peripheral = it.address
            val typeSensor = it.typeSensor
            //Logger.log(1, "TypeSENSOR", "${typeSensor.name}")
            when (typeSensor) {
                TypeSensor.BIO1 -> {
                    // Acelerometro
                    bluetoothManager.write(peripheral, typeSensor.UUID_TAG_OPER, byteArrayOf(0x00))
                    bluetoothManager.enableNotify(peripheral, typeSensor.UUID_ACCELEROMETER_CHARACTERISTIC, true)
                    // Estado
                    bluetoothManager.write(peripheral, typeSensor.UUID_TAG_OPER, byteArrayOf(0x0C))
                    bluetoothManager.enableNotify(peripheral, typeSensor.UUID_STATUS_CHARACTERISTIC, true)
                    // EMG
                    bluetoothManager.write(peripheral, typeSensor.UUID_TAG_OPER, byteArrayOf(0x01))
                    bluetoothManager.enableNotify(peripheral, typeSensor.UUID_ECG_CHARACTERISTIC, true)
                }
                TypeSensor.BIO2 -> {
                    // Estado
                    bluetoothManager.write(peripheral, typeSensor.UUID_TAG_OPER, byteArrayOf(0x0C))
                    bluetoothManager.enableNotify(peripheral, typeSensor.UUID_STATUS_CHARACTERISTIC, true)
                    // Acelerometro
                    bluetoothManager.write(peripheral, typeSensor.UUID_TAG_OPER, byteArrayOf(0x00))
                    bluetoothManager.enableNotify(peripheral, typeSensor.UUID_ACCELEROMETER_CHARACTERISTIC, true)
                    if(enableSensors.emg) {
                        // EMG
                        bluetoothManager.write(peripheral, typeSensor.UUID_CH_FR, byteArrayOf(0x0F, 0x02))
                        bluetoothManager.write(peripheral, typeSensor.UUID_TAG_OPER, byteArrayOf(0x04))
                        bluetoothManager.enableNotify(peripheral, typeSensor.UUID_ECG_CHARACTERISTIC, true)
                    }
                    //write(peripheral, UUID.fromString(typeSensor.UUID_TAG_OPER), byteArrayOf(5), typeSensor)
                    if(enableSensors.hr) {
                        bluetoothManager.enableNotify(peripheral, typeSensor.UUID_HR_CHARACTERISTIC, true)
                        bluetoothManager.write(peripheral, typeSensor.UUID_TAG_OPER, byteArrayOf(0x06))
                    }
                }
                TypeSensor.PIKKU -> {
                    bluetoothManager.write(peripheral, typeSensor.UUID_TAG_OPER, byteArrayOf(0x01))
                    // Estado
                    // Acelerometro
                    bluetoothManager.write(peripheral, UUID_TR_PERIOD, byteArrayOf(0x32))
                    bluetoothManager.write(peripheral, UUID_SCORE_CFGMPU, byteArrayOf(0x3F, 0x00, 0x00, 0x08, 0x03, 0x03, 0x10))
                    bluetoothManager.enableNotify(peripheral, typeSensor.UUID_TAG_OPER, true)
                    bluetoothManager.enableNotify(peripheral, typeSensor.UUID_ACCELEROMETER_CHARACTERISTIC, true)
                }
                TypeSensor.CROLL -> {
                    // Estado
                    bluetoothManager.enableNotify(peripheral, typeSensor.UUID_STATUS_CHARACTERISTIC, true)

                    // Acelerometro
                    bluetoothManager.enableNotify(peripheral, typeSensor.UUID_MPU_CHARACTERISTIC, true)
                    bluetoothManager.write(peripheral, typeSensor.UUID_TR_PERIOD, byteArrayOf(0, 0x10))
                    bluetoothManager.write(peripheral, typeSensor.UUID_TAG_OPER, byteArrayOf(0x0F))
                }
            }
        }

    }

    override fun onDiscoveredServices(peripheral: String) {
        val typeSensor = getSensorNum(peripheral)!!.typeSensor
        //Logger.log(1, "TypeSENSOR", "${typeSensor.name}")
        /*when (typeSensor) {
            TypeSensor.BIO1 -> {
                // Acelerometro
                bluetoothManager.write(peripheral, typeSensor.UUID_TAG_OPER, byteArrayOf(0x00))
                bluetoothManager.enableNotify(peripheral, typeSensor.UUID_ACCELEROMETER_CHARACTERISTIC, true)
                // Estado
                bluetoothManager.write(peripheral, typeSensor.UUID_TAG_OPER, byteArrayOf(0x0C))
                bluetoothManager.enableNotify(peripheral, typeSensor.UUID_STATUS_CHARACTERISTIC, true)
                // EMG
                bluetoothManager.write(peripheral, typeSensor.UUID_TAG_OPER, byteArrayOf(0x01))
                bluetoothManager.enableNotify(peripheral, typeSensor.UUID_ECG_CHARACTERISTIC, true)
            }
            TypeSensor.BIO2 -> {
                // Estado
                bluetoothManager.write(peripheral, typeSensor.UUID_TAG_OPER, byteArrayOf(0x0C))
                bluetoothManager.enableNotify(peripheral, typeSensor.UUID_STATUS_CHARACTERISTIC, true)
                // Acelerometro
                bluetoothManager.write(peripheral, typeSensor.UUID_TAG_OPER, byteArrayOf(0x00))
                bluetoothManager.enableNotify(peripheral, typeSensor.UUID_ACCELEROMETER_CHARACTERISTIC, true)
                if(enableSensors.emg) {
                    // EMG
                    bluetoothManager.write(peripheral, typeSensor.UUID_CH_FR, byteArrayOf(0x0F, 0x02))
                    bluetoothManager.write(peripheral, typeSensor.UUID_TAG_OPER, byteArrayOf(0x04))
                    bluetoothManager.enableNotify(peripheral, typeSensor.UUID_ECG_CHARACTERISTIC, true)
                }
                //write(peripheral, UUID.fromString(typeSensor.UUID_TAG_OPER), byteArrayOf(5), typeSensor)
                if(enableSensors.hr) {
                    bluetoothManager.enableNotify(peripheral, typeSensor.UUID_HR_CHARACTERISTIC, true)
                    bluetoothManager.write(peripheral, typeSensor.UUID_TAG_OPER, byteArrayOf(0x06))
                }
            }
            TypeSensor.PIKKU -> {
                bluetoothManager.write(peripheral, typeSensor.UUID_TAG_OPER, byteArrayOf(0x01))
                // Estado
                // Acelerometro
                bluetoothManager.write(peripheral, UUID_TR_PERIOD, byteArrayOf(0x32))
                bluetoothManager.write(peripheral, UUID_SCORE_CFGMPU, byteArrayOf(0x3F, 0x00, 0x00, 0x08, 0x03, 0x03, 0x10))
                bluetoothManager.enableNotify(peripheral, typeSensor.UUID_TAG_OPER, true)
                bluetoothManager.enableNotify(peripheral, typeSensor.UUID_ACCELEROMETER_CHARACTERISTIC, true)
            }
            TypeSensor.CROLL -> {
                // Estado
                bluetoothManager.enableNotify(peripheral, typeSensor.UUID_STATUS_CHARACTERISTIC, true)

                // Acelerometro
                bluetoothManager.enableNotify(peripheral, typeSensor.UUID_MPU_CHARACTERISTIC, true)
                bluetoothManager.write(peripheral, typeSensor.UUID_TR_PERIOD, byteArrayOf(0, 0x10))
                bluetoothManager.write(peripheral, typeSensor.UUID_TAG_OPER, byteArrayOf(0x0F))
            }
        }*/
    }
}