# Design

> **Aprobación (governance CRÍTICO)**: proposal, specs, design y tasks aprobados por Sofia Indovino, la líder, el 2026-11-24 con el merge del PR #81 (confirmado por Enzo).

## Context

El motivo está en `proposal.md`, sección Why.

**Backend, lo que existe:**
- La tabla `usuario` (V3) ya tiene `profesional_id`, `activo` y las reglas que impiden dos cuentas para el mismo profesional (`usuario_profesional_unico`) y un `ODONTOLOGO` sin profesional.
- El login no usa `formLogin`. `AutenticacionController` autentica con el `AuthenticationManager` y guarda el contexto en la sesión HTTP con `HttpSessionSecurityContextRepository`.
- El principal es `UsuarioAutenticado`, que compara por `usuarioId` y guarda `activo`.
- `SecurityConfig` deja `/api/v1/**` para los tres roles del personal, y cada operación declara sus roles con `@PreAuthorize` (como `AusenciasController`).
- `PoliticaContrasena` exige de 8 a 72 caracteres, `NombreUsuario` normaliza el usuario y `CrearAdminInicial` crea el primer `ADMIN` desde el entorno.

**Frontend, lo que existe:**
- `app.ts` consulta la sesión antes de cada página privada. Sin sesión lleva a `#/mi-cuenta` (`ingreso-unico-mi-cuenta`).
- Configuración arma sus pestañas con `configuracion/pestanas.ts`, decide los permisos en `configuracion/permisos.ts` y muestra el detalle del profesional en `profesional-detalle.ts`.
- Los diálogos y mensajes comunes están en `shared/ui/dialogo.ts` y `shared/ui/mensajes.ts`.

## Goals / Non-Goals

**Goals:**
- Que el servidor imponga el cambio de la contraseña temporal y el cierre de sesiones, sin depender de la pantalla.
- No tocar el módulo `configuracion/` de Emanuel, ni los requisitos de `seguridad-autenticacion` y `frontend-sesion-personal` que C-35 e `ingreso-unico-mi-cuenta` todavía no archivaron.

**Non-Goals:**
- Un modelo general de permisos o grupos. Siguen siendo los cuatro roles fijos.
- Persistir una auditoría de las cuentas. Alcanza con el log sin contraseñas.
- Limitar los intentos de login. No es parte de este change.

## Decisions

### D1. API bajo `/api/v1/usuarios` y `/api/v1/cuenta/contrasena`
- `GET /usuarios`, `POST /usuarios`, `POST /usuarios/{id}/desactivar`, `POST /usuarios/{id}/activar` y `POST /usuarios/{id}/restablecer-contrasena`, con `@PreAuthorize("hasRole('ADMIN')")`.
- Se usan acciones con `POST`, igual que en los catálogos de C-04 (`/{id}/activar`), y no `PATCH` con el estado, para seguir la convención del proyecto.
- El cambio propio va en `POST /api/v1/cuenta/contrasena`, para los tres roles del personal. Queda fuera de `/api/v1/paciente/**` y de `/usuarios`, porque no es una operación del `ADMIN` sobre otra cuenta.
- **No cambia `SecurityConfig`**: la regla general de `/api/v1/**` ya deja afuera a los pacientes.

### D2. Contraseña temporal
Un `GeneradorContrasenaTemporal` en `usuarios/domain` usa `SecureRandom`:
- 14 caracteres de un alfabeto sin caracteres confusos (sin `0`, `O`, `1`, `l` ni `I`), con al menos una mayúscula, una minúscula y un dígito. Cumple `PoliticaContrasena` y es fácil de dictar.
- **Alternativa descartada: que el `ADMIN` la escriba.** El servidor no podría garantizar que es aleatoria, y el `ADMIN` tendería a repetir siempre la misma.
- La respuesta que la trae lleva `Cache-Control: no-store`. El DTO de la cuenta no tiene ese campo: la contraseña viaja en un DTO aparte (`CuentaCreadaResponse`).

### D3. Cierre de sesiones: verificar la cuenta en cada pedido
Un filtro de `shared/security`, ubicado después de la carga del contexto:
1. Relee la cuenta de la sesión con una consulta por id: `activo`, `password_hash` y `debe_cambiar_contrasena`.
2. Si la cuenta está inactiva o su `password_hash` ya no es el del principal, invalida la sesión y sigue como pedido sin sesión, que termina en `401 NO_AUTENTICADO`.
3. Si no, refresca `debeCambiarContrasena` en el principal.

