# SC-A: filtros combinados sin conexión

Rama: sc-a-paredes, iniciada desde develop. El usuario solicitó incluir todos los filtros.

1. La consulta observarLotes combina valle, nombre/código y estado de campaña.
2. El repositorio transmite los parámetros a SQLDelight. El composable no filtra listas.
3. AgroViewModel cambia la suscripción cuando cambia un filtro y mantiene los otros.
4. ListadoScreen ofrece todas las campañas, solo abiertas y solo cerradas, además de búsqueda y valle.
5. Dos pruebas adicionales verifican combinación y actualización de la consulta local sin utilizar la red.

Verificación manual: activar modo avión, seleccionar Solo campañas abiertas, escribir el valle completo y un código.
El resultado debe contener únicamente las filas que cumplen las tres condiciones.
La búsqueda vacía y el valle vacío eliminan esas restricciones. Todas las campañas elimina el filtro de estado.

El backend ya admite campaniaCerrada en GET /lotes; esta solicitud no necesita cambios en su contrato.
Los tres commits corresponden a consulta/contrato, interfaz funcional y pruebas/documentación, con fechas reales.
No se afirma que estos commits hayan ocurrido dentro de un bloque de examen supervisado.
