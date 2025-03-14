package Sensor

data class ScanResult(
    val name: String = "",
    val address: String = "",
    var rssi: Int = 0,
    var button: Boolean = false,
    var typeSensor: TypeSensor = TypeSensor.BIO1,
) {

    fun rssiLevel(): Int {
        return when {
            rssi < -65 -> 3
            rssi < -45 -> 2
            rssi < -28 -> 1
            rssi < -10 -> 0
            else -> 4
        }
    }
}