import kotlinx.serialization.SerialName

@kotlinx.serialization.Serializable
data class VideoImage(
    @SerialName("imagen")
    var imagen: String = "",
    @SerialName("video")
    val video: String = ""
)
