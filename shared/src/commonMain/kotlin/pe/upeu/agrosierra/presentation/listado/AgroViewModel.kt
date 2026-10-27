package pe.upeu.agrosierra.presentation.listado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.*
import pe.upeu.agrosierra.domain.model.*
import pe.upeu.agrosierra.domain.repository.*
import pe.upeu.agrosierra.domain.usecase.*

sealed interface AccionReintento {
    data object Sincronizar : AccionReintento
    data class CerrarCampania(val loteId: Long) : AccionReintento
    data class EnviarLabor(val idLocal: Long) : AccionReintento
}

data class AgroUiState(
    val sesion: Sesion? = null, val cargando: Boolean = false, val sincronizando: Boolean = false,
    val conectado: Boolean = true, val lotes: List<Lote> = emptyList(), val labores: List<Labor> = emptyList(),
    val seleccionado: Lote? = null, val perfil: Boolean = false, val valle: String = "", val busqueda: String = "",
    val ultimaSync: String? = null, val resumen: String? = null, val error: ErrorApi? = null,
    val editando: Labor? = null, val formulario: Boolean = false, val guardando: Boolean = false,
    val erroresCampo: Map<String, String> = emptyMap(), val editables: Set<Long> = emptySet(),
    val estadoCampania: Boolean? = null, val reintento: AccionReintento? = null
)

