# Funcionalidades

Están organizadas por **épica** y, dentro de cada una, por **historia de usuario** (US-NNN). Su prioridad sigue la sección D.3 del informe de Discovery.

## Épica 1: Configuración del consultorio (v1)

### US-001: Gestionar profesionales
**Como** administrador **quiero** dar de alta, modificar y desactivar profesionales **para** asignarles turnos.
**Criterios de aceptación**:
- [ ] CA-1: un profesional desactivado no aparece en la disponibilidad ni admite turnos nuevos.
- [ ] CA-2: la matrícula es única.
**Reglas relacionadas**: RN-AG-11

### US-002: Gestionar sillones o boxes
**Como** administrador **quiero** registrar los sillones del consultorio **para** que la agenda controle su ocupación.
**Criterios de aceptación**:
- [ ] CA-1: el nombre del sillón es único.
- [ ] CA-2: un sillón desactivado no admite turnos nuevos.
**Reglas relacionadas**: RN-AG-03, RN-AG-11

### US-003: Gestionar prestaciones con duración
**Como** administrador **quiero** definir prestaciones con su duración en minutos **para** que el fin del turno se calcule solo.
**Criterios de aceptación**:
- [ ] CA-1: la duración es mayor que 0 y múltiplo de 5.
- [ ] CA-2: cambiar la duración no modifica los turnos ya creados.
**Reglas relacionadas**: RN-AG-01

## Épica 2: Agenda y turnos (v1, núcleo)

### US-010: Crear un turno sin solapamientos ⭐ *(candidato al change OPSX)*
**Como** recepcionista **quiero** crear un turno eligiendo paciente, profesional, sillón, prestación e inicio **para** que el sistema me impida superponerlo con otro turno del mismo profesional o del mismo sillón.
**Criterios de aceptación**:
- [ ] CA-1 (feliz): si no hay conflicto, el turno se crea en estado `RESERVADO` con `fin = inicio + duración`.
- [ ] CA-2 (error de negocio): si el profesional tiene un turno activo en conflicto, se rechaza con `SOLAPAMIENTO_PROFESIONAL`.
- [ ] CA-3 (error de negocio): si el sillón tiene un turno activo en conflicto, se rechaza con `SOLAPAMIENTO_SILLON`.
- [ ] CA-4 (borde): con un turno existente de 10:00 a 10:30 que comparte sillón o profesional, un turno a las 10:30 se rechaza (`MARGEN_INSUFICIENTE`) y uno a las 10:40 se acepta.
- [ ] CA-5 (borde): un turno a las 10:30 con otro profesional y otro sillón se acepta.
- [ ] CA-6: los turnos `CANCELADO` no generan conflicto.
- [ ] CA-7 (pantalla del personal) [2026-11-23]: el turno se carga en orden: prestación → profesional habilitado para ella (RN-AG-15) → día en un calendario, desde hoy en adelante → horario libre del profesional ese día. El sillón no se elige: se muestra y se usa el sillón habitual del profesional.
**Reglas relacionadas**: RN-AG-01 a RN-AG-05, RN-AG-09, RN-AG-11, RN-AG-12, RN-AG-15

### US-011: Cancelar un turno y liberar el horario
**Como** recepcionista **quiero** cancelar un turno **para** que el horario quede disponible.
**Criterios de aceptación**:
- [ ] CA-1: el turno pasa a `CANCELADO` y el hueco se puede reservar enseguida.
- [ ] CA-2: queda registrado en la auditoría con el motivo.
**Reglas relacionadas**: RN-AG-08, RN-GL-03

### US-012: Reprogramar un turno
**Como** recepcionista **quiero** mover un turno a otro horario **para** acomodar al paciente sin perder la reserva.
**Criterios de aceptación**:
- [ ] CA-1: el nuevo horario cumple las mismas reglas que un turno nuevo.
- [ ] CA-2: si el nuevo horario está en conflicto, el turno original queda intacto.
**Reglas relacionadas**: RN-AG-08, RN-AG-02 a RN-AG-05

