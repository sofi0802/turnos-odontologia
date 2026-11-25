# Proposal: senas-mercado-pago (C-23)

## Why

Un paciente puede reservar online y no venir, y el consultorio pierde el horario sin compensación. La KB del dominio Señas (RN-SE-01 a RN-SE-12, US-202, DD-21, PA-53 a PA-65) ya está aprobada (PR #133), con PA-62 y PA-64 decididas por la líder. C-22 dejó el cargo por turno atendido que la seña necesita para imputarse. Governance CRÍTICO (pagos): no se escribe código sin la aprobación explícita de esta propuesta y de sus mockups.

## What Changes

- **Configuración de las señas** (`/api/v1/senas/configuracion`, solo `ADMIN`): activarlas, el porcentaje del copago y el plazo para pagar. Vienen desactivadas (RN-SE-01).
- **Seña al reservar online**: con las señas activas y un copago mayor que cero, la reserva del paciente crea el turno `RESERVADO` (que ocupa el hueco como hoy) y una seña pendiente con su importe, su vencimiento y el link de pago de Mercado Pago. La respuesta de la reserva y "Mis turnos" informan la seña (RN-SE-02 a RN-SE-04).
- **Pasarela de pagos detrás de un puerto** (`PasarelaPago`): un adaptador simulado (desarrollo y tests) y uno de Mercado Pago en sandbox, elegidos por `PAGOS_PASARELA`, sin credenciales en el repositorio (RN-SE-10).
- **Notificación de Mercado Pago** (`POST /api/v1/public/pagos/mercadopago/notificaciones`): se acepta solo con firma válida, consulta el pago a Mercado Pago y es idempotente. Es la única ruta fuera de CSRF (PA-64, regla dura 8) (RN-SE-05).
- **Ciclo de vida de la seña** en el módulo `pagos/`, con una tarea periódica como la de C-22: vence las pendientes y cancela su turno, reintegra o retiene según cómo terminó el turno, traslada la seña al turno reprogramado y la imputa al cargo cuando el turno queda atendido, con reintegro del sobrante (RN-SE-04, RN-SE-06 a RN-SE-09).
- **Causa de la cancelación**: al cancelar un turno con seña pagada, el personal indica si la causa es del paciente o del consultorio; la API de turnos la pide y la agenda la guarda en la auditoría (RN-SE-06). Es el único cambio en `agenda/` (Sofia).
- **Cobro sin caja**: el cobro con medio `MERCADO_PAGO` no tiene caja, no entra en los totales ni en el arqueo, y no se anula desde la caja (RN-SE-08).
- **Pantallas**: la sección "Señas" en Configuración para el admin; el importe, el vencimiento y "Pagar seña" al terminar la reserva y en "Mis turnos", con el estado de cada seña; la causa en el diálogo de cancelación de la agenda.
- **Migración V23** (propuesta en la KB, a confirmar por la líder): tablas `sena` y `sena_movimiento`, y en `cobro` el medio `MERCADO_PAGO` con `caja_id` nullable, sin editar V15.

## Capabilities

### New Capabilities

- `senas`: configuración, creación, pago, vencimiento, cancelación, reprogramación, imputación y reintegro de las señas, la notificación de Mercado Pago y la privacidad de los datos que se le mandan.
- `frontend-senas`: la sección "Señas" de Configuración, la seña en la reserva online y en "Mis turnos", y la causa al cancelar desde la agenda.

### Modified Capabilities

- `cobros-pacientes`: se agrega el cobro con el medio `MERCADO_PAGO` sin caja, que no se anula desde la caja.
- `agenda-api`: se agrega la causa obligatoria al cancelar un turno con seña pagada.
- `seguridad-autorizacion`: se agrega la ruta pública de la notificación de Mercado Pago, fuera de CSRF y autenticada por la firma.
- `turnos-paciente`: "Mis turnos" informa la seña de cada turno.
- `reserva-online`: la reserva crea la seña y la informa en la respuesta.
- `frontend-configuracion-consultorio`: el `ADMIN` suma la pestaña "Señas" (agregado en el apply: el requisito "Secciones según el rol" fija la lista exacta de pestañas).

## Impact

- **Backend nuevo**: módulo `pagos/` (dominio de la seña, puerto `PasarelaPago` con adaptadores simulado y Mercado Pago, JDBC, tarea periódica y controladores). Dueño: Enzo.
- **Backend de otros dueños** (avisar en el PR): `reserva/` de Juan Sebastián (la reserva y "Mis turnos" informan la seña, por un puerto que implementa `pagos/`); `agenda/` de Sofia (la causa de la cancelación en el comando y en la auditoría); `notificaciones/` de Juan Cruz (aviso de reintegro sin importes); `shared/security/` de Juan Sebastián (la excepción de CSRF acotada a una ruta).
- **Backend propio**: `caja/` admite el cobro `MERCADO_PAGO` sin caja.
- **Base**: migración V23 (número a confirmar por la líder).
- **Configuración**: `PAGOS_PASARELA`, `MERCADOPAGO_ACCESS_TOKEN`, `MERCADOPAGO_WEBHOOK_SECRET` y `PAGOS_URL_PUBLICA` en `.env.example` sin valores reales.
- **Frontend**: `configuracion/` (sección "Señas"), `pages/reserva.ts`, `pages/mis-turnos.ts` y el diálogo de cancelación de `agenda/`, con mockups antes del apply (política `system-and-mockups`).
- **Fuera de alcance**: la seña en la agenda del personal (PA-65), señas en turnos cargados por el personal o en sobreturnos, cobros de caja con Mercado Pago, credenciales de producción y facturación (C-24).
