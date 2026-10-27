package pe.upeu.agrosierra.domain.model

data class Labor(val id: Long, val loteId: Long, val tipo: TipoLabor, val fecha: String,
    val responsable: String, val cantidad: Double, val observacion: String, val pendiente: Boolean,
    val responsableId: Long = 0, val creadaEn: String = "")
