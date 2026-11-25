# Flujos Principales

## Flujo 1: Crear un turno (personal) ⭐ núcleo del MVP

**Disparador**: la recepcionista carga un turno en el panel.
**Actor**: recepcionista u odontólogo.

**Pasos**:
1. **Frontend** envía `POST /api/v1/turnos` con `pacienteId`, `profesionalId`, `sillonId`, `prestacionId` e `inicio`.
2. **API** valida la entrada (campos obligatorios, formato de fecha) y la sesión y el rol.
3. **Caso de uso `CrearTurno`** carga la prestación y calcula `fin = inicio + duración` (RN-AG-01).
4. Verifica que el inicio no sea pasado (RN-AG-09) y que los recursos estén activos (RN-AG-11).
5. Consulta los **turnos activos** del profesional y del sillón en la ventana `[inicio − duraciónMáx − MARGEN, fin + MARGEN]`.
6. **Dominio `Agenda`** evalúa el conflicto con margen (RN-AG-02 a RN-AG-05) y los bloqueos (RN-AG-10).
7. Si no hay conflicto, **persiste** el turno en una transacción. La **restricción de exclusión** de PostgreSQL repite la verificación (RN-AG-12).
8. Registra la **auditoría** y programa el **recordatorio**.
9. Responde `201 Created` con el turno.

```
Recepción → Frontend → API → CrearTurno → Agenda(dominio) → TurnoRepository → PostgreSQL
                                   │            ↑ conflicto?          │ EXCLUDE (garantía final)
                                   └────────────┴─ 409 si hay choque ←─┘
```

**Casos de error**:
- Choque con un turno del mismo profesional → `409 SOLAPAMIENTO_PROFESIONAL`.
- Choque con un turno del mismo sillón → `409 SOLAPAMIENTO_SILLON`.
- Contiguo sin los 10 minutos de margen → `409 MARGEN_INSUFICIENTE`.
- Dos reservas concurrentes en el mismo hueco: una gana y la otra falla por la restricción de exclusión, que se traduce a `409`.
- Inicio en el pasado o recurso inactivo → `422`.

## Flujo 2: Reserva online del paciente

**Disparador**: el paciente entra a la página pública de reserva.
**Actor**: paciente.

**Pasos**:
1. Elige una prestación, una fecha y **un profesional en particular o "sin preferencia"** (RN-RO-04).
2. `GET /public/disponibilidad` calcula los huecos libres con las mismas reglas que el Flujo 1, más el horario de trabajo (RN-AG-13):
   - con profesional: los huecos de ese profesional;
   - sin preferencia: la unión de los huecos de todos los profesionales que trabajan ese día, sin repetir horarios.
   Un horario aparece solo si hay al menos un par de profesional y sillón libre (RN-RO-07). Se devuelven solo horarios, sin datos de otros pacientes.
3. El paciente elige un horario y completa sus datos (DNI, nombre, contacto, canal, consentimiento).
4. Con su sesión de paciente iniciada (RN-RO-08), `POST /paciente/reservas` **asigna el profesional** si eligió "sin preferencia" (habilitado para la prestación, con menos turnos ese día y, si hay empate, al azar; RN-RO-05) y **el sillón** (primero el habitual; RN-RO-06), y ejecuta el mismo caso de uso que el Flujo 1, con `origen = ONLINE` y sin opción de sobreturno.
5. Se devuelve el **código de reserva** y se envía la confirmación por el canal que corresponde.

**Casos de error**:
- El horario se ocupó entre la consulta y la reserva → `409` con alternativas. Si era "sin preferencia", se intenta primero con otro profesional o sillón libre en ese mismo horario.
- No queda ningún profesional o sillón libre → `409 SIN_DISPONIBILIDAD`.
- Datos de contacto faltantes → `422`.

## Flujo 3: Cancelar o reprogramar

**Disparador**: el paciente (desde el link o con el código) o el personal (desde el panel).

**Pasos**:
1. Se identifica el turno:
   - **paciente online**: con su sesión iniciada, ingresa el **número de reserva**; el sistema verifica que el turno sea suyo;
   - **por el consultorio**: la recepcionista valida el **DNI** del paciente y el **número de reserva (token)** antes de operar.
2. Si el actor es el paciente online: se verifica que falten **24 h o más** para el inicio (RN-AG-07).
3. **Cancelar**: el estado pasa a `CANCELADO` y el hueco queda libre (RN-AG-08).
4. **Reprogramar**: en una transacción se crea el turno nuevo con las reglas del Flujo 1 y se cancela el original. Si el nuevo falla, el original no cambia.
5. Se registra la auditoría y se notifica al paciente.

**Casos de error**:
- Paciente con menos de 24 h de anticipación → `409 CANCELACION_FUERA_DE_PLAZO`.
- Nuevo horario en conflicto → `409` (el original queda intacto).

## Flujo 4: Recordatorio y confirmación

**Disparador**: una tarea programada (`@Scheduled`) cada N minutos.

**Pasos**:
1. Busca los turnos activos (`RESERVADO` o `CONFIRMADO`) que empiezan dentro de **24 h** y todavía no tienen recordatorio enviado (RN-NO-04).
2. Filtra los pacientes con consentimiento (RN-NO-03).
3. Elige el canal: WhatsApp si `tiene_whatsapp`; si no, email (RN-NO-02).
4. Arma el mensaje **sin datos de salud** (RN-NO-01), con el link de confirmar, cancelar o reprogramar.
5. Lo envía por `NotificacionPort`. En el TP, el adaptador es simulado.
6. Cuando el paciente confirma (link o webhook), el turno pasa a `CONFIRMADO`.

**Casos de error**:
- Falla del proveedor → `Notificacion.estado = FALLIDA` y reintento con backoff; después de N fallas, se envía por email si hay email.

## Flujo 5: Lista de espera [2026-11-24, C-15]

**Disparador**: se cancela o se reprograma un turno activo que todavía no empezó (RN-LE-02).

**Pasos**:
1. Después de guardar la cancelación, el sistema busca el **primer** paciente compatible de la lista por orden de llegada (RN-LE-03, RN-LE-04).
2. Crea la oferta con su vencimiento: el plazo configurado o el inicio del horario, lo que pase primero (RN-LE-05).
3. Le manda el mensaje por su canal, sin datos de salud, con el link de la oferta (RN-LE-08).
4. El paciente abre el link: ve la fecha, la hora y el profesional, y elige **Aceptar** o **No me interesa** (RN-LE-07).
5. **Aceptar**: en una transacción se valida el turno como en el Flujo 1 y se crea `RESERVADO`; la inscripción pasa a `ASIGNADA`.
6. **No me interesa** o vencimiento (tarea programada): se vuelve al paso 1 con el siguiente paciente.

**Casos de error**:
- El horario se ocupó durante la oferta → `409 OFERTA_NO_DISPONIBLE`; la inscripción sigue activa (RN-LE-06).
- Oferta vencida → `409 OFERTA_VENCIDA`; ya respondida → `409 OFERTA_YA_RESPONDIDA`; token inexistente → `404 OFERTA_INEXISTENTE`.
