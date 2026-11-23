# Tasks

> Strict TDD en cada tarea de código: RED (el test falla por la razón correcta) → GREEN → TRIANGULATE → REFACTOR. Un test por escenario del spec, con el nombre del escenario. TypeScript con `strict`, sin `any` y sin frameworks. Los tests corren con `cd frontend && npm test` (Vitest) y el chequeo de tipos con `npm run build`. Los tests de C-09, C-12 y C-33 a C-36 siguen pasando, salvo los que dan por hecho la Agenda como página inicial o el destino viejo de "Salir": se actualizan y se informa cada uno. Datos ficticios (Paciente Uno, `recepcion`).

## 0. Línea base

- [x] 0.1 Verificar qué hace `#/ingreso` en la aplicación real, con y sin sesión, y dejarlo en `verify/ingreso.md`. Verificar que el archivo tiene la tabla de resultados y la conclusión (hecho el 2026-11-23: redirige a `#/mi-cuenta`; falta que el personal con sesión siga a la Agenda)
- [ ] 0.2 Correr `cd frontend && npm test` y `npm run build` sobre la rama y registrar la cantidad de tests; verificar que todo pasa antes de tocar código

## 1. Diseño: mockups aprobados (compuerta antes del apply)

- [ ] 1.1 Crear `mockups/` con la portada sin sesión (con "Acceso personal" arriba a la derecha, los dos botones y los tres pasos), la portada con sesión de paciente y con sesión del personal, y "Mi cuenta" con la indicación para el personal, con HTML y CSS locales que importan `design-system/tokens.css` y `brand.js`; renderizarlos a 390 × 844 y 1440 × 900, guardar las capturas en `mockups/previews/` y completar su `README.md`. Verificar abriendo las capturas y que no hay desplazamiento horizontal
- [ ] 1.2 Presentar los mockups y registrar la aprobación de la líder en `design.md` (`Approval: approved (<fecha>, by user)`, `Preview: rendered`). Verificar que la sección UI Mockups lo dice; no empezar el apply sin esto

## 2. Router y página inicial

- [ ] 2.1 RED: escribir en `navegacion.test.ts` "Abrir la portada por su dirección", "Abrir la aplicación sin dirección", "Dirección desconocida", "Abrir la aplicación sin dirección con sesión del personal" y "Abrir la página de ingreso por su dirección" y verlos fallar; GREEN: crear la página `inicio` (ruta `inicio`, pública, sin menú) con un título y pasarla como página inicial a `crearRouter`, sin tocar `REDIRECCIONES`; TRIANGULATE: con y sin sesión, `#/ingreso` sigue a `#/mi-cuenta`; REFACTOR: acotar el cambio a `app.ts` y a la página. Verificar con `npm test -- navegacion`

## 3. La portada

- [ ] 3.1 RED: escribir `portada.test.ts` con "Abrir la portada sin sesión", "La portada no envía datos", "Reservar sin registrarse" e "Ingresar a la cuenta" y verlos fallar; GREEN: dibujar la portada (título, descripción, los tres pasos y los botones "Reservar turno" e "Ingresar a mi cuenta") con los textos de `brand.md`; TRIANGULATE: un único `h1`, los botones son enlaces con la dirección de destino, sin pedidos salvo `GET /public/sesion`; REFACTOR: separar los textos de la estructura. Verificar con `npm test -- portada`
- [ ] 3.2 RED: escribir "Sin recursos de otros orígenes" y verlo fallar; GREEN: crear `pages/inicio-ilustraciones.ts` con las ilustraciones SVG propias, creadas con el DOM, con colores de `tokens.css` y `aria-hidden="true"`; TRIANGULATE: ningún `<img>`, `<link>` ni `<script>` con otro origen, ni `url(http` en los estilos; REFACTOR: una ilustración por función. Verificar con `npm test -- portada`

## 4. Encabezado y acceso del personal

- [ ] 4.1 RED: escribir "El botón del personal en el celular" y "El personal entra desde la portada", y "Volver a la portada desde otra página", y verlos fallar; GREEN: la página `inicio` pide al encabezado el texto "Acceso personal" en lugar de "Ingresar" y la marca pasa a ser un enlace a `#/inicio`; TRIANGULATE: en las demás páginas públicas el encabezado sigue con "Ingresar" y el menú no cambia; REFACTOR: dejar la propiedad opcional documentada en `Pagina`. Verificar con `npm test -- portada navegacion`

