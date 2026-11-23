# Mockups de C-40 `frontend-nuevo-turno-guiado`

- Design system revision: 1 (`design-system/README.md`, aprobado el 2026-11-22)
- Approval: approved (2026-11-23, by Sofia Indovino, la líder; PR #65)
- Preview: rendered (2026-11-23), capturas en `previews/` a 390 × 844 y 1440 × 900. Ninguna pantalla tiene desplazamiento horizontal ni errores en la consola. Los días del calendario miden 46 × 44 px en el celular.

Una vez aprobados, son el criterio de aceptación visual de las tareas de `tasks.md`.

## Brief

- **Para quién**: la recepción, el administrador y los odontólogos que cargan turnos desde la agenda, sobre todo en la computadora y a veces en el celular.
- **Qué tienen que poder hacer**: cargar un turno eligiendo solo entre opciones posibles: la prestación, un profesional que la hace, un día desde hoy y un horario libre, con el sillón del profesional a la vista.
- **Qué cambia respecto de C-09 y C-36**: el diálogo "Nuevo turno" deja de pedir todo junto. Se mantiene el buscador de pacientes de C-36 primero; después cada paso se habilita cuando el anterior tiene un valor. La fecha pasa a un calendario mensual y la hora a botones de horario. El sillón ya no se elige.
- **Decisiones del responsable**: calendario desde hoy en adelante; sillón habitual fijo; los horarios que pisan otro turno del sillón habitual no se ofrecen (design D4).
- **Tamaños**: 390 × 844 y 1440 × 900. En la computadora el diálogo mide 52 rem como máximo y pone el calendario y los horarios lado a lado, así que entra sin desplazarse. En el celular ocupa el ancho disponible, apila todo y **se desplaza por dentro**.
- **Sistema**:
  - Solo variables de `design-system/tokens.css`.
  - Componentes de `design-system/components.html`: encabezado del personal, campos, avisos, chips y diálogo. Los chips son los de C-12 y C-33 (`.chips` / `.chip`, de al menos 44 × 44 px).
  - El buscador de pacientes elegido sigue el de C-36.
  - El calendario es nuevo y usa los mismos tokens: día de hoy con borde de acento, día elegido relleno de acento, días pasados tachados y deshabilitados (el estado no depende solo del color).
  - El logo provisorio y el nombre de `brand.js`; la voz de `brand.md` (voseo, errores que dicen qué pasó y qué hacer).
- **Restricciones**: datos 100 % ficticios (Paciente Uno, DNI `99000001`, Ana Ejemplo, Bruno Prueba, Carla Ficticia); sin datos de salud; sin recursos remotos.
- **Base**: `mockups.css` es una copia de la de C-33 (que se va a archivar) con las reglas de C-40 al final.

## Pantallas

| Archivo | Pantalla y estado | Requisitos |
|---|---|---|
| `nuevo-turno-inicio.html` | Al abrir: el paciente con el foco, solo la prestación habilitada; profesional, calendario y horarios deshabilitados | Crear un turno; Pasos del nuevo turno en orden |
| `nuevo-turno-horarios.html` | Completo: paciente elegido, "Consulta", "Ana Ejemplo" con su "Sillón 1" a la vista, el jueves 3 de diciembre en el calendario (hoy es el martes 1, y los días anteriores están deshabilitados) y el horario de las 10:00 elegido | Crear un turno; Profesionales habilitados; Calendario desde hoy; Horarios libres; Sillón habitual |
| `nuevo-turno-estados.html` | Siete estados, cada uno en su tarjeta: día sin horarios, profesional sin sillón habitual, buscando horarios, falla la consulta de horarios, no se pudieron cargar los profesionales, crear sin elegir un horario y rechazo del backend por sillón ocupado | Horarios libres; Sillón habitual; Profesionales habilitados; Crear un turno (Faltan datos); Mensajes por código de rechazo |

## Cómo verlos

- **Capturas**: `previews/<pantalla>-390.png` y `previews/<pantalla>-1440.png`. `nuevo-turno-horarios-390-abajo.png` muestra el final del diálogo en el celular, con los horarios y los botones.
- **En el navegador**: los HTML cargan `../../../../design-system/`, así que hay que servirlos desde la raíz del repo. Desde ahí:

  ```powershell
  python -m http.server 8765 --bind 127.0.0.1
  ```

  y abrir `http://127.0.0.1:8765/openspec/changes/frontend-nuevo-turno-guiado/mockups/nuevo-turno-horarios.html`.
