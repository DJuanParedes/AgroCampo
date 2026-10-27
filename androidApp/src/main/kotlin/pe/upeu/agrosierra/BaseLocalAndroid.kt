package pe.upeu.agrosierra

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import pe.upeu.agrosierra.db.AgroDb

fun crearDriverAndroid(context: Context) = AndroidSqliteDriver(AgroDb.Schema, context, "agrosierra.db")
