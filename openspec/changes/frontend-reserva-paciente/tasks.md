# Tasks

Cada escenario de las specs tiene un test con el mismo nombre. Los comandos se corren desde `frontend/`. Antes de empezar, `npm test` pasa con 99 tests.

**Gate de diseño:** no empezar el apply hasta que los mockups de `mockups/` tengan `Approval: approved` en su `README.md` y en la sección UI Mockups de `design.md`. Los mockups aprobados son el criterio de aceptación visual de cada pantalla, y las clases y valores salen de `design-system/` Revision 1 (design D10).

## 1. Cliente de API (`frontend-cliente-api`)

- [ ] 1.1 Sumar `HorarioAlternativo` y `ErrorNegocio.alternativas` en `src/shared/api/errores.ts`, leer `alternativas` en `leerProblemDetail` validando su forma y pasarlas al construir el error del `409` en `cliente.ts` (design D7). Escribir en `test/cliente.test.ts` "Rechazo con alternativas", "Rechazo sin alternativas" y "Alternativas incompletas", y verificar que pasan junto con los 18 tests previos de ese archivo
- [ ] 1.2 Sumar a `MENSAJES_POR_CODIGO` los códigos de D8 con los textos de los specs. Verificar en `test/mensajes.test.ts` que cada código nuevo devuelve su texto y que `RESERVA_INEXISTENTE` en un `404` usa la tabla

## 2. Fechas, sesión y estructura de páginas

- [ ] 2.0 Importar `design-system/tokens.css` al principio de `src/styles.css` con `@import`, borrar del `:root` local las variables que ya dan los alias y sumar las clases de componentes de D10 copiadas de `design-system/components.html`, solo con variables `--ds-*`. Verificar que `npm run build` y `npm run dev` cargan los tokens (la agenda se ve igual que antes) y que los 99 tests previos siguen pasando
- [ ] 2.1 Sumar `fechaLarga` a `src/shared/fecha/zona.ts` (D1). Verificar en `test/zona.test.ts` que `2026-12-01T13:30:00Z` da "martes 1 de diciembre" y que el resultado es el mismo con `TZ=UTC npm test`
- [ ] 2.2 Sumar `registrarPaciente` y `esPaciente` a `src/shared/sesion/sesion.ts`. Verificar con un test unitario que el registro envía `POST /public/pacientes/registro` sin la repetición de la contraseña y que pide la sesión antes si no existe la cookie `XSRF-TOKEN`
- [ ] 2.3 Reemplazar `privada` por `acceso` y `menu` en `Pagina` y generalizar la guarda de `app.ts` (D2). Marcar Agenda con `acceso: 'personal'` y `menu: 'personal'`, y Configuración con `menu: 'personal'`. Verificar que los 8 escenarios de `test/sesion.test.ts` siguen pasando sin cambiar su nombre ni lo que comprueban
- [ ] 2.4 Filtrar el menú con la sesión, mostrar "Ingresar" sin sesión y llevar la salida del paciente a `#/reserva` en `layout.ts` y `app.ts` (D3).
  - Escribir en `test/navegacion.test.ts` "Menú de un visitante", "Menú del personal" e "Ir de Reservar turno a Mis turnos".
  - Actualizar "Abrir la página de reserva por su dirección", "Abrir el menú en el celular", "Elegir una página en el celular" y "Menú siempre visible en la computadora" al texto nuevo del spec.
  - Verificar que pasan todos los de `frontend-navegacion`.

## 3. Cuenta del paciente (`frontend-cuenta-paciente`)

- [ ] 3.1 Crear `src/pages/mi-cuenta.ts` con la sección "Ingresar" (D6), como en el mockup `mi-cuenta.html`. Escribir en un `test/cuenta-paciente.test.ts` nuevo "Ingreso correcto", "Campos vacíos en el ingreso", "Contraseña incorrecta", "Recepción ingresa desde la página del paciente", "Paciente con sesión abre su cuenta" y, en `navegacion.test.ts`, "Abrir la cuenta del paciente por su dirección". Verificar que pasan
- [ ] 3.2 Sumar la sección "Crear cuenta" con su validación local y el login después del `201`. Escribir "Registro con datos válidos", "DNI con formato inválido", "Contraseñas distintas", "Registro e ingreso seguidos" y "Falla el ingreso después del registro", y verificar que pasan
- [ ] 3.3 Mostrar los rechazos del registro en su campo. Escribir "DNI ya registrado", "Email ya registrado" y "Error de validación del servidor", y verificar que pasan
- [ ] 3.4 Escribir "Mis turnos sin sesión", "El personal abre Mis turnos", "La sesión vence en Mis turnos", "El paciente sale" y "El menú cambia al ingresar como paciente" sobre la guarda de 2.3 y una página `mis-turnos` mínima. Verificar que pasan

## 4. Reserva (`frontend-reserva-paciente`)

