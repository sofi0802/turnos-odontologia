# Design

## Context

La motivación está en `proposal.md` y los requisitos en `specs/`. El contrato de la API es el de C-27 (`openspec/changes/consentimientos-firma-electronica/design.md`, D2, D7 y D9), confirmado contra `ConsentimientoDtos.java` del PR #128:

- **Plantillas**: `PlantillaResumen { id, activa, versionActual, titulo, actualizadaEn }`, `PlantillaVista { id, activa, version }` y `VersionVista { numero, titulo, texto, creadaEn, creadaPor }`. Editar es `POST …/{plantillaId}/versiones` con `{ versionBase, titulo, texto }` y responde `201` (versión nueva) o `200` (sin cambios). El estado es `PUT …/{plantillaId}/estado` con `{ activa }`. El odontólogo solo ve las activas; una inactiva le da `404`.
- **Consentimientos**: `ConsentimientoResumen { id, plantillaId, versionNumero, titulo, firmadoEn, profesionalId, profesional, estado }` sin texto ni firmante; `ConsentimientoVista` suma `texto`, `firmante { nombre, dni }`, `evidencia { ip, dispositivo }`, `tipoFirma` (`"ELECTRONICA"`), `hash { algoritmo, formato, valor }` (`formato` es el entero 1) y `revocacion` (`null` o `{ profesionalId, profesional, revocadoEn, motivo }`). `VerificacionVista { consentimientoId, integro, algoritmo, hashGuardado, hashCalculado, verificadoEn }`. Las fechas son `OffsetDateTime` de Buenos Aires.
- **Firma**: `POST /api/v1/pacientes/{id}/consentimientos` con `{ plantillaId, versionNumero, firmanteNombre, firmanteDni, aceptado }`, solo `ODONTOLOGO`. El nombre es obligatorio (3–200, normalizado) y **no se compara** con el del paciente (PA-46); el DNI se normaliza y se compara (C-27, D3).
- **Errores** (`ProblemDetail` con `codigo`): `409` `PLANTILLA_DUPLICADA`, `VERSION_DESACTUALIZADA`, `PLANTILLA_INACTIVA`, `CONSENTIMIENTO_YA_REVOCADO`; `422` `FIRMANTE_NO_COINCIDE` y `DATOS_INVALIDOS`; `404` `RECURSO_NO_ENCONTRADO`; `403` `ACCESO_DENEGADO` (por ejemplo, un odontólogo sin profesional vinculado).

Lo que ya existe para el bloqueo (D12), sin cambiar el backend:
- `POST /api/v1/public/login` (`AutenticacionController`): autentica `{ username, password }`; con éxito **rota el id de la sesión** (`ChangeSessionIdAuthenticationStrategy`), guarda el contexto y **renueva el token CSRF** en la misma respuesta. Con credenciales malas responde `401` con `codigo` `CREDENCIALES_INVALIDAS` (`UsuariosExceptionHandler`) y **no toca la sesión existente**. Es pública: funciona con la sesión vencida o cerrada.
- `POST /api/v1/logout`: invalida la sesión, borra `JSESSIONID` y, por CSRF activo, el token. Responde `204`.
- Un `401` de una ruta protegida trae `codigo` `NO_AUTENTICADO` (`ProblemDetailAuthenticationEntryPoint`) y se rechaza en la cadena de filtros, **antes del controlador**: la firma no llega a guardarse.
- **No hay límite de intentos ni bloqueo de cuentas** en el backend (ni en `seguridad-autenticacion`). La sesión vence a los 30 minutos (`SESSION_TIMEOUT`).
- En el frontend, `shared/sesion/sesion.ts` tiene `iniciarSesion(api, username, password)` (pide `GET /public/sesion` antes si no hay token CSRF) y `cerrarSesion(api)`; `shared/api/cliente.ts` lee la cookie `XSRF-TOKEN` en **cada** pedido, así que el token renovado se usa solo. `ficha/seleccion.ts` pasa el paciente elegido entre pantallas en memoria (`tomarSeleccion`). El ingreso es `#/mi-cuenta` (`#/ingreso` redirige ahí).

En el frontend:
- `pages/configuracion.ts` arma las pestañas con `crearPestanas` a partir de `definiciones` y de `configuracion/permisos.ts`.
- `pages/pacientes.ts` (C-44) monta las secciones de la ficha: odontograma, periodontograma (C-46), imágenes (C-47) y la ficha básica. `editable` es `rol === 'ODONTOLOGO'`; `sesionVencida(causa)` lleva al ingreso con `MENSAJE_SESION_TERMINADA`.
- `shared/api/cliente.ts` ya traduce el `401` a `ErrorSesion`, el `409` a `ErrorNegocio` y el `422` a `ErrorValidacion`, con `codigo`; un fallo de red o un `5xx` es `ErrorGenerico`. `shared/ui/dialogo.ts` (`abrirDialogo`) es el molde de los diálogos.

## Goals / Non-Goals

**Goals:**
- Que la pantalla de firma no muestre datos clínicos ni el DNI, y que el paciente firme exactamente el texto que leyó.
- Que el paciente nunca vuelva a la ficha ni a la sesión del odontólogo: al terminar o cancelar la firma, el equipo queda bloqueado hasta que el odontólogo escriba su contraseña.
- Que una firma con un envío dudoso se compruebe antes de decir que se guardó o no, y que no se firme dos veces sin querer.
- Que el estado de integridad que se muestra salga siempre de una verificación hecha al abrir, nunca de un valor supuesto.
- Que ningún dato de un consentimiento quede en la dirección, el almacenamiento del navegador, el autocompletado ni la consola.

