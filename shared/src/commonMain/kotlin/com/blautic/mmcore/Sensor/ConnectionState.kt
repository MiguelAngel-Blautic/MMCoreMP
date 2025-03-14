package Sensor


enum class ConnectionState(val value: Int) {

    DISCONNECTED(0),

    CONNECTING(1),

    CONNECTED(2),

    DISCONNECTING(3),

    FAILED(4);

    companion object {
        fun fromValue(value: Int): ConnectionState {
            for (type in values()) {
                if (type.value == value) {
                    return type
                }
            }
            return DISCONNECTED
        }
    }
}