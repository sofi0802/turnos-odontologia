# Design

## Context

Lo observado en `master` (d8cc8fa) que condiciona el diseño:

- **`abrirNuevoTurno`** (`frontend/src/agenda/formularios.ts`) arma un `<dialog>` con el buscador de pacientes de C-36 y selectores de profesional, sillón y prestación con **todos** los activos del catálogo, más `<input type="date">` y `<input type="time">`. Envía `inicio` con `aIsoConZona(fecha, hora)`.
- **La reprogramación de C-33** ya resuelve "horarios libres como botones": `crearBuscadorHorarios` (estado de la consulta, descarta respuestas viejas) y `crearZonaHorarios` (fieldset con la grilla `.chips` de `paciente/horarios.ts`). La zona trae fijo el texto de ayuda `TEXTO_PISAN_EL_TURNO`, propio de reprogramar.
- **`agenda/catalogos.ts`** lee `GET /api/v1/profesionales`, pero `leerItem` descarta `sillonHabitualId`, que el backend sí devuelve (`ProfesionalVista`).
- **La disponibilidad pública** ofrece un horario si el profesional está libre y **algún** sillón está libre. C-33 lo confirmó contra la base real (`frontend-reprogramacion-agenda/verify/reproduccion.md`, candidato 1): con el sillón ocupado, el backend responde `409 SOLAPAMIENTO_SILLON`.
- **`GET /api/v1/turnos`** acepta `sillonId`. Con sesión `ODONTOLOGO` (C-35, RN-AG-18) solo devuelve los turnos de su profesional, aunque se filtre por sillón.
- **`GET /api/v1/configuracion/margen`** responde `{ minutos }` a todo el personal.
- **La reserva del paciente** elige la fecha con `<input type="date">`; no hay un calendario propio en el frontend.
- **Specs**: el delta de C-36 (`buscador-pacientes`, sin archivar) ya modifica "Crear un turno"; este change parte de esa versión.
- **Design system**: Revision 1 aprobada, modo `system-and-mockups`.

Motivación y alcance: ver `proposal.md`.

## Goals / Non-Goals

**Goals:**
- Que el formulario solo ofrezca combinaciones que el backend va a aceptar en el caso normal: profesional habilitado, día desde hoy, horario libre del profesional y de su sillón.
- Reusar lo que ya hicieron C-12, C-33 y C-36 sin cambiar su comportamiento.
- Dejar el calendario como componente reutilizable (la reserva del paciente o la reprogramación podrían adoptarlo después).

**Non-Goals:**
- Cambiar el backend.
- Mostrar en el calendario qué días tienen horarios libres: requeriría una consulta por día. Se avisa al elegir un día sin horarios.
- Cambiar los formularios de cancelar, reprogramar o sobreturno.

## Decisions

### D1. El formulario es una pequeña máquina de pasos
`abrirNuevoTurno` guarda `{ prestacionId, profesional, fecha, horario }` y una función `habilitarPasos()` que, después de cada cambio, deshabilita y vacía los pasos que dependen del que cambió. El paciente es independiente de la cadena y va primero (mantiene el foco y el escenario "Ya no se pide el id del paciente" de C-36).

*Alternativa descartada*: un asistente de varias pantallas, como la reserva del paciente. El personal carga turnos seguido y prefiere ver todo junto; el diálogo único además conserva los tests y textos de C-09 y C-36.

### D2. Prestaciones del catálogo del personal; profesionales de la API pública
Las prestaciones salen de `catalogos.prestaciones` (activas), que la agenda ya cargó. Los profesionales salen de `GET /public/profesionales?prestacionId=` (como en C-33), porque el catálogo del personal no trae las habilitaciones (RN-AG-15). El sillón habitual se busca por id en `catalogos.profesionales`, que pasa a leer `sillonHabitualId`.

### D3. Calendario propio en `shared/ui/calendario.ts`
Un componente sin dependencias: `crearCalendario({ hoy, alElegir })` con `deshabilitar(si)`, `elegido()` y `limpiar()`. Grilla de 7 columnas (lunes a domingo) dentro de un `role="group"` con el mes como título, botones "Mes anterior" y "Mes siguiente", días como `<button>` con `aria-label` de la fecha completa (`fechaLarga` de `shared/fecha/zona.ts`) y `aria-pressed` en el elegido. Los días anteriores a `hoy()` y los de relleno de otro mes se deshabilitan; "Mes anterior" se deshabilita en el mes de hoy. Las fechas se calculan con `shared/fecha/zona.ts` (zona del consultorio), así que el calendario no depende de la zona de la máquina.

