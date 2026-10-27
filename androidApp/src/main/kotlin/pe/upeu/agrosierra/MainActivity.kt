package pe.upeu.agrosierra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.core.context.GlobalContext
import pe.upeu.agrosierra.presentation.listado.AgroViewModel
import pe.upeu.agrosierra.presentation.navigation.AgroApp
import pe.upeu.agrosierra.presentation.theme.AgroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AgroViewModel = viewModel { GlobalContext.get().get<AgroViewModel>() }
            val estado by vm.estado.collectAsStateWithLifecycle()
            AgroTheme { AgroApp(estado, vm) }
        }
    }
}