## 5. Sesión

- [ ] 5.1 RED: escribir en `portada.test.ts` "Paciente con sesión en la portada" y "Personal con sesión en la portada" y verlos fallar; GREEN: la portada muestra los botones del rol cuando llega `sesionLista`, sin redirigir; TRIANGULATE: el encabezado muestra el usuario y "Salir", no aparecen "Ingresar a mi cuenta" ni "Acceso personal"; REFACTOR: reutilizar `esPaciente` y `esPersonal`. Verificar con `npm test -- portada`
- [ ] 5.2 RED: escribir en `cuenta-paciente.test.ts` "Personal con sesión abre su cuenta" y "Personal con sesión abre la dirección vieja del ingreso" y verlos fallar; GREEN: extender el cierre de `dibujar` en `mi-cuenta.ts` para llevar al personal a la Agenda; TRIANGULATE: el paciente sigue yendo a Mis turnos; REFACTOR: unificar con `seguirConSesion`. Verificar con `npm test -- cuenta-paciente sesion`
- [ ] 5.3 RED: escribir "Recepción cierra la sesión", "El cierre falla en el servidor" y "El paciente sale" con el destino nuevo y verlos fallar; actualizar los tests viejos que esperaban Mi cuenta o la reserva; GREEN: `alSalir` lleva a `#/inicio` para los dos roles y conserva el borrado de la reserva pendiente; TRIANGULATE: el cierre sigue enviando `POST /logout` con el token CSRF y falle o no; REFACTOR: quitar el desvío por rol. Verificar con `npm test -- sesion cuenta-paciente`
- [ ] 5.4 RED: escribir "Se ve la indicación" y verlo fallar; GREEN: el párrafo para el personal debajo del formulario de ingreso, sin cambiar la etiqueta "Email" ni los mensajes; TRIANGULATE: el registro del paciente no lo muestra; REFACTOR: texto como constante. Verificar con `npm test -- cuenta-paciente`

## 6. Estilos

- [ ] 6.1 RED: escribir en `responsivo.test.ts` la comprobación de "La portada en el celular" (reglas de CSS: botones de al menos 44 px, columnas que se apilan, sin ancho fijo mayor a 320 px) y verla fallar; GREEN: agregar los estilos de la portada con los tokens del design system, según los mockups aprobados; TRIANGULATE: 320 px de ancho; REFACTOR: reutilizar las clases de botones existentes. Verificar con `npm test -- responsivo`

## 7. Verificación integral

- [ ] 7.1 Recorrer los deltas y verificar que los 24 escenarios tienen un test con el nombre del escenario, sin duplicados. Verificar con un `grep` de los nombres
- [ ] 7.2 Correr `npm test` y `npm run build` completos y verificar que pasan, con la cantidad de 0.2 más los nuevos y la lista de tests viejos que se actualizaron
- [ ] 7.3 Verificación visual contra los mockups aprobados: con el backend y el frontend levantados y las cuentas de prueba (`cuentas-de-prueba.local.md`), abrir la portada sin sesión, con paciente y con personal, y "Mi cuenta", a 390 × 844 y 1440 × 900; guardar las capturas en `mockups/verify/`, registrar las diferencias por prioridad, inspeccionar la consola y las llamadas de red. Repetir las redirecciones de `#/ingreso` contra el backend real. Completar `Visual verify` en `design.md`
- [ ] 7.4 Correr `openspec validate frontend-portada-y-acceso --strict` y verificar que el change es válido

## Workflow follow-up

- Abrir el PR de `change/C-37-frontend-portada-y-acceso` a `master`; Sofía revisa y mergea (nunca commitear directo a `master`).
- Avisar en el PR a Enzo (dueño de `frontend/`) y a Emanuel (los mismos requisitos que `ingreso-unico-mi-cuenta`). Avisar a Enzo que su ficha `frontend-nuevo-turno-guiado` tiene el mismo número C-37 y tiene que renumerarse.
- Archivar con `openspec archive frontend-portada-y-acceso` **solo después del archive de `ingreso-unico-mi-cuenta`**; verificar los specs archivados.
- Marcar `[x]` en C-37 de `CHANGES.md` recién después del merge.
