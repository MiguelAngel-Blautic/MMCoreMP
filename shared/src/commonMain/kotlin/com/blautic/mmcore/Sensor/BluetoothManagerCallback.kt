import Sensor.AdapterState
import Sensor.ScanResult
import network.chaintech.cmp_bluetooth_manager.BluetoothPeripheral

interface BluetoothManagerCallback {
    fun onConnectedPeripheral(peripheral: String)
    fun onConnectionFailed(peripheral: String)
    fun onDisconnectedPeripheral(peripheral: String)
    fun onDiscoveredPeripheral(peripheral: String, scanResult: ScanResult)
    fun onScanFailed(scanFailure: String)
    fun onBluetoothAdapterStateChanged(state: AdapterState)
    fun onCharacteristicUpdate(peripheral: String, value: ByteArray, characteristic: String)
    fun onDiscoveredServices(peripheral: String)
}