package pe.upeu.agrosierra.domain.usecase

import kotlinx.datetime.Instant
import pe.upeu.agrosierra.domain.model.Labor
import pe.upeu.agrosierra.domain.model.Sesion

fun puedeEditarLabor(labor: Labor, sesion: Sesion, ahora: Instant): Boolean {
    if ("LABOR_EDITAR" !in sesion.privilegios) return false
    if (labor.pendiente && labor.propietarioPendiente != null && labor.propietarioPendiente != sesion.usuarioId) return false
    if ("LABOR_EDITAR_TODAS" in sesion.privilegios) return true
    if (labor.responsableId != sesion.usuarioId) return false
    if (labor.id < 0) return true
    return try {
        val transcurrido = ahora.epochSeconds - Instant.parse(labor.creadaEn).epochSeconds
        transcurrido in 0..86400
    } catch (_: IllegalArgumentException) { false }
}
