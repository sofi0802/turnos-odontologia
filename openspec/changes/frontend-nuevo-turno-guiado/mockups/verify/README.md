# Verificación visual y contra el backend de C-40 (tarea 4.2)

- Fecha: 2026-11-23. Código: rama `change/C-40-frontend-nuevo-turno-guiado` sobre `master` 40f67d2.
- Entorno: backend de desarrollo (`bootRun`, perfil `dev`) con PostgreSQL 16 en Docker y el seed ficticio; frontend con `vite`. Navegador automatizado (Playwright), sesión del admin de desarrollo.
- Datos de prueba, todos ficticios: paciente "Paciente Uno" (DNI 99000001, registrado por la API) y un turno del Dr. Bruno Prueba en el Sillón 1 el 2026-11-24 a las 11:00 (id 3), para ocupar el sillón habitual de la Dra. Ana Ejemplo.
- Comparado con los mockups aprobados (design system Revision 1, PR #65).

## Resultado

| Comprobación | Viewport | Resultado |
|---|---|---|
| Al abrir: solo "Prestación" habilitada; profesional, calendario y horarios deshabilitados | 1440 | Coincide |
| Profesionales habilitados para "Consulta o control": Ana Ejemplo y Bruno Prueba | 1440 | Coincide (`GET /public/profesionales?prestacionId=1`) |
| Sillón fijo: "Sillón: Sillón 1 · sillón habitual de Ana Ejemplo" | 1440 y 390 | Coincide (ver diferencia 1, ya corregida) |
| Calendario desde hoy (lunes 23/11): días anteriores tachados y deshabilitados, "Mes anterior" deshabilitado | 1440 y 390 | Coincide |
| **Filtro por el sillón habitual**: la disponibilidad pública de Ana el 24/11 ofrece 10:45, 11:00, 11:15 y 11:30; el formulario los oculta porque pisan el turno de Bruno en el Sillón 1 con el margen de 10 minutos, y muestra 10:15 y 11:45 | 1440 | Coincide (`1440-horarios-backend-real.png`) |
| Crear el turno de las 10:00 del 24/11 | 1440 | `201`, aviso "Turno creado. Número de reserva: …" y el diálogo se cierra |
| Después de crearlo, Ana ya no ofrece de 09:30 a 10:15 ese día | 390 | Coincide (`390-formulario-backend-real.png`) |
| Día sin horarios: domingo 29/11 (Ana no trabaja) | 390 | "No hay horarios libres ese día. Probá con otro día." (`390-dia-sin-horarios-backend-real.png`) |
| Días del calendario en el celular | 390 | 44 × 44 px, sin desplazamiento horizontal |
| Errores de consola | 1440 y 390 | Ninguno |

## Diferencias con los mockups

| Prioridad | Pantalla | Viewport | Esperado (mockup) | Observado | Estado |
|---|---|---|---|---|---|
| Menor | Sillón | 1440 y 390 | "· sillón habitual de Ana Ejemplo" | "· Sillón habitual…": la regla `.campo .ayuda::first-letter` de C-12 ponía la primera letra en mayúscula | **Corregida**: el texto usa la clase `sillon-detalle` |
| Menor | Botones del diálogo | 1440 | "Cerrar" a la izquierda y "Crear turno" a la derecha | "Crear turno" primero, como en todos los diálogos de la agenda desde C-09 | Se mantiene la convención actual; la unifica la etapa D de C-39 |
| Menor | Diálogo completo | 1440 × 900 | Entra sin desplazarse | Con 14 horarios libres el diálogo se desplaza unos pocos píxeles al final | Aceptable; el mockup tenía 5 horarios |

## No verificado contra el backend

- **Profesional sin sillón habitual**: el seed asigna sillón a los dos profesionales y no se cambiaron datos de Configuración para probarlo. Lo cubren los tests "Profesional sin sillón habitual" y "Se muestra el sillón habitual sin poder cambiarlo".
- **Sesión de odontólogo**: el seed no trae usuarios odontólogos; el riesgo de RN-AG-18 (el filtro solo ve sus propios turnos) queda documentado en `design.md`.
