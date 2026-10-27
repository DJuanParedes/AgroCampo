package pe.upeu.agrosierra.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import pe.upeu.agrosierra.data.local.*
import pe.upeu.agrosierra.data.remote.AgroApi
import pe.upeu.agrosierra.domain.model.*
import pe.upeu.agrosierra.domain.repository.*
import kotlin.random.Random

class AgroRepositoryImpl(private val api: AgroApi, private val local: BaseLocal,
    private val sesiones: SesionRepository) : AgroRepository {
    private val mutex = Mutex()
    override fun observarLotes(valle: String, busqueda: String) = local.lotes(valle, busqueda)
    override fun observarLabores(loteId: Long) = local.labores(loteId)
    override suspend fun lote(id: Long) = withContext(Dispatchers.Default) { local.lote(id) }
    override fun ultimaSincronizacion() = local.metadata("ultimaSincronizacion")
    private fun sesion() = sesiones.restaurar() ?: throw ErrorApi.NoAutorizado
    override suspend fun guardar(labor: Labor): Labor = withContext(Dispatchers.Default) {
        mutex.withLock {
            val u = sesion()
            val permiso = if (labor.id == 0L) "LABOR_CREAR" else "LABOR_EDITAR"
            if (permiso !in u.privilegios) throw ErrorApi.SinPrivilegio("No posee el privilegio requerido")
            val id = if (labor.id == 0L) local.idLocal() else labor.id
            val fila = labor.copy(id = id, pendiente = true,
                responsable = if (labor.id == 0L) u.nombre else labor.responsable,
                responsableId = if (labor.id == 0L) u.usuarioId else labor.responsableId)
            val anterior = local.pendientes().find { it.laborId == id }
            local.transaccion {
                local.guardar(fila)
                local.encolar(id, if (id < 0) "POST" else "PUT",
                    anterior?.clave ?: "${u.usuarioId}-${Clock.System.now().toEpochMilliseconds()}-${Random.nextLong()}", u.usuarioId)
            }
            fila
        }
    }
    override suspend fun eliminar(labor: Labor) = withContext(Dispatchers.Default) {
        mutex.withLock {
            val u = sesion()
            if ("LABOR_ELIMINAR" !in u.privilegios) throw ErrorApi.SinPrivilegio("No puede eliminar labores")
            local.transaccion {
                if (labor.id < 0) local.borrar(labor.id) else {
                    local.guardar(labor.copy(pendiente = true))
                    local.encolar(labor.id, "DELETE", "delete-${labor.id}", u.usuarioId)
                }
            }
        }
        try { sincronizar(); Unit } catch (e: ErrorApi) {
            if (e != ErrorApi.SinConexion && e != ErrorApi.TiempoAgotado) throw e
        }
    }
    override suspend fun cerrar(loteId: Long) = withContext(Dispatchers.Default) {
        mutex.withLock {
            if ("CAMPANIA_CERRAR" !in sesion().privilegios) throw ErrorApi.SinPrivilegio("No puede cerrar campañas")
            if (local.pendientes().any { local.labor(it.laborId)?.loteId == loteId })
                throw ErrorApi.Conflicto("Sincronice las labores pendientes antes de cerrar la campaña")
            local.guardar(api.cerrar(loteId).aDominio())
        }
    }
    override suspend fun sincronizar(): ResumenSync = withContext(Dispatchers.Default) {
        mutex.withLock {
            val u = sesion(); var enviados = 0; var recibidos = 0
            // Primero subir. Una fila de otro usuario nunca se envía con la sesión actual.
            local.pendientes().filter { it.propietario == u.usuarioId }.forEach { pendiente ->
                val fila = local.labor(pendiente.laborId) ?: return@forEach
                when (pendiente.operacion) {
                    "POST" -> local.sustituir(fila.id, api.crear(fila, pendiente.clave).aDominio())
                    "PUT" -> local.sustituir(fila.id, api.editar(fila).aDominio())
                    "DELETE" -> {
                        try { api.eliminar(fila.id) } catch (e: ErrorApi.NoEncontrado) { /* Ya eliminada: operación idempotente. */ }
                        local.transaccion { local.borrar(fila.id) }
                    }
                }
                enviados++
            }
            // Después descargar todas las páginas. Conservar cualquier pendiente local.
            val lotes = api.lotes().map { it.aDominio() }
            local.transaccion { lotes.forEach(local::guardar) }; recibidos += lotes.size
            lotes.forEach { lote ->
                val filas = api.labores(lote.id).map { it.aDominio() }
                local.refrescar(lote.id, filas); recibidos += filas.size
            }
            val fecha = Clock.System.now().toString()
            local.metadata("ultimaSincronizacion", fecha)
            ResumenSync(enviados, recibidos, fecha)
        }
    }
}
