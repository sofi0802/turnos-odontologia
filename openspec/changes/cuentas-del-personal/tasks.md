# Tasks

Cada escenario de la spec tiene un test con el mismo nombre. Los tests de integración usan PostgreSQL real y llevan `@Tag("integration")`. Comandos del backend desde `backend/` y del frontend desde `frontend/`.

## 0. Aprobación, línea de base y mockups (antes de escribir código)

- [ ] 0.1 Registrar en `design.md` la aprobación de la líder de proposal, specs, design y tasks (governance CRÍTICO). Verificar que figura con fecha; no empezar el grupo 1 sin esto
- [x] 0.2 Avisar a Juan Sebastián (dueño de `usuarios/` y `shared/security/`) y acordar con Juan Cruz el orden respecto de la etapa D de C-39. Verificar dejando la constancia en el PR de la propuesta *(2026-11-24: Enzo habló con los dos)*
- [ ] 0.3 Correr `./gradlew build` y, en `frontend/`, `npm test` y `npm run build` sobre la rama, y anotar acá la cantidad de tests. Verificar que todo pasa antes de tocar código
- [x] 0.4 Crear `mockups/` con las pantallas de la sección UI Mockups de `design.md`, con HTML y CSS locales que importan `design-system/tokens.css` y `brand.js`. Renderizarlas a 390 × 844 y 1440 × 900, guardar las capturas en `mockups/previews/` y completar su `README.md`. Verificar abriendo las capturas y que no hay desplazamiento horizontal *(2026-11-24: 11 pantallas y 22 capturas, sin desplazamiento horizontal ni errores de consola; ver `mockups/README.md`)*
- [ ] 0.5 Presentar los mockups y registrar la aprobación de la líder en `design.md` (`Approval: approved (<fecha>, by user)`, `Preview: rendered`). Verificar que la sección UI Mockups lo dice; no empezar el grupo 5 sin esto

## 1. Modelo y migración

- [ ] 1.1 Crear `V21__cuentas_del_personal.sql` con la columna `debe_cambiar_contrasena` (D5) y sumarla a `UsuarioEntity`, `Usuario` y `UsuarioRepositoryJpa`. Verificar con un test de integración que una cuenta existente queda en `false` y que `./gradlew build` pasa
- [ ] 1.2 RED: tests unitarios de `GeneradorContrasenaTemporal` (D2): largo, alfabeto sin caracteres confusos, mayúscula, minúscula y dígito, cumple `PoliticaContrasena` y dos llamadas dan valores distintos. GREEN: implementarlo en `usuarios/domain`. Verificar que pasan

## 2. API de cuentas (spec `cuentas-personal`)

- [ ] 2.1 RED: tests de "Operaciones de cuentas solo para el administrador" (los tres escenarios). GREEN: crear `UsuariosController` con `@PreAuthorize("hasRole('ADMIN')")` (D1). Verificar que pasan
- [ ] 2.2 RED: "Listar las cuentas". GREEN: `GET /api/v1/usuarios` con un DTO sin contraseña ni hash. Verificar que pasa y que el JSON no tiene `passwordHash`
- [ ] 2.3 RED: "Alta de una recepcionista", "La contraseña temporal no se vuelve a mostrar" y "Usuario normalizado". GREEN: el caso de uso de alta con el generador, el hash BCrypt, `debeCambiarContrasena` en `true`, `CuentaCreadaResponse` y `Cache-Control: no-store` (D2). Verificar que pasan
- [ ] 2.4 RED: los seis escenarios de "Vínculo de la cuenta con un profesional". GREEN: el puerto `ProfesionalesParaCuentas` con JDBC de solo lectura (D6) y las reglas por rol. Verificar que pasan y que no cambió ningún archivo de `configuracion/`
- [ ] 2.5 RED: los tres escenarios de "Validación del alta". GREEN: Bean Validation, verificación previa de `USUARIO_EXISTENTE` y traducción de la violación de `usuario_username_unico` y `usuario_profesional_unico` a sus `409` (D6, D7) en `UsuariosExceptionHandler`. Verificar que pasan
- [ ] 2.6 RED: los tres escenarios de "Desactivar y reactivar una cuenta". GREEN: las dos acciones y `404 USUARIO_INEXISTENTE` para un id inexistente o de un paciente. Verificar que pasan
- [ ] 2.7 RED: "Restablecer la contraseña de un odontólogo". GREEN: la acción con contraseña temporal nueva y `no-store`. Verificar que pasa
- [ ] 2.8 RED: los tres escenarios de "Protección de la propia cuenta". GREEN: `409 OPERACION_SOBRE_CUENTA_PROPIA` comparando con el usuario de la sesión (D8). Verificar que pasan
- [ ] 2.9 RED: "Alta registrada sin contraseña", capturando el log con `OutputCaptureExtension`. GREEN: el log de cada operación con el `ADMIN` y la cuenta, sin contraseña ni hash. Verificar que pasa

## 3. Sesión y contraseña propia (spec `seguridad-autenticacion`)

