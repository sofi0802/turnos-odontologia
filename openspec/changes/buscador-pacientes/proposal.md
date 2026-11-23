# Proposal

## Why

En la prueba de la aplicación del 2026-11-23, el formulario "Nuevo turno" de la agenda del personal pide el **id numérico del paciente**, un dato que la recepción no conoce. Hoy no existe ningún endpoint para buscar pacientes, así que el personal no puede dar un turno sin consultar la base. C-36 (fase 5b, ajuste del MVP) agrega una búsqueda acotada por DNI o por nombre y apellido, con la mínima cantidad de datos personales (Ley 25.326), y la usa en la agenda.

## What Changes

- **Backend, nuevo endpoint** `GET /api/v1/pacientes?q=` solo para el personal (`ADMIN`, `RECEPCIONISTA`, `ODONTOLOGO`):
  - Si `q` tiene solo dígitos, busca por DNI. Si tiene letras, busca por nombre y apellido sin distinguir mayúsculas ni tildes.
  - Devuelve una lista corta, con un máximo fijo de resultados. Cada resultado trae **solo** `id`, `nombre`, `apellido` y `dni`: nada de email, teléfono, WhatsApp, consentimiento ni datos de salud.
  - Una `q` ausente, demasiado corta o demasiado larga responde `422 DATOS_INVALIDOS`. Sin sesión responde `401 NO_AUTENTICADO` y con sesión `PACIENTE`, `403 ACCESO_DENEGADO`, por las reglas que ya existen en `SecurityConfig` (C-05).
  - El término buscado no se escribe en logs ni se repite en las respuestas de error.
- **Frontend, buscador de pacientes**: un componente reutilizable en `frontend/src/agenda/` con estos estados: vacío, buscando, resultados, sin resultados, error y paciente elegido. Espera 300 ms después de la última tecla, se maneja con el teclado (patrón combobox de ARIA) y guarda el id del paciente elegido.
- **Frontend, "Nuevo turno"**: el campo "Id del paciente" se reemplaza por el buscador. El pedido `POST /api/v1/turnos` sigue enviando `pacienteId`; la API de turnos no cambia.
- **"Sobreturno" (resuelto: fuera de C-36)**: el scope de CHANGES.md nombra el formulario "Sobreturno", pero **ese formulario no existe en `frontend/`**. C-09 dejó fuera de scope la creación de sobreturnos y C-08 solo hizo el backend. La propuesta deja el buscador listo para reutilizar y no crea el formulario de sobreturno. La líder decidió el 2026-11-23 que queda fuera de C-36 y tendrá su propia ficha (design D11).
- **Migración V20**: no se propone. Con el volumen de un consultorio chico, la búsqueda no necesita un índice nuevo (design D7, confirmado). El número queda reservado por si la líder pide el índice.
- Sin cambios en `SecurityConfig`, en `PacienteEntity` ni en la API de turnos.

## Capabilities

### New Capabilities
- `busqueda-pacientes`: búsqueda de pacientes para el personal por DNI o por nombre y apellido. Cubre la regla de coincidencia, los límites de la consulta y de la respuesta, los datos mínimos que devuelve, los roles y el manejo del término buscado como dato personal.
- `frontend-buscador-pacientes`: componente de búsqueda y elección de un paciente en los formularios del personal. Cubre la espera antes de buscar, los estados, el teclado y la accesibilidad, y los errores.

### Modified Capabilities
- `frontend-agenda-personal`: el requisito "Crear un turno" cambia el campo "paciente (id)" por el buscador de pacientes y exige un paciente elegido para enviar. **Dependencia**: la capacidad la definió C-09, que ya está archivado, y el delta modifica el requisito de `openspec/specs/frontend-agenda-personal/spec.md`.

## Impact

- **Backend** (módulo `usuarios/`, dueño Juan Sebastián): `usuarios/api` (controller, DTO y manejo de `422` en `UsuariosExceptionHandler`), `usuarios/application` (caso de uso y puerto de búsqueda), `usuarios/domain` (término de búsqueda validado) y `usuarios/infrastructure` (adaptador JDBC de solo lectura sobre la tabla `paciente`). No se toca `shared/persistence/PacienteEntity` (Juan Cruz) ni `shared/security`.
- **Frontend** (dueño Enzo, **se le avisa en el PR**): `agenda/buscador-pacientes.ts` (nuevo), `agenda/formularios.ts` (`abrirNuevoTurno`), `styles.css`, y tests en `frontend/test/`.
- **Coordinación con C-33 y C-35**: los dos tocan la misma pantalla de agenda. C-33 cambia `abrirReprogramacion` en `formularios.ts` y C-35 los filtros de `pages/agenda.ts`. C-36 solo cambia `abrirNuevoTurno` y suma un archivo nuevo, para achicar los conflictos. El orden de los PR se acuerda con Juan Cruz y Sofia.
- **Specs**: C-09 tiene que archivarse antes que C-36 (MODIFIED de `frontend-agenda-personal`).
- **Base de datos**: ninguna migración (V20 queda reservada y sin uso, salvo que la líder la pida).
- **Governance ALTO** (datos personales de pacientes): la propuesta la revisa la líder antes de escribir código (tasks, grupo 0).
- **Fuera de scope**: alta y edición de pacientes desde la agenda, la ficha del paciente, el formulario de sobreturno (salvo decisión D11), búsqueda por email o teléfono, paginación y auditoría de consultas.
