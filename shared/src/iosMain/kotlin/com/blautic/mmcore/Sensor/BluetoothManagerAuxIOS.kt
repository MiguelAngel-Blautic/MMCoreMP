package com.blautic.mmcore.Sensor

import Sensor.AdapterState
import Utils.Logger
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCClass
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.refTo
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import objcnames.classes.Protocol
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_async
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerScanOptionAllowDuplicatesKey
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBManagerAuthorizationDenied
import platform.CoreBluetooth.CBManagerAuthorizationNotDetermined
import platform.CoreBluetooth.CBManagerAuthorizationRestricted
import platform.CoreBluetooth.CBManagerStatePoweredOff
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSLog
import platform.Foundation.NSNumber
import platform.Foundation.NSTimer
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.darwin.NSObject
import platform.darwin.NSUInteger
import platform.posix.memcpy

class BluetoothManagerAuxIOS: NSObject(), CBCentralManagerDelegateProtocol, CBPeripheralDelegateProtocol {
    private val _adapterstateFlow = MutableStateFlow<AdapterState?>(null)
    val adapterstateFlow = _adapterstateFlow.asStateFlow()
    private val discoveredPeripherals = mutableMapOf<String, CBPeripheral>()
    private var centralManager: CBCentralManager? = null
    init {
        centralManager = CBCentralManager(this, dispatch_get_main_queue())
        centralManager?.delegate = this
        NSLog("AUXIOS inicializado")
    }
    fun checkBluetoothPermissions() {
        val status = CBCentralManager.authorization
        when (status) {
            CBManagerAuthorizationAllowedAlways -> NSLog("Permiso concedido")
            CBManagerAuthorizationNotDetermined -> NSLog("Permiso no solicitado aún")
            CBManagerAuthorizationRestricted -> NSLog("Permiso restringido (control parental o restricción de sistema)")
            CBManagerAuthorizationDenied -> NSLog("Permiso denegado por el usuario")
            else -> NSLog("Estado desconocido de permisos")
        }
    }

    private val _errorFlow = MutableStateFlow<String?>(null)
    val errorFlow = _errorFlow.asStateFlow()
    fun startScan(){
        NSLog("IOS-MMCORE: START SCANN")
        if (centralManager?.state == CBManagerStatePoweredOn) {
            centralManager?.scanForPeripheralsWithServices(null, mapOf(
                CBCentralManagerScanOptionAllowDuplicatesKey to true))
            checkBluetoothPermissions()
            NSLog("IOS-MMCORE: scanForPeripheralsWithServices called")
            val scanTimer = NSTimer.scheduledTimerWithTimeInterval(5.0, true) {
                if (centralManager?.isScanning == true) {
                    NSLog("IOS-MMCORE: Scanning...")
                }else{
                    NSLog("IOS-MMCORE: Error Scann")
                }
            }
        } else {
            NSLog("IOS-MMCORE: Error Scann")
            _errorFlow.value = "Bluetooth is off or not available"
        }
    }
    fun stopScan(){
        centralManager?.stopScan()
    }
    fun connectPeripheral(mac: String){
        discoveredPeripherals[mac]?.let { peripheral ->
            centralManager?.connectPeripheral(peripheral, null)
        } ?: run {
            _connectErrorFlow.value = "Error connecting $mac"
        }
    }
    fun cancelConnection(mac: String){
        discoveredPeripherals[mac]?.let { peripheral ->
            centralManager?.cancelPeripheralConnection(peripheral)
        }
    }
    fun write(peripheralName: String, characteristic1: String, value: ByteArray){
        val searchChar = characteristic1.substring(4, 8).uppercase()
        val peripheral = discoveredPeripherals[peripheralName]  // Asumimos que `BlePeripheral` tiene un `CBPeripheral` en la propiedad `peripheral`
        if(peripheral != null) {
            //Logger.log(1, "WRITE","peripheral ${peripheral}")
            //Logger.log(1, "WRITE","services ${peripheral.services}")
            //Logger.log(1, "WRITE","characteristic ${searchChar}")
            val characteristicsList = peripheral.services?.flatMap {
                (it as? CBService)?.characteristics ?: emptyList()
            }
            //Logger.log(1, "WRITE","characteristics list ${characteristicsList?.map { (it as? CBCharacteristic)?.UUID?.UUIDString }}}")
            // Buscar la característica con el UUID proporcionado
            val characteristic = characteristicsList?.find { (it as? CBCharacteristic)?.UUID?.UUIDString == searchChar } as? CBCharacteristic

            if (characteristic != null) {
                // Escribir el valor en la característica
                peripheral.writeValue(value.toNSDataNoCopy(), characteristic, 0L)
                // Aquí puedes agregar cualquier lógica adicional basada en el tipo de sensor
                //Logger.log(1, "WRITE","Escribiendo en característica ${characteristic} con valor $value")
            } else {
                Logger.log(2, "WRITE","Característica con UUID $characteristic no encontrada en el periférico.")
            }
        }else{
            Logger.log(2, "WRITE", "Caracteristica ${characteristic1} no encontrada en ${discoveredPeripherals.keys}")
        }
    }
    fun enableNotify(peripheralName: String, characteristic: String, value: Boolean){
        val peripheral = discoveredPeripherals[peripheralName]  // Asumimos que `BlePeripheral` tiene un `CBPeripheral` en la propiedad `peripheral`

        // Buscar la característica con el UUID proporcionado
        val characteristic = peripheral!!.services?.flatMap {
            (it as? CBService)?.characteristics ?: emptyList()
        }?.find { (it as? CBCharacteristic)?.UUID?.UUIDString == characteristic.substring(4, 8) } as? CBCharacteristic

        if (characteristic != null) {
            // Habilitar o deshabilitar las notificaciones según el valor de 'value'
            if (value) {
                // Habilitar notificaciones
                peripheral.setNotifyValue(true, characteristic)
                println("Habilitando notificaciones en la característica ${characteristic} para el periférico ${peripheralName}")
            } else {
                // Deshabilitar notificaciones
                peripheral.setNotifyValue(false, characteristic)
                println("Deshabilitando notificaciones en la característica ${characteristic} para el periférico ${peripheralName}")
            }
        } else {
            println("Característica con UUID $characteristic no encontrada en el periférico.")
        }
    }
    @OptIn(ExperimentalForeignApi::class)
    fun ByteArray.toNSDataNoCopy(): NSData {
        val bytes = this
        val length = bytes.size.toULong()
        val ptr = nativeHeap.allocArray<ByteVar>(bytes.size)
        memcpy(ptr, bytes.refTo(0), bytes.size.toULong())
        return NSData.create(bytesNoCopy = ptr, length = length, freeWhenDone = true)
    }
    // CBCentralManagerDelegate Methods

