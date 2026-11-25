# Design

## Context

C-18 dejó la API de coberturas (ver `openspec/specs/catalogo-coberturas/` y `afiliaciones-pacientes/`):

- **Catálogo**: `GET/POST /api/v1/obras-sociales`, `GET/PUT /{id}`, `POST /{id}/activar|desactivar`; planes en `/{id}/planes` con las mismas acciones; nomenclador en `/api/v1/nomenclador`. `GET /obras-sociales` ya trae los planes de cada una. Solo `ADMIN` escribe; `RECEPCIONISTA` y `ODONTOLOGO` leen.
- **Afiliaciones**: `GET/POST /api/v1/pacientes/{id}/afiliaciones`, `PUT /{afiliacionId}` (plan, número y `vigenteDesde`; no `vigenteHasta`), `POST /{afiliacionId}/cerrar` (`vigenteHasta`); `GET /api/v1/pacientes/{id}/cobertura` → `{particular, afiliacion}`. `ADMIN` y `RECEPCIONISTA` escriben; `ODONTOLOGO` lee.
- **Paciente**: `GET /api/v1/paciente/cobertura`, identificado por la sesión.
- **Errores**: `ProblemDetail` con `codigo`. Los `DATOS_INVALIDOS` traen `errores[{campo, mensaje:"valor inválido"}]`: el campo sirve, el mensaje no.

En el frontend:

- `pages/configuracion.ts` arma las pestañas con `crearPestanas` y los permisos de `configuracion/permisos.ts`. `configuracion/ui.ts` tiene `cargarEn`, `guardando`, `abrirDialogo`, `confirmar`, `crearFila` y `mostrarErrorEnFormulario`. `catalogo.ts` resuelve listas planas (sin anidar) con activar/desactivar sin confirmación.
- `pages/pacientes.ts` (C-44) es solo para `ADMIN` y `ODONTOLOGO` (`roles`), y monta el periodontograma (C-46) y las imágenes (C-47) como secciones que se cargan aparte, con `vigente()` y `destruir()`.
- `pages/mi-cuenta.ts` es el ingreso: con sesión de paciente **redirige** a "Mis turnos". No hay hoy una página de cuenta con sesión.
- `agenda/buscador-pacientes.ts` usa `GET /api/v1/pacientes?q=`, que la recepción ya puede pedir.

## Goals / Non-Goals

**Goals:**
- Que el administrador mantenga el catálogo sin salir de Configuración.
- Que la recepción y el administrador afilien, corrijan y cierren sin que la recepción vea datos clínicos.
- Que el odontólogo y el paciente vean la cobertura sin poder cambiarla.

**Non-Goals:**
- Aranceles, porcentajes por plan y presupuestos (C-50) y liquidaciones (C-51).
- Mostrar la cobertura en la agenda o en el detalle del turno.
- Cambiar la API de C-18 o agregar endpoints.

## Decisions

### D1. Una capacidad nueva y deltas mínimos
Todo el comportamiento nuevo va en `frontend-coberturas`. Se modifican solo:
- `frontend-configuracion-consultorio` → "Secciones según el rol" (la pestaña nueva);
- `frontend-cuenta-paciente` → "Cuenta del paciente con sesión iniciada" (D3);
- `frontend-navegacion`, con dos requisitos **ADDED** y sin tocar "Menú según la sesión" (ver D9).

`frontend-ficha-clinica` no se modifica: C-46 tiene su delta abierto. Es el mismo criterio que C-47.

### D2. Cómo llega la recepción a la cobertura *(confirmada por la líder, 2026-11-24)*
**Recomendación**: una página nueva **"Coberturas"** (`#/coberturas`, `roles: ['ADMIN', 'RECEPCIONISTA']`) con el buscador de pacientes, la cabecera del paciente (nombre, DNI, "Cambiar de paciente") y la misma sección de cobertura que la ficha. No pide la ficha ni nada clínico (RN-CL-01). El paciente elegido vive solo en memoria, como en `ficha/seleccion.ts`.

Alternativas:
- *Abrir `#/pacientes` a la recepción y ocultarle lo clínico*: se descarta. Deja los datos clínicos a una condición de rol de distancia, contradice el requisito de navegación de C-44 y obliga a tocar todas las secciones de la ficha.
- *Un botón "Cobertura" en el detalle del turno de la agenda*: es cómodo cuando el paciente llega, pero toca la agenda (módulo crítico de Enzo) y no sirve para un paciente sin turno. Queda como mejora posterior.

