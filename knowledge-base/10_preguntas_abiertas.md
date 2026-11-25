# Preguntas Abiertas

## Inconsistencias detectadas

### IN-01: Turno contiguo: ¿se solapa o no? *(resuelta)*
**Documento A dice**: la primera versión del Discovery decía que "un turno que empieza justo cuando termina otro no se considera solapado".
**Documento B dice**: la regla RN-04 (agregada el 2026-11-19) exige 10 minutos de margen entre turnos que comparten sillón o profesional.
**Impacto**: definía el escenario de borde del spec.
**Resolución**: el equipo decidió el 2026-11-19 que el margen aplica al sillón **y** al profesional. Contiguos que comparten recurso se rechazan; con distintos recursos, se aceptan (RN-AG-05).

### IN-02: Precio de DentalCore
**Documento A dice**: la home publica ARS 75.000/mes.
**Documento B dice**: el FAQ publica USD 50/mes.
**Impacto**: el puntaje de precio en la matriz B y el posicionamiento comercial.
**Resolución propuesta**: verificarlo a mano (fuente opcional 6 del informe). No afecta al MVP.

### IN-03: Portal del paciente de VecDent *(resuelta)*
**Documento A dice**: el relevamiento automático marcaba "Comprobado (portal en demo)".
**Documento B dice**: la verificación manual de Sofia (2026-11-20) encontró que solo el personal crea turnos.
**Resolución**: el informe se corrigió a "No evidenciado". La lección se incorporó a RN-RO-01 y a la matriz RBAC: el paciente no es un rol del personal.

## Preguntas abiertas (priorizadas)

### Pendientes

| ID | Prioridad | Pregunta | Bloquea | Decisor |
|---|---|---|---|---|
| PA-11 | Media | ¿Quiénes del equipo tienen **Docker**? La líder sí. Sin Docker hay alternativas (SU-04). | Verify y README | Cada integrante |

### Resueltas (2026-11-20, respuestas de la líder)

