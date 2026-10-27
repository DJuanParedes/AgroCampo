package pe.upeu.agrosierra.presentation.listado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.*
import pe.upeu.agrosierra.domain.model.*
import pe.upeu.agrosierra.domain.repository.*
import pe.upeu.agrosierra.domain.usecase.*

data class AgroUiState(
    val sesion: Sesion? = null, val cargando: Boolean = false, val sincronizando: Boolean = false,
    val conectado: Boolean = true, val lotes: List<Lote> = emptyList(), val labores: List<Labor> = emptyList(),
    val seleccionado: Lote? = null, val perfil: Boolean = false, val valle: String = "", val busqueda: String = "",
    val ultimaSync: String? = null, val resumen: String? = null, val error: ErrorApi? = null,
    val editando: Labor? = null, val formulario: Boolean = false, val guardando: Boolean = false,
    val erroresCampo: Map<String, String> = emptyMap(), val editables: Set<Long> = emptySet()
)

class AgroViewModel(private val sesiones: SesionRepository, private val repo: AgroRepository,
    private val sincronizar: SincronizarUseCase, private val guardarLabor: GuardarLaborUseCase,
    conectividad: Conectividad) : ViewModel() {
    private val mutable = MutableStateFlow(AgroUiState(sesion = sesiones.restaurar(), ultimaSync = repo.ultimaSincronizacion()))
    val estado = mutable.asStateFlow()
    private var listaJob: Job? = null
    private var detalleJob: Job? = null
    init {
        observarLista()
        viewModelScope.launch {
            conectividad.disponible.distinctUntilChanged().collect { conectado ->
                mutable.update { it.copy(conectado = conectado) }
                if (conectado && sesiones.restaurar() != null) sync()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(15000)
                if (mutable.value.sesion != null && sesiones.restaurar() == null) salir()
            }
        }
    }
    private fun observarLista() {
        listaJob?.cancel()
        listaJob = viewModelScope.launch {
            repo.observarLotes(mutable.value.valle, mutable.value.busqueda).collect { lotes ->
                mutable.update { it.copy(lotes = lotes, seleccionado = it.seleccionado?.let { previo -> lotes.find { l -> l.id == previo.id } ?: previo }) }
            }
        }
    }
    fun filtrar(valle: String = mutable.value.valle, busqueda: String = mutable.value.busqueda) {
        mutable.update { it.copy(valle = valle, busqueda = busqueda) }; observarLista()
    }
    fun login(usuario: String, contrasena: String) = viewModelScope.launch {
        mutable.update { it.copy(cargando = true, error = null) }
        try {
            val sesion = sesiones.login(usuario.trim(), contrasena)
            mutable.update { it.copy(sesion = sesion, cargando = false) }; sync()
        } catch (e: Exception) { fallo(e); mutable.update { it.copy(cargando = false) } }
    }
    fun sync() {
        if (mutable.value.sincronizando || mutable.value.sesion == null) return
        mutable.update { it.copy(sincronizando = true, error = null) }
        viewModelScope.launch {
            try {
                val r = sincronizar()
                mutable.update { it.copy(ultimaSync = r.fecha, resumen = "${r.enviados} enviados · ${r.recibidos} recibidos") }
            } catch (e: Exception) { fallo(e) }
            finally { mutable.update { it.copy(sincronizando = false) } }
        }
    }
    fun abrir(lote: Lote) {
        mutable.update { it.copy(seleccionado = lote, perfil = false, labores = emptyList(), cargando = true, error = null) }
        detalleJob?.cancel()
        detalleJob = viewModelScope.launch {
            repo.observarLabores(lote.id).collect { filas ->
                val sesion = mutable.value.sesion
                val editables = filas.filter { labor ->
                    sesion != null && "LABOR_EDITAR" in sesion.privilegios &&
                        ("LABOR_EDITAR_TODAS" in sesion.privilegios || labor.responsableId == sesion.usuarioId && dentroDePlazo(labor))
                }.map { it.id }.toSet()
                mutable.update { it.copy(labores = filas, cargando = false, editables = editables) }
            }
        }
    }
    private fun dentroDePlazo(labor: Labor): Boolean {
        if (labor.id < 0) return true
        return try {
            val fecha = LocalDateTime.parse(labor.creadaEn).toInstant(TimeZone.of("America/Lima"))
            Clock.System.now().epochSeconds - fecha.epochSeconds <= 86400
        } catch (_: Exception) { false }
    }
    fun volver() {
        if (mutable.value.formulario) { mutable.update { it.copy(formulario = false, error = null, erroresCampo = emptyMap()) }; return }
        detalleJob?.cancel(); mutable.update { it.copy(seleccionado = null, perfil = false, error = null) }
    }
    fun perfil() {
        mutable.update { it.copy(perfil = true, seleccionado = null, error = null) }
        viewModelScope.launch {
            try { mutable.update { it.copy(sesion = sesiones.perfil()) } }
            catch (e: Exception) { if (e != ErrorApi.SinConexion && e != ErrorApi.TiempoAgotado) fallo(e) }
        }
    }
    fun formulario(labor: Labor? = null) { mutable.update { it.copy(formulario = true, editando = labor, error = null, erroresCampo = emptyMap()) } }
    fun guardar(tipo: TipoLabor, fecha: String, cantidad: String, observacion: String) = viewModelScope.launch {
        val lote = mutable.value.seleccionado ?: return@launch
        val numero = cantidad.toDoubleOrNull()
        if (numero == null) { mutable.update { it.copy(erroresCampo = mapOf("cantidad" to "Ingrese una cantidad numérica")) }; return@launch }
        mutable.update { it.copy(guardando = true, erroresCampo = emptyMap(), error = null) }
        try {
            val original = mutable.value.editando
            val guardada = guardarLabor(original?.copy(tipo = tipo, fecha = fecha, cantidad = numero, observacion = observacion)
                ?: Labor(0, lote.id, tipo, fecha, "", numero, observacion, true))
            mutable.update { it.copy(editando = guardada) }
            var enviada = false
            try { sincronizar(); enviada = true }
            catch (e: ErrorApi) { if (e != ErrorApi.SinConexion && e != ErrorApi.TiempoAgotado) throw e }
            mutable.update { it.copy(formulario = false, ultimaSync = repo.ultimaSincronizacion(), resumen = if (enviada) "Registro sincronizado" else "Guardado sin conexión · pendiente de envío") }
        } catch (e: Exception) { fallo(e) }
        finally { mutable.update { it.copy(guardando = false) } }
    }
    fun eliminar(labor: Labor) = viewModelScope.launch {
        try { repo.eliminar(labor); mutable.update { it.copy(resumen = "Eliminación guardada") } }
        catch (e: Exception) { fallo(e) }
    }
    fun cerrar() = viewModelScope.launch {
        val lote = mutable.value.seleccionado ?: return@launch
        mutable.update { it.copy(cargando = true, error = null) }
        try {
            repo.cerrar(lote.id)
            mutable.update { it.copy(seleccionado = repo.lote(lote.id), resumen = "Campaña cerrada") }
        } catch (e: Exception) { fallo(e) }
        finally { mutable.update { it.copy(cargando = false) } }
    }
    fun salir() { sesiones.salir(); detalleJob?.cancel(); mutable.value = AgroUiState(); observarLista() }
    fun limpiarMensaje() { mutable.update { it.copy(error = null, resumen = null) } }
    private fun fallo(e: Exception) {
        if (e is CancellationException) throw e
        val error = e as? ErrorApi ?: ErrorApi.Servidor
        if (error == ErrorApi.NoAutorizado && mutable.value.sesion != null) { salir(); return }
        mutable.update { it.copy(error = error, erroresCampo = (error as? ErrorApi.Validacion)?.campos ?: emptyMap()) }
        if (error is ErrorApi.NoEncontrado) {
            mutable.update { it.copy(formulario = false, seleccionado = null) }; detalleJob?.cancel()
            // Refrescar desde el servidor sin ofrecer reintento del recurso eliminado.
            viewModelScope.launch { try { sincronizar() } catch (_: ErrorApi) { } }
        }
    }
}
