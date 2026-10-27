package pe.upeu.agrosierra.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import pe.upeu.agrosierra.data.local.*
import pe.upeu.agrosierra.data.remote.AgroApi
import pe.upeu.agrosierra.data.remote.SesionCambiada
import pe.upeu.agrosierra.domain.model.*
import pe.upeu.agrosierra.domain.repository.*
import kotlin.random.Random

class AgroRepositoryImpl(private val api: AgroApi, private val local: BaseLocal,
    private val sesiones: SesionRepository) : AgroRepository {
    private val mutex = Mutex()
    override fun observarLotes(valle: String, busqueda: String, estado: Boolean?) = local.lotes(valle, busqueda, estado)
    override fun observarLabores(loteId: Long) = local.labores(loteId).map { filas ->
        val usuarioId = sesiones.restaurar()?.usuarioId
        filas.filter { !it.pendiente || it.propietarioPendiente == usuarioId }
    }
    override suspend fun lote(id: Long) = withContext(Dispatchers.Default) { local.lote(id) }
    override fun ultimaSincronizacion() = local.metadata("ultimaSincronizacion")
    private fun sesion() = sesiones.restaurar() ?: throw ErrorApi.NoAutorizado
    private suspend fun asegurar(u: Sesion) {
        currentCoroutineContext().ensureActive()
        if (sesiones.restaurar()?.token != u.token) throw SesionCambiada()
    }
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
            if (anterior != null && anterior.propietario != u.usuarioId)
                throw ErrorApi.SinPrivilegio("Este pendiente pertenece a otra cuenta")
            local.transaccion {
                local.limpiarConflicto(id)
                local.guardar(fila)
                local.encolar(id, if (id < 0) "POST" else "PUT",
                    anterior?.clave ?: "${u.usuarioId}-${Clock.System.now().toEpochMilliseconds()}-${Random.nextLong()}", u.usuarioId)
            }
            fila
        }
    }
    override suspend fun descartar(id: Long) = withContext(Dispatchers.Default) {
        mutex.withLock {
            val u = sesion()
            val pendiente = local.pendientes().find { it.laborId == id }
                ?: throw ErrorApi.NoEncontrado("El cambio local ya no está pendiente")
            if (pendiente.propietario != u.usuarioId) throw ErrorApi.SinPrivilegio("Este pendiente pertenece a otra cuenta")
            // Descarta únicamente el cambio local. Nunca envía DELETE al servidor.
            local.transaccion { local.borrar(id) }
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
            val u = sesion()
            if ("CAMPANIA_CERRAR" !in u.privilegios) throw ErrorApi.SinPrivilegio("No puede cerrar campañas")
            if (local.pendientes().any { local.labor(it.laborId)?.loteId == loteId })
                throw ErrorApi.Conflicto("Sincronice las labores pendientes antes de cerrar la campaña")
            val cerrada = api.cerrar(loteId, u.token).aDominio()
            asegurar(u); local.guardar(cerrada)
        }
    }
    override suspend fun sincronizar(): ResumenSync = withContext(Dispatchers.Default) {
        mutex.withLock {
            val u = sesion(); var enviados = 0; var recibidos = 0
            val conflictos = mutableListOf<ConflictoSync>()
            val idsEnviados = mutableSetOf<Long>()
            // Primero subir. Una fila de otro usuario nunca se envía con la sesión actual.
            local.pendientes().filter { it.propietario == u.usuarioId }.forEach { pendiente ->
                asegurar(u)
                val fila = local.labor(pendiente.laborId) ?: return@forEach
                try {
                when (pendiente.operacion) {
                    "POST" -> {
                        var remota = api.crear(fila, pendiente.clave, u.token).aDominio()
                        // Si la primera respuesta se perdió y el usuario corrigió la fila,
                        // el POST idempotente devuelve el original: aplicar después la corrección.
                        if (remota.tipo != fila.tipo || remota.fecha != fila.fecha ||
                            remota.cantidad != fila.cantidad || remota.observacion != fila.observacion) {
                            remota = api.editar(fila.copy(id = remota.id), u.token).aDominio()
                        }
                        asegurar(u)
                        local.sustituir(fila.id, remota)
                    }
                    "PUT" -> {
                        val remota = api.editar(fila, u.token).aDominio()
                        asegurar(u); local.sustituir(fila.id, remota)
                    }
                    "DELETE" -> {
                        try { api.eliminar(fila.id, u.token) } catch (e: ErrorApi.NoEncontrado) { /* Ya eliminada: operación idempotente. */ }
                        asegurar(u)
                        local.transaccion { local.borrar(fila.id) }
                    }
                }
                enviados++
                idsEnviados += fila.id
                } catch (e: ErrorApi) {
                    if (e == ErrorApi.NoAutorizado || e == ErrorApi.SinConexion || e == ErrorApi.TiempoAgotado) throw e
                    asegurar(u)
                    val motivo = if (e is ErrorApi.Validacion && e.campos.isNotEmpty())
                        e.campos.entries.joinToString("; ") { "${it.key}: ${it.value}" } else e.message
                    local.conflicto(fila.id, motivo)
                    conflictos += ConflictoSync(fila.id, fila.loteId, e)
                }
            }
            // Después descargar todas las páginas. Conservar cualquier pendiente local.
            val lotes = api.lotes(u.token).map { it.aDominio() }
            asegurar(u)
            local.transaccion { lotes.forEach(local::guardar) }; recibidos += lotes.size
            lotes.forEach { lote ->
                val filas = api.labores(lote.id, u.token).map { it.aDominio() }
                asegurar(u)
                local.refrescar(lote.id, filas); recibidos += filas.size
            }
            val fecha = Clock.System.now().toString()
            local.metadata("ultimaSincronizacion", fecha)
            ResumenSync(enviados, recibidos, fecha, conflictos, idsEnviados)
        }
    }
}
