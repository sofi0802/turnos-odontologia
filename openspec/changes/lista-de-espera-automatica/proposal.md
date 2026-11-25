# Proposal

## Why

Cuando un paciente cancela, el horario queda libre (RN-AG-08), pero nadie se entera. Si la cancelación es con poca anticipación, el sillón queda vacío mientras otros pacientes esperan un turno más cercano. Ninguno de los 16 sistemas del Discovery ofrece una lista de espera automática (informe, C.3), y es el primer diferenciador del post-MVP (C-15, US-100). La KB se amplió el 2026-11-24 con las decisiones de la líder: RN-LE-01 a RN-LE-09, Flujo 5 y DD-21.

## What Changes

- **Inscripción** en la lista de espera con la prestación, un profesional o "sin preferencia" y un rango de fechas de hasta 60 días. El paciente se anota desde su cuenta; la recepción y el administrador anotan a cualquier paciente. Una sola inscripción activa por paciente y prestación, y solo con consentimiento de comunicaciones (RN-LE-01).
- **Ofertas automáticas**: al cancelar o reprogramar un turno activo que no empezó, el horario se le ofrece al primer paciente compatible por orden de llegada, de a uno por vez (RN-LE-02 a RN-LE-04). Compatible = su prestación entra en ese horario con ese profesional y cumple todas las reglas de la agenda.
- **Plazo para aceptar** configurable por el administrador: de 1 a 24 h, 2 h por defecto. La oferta vence con el plazo o con el inicio del horario (RN-LE-05).
- **Respuesta desde un link con token**, sin cuenta: Aceptar crea el turno `RESERVADO`, y "No me interesa" pasa el horario al siguiente. El horario no se reserva mientras se ofrece (DD-21): si se ocupó, la aceptación se rechaza con `OFERTA_NO_DISPONIBLE` (RN-LE-06, RN-LE-07).
- **Mensaje sin datos de salud** por el canal del paciente: no nombra la prestación (RN-LE-08).
- **Tarea programada** que vence ofertas e inscripciones y pasa los horarios al siguiente (RN-LE-09).
- **Frontend**:
  - página **Lista de espera** para la recepción y el administrador: la lista en orden, las ofertas pendientes, anotar y dar de baja;
  - sección **Lista de espera** en Mis turnos, con la baja propia;
  - "Anotarme en la lista de espera" en la reserva cuando un día no tiene horarios;
  - pantalla pública de la oferta (`#/oferta`);
  - plazo en la pestaña Margen de Configuración.
- Migración **V9** (número reservado en CHANGES.md).

## Capabilities

### New Capabilities
- `lista-espera`: inscripción, ofertas por orden de llegada al liberarse un horario, plazo configurable, aceptación y rechazo por token, vencimientos, baja y control de acceso por rol.
- `frontend-lista-espera`: las pantallas de la lista de espera para el personal y para el paciente, la pantalla pública de la oferta y el campo del plazo en Configuración.

### Modified Capabilities
- `notificaciones-recordatorios`: se agrega el aviso de la oferta de la lista de espera, con las mismas reglas de consentimiento, canal, contenido y reintentos que los demás avisos.

## Impact

- **Código backend**:
  - `backend/.../agenda/listaespera/`: es nuevo, de Sofia. Es el lugar que reservó CHANGES.md.
  - `ServicioAgenda`, también de Sofia: avisa a la lista después de cancelar o reprogramar, en la misma transacción.
  - `notificaciones/`, de Juan Cruz: el tipo `OFERTA_LISTA_ESPERA` y su mensaje. Se le avisa y revisa el PR.
  - La clave de configuración del plazo sigue el patrón del margen de `configuracion/`, de Emanuel. Se le avisa.
  - Las rutas del paciente siguen el patrón de `reserva/`, de Juan Sebastián. Se le avisa.
- **Código frontend**: `frontend/`, de Enzo. Se le avisa. Hay mockups previos al apply, por la política `system-and-mockups`.
- **Base de datos**: `V9__lista_espera.sql` con dos tablas y la clave `lista_espera_plazo_horas`. Flyway corre con out-of-order, así que V9 se aplica aunque haya migraciones más altas en `master`.
- **API**: endpoints nuevos para el personal (`/api/v1/lista-espera`), para el paciente (`/api/v1/paciente/lista-espera`), públicos para la oferta (`/api/v1/public/ofertas/{token}`) y para el plazo (`/api/v1/configuracion/lista-espera`). Ningún endpoint existente cambia su contrato.
- **Seguridad**: sesión con CSRF y `@PreAuthorize` por rol. El token se guarda como hash, y el `GET` público no lo consume. No hay datos de salud en mensajes ni logs (reglas duras 6 y 8).
- **Garantías contra solapamientos**: no cambian (regla dura 5). La aceptación crea el turno por el mismo caso de uso de la reserva online.
