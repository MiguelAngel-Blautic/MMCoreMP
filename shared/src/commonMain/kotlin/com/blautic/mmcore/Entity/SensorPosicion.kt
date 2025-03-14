package Entity

import Sensor.TypeSensor

data class SensorPosicion(var tipoSensor: MutableList<TypeSensor>, val posicion: Int)

enum class Posiciones(private val id: Int, private val nombre: String){
    POINTS(0, "Ninguna"),
    RIGHT_HAND(1, "Mano derecha"),
    RIGHT_ARM(2, "Brazo derecho"),
    RIGHT_SHOULDER(3, "Hombro derecho"),
    RIGHT_HIP(4, "Cadera derecha"),
    LOWER_TORSO(5, "Torso inferior"),
    CENTER_TORSO(6, "Torso central"),
    CHEST(7, "PEcho"),
    LEFT_HIP(8, "Cadera Izquierda"),
    LEFT_HAND(9, "Mano Izquierda"),
    LEFT_ARM(10, "Brazo Izquierdo"),
    LEFT_SHOULDER(11, "Hombro Izquierdo"),
    RIGHT_FOOT(12, "Pie derecho"),
    RIGHT_THIGH(13, "Muslo derecho"),
    LEFT_FOOT(14, "Pie Izquierdo"),
    LEFT_THIGH(15, "Muslo Izquierdo");

    fun getNombre(id: Int): String{
        return entries.find { it.id == id }?.nombre ?: "Ninguna"
    }
    fun getId(nombre: String): Int{
        return entries.find { it.nombre == nombre }?.id ?: 0
    }
}