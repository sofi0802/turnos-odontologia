# Design

## Context

C-02 dejó en `frontend/` un cliente de API (`shared/api/cliente.ts` y `errores.ts`), un router por hash (`shared/ui/router.ts`), un layout con menú adaptable (`shared/ui/layout.ts`) y tres páginas que solo muestran su título. Hoy el cliente traduce `409` y `422`, pero `401`, `403` y `404` llegan como `ErrorGenerico` y el `422` no trae la lista `errores` (`[{campo, mensaje}]`) que arma el backend.

El backend expone `agenda-api` (C-06) y la sesión de C-05: `POST /api/v1/public/login` (`{username, password}` → `{autenticado, username, rol}`), `GET /api/v1/public/sesion` (misma forma y cookie `XSRF-TOKEN`) y `POST /api/v1/logout` (`204`). Sin sesión, la API responde `401` con `NO_AUTENTICADO`. `GET /api/v1/turnos` devuelve ids de profesional, sillón y prestación (no sus nombres) y `esSobreturno` (C-08). Los nombres salen de los catálogos de C-04 (ver "Contratos que consume" en proposal.md).

Restricciones: TypeScript `strict` sin `any` ni frameworks, Vite, Vitest con jsdom, datos ficticios, responsivo.

## Goals / Non-Goals

**Goals:**
- Pantallas probables sin navegador: la lógica de fechas, formateo de mensajes y armado de pedidos vive en funciones puras con tests unitarios; el DOM se prueba con jsdom.
- Un único lugar para la zona horaria y otro para los textos de error, para no repetirlos por pantalla.

**Non-Goals:**
- Estado global, caché de turnos o actualización en tiempo real: cada acción vuelve a pedir la agenda.
- Vista por hora en grilla (calendario de bloques): se muestra una lista por día, que funciona igual en celular.
- Internacionalización: los textos están en español en el código.

## Decisions

### D1. Módulos nuevos
- `shared/api/errores.ts`: suma `ErrorSesion` (401), `ErrorPermiso` (403) y `ErrorNoEncontrado` (404), todos con `codigo: string | null`, y `ErrorValidacion.errores: readonly ErrorCampo[]`. `leerProblemDetail` también lee `errores` con validación de forma (nunca falla).
- `shared/sesion/sesion.ts`: `consultarSesion()`, `iniciarSesion()`, `cerrarSesion()` y `esPersonal(rol)` sobre `ClienteApi`.
- `shared/fecha/zona.ts`: zona `America/Argentina/Buenos_Aires`, `inicioDelDia`, `inicioDeSemana` (lunes), `sumarDias`, `aIsoConZona` y `formatearHora`.
- `agenda/`: `periodo.ts` (día/semana y navegación), `consulta.ts` (arma la query de `GET /turnos`), `catalogos.ts`, `mensajes.ts` (código → texto), `vista.ts` (lista de turnos), `formularios.ts` (nuevo, cancelar, reprogramar) y `pages/agenda.ts` que los une.
- `pages/ingreso.ts`: página `#/ingreso`.

Alternativa descartada: un archivo `agenda.ts` grande. Separar la lógica pura permite cubrir cada escenario con tests rápidos.

### D2. Zona horaria con `Intl`, no con un desplazamiento fijo
Las horas se muestran con `Intl.DateTimeFormat('es-AR', {timeZone: ZONA})`, y para armar `desde`/`hasta` se obtiene el desplazamiento de la zona para esa fecha con `timeZoneName: 'longOffset'`. Argentina hoy usa `-03:00` todo el año, pero no escribir `-03:00` en el código evita un error si eso cambia y hace que los tests no dependan de la zona de la máquina que los corre. Los tests fijan "hoy" con `vi.setSystemTime`.

### D3. Guarda de sesión en el router
`Pagina` suma una propiedad opcional `privada: true`. Antes de dibujar una página privada, `app.ts` consulta la sesión: sin sesión, redirige a `#/ingreso` (con `history.replaceState`); con rol `PACIENTE`, dibuja el aviso de acceso no permitido. Un `ErrorSesion` en cualquier pedido de la agenda lleva a `#/ingreso?vencida` para mostrar el aviso de sesión terminada.

Alternativa descartada: que cada página consulte la sesión por su cuenta, porque repite la lógica y se olvida fácil en C-12.

### D4. Nombres en la agenda a partir de los catálogos
La agenda pide en paralelo turnos, profesionales, sillones y prestaciones, y arma un `Map<id, nombre>` por catálogo. Si un catálogo falla o falta un id, se muestra "Profesional 3", "Sillón 2" o "Prestación 1", para que la agenda nunca quede vacía si un catálogo falla. Se usan las formas de `catalogo-consultorio`: de los profesionales, `id`, `nombre`, `apellido` y `activo` (mostrado como "Ana Ejemplo"); de sillones y prestaciones, `id`, `nombre` y `activo`. Los demás campos se ignoran. La lectura y validación de esas respuestas vive en `catalogos.ts`. Los listados incluyen inactivos: la agenda los usa para los nombres y el formulario solo ofrece los activos.

### D5. Formularios con `<dialog>` nativo
Los formularios usan `<dialog>` con `showModal()`, que da foco atrapado y cierre con Escape sin librerías. jsdom no implementa `showModal`, así que los tests usan un pequeño polyfill en `test/setup.ts`. Los errores de campo se enlazan con `aria-describedby` y el mensaje general va en una región `role="alert"`.

### D6. Mensajes por código en una tabla
`mensajes.ts` exporta `mensajeDeError(error: Error): string` con un `Record<string, string>` de códigos conocidos. Un código desconocido usa el `detail`, y si falta, el mensaje genérico. Incluye los códigos de C-07 (`FUERA_DE_HORARIO`, `PROFESIONAL_AUSENTE`, `AGENDA_BLOQUEADA`), y los changes que vengan suman los suyos en el mismo lugar.

## Risks / Trade-offs

- [Un catálogo cambia de forma en el futuro] → la lectura está aislada en `catalogos.ts`, valida la forma y, si no coincide, se usa el respaldo con el id.
- [Con C-07 aplicado, la base de dev sin franjas no acepta turnos] → las pruebas manuales usan el seed de dev (Ana Ejemplo lunes a viernes de 8 a 13, Bruno Prueba de 10 a 18); un `409 FUERA_DE_HORARIO` fuera de esas franjas es lo esperado.
- [El paciente se elige por id] → incómodo para recepción; es temporal hasta que exista una búsqueda de pacientes. Queda anotado como pendiente en CHANGES.md.
- [Cancelar exige DNI y número de reserva] → recepción tiene que pedírselos al paciente. Es lo que permite el contrato actual; si Sofia suma una cancelación del personal sin esos datos, solo cambia `formularios.ts`.
- [Agenda semanal con muchos turnos] → lista larga en el celular; aceptable para un consultorio chico (sin paginación).

## Migration Plan

Solo frontend, sin cambios de backend ni de base. Se despliega con el build de Vite; para volver atrás alcanza con revertir el PR.

## UI Mockups

- Design system revision: no aplica (sin design system).
- Decision: no (project policy), 2026-11-21. `visual_design.mode` es `off`.
- Mockups: ninguno.
- Approval: no aplica.
- Preview: no aplica.
- Visual verify: pendiente; en verify se capturan 390 × 844 y 1440 × 900 como evidencia en `verify/`.
