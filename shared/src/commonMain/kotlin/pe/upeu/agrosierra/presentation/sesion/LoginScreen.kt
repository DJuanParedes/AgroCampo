package pe.upeu.agrosierra.presentation.sesion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pe.upeu.agrosierra.domain.model.ErrorApi
import pe.upeu.agrosierra.presentation.listado.AgroUiState

@Composable fun LoginScreen(s: AgroUiState, login: (String, String) -> Unit) {
    var usuario by rememberSaveable { mutableStateOf("") }
    var contrasena by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("AGROSIERRA", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("El campo,\nsiempre conectado.", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Registre sus labores y continúe trabajando incluso sin señal.")
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(usuario, { usuario = it }, label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !s.cargando)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(contrasena, { contrasena = it }, label = { Text("Contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(), enabled = !s.cargando)
        s.error?.let { Text(if (it == ErrorApi.NoAutorizado) "Usuario o contraseña incorrectos" else it.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp)) }
        Spacer(Modifier.height(20.dp))
        Button(onClick = { login(usuario, contrasena) }, enabled = !s.cargando && usuario.isNotBlank() && contrasena.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (s.cargando) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Ingresar")
        }
        Spacer(Modifier.height(20.dp)); Text("AgroCampo · Gestión de labores de campo", style = MaterialTheme.typography.bodySmall)
    }
}
