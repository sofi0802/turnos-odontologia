# Design

## Context

El motivo está en proposal.md (Why) y los requisitos, en los deltas de `specs/`.

- `TurnosController.agenda` le pasa a `ServicioAgenda.agenda(desde, hasta, profesionalId, sillonId)` los parámetros tal cual llegan, sin mirar la sesión.
- El principal `UsuarioAutenticado` ya trae `rol()` y `profesionalId()`, que se cargan del usuario al iniciar sesión. La base garantiza que todo `ODONTOLOGO` tiene un profesional vinculado (`usuario_odontologo_con_profesional`, V3), y un `ADMIN` puede tenerlo o no.
- `SesionResponse` solo informa `autenticado`, `username` y `rol`. En el frontend, `Sesion` (`shared/sesion/sesion.ts`) tiene esos mismos tres campos, y `pages/agenda.ts` arma siempre los filtros "Profesional" y "Sillón" con el título "Agenda".
- Dueños de los módulos: `agenda/api` es de Sofia, `usuarios/api` es de Juan Sebastián y `frontend/` es de Enzo. A los dos últimos se les avisa en el PR.

## Goals / Non-Goals

**Goals:**
- El filtro del odontólogo lo pone el servidor a partir de la sesión: ningún parámetro del pedido lo puede saltear.
- El frontend refleja la regla sin un pedido extra para saber de quién es la agenda.

**Non-Goals:**
- Restringir crear, sobreturno, cancelar, reprogramar o extender turnos de otros profesionales (la matriz de 03 no cambia).
- Tocar bloqueos, ausencias u horarios, que ya filtran por el `Solicitante` de C-07.
- Ocultar los datos de otros profesionales en los catálogos (`/profesionales`, `/sillones`): siguen siendo de lectura para el odontólogo.
- Cambiar la agenda de la ficha clínica (C-13).

## Decisions

1. **El controlador resuelve el alcance; el servicio no cambia.** `TurnosController.agenda` recibe el `UsuarioAutenticado`. Si el rol es `ODONTOLOGO`, reemplaza `profesionalId` por `usuario.profesionalId()` y llama al mismo `ServicioAgenda.agenda`. Así la regla queda en un solo lugar del borde de la API y la consulta, que ya filtra por profesional y sillón, se reutiliza.
   - *Alternativa*: pasar un `Solicitante` al servicio, como hace disponibilidad. Se descartó porque obliga a cambiar la firma del servicio y sus llamadas para una regla que solo afecta este endpoint.
   - El reemplazo se hace con una función chica y pura (`profesionalVisible(usuario, pedido)`) para probarla también sin base de datos.

2. **Se reemplaza sin avisar, sin responder `403`.** Lo decidió la líder el 2026-11-23 y está escrito así en RN-AG-18. Así la agenda del odontólogo nunca se rompe por un parámetro viejo en la URL, y la respuesta no revela si el otro profesional tiene turnos.

3. **El rol manda, no el vínculo.** Un `ADMIN` con `profesionalId` sigue viendo todo. La condición es `rol == ODONTOLOGO`, no `profesionalId != null`.

4. **Un `ODONTOLOGO` sin profesional responde vacío.** La base no lo permite, pero si el principal llegara con `profesionalId` nulo, la API responde una lista vacía y no la agenda completa (denegación por defecto, DD-10).

5. **`profesionalId` en `SesionResponse`, solo para `ODONTOLOGO`.** Es un campo opcional nuevo y `@JsonInclude(NON_NULL)` lo omite para los demás roles. El frontend lo necesita para fijar el filtro y para mostrar "Mi agenda" con el nombre aunque el día no tenga turnos.
   - *Alternativa*: un endpoint `/me/profesional`. Se descartó porque suma un pedido y una ruta que hay que autorizar, cuando el dato ya está en el principal.

6. **Frontend: el filtro fijo lo decide la sesión.** `Sesion` suma `profesionalId?: number`, opcional para que las sesiones de los otros roles no cambien de forma. `leerSesion` lo acepta solo si es un entero positivo y el rol es `ODONTOLOGO`; si no, el campo no existe. En `pages/agenda.ts`, si hay `profesionalId`:
   - no se crea el campo "Profesional";
   - `filtrosActuales()` devuelve siempre ese id;
   - el título pasa a "Mi agenda" con el nombre del profesional debajo, con el nombre tomado del catálogo de profesionales. Si el catálogo falla, queda "Mi agenda".
   
   El backend igual aplica la regla, así que esto es solo comodidad de pantalla.

