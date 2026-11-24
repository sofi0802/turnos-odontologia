# Proposal

## Why

Hoy la aplicación tiene dos pantallas de ingreso: "Mi cuenta" (`#/mi-cuenta`, de C-12), a la que lleva el enlace "Ingresar" del encabezado y que ya acepta al personal, y la vieja página del personal (`#/ingreso`, de C-09), a la que llevan "Salir", la Agenda sin sesión y la sesión vencida. El mismo usuario termina en dos pantallas distintas según por dónde entró. El usuario decidió el 2026-11-23 que "Mi cuenta" sea la única pantalla de ingreso, para pacientes y personal.

Es un fix de C-12 (`frontend-reserva-paciente`). Modifica specs de C-09 (`frontend-sesion-personal`) y el código de `frontend/`.

## What Changes

- "Salir" con una sesión del personal lleva a `#/mi-cuenta` en lugar de `#/ingreso`. Con una sesión de paciente sigue llevando a "Reservar turno" y borrando la reserva pendiente.
- Abrir una página del personal sin sesión lleva a `#/mi-cuenta` sin pedir datos. Un paciente en una página del personal sigue viendo "Esta sección es solo para el personal del consultorio.".
- La sesión vencida (`401`) del personal lleva a `#/mi-cuenta` con el aviso "Tu sesión terminó. Ingresá de nuevo.".
- **BREAKING**: se elimina la página `#/ingreso`. La dirección vieja `#/ingreso` lleva a `#/mi-cuenta`, no a la página inicial, para no romper marcadores.
- El personal que se equivoca de contraseña ve el mensaje de Mi cuenta, "Email o contraseña incorrectos.", en lugar de "Usuario o contraseña incorrectos.".
- La pantalla Mi cuenta no cambia: ni la etiqueta "Email", ni los textos, ni los estilos. Solo cambia a dónde llevan las redirecciones.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `frontend-sesion-personal`: el ingreso, las credenciales rechazadas, las páginas privadas sin sesión, la sesión vencida y el cierre de sesión del personal pasan a `#/mi-cuenta`.
- `frontend-navegacion`: desaparece la página `#/ingreso` de las páginas disponibles y la dirección vieja lleva a `#/mi-cuenta` en lugar de la página inicial.
- `frontend-cuenta-paciente`: `#/mi-cuenta` queda especificada como el único ingreso de la aplicación, también para el personal.

## Impact

- **Código:** `frontend/src/app.ts`, `frontend/src/pages/pagina.ts`, `frontend/src/pages/agenda.ts`, `frontend/src/shared/ui/router.ts`; se borra `frontend/src/pages/ingreso.ts`. Tests en `frontend/test/` (`sesion`, `navegacion`, `cuenta-paciente`, `buscador-pacientes`).
- **Dueño:** `frontend/` es de Enzo; hay que avisarle en el PR. Los specs de `frontend-sesion-personal` son de C-09 (Enzo).
- **Changes en curso:** `frontend-configuracion-consultorio` (C-34) y `buscador-pacientes` (C-36) todavía no están archivados y mencionan el ingreso. Ver design.
- **Sin backend, sin API nueva, sin migración.** Governance BAJO.
