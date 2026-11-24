# Mockups de C-39, etapa C: pantallas del paciente

- Design system revision: 3 (`design-system/README.md`)
- Approval: pending
- Preview: rendered (2026-11-24), capturas en `previews/` a 390 × 844 y 1440 × 900 (página completa), y a 320 px para la portada sin sesión, el paso 2 de la reserva, Mis turnos y el diálogo de cancelar. Ninguna pantalla tiene desplazamiento horizontal ni errores en la consola.

Los aprueba la líder antes del apply de la etapa C (`design-system/README.md`, modo `system-and-mockups`). Una vez aprobados, son el criterio de aceptación visual de la tarea C.3 de `tasks.md`.

## Brief

- **Para quién**: pacientes (con y sin cuenta), casi siempre desde el celular.
- **Qué cambia**: las pantallas del paciente pasan a vivir dentro del layout de la etapa B (encabezado azul con el logo, barra lateral con sesión, pie) y usan sus componentes: botones con hover y foco, diálogo uniforme, estados vacíos con ilustración y fondo de página gris con tarjetas blancas. **No cambia ninguna funcionalidad ni ningún texto**, salvo lo indicado en "Decisiones".
- **Tamaños**: mobile first a 390 × 844; en 1440 × 900 el contenido queda en una columna a la derecha de la barra lateral.
- **Sistema**: solo variables de `design-system/tokens.css`; la voz de `brand.md` (voseo); el logo con el nombre de `design-system/assets/logo.svg`; datos ficticios (`paciente.uno@example.com`, prestaciones y profesionales del seed).
- **Punto de partida**: los mockups aprobados de C-12 (`openspec/changes/archive/2026-11-23-frontend-reserva-paciente/mockups`), C-37 (portada y Mi cuenta) y C-38 (selector de prestaciones), con el layout de la etapa B. Las clases de componentes salen de `design-system/components.html`.

## Pantallas

| Archivo | Estado | Requisitos |
|---|---|---|
| `c-inicio-sin-sesion.html` | Portada del visitante: "Reservar turno", "Ingresar a mi cuenta" y los tres pasos; encabezado con "Ingresar" | Portada pública; Menú de un visitante |
| `c-inicio-paciente.html` | Portada con sesión de paciente: "Reservar turno" y "Mis turnos", y la barra lateral | Portada con sesión iniciada |
| `c-reserva-paso1.html` | Paso 1 con "Limpieza dental" elegida en el selector | Elección de la prestación |
| `c-reserva-paso1-abierto.html` | Paso 1 con el selector abierto | Buscar la prestación |
| `c-reserva-paso2.html` | Paso 2: profesional, día y horarios libres como botones | Elección del profesional y la fecha; Horarios libres del día |
| `c-reserva-paso3.html` | Paso 3: resumen antes de confirmar | Resumen antes de confirmar |
| `c-reserva-ocupado.html` | Horario ocupado al confirmar, con alternativas | Horario ocupado al confirmar |
| `c-reserva-confirmada.html` | Reserva confirmada con el número de reserva | Reserva confirmada |
| `c-mi-cuenta.html` | Mi cuenta sin sesión, pestaña "Ingresar", con la indicación para el personal | Cuenta del personal en el ingreso del paciente; Se ve la indicación |
| `c-mi-cuenta-registro.html` | Mi cuenta, pestaña "Crear cuenta" | Registro del paciente |
| `c-mis-turnos.html` | Próximos y anteriores, con estados, "Cambiar horario" y "Cancelar turno" | Mis turnos |
| `c-mis-turnos-vacio.html` | Sin turnos: ilustración, texto y "Reservar turno" | Lista vacía con ilustración |
| `c-mis-turnos-cancelar.html` | Diálogo "¿Cancelar el turno?" | Orden de los botones en los diálogos |
| `c-mis-turnos-cambiar.html` | Cambiar horario: profesional, día y horarios | Reprogramar desde Mis turnos |
| `c-confirmar.html` | Confirmar asistencia desde el link del recordatorio, con sus resultados | Confirmar asistencia |

## Cómo verlos

- **Capturas**: en `previews/<pantalla>-390.png` y `previews/<pantalla>-1440.png` (y `-320.png`).
- **En el navegador**: los HTML cargan `../../../../../design-system/`, así que hay que servirlos desde la raíz del repo. Desde ahí:

  ```powershell
  python -m http.server 8765 --bind 127.0.0.1
  ```

  Después se abre, por ejemplo, `http://127.0.0.1:8765/openspec/changes/frontend-uniformidad-visual/mockups/C/c-mis-turnos.html`.

## Qué se midió

| Control | Resultado |
|---|---|
| Desplazamiento horizontal a 320, 390 y 1440 px | No hay en ninguna pantalla |
| Alto mínimo de botones, enlaces de la barra, campos y horarios | 44 px |
| Título principal (`h1`) por pantalla | Uno solo |
| Errores de la consola y recursos que no cargan | 0 |

## Decisiones para revisar

1. **Fondo de página gris azulado con tarjetas blancas** en todas las pantallas del paciente, como en los mockups de B; la barra lateral y el encabezado quedan fuera de ese fondo. Es la diferencia menor que la verificación de B dejó anotada.
2. **Columna de contenido de 44 rem** centrada en el espacio a la derecha de la barra lateral (la portada llega a 64 rem). En el celular ocupa todo el ancho menos 16 px de cada lado.
3. **Mis turnos**: "Cambiar horario" va como botón secundario y "Cancelar turno" como botón de peligro (rojo), como ya estaba en los mockups de C-12.
4. **Los turnos pasados que nadie actualizó siguen mostrándose como "Reservado".** Cambiarlo es una regla nueva (C-41, a decidir por la líder) y no entra en esta etapa; los mockups no inventan un estado "Vencido".
5. **Confirmar asistencia** muestra juntos, para revisarlos, el estado inicial y los tres resultados (confirmado, ya confirmado, link inválido y no se puede confirmar); en la aplicación se ve uno por vez.
6. **Reservar turno sin sesión** usa el encabezado simple (sin barra lateral); el paso de confirmación y los resultados se ven con la sesión iniciada porque el paciente ingresó al confirmar.
7. **El diálogo de cancelar** usa el molde de la etapa B: "Sí, cancelar turno" arriba y "Volver" abajo en el celular; en la computadora, "Volver" a la izquierda y la acción de peligro a la derecha.