### US-013: Cargar un sobreturno
**Como** odontólogo o recepcionista **quiero** cargar un sobreturno **para** atender una urgencia aunque el horario esté ocupado.
**Criterios de aceptación**:
- [ ] CA-1: solo el personal puede hacerlo; el paciente recibe `403`.
- [ ] CA-2: el motivo es obligatorio.
- [ ] CA-3: puede superponerse con otro turno del mismo profesional, pero necesita un **sillón libre con margen de limpieza**. Si no hay, se rechaza con `SIN_SILLON_DISPONIBLE`.
- [ ] CA-4: el turno queda marcado como sobreturno y auditado.
**Reglas relacionadas**: RN-AG-06, RN-RO-03

### US-014: Ver la agenda diaria y semanal
**Como** odontólogo o recepcionista **quiero** ver la agenda por día y semana, filtrada por profesional o por sillón, **para** saber qué está libre.
**Criterios de aceptación**:
- [ ] CA-1: se distinguen visualmente los sobreturnos y los turnos confirmados.
- [ ] CA-2: el diseño es responsivo y se usa desde el celular.
- [ ] CA-3: el odontólogo ve solo sus turnos y no puede elegir otro profesional; la recepción y el administrador ven los de todos [2026-11-23].
**Reglas relacionadas**: RN-AG-18

### US-016: Configurar los horarios de trabajo de cada profesional
**Como** administrador **quiero** cargar las franjas semanales en que atiende cada profesional **para** que solo se den turnos cuando hay alguien trabajando.
**Criterios de aceptación**:
- [ ] CA-1: un profesional puede tener varias franjas por día, sin que se superpongan entre sí.
- [ ] CA-2: un turno fuera de horario se rechaza con `FUERA_DE_HORARIO`, salvo un sobreturno del personal.
**Reglas relacionadas**: RN-AG-13

### US-017: Registrar ausencias y licencias del profesional
**Como** administrador o recepcionista **quiero** registrar cuándo un profesional falta o está de licencia **para** que no se le asignen turnos en ese período.
**Criterios de aceptación**:
- [ ] CA-1: si ya había turnos dados en el período, el sistema los lista para reasignarlos a otro profesional habilitado o reprogramarlos.
- [ ] CA-2: cada paciente afectado recibe un aviso por su canal.
**Reglas relacionadas**: RN-AG-16, RN-AG-10

### US-018: Configurar especialidades y sillón habitual
**Como** administrador **quiero** indicar qué prestaciones hace cada profesional y cuál es su sillón habitual **para** que la reserva asigne bien.
**Reglas relacionadas**: RN-AG-15, RN-RO-06

### US-019: Extender un turno por complicaciones
**Como** odontólogo **quiero** extender el turno en curso si surge una complicación **para** terminar la atención sin perder el control de la agenda.
**Criterios de aceptación**:
- [ ] CA-1: si la extensión no choca, se registra con su motivo.
- [ ] CA-2: si choca con el turno siguiente, el sistema avisa y ofrece reprogramar el siguiente o registrar la extensión como sobreturno.
**Reglas relacionadas**: RN-AG-14

### US-015: Bloquear horarios
**Como** odontólogo **quiero** bloquear franjas de mi agenda **para** que no me asignen turnos.
**Reglas relacionadas**: RN-AG-10

## Épica 3: Reserva online del paciente (v1)

### US-020: Consultar disponibilidad
**Como** paciente **quiero** ver los horarios libres para una prestación **para** elegir cuándo atenderme.
**Criterios de aceptación**:
- [ ] CA-1: solo se muestran horarios que cumplen las reglas de la agenda.
- [ ] CA-2: no se exponen datos de otros pacientes.
**Reglas relacionadas**: RN-RO-01, RN-RO-02

