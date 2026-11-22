# Proposal

## Why

Hoy la agenda acepta un turno a cualquier hora de cualquier día, aunque el profesional no trabaje en ese horario, esté de licencia o haya bloqueado su agenda. C-04 ya dejó cargados los profesionales y C-06 la API de turnos. Falta que el consultorio defina cuándo atiende cada profesional y cuándo no, y que la agenda lo respete (RN-AG-10, RN-AG-13, RN-AG-16, DD-08). Esto es prerrequisito de C-10: la reserva online solo puede ofrecer horarios dentro de las franjas de trabajo (RN-RO-02).

## What Changes

- **Horarios de trabajo**: `GET` y `PUT /api/v1/profesionales/{id}/horarios` (US-016). Son franjas semanales por día de la semana; un día puede tener varias franjas (por ejemplo, de 8 a 12 y de 16 a 20), y no se superponen. El `PUT` reemplaza el conjunto completo.
- **Ausencias y licencias**: `POST`, `GET` y `DELETE /api/v1/profesionales/{id}/ausencias` (US-017, RN-AG-16). Tienen tipo `AUSENCIA` o `LICENCIA`, inicio, fin y motivo opcional. Al registrarlas, la respuesta lista los turnos activos del profesional en ese período.
- **Reasignar un turno afectado**: `POST /api/v1/profesionales/{id}/ausencias/{ausenciaId}/turnos/{turnoId}/reasignar` (RN-AG-16). Lo hacen el `ADMIN` o la recepción.
  - **Destino**: otro profesional habilitado en el mismo horario, u otro horario.
  - **Sin DNI ni número de reserva**: los exige el reprogramar de C-06 (RN-AG-07) porque lo pide el paciente. Acá la reasignación la inicia el consultorio.
  - **Reglas**: valida la habilitación del profesional (RN-AG-15, `PROFESIONAL_NO_HABILITADO`) y todas las reglas de la agenda.
  - **Resultado**: cancela el turno original y crea el nuevo en una sola operación.
  - **Aviso al paciente**: después, lo pide con el aviso de reasignación de C-11 (`notificaciones-recordatorios`). Si el aviso falla, la reasignación queda hecha.
- **Bloqueos de agenda**: `POST`, `GET` y `DELETE /api/v1/profesionales/{id}/bloqueos` (US-015, RN-AG-10), con inicio, fin y motivo opcional. Como las ausencias, se registran aunque haya turnos dados y la respuesta lista los afectados.
- **Reglas nuevas de la agenda**, como validadores aparte en `agenda/domain/reglas/`:
  - **`FUERA_DE_HORARIO`** (RN-AG-13): el turno tiene que quedar completo dentro de una franja de ese día de la semana, en la zona `America/Argentina/Buenos_Aires`. **Un profesional sin franjas no recibe turnos** (decisión del dueño del change).
  - **`PROFESIONAL_AUSENTE`** (RN-AG-16): el turno no puede cruzarse con una ausencia o licencia del profesional.
  - **`AGENDA_BLOQUEADA`** (RN-AG-10): el turno no puede cruzarse con un bloqueo del profesional.
  - Se aplican al crear y al reprogramar un turno. El sobreturno y la extensión siguen pudiendo quedar fuera de horario (RN-AG-06 y RN-AG-14), pero **sí los frenan las ausencias y los bloqueos** (decisión del dueño del change).
  - Se responden como `409` con su código y entran en la prioridad fija de errores, antes de los solapamientos.
- **Permisos** según la matriz RBAC:
  - **Horarios**: el `ADMIN` los modifica. Los leen la recepción y el odontólogo, este último solo los suyos.
  - **Ausencias**: las registran y borran el `ADMIN` y la recepción. El odontólogo lee solo las suyas.
  - **Bloqueos**: el `ADMIN` los gestiona para cualquier profesional y el odontólogo solo para el suyo. La recepción no accede.
