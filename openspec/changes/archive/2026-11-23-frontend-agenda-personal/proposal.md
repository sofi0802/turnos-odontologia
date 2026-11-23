# Proposal

## Why

El backend ya permite crear, consultar, cancelar y reprogramar turnos (C-06, capacidad `agenda-api`), pero el personal del consultorio todavía no tiene una pantalla para usarlo: la página Agenda que dejó C-02 solo muestra su título. C-09 entrega la agenda diaria y semanal que pide US-014, para que recepción y los odontólogos vean qué está libre y gestionen los turnos desde la computadora o el celular.

## What Changes

- **Inicio y cierre de sesión del personal** en el frontend, con `POST /api/v1/public/login`, `GET /api/v1/public/sesion` y `POST /api/v1/logout` (C-05). La página Agenda solo se muestra con una sesión de `ADMIN`, `RECEPCIONISTA` u `ODONTOLOGO`; sin sesión lleva a una nueva página de ingreso (`#/ingreso`), y una sesión de `PACIENTE` ve un aviso de acceso no permitido.
- **Agenda del día y de la semana** con `GET /api/v1/turnos?desde&hasta`, con navegación al día o la semana anterior y siguiente, y filtros por profesional y por sillón. Los turnos se ordenan por inicio y se muestran en la hora de `America/Argentina/Buenos_Aires`.
- **Indicadores visuales** de turno confirmado (`estado` `CONFIRMADO`) y de sobreturno (propiedad `esSobreturno` de C-08).
- **Formulario para crear un turno** (`POST /api/v1/turnos`), con los mensajes en español que corresponden a cada código de error (`SOLAPAMIENTO_PROFESIONAL`, `SOLAPAMIENTO_SILLON`, `MARGEN_INSUFICIENTE`, `INICIO_EN_EL_PASADO`, `RECURSO_INACTIVO`, y los de C-07 `FUERA_DE_HORARIO`, `PROFESIONAL_AUSENTE` y `AGENDA_BLOQUEADA`…) y los errores de cada campo ante un `422`.
- **Cancelar y reprogramar** un turno elegido de la agenda. Como la agenda no devuelve el número de reserva, el formulario pide al personal el DNI del paciente y el número de reserva que este informa (decisión acordada con el responsable del change).
- **Cliente de API**: el error de validación incluye la lista de campos con error que manda el backend (`errores`), y las respuestas `401` y `403` se entregan como errores propios con su código, para que las pantallas distingan "sin sesión" de "sin permiso".

### Contratos que consume

- **Catálogos (C-04, capacidad `catalogo-consultorio`, en master)**: `GET /api/v1/profesionales` (`{id, nombre, apellido, matricula, activo, sillonHabitualId}`), `GET /api/v1/sillones` (`{id, nombre, activo}`) y `GET /api/v1/prestaciones` (`{id, nombre, duracionMinutos, activo}`). Los consultan `ADMIN`, `RECEPCIONISTA` y `ODONTOLOGO`; solo `ADMIN` modifica. Si un pedido falla, la pantalla sigue funcionando con un aviso.
- **Sobreturno (C-08, capacidad `agenda-api`, en master)**: cada turno de `GET /api/v1/turnos` trae `esSobreturno: boolean`. C-09 solo lo muestra; no crea ni extiende sobreturnos.
- **Horarios, ausencias y bloqueos (C-07, propuesta en master, implementación pendiente)**: crear o reprogramar un turno puede responder `409` con `FUERA_DE_HORARIO`, `PROFESIONAL_AUSENTE` o `AGENDA_BLOQUEADA`. Con C-07 aplicado, un profesional sin franjas no recibe turnos; en dev, el seed carga a Ana Ejemplo de lunes a viernes de 8 a 13 y a Bruno Prueba de lunes a viernes de 10 a 18.
- **Paciente**: no hay búsqueda de pacientes en la API; el formulario pide el id del paciente hasta que exista una búsqueda (fuera de alcance).

### Fuera de alcance

- Formularios para crear sobreturnos y extender turnos (endpoints de C-08), confirmar turnos (C-11), pantallas de configuración, horarios, ausencias y bloqueos (C-04 y C-07) y de reserva del paciente (C-12).
- Cambios en el backend: este change no toca `backend/`.

## Capabilities

### New Capabilities
- `frontend-sesion-personal`: ingreso y salida del personal en la aplicación web, verificación de la sesión al abrir pantallas privadas y respuesta ante sesión vencida o rol sin permiso.
- `frontend-agenda-personal`: agenda diaria y semanal del personal, con filtros, indicadores, y los formularios para crear, cancelar y reprogramar turnos con mensajes por código de error.

### Modified Capabilities
- `frontend-cliente-api`: el error de validación incluye la lista de campos con error; las respuestas `401` y `403` se entregan como errores con su código en lugar de un error genérico.
- `frontend-navegacion`: se suma la página de ingreso y la página Agenda deja de mostrar solo su título.

## Impact

- **Código**: `frontend/src/shared/api/` (cliente y errores), nuevo `frontend/src/shared/sesion/`, `frontend/src/pages/agenda.ts` y nuevos módulos de agenda e ingreso, `frontend/src/app.ts`, `frontend/src/styles.css`, y sus tests en `frontend/test/`.
- **APIs que consume**: `agenda-api` (C-06 y C-08), `seguridad-autenticacion` (C-05), `catalogo-consultorio` (C-04) y los códigos de rechazo de C-07. No cambia ninguna API.
- **Dependencias**: ninguna nueva; sigue siendo TypeScript `strict` vanilla con Vite y Vitest.
- **Equipo**: Emanuel (C-04, C-07) y Juan Cruz (C-08) confirmaron los contratos; Sofia revisa la propuesta.
