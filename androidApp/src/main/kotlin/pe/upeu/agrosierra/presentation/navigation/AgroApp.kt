package pe.upeu.agrosierra.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.presentation.listado.*
import pe.upeu.agrosierra.presentation.sesion.LoginScreen
import pe.upeu.agrosierra.presentation.detalle.DetalleScreen
import pe.upeu.agrosierra.presentation.perfil.PerfilScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AgroApp(s: AgroUiState, vm: AgroViewModel) {
    val sesion = s.sesion
    if (sesion == null) { LoginScreen(s, vm::login); return }
    BackHandler(s.seleccionado != null || s.perfil || s.formulario) { vm.volver() }
    Scaffold(topBar = {
        TopAppBar(title = { Text(if (s.perfil) "Mi perfil" else if (s.formulario) "${if (s.editando == null) "Registrar" else "Corregir"} labor" else s.seleccionado?.codigo ?: "AgroCampo") },
            navigationIcon = { if (s.seleccionado != null || s.perfil) IconButton(onClick = vm::volver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            actions = {
                IconButton(onClick = vm::sync, enabled = !s.sincronizando) { Icon(Icons.Default.Sync, "Sincronizar") }
                IconButton(onClick = vm::perfil) { Icon(Icons.Default.AccountCircle, "Perfil") }
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (s.sincronizando) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Sincronizando…", style = MaterialTheme.typography.labelSmall) }
            if (!s.conectado) SuggestionChip(onClick = {}, label = { Text("Sin conexión · puede registrar labores") })
            s.error?.let { error ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(error.message)
                        if (error == ErrorApi.Servidor || error == ErrorApi.TiempoAgotado) TextButton(onClick = vm::sync) { Text("Reintentar") }
                        TextButton(onClick = vm::limpiarMensaje) { Text("Entendido") }
                    }
                }
            }
            s.resumen?.let { Text(it, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 8.dp)) }
            when {
                s.perfil -> PerfilScreen(sesion, vm::salir)
                s.seleccionado != null -> DetalleScreen(s, vm)
                else -> ListadoScreen(s, vm)
            }
        }
    }
}
