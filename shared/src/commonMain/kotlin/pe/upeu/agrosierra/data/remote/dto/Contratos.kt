package pe.upeu.agrosierra.data.remote.dto

import kotlinx.serialization.Serializable
import pe.upeu.agrosierra.domain.model.*

@Serializable data class PaginaDto<T>(val contenido: List<T>, val pagina: Int, val tamanio: Int,
    val totalElementos: Long, val totalPaginas: Int, val ultima: Boolean)
@Serializable data class ErrorDto(val timestamp: String = "", val status: Int = 0, val error: String = "",
    val message: String = "No se pudo completar la operación", val path: String = "",
    val validationErrors: Map<String, String> = emptyMap())
@Serializable data class LoginDto(val usuario: String, val contrasena: String)
@Serializable data class SesionDto(val token: String, val usuario: String, val nombre: String,
    val rol: String, val privilegios: Set<String>)
@Serializable data class UsuarioDto(val id: Long, val usuario: String, val nombre: String,
    val rol: String, val privilegios: Set<String>)
@Serializable data class LoteDto(val id: Long, val codigo: String, val nombre: String,
    val hectareas: Double, val cultivo: String, val valle: String, val campaniaCerrada: Boolean,
    val dosisMaximaPorHectarea: Double) {
    fun aDominio() = Lote(id, codigo, nombre, hectareas, cultivo, valle, campaniaCerrada, dosisMaximaPorHectarea)
}
@Serializable data class LaborDto(val id: Long, val loteId: Long, val tipo: String, val fecha: String,
    val responsable: String, val cantidad: Double, val observacion: String,
    val responsableId: Long = 0, val creadaEn: String = "") {
    fun aDominio() = Labor(id, loteId, TipoLabor.valueOf(tipo), fecha, responsable, cantidad, observacion, false, responsableId, creadaEn)
}
@Serializable data class LaborRequestDto(val loteId: Long, val tipo: String, val fecha: String,
    val cantidad: Double, val observacion: String)
fun Labor.aPeticion() = LaborRequestDto(loteId, tipo.name, fecha, cantidad, observacion)
