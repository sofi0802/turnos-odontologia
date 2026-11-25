# Design

## Context

- **Cancelaciones.** Todas las cancelaciones y reprogramaciones pasan por `ServicioAgenda.cancelar` y `ServicioAgenda.reprogramar` (`agenda/infrastructure`), con una transacción y la auditoría. La reserva online (`reserva/`) llega ahí por `AgendaDeTurnos`, y el link del recordatorio lleva a Mis turnos, que también usa ese camino. La reasignación por ausencia (`reasignar`) y la ausencia del paciente (`marcarAusencia`) son métodos aparte.
- **Reserva online.** `ReservarTurno` asigna el sillón con `AsignadorDeReserva` (RN-RO-06) y crea el turno con `agenda.crear`. Usa el actor `paciente-online:<usuarioId>`, que necesita una cuenta.
- **Confirmación por token (C-08).** Es el modelo de la oferta: token de 256 bits en base64url, guardado como hash, `POST` público con CSRF, y el link `/confirmar/{token}` que el frontend traduce a `#/confirmar` y guarda el token solo en memoria (`entrada.ts`).
- **Configuración.** Vive en la tabla `configuracion` (clave y valor), con el margen como ejemplo (`MargenVigenteJpa`, `/api/v1/configuracion/margen`).
- **Tareas programadas.** Siguen el patrón de `TareaRecordatorios`: `@Scheduled` con una propiedad para activarla y otra para el intervalo.
- **Migraciones.** Flyway corre con `out-of-order: true`, y CHANGES.md reserva V9 para este change.

## Goals / Non-Goals

**Goals:**
- Inscripción, ofertas de a una por orden de llegada, plazo configurable, aceptación y rechazo por link, vencimientos y bajas, según RN-LE-01 a RN-LE-09.
- Pantallas para la recepción, para el paciente, la pública de la oferta y el plazo en Configuración.
- Sin tocar las garantías contra solapamientos ni los estados del turno (regla dura 5, DD-22).

**Non-Goals:**
- Reservar el horario mientras se ofrece (DD-22).
- Ofertas por ausencia del paciente, por reasignación o por un sobreturno.
- Ofrecer un horario parcial: el turno ofrecido empieza en el mismo inicio del liberado.
- Cambiar RN-RE-05: los turnos aceptados desde la lista cuentan como del consultorio en los reportes.
- Ver la lista de espera desde la agenda del odontólogo.

## Decisions

### D1. Módulo `agenda/listaespera` con dominio propio
- `dominio`: `Inscripcion`, `Oferta`, sus estados, el `PlazoOferta` (1–24 h) y la regla de compatibilidad, pura y testeable sin base.
- `aplicacion`: `Inscribir`, `DarDeBaja`, `OfrecerHorario`, `ResponderOferta` y `VencerOfertas`.
- `infraestructura`: JDBC, configuración y controladores.

Es el lugar que CHANGES.md reservó (`backend/.../agenda/` lista de espera, Sofia). *Alternativa*: un módulo `listaespera/` de primer nivel. Se descarta porque la lista depende de reglas de la agenda y de su transacción.

### D2. El disparo es una llamada directa en `ServicioAgenda`, después del commit
Después de `cancelarTurno.ejecutar` y de `reprogramarTurno.ejecutar`, `ServicioAgenda` llama a un puerto `HorariosLiberados` con el turno cancelado. Ese puerto lo implementa `ServicioListaEspera`, y en los tests de agenda se usa uno que no hace nada. El puerto se resuelve tarde (`ObjectProvider`), porque la lista de espera depende de la reserva online, que a su vez depende de la agenda.

**Cambio durante el apply**: la oferta se crea **después del commit** de la cancelación, en su propia transacción (`REQUIRES_NEW`), y no en la misma. Si se creaba en la misma, una falla de la oferta (o una violación de un índice único, que en PostgreSQL aborta toda la transacción) deshacía la cancelación. Si la llamada llega dentro de una transacción externa, espera a su `afterCommit`. Una falla queda en el log solo con el id del turno, y la cancelación ya está guardada. Si el proceso se corta entre los dos commits, ese horario queda libre sin oferta: es el riesgo que se acepta, igual que con `@TransactionalEventListener(AFTER_COMMIT)`, que se descartó por depender de eventos de Spring en el dominio.

### D3. Compatibilidad: se reusa el validador de la reserva online
Para cada inscripción candidata, por orden de llegada, se arma una solicitud igual a la de la reserva online: la prestación de la inscripción, el profesional del turno liberado y el inicio del turno liberado. Se le pide a `AsignadorDeReserva.preparar` y a la verificación de disponibilidad si existe un sillón libre que cumpla todas las reglas. La primera que da sí recibe la oferta. Las inscripciones se recorren con `SELECT … FOR UPDATE SKIP LOCKED`, para que dos cancelaciones simultáneas no ofrezcan dos veces a la misma persona. Los índices únicos parciales son la última defensa:
- una oferta `PENDIENTE` por `turno_liberado_id`;
- una oferta `PENDIENTE` por inscripción.

