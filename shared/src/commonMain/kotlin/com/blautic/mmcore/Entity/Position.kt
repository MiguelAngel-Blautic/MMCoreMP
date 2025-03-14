
import kotlinx.serialization.SerialName

@kotlinx.serialization.Serializable
data class Position(
    @SerialName("fldSName")
    var fldSName: String = "",
    @SerialName("fldSDescription")
    val fldSDescription: String = "",
    @SerialName("id")
    val id: Int = 0,
)
