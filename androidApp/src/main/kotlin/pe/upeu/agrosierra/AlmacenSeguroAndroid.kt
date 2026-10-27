package pe.upeu.agrosierra

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import pe.upeu.agrosierra.data.local.AlmacenSeguro
import pe.upeu.agrosierra.domain.model.Sesion

class AlmacenSeguroAndroid(context: Context) : AlmacenSeguro {
    private val preferencias = EncryptedSharedPreferences.create(context, "sesion_segura",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    override fun leer(): Sesion? {
        val raw = preferencias.getString("sesion", null) ?: return null
        return try {
            val j = JSONObject(raw); val permisos = j.getJSONArray("privilegios")
            Sesion(j.getString("token"), j.getString("usuario"), j.getString("rol"),
                (0 until permisos.length()).map { permisos.getString(it) }.toSet(),
                j.getString("nombre"), j.getLong("usuarioId"), j.getLong("venceEn"))
        } catch (_: Exception) { borrar(); null }
    }
    override fun guardar(sesion: Sesion) {
        val j = JSONObject().put("token", sesion.token).put("usuario", sesion.usuario)
            .put("rol", sesion.rol).put("privilegios", org.json.JSONArray(sesion.privilegios.toList()))
            .put("nombre", sesion.nombre).put("usuarioId", sesion.usuarioId).put("venceEn", sesion.venceEn)
        check(preferencias.edit().putString("sesion", j.toString()).commit()) { "No se pudo guardar la sesión" }
    }
    override fun borrar() { check(preferencias.edit().clear().commit()) { "No se pudo borrar la sesión" } }
}
