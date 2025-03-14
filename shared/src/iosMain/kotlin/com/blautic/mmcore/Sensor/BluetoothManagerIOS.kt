
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import Sensor.Device
import Utils.Logger
import com.blautic.mmcore.Sensor.BluetoothManagerAuxIOS
import platform.Foundation.NSData
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.CoreBluetooth.*
import platform.CoreLocation.CLLocationManager
import platform.Foundation.NSLog
import platform.Foundation.getBytes
import platform.darwin.ByteVar


class BluetoothManagerIOS(bluetoothManagerCallback: BluetoothManagerCallback) :
    BluetoothManagerOwn(bluetoothManagerCallback) {

    //private var centralManager: CBCentralManager? = null
    //private val discoveredPeripherals = mutableMapOf<String, CBPeripheral>()
    private val bluetoothManagerAuxIOS = BluetoothManagerAuxIOS()
    private var scope = CoroutineScope(Dispatchers.Main)
    private var locationManager: CLLocationManager? = null

    init {
        //centralManager = CBCentralManager(bluetoothManagerAuxIOS, dispatch_get_main_queue())
        locationManager = CLLocationManager()
        locationManager?.requestWhenInUseAuthorization()  // SOLICITA PERMISOS DE UBICACIÓN
        observeAux()
    }

    fun observeAux(){
        scope.launch(Dispatchers.Main) {
            bluetoothManagerAuxIOS.connectFlow.collect{item ->
                if(item != null){
                    bluetoothManagerCallback.onConnectedPeripheral(item)
                }
            }
        }
        scope.launch(Dispatchers.Main) {
            bluetoothManagerAuxIOS.errorFlow.collect{item ->
                if(item != null){
                    bluetoothManagerCallback.onScanFailed(item)
                }
            }
        }
        scope.launch(Dispatchers.Main) {
            bluetoothManagerAuxIOS.adapterstateFlow.collect{item ->
                if(item != null) {
                    bluetoothManagerCallback.onBluetoothAdapterStateChanged(item)
                }
            }
        }
        scope.launch(Dispatchers.Main) {
            bluetoothManagerAuxIOS.discoverPeriferalFlow.collect{item ->
                if(item != null) {
                    Logger.log(1, "IOS-CORE", "Device discovered: ${item.first}--${item.third}")
                    val serviceData = item.third["kCBAdvDataServiceData"] as? Map<*, *>
                    if(serviceData != null) {
                        val key = serviceData.keys.first()
                        if(key != null) {
                            val firstEntry = serviceData[key]
                            if(firstEntry != null) {
                                Logger.log(1, "IOS-CORE", "firstEntry: ${firstEntry}")
                                val bytesIni = (firstEntry as? NSData)?.toByteArray()
                                if(bytesIni != null) {
                                    val bytes = ByteArray(bytesIni.size + 7)
                                    val keyBytes = (key as CBUUID).data.toByteArray()
                                    bytes[5] = keyBytes[1]
                                    bytes[6] = keyBytes[0]
                                    for (i in bytesIni.indices) {
                                        bytes[i + 7] = bytesIni[i]
                                    }
                                    Logger.log(1, "IOS-CORE", "bytes: ${bytes.map { "$it" }}")
                                    val name = item.third["kCBAdvDataLocalName"] as? String
                                    discoveredPeripheralPrecess(
                                        name ?: "",
                                        item.first,
                                        item.second,
                                        bytes
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        scope.launch(Dispatchers.Main) {
            bluetoothManagerAuxIOS.connectErrorFlow.collect{item ->
                if(item != null) {
                    bluetoothManagerCallback.onConnectionFailed(item)
                }
            }
        }
        scope.launch(Dispatchers.Main) {
            bluetoothManagerAuxIOS.discoverServicesFlow.collect{item ->
                if(item != null) {
                    bluetoothManagerCallback.onDiscoveredServices(item)
                }
            }
        }
        scope.launch(Dispatchers.Main) {
            bluetoothManagerAuxIOS.updateCharacteristicFlow.collect{item ->
                if(item != null) {
                    bluetoothManagerCallback.onCharacteristicUpdate(item.first, item.second, "0000${item.third}-0000-1000-8000-00805f9b34fb")
                }
            }
        }
    }

    override fun startScan() {
        bluetoothManagerAuxIOS.startScan()
    }

    override fun stopScan() {
        bluetoothManagerAuxIOS.stopScan()
    }

    override fun connectPeripheral(numDevice: Int, mac: String, sensor: Device) {
        bluetoothManagerAuxIOS.connectPeripheral(mac)
    }

    override fun cancelConnection(address: String) {
        bluetoothManagerAuxIOS.cancelConnection(address)
    }

    override fun write(peripheralName: String, characteristic: String, value: ByteArray) {
        bluetoothManagerAuxIOS.write(peripheralName, characteristic, value)
    }

    override fun enableNotify(peripheralName: String, characteristic: String, value: Boolean) {
        bluetoothManagerAuxIOS.enableNotify(peripheralName, characteristic, value)
    }

    @OptIn(ExperimentalForeignApi::class)
    fun NSData.toByteArray(): ByteArray {
        val size = this.length.toInt()
        val byteArray = ByteArray(size)

        // Convertimos el puntero de NSData a ByteArray manualmente
        this.bytes?.let { pointer ->
            // Usamos 'reinterpret' para obtener acceso al puntero como un arreglo de bytes
            val bytePointer = pointer.reinterpret<ByteVar>()
            for (i in 0 until size) {
                byteArray[i] = bytePointer[i].toByte()  // Copiar byte por byte
            }
        }
        return byteArray
    }

}

actual class BluetoothManagerFactory {
    actual companion object {
        actual fun create(bluetoothManagerCallback: BluetoothManagerCallback, context: Any?): BluetoothManagerOwn {
            return BluetoothManagerIOS(bluetoothManagerCallback)
        }
    }
}