**Non-Goals:**
- Imprimir, descargar o exportar el documento firmado (PDF): queda para C-28 (pregunta 6, resuelta).
- Firma desde el dispositivo del paciente, firma manuscrita en tableta, firma de un representante (PA-47) o asociación a un turno (PA-50).
- Editor con formato (negrita, listas): el texto es plano (C-27, D3).
- Cambiar el backend, el cliente de la API o el resto de la ficha. Un límite de intentos de ingreso en el servidor o una sesión restringida a la firma quedan como propuestas (D12, Open Questions).

## Decisions

### D1. Dos capacidades nuevas
`frontend-plantillas-consentimiento` (pestaña de Configuración) y `frontend-consentimientos` (sección de la ficha). Las dos tienen usuarios, pantallas y ciclos distintos: el `ADMIN` administra textos que no son de un paciente; el odontólogo trabaja con datos de salud (RN-CL-01). Igual que C-47 (D1), las reglas de solo lectura y de privacidad van como requisitos propios y no como delta de `frontend-ficha-clinica`.

### D2. Pestaña en Configuración y coordinación con C-49
- `Permisos` suma `gestionaConsentimientos`, verdadero solo para `ADMIN`. Sin él, la pestaña no se agrega y no se pide nada.
- La pestaña va **última**. Sobre el `master` de hoy queda después de "Usuarios"; si C-49 ya está, después de "Obras sociales".
- **El delta de "Secciones según el rol" se escribe contra el spec principal actual** (`openspec/specs/frontend-configuracion-consultorio/spec.md`, sin "Obras sociales"): suma solo "Consentimientos". No supone que C-49 se archive antes.
- **Qué necesita C-48 de C-49: nada funcional.** Ningún requisito, pedido, módulo ni estilo de C-48 usa algo de C-49 (`frontend-obras-sociales`, Juan Sebastián). Es **coordinación, no dependencia**: los dos tocan los mismos archivos y el mismo requisito.
  - El requisito "Secciones según el rol" (los dos lo reemplazan entero con `MODIFIED`).
  - `pages/configuracion.ts` y `configuracion/permisos.ts` (cada uno suma una pestaña y un permiso: `gestionaCoberturas` y `gestionaConsentimientos`).
  - `pages/pacientes.ts`: C-49 monta "Cobertura" entre la cabecera y el odontograma; C-48 monta "Consentimientos" después de "Imágenes y estudios". No compiten por el mismo lugar, pero los dos cambian el armado de la ficha y su `destruir()`.
  - `styles.css` y `test/estilos-modulos.test.ts` (`REGLAS_POR_MODULO`): en `master` ya están `22-caja.css` (C-22), `23-lista-espera.css` (C-15), `24-coberturas.css` (C-49) y `25-presupuestos.css` (C-50).
- **Regla de coordinación**:
  - **Archive**: el que se archive **segundo** incluye la pestaña del otro en su `MODIFIED` de "Secciones según el rol" (la lista del `ADMIN`, "sin las N últimas" de los otros roles y los escenarios del otro, como "El odontólogo no ve Obras sociales"), porque `MODIFIED` reemplaza el requisito entero y si no borraría la pestaña del primero. Se verifica con `openspec validate --strict` y leyendo el spec principal después del archive.
  - **Apply**: el que se mergee **segundo** rebasa sobre `master` y resuelve `configuracion.ts` (las dos pestañas, "Obras sociales" antes de "Consentimientos"), `permisos.ts` (los dos campos), `pacientes.ts` (el orden de la ficha: cabecera, Cobertura, odontograma, periodontograma, imágenes, Consentimientos) y `styles.css` y `REGLAS_POR_MODULO` (los dos módulos). Los tests de los dos siguen pasando.
  - Lo controla la tarea 1.2. C-48 no suma C-49 a sus "Dependencias".

### D3. Módulos
Todo en `frontend/src/consentimientos/`, que no toca `ficha/` (C-44, C-46, C-47):
- `api.ts`: pedidos de plantillas y consentimientos con lectura defensiva (descarta valores de otro tipo, como `ficha/api.ts`); nunca escribe en la consola. La firma acepta una `AbortSignal` (D13).
- `modelo.ts`, funciones puras sin DOM:
  - validaciones del título (1–120), el texto (1–20.000), el nombre (3–200), el DNI (7 u 8 dígitos tras quitar puntos y espacios) y el motivo (1–200), aplicadas sobre el valor recortado y normalizado a NFC, igual que el backend (C-27, D3);
  - formato del contador con separador de miles de `es-AR` ("1.234/20.000");
  - nombres accesibles ("Ver Consentimiento para extracción dentaria del 24/11/2026") y fechas;
  - `firmaNueva(antes, despues, intento)` para la comprobación (D13) y `firmadoHoy(lista, plantillaId, version, hoy)` para el aviso de doble firma (D14).
- `plantillas.ts`: `montarConsentimientos(panel, contexto)` para la pestaña, con el editor y las versiones.
- `seccion.ts`: `crearConsentimientos(...)` para la ficha. Devuelve `{ contenedor, destruir(): void }`.
- `firma.ts`: la vista de firma a pantalla completa (D5).
- `bloqueo.ts`: la pantalla "Devolvé el equipo al profesional" y el desbloqueo (D12).
- `documento.ts`: el documento firmado con su verificación (D7).
- `pages/pacientes.ts` solo monta la sección después de las imágenes, llama a `destruir()` al cambiar de paciente o salir, y deja que la sección destruya la ficha al bloquear (D12).

