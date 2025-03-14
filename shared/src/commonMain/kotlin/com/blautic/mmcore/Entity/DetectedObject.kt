package Entity

data class DetectedObject(
    val classId: Int,          // ID de la clase detectada
    val confidence: Float,     // Nivel de confianza (0 - 1)
    val boundingBox: FloatArray // [ymin, xmin, ymax, xmax] (coordenadas normalizadas)
) {
    fun getBoundingBoxAsRect(imageWidth: Int, imageHeight: Int): BoundingBox {
        val ymin = (boundingBox[0] * imageHeight).toInt()
        val xmin = (boundingBox[1] * imageWidth).toInt()
        val ymax = (boundingBox[2] * imageHeight).toInt()
        val xmax = (boundingBox[3] * imageWidth).toInt()

        return BoundingBox(xmin, ymin, xmax, ymax)
    }
}

data class BoundingBox(
    val left: Int,   // Coordenada X de la esquina superior izquierda
    val top: Int,    // Coordenada Y de la esquina superior izquierda
    val right: Int,  // Coordenada X de la esquina inferior derecha
    val bottom: Int  // Coordenada Y de la esquina inferior derecha
)