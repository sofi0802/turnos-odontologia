# Proposal

## Why

La matriz RBAC de `knowledge-base/03_actores_y_roles.md` dice que el `ADMIN` hace el CRUD de los "Usuarios del personal", pero ningún change lo implementó. Hoy solo existe el administrador inicial que crea C-05 desde el `.env`. Para dar acceso a un odontólogo o a una recepcionista hay que insertar la fila en la tabla `usuario` a mano, con un hash BCrypt generado aparte. Así se creó la cuenta de prueba `ana.ejemplo` el 2026-11-24. Sin esto, el consultorio no puede usar los roles `RECEPCIONISTA` ni `ODONTOLOGO` ni la agenda propia de C-35. C-41 es un ajuste del MVP (fase 5b).

## What Changes

- **Backend, cuentas del personal** (`/api/v1/usuarios`, solo `ADMIN`):
  - Listar las cuentas del personal (`ADMIN`, `RECEPCIONISTA` y `ODONTOLOGO`). Las de pacientes nunca aparecen.
  - Crear una cuenta con usuario, rol y, si corresponde, el profesional vinculado:
    - `ODONTOLOGO` exige un profesional activo que todavía no tenga cuenta.
    - `ADMIN` puede vincularse a un profesional, porque el dueño puede ser el mismo odontólogo.
    - `RECEPCIONISTA` no se vincula a ningún profesional.
  - **Contraseña temporal** (decisión de Enzo del 2026-11-24): la genera el servidor, cumple la política de contraseñas y se devuelve **una sola vez**, en la respuesta que crea la cuenta o que la restablece. El servidor guarda solo su hash BCrypt.
  - Desactivar y reactivar cuentas (no se borran) y restablecer su contraseña. Desactivar una cuenta o restablecer su contraseña cierra las sesiones abiertas de esa cuenta.
  - El `ADMIN` no puede desactivar ni restablecer su propia cuenta. Como quien desactiva es siempre un `ADMIN` activo, el consultorio nunca se queda sin uno.
  - Errores de negocio como `409` con código estable y validaciones de entrada como `422`.
- **Backend, sesión con contraseña temporal**:
  - Las respuestas de login y de sesión informan `debeCambiarContrasena`.
  - Mientras sea `true`, la sesión solo puede consultar la sesión, cambiar su contraseña y salir. Cualquier otro pedido a `/api/v1/**` responde `403 CAMBIO_DE_CONTRASENA_PENDIENTE`. Lo impone el servidor, no la pantalla.
- **Backend, cambio de la propia contraseña**: cualquier usuario del personal la cambia con la contraseña actual (`POST /api/v1/cuenta/contrasena`). Al cambiarla se borra la marca de contraseña temporal.
- **Migración V21**: la columna `debe_cambiar_contrasena` en `usuario`, `false` para las cuentas que ya existen.
- **Frontend**:
  - Pestaña **"Usuarios"** en Configuración, solo para `ADMIN`: lista, alta, desactivar, reactivar y restablecer. La contraseña temporal se muestra en un diálogo que avisa que no se vuelve a mostrar.
  - **"Dar acceso"** en el detalle de un profesional sin cuenta. Abre el alta con el rol `ODONTOLOGO` y ese profesional ya elegidos.
  - Página **`#/cambiar-contrasena`**: es obligatoria después de ingresar con una contraseña temporal y también se usa para cambiarla por decisión propia.
- Ninguna contraseña ni hash en logs. La temporal solo aparece en la respuesta que la crea, y esa respuesta no se guarda en caché.

## Capabilities

### New Capabilities

- `cuentas-personal`: gestión de las cuentas del personal por el `ADMIN`. Cubre listar, crear con vínculo a un profesional, generar la contraseña temporal, desactivar y reactivar, restablecer, cerrar sesiones y las reglas del último `ADMIN` y de la propia cuenta.
- `frontend-cuentas-personal`: pantallas de las cuentas del personal. Cubre la pestaña "Usuarios", el alta desde el detalle del profesional, el diálogo de la contraseña temporal y la página de cambio de contraseña, obligatoria o voluntaria.

### Modified Capabilities

- `seguridad-autenticacion`: se agregan (ADDED) dos requisitos:
  - la sesión con contraseña temporal (`debeCambiarContrasena` y las rutas que quedan permitidas);
  - el cambio de la propia contraseña.

  No se modifica "Estado de la sesión" porque ya lo cambia C-35 (`agenda-propia-odontologo`), que todavía no está archivado.
- `frontend-configuracion-consultorio`: el requisito "Secciones según el rol" suma la pestaña "Usuarios" para `ADMIN`. `RECEPCIONISTA` y `ODONTOLOGO` no la ven.

## Impact

- **Backend**:
  - **`usuarios/`** (dueño Juan Sebastián, **se le avisa en el PR**): API, casos de uso, generador de contraseñas y adaptador de solo lectura sobre `profesional` para validar el vínculo.
  - **`shared/security/`**: registro de sesiones para cerrarlas y filtro de contraseña pendiente.
  - **`configuracion/`** (Emanuel): no se toca.
- **Base de datos**: la migración V21, reservada en CHANGES.md.
- **Frontend** (dueño Enzo): `configuracion/` (pestaña nueva y "Dar acceso"), `pages/` (cambio de contraseña), el guard de sesión de `app.ts` y `pages/mi-cuenta.ts`. La etapa D de C-39 (Juan Cruz) reestiliza Configuración, así que el orden de los PR se coordina con él.
- **Specs abiertos**: C-35 e `ingreso-unico-mi-cuenta` tienen deltas sin archivar sobre `seguridad-autenticacion` y `frontend-sesion-personal`. C-41 solo agrega requisitos en esos specs, para que los archives no choquen.
- **Governance CRÍTICO** (autenticación y cuentas): la líder aprueba proposal, specs, design y tasks antes de escribir código. Además, el design system está en modo `system-and-mockups`, así que los mockups se aprueban antes del apply.
- **Fuera de scope**:
  - invitación por email con link (post-MVP, cuando el email deje de estar simulado);
  - recuperación de contraseña sin el `ADMIN`;
  - cambiar el rol o el usuario de una cuenta existente (se desactiva y se crea otra);
  - cuentas de pacientes (C-05 y C-12);
  - auditoría persistida de las altas;
  - qué pasa con la cuenta cuando se desactiva un profesional (pregunta abierta en design).
