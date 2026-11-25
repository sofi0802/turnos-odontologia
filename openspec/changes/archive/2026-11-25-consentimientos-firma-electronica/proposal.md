# Proposal

## Why

Antes de ciertos tratamientos el consultorio necesita el **consentimiento informado** del paciente por escrito (Ley 26.529), y hoy lo resuelve en papel, fuera del sistema. El relevamiento muestra que Bilog ya ofrece "consentimientos con firma" citando la Ley 25.506 (`docs/discovery/sources/relevamiento-fuentes.md` §Bilog). C-27 es el siguiente change de clínica avanzada del post-MVP. La KB solo tenía la línea US-208 del backlog; las reglas las fijó Emanuel el 2026-11-24 y van en este mismo PR para que la líder las revise: RN-CL-09 a RN-CL-11, el detalle de US-208, DD-20 y las preguntas PA-43 a PA-52.

## What Changes

- **Plantillas de consentimiento editables por el `ADMIN`**:
  - Una plantilla tiene un título y un texto plano (sin HTML).
  - Cada edición crea una **versión nueva** numerada; las versiones anteriores no se modifican ni se borran.
  - El `ADMIN` puede desactivar una plantilla para que no se use en firmas nuevas, y reactivarla.
- **Firma electrónica en el consultorio** (Ley 25.506, art. 5). Es **firma electrónica, no firma digital**: no hay certificado de un certificador licenciado ni dispositivo del paciente.
  - En la computadora del consultorio y con la sesión del odontólogo, el paciente lee el texto de una versión concreta, escribe su nombre completo y su DNI y acepta.
  - El DNI escrito tiene que coincidir con el del paciente.
  - El sistema guarda una **copia del título y del texto firmados**, el nombre y el DNI escritos, la fecha y hora, la IP y el dispositivo (User-Agent) desde donde se firmó, y un **hash SHA-256** de todo eso en un formato canónico.
- **Documento firmado bloqueado**: nunca se modifica ni se borra, ni por la API ni en la base. Editar la plantilla después no cambia lo firmado.
- **Verificación del hash**: un endpoint recalcula el hash del documento guardado y dice si coincide.
- **Revocación con registro**: el odontólogo registra la revocación con un motivo; queda quién y cuándo. El documento firmado no cambia y su hash sigue verificando.
- **Acceso**:
  - Plantillas: el `ADMIN` las crea, edita, desactiva y ve todas sus versiones; el odontólogo ve las activas para elegir cuál firmar.
  - Consentimientos firmados (RN-CL-01): el odontólogo inicia la firma, consulta, verifica y revoca; el `ADMIN` solo consulta y verifica; la recepción y el paciente reciben `403` sin datos.
- **Migración V18** (número reservado en CHANGES.md): tablas de plantillas, versiones, consentimientos firmados y revocaciones. Las versiones, los firmados y las revocaciones son de solo agregado, con triggers.
- **Solo backend**. Las pantallas van en **C-48 `frontend-consentimientos`**, ya registrado en CHANGES.md; el contrato de la API que va a usar está en `design.md`.

## Capabilities

### New Capabilities
- `consentimientos-informados`: cubre
  - las plantillas de consentimiento con versiones y su activación;
  - la firma electrónica en el consultorio con su evidencia (copia del texto, firmante, fecha, IP, dispositivo y hash);
  - la inmutabilidad del documento firmado y la verificación de su hash;
  - la revocación con registro;
  - el control de acceso por rol y la privacidad de los datos del firmante.

### Modified Capabilities
<!-- Ninguna: ficha-clinica no cambia. Los consentimientos reutilizan sus reglas de acceso y de privacidad como requisitos propios, igual que imagenes-clinicas (C-26). -->

## Impact

- **Código**: `backend/.../clinica/` (dominio, aplicación, API e infraestructura nuevos). El módulo es de Emanuel. No se toca ningún archivo fuera de `clinica/` ni la configuración global.
- **Base de datos**: `V18__consentimientos.sql`. Flyway corre con out-of-order, así que V18 se aplica aunque V21 y V22 ya estén en `master`.
- **API**: endpoints nuevos bajo `/api/v1/consentimientos/plantillas` y `/api/v1/pacientes/{id}/consentimientos`. Ninguno existente cambia.
- **Seguridad**: `@PreAuthorize` por rol, sesión con CSRF activo. El nombre y el DNI escritos, la IP, el dispositivo, el texto y el motivo no van a logs ni a mensajes de error (regla dura 6).
- **Validez legal**: el documento tiene valor probatorio como firma electrónica; no equivale a una firma digital ni a una manuscrita. Los límites están en `design.md`, Riesgos.
- **KB**: se actualizan
  - `03_actores_y_roles.md` (dos filas en la matriz de permisos);
  - `04_modelo_de_datos.md` (entidades `PlantillaConsentimiento`, `PlantillaConsentimientoVersion`, `ConsentimientoFirmado` y `ConsentimientoRevocacion`);
  - `05_reglas_de_negocio.md` (RN-CL-09 a RN-CL-11);
  - `06_funcionalidades.md` (detalle de US-208);
  - `09_decisiones_y_supuestos.md` (DD-20, firma electrónica con evidencia y hash canónico);
  - `10_preguntas_abiertas.md` (PA-43 a PA-52, confirmadas por Emanuel el 2026-11-24).
- **Roadmap**: `CHANGES.md` ya tiene las decisiones de C-27 y el change C-48; este propose no lo modifica.
