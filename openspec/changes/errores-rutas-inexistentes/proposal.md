## Why

Una sesión del personal que pide una ruta de `/api/v1/**` sin endpoint recibe `403 ACCESO_DENEGADO`, no `404`. Lo reprodujimos el 2026-11-23 con `GET /api/v1/no-existe` y la sesión `admin`. Esto confunde a quien prueba la aplicación. Un compañero corrió un backend sin el endpoint de C-36 y el frontend le mostró "No tenés permiso para esta acción" con los tres roles del personal, cuando el problema era que la ruta no existía. Con el mismo mecanismo, un error interno (`500`) también puede quedar disfrazado de `403`.

La causa es que, cuando un pedido termina en error, Spring envía un *ERROR dispatch* interno a `/error`. La cadena de filtros de C-05 lo evalúa contra la regla `anyRequest().denyAll()` y lo convierte en `403`.

## What Changes

- Una sesión del personal que pide una ruta inexistente bajo `/api/v1/**` recibe `404` con el código `RECURSO_INEXISTENTE` en un `ProblemDetail`.
- Un error no controlado en una operación responde `500` con el código `ERROR_INTERNO`. No incluye la traza ni el mensaje de la excepción, y no repite datos del pedido.
- Las garantías de C-05 no cambian:
  - Sin sesión sigue respondiendo `401 NO_AUTENTICADO`, también en rutas inexistentes, así que no se puede averiguar sin sesión qué rutas existen.
  - Un paciente que pide una ruta del personal, exista o no, sigue recibiendo `403 ACCESO_DENEGADO`.
  - Las rutas fuera de `/api/v1/**` (por ejemplo `/actuator/env`) siguen denegadas con `403`.
  - CSRF sigue activo y no se usa JWT.
- Pedir `/error` directamente sigue denegado. Solo se permite el despacho interno de error.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `seguridad-autorizacion`: se agregan dos requisitos: una ruta inexistente del personal responde `404` y un error interno responde `500`, con código estable y sin cambiar las reglas de `401` y `403`.

## Impact

- **Código:** `backend/.../shared/security/SecurityConfig.java` (C-05, dueño Juan Sebastián) y un manejador global de `ProblemDetail` nuevo en `shared/`. Ningún endpoint cambia.
- **Frontend:** sin cambios. `ErrorNoEncontrado` ya existe en `shared/api/errores.ts`.
- **Sin migración.**
- **Governance: CRÍTICO** (seguridad). Esta propuesta es solo análisis: no se escribe código hasta que la líder apruebe proposal, spec, design y tasks.
- No está en `CHANGES.md`. La líder decide si lo suma como fix de C-05 o como ficha propia.
