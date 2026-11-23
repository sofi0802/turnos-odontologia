# Design

## Context

- **Hoy no existe nada de notificaciones.** `backend/.../` tiene `agenda`, `shared` y `usuarios`. La migración más alta en `master` es V4; V6 (C-08) ya está mergeado y V7 está reservada para este change.
- **`paciente` no tiene datos de comunicación**: solo `nombre`, `apellido`, `dni`, `email` y `telefono` (V1). Faltan `tiene_whatsapp` y el consentimiento (RN-NO-02, RN-NO-03).
- **`turno` no guarda cuándo se reservó.** El único registro de ese momento es `turno_auditoria.fecha` de la acción `CREAR` (V4).
- **`/api/v1/public/**` ya es `permitAll`** en `SecurityConfig`, con CSRF activo. No hace falta tocar `shared/security`.
- **Hay un `Clock` único** (`AgendaConfig.reloj`, zona Buenos Aires) que sirve para fijar el tiempo en los tests.
- **El `CHECK` de `turno_auditoria.accion` lo reescriben V6 (C-08) y V7**: los dos lo dejan con la lista de acciones que conocen.
- Motivación y alcance: ver `proposal.md`. Comportamiento exigido: ver los specs del change.

## Goals / Non-Goals

**Goals:**
- Que el módulo `notificaciones` sea propio, con dominio y aplicación sin Spring, y que toque lo menos posible los módulos de Sofía.
- Que no se pueda enviar dos veces el mismo recordatorio, ni siquiera con dos corridas simultáneas de la tarea.
- Que ningún dato de salud ni dato de contacto completo llegue al mensaje, al log, a la auditoría ni a la respuesta de la API.

**Non-Goals:**
- Enviar de verdad por WhatsApp o SMTP (DD-04).
- Gestionar el consentimiento (C-17) ni conectar las ausencias (C-07).
- Soportar varias instancias del servidor corriendo la tarea a la vez (DD-05: un consultorio por despliegue).

## Decisions

### D1. Módulo `notificaciones` en capas, con el mismo estilo que `agenda`
`domain` (notificación, canal, estados, mensaje, política de reintentos) y `application` (casos de uso y puertos) sin imports de Spring; `infrastructure` (JPA, adaptadores simulados, tarea programada) y `api` (un controller). Se verifica con `grep` como en C-08.

*Alternativa descartada*: poner todo dentro de `agenda`. Mezclaría un módulo de Juan Cruz con uno de Sofía y haría crecer el área que requiere aprobación.

### D2. La consulta de turnos a recordar vive en `notificaciones/infrastructure`
Un puerto `TurnosARecordar` en `notificaciones/application` se implementa con una consulta nativa propia sobre `turno`, `paciente`, `profesional` y `turno_auditoria`. Devuelve un registro de lectura con lo estrictamente necesario para el mensaje (nombre, inicio, nombre del profesional, contacto y canal), nunca la entidad.

Condiciones: turno `RESERVADO` o `CONFIRMADO`; `inicio` entre ahora y ahora + 24 h; consentimiento verdadero; sin una `notificacion` de tipo `RECORDATORIO`; y `inicio - primera fecha de CREAR/SOBRETURNO` ≥ 24 h.

*Alternativa descartada*: agregar métodos a `TurnoJpaRepository` (módulo de Sofía). La consulta propia evita editar `agenda/infrastructure` solo para leer.

*Trade-off*: un turno sin registro de auditoría (anterior a V4) se trata como reservado con tiempo, porque se creó mucho antes. Es la opción que menos silencia recordatorios; ver Riesgos.

### D3. Confirmar toca a `agenda`, y se mantiene mínimo
Hay que cambiar el estado de un turno, y eso es dominio de Sofía. Se agregan solo cuatro piezas, todas aditivas:
- `Turno.confirmado()` y el caso de uso `ConfirmarTurno` en `agenda/domain` y `agenda/application`: pasa `RESERVADO` a `CONFIRMADO` y rechaza cualquier otro estado.
- La acción `CONFIRMAR` en `AuditoriaTurnos.Accion`.
- `ServicioAgenda.confirmar(turnoId, actor)` en `agenda/infrastructure`, con la misma transacción y auditoría que el resto.

`notificaciones/application` no conoce a `agenda`: usa un puerto `ConfirmadorDeTurnos` que implementa un adaptador de `notificaciones/infrastructure` llamando a `ServicioAgenda.confirmar`. Todo cambio en archivos de Sofía va listado en el PR para su aprobación (regla dura 3).

### D4. El token: 256 bits aleatorios, guardado como huella, un solo uso atómico
Se genera con `SecureRandom` (32 bytes, base64url) y en la base se guarda su SHA-256 hexadecimal en `token_confirmacion` (único). El link lleva el token en claro; la base nunca lo tiene.

El consumo es atómico: `UPDATE notificacion SET usado_en = :ahora WHERE token_confirmacion = :huella AND usado_en IS NULL`. Si no afecta ninguna fila, se distingue `TOKEN_INEXISTENTE` de `TOKEN_USADO` con una lectura posterior. El consumo, el cambio de estado del turno y la auditoría ocurren en **una sola transacción**: si el turno no se puede confirmar, el token **no** se consume.