El administrador ve la sección en los dos lugares, con el mismo componente.

### D3. "Mi cobertura" del paciente *(confirmada por la líder, 2026-11-24)*
La KB pide verla "en Mi cuenta" (PA-21, US-046 CA-6), pero hoy `#/mi-cuenta` con sesión redirige a "Mis turnos".

**Recomendación**:
- `#/mi-cuenta` con sesión de paciente muestra la página "Mi cuenta" con la sección "Mi cobertura";
- el menú del paciente suma "Mi cuenta" después de "Mis turnos";
- al ingresar se sigue yendo a "Mis turnos" o a la reserva pendiente;
- C-50 sumará "Mis presupuestos" en la misma página (PA-27).

Alternativas:
- *Una página propia `#/mi-cobertura`*: no cambia el ingreso, pero no respeta la KB y C-50 necesitaría otra página más.
- *Una tarjeta arriba de "Mis turnos"*: mezcla dos temas y no deja lugar para los presupuestos.

### D4. Módulos
- `coberturas/api.ts`: pedidos con lectura defensiva (descarta valores con otro tipo; nunca escribe en la consola), como `ficha/api.ts`.
- `coberturas/modelo.ts`, funciones puras que se prueban sin DOM:
  - validación del número (`^[A-Za-z0-9./-]{1,30}$` después de `trim`), de las fechas y de los largos del catálogo;
  - el estado de una afiliación: "Vigente" (lo dice la API con `vigente`), "Próxima" (`vigenteDesde` posterior a hoy) o "Finalizada";
  - los textos de vigencia ("Desde 01/01/2026, sin fecha de fin", "Del … al …");
  - el "Vigente desde" por defecto: hoy, o el día siguiente al `vigenteHasta` más reciente si es posterior;
  - "hoy" con `shared/fecha/zona.ts` (`America/Argentina/Buenos_Aires`).
- `coberturas/catalogo.ts`: `montarObrasSociales(panel, contexto)` para la pestaña.
- `coberturas/seccion.ts`: `crearSeccionCobertura({ pacienteId, nombrePaciente, editable, api, mensajeDeError, vigente })` → `{ contenedor, destruir() }`.
- `coberturas/mi-cobertura.ts`: la sección del paciente.
- `pages/coberturas.ts`: la página nueva; `pages/pacientes.ts` solo monta la sección entre la cabecera y el odontograma y llama a `destruir()` al cambiar de paciente.

### D5. Pestaña "Obras sociales"
- **Permiso**: un campo nuevo `gestionaCoberturas` en `Permisos`, verdadero solo para `ADMIN`. Sin él, la pestaña no se agrega y no se pide nada (los otros roles pueden leer el catálogo por la API, pero el alcance de C-49 lo deja en el administrador).
- **Un solo panel con dos bloques**, "Obras sociales y prepagas" y "Nomenclador" *(confirmada por la líder, 2026-11-24; alternativa descartada: pestaña "Nomenclador" aparte)*. Recomendación: un panel, porque las dos cosas se cargan juntas al empezar y Configuración ya tiene nueve pestañas.
- Cada bloque carga con su `cargarEn`, con su error y "Reintentar". Las prestaciones del diálogo de códigos se piden recién al abrirlo.
- No se reusa `montarCatalogo`: es para listas planas. Sí se reusan `abrirDialogo`, `guardando`, `crearEstado` y `mostrarErrorEnFormulario`.
- El cuerpo del `POST`/`PUT` de obra social manda `sigla: null` si está vacía.

### D6. Activar y desactivar sin confirmación *(confirmada por la líder, 2026-11-24)*
Como en C-34, "Activar" y "Desactivar" no piden confirmación: son reversibles y no borran nada. Una obra social inactiva muestra la ayuda "Inactiva: no se puede afiliar a sus planes. Las afiliaciones que ya existen no cambian.". Alternativa: confirmar solo al desactivar una obra social con planes activos.

