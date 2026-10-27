# Lista de cotejo de entrega

Fecha de verificación: 27 de octubre de 2026. Desarrollo asistido con IA.

| N.º | Criterio del examen | Evidencia o situación |
|---|---|---|
| 1 | Backend con un comando | gradlew bootRun, comprobado en localhost:8080 |
| 2 | Login y credenciales | integración y colección Newman |
| 3 | Rutas protegidas sin token | integración y respuesta real 401 |
| 4 | Token alterado/vencido | pruebas de integración JWT |
| 5 | Sin privilegio produce 403 | Newman y respuestas-reales.json |
| 6 | Paginación | integración, colección y AgroApi descarga todas las páginas |
| 7 | Validación por campo | 400 con validationErrors.cantidad |
| 8 | Token seguro | AlmacenSeguroAndroid, MasterKey y preferencias cifradas |
| 9 | Cabecera centralizada | ClienteHttp.defaultRequest y commonTest |
| 10 | Cierre de sesión | SesionRepositoryImpl.salir y almacenamiento seguro |
| 11 | Acciones por privilegios | detalle/perfil y control del servidor |
| 12 | Pantalla observa base local | BaseLocal.asFlow y AgroViewModel |
| 13 | Modo avión | 03-detalle-modo-avion.png |
| 14 | Registro pendiente con ID provisional | 04-registro-pendiente.png, ID -1 |
| 15 | Sin duplicar ni perder al sincronizar | 05-registro-sincronizado.png; servidor: una coincidencia, ID 21 |
| 16 | commonTest | 25 pruebas en JVM y 25 en Android en la versión corregida 1.1 |
| 17 | Historial distribuido en varias fechas | NO ACREDITADO: proyectos creados hoy, fechas reales |
| 18 | Integración desde develop | pull requests y merges, salvo el commit documental inicial |
| 19 | Convención y etiqueta | mensajes en español; v1.0-unidad2 original conservada y v1.1-unidad2 para las correcciones |
| 20 | Tres commits durante el bloque de examen | SC-A tiene tres commits reales; el contexto de examen supervisado debe acreditarlo el estudiante |

La calificación de la defensa depende de que el estudiante explique el código en ejecución.
SC-A corresponde al pedido del usuario de incluir todos los filtros; el docente determina la solicitud evaluada.
Los repositorios son privados: el docente necesita acceso o una visibilidad que autorice el propietario.