*Alternativa descartada*: JWT firmado como token. Prohibido por la regla dura 8 y no se podría invalidar.
*Alternativa descartada*: confirmar con `GET`. Los escáneres de links de WhatsApp y de email los abren sin que el paciente haga nada, y consumirían el token. Por eso el link apunta a una pantalla del frontend (de Enzo, fuera de este change) que hace el `POST`.

### D5. Tabla `notificacion` y no duplicación a nivel de base
`notificacion`: `id`, `turno_id` (FK), `canal`, `tipo`, `estado`, `programada_para`, `enviada_en`, `token_confirmacion` (nullable, único), `usado_en`, `intentos`, `proximo_intento`, `canal_alternativo` (boolean).

- **`tipo`** acepta `RECORDATORIO`, `CONFIRMACION`, `CANCELACION` (los de la KB) y `REASIGNACION` (nuevo, para RN-AG-16). Es un desvío respecto de `04_modelo_de_datos.md`; se deja anotado para que la líder lo apruebe.
- **Índice único parcial** sobre `(turno_id)` donde `tipo = 'RECORDATORIO'`: aunque dos corridas de la tarea elijan el mismo turno, solo una inserta. Es la garantía de base, igual que los `EXCLUDE` en la agenda; el filtro de la consulta es la validación previa.
- El flujo es insertar la fila `PENDIENTE` (la unicidad decide quién gana), enviar, y pasar a `ENVIADA` o `FALLIDA`.

### D6. Reintentos y respaldo por email sobre la misma fila
Backoff de 5, 10 y 20 minutos, hasta 3 intentos por canal. Después de la tercera falla por WhatsApp, si el paciente tiene email, **la misma fila** pasa a `canal = EMAIL`, `canal_alternativo = true` e `intentos = 0`; después de 3 fallas por email, o de 3 por WhatsApp sin email, queda `FALLIDA` con `proximo_intento` nulo.

*Alternativa descartada*: una fila nueva para el email. Chocaría con el índice único de D5 y complicaría la consulta de lo pendiente.

### D7. La tarea programada es una capa fina
`@Scheduled(fixedDelayString = ...)` cada 5 minutos (configurable) llama a un caso de uso (`EnviarRecordatorios.ejecutar()`), que es lo que se prueba con un `Clock` fijo. La tarea se apaga con `app.notificaciones.activo=false`, y los tests de integración la apagan y llaman al caso de uso a mano, así no dependen del tiempo real ni de otra corrida.

### D8. Mensaje sin datos de salud, armado en el dominio
Un objeto de dominio (`MensajeRecordatorio`) arma el texto desde un registro que **no tiene** campo para la prestación ni el motivo: lo que no existe en el dato de entrada no puede filtrarse. Usa fecha y hora en `America/Argentina/Buenos_Aires`. El nombre y la dirección del consultorio salen de `CONSULTORIO_NOMBRE` y `CONSULTORIO_DIRECCION`.

### D9. Adaptadores simulados que no exponen nada
`WhatsAppAdapter` y `EmailAdapter` registran una línea con canal, id de notificación y resultado, y nada más (ni cuerpo, ni token, ni contacto). Se prueba capturando el log. Un adaptador se puede configurar para fallar (propiedad de prueba), de modo que los reintentos tengan un camino real de integración sin un proveedor.

### D10. Migración V7, hecha para convivir con V6
`V7__notificaciones.sql` crea `notificacion` con sus índices, agrega a `paciente` `tiene_whatsapp` (`BOOLEAN NOT NULL DEFAULT FALSE`), `consentimiento_comunicaciones` (`BOOLEAN NOT NULL DEFAULT FALSE`) y `consentimiento_fecha` (`TIMESTAMPTZ`), y reescribe el `CHECK` de `turno_auditoria.accion` con `DROP CONSTRAINT IF EXISTS` y las siete acciones (`CREAR`, `CANCELAR`, `REPROGRAMAR`, `SOBRETURNO`, `EXTENDER`, `CONFIRMAR`, `AUSENTE`; esta última la usa C-13). Los valores por defecto hacen que nadie reciba mensajes hasta que dé su consentimiento.

`notificaciones` lee esas columnas con su propia consulta, por lo que `PacienteEntity` y `usuarios` no cambian.

**Orden de merge**: C-08 (V6) ya está en `master`. V7 y el V8 de C-13 dejan el `CHECK` con las mismas siete acciones, así que no importa cuál de los dos se mergee primero.

### D11. Aviso de reasignación detrás de un caso de uso
`AvisarReasignacion.ejecutar(turnoId)` crea una notificación `REASIGNACION` con las mismas reglas de consentimiento, canal y contenido, leyendo el turno ya reasignado. Sin token y sin link. El punto de entrada para otros módulos es `ServicioNotificaciones.avisarReasignacion(turnoId)`, que devuelve vacío si el paciente no puede recibir comunicaciones. Quien llama se define con C-07: la propuesta es que C-07 agregue la operación "reasignar turno afectado" y que, después de guardar el turno con su nueva fecha, hora y profesional, llame a este aviso a través de un puerto propio de `agenda`, implementado por un adaptador de `notificaciones`, para que `agenda` no dependa de `notificaciones/infrastructure`. Acá se prueba con un doble.