### D4. Texto plano
- Todo texto que viene de la API (título, texto de la plantilla o del documento, motivo, nombres, evidencia) se asigna con `textContent`; nunca `innerHTML`. El bloque del texto usa `white-space: pre-wrap` y `overflow-wrap: anywhere`, así se ven los saltos de línea y una palabra larga no rompe el ancho a 390 px.
- **Sin `maxlength`** en el título ni en el texto: un `maxlength` recorta en silencio lo que se pega. El contador lo marca como error y no se envía (escenario "Texto que supera el límite").
- El contador está ligado al campo con `aria-describedby`. No se anuncia en cada tecla: un `role="status"` aparte avisa una sola vez al pasar el 90 % y al pasar el límite.
- El área de texto mide 16 filas en la computadora y 12 en el celular, con `resize: vertical`.

### D5. Vista de firma a pantalla completa
- Es un `<dialog>` abierto con `showModal()`: el navegador deja el resto de la página inerte, fuera del teclado y de los lectores. Ocupa `100vw × 100dvh` con fondo **opaco** (`--ds-fondo`), así no se ve la ficha detrás (el `::backdrop` también es opaco). No usa `abrirDialogo` porque su molde es un diálogo chico con dos botones.
- Contenido, en orden: rótulo "Consentimiento informado · Firma electrónica", título (`h2`, recibe el foco), "Versión N", **"Para: Nombre Apellido"** (D15), el texto en el flujo de la página (sin una caja con su propio desplazamiento, que en el celular es incómoda), y al final el formulario: "Nombre completo", "DNI", la explicación de qué se guarda, la casilla "Leí y acepto este consentimiento", "Cancelar" y "Firmar".
- **Del paciente solo se muestran el nombre y el apellido** (D15). El DNI no aparece en la vista, ni en su DOM, ni en un atributo; el título de la pestaña del navegador sigue siendo el de la página.
- Los campos llevan `autocomplete="off"`, `autocapitalize="words"` (nombre) e `inputmode="numeric"` (DNI), `spellcheck="false"`. Los dos empiezan **vacíos**. El DNI se envía tal como se escribió: el backend lo normaliza (C-27, D3).
- **La casilla no depende del desplazamiento** (pregunta 4, resuelta): no se exige llegar al final del texto para habilitarla. Exigirlo no prueba la lectura y traba a quien navega con teclado o lector. El formulario está después del texto, así que para llegar a la casilla hay que pasar por él.
- La versión que se envía es la que se muestra (`versionNumero` de la `PlantillaVista` cargada). Con `409 VERSION_DESACTUALIZADA` se vuelve a pedir la plantilla, se reemplaza el texto, se desmarca la casilla y se lleva el foco al aviso (C-27, D2).
- Con `409 PLANTILLA_INACTIVA` se ocultan los campos y "Firmar" y queda "Terminar", que bloquea (D12).
- Con `422 FIRMANTE_NO_COINCIDE`, el mensaje es fijo y va en el campo del DNI; el valor escrito queda solo dentro del campo para poder corregirlo.
- **Un solo envío** (D13): mientras se envía, "Firmar" dice "Firmando…" y está deshabilitado, "Cancelar" también, y un segundo `submit` (Enter, doble clic) no hace nada.
- **Cualquier final pasa por el bloqueo** (pregunta 2, resuelta): con `201`, al cancelar, con "Terminar", con un envío fallido o sesión vencida, la vista se cierra, se quita del DOM con lo escrito y se muestra la pantalla de bloqueo (D12). Ningún botón de la vista dice "Volver a la ficha".
- **Cancelar**: el paciente puede cancelar antes de firmar. "Cancelar" y Escape (evento `cancel`, que se intercepta con `preventDefault`) cierran sin preguntar si no hay nada escrito; si hay algo, preguntan con `abrirDialogo` (peligro) "¿Cancelar la firma? Lo escrito se descarta.". Algunos navegadores cierran igual un `<dialog>` con un segundo Escape seguido; por eso el evento `close` que no inició la aplicación se trata como una cancelación sin enviar (o, si había un envío en curso, como un envío dudoso, D13) y también termina en el bloqueo.

### D6. Elegir la plantilla
"Firmar consentimiento" abre un diálogo con `abrirDialogo` con la lista de plantillas activas como botones de opción (título y "Versión N"). "Continuar" pide **en paralelo** `GET …/plantillas/{id}` y el listado del paciente (`GET /pacientes/{id}/consentimientos`), que sirve para el aviso de doble firma (D14) y como foto de "antes" para la comprobación (D13). Con `404` de la plantilla, avisa "Esa plantilla ya no está disponible. Elegí otra." y vuelve a pedir la lista. Sin plantillas, el diálogo lo explica y solo ofrece "Cerrar".

### D7. Documento firmado y verificación
- "Ver" abre un `<dialog>` ancho (hasta 56 rem) y pide **en paralelo** el documento y la verificación. El documento se dibuja cuando llega; el estado de integridad empieza en "Verificando…" y solo pasa a "Íntegro" o "Alterado" con la respuesta de la verificación, que es la única fuente de ese estado.
- La verificación va en una franja al lado del documento: a la derecha desde 64 rem y arriba del texto en el celular.
  - **Íntegro**: etiqueta en `--ds-ok` con ícono y texto, "El contenido coincide con su hash SHA-256." y "Verificado el … a las …".
  - **Alterado**: `role="alert"` en `--ds-error` con "Alterado: el contenido guardado no coincide con su hash. Avisá al administrador y no lo uses como prueba hasta revisarlo.", más el hash guardado y el calculado.
  - **Sin verificar** (la verificación falló): texto neutro con "Verificar de nuevo". Nunca se muestra "Íntegro" por defecto.
