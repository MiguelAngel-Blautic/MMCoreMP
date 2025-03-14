
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Model(
    @SerialName("devices")
    val devices: List<Device>,
    @SerialName("fkOwner")
    val fkOwner: Int,
    @SerialName("fkTipo")
    val fkTipo: Int = 1,
    @SerialName("fldBAutoTraining")
    val fldBAutoTraining: Boolean = false,
    @SerialName("fldDTimeCreateTime")
    val fldDTimeCreateTime: String,
    @SerialName("fldNDuration")
    val fldNDuration: Int,
    @SerialName("fldNProgress")
    val fldNProgress: Int? = null,
    @SerialName("fldSDescription")
    val fldSDescription: String?,
    @SerialName("fldSImage")
    val fldSImage: String? = null,
    @SerialName("fldSName")
    val fldSName: String,
    @SerialName("fldSStatus")
    val fldSStatus: Int,
    @SerialName("fldSUrl")
    val fldSUrl: String? = null,
    @SerialName("fldSVideo")
    val fldSVideo: String? = null,
    @SerialName("id")
    val id: Int,
    @SerialName("movements")
    val movements: List<Movement>,
    @SerialName("versions")
    val versions: List<Version>,
    @SerialName("dispositivos")
    var dispositivos: List<DispositivoSensor>,
    @SerialName("imagen")
    val imagen: String? = null,
    @SerialName("video")
    val video: String? = null,
    @SerialName("fldBPublico")
    val fldBPublico: Int = 0,
    @SerialName("tuyo")
    val tuyo: Int = 1,
    @SerialName("fldBRegresivo")
    val fldBRegresivo: Int = 0,
    @SerialName("fldFMinValor")
    val minValor: Float = 0f,
    @SerialName("fldFMaxValor")
    val maxValor: Float = 1f,
    @SerialName("fldSNomValor")
    val nomValor: String = "",
    @SerialName("fldSToken")
    val fldSToken: String? = null,
    @SerialName("creador")
    val creador: Creador? = null
)