- [ ] 4.1 Crear `src/paciente/catalogo.ts`, `disponibilidad.ts` y `horarios.ts`, y la página `src/pages/reserva.ts` con los tres pasos y sus pantallas 1 y 2 como en los mockups `reserva-paso-1.html` y `reserva-paso-2.html` (D1 y D10). Escribir en un `test/reserva-paciente.test.ts` nuevo, con `vi.setSystemTime` el 2026-12-01:
  - "Pasar del paso 1 al 2", "Continuar sin elegir", "Volver sin perder lo elegido" y "Prestaciones a elegir".
  - "Profesionales de la prestación elegida" y "Fecha inicial y días pasados".
  - "Horarios sin preferencia", "Horarios de un profesional elegido" y "Horario elegido".
  - "Día completo" y "Recepción abre la reserva".

  Verificar que pasan.
- [ ] 4.2 Sumar el resumen (paso 3, mockup `reserva-paso-3.html`) y la reserva pendiente en `sessionStorage` en `src/paciente/reserva.ts` (D4). Escribir "Resumen sin preferencia", "Confirmar sin sesión" y "Vuelta a la reserva después de ingresar", y verificar que pasan, incluido que una reserva pendiente con forma inválida se ignora
- [ ] 4.3 Confirmar con sesión enviando el `inicio` de la API (D5), con el comprobante del mockup `reserva-confirmada.html`. Escribir "Reserva sin preferencia confirmada" y "Doble envío", y verificar que pasan
- [ ] 4.4 Manejar los rechazos. Escribir "Horario tomado con alternativas", "Elegir una alternativa", "Horario tomado sin alternativas" y "El horario pasó mientras se elegía", y verificar que pasan

## 5. Mis turnos (`frontend-turnos-paciente`)

- [ ] 5.1 Crear `src/paciente/turnos.ts` y completar `src/pages/mis-turnos.ts` con "Próximos turnos" y "Turnos anteriores", como en el mockup `mis-turnos.html`. Escribir en un `test/turnos-paciente.test.ts` nuevo "Próximos y anteriores", "Sin turnos" y "Turno dentro y fuera del plazo", y verificar que pasan
- [ ] 5.2 Sumar la cancelación con confirmación. Escribir "Cancelación confirmada", "Cancelación desistida", "Plazo vencido mientras la página estaba abierta" y "Turno que ya no existe", y verificar que pasan
- [ ] 5.3 Sumar "Cambiar horario" reusando `disponibilidad.ts` y `horarios.ts`. Escribir "Horarios para cambiar con el mismo profesional", "Cambio confirmado", "Cambio sin preferencia" y "Nuevo horario ocupado", y verificar que pasan

## 6. Links de los avisos (`frontend-turnos-paciente`, `frontend-confirmacion-asistencia`)

- [ ] 6.1 Crear `src/shared/ui/entrada.ts` y llamarlo en `main.ts` antes del router (D9). Escribir "Link de cancelar sin sesión" y "Link de reprogramar con sesión", y verificar que pasan y que la traducción no suma entradas al historial
- [ ] 6.2 Crear `src/pages/confirmar.ts`, como en el mockup `confirmar-asistencia.html`. Escribir en un `test/confirmacion.test.ts` nuevo "Abrir el link del recordatorio", "Confirmación correcta", "Link ya usado", "Link inválido" y "Turno que ya no se puede confirmar", y verificar que pasan, incluido que `#/confirmar` sin token muestra el aviso de link inválido sin botón

## 7. Responsivo y verificación integral

- [ ] 7.1 Revisar que `styles.css` cumpla D10 en el celular (controles uno debajo del otro, `.chip` y botones de 44 px). Escribir en `test/responsivo.test.ts` la parte comprobable en jsdom de "Reserva en el celular" y "Mis turnos en el celular", y verificar que siguen pasando los tests responsivos de C-09
- [ ] 7.2 Recorrer el flujo completo contra el backend local (perfil `dev` con el seed de C-04 y C-07):
  - Registrar un paciente ficticio.
  - Reservar "Sin preferencia" en un día hábil dentro de las franjas.
  - Ver el turno en "Mis turnos", cambiarle el horario y cancelarlo.
  - Abrir `/confirmar/{token}` con un token inventado y comprobar el aviso de link inválido.

  Capturar `#/reserva`, `#/mi-cuenta`, `#/mis-turnos` y `#/confirmar` a 390 × 844 y 1440 × 900 en `openspec/changes/frontend-reserva-paciente/mockups/verify/`, compararlas con los mockups aprobados (diferencias como blocker, major o minor), y verificar que no hay desplazamiento horizontal ni errores en la consola.
- [ ] 7.3 Correr `npm run build` y `npm test`, y verificar:
  - Pasan los 99 tests previos, ajustados solo donde el spec de navegación cambió el texto, más uno por cada escenario nuevo, con el nombre igual al escenario.
  - No hay `any`.

## Workflow follow-up

- Antes del archive de este change, C-09 (`frontend-agenda-personal`) tiene que estar archivado, porque el delta de `frontend-navegacion` parte de su texto.
- Al mergear, marcar C-12 en CHANGES.md y archivar en un PR aparte, como con C-07 y C-13.