| ID | Pregunta | Resolución | Dónde |
|---|---|---|---|
| PA-01 | ¿Margen fijo o configurable? | Configurable, **mínimo 10 min** | RN-AG-04 |
| PA-02 | ¿El sobreturno saltea el margen? | Saltea la agenda del profesional; **nunca la limpieza del sillón**: si no hay sillón libre con margen, se rechaza | RN-AG-06 |
| PA-03 | ¿Login del paciente? | Sí: cancela o reprograma **desde su cuenta con el número de reserva**; por el consultorio, con **DNI y número de reserva** | RN-AG-07, RN-RO-08 |
| PA-04 | ¿Se registra el ausentismo? | Se registran las **ausencias y licencias del profesional**, que cargan el administrador o la recepcionista | RN-AG-16 (el ausentismo del paciente sigue en PA-19) |
| PA-05 | ¿Motivo obligatorio en el sobreturno? | **Sí** | RN-AG-06 |
| PA-06 | ¿Horario de atención? | Horarios de trabajo semanales por profesional | RN-AG-13 |
| PA-07 | ¿La duración varía por profesional? | **No**: es la misma para todos; el personal puede **extender** el turno si hay complicaciones | RN-AG-01, RN-AG-14 |
| PA-08 | ¿Anticipación del recordatorio? | 24 h antes | RN-NO-04 |
| PA-09 | ¿Zona horaria única? | **Sí**, `America/Argentina/Buenos_Aires` | RN-GL-02 |
| PA-12 | ¿Acepta el docente un grupo de 5? | **Sí** | — |
| PA-14 | ¿A quién se asigna en "sin preferencia"? | Al profesional **habilitado** para la prestación con **menos turnos** ese día; si hay empate, **al azar** | RN-RO-05 |
| PA-15 | ¿Sillón habitual? | **Sí**, se usa primero; si está ocupado, otro libre | RN-RO-06 |
| PA-16 | ¿Especialidades? | **Sí**, hay profesionales con especialidades | RN-AG-15 |
| PA-10 | ¿Sesión o JWT? *(2026-11-21)* | **Sesión con cookie** | DD-10 |
| PA-17 | ¿Turnos dados durante la ausencia de un profesional? *(2026-11-21)* | Se **reasignan**, avisando a los pacientes | RN-AG-16 |
| PA-18 | ¿La reserva exige cuenta? *(2026-11-21)* | **Sí**, el paciente se registra solo la primera vez | RN-RO-08 |
| PA-19 | ¿Ausencia del paciente? *(2026-11-21; ampliada el 2026-11-24)* | La marcan el odontólogo **desde la ficha clínica** o la recepción **desde la agenda**, a partir de los 15 minutos del inicio, y el turno queda **liberado**. Solo la recepción la deshace, si el horario sigue libre. La recepción registra la **llegada** y al terminar el turno pasa a `ATENDIDO` | RN-AG-17, RN-AG-20 |
| PA-13 | ¿Verificaciones de Bilog y DentalSoft? *(2026-11-21)* | **Bilog coincide** (Emanuel). **DentalSoft**: el plan gratis dura 3 meses y el precio suma $10.000 por profesional extra (Juan Sebastián). Se corrigieron el informe y la matriz; **no afecta reglas ni MVP** | Informe, verificación de fuentes |
| PA-20 | ¿Dos coberturas vigentes a la vez? *(2026-11-24, C-18)* | **No**: una sola afiliación vigente por paciente en cada fecha. Sí puede **cambiar de cobertura** (por ejemplo, deja la obra social y pasa a una prepaga): se cierra la afiliación anterior y se abre otra, y queda el historial | RN-OS-02, DD-14 |
| PA-21 | ¿El paciente ve su propia cobertura? *(2026-11-24, C-18)* | **Sí**, en "Mi cuenta", solo la suya y sin poder cambiarla | RN-OS-07, matriz de 03, US-046 CA-6 |
| PA-22 | ¿El nomenclador se carga a mano o se importa? *(2026-11-24, C-18)* | **A mano**, por el `ADMIN`, con el seed ficticio | RN-OS-01, RN-OS-05 |
| PA-23 | ¿Quién arma el presupuesto? *(2026-11-24, C-19)* | El **odontólogo**; el `ADMIN` lo ve | RN-PR-06, matriz de 03 |
| PA-24 | ¿La recepción ve los presupuestos? *(2026-11-24, C-19)* | **Sí**, con prestaciones e importes, sin pieza ni cara (datos de salud, RN-CL-01) | RN-PR-07, matriz de 03 |
| PA-25 | ¿Cobertura por porcentaje o también monto fijo? *(2026-11-24, C-19)* | **Solo porcentaje**, entero de 0 a 100 | RN-PR-02 |
| PA-26 | ¿El presupuesto vence? *(2026-11-24, C-19)* | **Sí**: vence a los **30 días** de su fecha; un presupuesto pendiente vencido ya no se puede aceptar | RN-PR-05 |
| PA-27 | ¿El paciente ve sus presupuestos? *(2026-11-24, C-19)* | **Sí**, en "Mi cuenta", solo los suyos y sin poder cambiarlos | Matriz de 03 |
| PA-28 | ¿El precio particular tiene historial? *(2026-11-24, C-19)* | **No**: un solo precio vigente; el presupuesto guarda una copia | RN-PR-01, DD-15 |
| PA-29 | ¿Quién ve los reportes? *(2026-11-24, C-32)* | El `ADMIN` y la `RECEPCIONISTA`, con todo el consultorio. El odontólogo no los ve y el paciente recibe `403` | RN-RE-01, matriz de 03 |
| PA-30 | ¿De dónde sale el origen del turno? *(2026-11-24, C-32)* | De la **auditoría**: un actor `paciente-online:<id>` es online; el resto, del consultorio. Sin migración | RN-RE-05, DD-16 |
| PA-31 | ¿Cómo se mide la ocupación? *(2026-11-24, C-32)* | En **porcentaje** de los minutos de atención del profesional (horario menos ausencias, licencias y bloqueos); el sillón, sobre los profesionales que lo tienen como habitual | RN-RE-04 |
| PA-32 | ¿Quién opera la caja? *(2026-11-24, C-22)* | La **recepción** abre, cobra y cierra; el `ADMIN` consulta y anula cobros; el odontólogo y el paciente no acceden | RN-CA-01, matriz de 03 |
| PA-33 | ¿Qué importe se cobra por un turno? *(2026-11-24, C-22)* | El **copago** de la prestación según la cobertura vigente (reglas de C-19); se puede pagar en parte y lo que falta queda como deuda | RN-CA-03, RN-CA-04 |
| PA-34 | ¿Qué medios de pago se registran? *(2026-11-24, C-22)* | **Efectivo, débito, crédito y transferencia**; el arqueo compara solo el efectivo | RN-CA-04, RN-CA-06 |
| PA-35 | ¿Cuántas cajas hay abiertas a la vez? *(2026-11-24, C-22)* | **Una por día del consultorio**: una sola abierta, con monto inicial en efectivo | RN-CA-02 |
| PA-36 | ¿Cuándo nace la deuda de un turno? *(2026-11-24, C-22)* | Cuando el turno queda **`ATENDIDO`**; ausentes y cancelados no generan deuda | RN-CA-03, DD-18 |
| PA-37 | ¿Cómo se corrige un cobro o una diferencia de arqueo? *(2026-11-24, C-22)* | El `ADMIN` **anula** el cobro con motivo, sin borrarlo; una diferencia al cerrar se registra con su motivo | RN-CA-06, RN-CA-07 |
| PA-38 | ¿Qué código se liquida si la prestación tiene varios? *(2026-11-24, C-20)* | La línea queda **sin código** en el borrador y el `ADMIN` elige uno antes de presentar | RN-LQ-01, RN-LQ-04 |
| PA-39 | ¿Quién maneja las liquidaciones? *(2026-11-24, C-20)* | **Solo el `ADMIN`** | RN-LQ-08, matriz de 03 |
| PA-40 | ¿Con qué precio se liquida? *(2026-11-24, C-20)* | El del **cargo de caja del turno** (C-22) si existe; si no, el precio vigente al generar, guardado como copia | RN-LQ-03 |
| PA-41 | ¿CSV, PDF o los dos? *(2026-11-24, C-20)* | **CSV** en C-20; el PDF queda para después | RN-LQ-05 |
| PA-42 | ¿Se reabre una liquidación presentada? *(2026-11-24, C-20)* | **No**; se corrige en la del mes siguiente | RN-LQ-04 |
| PA-43 | ¿Quién inicia la firma de un consentimiento? *(2026-11-24, C-27; confirmada por Emanuel)* | **solo el `ODONTOLOGO`** vinculado a un profesional, con su sesión; el `ADMIN` solo ve y verifica | RN-CL-10 |
| PA-44 | ¿Quién ve las plantillas? *(2026-11-24, C-27; confirmada por Emanuel)* | el `ADMIN` todas con sus versiones; el odontólogo solo las activas; la recepción y el paciente no | RN-CL-09 |
| PA-45 | ¿Quién registra la revocación y se vuelve a confirmar la identidad del paciente? *(2026-11-24, C-27; confirmada por Emanuel)* | la registra **el odontólogo** con un motivo, **sin** volver a pedir el DNI; queda su profesional como responsable | RN-CL-11 |
| PA-46 | ¿El nombre y el DNI escritos tienen que coincidir con la ficha? *(2026-11-24, C-27; confirmada por Emanuel)* | el **DNI sí**, sin puntos ni espacios (`FIRMANTE_NO_COINCIDE`); el **nombre no** se compara y se guarda como lo escribió | RN-CL-10 |
| PA-47 | ¿Puede firmar un representante (menor de edad o persona con capacidad restringida)? *(2026-11-24, C-27; confirmada por Emanuel)* | **no en C-27**; firma solo el propio paciente y el representante queda para un change futuro | C-27 |
| PA-48 | ¿Qué hash y qué sello? *(2026-11-24, C-27; confirmada por Emanuel)* | **SHA-256** sobre el contenido canónico con longitudes (formato 1), sin HMAC ni sellado de tiempo de un tercero | DD-20 |
| PA-49 | ¿Cómo se toma la IP detrás de un proxy? *(2026-11-24, C-27; confirmada por Emanuel)* | la que ve el servidor (`getRemoteAddr`), sin confiar en `X-Forwarded-For`; configurar `forward-headers-strategy` con proxies de confianza es global y queda para el despliegue | despliegue |
| PA-50 | ¿La firma se asocia a un turno? *(2026-11-24, C-27; confirmada por Emanuel)* | **no**; solo al paciente y a la versión de la plantilla | C-27 |
| PA-51 | ¿Una plantilla se puede desactivar y qué pasa si se edita sin cambios? *(2026-11-24, C-27; confirmada por Emanuel)* | **sí** se desactiva y se reactiva, nunca se borra; una edición sin cambios no crea versión y responde `200` | RN-CL-09 |
| PA-52 | ¿Hace falta un endpoint de verificación y quién lo usa? ¿El paciente recibe una copia? *(2026-11-24, C-27; confirmada por Emanuel)* | **sí**, lo usan el `ADMIN` y el odontólogo; el paciente **no** recibe copia por email ni WhatsApp en C-27 | RN-CL-11 y C-48 |
| PA-53 | ¿Quién configura las señas y cómo vienen? *(2026-11-24, C-23)* | Solo el `ADMIN`: las activa y fija el porcentaje y el plazo; vienen **desactivadas** | RN-SE-01 |
| PA-54 | ¿Qué reservas piden seña? *(2026-11-24, C-23)* | Solo la **reserva online** del paciente; ni los turnos del personal ni los sobreturnos | RN-SE-02 |
| PA-55 | ¿Cómo se calcula la seña? *(2026-11-24, C-23)* | Un **porcentaje del copago** al reservar; sin seña si el copago es cero o no hay arancel | RN-SE-02, RN-SE-03 |
| PA-56 | ¿Qué pasa con el turno mientras la seña no está pagada? *(2026-11-24, C-23)* | Ocupa el hueco con un **vencimiento**; si vence sin pago se cancela solo y el hueco se libera | RN-SE-04, DD-21 |
| PA-57 | ¿Se devuelve la seña si se cancela a tiempo? *(2026-11-24, C-23)* | **Sí**, por Mercado Pago, si el paciente cancela en plazo o si cancela el consultorio | RN-SE-06, RN-SE-09 |
| PA-58 | ¿Qué pasa con un pago que llega después del vencimiento? *(2026-11-24, C-23)* | El turno no se recupera y el pago se **reintegra** | RN-SE-05 |
| PA-59 | ¿Cómo se usa la seña cuando el turno queda atendido? *(2026-11-24, C-23)* | **A cuenta del cargo**, como un cobro `MERCADO_PAGO` fuera de la caja y del arqueo | RN-SE-08, DD-21 |
| PA-60 | ¿Qué pasa con la seña si se reprograma? *(2026-11-24, C-23)* | **Pasa al turno nuevo** sin volver a pagar ni pedir diferencia | RN-SE-07 |
| PA-61 | ¿Qué pasa si el paciente no viene o pide cancelar con menos de 24 h? *(2026-11-24, C-23)* | La **retiene** el consultorio; al cancelar, el personal indica si la causa es del paciente o del consultorio | RN-SE-06 |
| PA-62 | Con la seña pagada, ¿el turno pasa a `CONFIRMADO`? *(2026-11-24, C-23; decidida por la líder al revisar la KB)* | **No**: sigue `RESERVADO` y la confirmación de asistencia de RN-NO sigue aparte | RN-SE-04 |
| PA-63 | ¿Qué pasa si la seña es mayor que el copago final? *(2026-11-24, C-23)* | Se imputa el copago y la **diferencia se reintegra** (no hay saldo a favor) | RN-SE-08, RN-SE-09 |
| PA-64 | La notificación de Mercado Pago no puede llevar el token CSRF: ¿se acepta una excepción? *(2026-11-24, C-23; decidida por la líder al revisar la KB)* | **Sí**, una excepción de CSRF **solo para esa ruta**, autenticada por la firma y la consulta del pago; el resto de la API sigue con CSRF (regla dura 8) | RN-SE-10, DD-21 |
| PA-65 | ¿Dónde se ve el estado de la seña? *(2026-11-24, C-23)* | En **"Mis turnos"** del paciente; el personal no la ve en la agenda en C-23 | RN-SE-12 |

