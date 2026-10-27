package pe.upeu.agrosierra

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import pe.upeu.agrosierra.domain.model.*
import pe.upeu.agrosierra.domain.model.Labor
import pe.upeu.agrosierra.domain.model.Lote
import pe.upeu.agrosierra.domain.repository.*
import pe.upeu.agrosierra.domain.usecase.*
import pe.upeu.agrosierra.presentation.listado.*
import kotlin.test.*

private val loteVm = Lote(1, "LOT-001", "Lote 1", 3.0, "Maíz", "Mantaro", false, 3.0)
private class SesionVm : SesionRepository {
    var sesion: Sesion? = AlmacenPrueba().leer()
    override fun restaurar() = sesion
    override suspend fun login(usuario: String, contrasena: String) = sesion!!
    override suspend fun perfil() = sesion!!
    override fun salir() { sesion = null }
}
private class RepoVm : AgroRepository {
    var cierres = 0; var sincronizaciones = 0; var esperar = false; var cancelado = false
    var fallarSyncUnaVez = false; var guardados = 0
    override fun observarLotes(valle: String, busqueda: String, estado: Boolean?) = flowOf(listOf(loteVm))
    override fun observarLabores(loteId: Long) = flowOf(emptyList<Labor>())
    override suspend fun lote(id: Long) = loteVm.copy(campaniaCerrada = cierres > 1)
    override suspend fun guardar(labor: Labor): Labor { guardados++; return labor.copy(id = -1) }
    override suspend fun eliminar(labor: Labor) { }
    override suspend fun descartar(id: Long) { }
    override suspend fun cerrar(loteId: Long) { cierres++; if (cierres == 1) throw ErrorApi.Servidor }
    override suspend fun sincronizar(): ResumenSync {
        sincronizaciones++
        if (fallarSyncUnaVez && sincronizaciones == 1) throw ErrorApi.Servidor
        if (esperar) try { awaitCancellation() } finally { cancelado = true }
        return ResumenSync(0, 1, "2026-10-27T12:00:00Z", idsEnviados = setOf(-1))
    }
    override fun ultimaSincronizacion(): String? = null
}
@OptIn(ExperimentalCoroutinesApi::class)
class AgroViewModelTest {
    @Test fun `reintentar cierre repite el cierre y no otra sincronizacion`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val repo = RepoVm(); val sesiones = SesionVm()
            val vm = AgroViewModel(sesiones, repo, SincronizarUseCase(repo), GuardarLaborUseCase(repo), object : Conectividad { override val disponible = emptyFlow<Boolean>() })
            store.put("vm", vm); vm.abrir(loteVm); runCurrent()
            vm.cerrar(); runCurrent()
            assertEquals(AccionReintento.CerrarCampania(1), vm.estado.value.reintento)
            vm.reintentar(); runCurrent()
            assertEquals(2, repo.cierres); assertEquals(0, repo.sincronizaciones)
            assertTrue(vm.estado.value.seleccionado!!.campaniaCerrada)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun `salir cancela la sincronizacion activa y limpia el estado`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val repo = RepoVm().apply { esperar = true }; val sesiones = SesionVm()
            val vm = AgroViewModel(sesiones, repo, SincronizarUseCase(repo), GuardarLaborUseCase(repo), object : Conectividad { override val disponible = emptyFlow<Boolean>() })
            store.put("vm", vm); vm.sync(); runCurrent(); vm.salir(); runCurrent()
            assertTrue(repo.cancelado); assertNull(vm.estado.value.sesion)
            assertFalse(vm.estado.value.sincronizando); assertNull(sesiones.restaurar())
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun `reintentar envio no vuelve a crear el registro local`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val repo = RepoVm().apply { fallarSyncUnaVez = true }; val sesiones = SesionVm()
            val vm = AgroViewModel(sesiones, repo, SincronizarUseCase(repo), GuardarLaborUseCase(repo), object : Conectividad { override val disponible = emptyFlow<Boolean>() })
            store.put("vm", vm); vm.abrir(loteVm); runCurrent(); vm.formulario()
            vm.guardar(TipoLabor.RIEGO, "2026-10-27", "2.0", "Prueba"); runCurrent()
            assertEquals(AccionReintento.EnviarLabor(-1), vm.estado.value.reintento)
            vm.reintentar(); runCurrent()
            assertEquals(1, repo.guardados); assertEquals(2, repo.sincronizaciones)
            assertFalse(vm.estado.value.formulario)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
