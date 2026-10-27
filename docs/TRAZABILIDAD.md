# Trazabilidad y defensa

Desarrollo asistido con IA, iniciado el 27 de octubre de 2026, con fechas reales.
Los repositorios nuevos no acreditan el historial de semanas previas que pide la rúbrica.

| Rama | Aporte |
|---|---|
| feature/sesion-segura | cliente único, almacenamiento seguro, arquitectura y pantallas |
| feature/offline-pruebas | pruebas con MockEngine y SQLite en memoria; reintentos idempotentes |
| feature/documentacion-evidencias | ejecución, defensa, capturas y documentación |
| sc-a-paredes | filtros combinados offline por valle, búsqueda y campañas abiertas |
| fix/ci-android | SDK vigente en CI y contraste de barras del sistema |
| feature/evidencias-finales | pantallas en commonMain, cierre de sesión y cotejo final |

Cada rama se integra mediante pull request hacia develop, con descripción y verificaciones.
main recibe la integración desde develop. La etiqueta v1.0-unidad2 identifica la versión evaluable.
Las capturas se obtienen del emulador real; las pruebas indican motor, fecha y resultado.

## Recorrido para la defensa

1. LoginScreen → AgroViewModel.login → SesionRepositoryImpl → AgroApi → ClienteHttp.
2. El backend valida BCrypt y firma JWT. /usuarios/me completa el perfil.
3. AlmacenSeguroAndroid usa EncryptedSharedPreferences y MasterKey respaldada por Keystore.
4. ClienteHttp añade Authorization en defaultRequest; MapeoErrores borra la sesión en 401 y la conserva en 403.
5. ListadoScreen observa AgroUiState. AgroRepositoryImpl observa BaseLocal.lotes mediante SQLDelight.asFlow.
6. GuardarLaborUseCase valida formato; el repositorio guarda con ID negativo y encola POST.
7. SincronizarUseCase ejecuta primero la cola y después descarga todas las páginas.
8. BaseLocal.sustituir elimina el ID provisional e inserta el definitivo en una transacción.
9. RN-01..04 se hacen cumplir en AgroAPI/servicio/AgroService.kt.
10. commonTest usa MockEngine y un driver SQLite en memoria real, no una lista falsa como base de datos.

La defensa individual requiere comprender y mostrar este recorrido en ejecución.
