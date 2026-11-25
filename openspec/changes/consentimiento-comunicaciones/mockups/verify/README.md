# Verificación de C-17 (`consentimiento-comunicaciones`), 2026-11-25

- Entorno: backend real (`bootRun` con el perfil `dev`, PostgreSQL en Docker, V11 aplicada) y frontend con Vite en `http://localhost:5173`; cuentas de prueba ficticias (`paciente.uno@example.com` y `recepcion`). Capturas con Playwright a 1440 × 900 y 390 × 844.
- Mockups de referencia: los aprobados en el PR #142 (`../previews/`), Revision 5 del design system.
- Resultado: **sin blockers ni majors**. Una diferencia menor encontrada y corregida durante la verificación (abajo).

## Pantallas revisadas

| Pantalla | Mockup | Capturas | Resultado |
|---|---|---|---|
| Registro con la casilla de mensajes | `registro.html` | `registro-1440.png`, `registro-390.png` | Igual: casilla sin marcar antes de "Crear cuenta" |
| Mi cuenta sin consentimiento | `mi-cuenta-sin-consentimiento.html` | `mi-cuenta-sin-consentimiento-1440.png`, `-390.png` | Igual: "Tus datos" y "Mensajes del consultorio" antes de "Mi cobertura" y "Mis presupuestos" |
| Mi cuenta con consentimiento | `mi-cuenta-con-consentimiento.html` | `mi-cuenta-con-consentimiento-1440.png`, `-390.png` | Igual, con el aviso de éxito y la fecha real del cambio |
| Confirmación para dejar de recibir | `mi-cuenta-con-consentimiento.html` | `mi-cuenta-revocar-1440.png`, `-390.png` | Igual: botón de peligro y "Volver" |
| Después de revocar | `mi-cuenta-con-consentimiento.html` | `mi-cuenta-revocado-1440.png` | Igual |
| Recepción: rechazo `SIN_CONSENTIMIENTO` | `recepcion-sin-consentimiento.html` | `recepcion-sin-consentimiento-1440.png`, `-390.png` | Igual; el foco queda en "Registrar consentimiento" destacado |
| Recepción: diálogo del medio | `recepcion-registrar.html` | `recepcion-medio-1440.png` (con el error de falta de medio), `recepcion-medio-390.png` | Igual |
| Recepción: después de registrar | `recepcion-registrar.html` | `recepcion-registrado-1440.png`, `-390.png` | Igual (ver la diferencia corregida) |
| Recepción: línea en "Nuevo turno" | `recepcion-registrar.html` (nota) | `recepcion-nuevo-turno-1440.png` | Igual: la línea va debajo del paciente elegido |

## Diferencias

- **Menor, corregida**: "Tus datos" no tenía separación entre los datos y la ayuda. Se agregó la clase `pila` al cuerpo de la sección.
- **Menor, corregida**: después de registrar el consentimiento, el aviso rojo de `SIN_CONSENTIMIENTO` seguía visible en "Anotar paciente". La línea avisa el cambio (`alCambiar`) y el diálogo lo borra; se sumó la comprobación al test "Registrar y volver a guardar".

## Comprobaciones

- Sin desplazamiento horizontal a 390 px (ancho del documento igual al de la vista).
- En la consola solo hay respuestas esperadas: `401` de un ingreso con una contraseña vieja y `409` del rechazo `SIN_CONSENTIMIENTO`.
- Recorrido completo (tarea 7.3): Paciente Uno aceptó los mensajes desde "Mi cuenta" y reservó (`Envío simulado: canal=EMAIL notificacion=4 resultado=OK`); después revocó y otra reserva no creó ninguna notificación. La recepción registró por teléfono el consentimiento de Paciente Dos (historial con `RECEPCION`, `TELEFONO` y `recepcion`) y lo anotó en la lista de espera (inscripción `ACTIVA`).

## No revisado en el navegador

- El enlace "Aceptar mensajes en Mi cuenta" del aviso de la lista de espera del paciente: en la base de desarrollo no hubo un día sin horarios para mostrar "Anotarme en la lista de espera". Queda cubierto por el test "Paciente sin consentimiento en la lista de espera".
- No se probó con lector de pantalla.

## Fuera de C-17 (para avisar)

- El diálogo "Anotar paciente" de la lista de espera (C-15) queda abierto al pasar a otra página del menú, porque se agrega al documento y no se cierra al navegar.
