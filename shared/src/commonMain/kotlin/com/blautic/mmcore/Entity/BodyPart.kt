package Entity

enum class BodyPart(val position: Int) {

    NOSE(0),
    LEFT_EYE(1),
    RIGHT_EYE(2),
    LEFT_EAR(3),
    RIGHT_EAR(4),
    LEFT_SHOULDER(5),
    RIGHT_SHOULDER(6),
    LEFT_ELBOW(7),
    RIGHT_ELBOW(8),
    LEFT_WRIST(9),
    RIGHT_WRIST(10),
    LEFT_HIP(11),
    RIGHT_HIP(12),
    LEFT_KNEE(13),
    RIGHT_KNEE(14),
    LEFT_ANKLE(15),
    RIGHT_ANKLE(16),
    LEFT_PULGAR(17),
    RIGHT_PULGAR(18),
    LEFT_INDICE(19),
    RIGHT_INDICE(20),
    LEFT_PINKY(21),
    RIGHT_PINKY(22),
    LEFT_PIE(23),
    RIGHT_PIE(24),
    LEFT_TALON(25),
    RIGHT_TALON(26);

    companion object{
        private val map = values().associateBy(BodyPart::position)
        fun fromInt(position: Int): BodyPart = map.getValue(position)
        fun fromMediaPipe(position: Int): BodyPart?{
            return when(position){
                0 -> NOSE
                1 -> null
                2 -> LEFT_EYE
                3 -> null
                4 -> null
                5 -> RIGHT_EYE
                6 -> null
                7 -> LEFT_EAR
                8 -> RIGHT_EAR
                9 -> null
                10 -> null
                11 -> LEFT_SHOULDER
                12 -> RIGHT_SHOULDER
                13 -> LEFT_ELBOW
                14 -> RIGHT_ELBOW
                15 -> LEFT_WRIST
                16 -> RIGHT_WRIST
                17 -> LEFT_PINKY
                18 -> RIGHT_PINKY
                19 -> LEFT_INDICE
                20 -> RIGHT_INDICE
                21 -> LEFT_PULGAR
                22 -> RIGHT_PULGAR
                23 -> LEFT_HIP
                24 -> RIGHT_HIP
                25 -> LEFT_KNEE
                26 -> RIGHT_KNEE
                27 -> LEFT_ANKLE
                28 -> RIGHT_ANKLE
                29 -> LEFT_TALON
                30 -> RIGHT_TALON
                31 -> LEFT_PIE
                32 -> RIGHT_PIE
                else -> null
            }
        }
    }

}