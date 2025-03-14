import kotlinx.serialization.SerialName

@kotlinx.serialization.Serializable
data class Creador(
    @SerialName("id")
    val id: Int,
    @SerialName("nombre")
    val nombre: String,
    @SerialName("verificado")
    val verificado: Int,
)
