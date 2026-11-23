# Mockups de C-34 `frontend-configuracion-consultorio`

- Design system revision: 1 (`design-system/README.md`, aprobado el 2026-11-22)
- Revision: 2 (segunda prueba, 2026-11-23). La revisión 1 la aprobó Sofia Indovino el 2026-11-23 al mergear el PR #51; la revisión 2 reemplaza a las pantallas de la tabla marcadas con (r2).
- Approval: approved (2026-11-23, by Emanuel) para la revisión 2, con el ajuste 2b del sillón habitual en tarjetas
- Preview: rendered (2026-11-23, Chrome headless con emulación de 390 × 844 y 1440 × 900). Las capturas de la revisión 2 están en `previews/` con el sufijo `-r2-390.png` y `-r2-1440.png`; las de la revisión 1 (sin sufijo) quedan como referencia de lo que no cambió. Son de página completa, salvo las de los diálogos, que muestran el diálogo entero. Ninguna pantalla tiene desplazamiento horizontal ni errores en la consola.

Los aprueba la líder antes del apply (`design-system/README.md`). Una vez aprobados, son el criterio de aceptación visual de las tareas de `tasks.md`.

## Brief

- **Para quién**: el personal del consultorio (administrador, recepción y odontólogos), sobre todo desde la computadora, aunque la página tiene que funcionar en el celular.
- **Qué tienen que poder hacer**:
  - **Administrador**: cargar y mantener profesionales, sillones y prestaciones, las especialidades y el sillón habitual, el margen entre turnos y los horarios de trabajo.
  - **Recepción**: registrar ausencias y licencias, y reasignar los turnos que quedan afectados.
  - **Odontólogo**: bloquear su propia agenda.
  - Todos ven lo demás en modo lectura, según la matriz RBAC.
- **Tamaños**: a 1440 × 900, el contenido va en una columna de 64rem (es una herramienta de trabajo, más ancha que el flujo del paciente). A 390 × 844, las listas se apilan y las pestañas se desplazan dentro de su propia barra; la elegida siempre queda a la vista.
- **Sistema**:
  - Solo variables de `design-system/tokens.css`.
  - Los componentes de `design-system/components.html`: encabezado, botones, campos, avisos, estados y diálogo.
  - El logo provisorio y el nombre de `brand.js`.
  - La voz de `brand.md`: voseo, y errores que dicen qué pasó y qué hacer.
- **Restricciones**:
  - Sin datos de salud. Los turnos afectados muestran solo fecha, hora y prestación, y el motivo de una ausencia avisa que no se anoten datos de salud.
  - Datos 100% ficticios (seed de dev, más "Carla Muestra" y "Diego Ficticio" para mostrar un inactivo y un alta).
  - Sin fuentes ni recursos remotos.
- **Fuera del sistema**: las clases de armado de pantalla (`.pagina-config`, `.pestanas-config`, `.pestana`, `.lista-config`, `.estado-activo`, `.estado-inactivo`, `.franjas`, `.afectados`) usan tokens existentes y no suman valores nuevos.
  - `.pestanas` de C-12 (Mi cuenta) no se reutiliza porque es una grilla de dos botones.
  - El estado activo o inactivo se distingue con el texto y la forma del punto, no solo con el color.

## Pantallas

| Archivo | Pantalla y estado | Requisitos |
|---|---|---|
| `profesionales.html` (r2) | Admin, Profesionales: detalle de Ana Ejemplo con cada bloque separado (`.grupo-config`: 8 px entre campo y botón, 24 px entre bloques), "Sillón 2 (de Bruno Prueba)" deshabilitado (el selector se dibuja abierto para mostrarlo) y el aviso de falla parcial "El profesional se creó, pero no se pudieron guardar sus prestaciones o su sillón." | Secciones según el rol; Catálogos; Edición de catálogos; Especialidades y sillón habitual; Edición de especialidades |
| `profesional-formulario.html` (r2) | Admin, diálogo "Agregar profesional" con casillas de prestaciones, selector de sillón (los ocupados deshabilitados) y `MATRICULA_DUPLICADA` | Edición de catálogos (Matrícula repetida) |
| `prestaciones-recepcion.html` (r2) | Recepción, Prestaciones solo lectura; ahora con la pestaña Bloqueos (solo lectura) | Catálogos (solo lectura); Secciones según el rol |
| `sillones-error.html` | Admin, Sillones con la carga fallida y "Reintentar" | Errores al cargar o guardar |
| `margen.html` | Admin, Margen con `MARGEN_INVALIDO` | Margen entre turnos |
| `horarios.html` (r2) | Admin, "Agregar franja" con casillas Lu a Do y la hora en dos selectores (hora y minutos de 5 en 5); además, editando el horario de Ana Ejemplo, con una franja sin guardar y `FRANJAS_SUPERPUESTAS` | Edición de horarios por el administrador |
| `horarios-recepcion.html` (r2) | Recepción, con la pestaña Bloqueos, horario de Ana Ejemplo solo lectura ("No atiende" sábado y domingo) | Horarios de trabajo; Profesional de las secciones de agenda |
| `ausencias.html` (r2) | Recepción, "Todos los profesionales" (por defecto) con la columna Profesional, la pestaña Bloqueos de solo lectura y "Turnos afectados: 2" con "Reasignar" | Ausencias y licencias; Turnos afectados por una ausencia |
| `ausencia-formulario.html` (r2) | Recepción, diálogo "Registrar ausencia" con selector de profesional, la hora en dos selectores de 5 en 5 y `PERIODO_INVALIDO` | Registro y borrado de ausencias |
| `reasignar.html` (r2) | Recepción, diálogo "Reasignar turno" con solo los profesionales habilitados y el inicio de 5 en 5 | Reasignación de un turno afectado |
| `bloqueos-odontologo.html` (r2) | `ana.ejemplo`, Bloqueos de su profesional fijo, con "se cruza con 2 turnos dados", los turnos afectados sin "Reasignar" y "Avisale a la recepción para reasignarlos." |
| `bloqueos.html` (r2, nueva) | Recepción, Bloqueos de "Todos los profesionales" en solo lectura (sin "Bloquear horario" ni "Borrar"), con la columna Profesional y los turnos afectados con "Reasignar" | Bloqueos de agenda; Profesional de las secciones de agenda |
| `ausencias-odontologo.html` | `ana.ejemplo`, sus ausencias solo lectura, más el aviso del odontólogo sin profesional vinculado | Ausencias y licencias; Profesional de las secciones de agenda |

## Cómo verlos

Hay que servirlos desde la raíz del repo, porque las rutas a `design-system/` son relativas:

```powershell
python -m http.server 8765
```

Después se abre `http://localhost:8765/openspec/changes/frontend-configuracion-consultorio/mockups/profesionales.html`. Para ver el celular, se usan las herramientas del navegador a 390 × 844. Los botones de los mockups no hacen nada: muestran cada estado en un archivo aparte.

## Revisión 2: estilos nuevos

`.grupo-config`, `.dias-semana`, `.hora-5`, `.item-profesional` (la columna Profesional, que en 390 px pasa a ser una línea "Profesional: X") y `.agregar-franja-dias`. Usan solo tokens existentes. Los diálogos ahora se desplazan por dentro si no entran en pantalla. El error "Elegí al menos un día." aparece en "Agregar franja" cuando no se marca ningún día, y "Ese sillón ya es el habitual de otro profesional." cuando falla el guardado del sillón; ninguno tiene una captura propia.
