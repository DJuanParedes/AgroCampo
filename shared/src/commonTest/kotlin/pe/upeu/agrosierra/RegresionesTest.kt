package pe.upeu.agrosierra

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import pe.upeu.agrosierra.data.local.BaseLocal
import pe.upeu.agrosierra.data.remote.*
import pe.upeu.agrosierra.data.repository.*
import pe.upeu.agrosierra.db.AgroDb
import pe.upeu.agrosierra.domain.model.*
import pe.upeu.agrosierra.domain.model.Labor
import pe.upeu.agrosierra.domain.model.Lote
import pe.upeu.agrosierra.domain.usecase.puedeEditarLabor
import kotlin.test.*

private val cabecerasJson = headersOf(HttpHeaders.ContentType, "application/json")
private val loteRegresion = Lote(1, "LOT-001", "Lote 1", 3.0, "Maíz", "Mantaro", false, 3.0)
private const val loteRegresionJson = """{"id":1,"codigo":"LOT-001","nombre":"Lote 1","hectareas":3.0,"cultivo":"Maíz","valle":"Mantaro","campaniaCerrada":false,"dosisMaximaPorHectarea":3.0}"""
private fun laborRegresion(id: Long = 0) = Labor(id, 1, TipoLabor.RIEGO, "2026-10-27", "Técnico", 2.0, "Prueba", id <= 0, 1, "2026-10-27T12:00:00Z")
private fun laborRegresionJson(id: Long = 10, cantidad: Double = 2.0) = """{"id":$id,"loteId":1,"tipo":"RIEGO","fecha":"2026-10-27","responsable":"Técnico","cantidad":$cantidad,"observacion":"Prueba","responsableId":1,"creadaEn":"2026-10-27T12:00:00Z"}"""
private fun paginaRegresion(contenido: String) = """{"contenido":[$contenido],"pagina":0,"tamanio":100,"totalElementos":1,"totalPaginas":1,"ultima":true}"""
private class Entorno(engine: MockEngine, val almacen: AlmacenPrueba = AlmacenPrueba()) {
    val driver: SqlDriver = driverEnMemoria()
    val local = BaseLocal(driver).also { it.guardar(loteRegresion) }
    val http: HttpClient = crearClienteHttp(engine, ApiConfig("http://localhost/api/v1/"), almacen)
    val api = AgroApi(http, almacen)
    val sesiones = SesionRepositoryImpl(api, almacen)
    val repo = AgroRepositoryImpl(api, local, sesiones)
    fun cerrar() { http.close(); driver.close() }
}

