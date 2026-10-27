package pe.upeu.agrosierra

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import pe.upeu.agrosierra.db.AgroDb

actual fun driverEnMemoria(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AgroDb.Schema.create(it) }
