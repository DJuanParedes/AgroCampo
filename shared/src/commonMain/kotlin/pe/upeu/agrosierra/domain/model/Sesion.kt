package pe.upeu.agrosierra.domain.model

data class Sesion(val token: String, val usuario: String, val rol: String, val privilegios: Set<String>,
    val nombre: String = usuario, val usuarioId: Long = 0, val venceEn: Long = 0)
