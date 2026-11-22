# Proposal

## Why

RN-AG-15 dice que un turno solo puede asignarse a un profesional **habilitado para la prestación**. Hoy solo lo cumplen la reserva online (C-10) y la reasignación por ausencia del profesional (C-07). Cuando el personal **crea** un turno, lo **reprograma** a otro profesional o carga un **sobreturno**, la agenda no lo controla: la recepción puede darle una endodoncia a quien no la hace. El hueco quedó a la vista al revisar C-07. Conviene cerrarlo antes de la entrega (C-14), porque es una regla del corazón de la agenda.

## What Changes

- **Crear turno** (`CrearTurno`): rechaza con `PROFESIONAL_NO_HABILITADO` si el profesional no está habilitado para la prestación. Como la reserva online usa este mismo caso de uso, gana la misma garantía en el servidor.
- **Reprogramar** (`ReprogramarTurno`): rechaza con `PROFESIONAL_NO_HABILITADO` si el turno pasa a **otro** profesional no habilitado. El turno original queda intacto. Si se conserva el mismo profesional, no se vuelve a exigir: el turno ya estaba dado. Es el mismo criterio que usa hoy la reasignación (C-07).
- **Sobreturno** (`CrearSobreturno`): rechaza con `PROFESIONAL_NO_HABILITADO`. RN-AG-06 exceptúa al sobreturno de la agenda del profesional, no de sus especialidades.
- **Extender un turno**: no cambia. Conserva el profesional y la prestación del turno ya dado.
- **Prioridad del error**: `PROFESIONAL_NO_HABILITADO` va después de los controles de pasado y de recursos inexistentes o inactivos, y antes de ausencia, bloqueo, horario y solapamientos.
- **API**: `POST /api/v1/turnos`, `POST /api/v1/turnos/sobreturno` y la reprogramación responden `409` con el código `PROFESIONAL_NO_HABILITADO`, que ya existe en `CodigoRechazo` desde C-07. No es un cambio que rompa clientes: es un rechazo nuevo con un código estable.
- Sin migración: la tabla `profesional_prestacion` existe desde C-04 (V2).

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `agenda-turnos`: nuevo requisito de profesional habilitado al crear, reprogramar y cargar un sobreturno; la prioridad fija de errores suma `PROFESIONAL_NO_HABILITADO`.
- `agenda-api`: los rechazos `409` documentan el código `PROFESIONAL_NO_HABILITADO`, con su escenario por la API.

## Impact

- **Código (módulo `agenda/`, dueña Sofia)**:
  - `CrearTurno`, `CrearSobreturno` y `ReprogramarTurno`;
  - el cableado en `AgendaConfig`.
- **Puerto reutilizado, sin cambios**: `HabilitacionProfesional` de `configuracion/application` (C-04, Emanuel), con su adaptador JPA `HabilitacionProfesionalJpa`.
- **Sin cambios en** `ReasignarTurno` (C-07, Emanuel): ya controla la habilitación por su cuenta.
- **Tests**:
  - unitarios nuevos por escenario;
  - fixtures de integración que crean turnos sin cargar `profesional_prestacion`. Los propios están en `agenda/IntegracionAgendaBase`; los ajenos, en `clinica/IntegracionClinicaBase` (Emanuel), `shared/security/IntegracionSeguridadBase` (Juan Sebastián) y posiblemente `notificaciones` (Juan Cruz). Hay que avisar a sus dueños.
- **Frontend (Enzo)**: `MENSAJES_POR_CODIGO` no tiene texto para `PROFESIONAL_NO_HABILITADO`, así que hoy mostraría el mensaje genérico. Queda fuera de este change y se le avisa a Enzo.
- **Fuera de alcance**: la UI para gestionar especialidades, que es de C-04, y revalidar los turnos ya dados si un profesional pierde una habilitación.
