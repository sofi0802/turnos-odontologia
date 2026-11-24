# Mockups de C-44 `frontend-ficha-clinica-odontograma`

- Design system revision: 4 (`design-system/README.md`). C-44 suma los tokens `--ds-odonto-*` y la sección "Odontograma" de `components.html`. La aprobación de las revisiones 2, 3 y 4 sigue pendiente con la líder.
- Approval: approved (2026-11-24, by Emanuel). La revisión 4 del design system sigue esperando que Sofia la renueve.
- Preview: rendered (2026-11-24).
  - Las capturas están en `previews/`, a 390 × 844 y 1440 × 900, de página completa, con Playwright.
  - Ninguna pantalla tiene desplazamiento horizontal, errores de consola ni pedidos fallidos.
  - Las capturas de `design-system/previews/` se actualizaron con la sección nueva.

Los aprueba la líder antes del apply (modo `system-and-mockups`). Una vez aprobados, son el criterio de aceptación visual de los grupos 1 a 6 de `tasks.md`.

## Cómo verlos

- Los mockups cargan `frontend/src/styles.css` tal como está en `master`, más `ficha.css`, que es la propuesta del módulo nuevo `17-ficha.css`. Así lo que se ve coincide con la aplicación.
- `mockup.js` arma el encabezado y la barra lateral, y dibuja el odontograma en SVG con la geometría de design D4.
- Algunos navegadores bloquean los `@import` desde `file://`. Para evitarlo, serví la raíz del repo y abrí la página desde ahí:

  ```powershell
  python -m http.server 8765 --bind 127.0.0.1
  ```

  Después abrí `http://127.0.0.1:8765/openspec/changes/frontend-ficha-clinica-odontograma/mockups/ficha-odontologo.html`.

## Brief

- **Para quién:**
  - **Odontólogo:** carga la ficha y el odontograma durante la consulta, casi siempre desde la computadora del consultorio y a veces desde una tablet o un celular.
  - **Administrador:** consulta la ficha sin modificarla.
- **Qué tienen que poder hacer:**
  - encontrar al paciente desde la agenda o por DNI;
  - ver de un vistazo el estado de la boca;
  - registrar varios cambios y guardarlos juntos;
  - leer los antecedentes, las alergias y la medicación;
  - marcar que el paciente no vino.
- **Pantallas afectadas:**
  - la página nueva "Pacientes" (`#/pacientes`), en la barra lateral entre Agenda y Configuración;
  - "Ver ficha" en cada turno de la agenda.
- **Tamaños:**
  - mobile first a 390 × 844;
  - a 1440 × 900, la barra lateral queda a la izquierda y la ficha usa el ancho del contenido (72 rem), con dos columnas debajo del odontograma.
- **Sistema:** solo variables de `design-system/tokens.css` y el layout de la etapa B de C-39, ya aplicada.
- **Restricciones:**
  - datos 100 % ficticios (Paciente Uno, Paciente Dos, Elena Ejemplo, Ana Ejemplo, Bruno Prueba, `admin`, `recepcion`);
  - los antecedentes, las alergias y la medicación de ejemplo son inventados;
  - no hay recursos remotos.

## Decisiones de diseño

- **El odontograma usa el esquema de cinco caras.** Cada pieza es un cuadrado central (oclusal) con cuatro trapecios alrededor:
  - arriba y abajo, vestibular y palatina (o lingual en el maxilar inferior);
  - a los costados, mesial y distal;
  - la mesial siempre queda del lado de la línea media, que se ve punteada.
- **Cómo se ve cada estado:**
  - **Sano, caries, obturado y a tratar** pintan la cara. La cara oclusal lleva además la letra del estado.
  - **Ausente, corona y endodoncia** se marcan sobre la pieza completa: una cruz, un contorno y una línea vertical.
  - La leyenda tiene las siete letras y los nombres.
  - "Estado vigente en texto" lista lo mismo para quien no distingue los colores o usa un lector de pantalla.
- **Escritorio y celular:**
  - En escritorio, la boca entera va en un solo dibujo.
  - En el celular se parte en dos dibujos, "Lado derecho del paciente" y "Lado izquierdo del paciente", así cada cara mide unos 35 px en lugar de 17.
  - La edición se hace en el panel de abajo, con controles de 44 px.
- **Los cambios no se guardan uno por uno.** Se suman a "Cambios sin guardar", que muestra cada uno con "Quitar". En el dibujo se ven con borde punteado hasta "Guardar cambios (n)". Si se cambia de paciente con cambios sin guardar, se pide confirmación.
- **"Marcar ausente" va en la franja del turno**, arriba de todo, y solo aparece si la ficha se abrió desde un turno propio del odontólogo. Es un botón de peligro, porque cambia el turno. Antes de los 15 minutos se ve deshabilitado con "Desde las HH:mm", como en la agenda de C-43.
- **El administrador ve todo como texto**, con el aviso "Solo lectura: la ficha clínica la edita el odontólogo." y sin ningún control de edición.
- **El historial se pliega y se pide recién al abrirlo.** En el celular, cada registro se apila en lugar de usar columnas.

## Pantallas

| Archivo | Pantalla y estado | Requisitos |
|---|---|---|
| `pacientes-buscar.html` | "Pacientes" de la odontóloga sin paciente elegido, con el buscador abierto y dos coincidencias | Elegir al paciente en "Pacientes"; Página "Pacientes" para odontólogos y administradores |
| `ficha-odontologo.html` | Ficha de Paciente Uno abierta desde el turno de las 10:00 (son las 10:20). "Marcar ausente" habilitado, cara elegida, dos cambios sin guardar, ficha editable, ausencias (una deshecha) e historial abierto | Abrir la ficha desde un turno; Ficha básica; Editar la ficha básica; Odontograma; Estado de pieza completa; Odontograma accesible; Registrar cambios; Historial; Ausencias; Marcar ausente |
| `ficha-admin.html` | La misma ficha vista por `admin`, en solo lectura | Solo lectura para el administrador |
| `ficha-estados.html` | Seis estados:<br>1. antes de la tolerancia, con las piezas temporales;<br>2. paciente sin ficha;<br>3. error de campo y rechazos del servidor;<br>4. falla de la carga con "Reintentar";<br>5. confirmación al cambiar de paciente con cambios;<br>6. la recepción abre `#/pacientes` | Marcar ausente; Odontograma (temporales); Ficha básica (sin ficha); Errores al cargar la ficha; Registrar cambios (límite y rechazo); Página "Pacientes" |
| `agenda-ver-ficha.html` | Agenda de la odontóloga con "Ver ficha" en cada turno, también en el atendido | Ver la ficha desde un turno |

## Capturas

`previews/<archivo>-390.png` y `previews/<archivo>-1440.png` para cada pantalla de la tabla.
