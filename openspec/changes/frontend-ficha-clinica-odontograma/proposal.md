# Proposal

## Why

C-13 hizo el backend de la ficha clínica, el odontograma y las ausencias del paciente (`/api/v1/pacientes/{id}/ficha`, `/odontograma`, `/odontograma/historial` y `/ausencias`), pero ninguna pantalla lo usa. Hoy el odontólogo no puede cargar ni consultar los antecedentes, las alergias, la medicación ni el odontograma de un paciente desde la aplicación, y tampoco marcar desde la ficha que el paciente no vino (RN-AG-17). C-44 suma esa interfaz para que la Épica 5 (US-040 a US-042) se pueda usar.

## What Changes

- **Página nueva "Pacientes"** (`#/pacientes`), solo para `ADMIN` y `ODONTOLOGO`. Busca al paciente con el buscador de C-36 y muestra su ficha. Figura en la barra lateral, entre "Agenda" y "Configuración", solo para esos dos roles. La recepción y el paciente no la ven, y si abren la dirección reciben un aviso sin que se pida ningún dato (RN-CL-01).
- **"Ver ficha" en la agenda**: cada turno ofrece abrir la ficha de su paciente, solo con sesión `ADMIN` u `ODONTOLOGO`.
- **Ficha básica**: antecedentes, alergias y medicación, con quién la actualizó y cuándo. El odontólogo la edita y la guarda con `PUT /ficha`. El administrador solo la lee. Un paciente sin ficha la muestra vacía.
- **Odontograma**:
  - un dibujo de las piezas en notación FDI, permanentes (11 a 48) y temporales (51 a 85);
  - cada pieza tiene sus cinco caras y su estado de pieza completa, coloreados según el estado vigente, con leyenda y texto (no solo color);
  - el odontólogo elige pieza, cara (o pieza completa) y estado, junta uno o más cambios y los guarda en un solo `PUT /odontograma`;
  - se puede usar con teclado;
  - el historial se muestra del más nuevo al más viejo, con el profesional y el momento, y no se edita (RN-CL-02, RN-CL-03).
- **Ausencias**:
  - la ficha lista las ausencias del paciente, incluidas las que deshizo la recepción (C-43);
  - abierta desde un turno propio, el odontólogo puede "Marcar ausente" (`POST /ausencias`), habilitado a los 15 minutos del inicio (RN-AG-17).
- **Errores**: mensajes en español para `403`, `404 RECURSO_NO_ENCONTRADO`, `422` (`DATOS_INVALIDOS`, `PIEZA_INVALIDA`, `CARA_INVALIDA`) y los rechazos de la ausencia (`AUSENCIA_ANTES_DE_TOLERANCIA`, `LLEGADA_REGISTRADA`, `TURNO_NO_ACTIVO`).
- **Privacidad**: ningún dato de salud en la dirección, en `localStorage` o `sessionStorage`, ni en la consola (Ley 26.529, regla dura 6).
- **Fuera de alcance**:
  - cambios de backend o de la API;
  - periodontograma, imágenes y consentimientos (C-25 a C-27);
  - imprimir o exportar la ficha;
  - que la recepción vea algo de la ficha.

## Capabilities

### New Capabilities

- `frontend-ficha-clinica`: la página "Pacientes" y la ficha del paciente en el frontend:
  - búsqueda del paciente;
  - ficha básica;
  - odontograma con historial;
  - ausencias y "Marcar ausente";
  - permisos por rol, errores y privacidad de los datos de salud.

### Modified Capabilities

- `frontend-navegacion`: suma la página "Pacientes", que figura en el menú y se abre solo con sesión `ADMIN` u `ODONTOLOGO`.
- `frontend-agenda-personal`: cada turno ofrece "Ver ficha" con sesión `ADMIN` u `ODONTOLOGO`.

## Impact

- **Frontend**, dueño Enzo, se le avisa en el PR:
  - una carpeta nueva `frontend/src/ficha/` y la página `frontend/src/pages/pacientes.ts`;
  - un campo `roles` en `Pagina`, que usan `layout.ts` y `app.ts`;
  - `pacienteId` en `TurnoAgenda` (la API ya lo envía);
  - "Ver ficha" en `agenda/vista.ts`;
  - estilos nuevos en `src/styles/`.
- **Backend:** ninguno. Se usan los endpoints de C-13 (`clinica/`, de Emanuel) y `GET /api/v1/pacientes?q=` de C-36. No hay migraciones.
- **Tests:**
  - un escenario por requisito en `frontend/test/` (ficha, odontograma, permisos por rol, navegación y agenda);
  - siguen pasando los de C-09, C-36, C-39 y C-43.
- **Diseño:**
  - modo `system-and-mockups`: mockups de "Pacientes", de la ficha y del odontograma a 390 y 1440 px, aprobados antes del apply;
  - colores nuevos para los estados del odontograma en `design-system/tokens.css`, que suben la revisión del design system;
  - la revisión 3 del design system espera que la líder renueve la aprobación.
- **Governance:** ALTO, por los datos de salud (Ley 26.529). La líder revisa la propuesta antes de escribir código.
