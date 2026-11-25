# Decisiones y Supuestos

## Decisiones documentadas

### DD-01: Stack: Spring Boot + Gradle + PostgreSQL y TypeScript vanilla
**Decisión**: backend en Spring Boot con Gradle y PostgreSQL; frontend en TypeScript sin framework, con diseño responsivo.
**Contexto**: el equipo lo definió en el Discovery (punto 9).
**Alternativas consideradas**: no se evaluaron otras; es una restricción del equipo.
**Justificación**: es el stack que maneja el equipo de 5 personas, y eso reduce la curva de aprendizaje con 6 días de plazo.
**Trade-offs aceptados**: sin framework de frontend hay más código manual de UI. Se mitiga porque la consigna no exige interfaz gráfica para el change.

### DD-02: El núcleo del MVP es "turno sin solapamiento por profesional y sillón, con margen"
**Decisión**: la primera funcionalidad, y el change OPSX, es crear un turno con la verificación de conflictos.
**Contexto**: el informe (C.3) muestra que ninguno de los 16 sistemas documenta la prevención de solapamientos entre profesional y sillón.
**Alternativas consideradas**: empezar por la reserva online o por los recordatorios (estándar de mercado).
**Justificación**: es el vacío del mercado, tiene reglas verificables (lo pide la consigna) y es base de todas las demás funcionalidades.
**Trade-offs aceptados**: el TP entrega un núcleo de dominio y no un producto completo.

### DD-03: Doble garantía contra solapamientos: dominio y restricción de exclusión en PostgreSQL
**Decisión**: la regla se evalúa en el dominio (mensajes de error claros y tests por escenario) **y** en la base con dos restricciones de exclusión:
- **por sillón**: `EXCLUDE USING gist (sillon_id WITH =, tstzrange(inicio, fin_con_margen) WITH &&) WHERE (estado IN ('RESERVADO','CONFIRMADO'))`. **Incluye los sobreturnos**, porque la limpieza del sillón es obligatoria (RN-AG-06);
- **por profesional**: la misma con `profesional_id`, agregando `AND NOT es_sobreturno`.

Como el margen es configurable (con mínimo 10), se guarda la columna `fin_con_margen = fin + margen vigente al crear el turno`.
**Contexto**: dos reservas concurrentes, una del personal y otra online, pueden pasar las dos la verificación en memoria.
**Alternativas consideradas**: bloqueo pesimista (`SELECT ... FOR UPDATE`) o aislamiento serializable.
**Justificación**: la restricción es declarativa, atómica y no depende de la disciplina del código.
**Trade-offs aceptados**: requiere la extensión `btree_gist`, y los tests de integración necesitan PostgreSQL real (ver SU-04). *Nota técnica: si cada fila guarda el rango `[inicio, fin + 10 min)`, dos filas A y B se intersecan exactamente cuando `A.inicio < B.fin + 10` y `B.inicio < A.fin + 10`, que es la definición de conflicto de `05_reglas_de_negocio.md`. Los casos 10:30 (rechaza) y 10:40 (acepta) se cubren con tests de integración.*

### DD-04: Integraciones externas detrás de puertos y simuladas en el TP
**Decisión**: WhatsApp y email se implementan como adaptadores de `NotificacionPort`; en el TP, son simulados (log o servidor SMTP de desarrollo).
**Contexto**: la WhatsApp Business API tiene costo (USD 0,026 por mensaje, dato de DentalSoft) y requiere aprobar plantillas. La consigna prohíbe crear cuentas con datos reales.
**Justificación**: permite probar el flujo sin cuentas ni costos.
**Trade-offs aceptados**: no se valida la entrega real del mensaje.

### DD-05: Un consultorio por despliegue (single-tenant) en v1
**Decisión**: no hay entidad `Consultorio` ni multi-tenencia en v1.
**Contexto**: el segmento es el independiente o el consultorio chico; multisucursal es "posterior" (informe, D.3).
**Trade-offs aceptados**: pasar a multi-tenant después requerirá una migración.

