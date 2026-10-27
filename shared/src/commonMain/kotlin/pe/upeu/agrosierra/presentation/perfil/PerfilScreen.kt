package pe.upeu.agrosierra.presentation.perfil

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.upeu.agrosierra.domain.model.Sesion

@Composable fun PerfilScreen(sesion: Sesion, salir: () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(sesion.nombre, style = MaterialTheme.typography.headlineMedium)
        Text("Usuario: ${sesion.usuario}")
        Text("Rol: ${sesion.rol}")
        HorizontalDivider()
        Text("Privilegios", style = MaterialTheme.typography.titleMedium)
        sesion.privilegios.sorted().forEach { Text("• $it") }
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = salir, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
    }
}
