package pe.upeu.agrosierra

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pe.upeu.agrosierra.data.local.*
import pe.upeu.agrosierra.data.remote.*
import pe.upeu.agrosierra.data.remote.dto.*
import pe.upeu.agrosierra.data.repository.*
import pe.upeu.agrosierra.domain.model.*
import pe.upeu.agrosierra.domain.repository.*
import kotlin.test.*

expect fun driverEnMemoria(): SqlDriver

class AlmacenPrueba : AlmacenSeguro {
    var sesion: Sesion? = Sesion("token-prueba", "tecnico1", "TECNICO", setOf("LOTE_LEER", "LABOR_LEER", "LABOR_CREAR", "LABOR_EDITAR"), "Técnico", 1, 4102444800)
    override fun leer() = sesion
    override fun guardar(sesion: Sesion) { this.sesion = sesion }
    override fun borrar() { sesion = null }
}
private val lote = Lote(1, "LOT-001", "Lote 1", 3.0, "Maíz", "Mantaro", false, 3.0)
private val loteJson = """{"id":1,"codigo":"LOT-001","nombre":"Lote 1","hectareas":3.0,"cultivo":"Maíz","valle":"Mantaro","campaniaCerrada":false,"dosisMaximaPorHectarea":3.0}"""
private val laborJson = """{"id":10,"loteId":1,"tipo":"RIEGO","fecha":"2026-10-27","responsable":"Técnico","cantidad":2.0,"observacion":"Prueba","responsableId":1}"""
private fun pagina(contenido: String) = """{"contenido":[$contenido],"pagina":0,"tamanio":100,"totalElementos":1,"totalPaginas":1,"ultima":true}"""

