# Mockups de C-37 `frontend-portada-y-acceso`

- Design system revision: 1 (`design-system/README.md`, aprobado el 2026-11-22)
- Approval: pending
- Preview: rendered (2026-11-23), capturas en `previews/` a 390 × 844 y 1440 × 900 (página completa), y a 320 px para la portada sin sesión y para Mi cuenta. Ninguna pantalla tiene desplazamiento horizontal ni errores en la consola.

Los aprueba la líder antes del apply (`design-system/README.md`, modo `system-and-mockups`). Una vez aprobados, son el criterio de aceptación visual de las tareas de `tasks.md`.

## Brief

- **Para quién**: cualquier persona que abre la aplicación, casi siempre desde el celular: pacientes nuevos, pacientes con cuenta y personal del consultorio.
- **Qué tiene que poder hacer**: entender en segundos qué es la aplicación, reservar o mirar horarios sin registrarse, ingresar a su cuenta, y (el personal) llegar a su ingreso desde arriba a la derecha.
- **Qué cambia**: la dirección inicial deja de ser un formulario de login y pasa a ser la portada `#/inicio`. Mi cuenta suma una indicación para el personal.
- **Tamaños**: mobile first a 390 × 844; en 1440 × 900 el contenido queda en una columna de 64 rem.
- **Sistema**:
  - Solo variables de `design-system/tokens.css`.
  - Componentes de `design-system/components.html`: encabezado, botones, campos, pestañas y tarjetas.
  - El logo provisorio de `design-system/assets/logo.svg` y el nombre de `brand.js`.
  - La voz de `brand.md`: voseo, frases cortas.
  - El acento solo en acciones, íconos y títulos de pasos; el fondo de la portada es un degradé suave del acento al blanco, no un bloque de color lleno (regla de `brand.md`).
- **Ilustración**: una muela y un calendario dibujados en SVG propio, con los colores del sistema y `aria-hidden`. Sin fotos, sin archivos de imagen y sin recursos remotos.
- **Restricciones**: datos 100 % ficticios (`paciente.uno@example.com`, `recepcion`); sin datos del consultorio (dirección, teléfono) ni de salud.
- **Fuera del sistema**: las clases de página (`.inicio`, `.hero`, `.tarjeta-paso`, `.btn-acceso`, `.nota-personal`) arman estas pantallas con tokens existentes. No suman valores nuevos.

## Pantallas

| Archivo | Pantalla y estado | Requisitos |
|---|---|---|
| `inicio-sin-sesion.html` | Portada sin sesión: "Acceso personal" arriba a la derecha, título, descripción, "Reservar turno" e "Ingresar a mi cuenta", y los tres pasos | Portada pública; Botones de la portada; Acceso del personal; Solo recursos propios |
| `inicio-paciente.html` | Portada con sesión de paciente: "Reservar turno" y "Mis turnos", y el usuario con "Salir" en el encabezado | Portada con sesión iniciada |
| `inicio-personal.html` | Portada con sesión del personal: solo "Ir a la agenda" | Portada con sesión iniciada |
| `mi-cuenta-personal.html` | Mi cuenta sin sesión, con la indicación para el personal debajo del formulario | Indicación para el personal en Mi cuenta |

## Cómo verlos

- **Capturas**: en `previews/<pantalla>-390.png` y `previews/<pantalla>-1440.png` (y `-320.png`).
- **En el navegador**: los HTML cargan `../../../../design-system/`, así que hay que servirlos desde la raíz del repo. Desde ahí:

  ```powershell
  python -m http.server 8765 --bind 127.0.0.1
  ```

  Después se abre `http://127.0.0.1:8765/openspec/changes/frontend-portada-y-acceso/mockups/inicio-sin-sesion.html`.

## Qué se midió

| Control | Resultado |
|---|---|
| Desplazamiento horizontal a 320, 390 y 1440 px | No hay en ninguna pantalla |
| Alto de botones, campos y pestañas | 44 px como mínimo |
| Título principal (`h1`) por pantalla | Uno solo |
| "Acceso personal" a 320 y 390 px | Arriba a la derecha, en la misma fila que el nombre, de 44 px de alto |
| Campos de Mi cuenta | Dentro del recuadro a 320, 390 y 1440 px |
| Errores de la consola | 0 |

## Decisiones para revisar

1. **La portada no repite el logo provisorio en el celular.** El encabezado de la aplicación real todavía no tiene logo; el del mockup es una etiqueta ancha ("LOGO PROVISORIO") que obligaba a pasar "Acceso personal" a una segunda fila. En pantallas de menos de 480 px el encabezado de la portada lo oculta y compacta un poco el texto, para que el botón quede arriba a la derecha.
2. **Con sesión, la portada conserva los tres pasos.** Se repiten para el paciente y el personal por consistencia; se pueden ocultar si la líder prefiere una portada más corta con sesión.
3. **No hay una nota de recordatorios.** Se evaluó "Si lo autorizás, te recordamos el turno 24 horas antes", pero el consentimiento de comunicaciones todavía no se puede gestionar (C-17), así que prometerlo en la portada sería engañoso. Se agrega cuando exista C-17.
4. **La etiqueta de Mi cuenta sigue siendo "Email".** El personal ingresa con un usuario (por ejemplo, `recepcion`) que no es un email; la indicación lo aclara sin cambiar la etiqueta ni los mensajes actuales. Cambiar la etiqueta ("Email o usuario") ampliaría el alcance a los tests y specs de C-12.
5. **El resto de los encabezados no cambia** ("Ingresar", "Salir" y el menú de la reserva): su rediseño es de C-39. Los botones chicos de ese encabezado (36 px) vienen de C-12.
6. **Textos de la portada**: el título y los pasos son un borrador en la voz de `brand.md`, para ajustar con el equipo.
