# Mockups de C-34 `frontend-configuracion-consultorio`

- Design system revision: 1 (`design-system/README.md`, aprobado el 2026-11-22)
- Approval: pending
- Preview: rendered (2026-11-23). Las capturas están en `previews/`, a 390 × 844 y 1440 × 900. Son de página completa, salvo las de los diálogos, que muestran lo que se ve en pantalla. Ninguna pantalla tiene desplazamiento horizontal, errores en la consola ni recursos que no carguen.

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
| `profesionales.html` | Admin, Profesionales, con el detalle de Ana Ejemplo abierto ("Prestaciones guardadas.") y un profesional inactivo | Secciones según el rol; Catálogos; Edición de catálogos; Especialidades y sillón habitual; Edición de especialidades |
| `profesional-formulario.html` | Admin, diálogo "Agregar profesional" con `MATRICULA_DUPLICADA` | Edición de catálogos (Matrícula repetida) |
| `prestaciones-recepcion.html` | Recepción, Prestaciones solo lectura, sin la pestaña Bloqueos | Catálogos (solo lectura); Secciones según el rol |
| `sillones-error.html` | Admin, Sillones con la carga fallida y "Reintentar" | Errores al cargar o guardar |
| `margen.html` | Admin, Margen con `MARGEN_INVALIDO` | Margen entre turnos |
| `horarios.html` | Admin editando el horario de Ana Ejemplo, con una franja sin guardar y `FRANJAS_SUPERPUESTAS` | Edición de horarios por el administrador |
| `horarios-recepcion.html` | Recepción, horario de Ana Ejemplo solo lectura ("No atiende" sábado y domingo) | Horarios de trabajo; Profesional de las secciones de agenda |
| `ausencias.html` | Recepción, lista de ausencias y "Turnos afectados: 2" con "Reasignar" | Ausencias y licencias; Turnos afectados por una ausencia |
| `ausencia-formulario.html` | Recepción, diálogo "Registrar ausencia" con `PERIODO_INVALIDO` | Registro y borrado de ausencias |
| `reasignar.html` | Recepción, diálogo "Reasignar turno" con solo los profesionales habilitados | Reasignación de un turno afectado |
| `bloqueos-odontologo.html` | `ana.ejemplo`, Bloqueos de su profesional fijo, con el aviso "se cruza con 2 turnos dados" | Bloqueos de agenda; Profesional de las secciones de agenda |
| `ausencias-odontologo.html` | `ana.ejemplo`, sus ausencias solo lectura, más el aviso del odontólogo sin profesional vinculado | Ausencias y licencias; Profesional de las secciones de agenda |

## Cómo verlos

Hay que servirlos desde la raíz del repo, porque las rutas a `design-system/` son relativas:

```powershell
python -m http.server 8765
```

Después se abre `http://localhost:8765/openspec/changes/frontend-configuracion-consultorio/mockups/profesionales.html`. Para ver el celular, se usan las herramientas del navegador a 390 × 844. Los botones de los mockups no hacen nada: muestran cada estado en un archivo aparte.
