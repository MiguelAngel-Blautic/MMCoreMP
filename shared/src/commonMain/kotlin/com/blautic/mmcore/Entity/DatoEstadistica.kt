
import kotlinx.serialization.SerialName

@kotlinx.serialization.Serializable
data class DatoEstadistica(
    @SerialName("sample")
    var sample: Int,
    @SerialName("media")
    var media: Float,
    @SerialName("std")
    var std: Float,
)
