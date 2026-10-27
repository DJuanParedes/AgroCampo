package pe.upeu.agrosierra.domain.repository

import kotlinx.coroutines.flow.Flow
import pe.upeu.agrosierra.domain.model.*

data class ConflictoSync(val idLocal: Long, val loteId: Long, val error: ErrorApi)
data class ResumenSync(val enviados: Int, val recibidos: Int, val fecha: String,
    val conflictos: List<ConflictoSync> = emptyList(), val idsEnviados: Set<Long> = emptySet())
interface AgroRepository {
    fun observarLotes(valle: String, busqueda: String, estado: Boolean? = null): Flow<List<Lote>>
    fun observarLabores(loteId: Long): Flow<List<Labor>>
    suspend fun lote(id: Long): Lote?
    suspend fun guardar(labor: Labor): Labor
    suspend fun eliminar(labor: Labor)
    suspend fun descartar(id: Long)
    suspend fun cerrar(loteId: Long)
    suspend fun sincronizar(): ResumenSync
    fun ultimaSincronizacion(): String?
}
interface SesionRepository {
    fun restaurar(): Sesion?
    suspend fun login(usuario: String, contrasena: String): Sesion
    suspend fun perfil(): Sesion
    fun salir()
}
interface Conectividad { val disponible: Flow<Boolean> }
