package pe.upeu.agrosierra.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import pe.upeu.agrosierra.data.local.AlmacenSeguro
import pe.upeu.agrosierra.data.remote.dto.*
import pe.upeu.agrosierra.domain.model.Labor
import pe.upeu.agrosierra.domain.model.ErrorApi

class AgroApi(private val http: HttpClient, private val almacen: AlmacenSeguro) {
    private fun tokenActual() = almacen.leer()?.token ?: throw ErrorApi.NoAutorizado
    private suspend fun enviar(token: String, bloque: suspend () -> HttpResponse): HttpResponse {
        currentCoroutineContext().ensureActive()
        if (almacen.leer()?.token != token) throw SesionCambiada()
        val respuesta = bloque()
        currentCoroutineContext().ensureActive()
        respuesta.verificar(almacen, token)
        return respuesta
    }
    suspend fun login(usuario: String, contrasena: String): SesionDto = peticion {
        http.post("auth/login") { setBody(LoginDto(usuario, contrasena)) }.also { it.verificar(almacen) }.body()
    }
    suspend fun perfil(token: String = tokenActual()): UsuarioDto = peticion {
        enviar(token) { http.get("usuarios/me") { attributes.put(TokenSesion, token) } }.body()
    }
    suspend fun lotes(token: String = tokenActual()): List<LoteDto> = peticion {
        val resultado = mutableListOf<LoteDto>(); var pagina = 0
        do {
            val res = enviar(token) { http.get("lotes") { attributes.put(TokenSesion, token); parameter("pagina", pagina); parameter("tamanio", 100) } }.body<PaginaDto<LoteDto>>()
            resultado.addAll(res.contenido); pagina++
        } while (!res.ultima)
        resultado
    }
    suspend fun labores(id: Long, token: String = tokenActual()): List<LaborDto> = peticion {
        val resultado = mutableListOf<LaborDto>(); var pagina = 0
        do {
            val res = enviar(token) { http.get("lotes/$id/labores") { attributes.put(TokenSesion, token); parameter("pagina", pagina); parameter("tamanio", 100) } }.body<PaginaDto<LaborDto>>()
            resultado.addAll(res.contenido); pagina++
        } while (!res.ultima)
        resultado
    }
    suspend fun crear(labor: Labor, clave: String, token: String = tokenActual()): LaborDto = peticion {
        enviar(token) { http.post("labores") { attributes.put(TokenSesion, token); header("Idempotency-Key", clave); setBody(labor.aPeticion()) } }.body()
    }
    suspend fun editar(labor: Labor, token: String = tokenActual()): LaborDto = peticion {
        enviar(token) { http.put("labores/${labor.id}") { attributes.put(TokenSesion, token); setBody(labor.aPeticion()) } }.body()
    }
    suspend fun eliminar(id: Long, token: String = tokenActual()): Unit = peticion {
        // 204 no contiene JSON: se verifica el código sin deserializar.
        enviar(token) { http.delete("labores/$id") { attributes.put(TokenSesion, token) } }; Unit
    }
    suspend fun cerrar(id: Long, token: String = tokenActual()): LoteDto = peticion {
        enviar(token) { http.post("lotes/$id/cerrar-campania") { attributes.put(TokenSesion, token) } }.body()
    }
}
