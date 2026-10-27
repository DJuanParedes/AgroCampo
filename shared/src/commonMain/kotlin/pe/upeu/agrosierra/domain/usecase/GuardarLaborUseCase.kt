package pe.upeu.agrosierra.domain.usecase

import kotlinx.datetime.LocalDate
import pe.upeu.agrosierra.domain.model.*
import pe.upeu.agrosierra.domain.repository.AgroRepository

class GuardarLaborUseCase(private val repositorio: AgroRepository) {
    suspend operator fun invoke(labor: Labor): Labor {
        val errores = linkedMapOf<String, String>()
        try { LocalDate.parse(labor.fecha) } catch (_: IllegalArgumentException) { errores["fecha"] = "Use una fecha válida: AAAA-MM-DD" }
        if (!labor.cantidad.isFinite() || labor.cantidad <= 0) errores["cantidad"] = "Ingrese una cantidad mayor que cero"
        if (labor.observacion.length > 1000) errores["observacion"] = "Máximo 1000 caracteres"
        if (errores.isNotEmpty()) throw ErrorApi.Validacion(errores, "Revise los campos indicados")
        return repositorio.guardar(labor)
    }
}
