
import kotlinx.serialization.SerialName

@kotlinx.serialization.Serializable
data class ObjetoEstadistica(
    @SerialName("id")
    var id: Int,
    @SerialName("nombre")
    var nombre: String,
    @SerialName("datos")
    var datos: List<DatoEstadistica>,
    @SerialName("idPosicion")
    var idPosicion: Int?,
)