Así, desactivar una cuenta y restablecer su contraseña cierran todas sus sesiones sin llevar un registro de sesiones. Cuando el usuario cambia su propia contraseña, el endpoint reemplaza el principal en la sesión actual con el hash nuevo, así que esa sesión sigue abierta.

**Nota del apply (2026-11-24):** el principal borra el hash de la contraseña después del login (`eraseCredentials`, C-05), así que no se compara el hash. Al autenticarse, el principal guarda una huella SHA-256 del hash, y el filtro compara esa huella con la del hash actual de la base. El comportamiento es el mismo y el hash no queda en la sesión. El filtro también cierra la sesión si la cuenta ya no existe.

**Alternativa descartada: `SessionRegistry` y `expireNow()`.** El login es manual, así que habría que registrar cada sesión a mano y publicar los eventos de sesión. Además, el registro vive en memoria y no ve los cambios hechos directo en la base.

**Costo:** una consulta por clave primaria en cada pedido autenticado, despreciable para un consultorio chico.

### D4. Contraseña pendiente impuesta por el servidor
El mismo filtro de D3 responde `403` con `ProblemDetail` y el código `CAMBIO_DE_CONTRASENA_PENDIENTE` cuando `debeCambiarContrasena` es `true`. Deja pasar `/api/v1/public/**`, `POST /api/v1/logout` y `POST /api/v1/cuenta/contrasena`.

**Alternativa descartada: solo redirigir en el frontend.** Con la contraseña temporal, que el `ADMIN` conoce, se podría seguir usando la API.

### D5. Migración V21
```sql
ALTER TABLE usuario ADD COLUMN debe_cambiar_contrasena BOOLEAN NOT NULL DEFAULT FALSE;
```
- Las cuentas que ya existen, incluido el admin inicial, quedan en `false`. El admin inicial sale del entorno y lo elige el operador, así que no se le obliga a cambiarla.
- Una fila `PACIENTE` nunca la tiene en `true`. No hace falta un `CHECK`, porque el único camino que la pone en `true` es este módulo, que rechaza el rol `PACIENTE`.

### D6. Validar el profesional sin tocar `configuracion/`
Un puerto `ProfesionalesParaCuentas` en `usuarios/application`, implementado con JDBC de solo lectura sobre la tabla `profesional`, responde si el profesional existe y está activo. Es el mismo patrón que `BusquedaPacientesJdbc` de C-36.
- Que el profesional ya tenga cuenta se verifica en `usuarios`. La restricción `usuario_profesional_unico` es la última defensa: su violación se traduce a `409 PROFESIONAL_CON_CUENTA`.
- `USUARIO_EXISTENTE` se resuelve igual, con una verificación previa más `usuario_username_unico`.

### D7. Códigos de error
- **`409`**: `USUARIO_EXISTENTE`, `PROFESIONAL_CON_CUENTA`, `PROFESIONAL_NO_DISPONIBLE` y `OPERACION_SOBRE_CUENTA_PROPIA`.
- **`422`**: `DATOS_INVALIDOS` y `CONTRASENA_ACTUAL_INCORRECTA`. El segundo es `422` y no `401` para no confundirlo con una sesión vencida, que en el frontend lleva al ingreso.
- **`404`**: `USUARIO_INEXISTENTE`.
- **`403`**: `CAMBIO_DE_CONTRASENA_PENDIENTE`.
- Todos van en `ProblemDetail` y se manejan en `UsuariosExceptionHandler`.

### D8. La protección de la propia cuenta alcanza para no quedarse sin `ADMIN`
Desactivar lo hace siempre un `ADMIN` activo, y nunca sobre su propia cuenta, así que siempre queda al menos uno activo. Una regla aparte de "último `ADMIN`" sería inalcanzable y no tendría test, por eso no se agrega.

