# Mockups de C-39, etapa D: pantallas del personal

- Design system revision: 3 (`design-system/README.md`)
- Approval: pending
- Preview: rendered (2026-11-24), capturas en `previews/` a 390 × 844 y 1440 × 900 (página completa) de las 17 pantallas; la base de comparación ("antes", la aplicación de hoy con los mismos datos) está en `antes/`. Ninguna pantalla tiene desplazamiento horizontal, ni errores en la consola, ni pedidos fuera de la máquina local.

Los aprueba la líder antes del apply de la etapa D (`design-system/README.md`, modo `system-and-mockups`). Una vez aprobados, son el criterio de aceptación visual de las tareas D.3 y D.4 de `tasks.md`.

## Brief

- **Para quién**: recepción, odontólogos y administración, casi siempre desde una computadora (y la agenda propia del odontólogo desde el celular).
- **Qué cambia**: las pantallas del personal (agenda, Nuevo turno, reprogramar y cancelar, y las siete pestañas de Configuración con sus diálogos) usan el mismo lenguaje visual que las del paciente después de la etapa C: fondo suave con tarjetas blancas, jerarquía de títulos, botones con hover, foco y peligro, diálogos y estados vacíos de la etapa B. **No cambia ninguna funcionalidad ni ningún texto.**
- **Cómo se hicieron**: no son dibujos aparte, sino la aplicación real. Cada HTML es una copia del DOM que arma la aplicación (con el backend real y la agenda simulada con datos ficticios, sin escribir nada en la base) más `mockups.css`, que son los módulos de estilos de `frontend/src/styles` tal como están hoy y, al final, la sección "Etapa D" con lo único que cambia. Por eso lo que se aprueba es exactamente lo que se va a aplicar.
- **Sistema**: solo variables de `design-system/tokens.css`; datos ficticios (pacientes "Paciente Uno", "Carla Ficticia", etc.; prestaciones, profesionales y sillones del seed).

## Pantallas

| Archivo | Estado | Requisitos |
|---|---|---|
| `d-agenda-dia.html` | Agenda del día con turnos en cada estado, sobreturno incluido | Agenda del día; Estados del turno |
| `d-agenda-semana.html` | Agenda de la semana en columnas, con días sin turnos | Agenda de la semana |
| `d-agenda-vacia.html` | Día sin turnos | Estado vacío con ilustración |
| `d-agenda-cancelar.html` | Diálogo "¿Cancelar el turno?" | Orden de los botones en los diálogos |
| `d-agenda-reprogramar.html` | Diálogo "Reprogramar turno" con el selector de fecha | Reprogramar desde la agenda |
| `d-nuevo-turno.html` | Nuevo turno: paciente, prestación, profesional y fecha | Nuevo turno guiado |
| `d-nuevo-turno-resultados.html` | Búsqueda de paciente con resultados | Buscar el paciente |
| `d-nuevo-turno-horarios.html` | Paciente elegido, calendario y horarios libres | Horarios libres del día |
| `d-config-profesionales.html` | Configuración › Profesionales | Configuración |
| `d-config-profesional-detalle.html` | Detalle de un profesional | Configuración |
| `d-config-sillones.html` | Configuración › Sillones | Configuración |
| `d-config-prestaciones.html` | Configuración › Prestaciones | Configuración |
| `d-config-margen.html` | Configuración › Margen | Configuración |
| `d-config-horarios.html` | Configuración › Horarios | Configuración |
| `d-config-ausencias.html` | Configuración › Ausencias | Configuración |
| `d-config-ausencia-formulario.html` | Diálogo "Registrar ausencia" con el selector de fecha | Configuración |
| `d-config-bloqueos.html` | Configuración › Bloqueos | Configuración |

## Cómo verlos

- **Capturas**: en `previews/<pantalla>-390.png` y `previews/<pantalla>-1440.png`; la comparación con la aplicación de hoy, en `antes/` con los mismos nombres.
- **En el navegador**: los HTML cargan `../../../../../design-system/`, así que hay que servirlos desde la raíz del repo. Desde ahí:

  ```powershell
  python -m http.server 8765 --bind 127.0.0.1
  ```

  Después se abre, por ejemplo, `http://127.0.0.1:8765/openspec/changes/frontend-uniformidad-visual/mockups/D/d-agenda-dia.html`.

## Qué se midió

| Control | Resultado |
|---|---|
| Desplazamiento horizontal a 390 y 1440 px | No hay en ninguna pantalla |
| Errores de la consola y pedidos fuera de la máquina local | 0 |
| Cambios de CSS respecto de la aplicación de hoy | Solo la sección "Etapa D" de `mockups.css` (unas 20 líneas) |

## Decisiones para revisar

1. **Fondo suave en las pantallas del personal**, el mismo de las del paciente (`.cuerpo.fondo-suave`, ya existe): gris azulado con tarjetas blancas. Se activa con la propiedad `fondoSuave` de las páginas de agenda y Configuración.
2. **La tarjeta del turno pasa a blanca y el paciente deja de ser una caja.** Hoy el nombre del paciente en la agenda reutiliza por accidente la clase `.turno-paciente`, la tarjeta de "Mis turnos", y se ve como una caja blanca dentro de otra tarjeta. Pasa a su propia clase (`.turno-persona`): nombre en negrita y más grande, y el horario chico y en azul oscuro.
3. **"Cancelar turno" es un botón de peligro (rojo) y "Reprogramar" uno secundario**, con hover y foco como el resto. Hoy los dos son iguales y neutros, y el que cancela no se distingue.
4. **Separación entre el detalle y las acciones del turno** de 16 px (antes 8 px), para que no se toquen al tocar con el dedo.
5. **Configuración y Agenda siguen con anchos de columna distintos** (la agenda llega al borde derecho; Configuración está centrada en una columna más angosta). Unificarlos cambia el aspecto de las tablas de Configuración y queda fuera de esta etapa; si la líder lo quiere, se suma.
6. **Los diálogos se muestran abiertos sobre la página** con el fondo atenuado; en la aplicación el diálogo se desplaza si no entra en la pantalla (a 390 px, el de "Registrar ausencia" y el de Nuevo turno).
7. **Agenda propia del odontólogo ("Mi agenda")** usa los mismos componentes que la de recepción y recibe los mismos cambios; no tiene mockup propio.