Si un índice salta, se pasa a la siguiente. *Alternativa*: duplicar las reglas en el módulo. Se descarta: dos implementaciones de las reglas de la agenda terminarían por divergir.

### D4. Aceptar crea el turno por el mismo camino que la reserva online
`ResponderOferta.aceptar` bloquea la oferta (`FOR UPDATE`) y verifica que esté `PENDIENTE` y no vencida. Después crea el turno con `AsignadorDeReserva.asignar` y `agenda.crear`, con el actor `lista-espera:<ofertaId>`: el paciente puede no tener cuenta. Las restricciones `EXCLUDE` siguen siendo la garantía final. Un `ReservaRechazadaException` o una violación de exclusión se traducen en `OFERTA_NO_DISPONIBLE`. En ese caso la oferta queda `NO_DISPONIBLE` y no se pasa a nadie: si el horario se ocupó, ya no está libre. El aviso de turno reservado se reusa del de la reserva online (`AvisoReservaOnline.reservado`).

**Cambio durante el apply**: la aceptación son tres pasos, cada uno en su transacción, porque la agenda abre la suya para crear el turno:
1. Bloquear la oferta, verificarla y marcarla `ACEPTADA`. Así un segundo pedido con el mismo token ya la ve respondida.
2. Crear el turno por la fachada `ReservaPorListaEspera` del módulo `reserva`.
3. Enlazar el turno con la oferta y pasar la inscripción a `ASIGNADA`.

Si el paso 2 rechaza el horario, la oferta pasa a `NO_DISPONIBLE`. Ante cualquier otra falla, vuelve a `PENDIENTE`.

La compatibilidad solo ofrece inicios que caen en la grilla de horarios del profesional, la misma de la reserva online. Un turno de recepción fuera de esa grilla no se ofrece al cancelarse.

### D5. Tokens y endpoints públicos como en C-08
- **Token**: `GeneradorTokenSeguro` y `HuellaToken`, de `notificaciones/`. El hash se guarda en `oferta_lista_espera.token_hash`.
- **Ver la oferta**: `GET /api/v1/public/ofertas/{token}` devuelve la fecha, la hora, el profesional, el vencimiento y el estado. No consume el token, porque los revisores de links abren las direcciones solos.
- **Responder**: `POST …/aceptar` y `POST …/rechazar`, con CSRF.
- **Token mal formado**: responde `404 OFERTA_INEXISTENTE` sin ir a la base.

### D6. Aviso de la oferta por un adaptador de notificaciones, sin fila en `notificacion`
**Cambio durante el apply**: no hay `TipoNotificacion` nuevo. La tabla `notificacion` está atada al turno y a su paciente, y la oferta es para otro paciente sobre un turno ajeno. Por eso el aviso es un puerto de la lista de espera (`AvisoOferta`) y lo implementa `AvisoOfertaNotificaciones` en `notificaciones/`:
- `MensajeRecordatorio.oferta`: la fecha, la hora, el consultorio, el vencimiento y el link `Enlaces.oferta(token)` = `{base}/oferta/{token}`. No lleva la prestación ni nombres.
- El canal sale de `ContactoPaciente.canalDeEnvio()`, con el consentimiento y el WhatsApp de RN-NO-02 y RN-NO-03. Si WhatsApp falla y el paciente tiene email, sale por email en el mismo intento.
- El aviso sale fuera de toda transacción y no se guarda en `notificacion`, así el token nunca se persiste en claro. La oferta guarda si el aviso salió (`aviso_enviado`, `aviso_intentos`).
- La tarea de la lista de espera reintenta los avisos que fallaron, hasta 3 intentos. Cada reintento genera un **token nuevo** y reemplaza su hash: el anterior no se puede reconstruir.
- El módulo es de Juan Cruz, que revisa esta parte.

### D7. Vencimientos con una tarea programada
- `TareaListaEspera` corre cada `app.lista-espera.intervalo-ms` (por defecto, 1 minuto) y se activa con `app.lista-espera.activo`.
- Marca `VENCIDA` cada oferta `PENDIENTE` con `vence_en <= now()`, con `FOR UPDATE SKIP LOCKED`. Por cada una, si el horario no empezó, vuelve a llamar a `OfrecerHorario`.
- Marca `VENCIDA` cada inscripción `ACTIVA` con `hasta < hoy`.
- *Alternativa*: vencer al leer. Se descarta porque el horario tiene que pasar al siguiente aunque nadie abra nada.

