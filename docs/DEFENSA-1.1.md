# Preparación de la defensa: versión 1.1

## Recorrido de una labor

DetalleScreen entrega el evento a AgroViewModel. GuardarLaborUseCase valida fecha y cantidad.
AgroRepositoryImpl guarda en SQLite y encola la operación. SincronizarUseCase primero envía la cola
y después descarga páginas del API. AgroApi convierte DTO a dominio. La pantalla observa SQLDelight mediante asFlow.

## Seguridad que debes poder mostrar

- El backend verifica la firma, el emisor y la expiración del JWT en JwtService y JwtAuthenticationFilter.
- SecurityConfig protege las rutas; hasAuthority verifica privilegios, no nombres de roles.
- AgroService aplica RN-01 a RN-04, incluso si la petición se hace fuera de la aplicación.
- El token de Android está en AlmacenSeguroAndroid, con EncryptedSharedPreferences y MasterKey de Keystore.
- AutorizacionAgro, en ClienteHttp, añade la cabecera centralmente. Cada operación lleva su token fijo.
- Un 401 de la sesión vigente borra su token. Un 403 mantiene la sesión. Un 401 antiguo no puede borrar un login nuevo.

## Offline y recuperación

- Un alta sin red recibe ID negativo y pendiente=true. El ID positivo lo asigna el servidor.
- BaseLocal.sustituir cambia la fila provisional por la definitiva en una transacción.
- Idempotency-Key permite repetir un POST cuya respuesta se perdió sin crear otra fila.
- Un 400/403/404/409 queda asociado al pendiente; las otras operaciones y la descarga continúan.
- Descartar cambio local solo elimina la intención local de su propietario. No llama a DELETE en el API.
- Cerrar sesión cancela sus trabajos. Las respuestas antiguas se invalidan antes de tocar el estado o la base.
- Reintentar conserva la operación: cerrar campaña o enviar el pendiente sin volver a crearlo.

## Fechas y pruebas

creadaEn llega con Z como instante UTC. Una hora escrita con -05:00 puede representar el mismo instante.
El plazo de 24 horas se calcula sobre instantes, evitando depender de la zona del servidor o del teléfono.

commonTest sustituye la red con MockEngine y utiliza SQLite real en memoria. Las regresiones cubren cola mixta,
cambio de cuenta, 401 tardío, perfil obsoleto, descarte, CRUD offline, respuesta perdida, migración y reintentos.
AgroAPI tiene pruebas HTTP con Spring y base relacional para JWT, privilegios, CRUD, reglas y UTC.

## Demostración en orden

1. Iniciar el backend y mostrar login válido e inválido.
2. Entrar como técnico, consultar lotes y activar modo avión.
3. Registrar, observar el ID negativo, reconectar y verificar una sola fila en el servidor.
4. Provocar exceso de dosis y mostrar el mensaje bajo Cantidad; corregir o descartar el pendiente.
5. Entrar como administrador y demostrar el 403 en la herramienta de API.
6. Entrar como jefe, corregir/eliminar una labor y probar el cierre con y sin cosecha.
7. Mostrar las pruebas y recorrer la rama sc-a-paredes y sus tres commits.

La defensa requiere explicar el código propio en ejecución. Esta guía no acredita por sí misma esos puntos
ni sustituye la confirmación docente de SC-A o el historial previo de la unidad.