- [ ] 3.1 RED: los cuatro escenarios de "Sesión con contraseña temporal". GREEN: sumar `debeCambiarContrasena` a `SesionResponse` (login y sesión) y el filtro de `shared/security` que responde `403 CAMBIO_DE_CONTRASENA_PENDIENTE` fuera de las rutas permitidas (D4). Verificar que pasan y que los tests de C-05 y C-35 siguen pasando
- [ ] 3.2 RED: los dos escenarios de "Cierre de las sesiones de una cuenta" (spec `cuentas-personal`). GREEN: en el mismo filtro, releer la cuenta e invalidar la sesión si está inactiva o cambió su hash (D3). Verificar que pasan
- [ ] 3.3 RED: los cinco escenarios de "Cambio de la propia contraseña". GREEN: `POST /api/v1/cuenta/contrasena`, que reemplaza el principal de la sesión actual para que siga abierta (D3) y responde `422 CONTRASENA_ACTUAL_INCORRECTA` (D7). Verificar que pasan
- [ ] 3.4 Correr `./gradlew build` con los tests de integración. Verificar que todo pasa y guardar la salida en `docs/evidencia/c41-backend.txt`

## 4. Cliente de la API en el frontend

- [ ] 4.1 RED: tests de `configuracion/api.ts` (o un módulo nuevo) para listar, crear, desactivar, activar, restablecer y cambiar la propia contraseña, con los tipos de `CuentaPersonal` y `CuentaCreada` (sin `any`). GREEN: implementarlos. Verificar con `npm test`
- [ ] 4.2 RED: `debeCambiarContrasena` en `consultarSesion` y en la respuesta del login, y que un `403 CAMBIO_DE_CONTRASENA_PENDIENTE` se reconoce en `shared/api/errores.ts`. GREEN: implementarlo. Verificar con `npm test`

## 5. Pantallas (specs `frontend-cuentas-personal` y `frontend-configuracion-consultorio`)

- [ ] 5.1 RED: los escenarios modificados de "Secciones según el rol", incluido "Recepción no ve la pestaña Usuarios". GREEN: `gestionaUsuarios` en `permisos.ts` y la pestaña en `pestanas.ts` (D9). Verificar que pasan los tests nuevos y los de C-34
- [ ] 5.2 RED: "Ver las cuentas del consultorio" y los cuatro escenarios de "Alta de una cuenta del personal". GREEN: `configuracion/usuarios.ts` y `usuario-alta.ts`. Verificar que pasan
- [ ] 5.3 RED: los dos escenarios de "Contraseña temporal mostrada una sola vez". GREEN: `configuracion/contrasena-temporal.ts` con "Copiar" y con el nodo vaciado al cerrar. Verificar que pasan y que no se escribe en `localStorage` ni en `sessionStorage`
- [ ] 5.4 RED: los tres escenarios de "Dar acceso desde el detalle del profesional". GREEN: `profesional-detalle.ts`. Verificar que pasan y que con `recepcion` no se pide `GET /api/v1/usuarios`
- [ ] 5.5 RED: los tres escenarios de "Desactivar, reactivar y restablecer desde la lista" y "Usuario repetido" de "Errores de las cuentas". GREEN: las acciones con confirmación y el mapa de mensajes. Verificar que pasan
- [ ] 5.6 RED: los dos escenarios de "Cambio obligatorio de la contraseña temporal" y los cuatro de "Formulario de cambio de contraseña". GREEN: `pages/cambiar-contrasena.ts`, el guard de `app.ts`, el paso posterior al login en `pages/mi-cuenta.ts`, "Mi contraseña" en el menú lateral y el enlace del usuario en el encabezado. Verificar que pasan y que los tests de `ingreso-unico-mi-cuenta`, C-35 y C-37 siguen pasando
- [ ] 5.7 Correr `npm test` y `npm run build` y anotar la cantidad de tests. Verificar que todo pasa y que no hay `any` (`npm run build` con `strict`)

## 6. Verificación integrada

- [ ] 6.1 Con PostgreSQL, el backend y Vite locales, recorrer el flujo completo: `admin` da acceso a Ana Ejemplo, ella ingresa con la temporal y la cambia, `admin` la desactiva con la sesión de ella abierta y su siguiente pedido la manda al ingreso. Comparar cada pantalla con su mockup aprobado a 390 × 844 y 1440 × 900, guardar las capturas en `mockups/verify/` y registrar `Visual verify` en `design.md`. Verificar que no hay errores de consola ni pedidos fallidos inesperados
- [ ] 6.2 Marcar C-41 en `CHANGES.md` con el PR y la evidencia. Verificar que la ficha dice el estado real

## Workflow follow-up

- Abrir el PR de la propuesta para que la líder la revise antes del grupo 1.
- Después del merge del apply, archivar el change (`/opsx:archive cuentas-del-personal`) en un PR aparte, después de los archives de C-35 e `ingreso-unico-mi-cuenta`.
