import kotlinx.serialization.SerialName

@kotlinx.serialization.Serializable
data class Device(
    @SerialName("fldNNumberDevice")
    val fldNNumberDevice: Int,
    @SerialName("id")
    val id: Int,
    @SerialName("fkPosition")
    val fkPosition: Int,
    @SerialName("fkOwner")
    val fkOwner: Int,
    @SerialName("position")
    val position: Position,
    @SerialName("imagen")
    val image: String? = null,
)