### D12. Avisos de la reserva online: adaptador de `AvisoReservaOnline`
`AvisoReservaOnlineNotificaciones` (en `notificaciones/infrastructure`) implementa el puerto de C-10 y delega en `ServicioNotificaciones`. Mapeo de tipos: `reservado` y `reprogramado` (turno nuevo) crean una notificación `CONFIRMACION`; `cancelado` crea una `CANCELACION`. No llevan token de confirmación (la reserva no es el recordatorio): `token_confirmacion` queda nulo.

El envío es inmediato. `AvisoSeguro` (C-10) ya captura cualquier excepción del adaptador, y una falla del proveedor deja la fila `FALLIDA` para la política de reintentos de D6. Las plantillas nuevas llevan solo fecha, hora, consultorio y, según el tipo, los links.

**Links**: se arman con `NOTIFICACIONES_BASE_URL` y dos rutas configurables (`NOTIFICACIONES_RUTA_CANCELAR`, por defecto `/mis-turnos/cancelar`, y `NOTIFICACIONES_RUTA_REPROGRAMAR`, por defecto `/mis-turnos/reprogramar`). **Nunca** incluyen el número de reserva, el DNI ni el id del turno: el número de reserva es la credencial para cancelar o reprogramar (RN-AG-07, `POST /api/v1/paciente/reservas/{numeroReserva}/cancelar`), así que el paciente inicia sesión y lo ingresa en la pantalla. Las rutas reales las define Enzo en C-12; el default es una propuesta.

El recordatorio suma los mismos dos links, por RN-NO-01.

*Alternativa descartada*: links con el número de reserva incrustado. Cualquiera que vea el mensaje podría cancelar el turno.
*Alternativa descartada*: que `reserva` conozca a `notificaciones`. Rompería el desacople que C-07 y C-10 definieron.

## Risks / Trade-offs

- **[Recordatorios a turnos anteriores a la auditoría]** Un turno sin registro `CREAR` se trata como reservado con tiempo, y podría recibir un recordatorio de más. → En la práctica no hay datos reales anteriores a V4 (solo seed ficticio); se documenta y se cubre con un test.
- **[Un link de confirmación reenviado]** Quien tenga el link puede confirmar el turno. → Es el mismo nivel de seguridad que el correo del paciente; el token es de 256 bits, de un solo uso, y confirmar no expone datos (solo fecha, hora y profesional).
- **[Tareas simultáneas]** Dos instancias del servidor duplicarían trabajo. → El índice único evita el doble envío; no se soporta más de una instancia en v1 (DD-05).
- **[Recordatorio de madrugada]** Un turno a las 07:30 se recuerda a las 07:30 del día anterior; uno a las 02:00 de madrugada no es realista. → Se acepta en v1; ventanas horarias de envío quedan para después.
- **[Recuperación "al menos una vez"]** Una notificación `PENDIENTE` con más de 10 minutos se reprocesa, y cada intento de un recordatorio genera un token nuevo (el anterior no se guarda en claro y no se puede recuperar). Si el primer intento sí llegó a salir y solo se cortó el registro, el paciente puede recibir dos mensajes, y el link viejo queda invalidado: solo sirve el último. → Se prefiere repetir un mensaje a perder un recordatorio. Una excepción sobre una fila ya reclamada cuenta como intento fallido (tope de 3), así una fila "venenosa" no se reprocesa para siempre.
- **[Plazo de 10 minutos]** Evita que dos corridas reclamen la misma fila solo si un envío termina antes de ese plazo. → El proveedor real (C-posterior a DD-04) debe tener un *timeout* de envío bien menor a 10 minutos; con los adaptadores simulados no hay espera.
- **[Rutas de los links sin pantalla todavía]** Hasta que C-12 las implemente, los links de cancelar y reprogramar apuntan a una ruta que el frontend aún no tiene. → Son configurables y quedan anotadas para Enzo; no afectan al backend.
- **[Cambio del `CHECK` compartido]** V6 y V7 tocan la misma restricción. → V7 es idempotente y el orden de merge queda escrito; un test de migración comprueba las siete acciones.

## Migration Plan

1. Mergear C-08 (V6).
2. Mergear este change: Flyway aplica V7 sobre una base con V1, V3, V4 y V6.
3. Rollback: V7 solo agrega; para volver atrás se restaura el `CHECK` anterior y se borran la tabla y las columnas con una migración nueva. Nunca se edita V7 una vez mergeada.

## Open Questions

- ¿La dirección y el nombre del consultorio salen de variables de entorno o de la configuración de C-04 (`configuracion`)? Para v1 son variables de entorno; si Emanuel agrega la dirección a `configuracion`, se cambia el origen sin tocar el mensaje.
