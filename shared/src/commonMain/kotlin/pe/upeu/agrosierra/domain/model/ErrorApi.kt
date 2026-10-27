package pe.upeu.agrosierra.domain.model

sealed class ErrorApi(override val message: String) : Exception(message) {
    data class Validacion(val campos: Map<String, String>, val detalle: String) : ErrorApi(detalle)
    data object NoAutorizado : ErrorApi("Su sesión ha vencido. Inicie sesión nuevamente.")
    data class SinPrivilegio(val detalle: String) : ErrorApi(detalle)
    data class NoEncontrado(val detalle: String) : ErrorApi(detalle)
    data class Conflicto(val detalle: String) : ErrorApi(detalle)
    data object Servidor : ErrorApi("El servidor no pudo completar la operación. Intente nuevamente.")
    data object SinConexion : ErrorApi("No hay conexión. Sus registros permanecen guardados en el dispositivo.")
    data object TiempoAgotado : ErrorApi("El servidor tardó demasiado en responder.")
}
