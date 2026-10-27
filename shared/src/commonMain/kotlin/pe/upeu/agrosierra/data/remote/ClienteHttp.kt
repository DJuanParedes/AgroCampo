package pe.upeu.agrosierra.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import pe.upeu.agrosierra.data.local.AlmacenSeguro

val TokenSesion = AttributeKey<String>("AgroTokenSesion")
class SesionCambiada : CancellationException("La sesión de esta operación ya no está activa")

fun crearClienteHttp(engine: HttpClientEngine, config: ApiConfig, almacen: AlmacenSeguro) = HttpClient(engine) {
    expectSuccess = false
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(HttpTimeout) { requestTimeoutMillis = 15000; connectTimeoutMillis = 10000; socketTimeoutMillis = 15000 }
    install(Logging) {
        level = if (config.depuracion) LogLevel.INFO else LogLevel.NONE
        sanitizeHeader { it == HttpHeaders.Authorization }
    }
    defaultRequest {
        url(config.baseUrl)
        contentType(ContentType.Application.Json)
    }
    install(io.ktor.client.plugins.api.createClientPlugin("AutorizacionAgro") {
        onRequest { request, _ ->
            if (!request.url.encodedPath.endsWith("auth/login")) {
                val token = request.attributes.getOrNull(TokenSesion) ?: almacen.leer()?.token
                if (token != null) {
                    if (almacen.leer()?.token != token) throw SesionCambiada()
                    request.bearerAuth(token)
                }
            }
        }
    })
}
