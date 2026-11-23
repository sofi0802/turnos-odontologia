# Proposal

## Why

En la prueba de la aplicación del 2026-11-23 el equipo vio que "Nuevo turno" no sirve para el personal: pide profesional, sillón, prestación, fecha y hora todos juntos y escritos a mano, así que es fácil elegir un profesional que no hace esa prestación, un día que no trabaja o un horario ocupado, y recién al enviar llega el rechazo. C-40 lo convierte en un formulario guiado que solo ofrece combinaciones posibles, como ya hace la reserva del paciente (C-12) y la reprogramación (C-33).

## What Changes

- **Formulario guiado** en "Nuevo turno" de la agenda del personal (`ADMIN`, `RECEPCIONISTA` y `ODONTOLOGO`). El paciente se sigue eligiendo con el buscador de C-36, en el primer lugar. Después, cada paso se habilita cuando el anterior tiene un valor: **prestación → profesional → fecha → horario**.
- **Profesional**: solo los habilitados para la prestación elegida (RN-AG-15), con `GET /api/v1/public/profesionales?prestacionId=`, como en C-33.
- **Fecha**: un calendario mensual, sin depender del selector de fecha del navegador, donde solo se eligen días desde hoy; los pasados se ven deshabilitados y no hay tope hacia adelante (no hay una regla de anticipación máxima en la KB).
- **Horario**: los horarios libres del profesional ese día como botones (`GET /api/v1/public/disponibilidad`), con la consulta y la grilla de C-12 y C-33. Antes de mostrarlos se **ocultan los que pisan otro turno del sillón habitual** (contando el margen), consultando `GET /api/v1/turnos?sillonId=` y `GET /api/v1/configuracion/margen`. Si el profesional no tiene horarios ese día, se avisa.
- **Sillón**: se muestra el sillón habitual del profesional, sin poder cambiarlo, y es el que se envía. Si el profesional no tiene sillón habitual, se avisa y no se puede crear el turno.
- Al cambiar un paso anterior se vacían los que dependen de él.
- Ya no hay campos de fecha y hora para escribir, ni selectores de profesional y sillón con todos los activos.

### Decisiones acordadas con el responsable (2026-11-23)

- El sillón **no se puede cambiar**: va con cada profesional.
- La fecha se elige en un **calendario desde hoy en adelante**.
- Como la disponibilidad pública ofrece un horario si hay **algún** sillón libre (C-33 lo confirmó contra la base real), el filtro por el sillón habitual se hace **en el frontend**, sin cambiar el backend. El `409 SOLAPAMIENTO_SILLON` del backend sigue siendo la garantía final.

### Fuera de alcance

- Cambios en el backend (`reserva/`, `agenda/api`). Si el filtro del frontend resulta insuficiente, se propone a Juan Sebastián sumar `sillonId` a la disponibilidad.
- Sobreturnos, cancelar y reprogramar: no cambian.
- El estilo general de las pantallas del personal, que rehace la etapa D de C-39. C-40 usa los componentes y tokens del design system Revision 1.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `frontend-agenda-personal`: el requisito "Crear un turno" pasa a describir el formulario guiado, y se suman los requisitos de cada paso (profesionales habilitados, calendario, horarios sin pisar el sillón habitual, sillón fijo y vaciado de los pasos siguientes).

## Impact

- **Código**: `frontend/src/agenda/formularios.ts` (`abrirNuevoTurno`), `frontend/src/agenda/catalogos.ts` (leer `sillonHabitualId`), `frontend/src/agenda/reprogramacion.ts` (la zona de horarios recibe su texto de ayuda), un componente nuevo de calendario en `frontend/src/shared/ui/`, `frontend/src/styles.css` y los tests de `frontend/test/`.
- **APIs que consume** (sin cambios): `GET /api/v1/public/prestaciones`, `GET /api/v1/public/profesionales`, `GET /api/v1/public/disponibilidad`, `GET /api/v1/profesionales`, `GET /api/v1/turnos`, `GET /api/v1/configuracion/margen` y `POST /api/v1/turnos`.
- **Orden de archivo**: el delta de "Crear un turno" parte de la versión de C-36 (`buscador-pacientes`), que todavía no está archivado. C-36 se archiva antes que C-40.
- **Equipo**: Enzo es dueño de `frontend/`; avisar a Juan Cruz que C-40 se mergea antes de la etapa D de C-39. Mockups aprobados por la líder antes del apply.
