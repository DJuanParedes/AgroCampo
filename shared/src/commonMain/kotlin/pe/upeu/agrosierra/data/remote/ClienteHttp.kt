package pe.upeu.agrosierra.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import pe.upeu.agrosierra.data.local.AlmacenSeguro

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
        // La cabecera se añade aquí para todas las operaciones protegidas.
        if (!url.encodedPath.endsWith("auth/login")) almacen.leer()?.let { bearerAuth(it.token) }
    }
}
