# Proposal

## Why

El odontólogo hoy no tiene dónde registrar los antecedentes, las alergias y la medicación del paciente ni el estado de sus piezas dentales, y tampoco puede dejar constancia de que el paciente no vino a su turno, lo que deja ese horario ocupado. C-06 ya dejó la API de turnos y C-05 la sesión con roles. Falta la ficha clínica del MVP (Épica 5: US-040, US-041 y US-042), que es la parte del sistema con datos de salud y por eso exige el control de acceso más estricto (Ley 26.529).

## What Changes

- **Ficha básica** en `GET` y `PUT /api/v1/pacientes/{id}/ficha` (US-040): antecedentes, alergias y medicación, con quién y cuándo la actualizó por última vez. El `PUT` reemplaza el contenido anterior (decisión del dueño del change). Un paciente sin ficha devuelve una ficha vacía.
- **Odontograma** con historial (US-041, RN-CL-02, RN-CL-03):
  - `PUT /api/v1/pacientes/{id}/odontograma` agrega uno o más registros (pieza, cara opcional y estado) firmados por el profesional de la sesión. Nunca modifica ni borra un registro anterior.
  - `GET /api/v1/pacientes/{id}/odontograma` devuelve el estado vigente de cada pieza y cara, y `GET /api/v1/pacientes/{id}/odontograma/historial` todos los registros, del más nuevo al más viejo.
  - Notación FDI: piezas permanentes 11–48 y temporales 51–85. Caras `O`, `M`, `D`, `V`, `L` (solo piezas inferiores) y `P` (solo piezas superiores). Catálogo cerrado de estados.
- **Ausencia del paciente** con `POST /api/v1/pacientes/{id}/ausencias` (US-042, RN-AG-17): el odontólogo del turno lo marca como `AUSENTE` una vez que pasó su hora de inicio. El turno deja de ocupar la agenda y el horario que queda se puede volver a dar desde ese momento. La operación queda en la auditoría de turnos con la acción nueva `AUSENTE`, y la ficha muestra las ausencias del paciente.
- **Permisos** (RN-CL-01, matriz RBAC):
  - El `ODONTOLOGO` lee y modifica la ficha y el odontograma.
  - El `ADMIN` solo lee.
  - El `RECEPCIONISTA` y el `PACIENTE` reciben `403 ACCESO_DENEGADO`.
  - Solo el odontólogo vinculado al profesional del turno marca la ausencia (decisión del dueño del change).
- **Errores** en `ProblemDetail` con código estable:
  - `404` para un paciente o un turno inexistentes.
  - `409` para `TURNO_NO_ACTIVO` y `TURNO_NO_INICIADO`.
  - `422` para `PIEZA_INVALIDA`, `CARA_INVALIDA` y los demás datos de entrada.
- **Sin datos de salud fuera de la ficha** (regla dura 6): no van a logs, a mensajes de error ni a la auditoría.
- **Migración V8** (número reservado para Emanuel):
  - tablas `ficha_clinica` y `odontograma_registro`, con `CHECK` de pieza FDI, cara y estado;
  - acción `AUSENTE` en el `CHECK` de `turno_auditoria`.

**Fuera de alcance**:
- Periodontograma, imágenes, radiografías y consentimientos (C-25 a C-27).
- Historial de versiones de la ficha básica.
- Marcar un turno como `ATENDIDO`.
- Avisar al paciente de la ausencia (C-11).
- Pantallas del frontend (C-09).

## Capabilities

### New Capabilities
- `ficha-clinica`: ficha básica y odontograma FDI del paciente, con su historial, los permisos de acceso a datos de salud y la consulta de las ausencias registradas.

### Modified Capabilities
- `agenda-turnos`: se agrega el requisito de marcar la ausencia del paciente. El turno pasa de activo a `AUSENTE` y libera el horario, con las condiciones de quién y cuándo.
- `auditoria-turnos`: se agrega el registro de la ausencia con la acción `AUSENTE`.

## Impact

- **Código nuevo**: `backend/.../clinica/` (módulo de Emanuel) con dominio (`PiezaDental`, `CaraDental`, `EstadoPieza`, `RegistroOdontograma`), casos de uso, adaptadores JPA y controlador REST.
- **Código ajeno que se toca**: hay que avisar en el PR, según la tabla de dueños de `CHANGES.md`.
  - **Sofia**:
    - `agenda/domain`: `Turno.ausente()`.
    - `agenda/application`: el caso de uso `MarcarAusencia` y la acción `AUSENTE` en `AuditoriaTurnos.Accion`.
    - `agenda/infrastructure`: el método en `ServicioAgenda` y el bean en `AgendaConfig`.
    - Son solo agregados, sin cambiar lo que ya existe.
  - C-08 (`sobreturno-y-extension`, ya mergeado) sumó campos a `Turno`. `Turno.ausente()` tiene que copiarlos, como hace `cancelado()`.
- **Base de datos**: migración `V8`, que borra el `CHECK` de `turno_auditoria` y lo crea de nuevo con la lista completa de siete acciones: las de V4 y V6, `CONFIRMAR` de C-11 y `AUSENTE`. No se edita ninguna migración mergeada: es el mismo procedimiento que usaron V6 y V7. La V7 de C-11 (Juan Cruz) deja la misma lista, así que el orden en que se mergeen C-11 y C-13 no importa.
- **API**: nuevos endpoints bajo `/api/v1/pacientes/{id}/`, consumidos más adelante por el frontend del personal (C-09).
- **Governance ALTO** (datos de salud): el apply arranca después de que Sofia revise y apruebe esta propuesta.
- **Tests**: un test por escenario del spec, con los permisos por rol en integración con PostgreSQL real (`@Tag("integration")`, perfil `test`).