class AgroRepositoryTest {
    @Test fun `DTO convierte al dominio sin anotaciones de transporte`() {
        val dto = LoteDto(1, "LOT-001", "Lote 1", 3.0, "Maíz", "Mantaro", false, 3.0)
        assertEquals(lote, dto.aDominio())
        val labor = LaborDto(10, 1, "RIEGO", "2026-10-27", "Técnico", 2.0, "Prueba").aDominio()
        assertFalse(labor.pendiente); assertEquals(TipoLabor.RIEGO, labor.tipo)
    }
    @Test fun `400 conserva errores de campo`() = runTest {
        val almacen = AlmacenPrueba()
        val http = crearClienteHttp(MockEngine { respond("""{"message":"Datos inválidos","validationErrors":{"cantidad":"Dosis excedida"}}""", HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json")) }, ApiConfig("http://localhost/api/v1/"), almacen)
        try {
            val api = AgroApi(http, almacen)
            val e = assertFailsWith<ErrorApi.Validacion> { api.lotes() }
            assertEquals("Dosis excedida", e.campos["cantidad"])
        } finally { http.close() }
    }
    @Test fun `401 borra sesion mientras 403 la conserva`() = runTest {
        for (codigo in listOf(401, 403)) {
            val almacen = AlmacenPrueba()
            val http = crearClienteHttp(MockEngine { respond("""{"message":"Acceso rechazado"}""", HttpStatusCode.fromValue(codigo), headersOf(HttpHeaders.ContentType, "application/json")) }, ApiConfig("http://localhost/api/v1/"), almacen)
            try {
                if (codigo == 401) { assertFailsWith<ErrorApi.NoAutorizado> { AgroApi(http, almacen).lotes() }; assertNull(almacen.leer()) }
                else { assertFailsWith<ErrorApi.SinPrivilegio> { AgroApi(http, almacen).lotes() }; assertNotNull(almacen.leer()) }
            } finally { http.close() }
        }
    }
    @Test fun `registro offline persiste con ID negativo y marca pendiente`() = runTest {
        val driver = driverEnMemoria(); val local = BaseLocal(driver); local.guardar(lote)
        val almacen = AlmacenPrueba()
        val http = crearClienteHttp(MockEngine { throw IOException("Sin red") }, ApiConfig("http://localhost/api/v1/"), almacen)
        try {
            val api = AgroApi(http, almacen)
            val repo = AgroRepositoryImpl(api, local, SesionRepositoryImpl(api, almacen))
            repo.guardar(Labor(0, 1, TipoLabor.RIEGO, "2026-10-27", "", 2.0, "Prueba", true))
            val filas = repo.observarLabores(1).first()
            assertEquals(1, filas.size); assertTrue(filas.single().id < 0); assertTrue(filas.single().pendiente)
            assertEquals("POST", local.pendientes().single().operacion)
        } finally { http.close(); driver.close() }
    }
    @Test fun `sincronizacion sube antes de bajar y sustituye la fila sin duplicarla`() = runTest {
        val driver = driverEnMemoria(); val local = BaseLocal(driver); local.guardar(lote)
        var online = false; val llamadas = mutableListOf<String>()
        val almacen = AlmacenPrueba()
        val http = crearClienteHttp(MockEngine { req ->
            if (!online) throw IOException("Sin red")
            llamadas += "${req.method.value} ${req.url.encodedPath}"
            val body = when {
                req.method == HttpMethod.Post -> laborJson
                req.url.encodedPath.endsWith("/labores") -> pagina(laborJson)
                else -> pagina(loteJson)
            }
            respond(body, if (req.method == HttpMethod.Post) HttpStatusCode.Created else HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }, ApiConfig("http://localhost/api/v1/"), almacen)
        try {
            val api = AgroApi(http, almacen); val repo = AgroRepositoryImpl(api, local, SesionRepositoryImpl(api, almacen))
            repo.guardar(Labor(0, 1, TipoLabor.RIEGO, "2026-10-27", "", 2.0, "Prueba", true))
            online = true
            val resumen = repo.sincronizar()
            assertEquals("POST /api/v1/labores", llamadas.first())
            assertTrue(llamadas[1].startsWith("GET")); assertEquals(1, resumen.enviados)
            val filas = repo.observarLabores(1).first()
            assertEquals(1, filas.size); assertEquals(10L, filas.single().id); assertFalse(filas.single().pendiente)
            assertTrue(local.pendientes().isEmpty())
            repo.sincronizar(); assertEquals(1, repo.observarLabores(1).first().size)
        } finally { http.close(); driver.close() }
    }
    @Test fun `204 no intenta deserializar cuerpo y cabecera es centralizada`() = runTest {
        val almacen = AlmacenPrueba(); var cabecera: String? = null
        val http = crearClienteHttp(MockEngine { req ->
            cabecera = req.headers[HttpHeaders.Authorization]
            respond("", HttpStatusCode.NoContent)
        }, ApiConfig("http://localhost/api/v1/"), almacen)
        try { AgroApi(http, almacen).eliminar(10); assertEquals("Bearer token-prueba", cabecera) }
        finally { http.close() }
    }
    @Test fun `sesion vencida se descarta sin consultar red`() {
        val almacen = AlmacenPrueba(); almacen.sesion = almacen.sesion!!.copy(venceEn = 1)
        val http = crearClienteHttp(MockEngine { error("No debe consultar red") }, ApiConfig("http://localhost/api/v1/"), almacen)
        try { assertNull(SesionRepositoryImpl(AgroApi(http, almacen), almacen).restaurar()); assertNull(almacen.leer()) }
        finally { http.close() }
    }
    @Test fun `SQLite filtra el padron local por valle y nombre`() = runTest {
        val driver = driverEnMemoria()
        try {
            val local = BaseLocal(driver); local.guardar(lote); local.guardar(lote.copy(id = 2, codigo = "LOT-002", valle = "Colca"))
            assertEquals(listOf(lote), local.lotes("Mantaro", "001").first())
            assertTrue(local.lotes("Colca", "inexistente").first().isEmpty())
        } finally { driver.close() }
    }
    @Test fun `SC A combina campania abierta valle y busqueda sin conexion`() = runTest {
        val driver = driverEnMemoria()
        try {
            val local = BaseLocal(driver)
            local.guardar(lote)
            local.guardar(lote.copy(id = 2, codigo = "LOT-002", campaniaCerrada = true))
            local.guardar(lote.copy(id = 3, codigo = "LOT-003", valle = "Colca"))
            assertEquals(listOf(lote), local.lotes("Mantaro", "001", false).first())
            assertTrue(local.lotes("Mantaro", "002", false).first().isEmpty())
            assertEquals(2L, local.lotes("Mantaro", "002", true).first().single().id)
            assertEquals(3, local.lotes("", "", null).first().size)
        } finally { driver.close() }
    }
    @Test fun `SC A el filtro observable se actualiza al cambiar la campania`() = runTest {
        val driver = driverEnMemoria()
        try {
            val local = BaseLocal(driver); local.guardar(lote)
            assertEquals(1, local.lotes("", "", false).first().size)
            local.guardar(lote.copy(campaniaCerrada = true))
            assertTrue(local.lotes("", "", false).first().isEmpty())
            assertEquals(1, local.lotes("", "", true).first().size)
        } finally { driver.close() }
    }
}
