package pe.upeu.agrosierra.data.repository

import kotlinx.datetime.Clock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import pe.upeu.agrosierra.data.local.AlmacenSeguro
import pe.upeu.agrosierra.data.remote.AgroApi
import pe.upeu.agrosierra.data.remote.SesionCambiada
import pe.upeu.agrosierra.domain.model.*
import pe.upeu.agrosierra.domain.repository.SesionRepository

class SesionRepositoryImpl(private val api: AgroApi, private val almacen: AlmacenSeguro) : SesionRepository {
    private val generacion = MutableStateFlow(0L)
    override fun restaurar(): Sesion? {
        val sesion = almacen.leer() ?: return null
        if (sesion.venceEn <= Clock.System.now().epochSeconds) { almacen.borrarSiToken(sesion.token); return null }
        return sesion
    }
    override suspend fun login(usuario: String, contrasena: String): Sesion {
        val version = generacion.updateAndGet { it + 1 }
        val inicio = Clock.System.now().epochSeconds
        val dto = api.login(usuario, contrasena)
        currentCoroutineContext().ensureActive()
        if (generacion.value != version) throw SesionCambiada()
        val provisional = Sesion(dto.token, dto.usuario, dto.rol, dto.privilegios, dto.nombre, venceEn = inicio + 3595)
        almacen.guardar(provisional)
        return try { perfil() } catch (e: Exception) { almacen.borrarSiToken(dto.token); throw e }
    }
    override suspend fun perfil(): Sesion {
        val anterior = restaurar() ?: throw ErrorApi.NoAutorizado
        val dto = api.perfil(anterior.token)
        currentCoroutineContext().ensureActive()
        val actualizada = anterior.copy(usuario = dto.usuario, nombre = dto.nombre, rol = dto.rol,
            privilegios = dto.privilegios, usuarioId = dto.id)
        if (!almacen.guardarSiToken(anterior.token, actualizada)) throw SesionCambiada()
        return actualizada
    }
    override fun salir() { generacion.updateAndGet { it + 1 }; almacen.borrar() }
}
