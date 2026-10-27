package pe.upeu.agrosierra.data.remote

import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException
import pe.upeu.agrosierra.data.local.AlmacenSeguro
import pe.upeu.agrosierra.data.remote.dto.ErrorDto
import pe.upeu.agrosierra.domain.model.ErrorApi

suspend fun HttpResponse.verificar(almacen: AlmacenSeguro, token: String? = null) {
    if (token != null && almacen.leer()?.token != token) throw SesionCambiada()
    if (status.value in 200..299) return
    val error = try { body<ErrorDto>() } catch (_: Exception) { ErrorDto() }
    throw when (status.value) {
        400 -> ErrorApi.Validacion(error.validationErrors, error.message)
        401 -> { if (token != null) almacen.borrarSiToken(token); ErrorApi.NoAutorizado }
        403 -> ErrorApi.SinPrivilegio(error.message)
        404 -> ErrorApi.NoEncontrado(error.message)
        409 -> ErrorApi.Conflicto(error.message)
        else -> ErrorApi.Servidor
    }
}
suspend fun <T> peticion(block: suspend () -> T): T = try { block() }
catch (e: CancellationException) { throw e }
catch (e: ErrorApi) { throw e }
catch (e: HttpRequestTimeoutException) { throw ErrorApi.TiempoAgotado }
catch (e: ConnectTimeoutException) { throw ErrorApi.TiempoAgotado }
catch (e: SocketTimeoutException) { throw ErrorApi.TiempoAgotado }
catch (e: IOException) { throw ErrorApi.SinConexion }
