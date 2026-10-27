package pe.upeu.agrosierra

import android.content.Context
import android.net.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import pe.upeu.agrosierra.domain.repository.Conectividad

class ConectividadAndroid(context: Context) : Conectividad {
    private val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    override val disponible = callbackFlow {
        fun conectado(): Boolean = manager.activeNetwork?.let { manager.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } == true
        trySend(conectado())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(conectado()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(conectado()) }
        }
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }
}
