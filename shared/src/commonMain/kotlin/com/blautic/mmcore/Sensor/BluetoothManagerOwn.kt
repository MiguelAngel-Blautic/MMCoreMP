import Sensor.ScanResult
import Sensor.TypeSensor
import Sensor.Device
import Utils.Logger
import kotlin.experimental.and

abstract class BluetoothManagerOwn(val bluetoothManagerCallback: BluetoothManagerCallback) {
    abstract fun startScan()
    abstract fun stopScan()
    abstract fun connectPeripheral(numDevice: Int, mac: String, sensor: Device)
    abstract fun cancelConnection(address: String)
    /*fun startScan(){
        bluetoothManager.startScan(
            onDeviceFound = { device ->
                discoveredPeripheralPrecess(device)
                println("Dispositivo found: ${device}") },
            onScanStateChanged = { state ->
                when (state) {
                    BluetoothScanState.IDLE -> println("Scan Idle")
                    BluetoothScanState.SCANNING -> println("Scanning...")
                    BluetoothScanState.STOPPED -> println("Scan Stopped")
                }
            },
            onError = { error ->
                println("Error: ${error}")
                bluetoothManagerCallback.onScanFailed("Error: $error")
            }
        )
    }
    fun stopScan(){
        bluetoothManager.stopScan()
    }
    fun cancelConnection(address: String){
        bluetoothManager.getConnectedDevices { device ->
            if(device.address == address){
                bluetoothManager.disconnectDevice(device)
            }
        }
    }
    fun connectPeripheral(
        peripheral: BluetoothPeripheral,
        sensor: Device
    ){
        bluetoothManager.connectToDevice(
            device = peripheral, // Bluetooth Device
            onConnectionStateChanged = { state ->
                when (state) {
                    BluetoothConnectionState.DISCONNECTED -> {
                        bluetoothManagerCallback.onDisconnectedPeripheral(peripheral.address ?: "")
                    }
                    BluetoothConnectionState.CONNECTING -> {

                    }
                    BluetoothConnectionState.CONNECTED -> {
                        bluetoothManagerCallback.onConnectedPeripheral(peripheral.address ?: "")
                    }
                    BluetoothConnectionState.ERROR -> {
                        bluetoothManagerCallback.onConnectionFailed(peripheral.address ?: "")
                    }
                }
            },
            onError = { bluetoothManagerCallback.onConnectionFailed(peripheral.address ?: "") }
        )
    }*/
    abstract fun write(peripheral: String, characteristic: String, value: ByteArray)
    abstract fun enableNotify(peripheral: String, characteristic: String, value: Boolean)
    fun checkButton(scan: ByteArray, tipo: TypeSensor): Boolean{
        //Logger.log(1, "CheckButton", "${tipo.name}: (${scan.size}) ${scan.map { "$it" }}")
        return when (tipo) {
            TypeSensor.BIO2 -> {
                if(scan.size > 10)
                    (scan[10].toInt() == 1)
                else
                    false
            }
            TypeSensor.BIO1 -> {
                if(scan.size > 10)
                    (scan[10].toInt() == 1)
                else
                    false
            }
            TypeSensor.PIKKU -> {
                if(scan.size > (16+2))
                    ((scan[16 + 2] and 0xFF.toByte()).toInt() == 1)
                else
                    false
            }
            TypeSensor.CROLL -> {
                if(scan.size > 8)
                    (scan[8].toInt() == 1)
                else
                    false
            }
        }
    }
    fun checkIfTarget(scan: ByteArray): Boolean {
        var res = false

        // Compruebo si es un Bio
        if(scan[5] == 0xBC.toByte()){
            res = true
        }
        // Compruebo si es un Bio2
        if(scan[5] == 0xAC.toByte()){
            res = true
        }
        // Compruebo si es un Pikku (BLE 5)
        if(scan[5] == 0xBE.toByte()){
            res = true
        }
        // Compruebo si es un Pikku (BLE 4)
        if(scan[5] == 0xBF.toByte()){
            res = true
        }
        // Compruebo si es un Crol
        if(scan[5] == 0xB5.toByte()){
            res = true
        }

        return res
    }
    fun String.hexStringToByteArray(): ByteArray {
        return this.split(":")  // Divide el string en pares de caracteres
            .map { it.toInt(16).toByte() } // Convierte cada par en un byte
            .toByteArray()
    }
    fun discoveredPeripheralPrecess(
        deviceName: String,
        deviceAddress: String,
        rssi: Int,
        bytes: ByteArray
    ) {
        if(bytes.size > 6) {
            if (checkIfTarget(bytes)) {
                //Logger.log(1, "CONEXION", "Device valid: ${deviceName}-${bytes.map { "$it-" }}")
                val type = when {
                    bytes[6] == 0X25.toByte() && bytes[5] == 0xBC.toByte() -> { // Compruebo si es Bio 1
                        TypeSensor.BIO1
                    }

                    bytes[6] == 0x26.toByte() && bytes[5] == 0xBC.toByte() -> { // Compruebo si es Bio 2
                        TypeSensor.BIO2
                    }

                    bytes[5] == 0xBE.toByte() -> { // Compruebo si es Pikku (BLE 5)
                        TypeSensor.PIKKU
                    }

                    bytes[5] == 0xBF.toByte() -> { // Compruebo si es Pikku (BLE 4)
                        TypeSensor.PIKKU
                    }

                    bytes[5] == 0xB5.toByte() -> {
                        TypeSensor.CROLL
                    }

                    else -> { // Por defecto lo asigno al Pikku
                        TypeSensor.PIKKU
                    }
                }
                bluetoothManagerCallback.onDiscoveredPeripheral(
                    deviceAddress, ScanResult(
                        name = deviceName,
                        address = deviceAddress, rssi = rssi,
                        button = checkButton(bytes, type),
                        typeSensor = type
                    )
                )
            }else{
                Logger.log(2, "CONEXION", "Device not valid: ${deviceName}-${bytes.map { "$it-" }}")
            }
        }
    }
}

expect class BluetoothManagerFactory {
    companion object {
        fun create(bluetoothManagerCallback: BluetoothManagerCallback, context: Any?): BluetoothManagerOwn
    }
}