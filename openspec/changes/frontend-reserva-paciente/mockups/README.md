# Mockups de C-12 `frontend-reserva-paciente`

- Design system revision: 1 (`design-system/README.md`, aprobado el 2026-11-22)
- Approval: pending
- Preview: rendered (2026-11-23), capturas en `previews/` a 390 × 844 y 1440 × 900 (página completa). Ninguna pantalla tiene desplazamiento horizontal ni errores en la consola.

Los aprueba la líder antes del apply (`design-system/README.md`). Una vez aprobados, son el criterio de aceptación visual de las tareas de `tasks.md`.

## Brief

- **Para quién**: pacientes del consultorio, casi siempre desde el celular. Sin sesión pueden mirar horarios; para confirmar necesitan su cuenta.
- **Qué tienen que poder hacer**:
  - Reservar en tres pasos.
  - Crear su cuenta o ingresar sin perder lo elegido.
  - Ver sus turnos, cancelarlos o cambiarlos de horario con 24 h o más de anticipación.
  - Confirmar la asistencia desde el link del recordatorio.
- **Tamaños**: mobile first a 390 × 844. En 1440 × 900 el contenido queda en una columna centrada de 36rem, porque es un flujo corto y no una herramienta de trabajo.
- **Sistema**:
  - Solo variables de `design-system/tokens.css`.
  - Los componentes de `design-system/components.html`: encabezado, botones, campos, avisos, estados, pasos, opciones, chips, comprobante y diálogo.
  - El logo provisorio de `design-system/assets/logo.svg` y el nombre de `brand.js`.
  - La voz de `brand.md`: voseo, y errores que dicen qué pasó y qué hacer.
- **Restricciones**:
  - Sin datos de salud: solo fecha, hora, prestación, profesional, estado y número de reserva.
  - Datos 100% ficticios (seed de dev).
  - Sin fuentes ni recursos remotos.
- **Fuera del sistema**: las clases de página (`.app-encabezado`, `.pagina`, `.panel`, `.turno-paciente`, `.pestanas`) son armado de pantalla con tokens existentes. No suman valores nuevos.

## Pantallas

| Archivo | Pantalla y estado | Requisitos |
|---|---|---|
| `reserva-paso-1.html` | Reserva, paso 1, visitante sin sesión | Reserva en tres pasos; Elección de la prestación |
| `reserva-paso-2.html` | Paso 2 con "Sin preferencia" y las 10:30 elegidas | Elección del profesional y la fecha; Horarios libres |
| `reserva-paso-3.html` | Paso 3, resumen con sesión de paciente | Resumen antes de confirmar |
| `reserva-horario-ocupado.html` | Paso 3 después de `SIN_DISPONIBILIDAD` con alternativas | Horario ocupado al confirmar |
| `reserva-confirmada.html` | Comprobante "¡Turno reservado!" | Reserva confirmada |
| `mi-cuenta.html` | Ingresar, llegando desde "Confirmar reserva" y con credenciales rechazadas | Ingreso del paciente; Credenciales rechazadas |
| `mi-cuenta-registro.html` | Crear cuenta con `DNI_YA_REGISTRADO` | Registro; Rechazos del registro |
| `mis-turnos.html` | Próximos (uno a menos de 24 h) y anteriores | Lista; Acciones según el plazo |
| `mis-turnos-cancelar.html` | Diálogo "¿Cancelar el turno?" | Cancelar un turno |
| `mis-turnos-cambiar.html` | Cambio de horario abierto en el turno, y el aviso posterior al cambio | Cambiar el horario |
| `confirmar-asistencia.html` | Antes de confirmar, confirmado y los tres rechazos | `frontend-confirmacion-asistencia` |
| `estados.html` | Día sin horarios, rechazos, personal en páginas del paciente, sesión vencida y sin turnos | Estados secundarios |

## Cómo verlos

- **Capturas**: en `previews/<pantalla>-390.png` y `previews/<pantalla>-1440.png`.
- **En el navegador**: los HTML cargan `../../../../design-system/`, así que hay que servirlos desde la raíz del repo. Desde ahí:

  ```powershell
  python -m http.server 8765 --bind 127.0.0.1
  ```

  Después se abre `http://127.0.0.1:8765/openspec/changes/frontend-reserva-paciente/mockups/reserva-paso-1.html`. Los botones navegan entre las pantallas.

## Decisiones para revisar

1. **Un paso por pantalla en la reserva**, como pide `brand.md`. Prestación y profesional se eligen como opciones grandes y los horarios como chips.
2. **El encabezado del paciente en el celular ocupa tres filas**: marca, usuario con "Salir", y menú. El email se corta con "…" para no generar desplazamiento horizontal.
3. **El cambio de horario se abre dentro del mismo turno**, en lugar de en un diálogo, para que se vean los horarios en el celular sin un modal largo.
4. **El número de reserva se muestra en cada turno**, porque el paciente lo necesita para cancelar o reprogramar (RN-AG-07).
