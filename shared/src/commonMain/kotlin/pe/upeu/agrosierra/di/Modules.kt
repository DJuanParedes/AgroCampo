package pe.upeu.agrosierra.di

import org.koin.dsl.module
import pe.upeu.agrosierra.data.local.BaseLocal
import pe.upeu.agrosierra.data.remote.*
import pe.upeu.agrosierra.data.repository.*
import pe.upeu.agrosierra.domain.repository.*
import pe.upeu.agrosierra.domain.usecase.*
import pe.upeu.agrosierra.presentation.listado.AgroViewModel

val redModule = module {
    single { crearClienteHttp(get(), get(), get()) }
    single { AgroApi(get(), get()) }
}
val dataModule = module {
    single { BaseLocal(get()) }
    single<SesionRepository> { SesionRepositoryImpl(get(), get()) }
    single<AgroRepository> { AgroRepositoryImpl(get(), get(), get()) }
}
val domainModule = module {
    factory { SincronizarUseCase(get()) }
    factory { GuardarLaborUseCase(get()) }
}
val presentationModule = module {
    factory { AgroViewModel(get(), get(), get(), get(), get()) }
}
