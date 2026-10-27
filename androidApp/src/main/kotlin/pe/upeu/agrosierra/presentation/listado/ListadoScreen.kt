package pe.upeu.agrosierra.presentation.listado

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun ListadoScreen(s: AgroUiState, vm: AgroViewModel) {
    Text("Padrón de lotes", style = MaterialTheme.typography.headlineMedium)
    Text("Última sincronización: ${s.ultimaSync ?: "Todavía no se sincronizó"}", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(s.busqueda, { vm.filtrar(busqueda = it) }, label = { Text("Buscar por nombre o código") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(s.valle, { vm.filtrar(valle = it) }, label = { Text("Valle (vacío para todos)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
    if (s.lotes.isEmpty()) {
        Text(if (s.sincronizando) "Cargando el padrón…" else "No hay lotes para estos filtros. Sincronice para obtener el padrón.", modifier = Modifier.padding(vertical = 24.dp))
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        items(s.lotes, key = { it.id }) { lote ->
            Card(onClick = { vm.abrir(lote) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(lote.codigo, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(lote.nombre, style = MaterialTheme.typography.titleLarge)
                    Text("${lote.valle} · ${lote.cultivo} · ${lote.hectareas} ha")
                    Text(if (lote.campaniaCerrada) "Campaña cerrada" else "Campaña abierta", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