- **Qué se muestra y qué se pliega** (pregunta 1, resuelta). El odontólogo y el `ADMIN` ven lo mismo; los dos tienen acceso a toda la evidencia (C-27, D11). Para no exponerla de más en una pantalla que puede ver otra persona:
  - **A la vista**: título, versión, estado ("Vigente" o "Revocado"), "Firma electrónica", texto firmado, nombre y DNI del firmante, fecha y hora, profesional y, si está revocado, quién, cuándo y el motivo. El DNI ya está en la cabecera de la ficha, así que no suma exposición.
  - **Plegado en "Evidencia de la firma"** (`<details>` cerrado): IP, dispositivo (User-Agent completo, con `overflow-wrap: anywhere`), algoritmo, formato y valor del hash. Son datos técnicos que sirven para una auditoría o un perito y no para la consulta diaria.
  - **No se muestran** los ids internos (`profesionalId`, `plantillaId`, `consentimientoId`).
- El diálogo solo tiene "Cerrar"; "Revocar" está en la fila del listado.

### D8. Revocación
"Revocar" abre `abrirDialogo` con `peligro`, el resumen "Título, firmado el …. El documento no se borra ni cambia: queda como revocado, con tu nombre, la fecha y el motivo." y el motivo obligatorio (1–200, con contador). No se vuelve a pedir el DNI (PA-45). Con `409 CONSENTIMIENTO_YA_REVOCADO` se avisa y se vuelve a pedir el listado.

### D9. Mensajes
Nuevos en `MENSAJES_POR_CODIGO`:
- `PLANTILLA_DUPLICADA`: "Ya existe una plantilla con ese título.";
- `VERSION_DESACTUALIZADA`: "La plantilla cambió mientras la usabas.";
- `PLANTILLA_INACTIVA`: "Esta plantilla se desactivó y ya no se puede firmar.";
- `CONSENTIMIENTO_YA_REVOCADO`: "El consentimiento ya estaba revocado.";
- `FIRMANTE_NO_COINCIDE`: "El DNI no coincide con el del paciente. Revisalo.".

El editor y la vista de firma reemplazan `VERSION_DESACTUALIZADA` por su texto específico (specs). Los textos del bloqueo, la comprobación y la doble firma son propios de `firma.ts` y `bloqueo.ts` (specs). Ningún mensaje incluye datos escritos ni el DNI.

### D10. Estilos e íconos
- Un módulo nuevo `frontend/src/styles/26-consentimientos.css`, importado al final de `styles.css` (después de `25-presupuestos.css`) y registrado en `MODULOS` de `test/estilos.ts` y en `REGLAS_POR_MODULO` de `test/estilos-modulos.test.ts`. Usa el **26** porque en `master` ya están `22-caja.css` (C-22), `23-lista-espera.css` (C-15), `24-coberturas.css` (C-49) y `25-presupuestos.css` (C-50). `MODULOS_EN_DISCO` lee los archivos que hay y los ordena, sin exigir números seguidos. Es el `mockups/consentimientos.css` sin las reglas "Solo mockups".
- Solo tokens de la Revision 5, sin `outline: none`. **No hace falta un token nuevo**: la vista de firma y el bloqueo usan `--ds-fondo` y `--ds-superficie`; "Íntegro" y "La firma quedó guardada" usan `--ds-ok`/`--ds-ok-fondo`; "Alterado" y "La firma no se guardó", `--ds-error`/`--ds-error-fondo`; "Comprobando…" y la doble firma, `--ds-alerta` (patrón `aviso-alerta`); "Revocado", el patrón de anulada de C-47 (borde punteado y `--ds-superficie-2`); el hash, `--ds-fuente-mono`.
- `shared/ui/iconos.ts` suma `firma`, `verificado`, `alterado` y `candado`, con el trazo de los existentes y `aria-hidden`; reusa `documento`, `ver` y `reloj`.

### D11. Privacidad
- Nada de un consentimiento se guarda en la dirección (sigue `#/pacientes` y `#/configuracion`), `localStorage` ni `sessionStorage`; los módulos no usan `console`. La única marca en el almacenamiento es la de D12 (`turnos.firmaEnCurso`), que vale `"1"` y no contiene datos.
- La vista de firma, el bloqueo y el documento se quitan del DOM al cerrarse; `destruir()` también los quita al cambiar de paciente o salir.
- `autocomplete="off"` en el nombre y el DNI: en la computadora compartida del consultorio, el navegador no ofrece el nombre de un paciente a otro.
- El estado de un intento de firma (paciente, plantilla, versión, ids de "antes", usuario) vive solo en memoria del módulo y se pierde al recargar.

### D12. "Devolvé el equipo al profesional": bloqueo y desbloqueo
Decisión de Emanuel del 2026-11-24 (pregunta 2). Sin cambios de backend.

- **Al abrir la vista de firma** se guarda `localStorage["turnos.firmaEnCurso"] = "1"`: un booleano, sin datos del paciente ni del consentimiento (no es dato de salud). Se borra solo al desbloquear con éxito.
- **Al bloquear** (firma `201`, cancelar, "Terminar", envío fallido o sesión vencida), en este orden:
  1. se cierra la vista de firma y se **destruye la ficha**: `pacientes.ts` vacía el cuerpo de la página y la sección suelta sus datos; en memoria quedan solo el id del paciente (para reabrir la ficha), el usuario de la sesión y el intento de firma (D13);
  2. se muestra la **pantalla de bloqueo**: un elemento fijo agregado a `document.body`, **fuera de la raíz de la aplicación**, con fondo opaco; la raíz queda con `inert`. No es un `<dialog>`, así que Escape no tiene nada que cerrar;
  3. se pide **`POST /api/v1/logout`**: la sesión del odontólogo se termina en el servidor. Si falla (sin red), se reintenta en cada intento de desbloqueo y al salir de la página (`pagehide`, `fetch` con `keepalive` y el token CSRF).
