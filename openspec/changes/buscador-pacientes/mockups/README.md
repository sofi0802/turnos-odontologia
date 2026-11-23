# Mockups de C-36 `buscador-pacientes`

- Design system revision: 1 (`design-system/README.md`, aprobado el 2026-11-22)
- Approval: approved (2026-11-23, by user — Sofia vía Juan Sebastián). Son el criterio de aceptación visual de `tasks.md`.
- Preview: rendered (2026-11-23), capturas en `previews/` a 390 × 844 y 1440 × 900 (página completa). Ninguna pantalla tiene desplazamiento horizontal ni mensajes en la consola.

## Brief

- **Para quién**: recepción, odontólogos y administración, en la computadora del consultorio y a veces en el celular. Saben el DNI o el nombre del paciente, no su id.
- **Qué tienen que poder hacer**: encontrar al paciente escribiendo parte del DNI o del nombre, elegirlo con el mouse o con el teclado, y dar el turno sin salir del diálogo "Nuevo turno".
- **Pantallas afectadas**: solo el diálogo "Nuevo turno" de la agenda del personal (C-09). El formulario "Sobreturno" no existe todavía (design D11: fuera de C-36, confirmado).
- **Tamaños**: 390 × 844, con el diálogo a todo el ancho, y 1440 × 900, con el diálogo de 30rem como en C-09.
- **Sistema**:
  - Solo variables de `design-system/tokens.css`.
  - Campos, avisos y diálogo de `design-system/components.html`.
  - El logo provisorio de `design-system/assets/logo.svg` y el nombre de `brand.js`.
  - La voz de `brand.md`: voseo, y errores que dicen qué pasó y qué hacer.
- **Restricciones**:
  - Cada resultado muestra solo apellido, nombre y DNI. Nada de contacto ni de salud (Ley 25.326).
  - Datos 100% ficticios: "Paciente Uno", "Paciente Dos" y "Carla Ficticia", DNI `9900000x`.
  - Sin fuentes ni recursos remotos.
- **Fuera del sistema**: las clases `.buscador-*` son el armado del componente con tokens existentes. No suman valores nuevos.

## Pantallas

| Archivo | Pantalla y estado | Requisitos |
|---|---|---|
| `nuevo-turno-resultados.html` | "Nuevo turno" con "paciente" escrito, dos coincidencias y la segunda activa por teclado | `frontend-buscador-pacientes`: Lista de coincidencias; Buscador manejable con el teclado |
| `nuevo-turno-seleccionado.html` | "Nuevo turno" con "Carla Ficticia" elegida y el resto completo | Elegir un paciente; `frontend-agenda-personal`: Crear un turno |
| `estados-buscador.html` | Vacío, texto corto, buscando, sin resultados, límite de 10, error, sin permiso y paciente sin elegir | Búsqueda mientras se escribe; Estado de búsqueda en curso; Búsqueda sin resultados; Falla de la búsqueda; Crear un turno ("Paciente escrito pero no elegido") |

## Cómo verlos

- **Capturas**: en `previews/<pantalla>-390.png` y `previews/<pantalla>-1440.png`.
- **En el navegador**: los HTML cargan `../../../../design-system/`, así que hay que servirlos desde la raíz del repo. Desde ahí:

  ```powershell
  python -m http.server 8765 --bind 127.0.0.1
  ```

  Después se abre `http://127.0.0.1:8765/openspec/changes/buscador-pacientes/mockups/nuevo-turno-resultados.html`. Las notas al pie enlazan las otras pantallas.

## Decisiones para revisar

1. **La lista se abre debajo del campo, dentro del diálogo**, y empuja los campos siguientes. Así no se superpone con ellos ni desborda en 390 px.
2. **Cada opción dice "Apellido, Nombre · DNI"**, con el DNI en tipografía monoespaciada, para distinguir pacientes con el mismo nombre.
3. **El paciente elegido se muestra como un recuadro con "Cambiar paciente"** en lugar de dejar el texto en el campo. Así se ve que hay una elección guardada y no solo texto escrito.
4. **Los errores de la búsqueda van debajo del campo**, como los errores de campo de C-09. El formulario no se cierra.
