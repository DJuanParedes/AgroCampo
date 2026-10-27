package pe.upeu.agrosierra.presentation.detalle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import pe.upeu.agrosierra.domain.model.*
import pe.upeu.agrosierra.presentation.listado.*

@Composable fun DetalleScreen(s: AgroUiState, vm: AgroViewModel) {
    if (s.formulario) { FormularioLabor(s, vm); return }
    val lote = s.seleccionado ?: return
    val permisos = s.sesion?.privilegios ?: emptySet()
    var confirmarCierre by remember { mutableStateOf(false) }
    var borrar by remember { mutableStateOf<Labor?>(null) }
    if (confirmarCierre) AlertDialog(onDismissRequest = { confirmarCierre = false }, title = { Text("¿Cerrar campaña?") },
        text = { Text("Después no se podrán registrar ni corregir labores en este lote.") },
        confirmButton = { TextButton(onClick = { confirmarCierre = false; vm.cerrar() }) { Text("Cerrar campaña") } },
        dismissButton = { TextButton(onClick = { confirmarCierre = false }) { Text("Cancelar") } })
    borrar?.let { labor -> AlertDialog(onDismissRequest = { borrar = null }, title = { Text("¿Eliminar labor?") },
        text = { Text("La eliminación se enviará al sincronizar.") },
        confirmButton = { TextButton(onClick = { borrar = null; vm.eliminar(labor) }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { borrar = null }) { Text("Cancelar") } }) }
    Text(lote.nombre, style = MaterialTheme.typography.headlineMedium)
    Text("${lote.cultivo} · ${lote.valle} · ${lote.hectareas} ha")
    Text("Dosis máxima: ${lote.dosisMaximaPorHectarea} por ha")
    Text(if (lote.campaniaCerrada) "Campaña cerrada" else "Campaña abierta", color = MaterialTheme.colorScheme.primary)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if ("LABOR_CREAR" in permisos && !lote.campaniaCerrada) Button(onClick = { vm.formulario() }) { Text("Registrar labor") }
        if ("CAMPANIA_CERRAR" in permisos && !lote.campaniaCerrada) OutlinedButton(onClick = { confirmarCierre = true }, enabled = !s.cargando) { Text("Cerrar campaña") }
    }
    if (s.cargando) CircularProgressIndicator()
    if (!s.cargando && s.labores.isEmpty()) Text("Este lote todavía no tiene labores.", modifier = Modifier.padding(vertical = 24.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        items(s.labores, key = { it.id }) { labor ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(labor.tipo.name, style = MaterialTheme.typography.titleMedium)
                    Text("${labor.fecha} · ${labor.cantidad}")
                    Text("Responsable: ${labor.responsable}")
                    if (labor.observacion.isNotBlank()) Text(labor.observacion)
                    if (labor.pendiente) Text("Pendiente de sincronizar · ID ${labor.id}", color = MaterialTheme.colorScheme.tertiary)
                    Row {
                        if (labor.id in s.editables && !lote.campaniaCerrada) TextButton(onClick = { vm.formulario(labor) }) { Text("Corregir") }
                        if ("LABOR_ELIMINAR" in permisos) TextButton(onClick = { borrar = labor }) { Text("Eliminar") }
                    }
                }
            }
        }
    }
}

@Composable private fun FormularioLabor(s: AgroUiState, vm: AgroViewModel) {
    val labor = s.editando
    var tipo by rememberSaveable(labor?.id) { mutableStateOf(labor?.tipo ?: TipoLabor.RIEGO) }
    var fecha by rememberSaveable(labor?.id) { mutableStateOf(labor?.fecha ?: Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()) }
    var cantidad by rememberSaveable(labor?.id) { mutableStateOf(labor?.cantidad?.toString() ?: "") }
    var observacion by rememberSaveable(labor?.id) { mutableStateOf(labor?.observacion ?: "") }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(s.seleccionado?.nombre ?: "", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TipoLabor.entries.forEach { opcion -> FilterChip(tipo == opcion, { tipo = opcion }, label = { Text(opcion.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
        }
        s.erroresCampo["tipo"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(fecha, { fecha = it }, label = { Text("Fecha (AAAA-MM-DD)") }, singleLine = true,
            isError = "fecha" in s.erroresCampo, supportingText = { Text(s.erroresCampo["fecha"] ?: "Fecha en que se realizó la labor") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(cantidad, { cantidad = it }, label = { Text("Cantidad aplicada") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = "cantidad" in s.erroresCampo, supportingText = { Text(s.erroresCampo["cantidad"] ?: "Cantidad mayor que cero") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(observacion, { observacion = it }, label = { Text("Observación") }, minLines = 3,
            isError = "observacion" in s.erroresCampo, supportingText = { Text(s.erroresCampo["observacion"] ?: "Máximo 1000 caracteres") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.guardar(tipo, fecha, cantidad, observacion) }, enabled = !s.guardando, modifier = Modifier.fillMaxWidth()) {
            if (s.guardando) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Guardar labor")
        }
        Text("Sin conexión, la labor queda pendiente y se envía al recuperar la señal.", style = MaterialTheme.typography.bodySmall)
    }
}