### D8. Plazo en `configuracion`
- La clave `lista_espera_plazo_horas`, con valor inicial 2 en V9.
- `GET` para `ADMIN` y `RECEPCIONISTA`, y `PUT` solo `ADMIN`, en `/api/v1/configuracion/lista-espera`. Se valida de 1 a 24 en el dominio (`PLAZO_INVALIDO`, `422`).
- El controlador vive en `agenda/listaespera` y lee la tabla igual que `MargenVigenteJpa`, sin tocar `configuracion/` (Emanuel).

### D9. Migración V9
- `inscripcion_lista_espera`: tiene `CHECK (hasta >= desde AND hasta <= desde + 60)` y un índice único parcial `(paciente_id, prestacion_id) WHERE estado = 'ACTIVA'`.
- `oferta_lista_espera`: tiene `token_hash` único y los índices parciales de D3.
- Índice `(estado, inscripta_en)` para recorrer la lista e índice `(estado, vence_en)` para la tarea.
- `INSERT` de la clave del plazo.
- Sin cambios en `notificacion` (D6): la oferta lleva `aviso_enviado` y `aviso_intentos`.
- Los dos `INSERT` usan `ON CONFLICT DO NOTHING RETURNING`. Así una carrera contra un índice único no aborta la transacción de PostgreSQL.

### D10. Frontend
- **Página `#/lista-espera`**: `pages/lista-espera.ts` y `lista-espera/` (api, tabla, diálogo de alta). Va en la barra lateral inmediatamente después de "Agenda", con `roles: ['ADMIN', 'RECEPCIONISTA']`. El orden final, con "Coberturas" de C-49 (Juan Sebastián), es: Agenda, Lista de espera, Pacientes, Coberturas, Reportes, Caja (C-22) y Configuración. "Coberturas" queda donde la define C-49 (después de "Pacientes" y antes de "Reportes"), así que los dos changes no se contradicen. El que se mergee segundo resuelve el conflicto en el orden de páginas de `app.ts`. El diálogo reusa el buscador de pacientes de C-36 y el catálogo público de prestaciones y profesionales habilitados (`paciente/catalogo.ts`).
- **`#/reserva`**: en el estado vacío del día, el botón "Anotarme en la lista de espera" abre un diálogo (`lista-espera/paciente.ts`).
- **`#/mis-turnos`**: la sección "Lista de espera" pide la lista en paralelo con los turnos. Un error en la lista no rompe la página.
- **`#/oferta`**: `pages/oferta.ts`. `entrada.ts` suma `LINK_OFERTA = /\/oferta\/([^/]+)\/?$/`, con el token solo en memoria, igual que la confirmación.
- **Configuración, pestaña "Margen"**: suma el bloque del plazo (`lista-espera/plazo.ts`, que monta `configuracion/margen.ts`).
- **Acceso**: la página usa `roles` y el aviso de administración y recepción, como Reportes (C-32). Por eso un `PACIENTE` que abre `#/lista-espera` recibe el aviso general de "solo para el personal", igual que en Reportes.
- **Estilos**: un módulo nuevo `23-lista-espera.css`, registrado en `test/estilos.ts` y en `estilos-modulos.test.ts`. Solo usa tokens `--ds-*`.
- **Mensajes**: `mensajes.ts` suma los códigos nuevos.
- **Navegación**: "Menú según la sesión" de `frontend-navegacion` no se modifica en este change, porque ya lo modifican C-22 y `frontend-uniformidad-visual`. La página entra con su requisito propio en `frontend-lista-espera`, y antes del archive se reconcilia con un delta MODIFIED sobre el texto vigente (tarea 11.3).

## Risks / Trade-offs

- **[Riesgo] La oferta llega y el horario ya no está.** → Es la consecuencia aceptada de DD-22. El mensaje lo explica y el paciente sigue en la lista.
- **[Riesgo] La cancelación se hace más lenta** porque busca candidatos en la misma transacción. → Se recorre en orden de llegada con un límite de 50 inscripciones activas evaluadas por horario, en la lista filtrada por prestación habilitada, profesional y rango. Es suficiente para un consultorio chico (DD-05).
- **[Riesgo] Un error en la oferta podría tumbar una cancelación.** → D2 la ofrece después del commit, en su propia transacción, la aísla en un `try` y deja en el log solo el id del turno.
- **[Riesgo] El proceso se corta entre el commit de la cancelación y la oferta.** → El horario queda libre sin oferta. Es raro y no pierde datos: el turno sigue cancelado y se puede reservar como cualquier otro.
- **[Limitación] Solo se ofrecen inicios de la grilla del profesional** (D4), la misma de la reserva online.
- **[Riesgo] C-49 suma "Coberturas" a la misma barra lateral.** → El orden queda fijado en D10 y en la spec. Se avisa a Juan Sebastián, y el segundo PR en mergearse resuelve el conflicto en `app.ts` y en los tests de navegación.
- **[Riesgo] Escenarios de navegación desactualizados** ("solo Agenda, Reportes y Configuración"). → Se reconcilia antes del archive (D10), igual que C-49.
- **[Trade-off] Reportes.** Los turnos aceptados desde la lista cuentan como del consultorio hasta que un change ajuste RN-RE-05.
- **[Trade-off] Toca módulos ajenos**: `notificaciones/` (Juan Cruz), el patrón de `configuracion/` (Emanuel), `reserva/` (Juan Sebastián) y `frontend/` (Enzo). Se les avisa, y revisan las partes que les tocan.

