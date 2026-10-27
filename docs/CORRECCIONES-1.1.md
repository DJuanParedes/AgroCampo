# Correcciones verificadas de la auditoría

Versión 1.1, 27 de octubre de 2026. Se conserva la versión original y sus fechas.

| Deficiencia | Solución | Regresión |
|---|---|---|
| Un rechazo detiene toda la cola | Captura por fila, error persistente y continuación antes de descargar | Dos POST, uno rechazado, otro enviado y GET ejecutados |
| Credenciales mezcladas al cambiar cuenta | Token fijo y guardas antes/después de cada petición | No se envía el segundo POST; las dos filas siguen pendientes |
| 401 antiguo borra login nuevo | Borrado condicional por token y respuesta obsoleta cancelada | El nuevo token permanece |
| Perfil antiguo restaura sesión cerrada | Escritura condicional y cancelación de trabajos | El almacén sigue vacío |
| Pendiente sin forma de recuperación | Descartar cambio local solo por su propietario | No requiere privilegio de eliminación y no envía DELETE |
| Reintentar hace otra operación | Acción tipada para cierre o envío | Se repite cierre; el envío no crea otro registro |
| Horas locales ambiguas | Instantes UTC desde el servicio hasta el cálculo del plazo | Mismo resultado con Z y -05:00 |
| Falta cobertura adversa | MockEngine, SQLite en memoria y ViewModel con reloj de pruebas | 24 pruebas compartidas en dos destinos |

La migración SQLite 1 a 2 crea conflicto y conserva registros y cola anteriores.
El APK 1.1 se instala como actualización de la app 1.0, sin borrar su base.
Las filas fallidas muestran el motivo del servidor en el detalle y permiten corregir o descartar el cambio local.

La captura 09-api-403-real.png proviene del navegador mostrando una petición real del inspector local a AgroAPI.
El token se mantiene oculto. No es una respuesta simulada ni una imagen generada.

Los puntos de historial previo, asignación de SC y defensa individual siguen sujetos a la evaluación docente.
