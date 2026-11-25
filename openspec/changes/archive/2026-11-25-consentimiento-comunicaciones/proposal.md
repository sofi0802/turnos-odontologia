# Proposal

## Why

C-11 envía recordatorios y avisos solo a pacientes con `consentimiento_comunicaciones = true` (RN-NO-03), pero hoy nada permite darlo: V7 lo deja en `false` para todos y ninguna pantalla ni endpoint lo cambia. Por eso no sale ninguna notificación, y C-15 (lista de espera) rechaza con `SIN_CONSENTIMIENTO` a todos los pacientes, incluidos los que la recepción anota por teléfono. La Ley 25.326 además pide poder demostrar cuándo, cómo y quién registró el consentimiento.

## What Changes

- **Consentimiento con historial**: un solo consentimiento para todos los mensajes (WhatsApp y email), como ya lo modela V7, con un historial de cada cambio: si se otorgó o se revocó, la fecha, el origen (cuenta del paciente, registro o recepción) y, si lo registró el personal, quién y por qué medio (teléfono o en persona). El historial no se edita ni se borra. **Migración V11**.
- **El paciente decide desde "Mi cuenta"**: la página "Mi cuenta" del paciente, que C-49 ya sumó a la barra lateral con "Mi cobertura", empieza con "Tus datos" (nombre, apellido, DNI, email y teléfono, de solo lectura) y "Mensajes del consultorio", con la opción de aceptar o dejar de recibir mensajes y la fecha del último cambio. No se agrega otra entrada a la barra lateral.
- **Casilla en el registro**: crear la cuenta ofrece "Acepto recibir mensajes del consultorio…", sin marcar por defecto. Si se marca, el consentimiento queda otorgado con origen "registro".
- **La recepción lo registra**: después de elegir un paciente con el buscador de C-36 (en "Anotar paciente" de la lista de espera y en "Nuevo turno"), `ADMIN` y `RECEPCIONISTA` ven si acepta mensajes y pueden registrar o revocar el consentimiento indicando el medio. Sirve para pacientes sin cuenta. El rechazo `SIN_CONSENTIMIENTO` de C-15 ofrece registrarlo ahí mismo.
- **El aviso de C-15 lleva a "Mi cuenta"**: el mensaje que ve el paciente cuando no puede anotarse en la lista de espera enlaza a esa página.
- **Revocar corta los mensajes**: después de revocar no se crean notificaciones nuevas ni se reintentan las fallidas, y la lista de espera deja de ofrecerle horarios.

Fuera de alcance: mostrar las notificaciones dentro de la aplicación, enviar emails o WhatsApp reales, consentimiento separado por canal, editar los datos personales y marcar si el paciente tiene WhatsApp.

## Capabilities

### New Capabilities
- `consentimiento-comunicaciones`: estado vigente e historial del consentimiento de comunicaciones del paciente, y la API para que lo gestionen el paciente y el personal.
- `frontend-consentimiento-comunicaciones`: cómo el paciente y la recepción ven, otorgan y revocan el consentimiento en la aplicación web, y a dónde llevan los avisos de falta de consentimiento.

### Modified Capabilities
- `cuentas-pacientes`: el registro acepta el consentimiento inicial y lo guarda con su historial.
- `frontend-cuenta-paciente`: la casilla del registro y la sección "Tus datos" en "Mi cuenta".
- `notificaciones-recordatorios`: revocar el consentimiento corta los avisos y los reintentos pendientes.

## Impact

- **Backend**: módulo `notificaciones/` (consentimiento, dueño Juan Cruz) con la migración **V11** (`consentimiento_comunicacion_evento`); `usuarios/` (registro y perfil, dueño Juan Sebastián: se le avisa); la lista de espera de C-15 (`agenda/listaespera`, dueña Sofia) solo consume el estado vigente.
- **API nueva**: `GET` y `PUT /api/v1/paciente/consentimiento-comunicaciones` (paciente), `GET` y `PUT /api/v1/pacientes/{id}/consentimiento-comunicaciones` (`ADMIN` y `RECEPCIONISTA`); `POST /api/v1/public/pacientes/registro` suma `aceptaMensajes` (opcional, compatible). La búsqueda de C-36 no cambia: sigue sin devolver el consentimiento.
- **Frontend** (`frontend/`, dueño Enzo: se le avisa): las secciones nuevas de `pages/mi-cuenta.ts` (que C-49 y C-50 también tocan: se coordina con Juan Sebastián), la casilla del registro, el panel de consentimiento junto al buscador y el aviso de `lista-espera/paciente.ts` (coordinado con Sofia por C-15).
- **Dependencias**: C-11 (mergeado) y la página "Mi cuenta" del paciente de C-49 (mergeada, sin archivar). La barra lateral no cambia, así que "Menú según la sesión" no se toca.
- **Governance ALTO** (datos personales): la propuesta y los mockups se revisan antes del apply.