- **Qué muestra**: "Devolvé el equipo al profesional", una línea según el caso ("Listo: la firma quedó registrada." / "La firma se canceló. No se guardó nada." / "La firma no se completó." / "No pudimos confirmar si la firma se guardó. El profesional lo va a revisar."), "Para seguir, el profesional escribe su contraseña.", el usuario **como texto fijo** ("Usuario: ana.ejemplo", no es un campo), "Contraseña" (`type="password"`, `autocomplete="off"`, sin un campo de usuario asociado) y "Desbloquear". Nada del paciente ni del consentimiento.
- **Desbloquear**: `iniciarSesion(api, usuarioFijo, contraseña)` → `POST /api/v1/public/login` (antes `GET /public/sesion` si el logout borró el token CSRF). El usuario sale de la sesión que había al abrir la ficha, nunca de la pantalla: el paciente no puede cambiar de cuenta.
  - **Éxito**: el servidor rota el id de la sesión y renueva el token CSRF (el cliente lo lee en el pedido siguiente). La aplicación **comprueba que la sesión nueva sea del mismo usuario y con rol `ODONTOLOGO`**; si no, pide el logout y va al ingreso. Si coincide, borra la marca, quita el bloqueo, reabre la ficha del paciente (`ficha/seleccion.ts`) y muestra el resultado: "Consentimiento firmado.", la comprobación (D13) o nada si se canceló. Si la respuesta pide cambiar la contraseña (C-41), sigue el flujo existente de `#/cambiar-contrasena`, sin reabrir la ficha.
  - **Contraseña incorrecta** (`401 CREDENCIALES_INVALIDAS`): sigue bloqueado, el campo se vacía y dice "Contraseña incorrecta. Te quedan N intentos.".
  - **Tres intentos fallidos**: se quita el bloqueo, se borra la marca y se va al ingreso (`#/mi-cuenta`) con "Por seguridad, cerramos la sesión. Ingresá de nuevo.". La sesión ya estaba cerrada desde el paso 3: no queda nada de la ficha en la página ni en el servidor.
  - **Sin conexión o `5xx`**: "No pudimos comprobar la contraseña. Probá de nuevo."; no cuenta como intento.
  - **Otro `401`, `403` u otra respuesta inesperada**: igual que tres intentos fallidos (ingreso, sin nada de la ficha).
- **Escape** no hace nada en el bloqueo. **Atrás, adelante o escribir otra ruta**: el router dibuja debajo la página de la nueva ruta; como la sesión está cerrada, cualquier ruta del personal lleva al ingreso. El bloqueo sigue encima, porque no vive en la raíz que el router reemplaza.
- **Recargar o abrir otra pestaña**: la memoria se pierde y el bloqueo desaparece, pero:
  - si el logout ya se hizo, no hay sesión: se ve el ingreso, sin nada de la ficha;
  - si no se hizo (o la recarga fue durante la firma, con la sesión todavía abierta), **al iniciar la aplicación la marca `turnos.firmaEnCurso` hace pedir el logout antes de dibujar cualquier página**, borra la marca y muestra el ingreso con "Por seguridad, la sesión se cerró. Si estabas haciendo firmar un consentimiento, revisá la ficha del paciente para ver si quedó guardado.". Esto es un cambio chico en `app.ts`, sin datos de salud en el almacenamiento: la marca no dice de qué paciente ni de qué consentimiento se trata.
- **Por qué cerrar la sesión y no solo tapar la pantalla**: con la sesión abierta, una recarga o una pestaña nueva mostrarían `#/pacientes` con el buscador de todos los pacientes, y las herramientas del navegador permitirían quitar el bloqueo. Con el logout, el bloqueo es real en el servidor. El costo es que las otras pestañas del odontólogo también pierden la sesión (la cookie es una sola).
- **Lo que no cubre sin backend**: mientras el paciente tiene la vista de firma, la sesión del odontólogo sigue abierta (la firma la necesita). Alguien que sepa usar las herramientas del navegador podría usarla en ese rato. La marca cubre recargar y abrir otra pestaña, pero no las herramientas de desarrollo. **La opción mínima de backend** (fuera de C-48, pregunta 12): un "modo firma" en la sesión del servidor, que mientras está activo solo deja pedir la plantilla y firmar para ese paciente y se apaga con un `POST /public/login` del mismo usuario. Tampoco hay límite de intentos en el servidor: los tres intentos son de la pantalla (pregunta 11).

### D13. Envío dudoso: comprobar si la firma se guardó
Decisión de Emanuel del 2026-11-24 (pregunta 3).