- **Migración V5** (número reservado para Emanuel): tablas `horario_trabajo`, `ausencia_profesional` y `bloqueo_agenda`. Las franjas superpuestas se impiden también en la base.
- **Seed de dev `V5_1`**, con los horarios ficticios de la KB: la Dra. Ana Ejemplo trabaja de lunes a viernes de 8 a 13 y el Dr. Bruno Prueba de lunes a viernes de 10 a 18.

**Fuera de alcance**:
- La disponibilidad para el paciente (huecos libres): es C-10, que va a reutilizar estas reglas.
- La reasignación automática de los turnos afectados: la recepción elige destino turno por turno.
- El contenido, el canal y el consentimiento del aviso al paciente: los define C-11. C-07 solo lo pide.
- La validación de habilitación (RN-AG-15) al crear o reprogramar turnos en general: C-07 la aplica solo en la reasignación.
- Bloqueos por sillón.
- Horarios con excepciones por fecha (feriados): se cubren con un bloqueo o una ausencia.
- Pantallas (C-09).

## Capabilities

### New Capabilities
- `disponibilidad-profesional`: horarios de trabajo, ausencias, licencias y bloqueos de cada profesional, con sus permisos, el listado de los turnos afectados al registrar un período sin atención, y la reasignación de esos turnos con el aviso al paciente.

### Modified Capabilities
- `agenda-turnos`: se agregan los requisitos de turno dentro del horario de trabajo y sin cruzarse con ausencias ni bloqueos, la extensión de la prioridad fija de errores y cómo afectan al sobreturno y a la extensión.

## Impact

- **Código nuevo (Emanuel)**:
  - `agenda/domain/reglas/`, con las reglas de horario, ausencia y bloqueo, sin Spring.
  - `agenda/disponibilidad/`, con los casos de uso, los adaptadores JPA y el controlador.
  - Las migraciones `V5` y `V5_1`.
- **Código ajeno que se toca**: hay que avisar en el PR.
  - **Sofia**:
    - `CrearTurno` llama a las reglas nuevas antes de verificar los conflictos. CHANGES.md pide que la líder revise cualquier cambio en `CrearTurno`.
    - `CodigoRechazo` suma tres códigos.
    - `AgendaConfig` cablea el puerto.
    - En `IntegracionAgendaBase` se cargan franjas amplias para los profesionales de los tests. Sin eso, sus tests de crear y reprogramar fallarían con `FUERA_DE_HORARIO`.
    - En `agenda/application` se suma el caso de uso `ReasignarTurno`. Reutiliza `CrearTurno.preparar` como `ReprogramarTurno`, pero sin validar DNI y número de reserva. Se suma también su método en `ServicioAgenda`.
  - **Juan Cruz**:
    - `CrearSobreturno` llama a las reglas de ausencia y bloqueo, pero no a la de horario.
    - Su módulo `notificaciones` implementa el puerto `AvisoReasignacion` que define C-07, delegando en `ServicioNotificaciones.avisarReasignacion`. `agenda` no depende de `notificaciones`. Así se acordó con Juan Cruz.
- **Dependencia con C-11**: ninguno de los dos changes bloquea al otro.
  - C-07 trae un adaptador inactivo que Spring reemplaza solo cuando existe el de `notificaciones`.
  - El adaptador real lo escribe el que se mergee segundo.
  - Los tests de C-07 usan un doble del puerto.
- **Usa C-04**: el puerto `HabilitacionProfesional` para RN-AG-15.
  - Se mantienen los constructores actuales de los casos de uso, sin reglas, para que los tests unitarios de C-01, C-06 y C-08 no cambien.
- **API**: nuevos endpoints bajo `/api/v1/profesionales/{id}/`, consumidos por C-09 y C-10. Hay códigos `409` nuevos en `/api/v1/turnos`, y `TURNO_NO_AFECTADO` y `PROFESIONAL_NO_HABILITADO` en la reasignación.
- **Comportamiento visible**: después del merge, en una base sin horarios cargados no se pueden crear turnos. En dev, el seed `V5_1` los carga.
- **Tests**: un test por escenario del spec. Las reglas tienen tests unitarios y la API, tests de integración con PostgreSQL real.