class RegresionesTest {
    @Test fun `un rechazo no bloquea los otros pendientes ni la descarga`() = runTest {
        var posts = 0; var gets = 0
        val e = Entorno(MockEngine { req ->
            if (req.method == HttpMethod.Post) {
                posts++
                if (posts == 1) respond("""{"message":"Dosis excedida","validationErrors":{"cantidad":"Máximo 9"}}""", HttpStatusCode.BadRequest, cabecerasJson)
                else respond(laborRegresionJson(), HttpStatusCode.Created, cabecerasJson)
            } else {
                gets++
                respond(paginaRegresion(if (req.url.encodedPath.endsWith("/labores")) laborRegresionJson() else loteRegresionJson), HttpStatusCode.OK, cabecerasJson)
            }
        })
        try {
            e.repo.guardar(laborRegresion()); e.repo.guardar(laborRegresion())
            val r = e.repo.sincronizar()
            assertEquals(2, posts); assertEquals(2, gets); assertEquals(1, r.enviados)
            assertEquals(1, r.conflictos.size); assertEquals(1, e.local.pendientes().size)
            assertEquals("Máximo 9", (r.conflictos.single().error as ErrorApi.Validacion).campos["cantidad"])
            assertEquals("cantidad: Máximo 9", e.local.labor(r.conflictos.single().idLocal)?.errorSincronizacion)
        } finally { e.cerrar() }
    }
    @Test fun `cambio de cuenta detiene el envio sin mezclar credenciales`() = runTest {
        val almacen = AlmacenPrueba(); val tokens = mutableListOf<String?>()
        val e = Entorno(MockEngine { req ->
            tokens += req.headers[HttpHeaders.Authorization]
            almacen.sesion = almacen.sesion!!.copy(token = "otra-cuenta", usuarioId = 2)
            respond(laborRegresionJson(), HttpStatusCode.Created, cabecerasJson)
        }, almacen)
        try {
            e.repo.guardar(laborRegresion()); e.repo.guardar(laborRegresion())
            assertFailsWith<SesionCambiada> { e.repo.sincronizar() }
            assertEquals(listOf<String?>("Bearer token-prueba"), tokens.toList())
            assertEquals(2, e.local.pendientes().size)
            assertEquals(2L, almacen.leer()?.usuarioId)
        } finally { e.cerrar() }
    }
    @Test fun `401 de una sesion antigua no borra el nuevo login`() = runTest {
        val almacen = AlmacenPrueba()
        val e = Entorno(MockEngine {
            almacen.sesion = almacen.sesion!!.copy(token = "nuevo-token", usuarioId = 2)
            respond("""{"message":"Vencido"}""", HttpStatusCode.Unauthorized, cabecerasJson)
        }, almacen)
        try {
            assertFailsWith<SesionCambiada> { e.api.perfil() }
            assertEquals("nuevo-token", almacen.leer()?.token)
        } finally { e.cerrar() }
    }
    @Test fun `perfil pendiente no restaura una sesion cerrada`() = runTest {
        val almacen = AlmacenPrueba()
        val e = Entorno(MockEngine {
            almacen.borrar()
            respond("""{"id":1,"usuario":"tecnico1","nombre":"Técnico","rol":"TECNICO","privilegios":[]}""", HttpStatusCode.OK, cabecerasJson)
        }, almacen)
        try {
            assertFailsWith<SesionCambiada> { e.sesiones.perfil() }
            assertNull(almacen.leer())
        } finally { e.cerrar() }
    }
    @Test fun `un tecnico puede descartar su alta local sin privilegio de eliminar`() = runTest {
        var llamadas = 0
        val e = Entorno(MockEngine { llamadas++; error("No debe llamar al servidor") })
        try {
            val fila = e.repo.guardar(laborRegresion())
            assertFalse("LABOR_ELIMINAR" in e.almacen.leer()!!.privilegios)
            e.repo.descartar(fila.id)
            assertTrue(e.local.pendientes().isEmpty()); assertNull(e.local.labor(fila.id)); assertEquals(0, llamadas)
        } finally { e.cerrar() }
    }
    @Test fun `otra cuenta no puede modificar ni descartar el pendiente ajeno`() = runTest {
        val e = Entorno(MockEngine { error("Sin red") })
        try {
            val fila = e.repo.guardar(laborRegresion())
            e.almacen.sesion = e.almacen.sesion!!.copy(usuarioId = 2, token = "otra")
            assertFailsWith<ErrorApi.SinPrivilegio> { e.repo.descartar(fila.id) }
            assertFailsWith<ErrorApi.SinPrivilegio> { e.repo.guardar(fila.copy(cantidad = 5.0)) }
            assertTrue(e.repo.observarLabores(1).first().isEmpty())
        } finally { e.cerrar() }
    }
    @Test fun `PUT 404 conserva conflicto y permite descartar el cambio local`() = runTest {
        val e = Entorno(MockEngine { req ->
            if (req.method == HttpMethod.Put) respond("""{"message":"Labor eliminada"}""", HttpStatusCode.NotFound, cabecerasJson)
            else respond(paginaRegresion(if (req.url.encodedPath.endsWith("/labores")) "" else loteRegresionJson), HttpStatusCode.OK, cabecerasJson)
        })
        try {
            e.local.guardar(laborRegresion(10)); e.repo.guardar(laborRegresion(10).copy(cantidad = 3.0))
            assertTrue(e.repo.sincronizar().conflictos.single().error is ErrorApi.NoEncontrado)
            assertEquals("Labor eliminada", e.local.labor(10)?.errorSincronizacion)
            e.repo.descartar(10); assertTrue(e.repo.sincronizar().conflictos.isEmpty())
            assertNull(e.local.labor(10))
        } finally { e.cerrar() }
    }
    @Test fun `edicion offline y eliminacion offline se envian sin deserializar 204`() = runTest {
        var online = false; val metodos = mutableListOf<String>()
        val almacen = AlmacenPrueba().apply { sesion = sesion!!.copy(privilegios = sesion!!.privilegios + "LABOR_ELIMINAR") }
        val e = Entorno(MockEngine { req ->
            if (!online) throw IOException("Sin conexión")
            metodos += req.method.value
            when (req.method) {
                HttpMethod.Put -> respond(laborRegresionJson(10, 5.0), HttpStatusCode.OK, cabecerasJson)
                HttpMethod.Delete -> respond("", HttpStatusCode.NoContent)
                else -> respond(paginaRegresion(if (req.url.encodedPath.endsWith("/labores")) laborRegresionJson(10, 5.0) else loteRegresionJson), HttpStatusCode.OK, cabecerasJson)
            }
        }, almacen)
        try {
            e.local.guardar(laborRegresion(10)); e.local.guardar(laborRegresion(11))
            e.repo.guardar(laborRegresion(10).copy(cantidad = 5.0))
            e.repo.eliminar(laborRegresion(11))
            assertEquals(2, e.local.pendientes().size)
            online = true; assertEquals(2, e.repo.sincronizar().enviados)
            assertEquals(listOf("PUT", "DELETE"), metodos.take(2)); assertNull(e.local.labor(11))
            assertEquals(5.0, e.local.labor(10)?.cantidad); assertTrue(e.local.pendientes().isEmpty())
        } finally { e.cerrar() }
    }
    @Test fun `respuesta de POST perdida se reintenta con la misma clave sin duplicar`() = runTest {
        val claves = mutableListOf<String?>(); var posts = 0
        val e = Entorno(MockEngine { req ->
            if (req.method == HttpMethod.Post) {
                posts++; claves += req.headers["Idempotency-Key"]
                if (posts == 1) throw IOException("Servidor aceptó; respuesta perdida")
                respond(laborRegresionJson(), HttpStatusCode.Created, cabecerasJson)
            } else respond(paginaRegresion(if (req.url.encodedPath.endsWith("/labores")) laborRegresionJson() else loteRegresionJson), HttpStatusCode.OK, cabecerasJson)
        })
        try {
            e.repo.guardar(laborRegresion())
            assertFailsWith<ErrorApi.SinConexion> { e.repo.sincronizar() }
            e.repo.sincronizar(); e.repo.sincronizar()
            assertEquals(2, posts); assertEquals(claves.first(), claves.last())
            assertEquals(1, e.repo.observarLabores(1).first().size)
            assertEquals(10L, e.repo.observarLabores(1).first().single().id)
        } finally { e.cerrar() }
    }
    @Test fun `RN02 calcula 24 horas usando instantes con cualquier desplazamiento`() {
        val sesion = AlmacenPrueba().leer()!!
        val ahora = Instant.parse("2026-10-27T22:00:00Z")
        assertFalse(puedeEditarLabor(laborRegresion(10).copy(creadaEn = "2026-10-26T21:00:00Z"), sesion, ahora))
        assertFalse(puedeEditarLabor(laborRegresion(10).copy(creadaEn = "2026-10-26T16:00:00-05:00"), sesion, ahora))
        assertTrue(puedeEditarLabor(laborRegresion(10).copy(creadaEn = "2026-10-27T12:00:00Z"), sesion, ahora))
    }
    @Test fun `un formulario con ID provisional ya sustituido no crea un duplicado`() = runTest {
        val e = Entorno(MockEngine { error("No debe consultar red") })
        try {
            val provisional = e.repo.guardar(laborRegresion())
            e.local.sustituir(provisional.id, laborRegresion(10).copy(pendiente = false))
            assertFailsWith<ErrorApi.NoEncontrado> { e.repo.guardar(provisional.copy(cantidad = 4.0)) }
            assertTrue(e.local.pendientes().isEmpty())
            assertEquals(1, e.repo.observarLabores(1).first().size)
        } finally { e.cerrar() }
    }
    @Test fun `migracion de SQLite conserva los pendientes de version 1`() = runTest {
        val e = Entorno(MockEngine { error("No debe consultar red") })
        try {
            val fila = e.repo.guardar(laborRegresion())
            e.driver.execute(null, "DROP TABLE conflicto", 0)
            AgroDb.Schema.migrate(e.driver, 1, 2)
            assertEquals(fila.id, e.local.pendientes().single().laborId)
            assertEquals(2.0, e.local.labor(fila.id)?.cantidad)
            e.local.conflicto(fila.id, "Prueba de migración")
            assertEquals("Prueba de migración", e.local.labor(fila.id)?.errorSincronizacion)
        } finally { e.cerrar() }
    }
}
