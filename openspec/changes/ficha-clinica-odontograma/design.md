# Design

## Context

- **Agenda (C-01 y C-06, Sofia)**:
  - `Turno` es un `record` inmutable con `cancelado()` para cambiar de estado. C-08 (mergeado en el PR #21) le sumó `esSobreturno`, `motivoSobreturno`, `extendido` y `motivoExtension`, y `cancelado()` copia esos cuatro campos.
  - `EstadoTurno` ya tiene `AUSENTE`, y `esActivo()` solo vale para `RESERVADO` y `CONFIRMADO`.
  - `TurnoRepository.buscarParaModificar(id)` bloquea la fila, y `guardar` actualiza el estado si el turno tiene id.
  - `ServicioAgenda` es el borde transaccional: cada operación y su auditoría van en la misma transacción, con reintento por deadlock.
  - `AgendaConfig` publica un `Clock` como bean.
- **Base**:
  - V4 dejó los `EXCLUDE` de `turno` limitados a `estado IN ('RESERVADO','CONFIRMADO')`: un turno `AUSENTE` ya no ocupa la agenda en la base.
  - El `CHECK` `turno_auditoria_accion_valida` acepta hoy en master `CREAR`, `CANCELAR`, `REPROGRAMAR`, `SOBRETURNO` y `EXTENDER`. Lo creó V4 con las tres primeras, y la V6 de C-08, ya mergeada, lo redefinió con las otras dos.
  - La V7 de C-11 (Juan Cruz, todavía sin mergear) lo redefine con siete acciones: suma `CONFIRMAR` y, a pedido de C-13, también `AUSENTE`.
  - Los perfiles `dev` y `test` usan `out-of-order` en Flyway, y `build.gradle` fija `user.timezone`. Los dos vinieron con C-04, ya mergeado.
- **Seguridad (C-05)**:
  - `/api/v1/**` permite solo a los tres roles del personal, y `@EnableMethodSecurity` está activo.
  - El principal `UsuarioAutenticado` expone `username()` y `profesionalId()`. El `profesionalId` es nulo si el usuario no está vinculado a un profesional.
  - Un `AccessDeniedException` que ningún manejador del módulo atrapa sale como `403 ACCESO_DENEGADO`. Así funcionan los permisos de C-04.
- **Pacientes**: `paciente` (V1) y `PacienteEntity` (`shared/persistence`) ya existen. C-05 los crea en el registro online, y los tests de C-06 los crean por JDBC.
- **Tests de C-06**: `IntegracionAgendaBase` inicia sesión de verdad (`IntegracionSeguridadBase`) y fija el reloj en el 2026-12-01 a las 08:00. La API no permite crear turnos en el pasado (RN-AG-09).

Motivación y alcance: ver `proposal.md`. Requisitos: ver `specs/`.

## Goals / Non-Goals

**Goals:**
- Que ningún dato de salud salga de la ficha: ni a logs, ni a errores, ni a la auditoría, ni a la recepción.
- Que el historial del odontograma sea solo de agregado, también a nivel de la base.
- Que la ausencia use el dominio y el borde transaccional de la agenda, sin duplicar sus reglas.

**Non-Goals:**
- Cifrado de columnas o auditoría de lecturas de la ficha. Quedan para C-27 (consentimientos) si la cátedra lo pide.
- Paginación del historial del odontograma: un paciente tiene, como mucho, decenas de registros por año.
- Un tipo de dato para la cara incisal (`I`) de los dientes anteriores: la KB define `O`, `M`, `D`, `V`, `L` y `P`.

## Decisions

### D1. Módulo `clinica/` con la misma estructura que `configuracion/`
- `clinica/domain`:
  - `PiezaDental`: valor que valida la pieza FDI y conoce su cuadrante.
  - `CaraDental`: enum que valida la cara contra la pieza.
  - `EstadoPieza`: el catálogo de estados.
  - `CodigoClinica` y `ClinicaException`.
- `clinica/application`: los servicios de ficha y odontograma, y el puerto de lectura `AusenciasPaciente`.
- `clinica/infrastructure`: entidades JPA propias (`FichaClinicaEntity`, `OdontogramaRegistroEntity`), repositorios Spring Data y el adaptador de ausencias.
- `clinica/api`: el controlador, los DTOs `record` y un `ClinicaExceptionHandler` limitado al paquete `clinica`.

Las entidades van en `clinica/infrastructure` y no en `shared/persistence`, porque ningún otro módulo las usa. Así no se toca el módulo de Juan Cruz.
*Alternativa descartada*: entidades en `shared/persistence` como las de C-03. Obligaría a avisar a Juan Cruz por tablas que solo usa C-13.

### D2. La ficha básica es una fila por paciente que se reemplaza
`ficha_clinica` tiene `paciente_id` único. El `PUT` crea o actualiza la fila y guarda `actualizado_por` (el `username`) y `actualizado_en`. El `GET` de un paciente sin fila devuelve los tres textos vacíos y `actualizadoPor` y `actualizadoEn` nulos. Los textos son opcionales, de hasta 4000 caracteres, y se validan con Bean Validation: `422 DATOS_INVALIDOS`.
*Alternativa descartada*: guardar versiones de la ficha. El dueño del change eligió reemplazar, y queda fuera de alcance en la propuesta.

### D3. El odontograma es solo de agregado
- **Tabla**: `odontograma_registro` (`id`, `paciente_id`, `pieza`, `cara` nula, `estado`, `profesional_id`, `registrado_en`).
- **Va contra `paciente`, no contra `ficha_clinica`**: así se puede registrar el odontograma sin haber cargado la ficha básica. Se aparta de la KB, que pone `ficha_id`, sin cambiar ningún comportamiento.
- **Solo de agregado en el código**: la entidad es de solo inserción (`@Column(updatable = false)` en todos los campos, sin setters), y el repositorio no expone ningún borrado.
- **Solo de agregado en la base**: un trigger `BEFORE UPDATE OR DELETE` levanta un error. Hay dos excepciones:
  - El `TRUNCATE` de los tests no dispara ese trigger.
  - El borrado en cascada de un paciente, que hoy no existe en la API, quedaría bloqueado. Para la Ley 26.529, eso es correcto.
- **Estado vigente**: `SELECT DISTINCT ON (pieza, cara) ... ORDER BY pieza, cara, registrado_en DESC, id DESC`. `DISTINCT ON` trata los `NULL` de `cara` como iguales, así que la pieza completa también tiene un solo estado vigente. El `id` desempata dos registros del mismo instante.
- **Historial**: `ORDER BY registrado_en DESC, id DESC`.

*Alternativa descartada*: una tabla de estado vigente más otra de historial. Duplica datos que una consulta resuelve.

### D4. Un pedido de odontograma es atómico
El `PUT` recibe una lista de 1 a 32 cambios. Primero se valida cada uno en el dominio (`PiezaDental.de`, `CaraDental.para`, `EstadoPieza`). Si uno falla, se lanza la excepción antes de guardar nada. Después se insertan todos en una transacción, con el mismo `registrado_en`, que sale del `Clock` del contexto.

### D5. Permisos con `@PreAuthorize` y el profesional de la sesión
- **Lecturas** (`GET` de ficha, odontograma e historial): `hasAnyRole('ADMIN','ODONTOLOGO')`.
- **Escrituras** (`PUT` de ficha y odontograma, `POST` de ausencias): `hasRole('ODONTOLOGO')`.
- **La recepción** queda afuera por la anotación, antes de que el servicio lea algún dato, y no ve contenido.
- **Odontólogo sin profesional vinculado**: para el odontograma y la ausencia, el servicio necesita el `profesionalId` de la sesión. Si es nulo, se lanza `AccessDeniedException`, que sale como `403 ACCESO_DENEGADO` igual que el resto de los permisos.
  - *Nota del apply*: la base ya impide ese caso (`CHECK usuario_odontologo_con_profesional` de V3), así que el control es una defensa. El test del escenario inyecta un principal sin profesional con `user(...)` y `csrf()` de spring-security-test, y descarta el contexto con `@DirtiesContext` porque `csrf()` modifica el filtro compartido.
- **Ficha básica**: no necesita profesional, porque firma con el `username`.

### D6. La ausencia es una operación de la agenda
Se agrega a la agenda, con aviso a Sofia:
- **`agenda/domain`**:
  - `Turno.ausente()`, que devuelve el mismo turno en `AUSENTE`, como `cancelado()`. Copia todos los campos del record, incluidos los de sobreturno y extensión de C-08 (ver Risks).
  - `TURNO_NO_INICIADO` en `CodigoRechazo`.
  - `AusenciaNoPermitidaException`, cuando el profesional del turno no es el de la sesión.
- **`agenda/application`**:
  - El caso de uso `MarcarAusencia(TurnoRepository, Clock)`, con el comando `MarcarAusenciaCommand(turnoId, pacienteId, profesionalId)`.
  - `AUSENTE` en `AuditoriaTurnos.Accion`.
- **`agenda/infrastructure`**:
  - `ServicioAgenda.marcarAusencia(comando, actor)`, en transacción y con auditoría.
  - El bean del caso de uso en `AgendaConfig`.

El orden de verificación evita revelar datos a quien no corresponde:
1. El turno existe y es del paciente; si no, `TurnoInexistenteException` y `404 TURNO_INEXISTENTE`.
2. El profesional coincide; si no, `AusenciaNoPermitidaException` y `403 ACCESO_DENEGADO`.
3. El turno está activo; si no, `409 TURNO_NO_ACTIVO`.
4. Ya pasó su inicio (`inicio <= ahora`); si no, `409 TURNO_NO_INICIADO`.

El turno se busca con `buscarParaModificar`, que bloquea la fila, así una cancelación simultánea no se cruza con la ausencia.

**No hace falta recalcular el intervalo.** El horario queda libre porque el turno sale de la agenda en el dominio (`esActivo()`) y en la base (el `WHERE` de los `EXCLUDE`). El tiempo ya transcurrido no se puede volver a dar, porque RN-AG-09 rechaza turnos en el pasado: eso cumple "desde el momento actual" sin otra regla.

El endpoint `POST /api/v1/pacientes/{id}/ausencias` con `{ "turnoId": … }` vive en `clinica/api` y llama a `ServicioAgenda`. Responde `200` con el id del turno, su inicio y el estado `AUSENTE`. Los rechazos de la agenda los traduce el `ClinicaExceptionHandler`, porque el `AgendaExceptionHandler` se limita a `agenda.api`.

*Alternativas descartadas*:
- `clinica/` actualiza el estado por SQL: se saltea el dominio, el bloqueo y la auditoría.
- Endpoint `POST /api/v1/turnos/{id}/ausencia` en `agenda/api`: la KB y US-042 ubican la acción en la ficha, y la ausencia sola no requiere más módulo de Sofia.

### D7. Las ausencias de la ficha se leen de `turno`
El puerto `AusenciasPaciente` de `clinica/application` devuelve el inicio y el nombre del profesional de los turnos `AUSENTE` de un paciente, del más nuevo al más viejo. El adaptador hace una consulta de solo lectura sobre `turno` y `profesional`. No hay una tabla de ausencias: el estado del turno ya es el registro, y la auditoría guarda quién y cuándo (`auditoria-turnos`).

### D8. Migración V8
Se crea `V8__ficha_clinica.sql` con cuatro partes:
1. **`ficha_clinica`**: `id` identity, `paciente_id` único con FK, `antecedentes`, `alergias` y `medicacion` `VARCHAR(4000)` nulos, `actualizado_por VARCHAR(120)` y `actualizado_en TIMESTAMPTZ`.
2. **`odontograma_registro`**: con estos `CHECK`:
   - `pieza` en los rangos FDI;
   - `cara IN ('O','M','D','V','L','P')`;
   - `L` solo en los cuadrantes 3, 4, 7 y 8, y `P` solo en 1, 2, 5 y 6;
   - `estado` en el catálogo.

   Lleva un índice `(paciente_id, pieza, cara, registrado_en DESC)` y el trigger de solo agregado (D3).
3. **El `CHECK` de `turno_auditoria`** se borra y se vuelve a crear con la lista completa de siete acciones, la misma que deja la V7 de C-11: `accion IN ('CREAR','CANCELAR','REPROGRAMAR','SOBRETURNO','EXTENDER','CONFIRMAR','AUSENTE')`. PostgreSQL no permite ampliar un `CHECK` existente, así que es el mismo procedimiento que usaron V6 y V7, sin editar ninguna migración mergeada.
   - **Con V6** no hay riesgo: ya está en master, y V8 siempre se aplica después.
   - **Con V7 el orden no importa**, porque las dos migraciones dejan exactamente la misma lista:
     - si C-11 entra primero, V8 reescribe lo mismo que ya había;
     - si C-13 entra primero, en una base nueva Flyway aplica V7 y después V8;
     - en una base existente V7 llega después, por `out-of-order`, y tampoco borra `AUSENTE`.
   - **Una migración futura que sume otra acción** tiene que copiar la lista entera, incluidos `CONFIRMAR` y `AUSENTE`.
4. Los `CHECK` repiten en la base las reglas del dominio, como hace la agenda con los `EXCLUDE`.

### D9. Sin datos de salud en logs ni errores
- El `ClinicaExceptionHandler` arma el `422 DATOS_INVALIDOS` con el campo y un mensaje fijo, nunca con el valor rechazado (`getRejectedValue`).
- Las excepciones de dominio llevan la pieza o la cara, que no son datos de salud por sí solas, pero no el texto de la ficha.
- No se loguean cuerpos de pedidos del módulo.
- La auditoría de la ausencia solo tiene ids, acción, actor y fecha (`AuditoriaTurnos` no acepta más).

### D10. Tests
- **Unitarios (sin base)**:
  - `PiezaDental`: todos los bordes de cada cuadrante, permanentes y temporales.
  - `CaraDental`: `L` y `P` por cuadrante.
  - `MarcarAusencia`: con los dobles en memoria de la agenda (`TurnoRepositoryEnMemoria`) y un `Clock` fijo, cubre el orden de verificación de D6.
- **Integración**:
  - Una base `IntegracionClinicaBase` extiende `IntegracionSeguridadBase` y crea por JDBC los usuarios `ana.ejemplo`, `bruno.prueba`, `admin` y `recepcion`, los profesionales, los sillones y los pacientes ficticios.
  - Los escenarios de ausencia necesitan que el turno haya empezado. Por eso la base reemplaza el `Clock` por uno ajustable (`@Primary`), inserta los turnos por JDBC en el pasado relativo y mueve el reloj por escenario.
  - Un test por escenario de los tres specs, con `@DisplayName` igual al nombre del escenario.
  - El escenario "Un error de validación no repite el contenido" también revisa la salida capturada (`OutputCaptureExtension`), para comprobar que no aparece en los logs.

## Risks / Trade-offs

- **[`ausente()` pierde los campos de C-08]**: si `Turno.ausente()` no copia `esSobreturno`, `motivoSobreturno`, `extendido` y `motivoExtension`, la base no los pierde, porque `TurnoRepositoryJpa.guardar` de un turno existente solo actualiza el estado y, si `extendido` es verdadero, la extensión. Pero el turno que devuelve el caso de uso diría que no es sobreturno ni está extendido, y cualquier cambio futuro en `guardar` los borraría de la base. → `ausente()` construye el turno con el constructor completo, como `cancelado()`. Un test de unidad marca como ausente un sobreturno y un turno extendido y verifica que conservan esos campos.
- **[Otro change agrega una acción de auditoría después de V8]**: si su migración no copia `AUSENTE` en la lista, la ausencia deja de poder auditarse y la operación falla. → Queda anotado en D8 y en el comentario de V8. Además, el test de esquema de V8 comprueba que la lista tiene las siete acciones.
- **[El trigger de solo agregado bloquea un borrado legítimo futuro]**: por ejemplo, el derecho de supresión de la Ley 25.326. → Para la Ley 26.529, la historia clínica se conserva. Si hiciera falta, una migración posterior reemplaza el trigger.
- **[Un odontólogo puede leer la ficha de cualquier paciente]**: no solo la de sus pacientes. → Es lo que define la matriz RBAC de la KB (`ODONTOLOGO`: CRUD). Restringirlo por turnos atendidos es una regla nueva que tendría que decidir la líder.
- **[El margen de limpieza no se aplica al hueco liberado]**: tras una ausencia a las 10:10, se puede dar un turno a las 10:15 en el mismo sillón, sin el margen respecto del turno ausente. → Es lo que pide RN-AG-17: el paciente no vino, no hay nada que limpiar.
- **[Un test con Flyway manual sin `out-of-order`]**: `V6MigrationIntegrationTest` (C-08) corre Flyway a mano sin `.outOfOrder(true)`. En una base `turnos_test` que tenía V6 antes de que llegara la V2 de C-04, ese test falla con "resolved migration not applied: 2". V8 no lo agrava: llega en orden, después de V6. → No es parte de C-13. Se le avisa a Juan Cruz, y si sigue pendiente durante el apply, se corrige en un commit aparte, como en C-04.

## Migration Plan

1. V8 es aditiva: tablas nuevas y un `CHECK` más amplio. No reescribe datos.
2. **Rollback**: `DROP` de las dos tablas y del trigger, y volver al `CHECK` anterior. Sale con una migración nueva, nunca editando V8 una vez mergeada.

## Open Questions

- ¿El frontend de C-09 necesita un resumen del odontograma por pieza, en lugar de pieza y cara? Se puede agregar como otra vista sin cambiar estos specs.
