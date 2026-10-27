package pe.upeu.agrosierra

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import pe.upeu.agrosierra.di.*

class AgroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin { androidContext(this@AgroApplication); modules(platformModule, redModule, dataModule, domainModule, presentationModule) }
    }
}
