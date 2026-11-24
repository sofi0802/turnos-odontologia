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
| PA-20 | Media | ¿Un paciente puede tener **dos coberturas vigentes** a la vez (obra social y prepaga complementaria)? La propuesta de C-18 dice que no (DD-14). | Modelo de `AfiliacionPaciente` (C-18) | Líder |
| PA-21 | Media | ¿El **paciente ve su propia cobertura** en "Mi cuenta"? La propuesta de C-18 dice que no por ahora (la carga el personal). | Matriz de 03 y pantallas del paciente | Líder |
| PA-22 | Baja | ¿El nomenclador se carga **a mano** por el `ADMIN` o se importa un nomenclador nacional? La propuesta de C-18 es la carga a mano, con el seed ficticio. | Alcance de C-18 | Líder |
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
