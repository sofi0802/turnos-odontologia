# Verificación visual de C-39, etapa C (2026-11-24)

- Mockups aprobados por la líder (README de `../`). Design system Revision 3.
- Entorno: backend real (`bootRun` con `.env`, PostgreSQL en Docker) y frontend con Vite en `http://localhost:5173`; cuenta de prueba ficticia (`cuentas-de-prueba.local.md`); `prefers-reduced-motion: reduce`.
- Capturas: `../antes/` (la aplicación antes de la etapa) y esta carpeta (después), como `<pantalla>-<ancho>.png` a 390 × 844 y 1440 × 900.
- Mis turnos se simuló solo en el navegador (respuesta de `GET /paciente/turnos` interceptada), con dos turnos próximos y dos anteriores ficticios, y vacío: no se escribió nada en la base.

## Pantallas revisadas

| Pantalla | Resultado |
|---|---|
| Portada sin sesión y con paciente | coincide con `c-inicio-sin-sesion` y `c-inicio-paciente` |
| Reservar turno: paso 1 (cerrado y abierto) y paso 2 | coincide con `c-reserva-paso1`, `-abierto` y `c-reserva-paso2` |
| Mi cuenta: Ingresar y Crear cuenta | coincide con `c-mi-cuenta` y `c-mi-cuenta-registro` |
| Mis turnos con turnos, vacío, diálogo de cancelar y cambiar horario | coincide con `c-mis-turnos*` |
| Confirmar asistencia (link con un token inválido: botón y rechazo) | coincide con `c-confirmar` |

No se revisó: el paso 3 de la reserva, la reserva confirmada y el horario ocupado (el servidor no tenía horarios libres para hoy y confirmar crearía datos; los cubren los tests de C-12 y los mockups), ni los resultados "confirmado" y "ya confirmado" de Confirmar asistencia (también necesitan un token válido). No hay capturas "antes" de Confirmar asistencia (se agregaron después de tomar las otras).

## Comprobaciones

- Sin desplazamiento horizontal en ninguna captura; ningún pedido fuera de `localhost`; en la consola solo los dos `404` esperados del link inválido de Confirmar asistencia.
- Diálogo de cancelar: a 390 px "Sí, cancelar turno" arriba y "Volver" abajo; a 1440 px "Volver" a la izquierda y la acción de peligro a la derecha.
- Los títulos pasan de `2em` a `1.5rem` (h1) y `1.125rem` (h2); la columna de las pantallas del paciente, de 36 a 44 rem; los turnos anteriores quedan transparentes con borde; el usuario del encabezado va en texto chico para que entre el email completo.

## Diferencias con los mockups

| Prioridad | Pantalla | Esperado (mockup) | Observado | Reproducción |
|---|---|---|---|---|
| minor | Mis turnos vacío | Título "Todavía no tenés turnos" y una frase más: "Cuando reserves uno, lo vas a ver acá con su número de reserva." | Solo "Todavía no tenés turnos." (el texto del spec; la frase extra no existe en la aplicación) | `#/mis-turnos` sin turnos |
| minor | Encabezado en el celular | El nombre de usuario no se muestra a 390 px (queda el avatar, que también se oculta) | Igual que el mockup | Abrir cualquier página con sesión a 390 px |

Blockers: ninguno. Majors: ninguno.
