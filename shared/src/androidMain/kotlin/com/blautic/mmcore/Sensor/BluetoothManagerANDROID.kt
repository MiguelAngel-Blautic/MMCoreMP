import Utils.Logger
import android.annotation.SuppressLint
import Sensor.Device
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

@SuppressLint("MissingPermission")
class BluetoothManagerAndroid(bluetoothManagerCallback: BluetoothManagerCallback, private val context: Context): BluetoothManagerOwn(bluetoothManagerCallback) {

    private var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val discoveredPeripherals = mutableMapOf<String, BluetoothGatt>()
    private val peripheralCharacteristics = mutableMapOf<String, List<BluetoothGattCharacteristic>>()

    override fun startScan() {
        if (bluetoothAdapter?.isEnabled == true) {
            val leScanner = bluetoothAdapter?.bluetoothLeScanner
            leScanner?.startScan(object : android.bluetooth.le.ScanCallback() {
                override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult?) {
                    result?.let { scanResult ->
                        val deviceName = scanResult.device.name ?: "Unknown"
                        val deviceAddress = scanResult.device.address
                        val rssi = scanResult.rssi
                        val advertisementData = scanResult.scanRecord?.bytes

                        discoveredPeripheralPrecess(deviceName, deviceAddress, rssi, advertisementData ?: byteArrayOf())
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    bluetoothManagerCallback.onScanFailed("Scan failed with error code $errorCode")
                }
            })
        } else {
            bluetoothManagerCallback.onScanFailed("Bluetooth is off or not available")
        }
    }

    override fun stopScan() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(object : android.bluetooth.le.ScanCallback() {
            override fun onScanFailed(errorCode: Int) {
                // Maneja el error si es necesario
                Logger.log(2, "ERROR ESCANEO", "Error al detener el escaneo: $errorCode")
            }
        })
    }


    fun connectPeripheral(mac: String) {
        val device = bluetoothAdapter?.getRemoteDevice(mac)
        device?.let {
            val gatt = it.connectGatt(context, false, gattCallback)
            discoveredPeripherals[mac] = gatt
        } ?: run {
            bluetoothManagerCallback.onConnectionFailed(mac)
        }
    }

    override fun cancelConnection(address: String) {
        discoveredPeripherals[address]?.disconnect()
        discoveredPeripherals[address]?.close()
    }

    override fun connectPeripheral(numDevice: Int, mac: String, sensor: Device) {
        connectPeripheral(mac)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private val gattCallback = object : android.bluetooth.BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                gatt.discoverServices()
                bluetoothManagerCallback.onConnectedPeripheral(gatt.device.address)
            } else {
                bluetoothManagerCallback.onConnectionFailed(gatt.device.address)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            gatt.services.forEach { service ->
                val characteristics = service.characteristics
                peripheralCharacteristics[gatt.device.address] = characteristics
                gatt.readCharacteristic(characteristics.first()) // O iniciar notificaciones si es necesario
                bluetoothManagerCallback.onDiscoveredServices(gatt.device.address)
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val value = characteristic.value
                bluetoothManagerCallback.onCharacteristicUpdate(
                    gatt.device.address,
                    value,
                    characteristic.uuid.toString()
                )
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val value = characteristic.value
            bluetoothManagerCallback.onCharacteristicUpdate(
                gatt.device.address,
                value,
                characteristic.uuid.toString()
            )
        }
    }

    override fun write(peripheralName: String, characteristic: String, value: ByteArray) {
        Logger.log(1, "ESCRITURA", "Inicio de escritura")
        val gatt = discoveredPeripherals[peripheralName]

        val characteristic = gatt?.services?.flatMap { it.characteristics }
            ?.find { it.uuid.toString() == characteristic }

        if (characteristic != null) {
            var success = gatt.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            Logger.log(1, "ESCRITURA", "Escribido en $success")
            if (success != 0) {
                Logger.log(1, "ESCRITURA", "Escribiendo en característica $characteristic con valor $value")
            } else {
                Logger.log(2, "ESCRITURA", "Error al escribir en característica $characteristic")
            }
        } else {
            Logger.log(2, "ESCRITURA", "Característica con UUID $characteristic no encontrada en el periférico.")
        }
    }

    override fun enableNotify(peripheralName: String, characteristic: String, value: Boolean) {
        val gatt = discoveredPeripherals[peripheralName]

        val characteristic = gatt?.services?.flatMap { it.characteristics }
            ?.find { it.uuid.toString() == characteristic }

        if (characteristic != null) {
            val success = gatt.setCharacteristicNotification(characteristic, value)
            if (value) {
                println("Habilitando notificaciones en la característica $characteristic para el periférico $peripheralName")
            } else {
                println("Deshabilitando notificaciones en la característica $characteristic para el periférico $peripheralName")
            }
        } else {
            println("Característica con UUID $characteristic no encontrada en el periférico.")
        }
    }

}
actual class BluetoothManagerFactory {
    actual companion object {
        actual fun create(bluetoothManagerCallback: BluetoothManagerCallback, context: Any?): BluetoothManagerOwn {
            return BluetoothManagerAndroid(bluetoothManagerCallback, context as Context)
        }
    }
}