class AgroViewModel(private val sesiones: SesionRepository, private val repo: AgroRepository,
    private val sincronizar: SincronizarUseCase, private val guardarLabor: GuardarLaborUseCase,
    conectividad: Conectividad) : ViewModel() {
    private val mutable = MutableStateFlow(AgroUiState(sesion = sesiones.restaurar(), ultimaSync = repo.ultimaSincronizacion()))
    val estado = mutable.asStateFlow()
    private var listaJob: Job? = null
    private var detalleJob: Job? = null
    private var loginJob: Job? = null
    private var trabajoSesion = SupervisorJob(viewModelScope.coroutineContext[Job])
    private var scopeSesion = CoroutineScope(viewModelScope.coroutineContext + trabajoSesion)
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
            repo.observarLotes(mutable.value.valle, mutable.value.busqueda, mutable.value.estadoCampania).collect { lotes ->
                mutable.update { it.copy(lotes = lotes, seleccionado = it.seleccionado?.let { previo -> lotes.find { l -> l.id == previo.id } ?: previo }) }
            }
        }
    }
    fun filtrar(valle: String = mutable.value.valle, busqueda: String = mutable.value.busqueda) {
        mutable.update { it.copy(valle = valle, busqueda = busqueda) }; observarLista()
    }
    fun filtrarEstado(cerrada: Boolean?) {
        mutable.update { it.copy(estadoCampania = cerrada) }; observarLista()
    }
    fun login(usuario: String, contrasena: String) {
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
        mutable.update { it.copy(cargando = true, error = null) }
        try {
            val sesion = sesiones.login(usuario.trim(), contrasena)
            mutable.update { it.copy(sesion = sesion, cargando = false) }; sync()
        } catch (e: Exception) { fallo(e); mutable.update { it.copy(cargando = false) } }
        }
    }
    fun sync() {
        if (mutable.value.sincronizando || mutable.value.sesion == null) return
        mutable.update { it.copy(sincronizando = true, error = null, reintento = null) }
        scopeSesion.launch {
            try {
                val r = sincronizar()
                aplicarResultado(r)
            } catch (e: Exception) { fallo(e) }
            finally { if (isActive) mutable.update { it.copy(sincronizando = false) } }
        }
    }
    fun abrir(lote: Lote) {
        mutable.update { it.copy(seleccionado = lote, perfil = false, labores = emptyList(), cargando = true, error = null) }
        detalleJob?.cancel()
        detalleJob = scopeSesion.launch {
            repo.observarLabores(lote.id).collect { filas ->
                val sesion = mutable.value.sesion
                val editables = filas.filter { labor ->
                    sesion != null && puedeEditarLabor(labor, sesion, Clock.System.now())
                }.map { it.id }.toSet()
                mutable.update { it.copy(labores = filas, cargando = false, editables = editables) }
            }
        }
    }
    fun volver() {
        if (mutable.value.formulario) { mutable.update { it.copy(formulario = false, error = null, erroresCampo = emptyMap()) }; return }
        detalleJob?.cancel(); mutable.update { it.copy(seleccionado = null, perfil = false, error = null) }
    }
    fun perfil() {
        detalleJob?.cancel()
        mutable.update { it.copy(perfil = true, seleccionado = null, formulario = false, error = null) }
        scopeSesion.launch {
            try {
                val perfil = sesiones.perfil()
                currentCoroutineContext().ensureActive()
                mutable.update { it.copy(sesion = perfil) }
            }
            catch (e: Exception) { if (e != ErrorApi.SinConexion && e != ErrorApi.TiempoAgotado) fallo(e) }
        }
    }
    fun formulario(labor: Labor? = null) { mutable.update { it.copy(formulario = true, editando = labor, error = null, erroresCampo = emptyMap()) } }
    fun guardar(tipo: TipoLabor, fecha: String, cantidad: String, observacion: String) = scopeSesion.launch {
        val lote = mutable.value.seleccionado ?: return@launch
        var idGuardado: Long? = null
        val numero = cantidad.toDoubleOrNull()
        if (numero == null) { mutable.update { it.copy(erroresCampo = mapOf("cantidad" to "Ingrese una cantidad numérica")) }; return@launch }
        mutable.update { it.copy(guardando = true, erroresCampo = emptyMap(), error = null) }
        try {
            val original = mutable.value.editando
            val guardada = guardarLabor(original?.copy(tipo = tipo, fecha = fecha, cantidad = numero, observacion = observacion)
                ?: Labor(0, lote.id, tipo, fecha, "", numero, observacion, true))
            mutable.update { it.copy(editando = guardada) }
            idGuardado = guardada.id
            var resultado: ResumenSync? = null
            try {
                resultado = sincronizar()
                resultado.conflictos.find { it.idLocal == guardada.id }?.let { throw it.error }
            }
            catch (e: ErrorApi) { if (e != ErrorApi.SinConexion && e != ErrorApi.TiempoAgotado) throw e }
            mutable.update { it.copy(formulario = false, ultimaSync = repo.ultimaSincronizacion(), resumen = resultado?.let(::describir) ?: "Guardado sin conexión · pendiente de envío") }
        } catch (e: Exception) { fallo(e, idGuardado?.let(AccionReintento::EnviarLabor) ?: AccionReintento.Sincronizar) }
        finally { if (isActive) mutable.update { it.copy(guardando = false) } }
    }
    fun eliminar(labor: Labor) = scopeSesion.launch {
        try { repo.eliminar(labor); mutable.update { it.copy(resumen = "Eliminación guardada") } }
        catch (e: Exception) { fallo(e) }
    }
    fun descartar(labor: Labor) = scopeSesion.launch {
        try {
            repo.descartar(labor.id)
            mutable.update { it.copy(error = null, resumen = "Cambio local descartado; no se eliminó información del servidor") }
            if (mutable.value.conectado) sync()
        } catch (e: Exception) { fallo(e) }
    }
    fun reintentar() {
        when (val accion = mutable.value.reintento) {
            is AccionReintento.CerrarCampania -> cerrar(accion.loteId)
            is AccionReintento.EnviarLabor -> reenviar(accion.idLocal)
            else -> sync()
        }
    }
    private fun reenviar(id: Long) = scopeSesion.launch {
        mutable.update { it.copy(guardando = true, error = null) }
        try {
            val r = sincronizar()
            r.conflictos.find { it.idLocal == id }?.let { throw it.error }
            aplicarResultado(r)
            mutable.update { it.copy(formulario = false, editando = null, reintento = null) }
        } catch (e: Exception) { fallo(e, AccionReintento.EnviarLabor(id)) }
        finally { if (isActive) mutable.update { it.copy(guardando = false) } }
    }
    fun cerrar(id: Long? = mutable.value.seleccionado?.id) = scopeSesion.launch {
        val loteId = id ?: return@launch
        mutable.update { it.copy(cargando = true, error = null) }
        try {
            repo.cerrar(loteId)
            mutable.update { it.copy(seleccionado = repo.lote(loteId), resumen = "Campaña cerrada", reintento = null) }
        } catch (e: Exception) { fallo(e, AccionReintento.CerrarCampania(loteId)) }
        finally { if (isActive) mutable.update { it.copy(cargando = false) } }
    }
    fun salir() {
        loginJob?.cancel(); trabajoSesion.cancel(); sesiones.salir(); detalleJob?.cancel()
        trabajoSesion = SupervisorJob(viewModelScope.coroutineContext[Job])
        scopeSesion = CoroutineScope(viewModelScope.coroutineContext + trabajoSesion)
        mutable.value = AgroUiState(); observarLista()
    }
    fun limpiarMensaje() { mutable.update { it.copy(error = null, resumen = null, reintento = null) } }
    private fun describir(r: ResumenSync) = "${r.enviados} enviados · ${r.recibidos} recibidos" +
        if (r.conflictos.isEmpty()) "" else " · ${r.conflictos.size} cambios requieren atención"
    private fun aplicarResultado(r: ResumenSync) {
        mutable.update {
            val enviado = it.editando?.id in r.idsEnviados
            it.copy(ultimaSync = r.fecha, resumen = describir(r),
                formulario = if (enviado) false else it.formulario,
                editando = if (enviado) null else it.editando,
                erroresCampo = if (enviado) emptyMap() else it.erroresCampo)
        }
    }
    private suspend fun fallo(e: Exception, accion: AccionReintento = AccionReintento.Sincronizar) {
        currentCoroutineContext().ensureActive()
        if (e is CancellationException) throw e
        val error = e as? ErrorApi ?: ErrorApi.Servidor
        if (error == ErrorApi.NoAutorizado && mutable.value.sesion != null) { salir(); return }
        mutable.update { it.copy(error = error, erroresCampo = (error as? ErrorApi.Validacion)?.campos ?: emptyMap(),
            reintento = if (error == ErrorApi.Servidor || error == ErrorApi.TiempoAgotado) accion else null) }
        if (error is ErrorApi.NoEncontrado) {
            mutable.update { it.copy(formulario = false, seleccionado = null) }; detalleJob?.cancel()
            // Refrescar desde el servidor sin ofrecer reintento del recurso eliminado.
            scopeSesion.launch { try { sincronizar() } catch (_: ErrorApi) { } }
        }
    }
}
