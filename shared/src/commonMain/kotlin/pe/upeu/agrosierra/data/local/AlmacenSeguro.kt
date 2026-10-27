package pe.upeu.agrosierra.data.local

import pe.upeu.agrosierra.domain.model.Sesion

interface AlmacenSeguro {
    fun leer(): Sesion?
    fun guardar(sesion: Sesion)
    fun borrar()
    fun borrarSiToken(token: String) { if (leer()?.token == token) borrar() }
    fun guardarSiToken(token: String, sesion: Sesion): Boolean {
        if (leer()?.token != token) return false
        guardar(sesion)
        return true
    }
}