### D7. Sección de cobertura
- Pide `cobertura` y `afiliaciones` juntos; un contador descarta respuestas viejas (el cliente no admite `AbortController`, como en C-47).
- Las acciones van en las filas del historial: "Corregir" en todas y "Cerrar afiliación" en las que no tienen `vigenteHasta`. La tarjeta "Cobertura de hoy" es solo informativa.
- **Diálogos** (`abrirDialogo` de `shared/ui/dialogo.ts`):
  - "Afiliar" y "Corregir" piden `GET /api/v1/obras-sociales` al abrirse (una vez por sección) y ofrecen solo obras sociales y planes activos; "Corregir" suma el plan actual aunque esté inactivo, con "(inactivo)".
  - El plan queda deshabilitado hasta elegir la obra social.
  - La validación previa usa `modelo.ts`; si falla, el foco va al primer campo con error.
  - Los errores por campo de la API se ubican por `campo` (`numeroAfiliado`, `vigenteDesde`, `vigenteHasta`, `planId`) con mensajes propios, porque el backend solo dice "valor inválido".
  - `AFILIADO_DUPLICADO` se muestra junto al número; los demás códigos, arriba del diálogo.
- **Cambiar de cobertura en dos pasos** *(confirmada por la líder, 2026-11-24)*: primero "Cerrar afiliación", después "Afiliar", que propone como inicio el día siguiente al cierre. Alternativa: un diálogo "Cambiar de cobertura" que hace los dos pedidos seguidos; se descarta por ahora porque la API no es atómica y una falla del segundo dejaría al paciente sin la nueva.
- `AFILIACION_CERRADA` cierra el diálogo, recarga y avisa en la sección.
- El odontólogo no ve botones y ve la ayuda de solo lectura.

### D8. Mensajes
Se suman a `MENSAJES_POR_CODIGO`:
- `OBRA_SOCIAL_EXISTENTE`: "Ya existe una obra social o prepaga con ese nombre.";
- `PLAN_EXISTENTE`: "Esa obra social ya tiene un plan con ese nombre.";
- `CODIGO_NOMENCLADOR_EXISTENTE`: "Ya existe ese código en el nomenclador.";
- `PRESTACION_INEXISTENTE`: "La prestación elegida ya no existe.";
- `VIGENCIA_SUPERPUESTA`: "El paciente ya tiene una cobertura en esas fechas. Cerrá la vigente o cambiá las fechas.";
- `AFILIADO_DUPLICADO`: "Ese número ya lo tiene otro paciente de la misma obra social.";
- `PLAN_INACTIVO`: "El plan está inactivo. Elegí otro.";
- `OBRA_SOCIAL_INACTIVA`: "La obra social está inactiva. Elegí otra.";
- `VIGENCIA_INVALIDA`: "La fecha de fin no puede ser anterior a la de inicio.";
- `AFILIACION_CERRADA`: "Esa afiliación ya estaba cerrada.".

### D9. Navegación
- `paginaCoberturas`: `acceso: 'personal'`, `menu: 'personal'`, `icono: 'credencial'`, `roles: ['ADMIN', 'RECEPCIONISTA']`, `avisoSinRol` con el texto de Reportes. En `PAGINAS` va entre `paginaPacientes` y `paginaReportes`.
- "Mi cuenta" en el menú del paciente: `paginaMiCuenta` pasa a tener `menu: 'paciente'` y `icono: 'cuenta'`, y el layout la muestra solo con sesión `PACIENTE` (un campo nuevo `soloConSesion` en `Pagina`, porque la página sigue siendo pública para ingresar).
- "Menú según la sesión" de `frontend-navegacion` **no se modifica** en este change: ya lo modifican C-22 (`caja-y-cobros`) y `frontend-uniformidad-visual`. Sus escenarios que enumeran "solo" ciertos enlaces quedan desactualizados. Antes de archivar C-49 se reconcilia con un delta MODIFIED sobre el texto que esté en `openspec/specs` en ese momento (tarea 9.2).

### D10. Estilos e íconos
- `frontend/src/styles/24-coberturas.css` (era `22-coberturas.css`; al mergear master, C-22 y C-15 ya usaban `22-caja.css` y `23-lista-espera.css`), importado al final de `styles.css` y registrado en `REGLAS_POR_MODULO` de `test/estilos-modulos.test.ts`. Es `mockups/coberturas.css` sin las reglas "Solo mockups". Solo tokens de la Revision 5; sin `outline: none`. El design system no cambia de revisión.
- `shared/ui/iconos.ts` suma `credencial` y `cuenta`, con el trazo de los existentes y `aria-hidden`.