- **Un solo pedido**: `firma.ts` guarda el pedido en curso y no envía otro hasta que termine; el pedido lleva una `AbortSignal` con **30 segundos** de espera.
- **Antes de enviar** se guarda el intento en memoria: paciente, `plantillaId`, `versionNumero` enviado y los ids de consentimientos del listado pedido en "Continuar" (D6), la foto de "antes".
- **Según la respuesta**:
  - `201`: guardada; bloqueo con "Listo: la firma quedó registrada." y, después de desbloquear, "Consentimiento firmado." con el título, la versión y la hora.
  - `422` o `409` con código: no se guardó y se sabe por qué; la vista sigue abierta como hasta ahora (D5).
  - `401` (`NO_AUTENTICADO`): se rechaza antes del controlador, así que **no se guardó**. Bloqueo con "La firma no se completó."; después de desbloquear (el login crea una sesión nueva), "La firma no se guardó porque la sesión había vencido. Si hace falta, hacela firmar de nuevo.", sin comprobar.
  - `403` por CSRF: también se rechaza en la cadena de filtros; se trata igual que el `401`.
  - **Fallo de red, tiempo agotado, `5xx`, respuesta ilegible o la vista cerrada por el navegador durante el envío**: **dudoso**. Bloqueo con "No pudimos confirmar si la firma se guardó. El profesional lo va a revisar.".
- **Comprobación** (solo para el dudoso, después de desbloquear): la sección muestra "Comprobando si la firma quedó guardada…" y pide el listado del paciente. Se busca un consentimiento con la misma `plantillaId` y `versionNumero`, del profesional de la sesión, cuyo id **no estaba en la foto de "antes"** (no se comparan relojes del navegador y del servidor).
  - Lo hay: "La firma quedó guardada." con el título, la versión, la hora y "Ver".
  - No lo hay: "La firma no se guardó." con "Hacer firmar de nuevo" (que pasa por el aviso de doble firma, D14).
  - Falla el listado: "No pudimos comprobar si la firma quedó guardada." con "Comprobar de nuevo". Nunca se dice "no se guardó" sin una respuesta del listado.
- Si la página se recarga antes de comprobar, el intento se pierde (está solo en memoria) y el aviso del ingreso pide revisar la ficha (D12).

### D14. Aviso antes de firmar dos veces
- En "Continuar" (D6), con el listado recién pedido: si el paciente tiene un consentimiento **vigente** de la misma plantilla y la **misma versión** que se va a firmar, **firmado hoy** (fecha de Buenos Aires), antes de abrir la vista se pregunta al odontólogo con `abrirDialogo`: "Ya firmó este consentimiento hoy" / "“Consentimiento para extracción dentaria”, versión 3, quedó firmado hoy a las 10:30 y está vigente. ¿Hacerlo firmar otra vez?", con "No firmar" (el foco empieza ahí) y "Firmar otra vez".
- "Hoy" es la definición de "la misma visita": C-27 no asocia la firma a un turno (PA-50).
- Una firma de otra versión, revocada o de otro día no avisa.
- El aviso lo ve el odontólogo, antes de darle el equipo al paciente.

### D15. Identificación del firmante
Decisión de Emanuel del 2026-11-24 (pregunta 5).
- La vista **muestra el nombre y el apellido del paciente** de la ficha ("Para: Paciente Uno"), para que confirme que el documento es suyo. Debajo: "Si no sos vos, avisale al profesional.".
- **"DNI" empieza vacío y el paciente lo escribe.** El DNI de la ficha nunca se muestra, ni se carga en la vista, ni va en un atributo: si estuviera, la comparación del backend no probaría nada. `FIRMANTE_NO_COINCIDE` sigue con su mensaje fijo, sin repetir el DNI escrito.
- **"Nombre completo" sigue y empieza vacío**: el paciente lo escribe.
  - C-27 lo exige (`firmanteNombre`, 3–200) y lo guarda dentro del contenido canónico del hash: es parte de la firma.
  - DD-20 (KB 09) dice que el paciente "escribe su nombre y su DNI y acepta": escribir el nombre es el acto de firmar (art. 5 de la Ley 25.506: datos electrónicos usados como medio de identificación). Precargarlo con el de la ficha lo convertiría en un dato del sistema y no del firmante.
  - El backend no compara el nombre (PA-46), así que una variación ("Paciente A. Uno") no bloquea la firma.
  - Alternativa descartada: precargado y editable. Ahorra escribir, pero el paciente podría firmar sin escribir nada propio salvo el DNI.

## Risks / Trade-offs

- **[Validez legal] La pantalla no prueba quién está frente a ella.** → La responsabilidad queda en la sesión del odontólogo (C-27, D1); la vista no muestra el DNI del paciente para que la comparación tenga sentido. Este diseño no es asesoramiento legal (DD-20).
- **[Validez legal] Un texto mal mostrado invalidaría lo firmado.** → Se muestra la versión exacta que se envía, como texto plano y sin recortar; un test compara el texto mostrado con el de la `PlantillaVista` byte a byte y otro, que una edición concurrente obliga a releer.
- **[Privacidad] El paciente con el equipo durante la firma.** → Ahora cualquier final pasa por el bloqueo con logout (D12). Queda el rato de la firma, con la sesión abierta: cubierto ante recargas y pestañas nuevas por la marca, no ante las herramientas del navegador. **Riesgo aceptado** por Emanuel el 2026-11-25 (pregunta 12).
- **[Privacidad] El navegador puede autocompletar la contraseña guardada del odontólogo en el bloqueo.** → El campo no tiene un usuario asociado y lleva `autocomplete="off"`, pero algunos navegadores lo ignoran en las contraseñas. No se avisa al consultorio: si guarda la contraseña, asume el riesgo (decisión de Emanuel del 2026-11-25, pregunta 13).
- **[Seguridad] Sin límite de intentos en el servidor.** → Los tres intentos son de la pantalla; después, la sesión ya está cerrada y el ingreso es el de siempre. Es un riesgo previo de `seguridad-autenticacion`, no de C-48; en esta primera versión no se limita (decisión de Emanuel del 2026-11-25, pregunta 11).
- **[Uso] El logout cierra también las otras pestañas del odontólogo.** → Se acepta: es el costo de que el bloqueo sea real.
- **[Riesgo] Doble firma por un envío dudoso.** → Un solo pedido, comprobación antes de decir "no se guardó" (D13) y aviso de doble firma (D14).
- **[Riesgo] Un "Íntegro" mostrado sin verificar.** → El estado sale solo de la respuesta de la verificación; hay tests de la falla y de la respuesta lenta.
- **[Riesgo] Textos de 20.000 caracteres.** → Se dibujan con `textContent` en un solo nodo; el listado no los trae (C-27).
- **[Coordinación] C-49** en `configuracion.ts`, `permisos.ts`, `pacientes.ts`, `styles.css`, `test/estilos-modulos.test.ts` y el requisito "Secciones según el rol". → Cambios aditivos y la regla del segundo (D2).
- **[Trade-off] La evidencia plegada** suma un clic para auditar. → Se gana que la IP y el dispositivo no queden a la vista en la consulta.

