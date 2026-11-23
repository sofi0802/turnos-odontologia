# Proposal

## Why

El backend de la reserva online ya está en `master` (C-10 y la cuenta de paciente de C-05), pero el paciente no tiene pantallas para usarlo: hoy la página "Reserva" muestra solo su título. Sin este change, el paciente no puede crear su cuenta, reservar, ver sus turnos ni cancelarlos o reprogramarlos (US-020 a US-024, Flujo 2), y el MVP queda sin su lado público (gate 6 de CHANGES.md).

## What Changes

- **Cuenta del paciente** en páginas propias, separadas del ingreso del personal (`#/ingreso` de C-09 no cambia):
  - `#/mi-cuenta`: ingresar con email y contraseña, o crear la cuenta con nombre, apellido, DNI, email, teléfono opcional y contraseña.
  - Como el registro no inicia la sesión (spec `cuentas-pacientes`), el frontend inicia la sesión enseguida con los mismos datos.
  - Mensajes claros para `DNI_YA_REGISTRADO`, `EMAIL_YA_REGISTRADO`, credenciales incorrectas y errores por campo.
- **Reserva de un turno** en `#/reserva`, pensada para el celular:
  - Sin sesión: elegir la prestación, el profesional o "Sin preferencia" y la fecha, y ver los horarios libres.
  - Para confirmar hace falta la sesión de paciente. La elección se conserva mientras el paciente ingresa o se registra.
  - Al confirmar se muestra el número de reserva, el profesional asignado y el horario.
  - Si el horario se ocupó (`SIN_DISPONIBILIDAD`), se ofrecen las alternativas que devuelve la API.
- **Mis turnos** en `#/mis-turnos`:
  - La lista de turnos del paciente con su estado.
  - Cancelar o reprogramar con el número de reserva cuando `modificableOnline` es verdadero.
  - Con menos de 24 h, el aviso de que hay que contactar al consultorio (`CANCELACION_FUERA_DE_PLAZO`).
- **Links de los avisos de C-11**, que hoy abren la agenda porque usan rutas sin `#`:
  - `/mis-turnos/cancelar` y `/mis-turnos/reprogramar` llevan a "Mis turnos".
  - `/confirmar/{token}` muestra la página "Confirmar asistencia". El paciente confirma con un botón, sin sesión, y la página no confirma sola al abrirse.
- **Menú según quién usa la aplicación**:
  - Sin sesión o con sesión de paciente: "Reservar turno" y "Mis turnos".
  - Con sesión del personal: "Agenda" y "Configuración".
  - Al salir con una sesión de paciente se vuelve a la reserva, no al ingreso del personal.
- **Cliente de API**: el error de negocio de un `409` conserva la lista `alternativas` del `ProblemDetail`, para ofrecer otros horarios.
- **Design system**:
  - El frontend importa `design-system/tokens.css` (Revision 1) y las pantallas del paciente usan sus componentes, con `design-system/components.html` como referencia.
  - Antes del apply, el change presenta los mockups de `mockups/` para que la líder los apruebe (modo `system-and-mockups`).

Fuera de alcance:
- Elegir el canal de recordatorios y dar consentimiento de comunicaciones (C-17).
- Recuperar la contraseña.
- Pantallas de Configuración y de la ficha clínica.
- La política de cancelación configurable (C-16).
- Cambios en el backend: este change consume la API existente sin modificarla.

## Capabilities

### New Capabilities
- `frontend-cuenta-paciente`: registro e ingreso del paciente desde el frontend, acceso a sus páginas solo con sesión de paciente y salida.
- `frontend-reserva-paciente`: elección de prestación, profesional o "Sin preferencia" y fecha, horarios libres, confirmación con número de reserva y manejo de un horario ocupado.
- `frontend-turnos-paciente`: lista de los turnos del paciente, cancelación y reprogramación online con el plazo de 24 h, y la entrada desde los links de cancelar y reprogramar de los avisos.
- `frontend-confirmacion-asistencia`: la pantalla del link de confirmación del recordatorio, que confirma con un botón y muestra el resultado.

### Modified Capabilities
- `frontend-navegacion`: "Páginas disponibles" pasa a listar las páginas del paciente y el menú muestra enlaces distintos según la sesión (sin sesión o paciente, o personal). Parte del texto que deja C-09 en su delta, todavía sin archivar.
- `frontend-cliente-api`: el error de negocio de un `409` incluye los horarios de la propiedad `alternativas` cuando la respuesta los trae.

## Impact

- **Código**: solo `frontend/`.
  - Páginas nuevas en `src/pages/`: `mi-cuenta`, `reserva` y `mis-turnos`. `reserva.ts` deja de ser un título.
  - Un módulo `src/paciente/` con las llamadas y la conservación de la reserva pendiente.
  - Cambios en `app.ts`, `layout.ts` (menú por sesión), `sesion.ts` (registro e ingreso del paciente), `errores.ts`, `cliente.ts` (alternativas) y `mensajes.ts` (códigos nuevos).
  - Tests con Vitest y jsdom, con el `ServidorFalso` de los tests de C-09.
- **API usada, sin cambios**:
  - `GET /public/prestaciones`, `GET /public/profesionales` y `GET /public/disponibilidad`.
  - `POST /public/pacientes/registro`, `POST /public/login` y `GET /public/sesion`.
  - `POST /paciente/reservas`, `GET /paciente/turnos`, y `POST /paciente/reservas/{numeroReserva}/cancelar` y `/reprogramar`.
  - `POST /public/confirmaciones/{token}`.
- **Despliegue**: las direcciones de los links (`/mis-turnos/...` y `/confirmar/...`) tienen que devolver `index.html`. Vite lo hace en desarrollo; el servidor de la entrega (C-14) tiene que hacerlo también.
- **Dueños**:
  - C-12 pasa de Enzo a Emanuel en CHANGES.md. Hay que avisar a Enzo, dueño de `frontend/`, y lo aprueba Sofia en el PR.
  - Se toca código de C-09 (`app.ts`, `layout.ts` y el menú) con el aviso correspondiente.
- **Orden con C-09**: C-09 está mergeado pero no archivado. Su delta de `frontend-navegacion` tiene que archivarse antes que el de este change, porque este modifica el mismo requisito.
- **Sin migraciones ni datos de salud**: las pantallas del paciente muestran solo fecha, hora, prestación y profesional (regla dura 6).
