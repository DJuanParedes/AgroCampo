package pe.upeu.agrosierra.data.repository

import kotlinx.datetime.Clock
import pe.upeu.agrosierra.data.local.AlmacenSeguro
import pe.upeu.agrosierra.data.remote.AgroApi
import pe.upeu.agrosierra.domain.model.*
import pe.upeu.agrosierra.domain.repository.SesionRepository

class SesionRepositoryImpl(private val api: AgroApi, private val almacen: AlmacenSeguro) : SesionRepository {
    override fun restaurar(): Sesion? {
        val sesion = almacen.leer() ?: return null
        if (sesion.venceEn <= Clock.System.now().epochSeconds) { almacen.borrar(); return null }
        return sesion
    }
    override suspend fun login(usuario: String, contrasena: String): Sesion {
        val inicio = Clock.System.now().epochSeconds
        val dto = api.login(usuario, contrasena)
        val provisional = Sesion(dto.token, dto.usuario, dto.rol, dto.privilegios, dto.nombre, venceEn = inicio + 3595)
        almacen.guardar(provisional)
        return try { perfil() } catch (e: Exception) { almacen.borrar(); throw e }
    }
    override suspend fun perfil(): Sesion {
        val anterior = restaurar() ?: throw ErrorApi.NoAutorizado
        val dto = api.perfil()
        return anterior.copy(usuario = dto.usuario, nombre = dto.nombre, rol = dto.rol,
            privilegios = dto.privilegios, usuarioId = dto.id).also(almacen::guardar)
    }
    override fun salir() = almacen.borrar()
}
