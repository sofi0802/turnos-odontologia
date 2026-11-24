# Visión y Objetivos

> Fuentes: `docs/discovery/discovery.md` (checklist de 11 puntos) e `docs/discovery/informe-discovery.md` (secciones C y D). Generado por `kb-creator` (modo import) el 2026-11-20.

## Propósito del sistema

**Ordenar la agenda de un consultorio odontológico chico para que ningún turno se superponga por profesional ni por sillón, y para que el paciente pueda reservar y recordar su turno sin llamar.**

Hoy, en los consultorios chicos, los turnos llegan por WhatsApp y por teléfono, se pierden o se superponen, y no se ve qué profesional y qué sillón están libres. El relevamiento de 16 sistemas del mercado mostró que la agenda multiprofesional, el odontograma y los recordatorios ya son estándar. En cambio, **solo 2 de 16 mencionan la agenda por sillón y ninguno documenta cómo previene los solapamientos entre profesional y sillón** (informe, C.3). Ese vacío es el foco del producto.

## Objetivos por actor

| Actor | Objetivo principal | Objetivos secundarios |
|---|---|---|
| Recepcionista | Cargar y mover turnos sin generar conflictos de profesional, sillón ni margen | Ver la ocupación diaria y semanal; cargar sobreturnos justificados |
| Odontólogo | Ver su agenda y atender sin choques | Bloquear horarios; registrar la ficha y el odontograma |
| Administrador del consultorio | Configurar profesionales, sillones y prestaciones con su duración | Definir la política de cancelación (v1.x) |
| Paciente | Reservar, confirmar, cancelar o reprogramar su turno sin llamar | Recibir el recordatorio por WhatsApp o email |

## Alcance v1.0 (MVP, sección D.3 del informe)

1. Alta, baja y modificación (ABM) de **profesionales**, **sillones o boxes** y **prestaciones** con duración propia.
2. **Crear un turno sin solapamiento por profesional ni por sillón**, respetando el margen de 10 minutos.
3. **Cancelar y reprogramar** turnos, liberando el horario.
4. **Sobreturno**, que solo puede cargar el personal, registrado como excepción.
5. **Agenda diaria y semanal** por profesional y por sillón.
6. **Reserva online** por el paciente según la disponibilidad real, eligiendo **un profesional en particular o "sin preferencia"**. Cada profesional tiene sus **horarios de trabajo** semanales.
7. **Recordatorio y confirmación** por WhatsApp, o por email si el paciente no tiene WhatsApp, sin datos de salud en el mensaje.
8. **Ficha básica del paciente con odontograma.**

> **Alcance del trabajo práctico (6 días):** de la v1.0 se implementa de punta a punta, con el ciclo OPSX, el punto 2 ("crear turno sin solapamientos"). El resto queda especificado en `CHANGES.md` para que el equipo lo implemente en paralelo según el roadmap.

## Fuera de alcance

- **v1.x (diferenciadores)**: lista de espera automática, política de cancelación configurable por consultorio, plan gratuito o precio publicado, registro de consentimiento para comunicaciones.
- **Etapas posteriores**: obras sociales y prepagas (nomencladores, liquidaciones, validación online), facturación electrónica ARCA, señas con Mercado Pago, periodontograma, imágenes y radiografías, consentimientos con firma electrónica, asistentes con IA, multisucursal, exportación HL7 FHIR.
- Clínicas y cadenas multisucursal.
- Datos reales de pacientes, en cualquier entorno del proyecto.

## Métricas de éxito

| Métrica | Meta orientativa | Cómo se mide |
|---|---|---|
| Turnos en conflicto creados | **0** (fuera de los sobreturnos) | Tests por escenario y restricción en la base de datos |
| Turnos reservados por el paciente online | Medir la línea base | Origen del turno, deducido de la auditoría (RN-RE-05); se ve en Reportes (C-32) |
| Ausentismo | Medir la línea base; no hay dato propio | Turnos `AUSENTE` sobre los atendidos más los ausentes (RN-RE-06); se ve en Reportes (C-32) |

Las cifras de los competidores ("−90 % de ausencias", "−40 % de ausentismo") **no se usan como meta**: no tienen metodología publicada (informe, resumen ejecutivo).
