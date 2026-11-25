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
- Sin tocar las garantías contra solapamientos ni los estados del turno (regla dura 5, DD-21).

**Non-Goals:**
- Reservar el horario mientras se ofrece (DD-21).
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

### D2. El disparo es una llamada directa en `ServicioAgenda`, dentro de la misma transacción
Después de `cancelarTurno.ejecutar` y de `reprogramarTurno.ejecutar`, `ServicioAgenda` llama a un puerto `HorarioLiberado` con el turno cancelado. Ese puerto lo implementa `OfrecerHorario`, y en los tests de agenda se usa uno que no hace nada. Si la oferta falla por un error inesperado, la excepción se registra sin datos y no cancela la cancelación: el puerto se llama en un `try` y el error queda en el log con el id del turno. *Alternativa*: un evento de Spring `@TransactionalEventListener(AFTER_COMMIT)`. Se descarta porque la oferta podría perderse si el proceso se corta entre el commit y el listener; con la llamada directa, la oferta y la cancelación se guardan juntas.

### D3. Compatibilidad: se reusa el validador de la reserva online
Para cada inscripción candidata, por orden de llegada, se arma una solicitud igual a la de la reserva online: la prestación de la inscripción, el profesional del turno liberado y el inicio del turno liberado. Se le pide a `AsignadorDeReserva.preparar` y a la verificación de disponibilidad si existe un sillón libre que cumpla todas las reglas. La primera que da sí recibe la oferta. Las inscripciones se recorren con `SELECT … FOR UPDATE SKIP LOCKED`, para que dos cancelaciones simultáneas no ofrezcan dos veces a la misma persona. Los índices únicos parciales son la última defensa:
- una oferta `PENDIENTE` por `turno_liberado_id`;
- una oferta `PENDIENTE` por inscripción.

Si un índice salta, se pasa a la siguiente. *Alternativa*: duplicar las reglas en el módulo. Se descarta: dos implementaciones de las reglas de la agenda terminarían por divergir.

### D4. Aceptar crea el turno por el mismo camino que la reserva online
`ResponderOferta.aceptar` bloquea la oferta (`FOR UPDATE`) y verifica que esté `PENDIENTE` y no vencida. Después crea el turno con `AsignadorDeReserva.asignar` y `agenda.crear`, con el actor `lista-espera:<ofertaId>`: el paciente puede no tener cuenta. Las restricciones `EXCLUDE` siguen siendo la garantía final. Un `ReservaRechazadaException` o una violación de exclusión se traducen en `OFERTA_NO_DISPONIBLE`. En ese caso la oferta queda `NO_DISPONIBLE` y no se pasa a nadie: si el horario se ocupó, ya no está libre. El aviso de turno reservado se reusa del de la reserva online (`AvisoReservaOnline.reservado`).

### D5. Tokens y endpoints públicos como en C-08
- **Token**: `GeneradorTokenSeguro` y `HuellaToken`, de `notificaciones/`. El hash se guarda en `oferta_lista_espera.token_hash`.
- **Ver la oferta**: `GET /api/v1/public/ofertas/{token}` devuelve la fecha, la hora, el profesional, el vencimiento y el estado. No consume el token, porque los revisores de links abren las direcciones solos.
- **Responder**: `POST …/aceptar` y `POST …/rechazar`, con CSRF.
- **Token mal formado**: responde `404 OFERTA_INEXISTENTE` sin ir a la base.

### D6. Avisos con un tipo de notificación nuevo
- `TipoNotificacion.OFERTA_LISTA_ESPERA` y su `MensajeOferta`: la fecha, la hora, el consultorio, el vencimiento y el link `{base}/oferta/{token}`. Sin la prestación ni nombres.
- El envío usa `AvisoDeTurno` con el turno liberado como referencia. La columna `notificacion.turno_id` apunta al turno cancelado, y la notificación **no** guarda el token: el link se arma al despachar, con el token que devuelve `OfrecerHorario` en memoria.
- La migración agrega el valor nuevo al `CHECK` del tipo.
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
- Ampliación del `CHECK` de `notificacion.tipo`.

### D10. Frontend
- **Página `#/lista-espera`**: `pages/lista-espera.ts` y `lista-espera/` (api, tabla, diálogo de alta). Va en la barra lateral inmediatamente después de "Agenda", con `roles: ['ADMIN', 'RECEPCIONISTA']`. El orden final, con "Coberturas" de C-49 (Juan Sebastián), es: Agenda, Lista de espera, Pacientes, Coberturas, Reportes y Configuración. "Coberturas" queda donde la define C-49 (después de "Pacientes" y antes de "Reportes"), así que los dos changes no se contradicen. El que se mergee segundo resuelve el conflicto en el orden de páginas de `app.ts`. El diálogo reusa el buscador de pacientes de C-36 y el selector de profesionales habilitados de la agenda.
- **`#/reserva`**: en el estado vacío del día, el botón "Anotarme en la lista de espera" abre un diálogo (`paciente/lista-espera.ts`).
- **`#/mis-turnos`**: la sección "Lista de espera" pide la lista en paralelo con los turnos. Un error en la lista no rompe la página.
- **`#/oferta`**: `pages/oferta.ts`. `entrada.ts` suma `LINK_OFERTA = /\/oferta\/([^/]+)\/?$/`, con el token solo en memoria, igual que la confirmación.
- **Configuración, pestaña "Margen"**: suma el bloque del plazo (`configuracion/plazo-lista-espera.ts`).
- **Estilos**: un módulo nuevo `22-lista-espera.css`, registrado en `test/estilos.ts` y en `estilos-modulos.test.ts`. Solo usa tokens `--ds-*`.
- **Mensajes**: `mensajes.ts` suma los códigos nuevos.
- **Navegación**: "Menú según la sesión" de `frontend-navegacion` no se modifica en este change, porque ya lo modifican C-22 y `frontend-uniformidad-visual`. La página entra con su requisito propio en `frontend-lista-espera`, y antes del archive se reconcilia con un delta MODIFIED sobre el texto vigente (tarea 11.3).

## Risks / Trade-offs

- **[Riesgo] La oferta llega y el horario ya no está.** → Es la consecuencia aceptada de DD-21. El mensaje lo explica y el paciente sigue en la lista.
- **[Riesgo] La cancelación se hace más lenta** porque busca candidatos en la misma transacción. → Se recorre en orden de llegada con un límite de 50 inscripciones activas evaluadas por horario, en la lista filtrada por prestación habilitada, profesional y rango. Es suficiente para un consultorio chico (DD-05).
- **[Riesgo] Un error en la oferta podría tumbar una cancelación.** → D2 lo aísla en un `try` y lo deja en el log sin datos personales.
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
- Visual verify: pendiente.

## Open Questions

- Ninguna bloqueante. Las decisiones de la líder del 2026-11-24 están en RN-LE-01 a RN-LE-09 y en DD-21.
