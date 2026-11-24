# Tasks

Criterios de aceptación: los mockups aprobados en `mockups/` (ver `design.md`, sección UI Mockups) y los escenarios de los tres deltas de `specs/`. Cada test se escribe antes del código que lo hace pasar.

## 0. Diseño (antes del apply)

- [x] 0.1 Sumar los tokens `--ds-odonto-*` a `design-system/tokens.css` y la sección "Odontograma" a `components.html`. Pasar el design system a la revisión 4 con `Approval: pending`. Verificar que el contraste de cada color sea AA sobre blanco.
- [x] 0.2 Crear los mockups HTML en `mockups/` con las pantallas listadas en `design.md`. Usar `tokens.css` y `brand.js`, y un `README.md` con `Approval: pending`. Verificar que se rendericen a 390 y 1440 px y guardar las capturas en `mockups/previews/`.
- [ ] 0.3 Registrar la aprobación de los mockups (hecha por Emanuel el 2026-11-24, en `design.md` y `mockups/README.md`) y conseguir que la líder renueve la revisión 4 del design system en `design-system/README.md`. El apply de los grupos 2 a 6 no empieza sin esto.

## 1. Navegación por rol

- [ ] 1.1 Escribir primero los tests de los cuatro escenarios de "Página Pacientes para odontólogos y administradores" en `frontend/test/navegacion.test.ts`. Verificar que fallen.
- [ ] 1.2 Sumar `roles` a `Pagina`, filtrar el menú en `layout.ts` y hacer el control en `app.ts`, que muestra el aviso sin llamar a `render`. Registrar `paginaPacientes` con su ícono. Verificar que pasen los tests de 1.1 y los de navegación existentes.

## 2. Selección del paciente

- [ ] 2.1 Crear `frontend/src/ficha/seleccion.ts`, que guarda en memoria el paciente y el turno y los borra al leerlos. Agregar `pacienteId` a `TurnoAgenda` en `agenda/consulta.ts`. Verificar con tests unitarios.
- [ ] 2.2 Escribir primero los tests de "Elegir al paciente en Pacientes" (tres escenarios) en `frontend/test/ficha-pacientes.test.ts`. Después dibujar la página con el buscador de C-36 y "Cambiar de paciente". Verificar que pasen.
- [ ] 2.3 Escribir primero los tests de "Ver la ficha desde un turno" (tres escenarios) en `frontend/test/agenda.test.ts` y de "Abrir la ficha desde un turno de la agenda". Después sumar "Ver ficha" en `agenda/vista.ts` para `ADMIN` y `ODONTOLOGO`. Verificar que pasen, junto con los tests de C-09 y C-43.

## 3. Ficha básica

- [ ] 3.1 Escribir primero los tests de "Ficha básica", "Editar la ficha básica" y "Solo lectura para el administrador" en `frontend/test/ficha-basica.test.ts`. Después crear `ficha/api.ts` (tipos y pedidos, con la validación de las respuestas) y `ficha/ficha-basica.ts`. Verificar que pasen.
- [ ] 3.2 Escribir primero los tests de "Errores al cargar la ficha". Después cargar en paralelo con "Reintentar" y sumar los mensajes de D8 a `shared/ui/mensajes.ts`. Verificar que pasen.

## 4. Odontograma

- [ ] 4.1 Escribir primero los tests del modelo en `frontend/test/odontograma-modelo.test.ts`: el estado vigente por pieza y cara, la pieza completa contra una cara más nueva, las caras por maxilar y el orden de las piezas permanentes y temporales. Después implementarlo en `ficha/odontograma-modelo.ts`. Verificar que pasen.
- [ ] 4.2 Escribir primero los tests de "Odontograma", "Estado de pieza completa" y "Odontograma accesible" en `frontend/test/odontograma.test.ts`. Después dibujar el SVG en `ficha/odontograma.ts`, con la leyenda, el selector de permanentes y temporales, y el teclado. Verificar que pasen.
- [ ] 4.3 Escribir primero los tests de "Registrar cambios en el odontograma" (cinco escenarios). Después implementar el panel, la lista de pendientes, "Guardar cambios" y la confirmación al cambiar de paciente con pendientes. Verificar que pasen.
- [ ] 4.4 Escribir primero el test de "Historial del odontograma". Después implementar el historial, que se pide al abrir la sección. Verificar que pase.
- [ ] 4.5 Sumar los estilos en `frontend/src/styles/17-ficha.css` con los tokens `--ds-odonto-*`. Verificar con el test de los módulos de estilo y que no queden colores escritos a mano.

## 5. Ausencias

- [ ] 5.1 Escribir primero el test de "Ausencias en la ficha". Después mostrar las ausencias vigentes y las deshechas. Verificar que pase.
- [ ] 5.2 Escribir primero los tests de "Marcar ausente desde la ficha" (cinco escenarios). Después implementar el botón con las condiciones de D7, reusando la tolerancia de `agenda/asistencia.ts`. Verificar que pasen.

## 6. Privacidad y verificación

- [ ] 6.1 Escribir los tests de "Datos de salud solo en la pantalla": espiar `Storage.prototype.setItem` y `console`, revisar la dirección y salir con la ficha abierta. Verificar que pasen.
- [ ] 6.2 Correr `npx tsc --noEmit`, `npm test` y `npm run build`. Verificar que estén en verde y que sigan pasando los tests de C-09, C-36, C-39 y C-43.
- [ ] 6.3 Hacer la verificación visual contra los mockups aprobados, a 390 y 1440 px, con el backend de desarrollo y datos ficticios, como `ana.ejemplo` y como `admin`. Guardar las capturas en `mockups/verify/`, revisar que la consola no tenga errores ni datos de salud, y registrar el resultado en `design.md`.
- [ ] 6.4 Correr `openspec validate frontend-ficha-clinica-odontograma --strict` y verificar que esté en verde. Marcar C-44 en `CHANGES.md` y avisar a Enzo (y a Juan Cruz por los estilos) en el PR.

## Workflow follow-up

- Archivar el change después del merge del apply: sincroniza `frontend-ficha-clinica`, `frontend-navegacion` y `frontend-agenda-personal`.