*Alternativa descartada*: `<input type="date">` con `min`. Es lo que se pidió reemplazar: no se ve como calendario en todos los navegadores y en el celular varía mucho.

### D4. Filtro por el sillón habitual en el cliente
Al elegir un día se piden en paralelo la disponibilidad, `GET /turnos?desde&hasta&sillonId=<habitual>` de ese día y (una vez por diálogo) el margen. Un horario `[inicio, fin)` se oculta si para algún turno activo `[i, f)` del sillón se cumple `inicio < f + margen` y `i < fin + margen`, la misma regla del dominio (RN-AG-02 a RN-AG-05). La función es pura (`horariosSinChoqueDeSillon`) y se prueba aparte.

Si la consulta de turnos o la del margen fallan, se muestran los horarios sin filtrar: el backend sigue rechazando con `409 SOLAPAMIENTO_SILLON`, que tiene su mensaje. El margen, si no llega, se toma como 10 minutos (el mínimo de RN-AG-04).

*Alternativas descartadas*: pedir a Juan Sebastián `sillonId` en la disponibilidad (más prolijo, pero deja C-40 esperando un cambio de otro módulo; queda como mejora si el filtro no alcanza) y aceptar el rechazo (el sillón es fijo, así que el personal quedaría sin salida para ese horario).

### D5. La zona de horarios de C-33 recibe su texto
`crearZonaHorarios(ayuda?: string)`: la reprogramación sigue pasando `TEXTO_PISAN_EL_TURNO` y el nuevo turno no pasa ayuda. Los textos de "sin horarios" y "error" también pasan a ser parámetros con los de C-33 por defecto, para no cambiar sus tests. `crearBuscadorHorarios` se reusa tal cual, con un paso de filtro antes de `listo` (D4).

### D6. Sillón como texto, no como control
Se muestra "Sillón: Sillón 1" en un párrafo con `aria-live="polite"` que se actualiza al cambiar el profesional. Sin sillón habitual, ese párrafo muestra el aviso de la spec como `role="alert"` y el calendario queda deshabilitado. Los errores del `422` sobre `sillonId` y el `409 SOLAPAMIENTO_SILLON` van al mensaje general del formulario.

### D7. Mensajes y errores de campo
Se mantienen `manejarEnvio` y `mensajeDeError`, con los escenarios de "Mensajes por código de rechazo" y "Errores de campo ante un 422" de C-09. El mapa de campos queda: `pacienteId` → buscador, `prestacionId` → "Prestación", `profesionalId` → "Profesional", `inicio` → zona de horarios y `sillonId` → mensaje general.

## Risks / Trade-offs

- [Con sesión `ODONTOLOGO`, `GET /turnos?sillonId=` solo trae sus propios turnos (RN-AG-18)] → para un odontólogo el filtro no ve los turnos de otros profesionales en su sillón. Queda la garantía del `409 SOLAPAMIENTO_SILLON`, con su mensaje. Si molesta en la práctica, se propone el filtro en el backend (Juan Sebastián).
- [La regla del margen se repite en el cliente] → solo para ocultar opciones; el dominio y la restricción `EXCLUDE` siguen siendo la garantía (regla dura 5). La función tiene tests con los bordes de RN-AG-04.
- [El calendario no marca los días sin horarios] → se avisa al elegirlo. Marcarlo pediría hasta 31 consultas por mes.
- [Los tests de C-09 y C-36 que llenan el formulario viejo] → se reescriben para recorrer los pasos nuevos, sin cambiar su nombre ni lo que comprueban.
- [Conflicto de specs con C-36 sin archivar] → C-36 se archiva antes que C-40; si cambia su delta, este se actualiza con `openspec-update-change`.
- [C-39 reestiliza las pantallas del personal] → C-40 usa los componentes del design system Revision 1 y se mergea antes de la etapa D de C-39.

## Migration Plan

Solo frontend. Se despliega con el build de Vite; se revierte con el PR.

## UI Mockups

- Design system revision: 1 (`design-system/README.md`).
- Decision: yes, 2026-11-23 (cambio visual en modo `system-and-mockups`).
- Mockups: `openspec/changes/frontend-nuevo-turno-guiado/mockups/`.
- Approval: pending.
- Preview: rendered (2026-11-23): `mockups/previews/` a 390 × 844 y 1440 × 900, sin desplazamiento horizontal ni errores de consola. En la computadora el calendario y los horarios van lado a lado (diálogo de hasta 52 rem); en el celular se apilan y los días miden 46 × 44 px.
- Visual verify: pending.