## Migration Plan

Sin migraciones ni cambios de backend. El apply necesita C-27 (PR #128) mergeado en `master`. Rollback: revertir el PR deja sin pantallas de consentimientos; lo firmado sigue en el backend, inmutable.

## UI Mockups

- Design system revision: 5 (sin tokens nuevos; ver D10).
- Decision: yes (2026-11-24). Modo `system-and-mockups`.
- Mockups: `openspec/changes/frontend-consentimientos/mockups/` (ver su `README.md`). Revisados el 2026-11-24 con las respuestas de Emanuel: vista de firma con el nombre del paciente y el DNI vacío, bloqueo y desbloqueo, comprobación del envío y aviso de doble firma.
- Approval: approved (2026-11-25, by user: Emanuel). La líder los revisa en el PR; son el criterio de aceptación visual.
- Preview: rendered (2026-11-24). 14 pantallas, 28 capturas de página completa (9 vueltas a capturar el 2026-11-24 tras la revisión: 3 nuevas y 6 cambiadas) en `mockups/previews/`, a 390 × 844 y 1440 × 900, con Playwright sobre un servidor estático local: sin desplazamiento horizontal a 390 px, sin errores de consola y sin controles de menos de 44 px.
- Visual verify: pendiente (después del apply).

## Open Questions

Respondidas por Emanuel el 2026-11-24:

1. **Evidencia a la vista**: resuelta, como se propuso. El odontólogo y el `ADMIN` ven lo mismo, con la IP, el dispositivo y el hash plegados (D7).
2. **Cancelar y volver**: resuelta, **con cambios**. El paciente puede cancelar; al cancelar o terminar, pantalla "Devolvé el equipo al profesional" con desbloqueo por contraseña (D5, D12).
3. **Sesión vencida o envío fallido**: resuelta, **con cambios**. Comprobar si se guardó antes de decirlo, un solo envío y aviso de doble firma (D13, D14). Sin temporizador propio de inactividad.
4. **Texto largo**: resuelta, como se propuso. Sin exigir el desplazamiento (D5).
5. **Datos del paciente en la vista de firma**: resuelta, **con cambios**. Se muestra el nombre y el apellido; el DNI se escribe y nunca se muestra; "Nombre completo" sigue, vacío (D15).
6. **Imprimir o descargar**: resuelta, como se propuso. No en C-48 (queda para C-28).
7. **Conflicto de versión al editar**: resuelta, como se propuso. Se conserva lo escrito y se pide copiar, cerrar y reabrir.
8. **Activar y desactivar sin confirmación**: resuelta, como se propuso.
9. **Orden con C-49**: resuelta, **con cambios**. El delta va contra el spec principal actual; el que se archive segundo incluye la pestaña del otro y el que se aplique segundo rebasa (D2). Es coordinación, no dependencia.
10. **Diez pestañas en Configuración**: resuelta, como se propuso. Se acepta la tira con desplazamiento por ahora.

Respondidas por Emanuel el 2026-11-25:

11. **Intentos del desbloqueo**: resuelta. En esta primera versión **no se limitan los intentos de ingreso en el servidor**; quedan los tres intentos de la pantalla y no se abre un change de `seguridad-autenticacion` por ahora.
12. **Sesión abierta durante la firma**: resuelta. **Se acepta el riesgo** de las herramientas del navegador mientras el paciente firma y queda anotado en Riesgos; no se propone el "modo firma" en el backend.
13. **Contraseña guardada en el navegador**: resuelta. **No se avisa** en la pantalla ni en el manual: si el consultorio guarda la contraseña en el navegador, asume ese riesgo.

## Notas del apply

Desvíos y decisiones del apply (2026-11-25), sin cambiar el comportamiento aprobado en los specs:

1. **`shared/api/cliente.ts` y `app.ts`**: `ClienteApi.solicitar` suma un cuarto parámetro opcional `senal?: AbortSignal` (y el envoltorio de `app.ts` lo pasa). Es aditivo: ningún pedido existente cambia. Hace falta para que la firma se corte a los 30 segundos (D13); el no-objetivo "cambiar el cliente de la API" se refería a su comportamiento, no a este parámetro.
2. **Diálogos con el molde**: `test/dialogo.test.ts` exige que ningún archivo fuera de `shared/ui/dialogo.ts` cree un `<dialog>`. Por eso la vista de firma (D5) sale de `abrirDialogo` y se reacomoda (clase `firma-pantalla` en lugar de `dialogo`, `Cancelar` y `Firmar` como sus dos botones, el aviso del molde como aviso de la vista), y los diálogos de versiones y del documento son `abrirDialogo` sin el botón principal. D5 decía que la vista no usaba `abrirDialogo`; el resultado visual y de accesibilidad es el mismo.
3. **Capa del bloqueo**: `mockups/consentimientos.css` usaba `z-index: var(--ds-capa-modal)`, pero ese token es un color (el velo de los diálogos, `rgb(29 42 51 / 45%)`) y no sirve como `z-index`. `26-consentimientos.css` usa una capa numérica (`1000`) y un test lo cuida. Conviene corregir el mockup.
4. **`pagehide` sin `keepalive`**: el cliente de la API no ofrece `keepalive`, así que, con el logout pendiente, al salir de la página se vuelve a pedir `POST /logout` con el cliente (mejor esfuerzo). Cubre menos que el `fetch` con `keepalive` de D12; para cubrirlo habría que sumarlo al cliente.
5. **Edición de plantillas, `201` y `200`**: el cliente no entrega el estado HTTP, así que "sin cambios" se decide porque la respuesta trae el mismo número de versión que `versionBase`. Tras `409 VERSION_DESACTUALIZADA`, "Guardar" queda deshabilitado (se conserva lo escrito y "Cancelar" sigue disponible).
6. **Clasificación del envío de la firma** (D13): `401` y `403` son "no se guardó"; red, tiempo agotado, `5xx`, respuesta ilegible o un error que no es de la API son dudosos; un `4xx` que no es de los listados (`404`, `400`, `413`) se avisa dentro de la vista, sin bloquear, porque el servidor lo rechazó antes de guardar. El `403` de `CAMBIO_DE_CONTRASENA_PENDIENTE` sigue llevando al cambio de contraseña por el envoltorio de `app.ts`.
7. **Reabrir la ficha**: al desbloquear, `pacientes.ts` deja la selección (`ficha/seleccion.ts`) y lo que dejó la firma (`consentimientos/pendiente.ts`, en memoria y de un solo uso) y navega a `#/pacientes`; así también funciona si el profesional usó "atrás" durante el bloqueo. Con la contraseña temporal pendiente (C-41) va a `#/cambiar-contrasena` sin reabrir la ficha.
8. **Aviso de "firmada"**: "Consentimiento firmado: Título, versión N, a las HH:MM." (el spec pide el título, la versión y la hora).
9. **"Hacer firmar de nuevo"** repite el camino de "Continuar" con la misma plantilla (pide la plantilla y el listado y pasa por el aviso de doble firma).
10. **Arranque con la marca y el logout caído**: se borra la marca igual y se sigue; con la sesión todavía abierta, Mi cuenta lleva a la agenda, nunca a la ficha.
11. **Salir de la página**: como en C-47 y C-49, la sección se destruye al cambiar de paciente o al volver a dibujar la ficha; `vigente()` frena los pedidos viejos. Con "atrás" mientras hay un diálogo del documento abierto, ese diálogo no se cierra solo (hallazgo menor, no cubierto por los specs).
12. **Tests de otros changes ajustados, solo para sumar lo nuevo**: `configuracion-pestanas.test.ts` (pestaña "Consentimientos" y permiso `gestionaConsentimientos`), `imagenes.test.ts` (la sección de consentimientos va entre las imágenes y `ficha-columnas`), `ficha-pacientes.test.ts` (respuesta por defecto del listado de consentimientos) y `estilos-modulos.test.ts` (el test de C-49 "Se importa al final de styles.css" pasó a "Se importa después del módulo de la lista de espera", como ya hizo C-22 con el de imágenes, porque ahora el último módulo es el 25).
13. **Merge con C-50 (frontend-presupuestos)**: C-50 se mergeó antes y tomó `25-presupuestos.css`, así que el módulo de C-48 pasó a `26-consentimientos.css`. En la ficha, "Presupuestos" va debajo de "Cobertura" y "Consentimientos" sigue después de "Imágenes y estudios". El test de C-50 "Se importa al final de styles.css" pasó a "Se importa justo después del módulo de coberturas", y el de C-49 ahora comprueba solo que sigue a la lista de espera (el mismo patrón de C-22 y C-15). Los códigos de error de los dos changes conviven en `MENSAJES_POR_CODIGO`.
14. **Merge con C-23 (señas) y C-17 (consentimiento de comunicaciones), 2026-11-25**: los dos se mergearon antes y tomaron `26-senas.css` y `27-consentimiento.css`, así que el módulo de C-48 pasó a `28-consentimientos.css` y sigue siendo el último de `styles.css`. En Configuración, el orden del `ADMIN` queda "Usuarios", "Obras sociales", "Señas" y "Consentimientos": C-48 va última, como pedía D2. Conviven los permisos `configuraSenas` y `gestionaConsentimientos` y los íconos de C-17 (`mensaje`, `mensaje-no`, `telefono`, `persona`) con los de C-48. Tests ajustados solo para sumar lo nuevo: `configuracion-pestanas.test.ts` (la última pestaña del administrador es "Consentimientos", después de "Señas"), `consentimientos-plantillas.test.ts` ("va después de Señas") y `estilos-modulos.test.ts` (el de C-50 ya no exige que 27 sea el último). Como C-23 ya está archivado, el delta de `frontend-configuracion-consultorio` se reescribió contra la spec principal con "Señas" (tarea 1.2); C-49 todavía no está archivado, así que "Obras sociales" queda fuera del delta según D2. Resultado tras el merge: 1693 tests, `tsc` y `build` en verde.