### DD-06: El sobreturno es una excepción explícita y auditada
**Decisión**: el sobreturno saltea RN-AG-02 a RN-AG-04, solo lo carga el personal y queda marcado y auditado.
**Contexto**: regla RN-06 del Discovery; todos los competidores que lo mencionan lo restringen al personal.

### DD-07: Privacidad desde el diseño en las comunicaciones
**Decisión**: los recordatorios no llevan datos de salud y requieren consentimiento.
**Contexto**: Ley 25.326 (los datos de salud son sensibles); el informe (C.3) muestra que ningún competidor dice qué datos viajan por WhatsApp.

### DD-08: Horarios de trabajo por profesional y reserva "con profesional" o "sin preferencia"
**Decisión**: cada profesional tiene franjas semanales de trabajo (RN-AG-13). El paciente elige un profesional o "sin preferencia". En ese caso ve la unión de los horarios libres y el sistema asigna el profesional y el sillón (RN-RO-04 a RN-RO-07).
**Contexto**: Ajustar de la líder sobre la KB v1 (2026-11-20). Sin horarios, el sistema ofrecía turnos en momentos en que nadie atiende, y la reserva online no definía qué sillón ni qué profesional le tocaba al paciente.
**Alternativas consideradas**: (a) usar solo bloqueos de agenda para cerrar las franjas sin atención; (b) que el paciente siempre elija profesional.
**Justificación**: (a) obliga a bloquear a mano cada semana; (b) le quita al paciente la opción de "el primer horario libre", que es lo más cómodo.
**Trade-offs aceptados**: suma una entidad (`HorarioTrabajo`) y un change al roadmap. **No amplía el change C-01** ("crear turno sin solapamientos"), que sigue enfocado en RN-AG-01 a RN-AG-05. La validación de horario se implementa en un change aparte que depende de C-01.

### DD-09: Respuestas de la líder a las preguntas abiertas (2026-11-20)
**Decisión**: se incorporan a las reglas las respuestas a PA-01 a PA-05, PA-07, PA-09, PA-12 y PA-14 a PA-16:
- el margen es configurable con un **mínimo de 10 min** (RN-AG-04);
- el **sobreturno** puede pisar la agenda del profesional, **nunca la limpieza del sillón** (RN-AG-06), y exige motivo;
- el paciente cancela o reprograma **desde su cuenta con el número de reserva**; por el consultorio, con **DNI y número de reserva** (RN-AG-07);
- se registran **ausencias y licencias del profesional** (RN-AG-16);
- la duración es **la misma para todos los profesionales**, con **extensión por complicaciones** (RN-AG-01, RN-AG-14);
- hay **especialidades** (RN-AG-15) y **sillón habitual** (RN-RO-06);
- en "sin preferencia" se asigna el profesional con **menos turnos** y, si hay empate, **al azar** (RN-RO-05).

**Contexto**: Ajustar de la líder sobre la KB v1.
**Trade-offs aceptados**: crece el modelo (cuentas de paciente, especialidades, ausencias). Nada de esto amplía el change C-01; cada punto va a su change del roadmap.

### DD-10: Autenticación con sesión y cookie (no JWT)
**Decisión**: personal y pacientes inician sesión con usuario y contraseña. El servidor mantiene la sesión y el navegador la identifica con una cookie `HttpOnly`.
**Contexto**: PA-10, decidida por la líder el 2026-11-21.
**Alternativas consideradas**: JWT (un token firmado que el navegador envía en cada pedido).
**Justificación**: es lo más simple con Spring Security y un único backend; no hay apps móviles ni varios servidores que justifiquen JWT.
**Trade-offs aceptados**: la sesión vive en el servidor, y para escalar a varias instancias habría que compartirla (por ejemplo, Spring Session con la base). No aplica en v1.

