# Proposal

## Why

En la agenda del personal, "Reprogramar turno" (C-09) pide una fecha y una hora libres y ofrece **todos** los profesionales activos. Así el personal puede elegir un profesional que no atiende esa prestación (RN-AG-15) o un horario en el que nadie atiende, y la reprogramación se rechaza sin haberle mostrado nunca qué horarios sí sirven. En la prueba de la aplicación del 2026-11-23 además se vio que la reprogramación falla incluso con un horario que parecía válido. La reserva del paciente (C-12) ya resuelve esto con horarios disponibles como botones; la pantalla del personal no.

## What Changes

- **Profesionales habilitados.** El selector de profesional del formulario "Reprogramar turno" ofrece solo los profesionales habilitados para la prestación del turno (`GET /api/v1/public/profesionales?prestacionId=`), más la opción de dejar el profesional actual.
- **Horarios como botones.** Se reemplaza la hora libre por los horarios libres del profesional elegido para el día elegido (`GET /api/v1/public/disponibilidad`), como botones, con los mismos componentes de la reserva del paciente (`paciente/horarios.ts` y `paciente/disponibilidad.ts`). El `inicio` que se envía es el del botón, sin recalcularlo.
- **Reproducir y corregir la falla.** Primero se reproduce, contra el backend real, por qué una reprogramación con un horario válido se rechaza. El análisis del código apunta a tres causas candidatas, que se confirman antes de corregir:
  1. el horario ofrecido por la disponibilidad solo exige que *algún* sillón esté libre, pero la reprogramación del personal mantiene el sillón original si no se elige otro (`SOLAPAMIENTO_SILLON`);
  2. el número de reserva solo coincide con el guion (`XXXX-XXXX`): sin guion se rechaza con `DATOS_DE_RESERVA_INVALIDOS`;
  3. un rechazo cuyo código no tiene texto propio muestra el detalle del servidor, que no siempre le sirve al personal.
  La corrección de esta ficha cubre lo que es del frontend: el formulario normaliza el número de reserva, ofrece el sillón y muestra un mensaje claro por cada código de rechazo.
- **Sin cambios en el backend.** Si la reproducción muestra que la causa está en `agenda/api` o en `reserva` (por ejemplo, que la reprogramación debería elegir sola un sillón libre, como ya hace el sobreturno), se avisa en el PR a su dueño y se deja como mejora aparte.
- **Design system y mockups.** El formulario cambia visualmente: se aplica el modo `system-and-mockups` (revisión 1 del design system). Los mockups se crean y se aprueban antes del apply.

Fuera de alcance:
- Cambiar la reserva del paciente o "Mis turnos" (C-12).
- Que la disponibilidad pública ignore el propio turno (ver el riesgo en `design.md`).
- Crear un turno nuevo desde la agenda (sigue como en C-09).
- Cualquier cambio en `backend/`.

## Capabilities

### New Capabilities

- (ninguna: el comportamiento nuevo se expresa como deltas de la capacidad de la agenda del personal)

### Modified Capabilities

- `frontend-agenda-personal`: la reprogramación de un turno desde la agenda. Esta capacidad la crea C-09, que todavía no está archivado: el delta de este change se escribe contra su texto actual en `openspec/changes/frontend-agenda-personal/` y **se aplica recién después del archive de C-09** (ver `design.md`).

## Impact

- **`frontend/src/agenda/formularios.ts`** (módulo de Enzo, que se avisa en el PR): `abrirReprogramacion`.
- **`frontend/src/agenda/`**: carga de los profesionales habilitados y de los horarios del profesional elegido (módulo nuevo, junto a `consulta.ts` y `catalogos.ts`).
- **`frontend/src/paciente/`**: se reutilizan `horarios.ts` y `disponibilidad.ts` sin cambiarlos.
- **`frontend/src/shared/ui/mensajes.ts`**: texto para `DATOS_DE_RESERVA_INVALIDOS` y para el rechazo por sillón al reprogramar.
- **`frontend/test/`**: `formularios.test.ts` y `agenda.test.ts` (los de C-09 siguen pasando) y tests nuevos de la reprogramación.
- **Sin cambios** en `backend/`, en migraciones ni en seguridad.
- **Orden de los PR**: C-33, C-35 y C-36 tocan la agenda del personal; se coordina el orden para evitar conflictos.
- **Dependencias**: C-09 (archivado), C-10 y C-12.