### US-024: Crear una cuenta de paciente e iniciar sesión
**Como** paciente **quiero** tener una cuenta **para** reservar, ver mis turnos y cancelarlos o reprogramarlos con mi número de reserva.
**Criterios de aceptación**:
- [ ] CA-1: con la cuenta veo **solo mis** turnos y datos.
- [ ] CA-2: para cancelar o reprogramar ingreso el número de reserva, y el sistema verifica que el turno sea mío.
**Reglas relacionadas**: RN-RO-08, RN-AG-07

### US-023: Elegir un profesional o "sin preferencia"
**Como** paciente **quiero** elegir con qué profesional atenderme, o indicar que me da igual, **para** conseguir el horario que más me convenga.
**Criterios de aceptación**:
- [ ] CA-1: con un profesional elegido, veo solo sus horarios libres dentro de su horario de trabajo.
- [ ] CA-2: con "sin preferencia", veo la unión de los horarios libres de todos los profesionales que trabajan ese día, sin horarios repetidos.
- [ ] CA-3: al reservar "sin preferencia", el sistema me asigna un profesional libre y habilitado para la prestación, el que tenga menos turnos ese día (si hay empate, al azar), y me informa cuál es.
- [ ] CA-4: nunca elijo sillón; el sistema asigna el sillón habitual del profesional o, si está ocupado, otro libre.
**Reglas relacionadas**: RN-RO-04 a RN-RO-07, RN-AG-13

### US-021: Reservar un turno online
**Como** paciente **quiero** reservar sin llamar **para** ahorrar tiempo.
**Criterios de aceptación**:
- [ ] CA-1: recibo un código de reserva y un recordatorio por el canal que corresponde.
- [ ] CA-2: si otra persona tomó el horario mientras tanto, recibo un error claro y otras opciones.
**Reglas relacionadas**: RN-AG-12, RN-NO-02

### US-022: Cancelar o reprogramar online
**Como** paciente **quiero** cancelar o reprogramar desde el link **para** liberar el horario.
**Criterios de aceptación**:
- [ ] CA-1: hasta 24 h antes, se permite.
- [ ] CA-2: con menos de 24 h, se rechaza con `CANCELACION_FUERA_DE_PLAZO` y se indica que hay que contactar al consultorio.
**Reglas relacionadas**: RN-AG-07, RN-AG-08

## Épica 4: Recordatorios y confirmación (v1)

### US-030: Recibir un recordatorio
**Como** paciente **quiero** un recordatorio por WhatsApp, o por email si no tengo WhatsApp, **para** no olvidarme del turno.
**Criterios de aceptación**:
- [ ] CA-1: el mensaje no contiene datos de salud.
- [ ] CA-2: solo se envía con consentimiento.
**Reglas relacionadas**: RN-NO-01 a RN-NO-04

### US-031: Confirmar asistencia
**Como** paciente **quiero** confirmar desde el recordatorio **para** que el consultorio sepa que voy.
**Criterios de aceptación**:
- [ ] CA-1: el turno pasa a `CONFIRMADO`.
- [ ] CA-2: el token es de un solo uso.

## Épica 5: Ficha clínica (v1)

### US-040: Registrar la ficha básica
**Como** odontólogo **quiero** registrar antecedentes, alergias y medicación **para** atender de forma segura.
**Reglas relacionadas**: RN-CL-01

### US-041: Registrar el odontograma
**Como** odontólogo **quiero** marcar el estado de cada pieza y cara con notación FDI **para** llevar el historial del paciente.
**Reglas relacionadas**: RN-CL-02, RN-CL-03

### US-042: Marcar la ausencia del paciente
**Como** odontólogo **quiero** marcar desde la ficha clínica que el paciente no vino **para** registrarlo y liberar el tiempo que queda del turno.
**Criterios de aceptación**:
- [ ] CA-1: el turno pasa a `AUSENTE` y el tiempo restante queda libre desde ese momento.
- [ ] CA-2: la ausencia queda en la ficha del paciente.
- [ ] CA-3: no se puede marcar antes de los 15 minutos del inicio ni si el turno tiene la llegada registrada [2026-11-24].
- [ ] CA-4: si la recepción deshizo la ausencia, la ficha lo muestra con la fecha y quién lo hizo [2026-11-24].
**Reglas relacionadas**: RN-AG-17

