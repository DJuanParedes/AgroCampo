package pe.upeu.agrosierra.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import pe.upeu.agrosierra.db.AgroDb
import pe.upeu.agrosierra.domain.model.*

class BaseLocal(driver: SqlDriver) {
    val db = AgroDb(driver)
    private val q = db.agroDbQueries
    fun lotes(valle: String, busqueda: String, estado: Boolean? = null) = q.observarLotes(valle, busqueda, estado?.let { if (it) 1L else 0L }).asFlow().mapToList(Dispatchers.Default).map { rows ->
        rows.map { Lote(it.id, it.codigo, it.nombre, it.hectareas, it.cultivo, it.valle, it.campaniaCerrada == 1L, it.dosisMaxima) }
    }
    fun todosLotes() = q.todosLotes().executeAsList().map { Lote(it.id, it.codigo, it.nombre, it.hectareas, it.cultivo, it.valle, it.campaniaCerrada == 1L, it.dosisMaxima) }
    fun lote(id: Long) = q.lotePorId(id).executeAsOneOrNull()?.let {
        Lote(it.id, it.codigo, it.nombre, it.hectareas, it.cultivo, it.valle, it.campaniaCerrada == 1L, it.dosisMaxima)
    }
    fun labores(id: Long) = q.observarLaboresPorLote(id).asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map { fila ->
        Labor(fila.id, fila.loteId, TipoLabor.valueOf(fila.tipo), fila.fecha, fila.responsable, fila.cantidad, fila.observacion, fila.pendiente == 1L, fila.responsableId, fila.creadaEn, fila.mensaje, fila.propietario)
    } }
    fun labor(id: Long) = q.laborPorId(id).executeAsOneOrNull()?.let {
        Labor(it.id, it.loteId, TipoLabor.valueOf(it.tipo), it.fecha, it.responsable, it.cantidad, it.observacion, it.pendiente == 1L, it.responsableId, it.creadaEn, it.mensaje, it.propietario)
    }
    fun guardar(l: Lote) = q.guardarLote(l.id, l.codigo, l.nombre, l.hectareas, l.cultivo, l.valle, if (l.campaniaCerrada) 1 else 0, l.dosisMaximaPorHectarea)
    fun guardar(l: Labor) = q.guardarLabor(l.id, l.loteId, l.tipo.name, l.fecha, l.responsable, l.cantidad, l.observacion, if (l.pendiente) 1 else 0, l.responsableId, l.creadaEn)
    fun idLocal() = minOf(-1L, q.siguienteIdLocal().executeAsOne() - 1)
    fun encolar(id: Long, op: String, clave: String, propietario: Long) = q.encolar(id, op, clave, propietario)
    fun pendientes() = q.pendientes().executeAsList()
    fun borrar(id: Long) { q.borrarLabor(id); q.desencolar(id); q.borrarConflicto(id) }
    fun conflicto(id: Long, mensaje: String) = q.guardarConflicto(id, mensaje)
    fun limpiarConflicto(id: Long) = q.borrarConflicto(id)
    fun sustituir(local: Long, definitiva: Labor) = db.transaction { borrar(local); guardar(definitiva) }
    fun refrescar(lote: Long, labores: List<Labor>) = db.transaction {
        q.borrarRemotasDelLote(lote)
        labores.forEach { if (labor(it.id)?.pendiente != true) guardar(it) }
    }
    fun metadata(clave: String) = q.leerMetadata(clave).executeAsOneOrNull()
    fun metadata(clave: String, valor: String) = q.guardarMetadata(clave, valor)
    fun transaccion(block: () -> Unit) = db.transaction { block() }
}
