package pe.upeu.agrosierra.domain.usecase

import pe.upeu.agrosierra.domain.repository.AgroRepository

class SincronizarUseCase(private val repositorio: AgroRepository) {
    suspend operator fun invoke() = repositorio.sincronizar()
}
