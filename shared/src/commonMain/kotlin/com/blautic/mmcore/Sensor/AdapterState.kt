package Sensor

enum class AdapterState(val value: Int) {
    STATE_OFF(10),
    STATE_ON(12),
    STATE_TURNING_OFF(13),
    STATE_TURNING_ON(11);

    companion object {
        fun fromValue(value: Int): AdapterState {
            for (state in values()) {
                if (state.value == value) return state
            }
            return STATE_OFF
        }
    }
}