### D9. Frontend
- **`configuracion/usuarios.ts`** (nuevo): la pestaña y su lista.
- **`configuracion/usuario-alta.ts`** (nuevo): el formulario de alta. Lo usan la pestaña y "Dar acceso".
- **`configuracion/contrasena-temporal.ts`** (nuevo): el diálogo con "Copiar". Usa `navigator.clipboard` y, si falla, selecciona el texto. Al cerrarse, vacía el nodo.
- **`pages/cambiar-contrasena.ts`** (nuevo): la página de cambio de contraseña.
- `permisos.ts` suma `gestionaUsuarios` (solo `ADMIN`) y `pestanas.ts` suma la pestaña.
- `profesional-detalle.ts` muestra "Dar acceso" o "Acceso: <usuario>" a partir de la lista de `GET /api/v1/usuarios`, que solo se pide con permiso de `ADMIN`.
- `app.ts`: el guard lleva a `#/cambiar-contrasena` cuando la sesión trae `debeCambiarContrasena`.
- `pages/mi-cuenta.ts`: después del login lleva ahí en ese mismo caso.
- `shared/api/errores.ts`: un `403` con `CAMBIO_DE_CONTRASENA_PENDIENTE` también lleva ahí.
- El menú lateral del personal suma "Mi contraseña", separado de las páginas, que lleva a `#/cambiar-contrasena`. En el celular la etapa B de C-39 oculta el usuario del encabezado, así que el menú es el camino que siempre está. Donde se ve, el usuario del encabezado también es un enlace a esa página.
- En la pantalla obligatoria no hay menú: solo el formulario y "Salir".

## Risks / Trade-offs

- **[Riesgo]** El `ADMIN` puede ver o filtrar la contraseña temporal. → **Mitigación**: es de un solo uso en la práctica, porque obliga a cambiarla. Se muestra una sola vez y no queda en logs, caché ni `localStorage`.
- **[Riesgo]** El filtro de D3 suma una consulta por pedido. → **Mitigación**: es por clave primaria. Si alguna vez pesa, se cachea por unos segundos, con el costo de cerrar las sesiones con ese retraso.
- **[Riesgo]** Conflictos con la etapa D de C-39, que reestiliza Configuración, y con `app.ts` (guard). → **Mitigación**: los archivos de la pestaña son nuevos. El orden de los PR se acuerda con Juan Cruz.
- **[Riesgo]** El archive de C-41 choque con los deltas abiertos de C-35 e `ingreso-unico-mi-cuenta`. → **Mitigación**: C-41 solo agrega requisitos en `seguridad-autenticacion` y no toca `frontend-sesion-personal`.
- **[Trade-off]** No se cambia el rol de una cuenta: para eso se desactiva y se crea otra. Es más simple y deja rastro, pero usa otro usuario.

## Migration Plan

1. Aplicar V21 con Flyway. Es aditiva y tiene valor por defecto, así que no cambia el comportamiento de las cuentas existentes.
2. Desplegar el backend y después el frontend. Un frontend viejo contra el backend nuevo sigue funcionando, salvo para una cuenta con contraseña temporal, y esas cuentas solo se crean desde el frontend nuevo.
3. **Rollback**: revertir el código deja la columna sin uso y es inofensivo. Las cuentas creadas siguen sirviendo con su contraseña.
4. La cuenta de prueba `ana.ejemplo`, insertada a mano en la base de desarrollo de Enzo, queda con `debe_cambiar_contrasena` en `false` y sigue funcionando.

## Open Questions

- **¿Qué pasa con la cuenta cuando se desactiva un profesional?** La propuesta no la toca: un `ODONTOLOGO` de un profesional inactivo sigue entrando y ve una agenda vacía. Se puede resolver después, en `configuracion/`, sin cambiar estos specs. Lo decide la líder.
- **Copiar al portapapeles en `http://`**: `navigator.clipboard` exige un contexto seguro. En `localhost` funciona. En otro host sin HTTPS se usa la selección del texto (D9).

## UI Mockups

- Design system revision: 3 (`design-system/README.md`; la aprobación de las revisiones 2 y 3 todavía está pendiente con la líder, y no cambian ningún valor de los tokens).
- Decision: yes, 2026-11-24 (cambio visual en modo `system-and-mockups`).
- Mockups: `openspec/changes/cuentas-del-personal/mockups/`, con 11 pantallas listadas en su `README.md`:
  - la pestaña "Usuarios" y sus estados;
  - el alta con cada rol y sus errores;
  - el diálogo de la contraseña temporal;
  - las confirmaciones;
  - el detalle del profesional con "Dar acceso";
  - `#/cambiar-contrasena`, obligatoria, voluntaria y con errores;
  - "Mi contraseña" en el menú del celular.
- Approval: approved (2026-11-24, by Sofia Indovino, la líder; PR #81, confirmado por Enzo).
- Preview: rendered (2026-11-24): capturas en `mockups/previews/` a 390 × 844 y 1440 × 900, sin desplazamiento horizontal ni errores de consola.
- Visual verify: passed (2026-11-24): contra el backend de desarrollo, con diferencias menores anotadas en `mockups/verify/README.md` (una corregida). Las capturas tapan los datos no ficticios de la base local.
