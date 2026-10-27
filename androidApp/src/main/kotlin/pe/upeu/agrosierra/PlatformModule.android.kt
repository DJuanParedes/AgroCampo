package pe.upeu.agrosierra

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import pe.upeu.agrosierra.data.local.AlmacenSeguro
import pe.upeu.agrosierra.data.remote.ApiConfig
import pe.upeu.agrosierra.domain.repository.Conectividad

val platformModule = module {
    single<AlmacenSeguro> { AlmacenSeguroAndroid(androidContext()) }
    single<SqlDriver> { crearDriverAndroid(androidContext()) }
    single<HttpClientEngine> { OkHttp.create() }
    single { ApiConfig(BuildConfig.API_BASE_URL, BuildConfig.DEBUG) }
    single<Conectividad> { ConectividadAndroid(androidContext()) }
}