7. **Flechas en "Anterior" y "Siguiente".** Las pidió la líder al revisar el mockup. Se dibujan con un SVG en línea con `aria-hidden`, y el botón lleva `aria-label` y `title` con la palabra. Así el lector de pantalla y el texto de ayuda no cambian, y no hace falta ninguna librería de íconos. El botón es cuadrado, de 44 × 44 px. El cambio vale para todos los roles porque la barra es la misma. El helper `boton()` de `test/apoyo.ts` encuentra el botón por su texto visible o por su `aria-label`, siempre por igualdad exacta. Así los tests existentes siguen comprobando lo mismo; los botones de cancelar y reprogramar ya tenían un `aria-label` más largo que su texto.

## Risks / Trade-offs

- [Un odontólogo no se da cuenta de que un `profesionalId` en la URL se ignora] → La pantalla no ofrece el filtro y el título deja claro de quién es la agenda.
- [Conflicto en `pages/agenda.ts` con C-33 y C-36] → Se acuerda el orden de los PR con Juan Cruz y Juan Sebastián. Si otro se mergea antes, se rebasa sobre `master` antes de pedir revisión.
- [Tests existentes que piden la agenda como `ana.ejemplo` esperando turnos de otro profesional] → En la tarea 1.1 se revisan los fixtures de `TurnosApiIntegrationTest` y de los demás tests de la API, y se ajustan solo si el escenario es de recepción. No se debilita ninguno.

## Migration Plan

No hay migración de datos. El despliegue es el normal y la vuelta atrás consiste en revertir el PR. Para un cliente viejo del frontend, el campo nuevo de sesión se ignora.

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-23, política `system-and-mockups`)
- Mockups: `openspec/changes/agenda-propia-odontologo/mockups/`: `odontologo-dia.html`, `odontologo-semana.html` y `estados.html` (ver su README)
- Approval: approved (2026-11-23, by user: Sofia Indovino aprobó la revisión 3 de los mockups: fecha del período junto a las flechas y más grande)
- Preview: rendered (2026-11-23, `mockups/previews/` a 390x844 y 1440x900)
- Visual verify: differences (2026-11-23, capturas en `mockups/verify/`). Ninguna diferencia es de C-35; detalle abajo.

### Verificación visual (2026-11-23)

Se probó la app real (backend `dev` y Vite en `localhost:5173`) con tres turnos ficticios del martes 24/11: Ana Ejemplo 09:00 Sillón 1, Bruno Prueba 10:30 Sillón 2 y Ana Ejemplo 11:30 Sillón 2.

| Pantalla | Vista | Resultado |
|---|---|---|
| `recepcion` | día, 1440 y 390 | Igual al mockup de referencia: título "Agenda", filtros "Profesional" y "Sillón", flechas y fecha grande junto a ellas. Se ven los tres turnos. |
| `ana.ejemplo` | día, 1440 y 390 | Igual al mockup: "Mi agenda" con "Ana Ejemplo" debajo, sin filtro "Profesional", solo los dos turnos de Ana (el de Bruno no aparece). |
| `ana.ejemplo` | semana, 1440 | Igual al mockup: solo los turnos de Ana. Los días sin turnos dicen "Sin turnos.". |

- **Consola**: sin errores. **Red**: ningún pedido fallido. Todos los `GET /turnos` de Ana llevan `profesionalId=1`.
- **API con la sesión real**: con la sesión de `ana.ejemplo`, un `GET /turnos` con `profesionalId=2` respondió `200` con solo los turnos de Ana (RN-AG-18).

Diferencias encontradas:

1. **Menor, previa a C-35 (viene de C-12)**: el nombre del paciente de cada turno se ve dentro de un recuadro blanco. Pasa para todos los roles. La agenda usa la clase `turno-paciente` (`src/agenda/vista.ts`) y C-12 le dio estilos de tarjeta a esa misma clase para "Mis turnos" (`src/styles.css`). No se corrige en este change.
2. **Menor, fuera de alcance**: el menú del personal en la app muestra "Agenda" y "Configuración"; el mockup muestra además "Reserva". El menú no cambia en C-35 (C-34 trata Configuración por rol).

No verificado en la app real (sí cubierto por tests): las etiquetas "Confirmado" y "Sobreturno" (los turnos de prueba no las tenían), el filtro por sillón del odontólogo y el estado con el catálogo de profesionales caído.