### D11. Privacidad
- Ningún `console.*` con datos; la dirección queda en la ruta de la página; nada en `localStorage` ni `sessionStorage`.
- El paciente elegido en "Coberturas" vive en la memoria de la página y se pierde al recargar.
- Los mensajes de error nunca incluyen el número escrito.

## Risks / Trade-offs

- **[Riesgo] Recepción y RN-CL-01.** → La página "Coberturas" no importa nada de `ficha/`; un test verifica que no se pide ninguna ruta clínica.
- **[Riesgo] Conflictos con otros changes abiertos** (C-22 y `frontend-uniformidad-visual` en la navegación; C-50 y C-51 en `styles.css` y Configuración). → C-49 a C-51 van en un PR por vez, en orden; "Menú según la sesión" se reconcilia antes del archive (D9).
- **[Riesgo] Cambiar `#/mi-cuenta` con sesión** rompe el test de C-12 que espera la redirección. → El escenario cambia en el delta y el test se actualiza con él; el ingreso sigue llevando a "Mis turnos".
- **[Trade-off] Cambio de cobertura en dos pasos**: dos diálogos en vez de uno, pero sin estados intermedios silenciosos.
- **[Limitación del backend] No se puede corregir la fecha de fin** de una afiliación ya cerrada: `PUT` no la acepta y cerrar otra vez da `AFILIACION_CERRADA`. El diálogo "Corregir" lo aclara. Si hace falta, se pide en un change de backend aparte.

## Migration Plan

Sin migraciones ni cambios de backend. Rollback: revertir el PR. Las afiliaciones y el catálogo cargados quedan en el backend.

## Open Questions

- ¿El odontólogo debería ver la cobertura también desde el turno de la agenda? Se puede sumar después sin cambiar estos specs.

## UI Mockups

- Design system revision: 5 (sin tokens nuevos).
- Decision: yes (2026-11-24). Modo `system-and-mockups`.
- Mockups: `openspec/changes/frontend-obras-sociales/mockups/` (ver su `README.md`).
- Approval: approved (2026-11-24, by Sofia Indovino). Confirmó D2, D3, D5, D6 y D7 tal como están recomendadas.
- Preview: rendered (2026-11-24). Nueve pantallas en `mockups/previews/`, a 390 × 844 y 1440 × 900, con Playwright sobre un servidor estático local; sin desplazamiento horizontal, sin errores de consola ni pedidos fallidos.
- Visual verify: passed with minor differences (2026-11-25). Frontend con Vite contra el backend de desarrollo (perfil `dev`, PostgreSQL 16 temporal), Playwright con Chromium, 9 pantallas × 390 × 844 y 1440 × 900 (18 capturas en `mockups/verify/`). Sin errores ni advertencias de consola, sin pedidos fallidos y sin desplazamiento horizontal en ninguna.
  - Revisadas: `config-obras-sociales` y `config-formularios` (admin), `cobertura-ficha` (admin), `cobertura-odontologo` (`ana.ejemplo`), `coberturas-recepcion`, `cobertura-formularios` y `cobertura-cerrar` (`recepcion`), `mi-cuenta-cobertura` y `mi-cuenta-particular` (pacientes).
  - Diferencias (todas minor): (1) el diálogo de plan rotula "Nombre", como dice el spec, y el mockup "Nombre del plan"; (2) al abrir "Afiliar" el foco queda en "N.º de afiliado", porque "Obra social" está deshabilitado mientras carga el catálogo; (3) solo de datos: en la base `dev`, N-0301 tiene la prestación "Extracción simple" (seed de C-19) y Paciente Uno no tiene la afiliación finalizada del mockup.
  - Usuarios: `paciente.uno` y `paciente.dos` no se pudieron usar porque el seed `dev` crea esos pacientes sin cuenta y el registro responde `DNI_YA_REGISTRADO`; "Mi cuenta" se verificó con dos pacientes ficticios nuevos (DNI 99000004 con Plan Base y 99000005 particular).
  - No revisado en el navegador: los estados de error y de carga, los rechazos 409 dentro de los diálogos y `AFILIACION_CERRADA` (cubiertos por los tests con jsdom).
