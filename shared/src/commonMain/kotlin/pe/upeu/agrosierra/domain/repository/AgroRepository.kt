package pe.upeu.agrosierra.domain.repository

import kotlinx.coroutines.flow.Flow
import pe.upeu.agrosierra.domain.model.*

data class ResumenSync(val enviados: Int, val recibidos: Int, val fecha: String)
interface AgroRepository {
    fun observarLotes(valle: String, busqueda: String): Flow<List<Lote>>
    fun observarLabores(loteId: Long): Flow<List<Labor>>
    suspend fun lote(id: Long): Lote?
    suspend fun guardar(labor: Labor): Labor
    suspend fun eliminar(labor: Labor)
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
