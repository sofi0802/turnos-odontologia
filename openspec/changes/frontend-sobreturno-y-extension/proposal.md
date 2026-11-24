# Proposal: frontend-sobreturno-y-extension (C-45)

## Why

C-08 hizo el backend del sobreturno (`POST /api/v1/turnos/sobreturno`) y de la extensión (`POST /api/v1/turnos/{id}/extender`), pero ninguna pantalla los usa. Hoy la agenda solo muestra la etiqueta "Sobreturno" de los que ya existen: la recepción y el odontólogo no pueden cargar una urgencia (US-013) ni extender un turno que se complicó (US-019) desde la aplicación. Es lo último de C-08 sin interfaz y hace falta para la entrega.

## What Changes

- **Botón "Sobreturno"** en la barra de la agenda del personal, al lado de "Nuevo turno", para `ADMIN`, `RECEPCIONISTA` y `ODONTOLOGO`. Para el odontólogo, el profesional queda fijo en el suyo (RN-AG-18).
- **Formulario de sobreturno**: paciente con el buscador de C-36, prestación, profesional habilitado para esa prestación, fecha, hora (libre: puede pisar la agenda del profesional, RN-AG-06) y motivo administrativo obligatorio, de hasta 500 caracteres. No se elige sillón: lo asigna el servidor y el mensaje de éxito lo informa junto con el número de reserva.
- **Acción "Extender"** en cada turno `RESERVADO` o `CONFIRMADO` que no es sobreturno, en la vista del día y en el detalle de la semana, para los mismos tres roles. El formulario muestra el horario actual y pide el nuevo fin (mismo día, posterior al fin actual) y el motivo.
- **Choque al extender** (US-019 CA-2): si la API rechaza la extensión porque choca con otro turno (`SOLAPAMIENTO_SILLON`, `SOLAPAMIENTO_PROFESIONAL` o `MARGEN_INSUFICIENTE`), se explica que no se movió nada y se ofrecen dos salidas: reprogramar el turno que choca, cuando la agenda cargada lo identifica, o cargar la atención como sobreturno con el formulario precargado.
- **Mensajes nuevos** para `SIN_SILLON_DISPONIBLE` y `TURNO_NO_EXTENSIBLE`. El resto de los códigos y el `422` reutilizan los mensajes que ya existen.
- El modelo `TurnoAgenda` del frontend suma `pacienteId`, que la API ya devuelve, para precargar el paciente.
- Sin cambios de backend, de contrato ni de migraciones.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `frontend-agenda-personal`: se suman requisitos para cargar un sobreturno, extender un turno, resolver el choque de una extensión y mostrar los mensajes de sus rechazos. No cambia ningún requisito existente.

## Impact

- **Frontend**: `frontend/src/pages/agenda.ts` (botón y acción nueva), `frontend/src/agenda/formularios.ts` o un módulo nuevo `agenda/sobreturno.ts` (formularios), `agenda/vista.ts` y `agenda/semana.ts` (botón "Extender"), `agenda/consulta.ts` (`pacienteId`), `shared/ui/mensajes.ts` (dos códigos) y los estilos de la agenda.
- **API usada**: `POST /api/v1/turnos/sobreturno`, `POST /api/v1/turnos/{id}/extender` y `GET /api/v1/public/profesionales?prestacionId=` (ya existentes).
- **Dueños**: `frontend/` es de Enzo. Se avisa a Juan Cruz, dueño del backend de sobreturno y extensión, aunque no se toca su código.
- **Fuera de alcance**: mostrar en la agenda que un turno fue extendido. `TurnoAgendaResponse` no trae `extendido`, y sumarlo es un cambio de `agenda/api` (Sofia). Tampoco se reprograma nada automáticamente.
- **Visual**: modo `system-and-mockups`. Mockups a 390 y 1440 px aprobados antes del apply.
