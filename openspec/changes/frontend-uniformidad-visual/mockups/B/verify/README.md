# Verificación visual de C-39, etapa B (2026-11-23)

- Mockups aprobados por la líder (README de `../`). Design system Revision 3.
- Entorno: backend real (`bootRun` con `.env`, PostgreSQL en Docker) y frontend con Vite en `http://localhost:5173`; cuentas de prueba ficticias (`cuentas-de-prueba.local.md`); `prefers-reduced-motion: reduce`.
- Capturas: `<pantalla>-<ancho>.png` en esta carpeta (390 × 844, 1440 × 900 y 320 × 844 donde aplica).

## Pantallas revisadas

| Pantalla | 390 | 1440 | 320 | Resultado |
|---|---|---|---|---|
| Portada sin sesión | sí | sí | sí | coincide con `b-sin-sesion` |
| Reservar turno sin sesión | sí | sí | no | coincide (encabezado y pie) |
| Reservar turno con paciente | sí | sí | no | coincide con `b-paciente` |
| Mis turnos con paciente (con un turno) | sí | sí | no | coincide |
| Cajón abierto con paciente | sí | no (la barra es fija) | no | coincide con `b-paciente-cajon-abierto` |
| Agenda con personal | sí | sí | no | coincide con `b-personal` |
| Diálogo "Nuevo turno" | sí | sí | no | coincide con `b-dialogo` |
| Configuración con personal | sí | sí | no | coincide en encabezado, barra y pie |

No se revisó: Mis turnos vacío (el paciente de prueba tiene un turno; el estado vacío y la carga con indicador se comprueban con tests), los estados de hover y foco de cada botón en el navegador (están en los mockups de `b-botones` y se comprueban con tests de las reglas de CSS), las demás pantallas de Configuración y los diálogos de reprogramación y cancelación.

## Comprobaciones en el navegador

- Sin desplazamiento horizontal en ninguna captura; consola sin errores; ningún pedido fuera de `localhost`.
- Sin sesión: no hay barra lateral; el encabezado ofrece "Reservar turno" e "Ingresar" (a `#/mi-cuenta`); el logo tiene `alt="Turnos odontología"`; el pie dice "Turnos odontología".
- Cajón en el celular: se abre con "Menú", el foco pasa al primer enlace; "Escape" lo cierra y el foco vuelve a "Menú"; tocar fuera lo cierra; elegir "Reservar turno" lleva a `#/reserva` y lo cierra.
- Con personal, la barra tiene "Agenda" (marcada como activa) y "Configuración"; con paciente, "Reservar turno" y "Mis turnos".
- Diálogo "Nuevo turno": a 390 px los botones se apilan con "Crear turno" arriba y "Cerrar" abajo; a 1440 px "Cerrar" va a la izquierda y "Crear turno" a la derecha.
- "Salir" lleva a la portada, con paciente y con personal.
- Con el teclado, el primer `Tab` cae en el logo del encabezado.

## Diferencias con los mockups

| Prioridad | Pantalla | Esperado (mockup) | Observado | Reproducción |
|---|---|---|---|---|
| minor | Todas con sesión | Fondo de la página gris azulado (`--ds-superficie`) con tarjetas blancas | Fondo blanco: el fondo y las tarjetas de cada pantalla se uniforman en las etapas C y D | Abrir `#/agenda` |
| minor | Diálogo "Nuevo turno" | Aviso de error debajo del título | El aviso está en su lugar pero vacío hasta que hay un error (no se provocó uno en esta verificación) | Abrir "Nuevo turno" |
| minor | Reservar turno, Mis turnos | Ilustración en el estado vacío | No se vio: el paciente de prueba tiene un turno | Abrir `#/mis-turnos` con un paciente sin turnos |

## Ajustes después de la revisión de la aplicación

- **Borde de color en los turnos de la agenda** (estaba en el mockup y faltaba): `personal-agenda-dia-1440.png` y `personal-agenda-semana-1440.png`.
- **Vista semanal en columnas sin huecos**: antes cada fila de la grilla tomaba el alto del día más largo y dejaba espacios; ahora los días se reparten en columnas independientes (`personal-agenda-semana-1440.png` y `personal-agenda-semana-390.png`). El orden de lectura sigue siendo lunes a domingo, de arriba hacia abajo y de columna en columna.
- **Foco rojo en los botones de peligro**, y **logo sin el rótulo "placeholder"**.

Blockers: ninguno. Majors: ninguno.
