# Tasks

> Fix de C-12, governance BAJO. Rama `fix/C-12-ingreso-unico`. Cada escenario del spec tiene su test, con el `it(...)` titulado igual que el escenario. Solo datos ficticios (`recepcion`, `paciente.uno@example.com`). TypeScript `strict`, sin `any`. Los comandos se corren desde `frontend/`.

## 1. Línea de base

- [x] 1.1 Correr `npx vitest run` sin cambios y verificar que los 264 tests de `master` están en verde

## 2. Redirección de la dirección vieja (spec `frontend-navegacion`, design D2)

- [x] 2.1 En `test/navegacion.test.ts`, cambiar el test "Abrir la página de ingreso por su dirección" al comportamiento nuevo (título "Mi cuenta" y dirección `#/mi-cuenta`) y verificar que falla
- [x] 2.2 Agregar a `crearRouter` el mapa opcional de direcciones viejas y registrar `ingreso → mi-cuenta` en `app.ts`. Verificar que pasan "Abrir la página de ingreso por su dirección", "Abrir la cuenta del paciente por su dirección", "Abrir la aplicación sin dirección" y "Dirección desconocida"

## 3. Ingreso único en Mi cuenta (spec `frontend-sesion-personal`, design D1, D3, D4 y D5)

- [x] 3.1 En `test/sesion.test.ts`, pasar los tests "Recepción ingresa con credenciales correctas", "Campos vacíos" y "Contraseña incorrecta" a la página Mi cuenta (campo "Email", mensaje "Email o contraseña incorrectos."), y "Abrir la agenda sin sesión", "La sesión vence mientras se usa la agenda", "Recepción cierra la sesión" y "sale aunque el servidor falle" al destino `#/mi-cuenta`. Verificar que fallan los que dependen de las redirecciones
- [x] 3.2 En `app.ts`: sin sesión, toda página privada muestra Mi cuenta (D3), y "Salir" del personal lleva a `#/mi-cuenta` (D4). Verificar que pasan "Abrir la agenda sin sesión", "Recepción cierra la sesión" y "Un paciente abre la agenda"
- [x] 3.3 Quitar `irAlIngreso` de `ContextoPagina` y de `app.ts`, y usar `irAMiCuenta(MENSAJE_SESION_TERMINADA)` en `pages/agenda.ts` (D1). Verificar que pasan "La sesión vence mientras se usa la agenda" y "Acción sin permiso"
- [x] 3.4 Borrar `pages/ingreso.ts` y su registro en `PAGINAS`, y actualizar los comentarios que nombran la página de ingreso del personal. Verificar con `grep -rn "irAlIngreso\|paginaIngreso\|#/ingreso" src` que solo queda la redirección de D2
- [x] 3.5 Actualizar en `test/buscador-pacientes.test.ts` "Sesión vencida durante la búsqueda" (de C-36) a la dirección `#/mi-cuenta` y verificar que pasa

## 4. Ingreso del personal en Mi cuenta (spec `frontend-cuenta-paciente`)

- [x] 4.1 Verificar que el test "Recepción ingresa desde la página del paciente" de `test/cuenta-paciente.test.ts` pasa sin cambios y que "Salida del paciente" (`El paciente sale`) sigue llevando a `#/reserva`

## 5. Verificación integral

- [x] 5.1 Correr `npx vitest run` y `TZ=UTC npx vitest run` y verificar que todo está en verde
- [x] 5.2 Correr `npm run build` (incluye `tsc` con `strict`) y verificar que compila sin errores
- [x] 5.3 Correr `openspec validate ingreso-unico-mi-cuenta --strict` y verificar que es válido
- [x] 5.4 Agregar en `CHANGES.md`, ficha `[C-12]`, la línea del fix `ingreso-unico-mi-cuenta` (rama `fix/C-12-ingreso-unico`)

## Workflow follow-up

- Abrir el PR de `fix/C-12-ingreso-unico` a `master` avisando a Enzo (dueño de `frontend/` y de los specs de C-09). Sofia revisa y mergea.
- Archivar `frontend-configuracion-consultorio` (C-34) antes que este change, porque los dos modifican "Páginas disponibles" (ver design, Risks).
- Archivar con `openspec archive ingreso-unico-mi-cuenta` y verificar los specs actualizados.