### US-043: Registrar la llegada del paciente [2026-11-24]
**Como** recepcionista **quiero** marcar en la agenda que el paciente llegó **para** que el turno quede como atendido sin que nadie tenga que acordarse después.
**Criterios de aceptación**:
- [ ] CA-1: "Llegó" se puede usar hasta que el turno termina, aunque el paciente llegue tarde, y el turno sigue ocupando el profesional y el sillón.
- [ ] CA-2: al terminar el horario, el turno con la llegada registrada pasa a `ATENDIDO` sin que nadie lo marque.
- [ ] CA-3: un turno que terminó sin registrar se puede corregir después registrando la llegada o la ausencia.
**Reglas relacionadas**: RN-AG-20

### US-044: Marcar y deshacer la ausencia desde la agenda [2026-11-24]
**Como** recepcionista **quiero** marcar ausente a un paciente que no vino, y deshacerlo si llega tarde, **para** liberar el horario sin perder al paciente que finalmente llegó.
**Criterios de aceptación**:
- [ ] CA-1: "Ausente" se habilita recién 15 minutos después del inicio.
- [ ] CA-2: "Deshacer ausencia" vuelve a activar el turno solo si su horario sigue libre; si se ocupó, se avisa y se le da otro turno al paciente.
**Reglas relacionadas**: RN-AG-17

## Épica 6: Clínica avanzada (post-MVP)

### US-045: Registrar el periodontograma [2026-11-24, C-25 backend y C-46 pantalla]
**Como** odontólogo **quiero** cargar el sondaje periodontal de cada pieza en cada visita **para** seguir la evolución de la enfermedad periodontal del paciente.
**Criterios de aceptación**:
- [ ] CA-1: por cada pieza permanente cargada registro los 6 sitios (profundidad, recesión, sangrado y placa), la movilidad y, si es multirradicular, la furcación; o la marco ausente.
- [ ] CA-2: al guardar se crea una versión nueva con fecha y profesional; las anteriores no cambian y las puedo consultar.
- [ ] CA-3: cada pieza muestra su estado calculado (sano, leve, moderado o severo) a partir del peor sitio.
- [ ] CA-4: un dato fuera de rango, una pieza temporal, una furcación en una pieza que no es multirradicular o una pieza con sitios incompletos se rechazan con un error claro.
- [ ] CA-5: el administrador lo ve sin poder cargarlo, y la recepción no accede.
**Reglas relacionadas**: RN-CL-01, RN-CL-04, RN-CL-05, RN-CL-06

## Épica 7: Coberturas (post-MVP)

### US-046: Registrar obras sociales y la cobertura del paciente [2026-11-24, C-18]
**Como** administrador o recepcionista **quiero** cargar las obras sociales y prepagas con sus planes, y asociar cada paciente a su plan con su número de afiliado, **para** saber con qué cobertura se atiende y preparar los presupuestos (C-19) y las liquidaciones (C-20).
**Criterios de aceptación**:
- [ ] CA-1: como `ADMIN` doy de alta, edito y activo o desactivo obras sociales, planes y códigos del nomenclador. No se pueden borrar.
- [ ] CA-2: como `ADMIN` o `RECEPCIONISTA` asocio un paciente a un plan activo con su número de afiliado y su vigencia. El odontólogo la ve.
- [ ] CA-3: un paciente con una afiliación vigente muestra su obra social, su plan y su número. Uno sin afiliación vigente figura como particular.
- [ ] CA-4: se rechazan con un error claro una vigencia que se superpone con otra del mismo paciente, un número de afiliado repetido en la misma obra social y un plan inactivo.
- [ ] CA-5: la cobertura no aparece en recordatorios, mensajes ni logs.
- [ ] CA-6: como paciente veo en "Mi cuenta" mi obra social, mi plan, mi número de afiliado y la vigencia, o que me atiendo como particular. No puedo cambiarla ni ver la de otro paciente (PA-21).
**Reglas relacionadas**: RN-OS-01 a RN-OS-07

