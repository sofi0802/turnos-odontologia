# Proposal

## Why

Quien abre la aplicación sin una dirección cae en la pantalla de ingreso: la página inicial hoy es la Agenda, que sin sesión lleva a "Mi cuenta" (verificado en `verify/ingreso.md`). Un paciente nuevo ve un formulario de email y contraseña, sin ninguna explicación de qué es la aplicación ni de que puede reservar un turno sin registrarse. En la revisión del 2026-11-23 se pidió una pantalla de inicio descriptiva y atractiva, con acceso directo a reservar y a la cuenta, y un botón para el personal arriba a la derecha.

## What Changes

- **Portada pública en `#/inicio`** (decidido por la líder el 2026-11-23): una pantalla que explica qué es el consultorio y cómo se reserva un turno en tres pasos, con ilustraciones propias. Tiene dos botones principales, "Reservar turno" (`#/reserva`, sin registrarse antes) e "Ingresar a mi cuenta" (`#/mi-cuenta`).
- **"Acceso personal"** arriba a la derecha de la portada, que lleva a `#/mi-cuenta`.
- **La portada pasa a ser la página inicial**: la dirección vacía y las desconocidas la muestran, en lugar de la Agenda. `#/ingreso` sigue redirigiendo a `#/mi-cuenta` (lo dejó así `ingreso-unico-mi-cuenta`, PR #59, y funciona: ver la verificación).
- **El logo del encabezado lleva a la portada.**
- **Con sesión**, la portada ofrece ir al espacio de cada rol ("Mis turnos" y "Reservar turno" para el paciente; "Ir a la agenda" para el personal), sin redirigir sola, y no muestra los botones de ingreso.
- **"Salir" lleva a la portada**, tanto del paciente (hoy a `#/reserva`) como del personal (hoy a `#/mi-cuenta`).
- **Mi cuenta con sesión del personal lleva a la Agenda.** Hoy lo hace solo con el paciente: el personal que llega a Mi cuenta con sesión (por ejemplo, desde `#/ingreso`) se queda ahí.
- **Una indicación para el personal en Mi cuenta**: un texto breve que dice que el personal ingresa con su usuario en el mismo formulario. No cambia la etiqueta "Email" ni los mensajes actuales.
- **Design system y mockups.** La portada es una pantalla nueva: se aplica el modo `system-and-mockups` (revisión 1). Los mockups se crean y se aprueban antes del apply.

Fuera de alcance:
- La barra lateral, el color del encabezado y el pie, los efectos hover y el rediseño general (C-39).
- El explorador de prestaciones de la reserva (C-38).
- Cualquier cambio en `backend/`, en el login o en la seguridad.
- Fotos de personas o de terceros: solo ilustraciones propias.
- Datos reales del consultorio (dirección, teléfono): la portada no los muestra; los define el equipo más adelante.

## Capabilities

### New Capabilities

- `frontend-portada`: la portada pública `#/inicio`: qué muestra, sus botones, el acceso del personal, cómo se ve con sesión y en el celular, y que no pide recursos de otros orígenes.

### Modified Capabilities

- `frontend-navegacion`: `#/inicio` entre las páginas disponibles y como página inicial, en lugar de la Agenda.
- `frontend-sesion-personal`: el cierre de sesión del personal lleva a la portada.
- `frontend-cuenta-paciente`: Mi cuenta con sesión del personal lleva a la Agenda, "Salir" del paciente lleva a la portada, y la indicación para el personal.

## Impact

- **`frontend/src/`** (módulo de Enzo, que se avisa en el PR): `app.ts` (páginas, página inicial, "Salir"), `pages/` (página nueva `inicio.ts` y sus ilustraciones), `pages/mi-cuenta.ts`, `shared/ui/layout.ts` (el logo como enlace y el botón "Acceso personal") y `styles.css`.
- **`frontend/test/`**: tests nuevos de la portada y de las redirecciones; hay que actualizar los que dan por hecho que la página inicial es la Agenda o que "Salir" lleva a Mi cuenta o a la reserva.
- **Sin cambios** en `backend/`, en migraciones ni en seguridad.
- **Dependencias**: C-09 y C-12; `ingreso-unico-mi-cuenta` (PR #59) ya está mergeado, pero **sin archivar**: este change modifica los mismos requisitos, así que su archive espera al de ese change.
- **Orden de los PR**: C-37 → C-38 → C-39. Enzo publicó una ficha propia con el número C-37 (`frontend-nuevo-turno-guiado`); se avisa para que se renumere (ver `design.md`).