## Puntos "No evidenciado" del Discovery

Información que el relevamiento **no pudo respaldar con fuentes públicas** (informe, secciones A y C.3). No se completa por deducción; se confirma en las demos públicas o queda abierta.

| # | Qué no está evidenciado | En qué sistemas | Por qué importa |
|---|---|---|---|
| NE-01 | **Cómo previenen los solapamientos por sillón**, y si lo hacen | Todos los relevados. Solo AgendaPro y Órbita mencionan la agenda por sillón, sin reglas | Es el diferencial del MVP (DD-02); confirmar que el vacío es real |
| NE-02 | **Lista de espera** | Los 16 sistemas | Diferenciador v1.x (US-100) |
| NE-03 | **Prevención de solapamientos por profesional** | Todos menos DentalSoft (detección solo en sobreturnos) | Validar el diferencial |
| NE-04 | **Precios** de Bilog, DentalTec, Dentalink, Órbita, Doctocliq, Dentidesk, Clinic Cloud, Medesk y Open Dental | 9 de 16 | Posicionamiento de precio |
| NE-05 | **Qué datos viajan en los recordatorios** por WhatsApp | Los 16 sistemas | Justifica RN-NO-01 |
| NE-06 | **Normativa argentina declarada** (Leyes 25.326 y 26.529) | 13 de 16 (solo DentalCore, AgendaPro y Bilog la citan) | Requisito legal propio (DD-07) |
| NE-07 | **Reserva online por el propio paciente** | DentalTec, DentalCore, Órbita, Doctocliq, VecDent (corregido tras la verificación) | El estándar de mercado es menos uniforme de lo que parecía |
| NE-08 | **ARCA y Mercado Pago** | Todos menos DentalCore (ambos), DentalTec (ARCA), DentalSoft y AgendaPro (MP) | Etapas posteriores |
| NE-09 | **Odontograma en Clinic Cloud**; **licencia y español de Open Dental** | Esos sistemas | Referencias internacionales |
| NE-10 | **Moneda de los precios** publicados con "$" | DentalSoft, VecDent | Comparación de precios |