### US-047: Armar un presupuesto con cobertura y copago [2026-11-24, C-19]
**Como** odontólogo **quiero** armar el presupuesto del paciente con las prestaciones de su tratamiento, **para** que sepa cuánto cubre su obra social y cuánto paga él.
**Criterios de aceptación**:
- [ ] CA-1: como `ADMIN` cargo el precio particular de cada prestación y el porcentaje que cubre cada plan por código del nomenclador.
- [ ] CA-2: al armar el presupuesto veo sugeridas las piezas y caras del odontograma marcadas `A_TRATAR` o `CARIES`, y elijo la prestación de cada una.
- [ ] CA-3: cada ítem muestra el importe, la cobertura y el copago, y el presupuesto muestra los totales y la cobertura que se usó, o "Particular".
- [ ] CA-4: un paciente sin cobertura vigente paga el importe completo como copago.
- [ ] CA-5: si después cambian los precios o la cobertura del plan, el presupuesto emitido no cambia.
- [ ] CA-6: el presupuesto pasa de pendiente a aceptado o rechazado, y no se borra.
- [ ] CA-7: el presupuesto vence a los 30 días; uno vencido ya no se puede aceptar.
- [ ] CA-8: la recepción ve los presupuestos sin pieza ni cara, y el paciente ve los suyos en "Mi cuenta".
**Reglas relacionadas**: RN-PR-01 a RN-PR-07, RN-OS-04

## Backlog: v1.x y posteriores (fuera del MVP)

> Cada fila se vincula con su change de `CHANGES.md`. Las reglas detalladas se escriben en esta KB **antes de empezar** el change, cuando el MVP esté completo.

| ID | Funcionalidad | Etapa | Change | Origen |
|---|---|---|---|---|
| US-100 | Lista de espera automática al liberarse un horario | v1.x | C-15 | Vacío del mercado: 0 de 16 sistemas (informe, C.3) |
| US-101 | Política de cancelación configurable por consultorio | v1.x | C-16 | Informe, C.4 |
| US-102 | Registro de consentimiento para comunicaciones con una pantalla propia | v1.x | C-17 | Informe, D.3 |
| US-200 | Obras sociales y prepagas (planes, nomencladores, liquidaciones; el catálogo y la afiliación pasaron a US-046 el 2026-11-24) | Posterior | C-18, C-20 | Informe, D.3 |
| US-205 | Presupuestos con cobertura y copago (pasó a US-047 el 2026-11-24) | Posterior | C-19 | Informe, A.3 |
| US-206 | Validación online de la cobertura con la obra social | Posterior | C-21 | Informe, C.2 (DentalTec) |
| US-207 | Caja diaria, cobros y cuenta corriente del paciente | Posterior | C-22 | Roadmap (Ajustar de la líder, 2026-11-21); base de C-23 y C-24 |
| US-202 | Señas con Mercado Pago | Posterior | C-23 | Informe, D.3 |
| US-201 | Facturación electrónica ARCA | Posterior | C-24 | Informe, D.3 |
| US-203 | Imágenes y radiografías (el periodontograma pasó a US-045 el 2026-11-24) | Posterior | C-26 | Informe, D.3 |
| US-208 | Consentimiento informado con firma electrónica | Posterior | C-27 | Informe, D.3; relevamiento: Bilog (Ley 25.506) |
| US-204 | Asistente con IA, multisucursal, exportación HL7 FHIR | Posterior | C-28, C-29, C-31 | Informe, D.3 |
| US-209 | Sincronización de la agenda del profesional con Google Calendar | Posterior | C-30 | Roadmap (Ajustar de la líder, 2026-11-21) |
| US-210 | Reportes de ocupación, ausencias y cancelaciones | Posterior | C-32 | `01_vision_y_objetivos.md` §Métricas de éxito |
