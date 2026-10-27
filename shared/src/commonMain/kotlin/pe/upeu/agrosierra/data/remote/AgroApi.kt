package pe.upeu.agrosierra.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import pe.upeu.agrosierra.data.local.AlmacenSeguro
import pe.upeu.agrosierra.data.remote.dto.*
import pe.upeu.agrosierra.domain.model.Labor

class AgroApi(private val http: HttpClient, private val almacen: AlmacenSeguro) {
    suspend fun login(usuario: String, contrasena: String): SesionDto = peticion {
        http.post("auth/login") { setBody(LoginDto(usuario, contrasena)) }.also { it.verificar(almacen) }.body()
    }
    suspend fun perfil(): UsuarioDto = peticion { http.get("usuarios/me").also { it.verificar(almacen) }.body() }
    suspend fun lotes(): List<LoteDto> = peticion {
        val resultado = mutableListOf<LoteDto>(); var pagina = 0
        do {
            val res = http.get("lotes") { parameter("pagina", pagina); parameter("tamanio", 100) }
                .also { it.verificar(almacen) }.body<PaginaDto<LoteDto>>()
            resultado.addAll(res.contenido); pagina++
        } while (!res.ultima)
        resultado
    }
    suspend fun labores(id: Long): List<LaborDto> = peticion {
        val resultado = mutableListOf<LaborDto>(); var pagina = 0
        do {
            val res = http.get("lotes/$id/labores") { parameter("pagina", pagina); parameter("tamanio", 100) }
                .also { it.verificar(almacen) }.body<PaginaDto<LaborDto>>()
            resultado.addAll(res.contenido); pagina++
        } while (!res.ultima)
        resultado
    }
    suspend fun crear(labor: Labor, clave: String): LaborDto = peticion {
        http.post("labores") { header("Idempotency-Key", clave); setBody(labor.aPeticion()) }.also { it.verificar(almacen) }.body()
    }
    suspend fun editar(labor: Labor): LaborDto = peticion {
        http.put("labores/${labor.id}") { setBody(labor.aPeticion()) }.also { it.verificar(almacen) }.body()
    }
    suspend fun eliminar(id: Long) = peticion {
        // 204 no contiene JSON: se verifica el código sin deserializar.
        http.delete("labores/$id").verificar(almacen)
    }
    suspend fun cerrar(id: Long): LoteDto = peticion {
        http.post("lotes/$id/cerrar-campania").also { it.verificar(almacen) }.body()
    }
}
