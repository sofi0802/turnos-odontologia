# Proposal

## Why

Hoy `GET /api/v1/turnos` devuelve la agenda de cualquier profesional que pida el cliente. Una odontóloga puede ver los turnos y los nombres de los pacientes de otro odontólogo, cosa que la regla nueva **RN-AG-18** (KB actualizada el 2026-11-23, prueba de la aplicación) ya no permite. La restricción tiene que estar en la API, no solo en la pantalla, porque los ids son correlativos y se pueden adivinar (DD-10).

## What Changes

- **BREAKING** para el rol `ODONTOLOGO`: `GET /api/v1/turnos` filtra siempre por el profesional vinculado al usuario de la sesión. Si el pedido trae otro `profesionalId`, se reemplaza sin avisar y la respuesta es `200` con los turnos propios. El filtro `sillonId` se sigue aplicando, pero solo sobre esos turnos.
- `ADMIN` y `RECEPCIONISTA` siguen viendo la agenda de todos, también un `ADMIN` que tenga un profesional vinculado.
- `GET /api/v1/public/sesion` y la respuesta del login informan además `profesionalId` cuando el usuario es `ODONTOLOGO`, para que el frontend sepa de quién es la agenda sin pedir datos extra. El campo es nuevo y opcional: los clientes que no lo leen no cambian.
- Frontend: con sesión de odontólogo, la agenda del día y de la semana muestra "Mi agenda" con el nombre de su profesional, no ofrece el filtro "Profesional" y conserva el filtro "Sillón".
- Frontend, para todos los roles del personal: los botones "Anterior" y "Siguiente" de la agenda pasan a ser flechas, con el mismo nombre accesible (pedido de la líder al revisar el mockup, 2026-11-23).
- Sin cambios en crear, sobreturno, cancelar, reprogramar ni extender: la matriz de permisos no cambia para esas operaciones.
- Sin migración: el vínculo `usuario.profesional_id` ya es obligatorio para `ODONTOLOGO` (V3).

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `agenda-api`: la consulta de la agenda depende del rol de la sesión. El odontólogo solo recibe los turnos de su profesional.
- `seguridad-autenticacion`: el estado de la sesión y la respuesta del login incluyen `profesionalId` para el rol `ODONTOLOGO`.
- `frontend-agenda-personal`: con sesión de odontólogo, la agenda queda fija en su profesional y sin el filtro "Profesional". Además, "Anterior" y "Siguiente" se muestran como flechas para todos los roles.

## Impact

- Backend: `agenda/api` (consulta de la agenda, dueña Sofia) y `usuarios/api` (respuesta de sesión; se avisa a Juan Sebastián en el PR).
- Frontend: `frontend/src/agenda/` y `frontend/src/pages/agenda.ts`, `shared/sesion/` (dueño Enzo; se le avisa en el PR).
- Tests: integración con PostgreSQL para la API (`@Tag("integration")`) y tests unitarios del frontend.
- Mockup: la vista del odontólogo cambia, así que la política `system-and-mockups` exige un mockup aprobado antes del apply.
- Se coordina el orden de los PR con C-33 y C-36, que también tocan la agenda del personal en `frontend/`.
