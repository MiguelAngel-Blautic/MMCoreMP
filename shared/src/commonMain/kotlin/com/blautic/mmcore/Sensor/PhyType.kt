package Sensor

enum class PhyType(val value: Int, val mask: Int) {
    LE_1M(1, 1),

    LE_2M(2, 2),

    LE_CODED(3, 4),

    UNKNOWN_PHY_TYPE(-1, -1);

    companion object {
        fun fromValue(value: Int): PhyType {
            for (type in PhyType.values()) {
                if (type.value == value) return type
            }
            return UNKNOWN_PHY_TYPE
        }
    }
}
