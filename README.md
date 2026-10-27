# AgroCampo

Aplicación Android Kotlin Multiplatform para AgroSierra.

Desarrollo iniciado el 27 de octubre de 2026. El historial conserva las fechas reales.
Backend compañero: AgroAPI.

## Ejecutar en Android

Requisitos: JDK 21, Android SDK 35 y un emulador o dispositivo con Android 8 o posterior.
Abra la carpeta en Android Studio y sincronice Gradle. Cree local.properties con sdk.dir si el IDE no lo genera.

```powershell
.\gradlew.bat :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Inicie AgroAPI con `.\gradlew.bat bootRun` en su repositorio. El emulador usa `http://10.0.2.2:8080/api/v1/`.
Para dispositivo físico cambie API_BASE_URL en androidApp/build.gradle.kts a la IP LAN del equipo.
La dirección está centralizada y HttpClient se crea una sola vez con Koin.
El build release exige configurar una URL HTTPS real antes de distribuirlo; la URL example es un marcador explícito.

Usuarios: tecnico1, jefe1, admin. Contraseña de demostración: Agro2026*.
La aplicación inicia con la base local vacía; no contiene listados ni usuarios incrustados.
El primer login y la primera sincronización requieren el backend disponible.

## Estructura

Código compartido: shared/src/commonMain/kotlin/pe/upeu/agrosierra/.

| Paquete | Responsabilidad |
|---|---|
| domain/model | Lote, Labor, TipoLabor, Sesion y errores tipados sin anotaciones de JSON |
| domain/repository | contratos de datos y sesión |
| domain/usecase | GuardarLaborUseCase y SincronizarUseCase |
| data/remote | ClienteHttp, ApiConfig, AgroApi, MapeoErrores y DTO |
| data/local | AlmacenSeguro y BaseLocal |
| data/repository | repositorios offline-first |
| presentation/listado | ViewModel, StateFlow y UiState |
| di | módulos de red, datos, dominio y presentación |

La UI Compose compartida vive en shared/src/commonMain/kotlin/pe/upeu/agrosierra/presentation/:
sesion, listado, detalle, perfil, navigation y theme. Android contiene AlmacenSeguroAndroid,
BaseLocalAndroid, PlatformModule.android y el monitor de conectividad.
El módulo compartido tiene destinos Android y JVM (para pruebas); la evaluación exige Android.

## Contrato, roles y privilegios

AgroAPI expone /api/v1/auth/login, /usuarios/me, /lotes, /lotes/{id}, /lotes/{id}/labores,
/labores (POST), /labores/{id} (PUT/DELETE) y /lotes/{id}/cerrar-campania (POST).
Los listados usan contenido, pagina, tamanio, totalElementos, totalPaginas y ultima.
AgroApi descarga todas las páginas y convierte cada DTO al dominio.

| Rol | Acciones ofrecidas |
|---|---|
| TECNICO | consultar, crear y corregir labores propias dentro de 24 h |
| JEFE_CAMPO | consultar, crear, corregir todas, eliminar y cerrar campaña |
| ADMINISTRADOR | consultar y ver su privilegio USUARIO_GESTIONAR |

La UI utiliza los privilegios recibidos; el backend vuelve a comprobarlos. Ocultar botones no es seguridad.
No se implementan pantallas de administración porque el contrato evaluado no define esas rutas.

## Sesión y errores

El token se cifra con EncryptedSharedPreferences y una MasterKey respaldada por Android Keystore.
El respaldo de la aplicación está deshabilitado. El cierre de sesión elimina el almacenamiento seguro.
La sesión se restaura si sigue vigente; una sesión vencida vuelve al login.
Authorization se añade centralmente y el registro de red se desactiva en release.

| Error | Conducta |
|---|---|
| 400 | muestra validationErrors bajo el campo y permite corregir el pendiente |
| 401 | borra el token y vuelve al login |
| 403 | informa el motivo y conserva la sesión |
| 404 | informa y refresca el listado, sin reintentar el recurso |
| 409 | muestra el motivo del servidor |
| 500 | mensaje breve y reintento |
| red o timeout | conserva el registro local y comunica la situación |
| 204 | no intenta deserializar cuerpo |

## Persistencia y sincronización

AgroDb.sq conserva las tablas lote y labor exigidas, ampliadas con cola y metadata.
La pantalla lee SQLDelight mediante asFlow; la red solo actualiza la base.
Los registros nuevos reciben IDs negativos. Las correcciones y eliminaciones también se encolan.
La cola conserva propietario y clave idempotente; nunca se envían pendientes de otra identidad.

SincronizarUseCase envía POST/PUT/DELETE pendientes antes de descargar lotes y labores.
Un Mutex evita sincronizaciones simultáneas. La sustitución provisional/definitivo es transaccional.
Idempotency-Key evita duplicar una creación si se pierde la respuesta. Un pendiente rechazado se conserva para corregirlo.
La sincronización es manual y automática cuando vuelve la conectividad mientras la aplicación está activa.
No se promete ejecución en segundo plano con la aplicación cerrada.

## Pruebas y defensa

```powershell
.\gradlew.bat :shared:jvmTest :shared:testDebugUnitTest :androidApp:assembleDebug
```

commonTest utiliza MockEngine y SQLite en memoria mediante expect/actual de prueba.
Cubre conversión DTO, errores de validación, 401/403, registro offline, orden de sincronización,
sustitución sin duplicados, 204, cabecera común, caducidad y filtros locales.
Informes HTML: shared/build/reports/tests/. Consulte docs/TRAZABILIDAD.md para recorrer el código en la defensa.
Las evidencias y la lista de cotejo se guardan en evidencias/.

## SC-A: todos los filtros

Rama `sc-a-paredes`. El padrón combina valle, nombre/código y estado (todas, solo abiertas, solo cerradas).
La combinación se ejecuta en SQLDelight y funciona sin conexión. El composable representa el resultado observado.
Véase docs/SC-A.md y las dos pruebas adicionales de commonTest.