### DD-11: Ausencias del profesional y del paciente
**Decisión**: (a) los turnos afectados por la ausencia o licencia de un profesional se **reasignan** a otro profesional habilitado o se reprograman, **avisando al paciente** (RN-AG-16). (b) La **ausencia del paciente** la marcan el odontólogo desde la ficha clínica o la recepción desde la agenda, a partir de los 15 minutos del inicio, y el tiempo restante del turno queda libre; solo la recepción la deshace, si el horario sigue libre (RN-AG-17). (c) La recepción registra la **llegada**, y al terminar el horario el turno pasa a `ATENDIDO` (RN-AG-20).
**Contexto**: PA-17 y PA-19, decididas por la líder el 2026-11-21. Los puntos (b) y (c) se ampliaron el 2026-11-24 (C-43): los turnos pasados quedaban como "Reservado" porque nadie registraba la asistencia. La llegada no pasa el turno a `ATENDIDO` en el momento porque eso liberaría el horario mientras el paciente se atiende (las restricciones `EXCLUDE` solo cuentan los turnos activos, DD-03).

### DD-12: Identificadores BIGINT en todo el proyecto (no UUID)
**Decisión**: todas las tablas usan `id BIGINT GENERATED ALWAYS AS IDENTITY`, y el código Java usa `Long`. El id lo asigna la base al insertar; un `Turno` nuevo no tiene id hasta que se guarda.
**Contexto**: el 2026-11-21, el agente de Emanuel detectó, mientras trabajaba en C-04, que las migraciones V1 (C-03) y V3 (C-05) usaban `BIGINT`, mientras que esta KB y el dominio de C-01 usaban `UUID`. Las dos fuentes se habían escrito por separado y nadie las había comparado.
**Alternativas consideradas**: pasar todo a UUID.
**Justificación**:
- V1 y V3 ya estaban mergeadas, y no se editan migraciones mergeadas (regla dura 3). Pasar a UUID exigía migraciones nuevas sobre todas las tablas y referencias.
- C-03 y C-05 ya usaban `Long`; solo el dominio de C-01 y esta KB usaban UUID.
- `BIGINT` identity es lo que recomienda la skill `postgresql-table-design`. UUID solo aporta si hay que ocultar los ids, y el paciente no los ve: usa el número de reserva (RN-RO-08).
**Trade-offs aceptados**: los ids son correlativos y se pueden adivinar. Por eso la API nunca debe autorizar solo por el id: siempre por sesión y rol (DD-10, regla dura 8).
**Impacto**: el dominio de C-01 pasó de `UUID` a `Long`, sin cambios en el spec `agenda-turnos`, que no menciona el tipo de id. El design archivado de C-01 (D6) dice UUID y queda como registro histórico.

### DD-13: Estado periodontal por pérdida de inserción [2026-11-24]
**Decisión**: el periodontograma (C-25) calcula el estado de cada pieza (sano, leve, moderado o severo) por la **pérdida de inserción** del peor sitio (profundidad de sondaje + recesión), con los cortes de RN-CL-06. Cada carga es una versión nueva e inmutable (RN-CL-04).
**Contexto**: la líder tomó C-25 el 2026-11-24 (antes de Emanuel). La KB solo nombraba el periodontograma en el backlog, sin un criterio para el estado. VecDent calcula el estado de la pieza (`docs/discovery/verificacion/sofia.md`).
**Alternativas consideradas**: clasificar solo por la profundidad de sondaje (sano hasta 3 mm, leve 4 mm, moderado 5–6 mm, severo 7 mm o más).
**Justificación**: la pérdida de inserción es el dato que usan los estadios de la clasificación AAP/EFP 2017, mientras que la profundidad sola no muestra la recesión. Los cortes 1–2, 3–4 y 5 o más mm siguen esa clasificación.
**Trade-offs aceptados**: es una simplificación por pieza; no calcula el estadio ni el grado del paciente, que dependen también de radiografías, de las piezas perdidas y de la historia clínica. Por eso el estado es orientativo y no reemplaza el diagnóstico.
**Impacto**: RN-CL-04 a RN-CL-06, US-045, la fila del periodontograma en la matriz de 03 y las entidades `PeriodontogramaVersion`, `PeriodontogramaPieza` y `PeriodontogramaSitio` (migración V16).