    override fun centralManagerDidUpdateState(central: CBCentralManager) {
        NSLog("Bluetooth State: ${when (central.state) {
            CBManagerStatePoweredOn -> "ON"
            CBManagerStatePoweredOff -> "OFF"
            else -> "Turning OFF"
        }}")
        _adapterstateFlow.value = when (central.state) {
            CBManagerStatePoweredOn -> AdapterState.STATE_ON
            CBManagerStatePoweredOff -> AdapterState.STATE_OFF
            else -> AdapterState.STATE_TURNING_OFF
        }
        //startScan()
    }

    private val _connectFlow = MutableStateFlow<String?>(null)
    val connectFlow = _connectFlow.asStateFlow()
    override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
        val address = didConnectPeripheral.identifier.UUIDString
        didConnectPeripheral.delegate = this
        didConnectPeripheral.discoverServices(null)
        _connectFlow.value = address
    }

    private val _discoverPeriferalFlow = MutableStateFlow<Triple<String, Int, Map<Any?, *>>?>(null)
    private val _connectErrorFlow = MutableStateFlow<String?>(null)
    val discoverPeriferalFlow = _discoverPeriferalFlow.asStateFlow()
    override fun centralManager(
        central: CBCentralManager,
        didDiscoverPeripheral: CBPeripheral,
        advertisementData: Map<Any?, *>,
        RSSI: NSNumber
    ) {
        //super.centralManager(central, didDiscoverPeripheral, advertisementData, RSSI)
        dispatch_async(dispatch_get_main_queue()) {
            val uuid = didDiscoverPeripheral.identifier.UUIDString
            discoveredPeripherals[uuid] = didDiscoverPeripheral
            _discoverPeriferalFlow.value = Triple(
                uuid,
                RSSI.intValue,
                advertisementData
            )
        }
    }

    val connectErrorFlow = _connectErrorFlow.asStateFlow()
    override fun centralManager(central: CBCentralManager, didFailToConnectPeripheral: CBPeripheral, error: NSError?) {
        println("IOS-MMCORE: CONNECT ERROR $error")
        _connectErrorFlow.value = didFailToConnectPeripheral.identifier.UUIDString
    }
    private val _discoverServicesFlow = MutableStateFlow<String?>(null)
    val discoverServicesFlow = _discoverServicesFlow.asStateFlow()
    override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) {
        println("IOS-MMCORE: Discover services")
        peripheral.services?.forEach { service ->
            peripheral.discoverCharacteristics(null, service as CBService)
        }
        _discoverServicesFlow.value = peripheral.identifier.UUIDString

    }

    private val _updateCharacteristicFlow = MutableStateFlow<Triple<String, ByteArray, String>?>(null)
    val updateCharacteristicFlow = _updateCharacteristicFlow.asStateFlow()
    override fun peripheral(peripheral: CBPeripheral, didUpdateValueForCharacteristic: CBCharacteristic, error: NSError?) {
        println("IOS-MMCORE: UPDATE CHARACTERISTIC")
        val value = didUpdateValueForCharacteristic.value
        val byteArray = value?.toByteArray() ?: byteArrayOf()
        _updateCharacteristicFlow.value = Triple(peripheral.identifier.UUIDString, byteArray, didUpdateValueForCharacteristic.UUID.UUIDString)
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


    override fun peripheral(peripheral: CBPeripheral, didDiscoverCharacteristicsForService: CBService, error: NSError?) {
        println("IOS-MMCORE: DISCOVER CHARACTERISTICS")
        didDiscoverCharacteristicsForService.characteristics?.forEach { characteristic ->
            val char = characteristic as CBCharacteristic
            peripheral.setNotifyValue(true, char)
            peripheral.readValueForCharacteristic(char)
        }
    }
}