## Migration Plan

1. Mergear V9 junto con el código. Flyway la aplica en desarrollo y en los tests de integración.
2. La tarea programada arranca activa. Con `APP_LISTA_ESPERA_ACTIVO=false` se apaga sin afectar el resto.
3. Rollback: desactivar la tarea y no mostrar la página. Las tablas nuevas no afectan a la agenda.

## UI Mockups

- Design system revision: 5.
- Decision: yes (2026-11-24). Modo `system-and-mockups`.
- Mockups: `openspec/changes/lista-de-espera-automatica/mockups/` (ver su `README.md`).
- Approval: approved (2026-11-24, by Sofia Indovino), con el orden del menú junto a "Coberturas" de C-49.
- Preview: rendered (2026-11-24). Capturas en `mockups/previews/`, a 390 × 844 y 1440 × 900, sin desplazamiento horizontal ni errores de consola.
- Visual verify: differences (2026-11-25). Capturas en `mockups/verify/`, con el backend de desarrollo y pacientes ficticios (DNI 991000xx):
  - Coinciden: la lista del personal (390 y 1440), "Anotar paciente", "Dar de baja", el plazo en solo lectura de la pestaña "Margen" y la página de la oferta, pendiente y aceptada. Sin errores de consola ni pedidos fallidos de la app.
  - Corregida (menor): la página del personal no tenía el fondo suave de las demás pantallas del personal. Se agregó `fondoSuave` y un caso en `personal-visual.test.ts`. Con eso también toma el ancho máximo de Configuración, en vez del ancho completo del mockup; queda igual que el resto del personal.
  - Esperada: "Coberturas" no aparece en el menú hasta que se mergee C-49.
  - Sin verificar en la app real: el botón "Anotarme" de `#/reserva`, la sección de `#/mis-turnos` y el plazo editable como administrador. Solo los cubren los tests. Queda como revisión manual.
- Visual verify, pantallas del paciente (2026-11-25, después de C-17 y C-49): con `docker compose` y un paciente ficticio nuevo (DNI 99150015), a 1440 × 900 y 390 × 844. Capturas `reserva-*` y `mis-turnos-lista-*` en `mockups/verify/`.
  - Coinciden: el día sin horarios con "Anotarme en la lista de espera", el diálogo (14 días por defecto), "Listo, te anotamos." y la sección "Lista de espera" de `#/mis-turnos`. A 390 px no hay desplazamiento horizontal y los botones del diálogo se apilan.
  - Coincide: `SIN_CONSENTIMIENTO` deja el diálogo abierto con el mensaje y "Anotarme" habilitado. C-17 le suma el enlace "Aceptar mensajes en Mi cuenta"; después de aceptarlos ahí, la inscripción entra.
  - Menú: el paciente ve "Reservar turno", "Mis turnos" y "Mi cuenta", como el delta de "Menú según la sesión".
  - Consola: solo el `409` esperado de `POST /paciente/lista-espera`, que el navegador registra como recurso fallido.
- Visual verify: manual (2026-11-25) para el plazo editable como administrador y el turno aceptado en la pantalla de la agenda. Hacen falta una sesión del personal; por decisión de la líder quedan como revisión manual y no bloquean el archive. Checklist:
  1. Como `ADMIN`, en Configuración › Margen, cambiar el plazo de la lista de espera a 3 horas, guardar y ver el aviso; probar 0 y 25 y ver el error de rango.
  2. Como `RECEPCIONISTA`, abrir la agenda del día del turno aceptado desde una oferta y verlo `RESERVADO` en su horario.
- Recorrido completo (2026-11-25):
  1. Se canceló online el turno de un paciente ficticio.
  2. Se creó la oferta para el primero de la lista, con vencimiento a 2 h, y el aviso salió por WhatsApp simulado, sin token en el log.
  3. Se aceptó desde `/oferta/{token}`: la oferta quedó `ACEPTADA`, la inscripción `ASIGNADA` y el turno `RESERVADO` en el mismo horario.
  - El turno se verificó en la base, no en la pantalla de la agenda.

## Open Questions

- Ninguna bloqueante. Las decisiones de la líder del 2026-11-24 están en RN-LE-01 a RN-LE-09 y en DD-22.