### DD-14: Una afiliación vigente por paciente, con vigencias que no se superponen [2026-11-24, C-18]
**Decisión**: C-18 modela la cobertura como una `AfiliacionPaciente` (paciente, plan, número de afiliado y vigencia). Un paciente tiene como máximo una afiliación vigente en cada fecha (RN-OS-02). La garantía va en el dominio y en PostgreSQL con `EXCLUDE USING gist` sobre la vigencia, igual que DD-03 para los turnos.
**Contexto**: la KB solo nombraba las obras sociales en el backlog (US-200). El relevamiento muestra que los sistemas del mercado cargan obras sociales con sus planes y su nomenclador (Dentalink, DentalCore y DentalTec; `docs/discovery/sources/relevamiento-fuentes.md`), pero no publica sus reglas. Las reglas RN-OS las propuso el responsable de C-18 y las aprobó la líder el 2026-11-24 (PR #103 y PA-20 a PA-22).
**Alternativas consideradas**:
1. Varias coberturas vigentes a la vez (por ejemplo, una obra social y una prepaga complementaria), con una marcada como principal.
2. Guardar la obra social y el número directamente en `Paciente`, sin historial.
**Justificación**:
- Una sola vigente alcanza para el consultorio chico del MVP, y C-19 calcula el copago sin tener que elegir entre coberturas.
- La vigencia con historial permite cambiar de plan sin perder con qué cobertura se atendió antes, que es lo que necesita la liquidación de C-20.
**Trade-offs aceptados**: un paciente con doble cobertura se carga solo con la principal. La líder lo confirmó el 2026-11-24 (PA-20).
**Impacto**: RN-OS-01 a RN-OS-07, US-046, dos filas de la matriz de 03 y las entidades `ObraSocial`, `Plan`, `Nomenclador` y `AfiliacionPaciente` (migración V12).

### DD-15: El presupuesto guarda una foto del cálculo [2026-11-24, C-19]
**Decisión**: C-19 guarda en cada ítem del presupuesto el precio, el porcentaje de cobertura y los importes calculados en el momento de emitirlo, además de la afiliación usada (RN-PR-04). El presupuesto no se recalcula después.
**Contexto**: el relevamiento muestra que los sistemas del mercado tienen presupuestos con convenios, planes y aranceles, y una "simulación de presupuesto" (Dentalink, verificación de Juan Cruz; DentalSoft, DentalCore y AgendaPro afirman presupuestos). No publica cómo calculan la cobertura. Las reglas RN-PR son una propuesta del responsable de C-19 para que la revise la líder.
**Alternativas consideradas**:
1. Calcular el presupuesto en el momento de mostrarlo, con los precios y la cobertura actuales.
2. Llevar un historial de aranceles con vigencias y recalcular con el precio de la fecha del presupuesto.
**Justificación**:
- Lo que se le informa al paciente no puede cambiar solo porque después se actualizó un precio.
- La foto es más simple que un historial de aranceles y alcanza para la liquidación de C-20, que necesita los importes de cada ítem.
**Trade-offs aceptados**: hay datos copiados en cada ítem. Para actualizar un presupuesto con precios nuevos se rechaza y se emite otro.
**Impacto**: RN-PR-01 a RN-PR-07, US-047, dos filas de la matriz de 03 y las entidades `Arancel`, `CoberturaPlan`, `Presupuesto` y `PresupuestoItem` (migración V13).

### DD-16: El origen y quién canceló salen de la auditoría [2026-11-24, C-32, en revisión]
**Decisión**: C-32 deduce el origen de cada turno y quién lo canceló a partir de `turno_auditoria`: un actor `paciente-online:<id>` es el paciente desde su cuenta; cualquier otro actor es el personal (RN-RE-05, RN-RE-07). No se agrega una columna `origen` al turno.
**Contexto**: la KB describía `origen`, `creado_por` y `cancelado_en` en `Turno` (04), pero ningún change los creó. La auditoría, en cambio, registra cada creación y cancelación con su actor desde C-06, y la reserva online usa un actor propio sin datos personales desde C-10.
**Alternativas consideradas**:
1. Una migración que agregue `turno.origen` y lo complete desde la auditoría para los turnos existentes.
2. Pedirle al personal que indique el origen al cargar cada turno.
**Justificación**:
- La auditoría ya tiene el dato, para todos los turnos y sin migración.
- No toca `agenda/` (de Sofia) ni pide un número de migración reservado.
- Respeta el actor sin datos personales de la reserva online.
**Trade-offs aceptados**: los reportes dependen del formato del actor online. Si un change futuro lo cambia, tiene que mantener un prefijo reconocible o actualizar RN-RE-05. Los turnos sin registro de creación se informan como "sin dato".
**Impacto**: RN-RE-05, RN-RE-07, la corrección de `Turno` en 04 y la métrica de origen de 01.

### DD-17: Los archivos clínicos van a un almacén detrás de un puerto, con un adaptador en disco [2026-11-24, C-26]
**Decisión**: el contenido de las imágenes y los estudios no se guarda en PostgreSQL.
- Va a un **almacén de archivos detrás de un puerto**. En este change el único adaptador escribe en una carpeta del disco, configurada con `IMAGENES_DIRECTORIO`.
- La base guarda los metadatos, el tamaño, el hash SHA-256 y una clave de almacén generada por el servidor, sin datos del paciente.
**Contexto**: CHANGES.md pedía para C-26 un almacenamiento "local o S3 compatible". El proyecto ya pone las integraciones externas detrás de puertos (DD-04).
**Alternativas consideradas**:
1. Guardar el contenido en PostgreSQL (`bytea`): es transaccional, pero agranda la base y los respaldos.
2. Hacer ya un adaptador S3: suma una dependencia y un servicio para los tests sin necesidad en el TP.
**Justificación**: con el puerto, pasar a S3 es agregar un adaptador sin tocar el dominio ni la API. El disco local alcanza para un despliegue de una sola instancia (DD-05).
**Trade-offs aceptados**:
- El respaldo tiene que incluir la carpeta de imágenes además de la base.
- Una caída entre el guardado del archivo y el de la fila puede dejar un archivo huérfano, invisible para la API.
**Impacto**: RN-CL-07, la entidad `ImagenClinica` (migración V17) y la variable `IMAGENES_DIRECTORIO` en `08_arquitectura_propuesta.md`.

### DD-18: La deuda nace como un cargo por el copago cuando el turno queda atendido [2026-11-24, C-22, en revisión]
**Decisión**: C-22 genera un **cargo** en la cuenta corriente del paciente cuando un turno pasa a `ATENDIDO`, por el copago de su prestación según la cobertura vigente, y lo guarda como una foto del cálculo (RN-CA-03). Los cobros se imputan a ese cargo, en uno o varios pagos (RN-CA-04).
**Contexto**: C-19 ya calcula el copago de una prestación con el arancel y la cobertura del plan (RN-PR-01 a RN-PR-03). La competencia afirma tener caja, cobros y cuenta corriente, pero no publica cómo los calcula (relevamiento de fuentes).
**Alternativas consideradas**:
1. Registrar solo los cobros, sin cargos: la deuda existiría solo si alguien carga un cobro parcial.
2. Calcular la deuda en el momento de mostrarla, con los precios y la cobertura actuales.
**Justificación**:
- Con el cargo, un turno atendido sin cobrar queda como deuda aunque nadie lo cobre ese día.
- La foto evita que la deuda cambie sola cuando se actualiza un precio, igual que el presupuesto (DD-15).
- El cargo por turno es la base que necesitan las señas (C-23) y la factura (C-24).
**Trade-offs aceptados**: el paso a `ATENDIDO` lo hace la agenda (`agenda/`, de Sofia), así que C-22 necesita enterarse de ese cambio; el propose define cómo, sin que `agenda/` dependa de `caja/`. Un turno atendido sin arancel no genera deuda hasta que se carga el precio.
**Impacto**: RN-CA-01 a RN-CA-08, US-207, una fila de la matriz de 03 y las entidades `Caja`, `Cargo` y `Cobro` (migración V15).

### DD-19: Se liquidan los turnos atendidos [2026-11-24, C-20]
**Decisión**: C-20 arma la liquidación mensual de cada obra social con los **turnos atendidos** de sus afiliados, uno por línea, con la prestación del turno, su código del nomenclador y el porcentaje del plan (RN-LQ-01). Elegido por el responsable de C-20 el 2026-11-24.
**Contexto**: el sistema no tiene un registro de "práctica realizada". Tiene turnos con estado `ATENDIDO` y su prestación, y presupuestos aceptados (C-19). En el relevamiento, Dentalink concilia por "paciente, prestación realizada, obra social, monto solicitado y estado" (verificación de Juan Cruz), y DentiClick liquida con nomenclador y exporta a CSV y PDF.
**Alternativas consideradas**:
1. Liquidar los ítems de los presupuestos aceptados. Es simple, pero aceptado no quiere decir que la práctica se hizo.
2. Un registro nuevo de práctica realizada (prestación, pieza y fecha) cargado por el odontólogo. Es lo más exacto, pero suma una entidad y una pantalla.
**Justificación**: el turno atendido ya prueba que la prestación se hizo, con fecha y profesional, y no hace falta cargar nada más.
**Trade-offs aceptados**: un turno es una sola prestación. Si en un turno se hacen varias prácticas, hoy se liquida solo la del turno; si hace falta, se agrega después el registro de práctica realizada.
**Impacto**: RN-LQ-01 a RN-LQ-08, US-049, una fila de la matriz de 03 y las entidades `Liquidacion` y `LiquidacionLinea` (migración V14).

### DD-20: Consentimiento con firma electrónica, evidencia y hash canónico [2026-11-24, C-27]
**Decisión**: el consentimiento informado se firma con **firma electrónica** (Ley 25.506, art. 5), **no con firma digital** (art. 2, con certificado de un certificador licenciado).
- En la computadora del consultorio, con la sesión del odontólogo, el paciente lee una versión concreta de la plantilla, escribe su nombre y su DNI y acepta.
- El sistema guarda una copia del texto firmado y la evidencia (firmante, profesional, fecha y hora, IP y dispositivo), y calcula un **SHA-256** sobre un **contenido canónico** con cada campo precedido por su longitud en bytes (formato 1).
- El documento firmado es inmutable en el dominio y en la base (trigger sin excepciones). La revocación va en otra tabla, así que no lo toca y su hash sigue verificando.
- Las plantillas tienen versiones de solo agregado y las edita el `ADMIN`.

**Contexto**: Emanuel fijó el alcance el 2026-11-24 (CHANGES.md §C-27). Bilog ofrece consentimientos con firma citando la Ley 25.506 (`docs/discovery/sources/relevamiento-fuentes.md` §Bilog), sin publicar cómo.
**Alternativas consideradas**:
1. Firma digital con certificado: cada paciente necesitaría un certificado de un certificador licenciado.
2. Trazo manuscrito en una tableta: suma hardware y no mejora la prueba de integridad.
3. Hashear solo el texto, o usar JSON canónico: lo primero no ata el texto al firmante y a la evidencia; lo segundo es más difícil de reproducir a mano.

**Justificación**: un consultorio chico no puede exigir certificados a sus pacientes. La evidencia guardada y el hash permiten probar qué se firmó, quién dijo firmar, cuándo y con qué sesión del personal.
**Trade-offs aceptados**:
- La firma electrónica no tiene las presunciones de autoría e integridad de la firma digital (arts. 7 y 8): si el paciente la desconoce, el consultorio tiene que probar su validez. Para el Código Civil y Comercial (art. 288), en instrumentos electrónicos la firma se cumple con firma digital, así que el documento vale como prueba y no como instrumento firmado en sentido estricto. Se recomienda que el consultorio lo valide con su asesor legal.
- El hash no está firmado con una clave fuera de la base: quien administra la base podría reescribir una fila y su hash. Un sello HMAC o un sellado de tiempo de un tercero quedan como extensión.
- La IP es la que ve el servidor; detrás de un proxy es la del proxy (PA-49).

**Impacto**: RN-CL-09 a RN-CL-11, US-208, dos filas de la matriz de 03 y las entidades `PlantillaConsentimiento`, `PlantillaConsentimientoVersion`, `ConsentimientoFirmado` y `ConsentimientoRevocacion` (migración V18).

## Supuestos inferidos

### ~~SU-07: Todos los profesionales pueden hacer todas las prestaciones~~ (refutado)
**Refutado el 2026-11-20**: hay especialidades (DD-09, RN-AG-15).

### SU-08: La reserva online requiere cuenta de paciente (**confirmado el 2026-11-21**: se registra solo la primera vez)
**Supuesto**: como el paciente cancela y reprograma "desde su cuenta", también **reserva** con la cuenta iniciada. No hay reserva anónima.
**Origen**: respuesta a PA-03.
**Riesgo si es falso**: se pierden reservas de pacientes que no quieren registrarse.
**Cómo validar**: PA-18.

### SU-01: Los consultorios chicos sufren solapamientos y desorden de turnos
**Supuesto**: el problema existe y vale la pena resolverlo.
**Origen**: `discovery.md`, punto 10. El equipo no conoce consultorios de primera mano.
**Riesgo si es falso**: el producto resuelve un problema menor.
**Cómo validar**: entrevistar a 2 o 3 odontólogos o secretarias, con datos anonimizados.

### SU-02: Los pacientes prefieren reservar online antes que escribir por WhatsApp
**Origen**: `discovery.md`, punto 10.
**Riesgo si es falso**: baja adopción de la reserva online.
**Cómo validar**: medir el `origen` de los turnos una vez en uso.

### SU-03: Zona horaria única `America/Argentina/Buenos_Aires` (**confirmado el 2026-11-20**)
**Origen**: el mercado objetivo es Argentina.
**Riesgo si es falso**: errores de hora en el cálculo de las 24 h y los recordatorios.
**Cómo validar**: confirmarlo con el equipo (PA-09).

### SU-04: Tests de integración con PostgreSQL real (Testcontainers o una instancia local)
**Origen**: DD-03 exige `btree_gist`, que H2 no soporta.
**Riesgo si es falso**: si el equipo no tiene Docker, los tests de integración no corren en todas las máquinas.
**Cómo validar**: confirmar si los 5 integrantes tienen Docker (PA-11). La líder sí lo tiene.
**Alternativas si alguien no tiene Docker**:
1. **Tests de dominio puros** (sin base): cubren **todos los escenarios del spec** y corren en cualquier máquina con Java. Son los que exige la consigna.
2. **PostgreSQL instalado localmente** (instalador oficial para Windows): los tests de integración apuntan a esa base con variables de entorno.
3. Tests de integración con un **tag aparte** (`@Tag("integration")`): quien no tenga base los saltea, y los corre al menos la líder antes de cada merge.

### SU-05: El frontend usa Vite como bundler de TypeScript vanilla
**Origen**: "TypeScript vanilla" no define cómo se compila; Vite tiene una plantilla `vanilla-ts` sin framework.
**Cómo validar**: confirmarlo con el equipo en el change de base del frontend.

### SU-06: Las duraciones de las prestaciones son múltiplos de 5 minutos
**Origen**: los ejemplos del Discovery (30 y 60 min) y el margen de 10 min.
**Riesgo si es falso**: rechazar duraciones válidas.
**Cómo validar**: confirmarlo con el equipo.
