# Design

## Context

El módulo `backend/.../clinica/` sigue puertos y adaptadores (C-13, C-25 y C-26; el dueño es Emanuel):

- **Dominio puro** con `ClinicaException` y `CodigoClinica`.
- **Servicios de aplicación** sobre puertos (`RegistroClinico`, `RegistroPeriodontal`, `RegistroImagenes`) con adaptadores JPA y JDBC (`JdbcClient`).
- **Controllers** bajo `/api/v1/pacientes/{id}` con `@PreAuthorize`: lectura `hasAnyRole('ADMIN', 'ODONTOLOGO')` y escritura `hasRole('ODONTOLOGO')`. El principal `UsuarioAutenticado` trae `usuarioId` y `profesionalId`, así que ningún endpoint acepta el id del autor desde el pedido.
- **Errores**: `ClinicaExceptionHandler` (limitado al paquete `clinica`) responde `422` por defecto, `404` para `RECURSO_NO_ENCONTRADO` y `409` o `413` para los códigos mapeados a mano. El `422` de Bean Validation lleva solo el campo.
- **Solo agregado**: V8, V16 y V17 lo garantizan con triggers `BEFORE UPDATE OR DELETE`. Los tests limpian con `TRUNCATE`, que no dispara triggers de fila.
- **Seguridad global** (`shared/security/SecurityConfig`): `/api/v1/**` exige un rol del personal y lo demás es `denyAll`. No hay `server.forward-headers-strategy` configurado, así que `getRemoteAddr()` es la IP de la conexión TCP que le llega a Tomcat.
- **Base**: Flyway y la aplicación usan el mismo usuario `DB_USER`, que es dueño de las tablas.

La motivación está en `proposal.md` y los requisitos en `specs/consentimientos-informados/spec.md`.

## Goals / Non-Goals

**Goals:**
- Que un consentimiento firmado se pueda probar después: qué texto exacto se firmó, quién dijo ser el firmante, cuándo, desde dónde y con qué sesión del personal, con un hash que detecta cualquier cambio.
- Que el documento firmado sea inmutable en el dominio **y** en la base, igual que la doble garantía del proyecto (DD-03).
- Dejar el contrato de la API cerrado para C-48, que no va a cambiar el backend.

**Non-Goals:**
- **Firma digital** (Ley 25.506, art. 2) con certificado de un certificador licenciado, token o firma remota. Tampoco firma manuscrita digitalizada (trazo en tableta).
- Firma desde el dispositivo del paciente o desde "Mi cuenta", y enviarle una copia por email o WhatsApp.
- Sellado de tiempo de un tercero o sello del servidor con una clave fuera de la base (HMAC). Quedan como extensión (Riesgos).
- Generar un PDF del documento firmado. C-48 lo muestra en pantalla; la exportación de la historia clínica es C-28.
- Firma de un representante legal (menores o personas con capacidad restringida). Queda en las preguntas abiertas (PA-47).
- Asociar el consentimiento a un turno o a una prestación (PA-50).
- Las pantallas, que son C-48.

## Decisions

### D1. Firma electrónica en el consultorio, no firma digital
La firma es una **firma electrónica** en el sentido del art. 5 de la Ley 25.506: datos electrónicos (el nombre y el DNI escritos y la aceptación) asociados al documento y usados como medio de identificación, pero sin certificado digital. Por eso:
- la respuesta lleva `tipoFirma: "ELECTRONICA"` y ningún texto de la API, la KB ni C-48 la llama "firma digital";
- el valor probatorio sale de la evidencia que se guarda (D4) y no de una presunción legal (ver Riesgos);
- la identidad la respalda la sesión del odontólogo que está con el paciente en el consultorio: es quien inicia la firma y queda registrado.

**Alternativa descartada: firma digital con certificado.** Exige que cada paciente tenga un certificado de un certificador licenciado, algo que un consultorio chico no puede pedir. Emanuel la descartó el 2026-11-24.

### D2. Recursos y endpoints (contrato para C-48)
Dos controllers en `clinica/api`: `PlantillaConsentimientoController` y `ConsentimientoController`.

**Plantillas** (no son datos del paciente: el `ADMIN` las edita).

| Método y ruta | Rol | Respuesta |
|---|---|---|
| `POST /api/v1/consentimientos/plantillas` (JSON `{ titulo, texto }`) | `ADMIN` | `201` `PlantillaVista` (versión 1) |
| `GET /api/v1/consentimientos/plantillas?incluirInactivas=false` | `ADMIN`, `ODONTOLOGO` | `200` lista de `PlantillaResumen`. Para el odontólogo, `incluirInactivas` se ignora y solo ve las activas |
| `GET /api/v1/consentimientos/plantillas/{plantillaId}` | `ADMIN`, `ODONTOLOGO` | `200` `PlantillaVista` con la última versión y su texto. Una inactiva da `404` al odontólogo |
| `POST /api/v1/consentimientos/plantillas/{plantillaId}/versiones` (JSON `{ versionBase, titulo, texto }`) | `ADMIN` | `201` `PlantillaVista` con la versión nueva, o `200` con la misma si no hubo cambios |
| `GET /api/v1/consentimientos/plantillas/{plantillaId}/versiones` | `ADMIN` | `200` lista de `VersionVista`, de la más nueva a la más vieja |
| `GET /api/v1/consentimientos/plantillas/{plantillaId}/versiones/{numero}` | `ADMIN`, `ODONTOLOGO` | `200` `VersionVista` |
| `PUT /api/v1/consentimientos/plantillas/{plantillaId}/estado` (JSON `{ activa }`) | `ADMIN` | `200` `PlantillaVista` |

- `PlantillaResumen`: `id`, `activa`, `versionActual` (número), `titulo`, `actualizadaEn`.
- `PlantillaVista`: `id`, `activa` y `version` (`VersionVista`).
- `VersionVista`: `numero`, `titulo`, `texto`, `creadaEn`, `creadaPor` (username del `ADMIN`).

**Consentimientos del paciente** (datos de salud: RN-CL-01).

| Método y ruta | Rol | Respuesta |
|---|---|---|
| `POST /api/v1/pacientes/{id}/consentimientos` (JSON `{ plantillaId, versionNumero, firmanteNombre, firmanteDni, aceptado }`) | `ODONTOLOGO` | `201` `ConsentimientoVista` |
| `GET /api/v1/pacientes/{id}/consentimientos` | `ADMIN`, `ODONTOLOGO` | `200` lista de `ConsentimientoResumen` |
| `GET /api/v1/pacientes/{id}/consentimientos/{consentimientoId}` | `ADMIN`, `ODONTOLOGO` | `200` `ConsentimientoVista` |
| `GET /api/v1/pacientes/{id}/consentimientos/{consentimientoId}/verificacion` | `ADMIN`, `ODONTOLOGO` | `200` `VerificacionVista` |
| `POST /api/v1/pacientes/{id}/consentimientos/{consentimientoId}/revocacion` (JSON `{ motivo }`) | `ODONTOLOGO` | `200` `ConsentimientoVista` |

- `ConsentimientoResumen`: `id`, `plantillaId`, `versionNumero`, `titulo`, `firmadoEn`, `profesionalId`, `profesional`, `estado` (`VIGENTE` o `REVOCADO`). Sin el texto ni el firmante.
- `ConsentimientoVista`: lo del resumen más `texto`, `firmante` (`nombre`, `dni`), `evidencia` (`ip`, `dispositivo`), `tipoFirma` (`ELECTRONICA`), `hash` (`algoritmo`, `formato`, `valor`) y `revocacion` (`null` o `profesionalId`, `profesional`, `revocadoEn`, `motivo`).
- `VerificacionVista`: `consentimientoId`, `integro`, `algoritmo`, `hashGuardado`, `hashCalculado`, `verificadoEn`.
- Las fechas van como `OffsetDateTime` en `America/Argentina/Buenos_Aires`, igual que `ImagenVista`.
- No hay `PUT`, `PATCH` ni `DELETE` sobre consentimientos ni sobre versiones; Spring responde `405`.

**`versionNumero` obligatorio al firmar.** El paciente firma exactamente el texto que se le mostró. Si el `ADMIN` editó la plantilla entre que C-48 la cargó y la firma, la respuesta es `409 VERSION_DESACTUALIZADA` y C-48 vuelve a mostrar el texto nuevo.

**Alternativa descartada: firmar "la última versión" sin indicar el número.** Una edición concurrente haría que el paciente firme un texto que no leyó.

### D3. Texto plano y normalización
El texto de las plantillas es **texto plano** (sin HTML ni Markdown), de 1 a 20 000 caracteres; el título, de 1 a 120. Al crear una versión, el dominio:
- pasa los saltos de línea `\r\n` y `\r` a `\n`;
- normaliza a Unicode NFC;
- recorta espacios al principio y al final del título y del texto.

Lo guardado ya está normalizado, así que el texto copiado al firmar y el que entra al hash son idénticos byte a byte. C-48 lo muestra como texto, respetando los saltos de línea, sin interpretarlo como HTML (evita XSS).

El nombre del firmante se recorta, se colapsan los espacios internos y se normaliza a NFC (3 a 200 caracteres). El DNI se normaliza quitando puntos y espacios y tiene que quedar en 7 u 8 dígitos; después se compara con el DNI del paciente normalizado igual. Si no coincide: `422 FIRMANTE_NO_COINCIDE` (es un dato de entrada que no corresponde, no un conflicto de estado). El nombre no se compara con el del paciente (PA-46).

**Edición sin cambios**: si el título y el texto normalizados son iguales a los de la última versión, no se crea una versión y la respuesta es `200` con la actual.

### D4. Contenido canónico y hash
`ContenidoCanonico` (dominio, función pura) arma los bytes UTF-8 de este formato, **versión 1**:

```
consentimiento-informado/v1\n
<campo>=<n>:<valor>\n   (una línea por campo, en este orden)
```

donde `<n>` es la longitud en **bytes UTF-8** de `<valor>`. Los campos, en orden:

1. `paciente_id` (decimal)
2. `plantilla_version_id` (decimal)
3. `titulo`
4. `texto`
5. `firmante_nombre` (normalizado, D3)
6. `firmante_dni` (normalizado, solo dígitos)
7. `profesional_id` (decimal)
8. `firmado_en`: instante en UTC, truncado a microsegundos, con el formato `uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'`
9. `ip_origen`: el texto guardado tal cual
10. `dispositivo`: el texto guardado tal cual (vacío si no vino)
11. `tipo_firma`: `ELECTRONICA`

El hash es `SHA-256` de esos bytes, en hexadecimal en minúsculas (64 caracteres). Se guardan `hash_algoritmo = 'SHA-256'` y `formato_canonico = 1` para poder evolucionar el formato sin invalidar lo firmado.

- **El prefijo de longitud** hace que el contenido no sea ambiguo: no se puede correr texto de un campo al siguiente sin cambiar el hash (escenario "Los campos no se pueden correr de lugar"). Un separador solo no alcanza, porque el texto puede contener saltos de línea.
- **El instante se trunca a microsegundos** antes de guardarlo, porque es la precisión de `timestamptz`. Así lo que se lee de la base vuelve a dar el mismo hash.
- **La revocación no entra en el hash**: vive en otra tabla (D5) y el documento firmado sigue verificando.
- **Verificación**: `ServicioConsentimientos.verificar` lee la fila, rearma el contenido canónico con las columnas guardadas, calcula el SHA-256 y lo compara en tiempo constante (`MessageDigest.isEqual`) con `hash`. Un resultado no íntegro **no** es una excepción: se devuelve `integro: false` con los dos valores, porque informarlo es justamente el objetivo.
- **Vector de referencia**: un test unitario fija un consentimiento con datos ficticios, el contenido canónico esperado en un archivo de `src/test/resources` y su SHA-256 calculado fuera de Java (`sha256sum`). Así un cambio accidental del formato rompe el test.

**Alternativa descartada: JSON canónico.** Exige fijar orden de claves, escape y números (RFC 8785) y una dependencia o código propio más grande; el formato con longitud es más simple de reproducir a mano para un perito.

**Alternativa descartada: hashear solo el texto.** No ataría el texto al firmante, a la fecha ni a la evidencia.

### D5. Tablas (V18) e inmutabilidad en la base
`V18__consentimientos.sql` crea cuatro tablas. Siguiendo la skill `postgresql-table-design`, los textos son `TEXT` con `CHECK` de longitud y los instantes `TIMESTAMPTZ`; los FK llevan índice.

- **`plantilla_consentimiento`**: `id`, `activa` (`BOOLEAN NOT NULL DEFAULT true`), `creada_en`, `creada_por` (FK a `usuario`). Es lo único mutable: el trigger `plantilla_consentimiento_solo_estado` rechaza el `DELETE` y cualquier `UPDATE` que cambie algo que no sea `activa`.
- **`plantilla_consentimiento_version`**: `id`, `plantilla_id` (FK), `numero` (`INTEGER`, `CHECK > 0`), `titulo` (`CHECK` 1–120), `texto` (`CHECK` 1–20 000), `creada_en`, `creada_por` (FK a `usuario`). `UNIQUE (plantilla_id, numero)`. Trigger de solo agregado: rechaza todo `UPDATE` y `DELETE`.
  - La unicidad del título se valida en la aplicación contra la última versión de cada plantilla (sin distinguir mayúsculas), porque el título vive en las versiones. Es una regla de usabilidad, no una garantía legal.
- **`consentimiento_firmado`**: `id`, `paciente_id` (FK), `plantilla_version_id` (FK), `profesional_id` (FK), `titulo`, `texto`, `firmante_nombre` (`CHECK` 3–200), `firmante_dni` (`CHECK ~ '^[0-9]{7,8}$'`), `firmado_en`, `ip_origen` (`TEXT`, `CHECK` 1–45), `dispositivo` (`TEXT NOT NULL`, `CHECK` hasta 512, puede ser vacío), `tipo_firma` (`CHECK = 'ELECTRONICA'`), `formato_canonico` (`SMALLINT`, `CHECK = 1`), `hash_algoritmo` (`CHECK = 'SHA-256'`), `hash` (`CHAR(64)`, `CHECK ~ '^[0-9a-f]{64}$'`). Índices `(paciente_id, firmado_en DESC, id DESC)` y `(plantilla_version_id)`. Trigger `consentimiento_firmado_bloqueado`: rechaza **todo** `UPDATE` y `DELETE`.
- **`consentimiento_revocacion`**: `id`, `consentimiento_id` (FK, **`UNIQUE`**), `revocado_en`, `revocado_por` (FK a `profesional`), `motivo` (`CHECK btrim <> ''` y hasta 200). Trigger de solo agregado. El `UNIQUE` garantiza una sola revocación aunque haya dos pedidos a la vez: el segundo `INSERT` falla y se traduce a `409 CONSENTIMIENTO_YA_REVOCADO`.

**`ip_origen` como `TEXT` y no `INET`.** `INET` reescribe la dirección (por ejemplo, comprime IPv6), y Java la formatea distinto (`0:0:0:0:0:0:0:1` contra `::1`). Como la IP entra al hash, se guarda exactamente el texto que se hasheó; el dominio valida que sea una IPv4 o IPv6 literal.

**Revocación en tabla aparte, no columnas en el firmado** (a diferencia de C-26, D7). El documento firmado queda completamente inmutable y su trigger no tiene excepciones; la regla "el documento no cambia" se puede comprobar con solo mirar el trigger.

**Inmutabilidad: trigger y no permisos.** Se descarta `REVOKE UPDATE, DELETE` porque la aplicación se conecta con el mismo usuario que corre Flyway y es dueño de las tablas: el dueño no pierde esos permisos de forma efectiva y separar roles de base cambia la infraestructura de todo el equipo (PA-11). El trigger sigue el patrón de V8, V16 y V17. Lo que el trigger no cubre (un dueño que lo desactiva o un `TRUNCATE`) lo detecta el hash si cambia el contenido, y queda en Riesgos.

### D6. Concurrencia entre edición y firma
- **Editar**: `SELECT ... FROM plantilla_consentimiento WHERE id = ? FOR UPDATE`, se lee la última versión, se compara con `versionBase` y se inserta `numero + 1`. Si otra edición ganó, el `UNIQUE (plantilla_id, numero)` también lo frena y se responde `409 VERSION_DESACTUALIZADA`.
- **Firmar**: en la misma transacción, `SELECT ... FOR SHARE` de la plantilla, se comprueba que esté activa y que `versionNumero` sea la última; recién después se inserta el firmado. Así una edición o una desactivación no se cuela entre la comprobación y la firma.
- **Revocar**: el `UNIQUE (consentimiento_id)` resuelve la carrera (D5).

### D7. Evidencia: IP y dispositivo
El controller arma un `EvidenciaFirma(ip, dispositivo)` desde `HttpServletRequest` y se lo pasa al servicio; el dominio no conoce HTTP.
- **IP**: `request.getRemoteAddr()`. **No** se lee `X-Forwarded-For` a mano: lo puede inventar cualquier cliente. Con la configuración actual, detrás del proxy de Vite en desarrollo o de un proxy inverso en producción, la IP guardada es la del proxy. Para guardar la del puesto hace falta configurar `server.forward-headers-strategy` con los proxies de confianza; es configuración global y queda en PA-49, sin cambiarla en este change.
- **Dispositivo**: el encabezado `User-Agent`, recortado a 512 caracteres y sin caracteres de control; vacío si no viene. Es una declaración del navegador, no una identificación del equipo.
- **Fecha y hora**: `Instant.now(reloj)` con el `Clock` inyectado, truncado a microsegundos (D4).

### D8. Acceso por rol
- **Plantillas**: el `ADMIN` las administra porque son textos del consultorio, no datos de un paciente. El odontólogo las lee para elegir y mostrar el texto. La recepción y el paciente no las necesitan (PA-44).
- **Consentimientos firmados**: son parte de la historia clínica (Ley 26.529), así que siguen RN-CL-01: el odontólogo firma, ve, verifica y revoca; el `ADMIN` solo ve y verifica; la recepción y el paciente reciben `403` sin datos. Es la misma separación que C-26: el `ADMIN` edita lo que no es del paciente y solo lee lo que es del paciente.
- Firmar y revocar exigen `profesionalId` en la sesión; sin él, `AccessDeniedException` → `403 ACCESO_DENEGADO`.

### D9. Códigos de error
Nuevos en `CodigoClinica`, mapeados en `ClinicaExceptionHandler`:

| Código | Estado | Cuándo |
|---|---|---|
| `PLANTILLA_DUPLICADA` | `409` | Título repetido sin distinguir mayúsculas |
| `VERSION_DESACTUALIZADA` | `409` | `versionBase` o `versionNumero` no es la última |
| `PLANTILLA_INACTIVA` | `409` | Firma sobre una plantilla desactivada |
| `CONSENTIMIENTO_YA_REVOCADO` | `409` | Segunda revocación |
| `FIRMANTE_NO_COINCIDE` | `422` | El DNI escrito no es el del paciente |

Las demás validaciones reutilizan `DATOS_INVALIDOS` y `RECURSO_NO_ENCONTRADO`. Un consentimiento que no pertenece al paciente de la ruta da `404`, igual que una imagen.

### D10. Persistencia con JDBC
`RegistroPlantillasJdbc` y `RegistroConsentimientosJdbc` implementan los puertos `RegistroPlantillas` y `RegistroConsentimientos` con `JdbcClient`, como `RegistroImagenesJdbc`. Los nombres de los profesionales y el username del `ADMIN` salen con un `JOIN`. Ningún método de los puertos actualiza ni borra un firmado, una versión o una revocación.

### D11. Privacidad
- `ClinicaException` lleva mensajes fijos: nunca el texto, el nombre, el DNI, la IP, el dispositivo ni el motivo.
- `FIRMANTE_NO_COINCIDE` no repite ni el DNI escrito ni el del paciente.
- El servicio no escribe logs. Un test captura la salida (`OutputCaptureExtension`) al firmar y comprueba que no aparezcan esos datos.
- Nada va a la auditoría de turnos ni a notificaciones.
- La IP y el dispositivo son datos personales (Ley 25.326) que se guardan con una finalidad declarada, la prueba de la firma, y solo los ven el odontólogo y el `ADMIN`.

## UI Mockups

- Design system revision: no aplica.
- Decision: no (no UI; las pantallas van en C-48) — 2026-11-24.
- Mockups: no aplica. C-48 `frontend-consentimientos` lleva sus mockups a 390 y 1440 px aprobados antes del apply.
- Approval: no aplica.
- Preview: no aplica.
- Visual verify: no aplica.

## Risks / Trade-offs

**Validez legal**
- [La firma electrónica no tiene las presunciones de la firma digital] → Por el art. 5 de la Ley 25.506, si el paciente desconoce la firma, le toca al consultorio probar su validez; las presunciones de autoría e integridad (arts. 7 y 8) son solo de la firma digital. Además, el Código Civil y Comercial (art. 288) dice que en instrumentos electrónicos el requisito de firma se cumple con firma digital, así que el documento vale como prueba y no como instrumento firmado en sentido estricto. La mitigación es la evidencia: texto exacto, nombre y DNI escritos, profesional con sesión presente, fecha, IP, dispositivo y hash. Se documenta en DD-20 y se recomienda que el consultorio lo valide con su asesor legal. Este diseño no es asesoramiento legal.
- [No repudio limitado] → El hash detecta un cambio en el contenido, pero quien controla la base podría reescribir una fila y recalcular su hash, porque el hash no está firmado con una clave externa. Mitigaciones futuras, fuera de C-27: un sello HMAC con una clave fuera de la base, un sellado de tiempo de un tercero o publicar periódicamente los hashes. Se anota en DD-20.
- [El paciente escribe datos que el odontólogo podría escribir por él] → Queda registrado el profesional de la sesión, que responde por la firma. La comparación del DNI evita errores de paciente, no suplantaciones.

**Integridad**
- [Algoritmo] → SHA-256 es estándar y resistente a colisiones hoy. `hash_algoritmo` y `formato_canonico` permiten pasar a otro sin invalidar lo firmado.
- [Cambio accidental del formato canónico] → El vector de referencia del test lo detecta. Un formato nuevo es `formato_canonico = 2`, nunca un cambio del 1.
- [El dueño de la base puede desactivar el trigger o hacer `TRUNCATE`] → El trigger frena errores y accesos de la aplicación, no a un administrador de base. Un cambio de contenido lo detecta la verificación; un borrado completo no. Se acepta para el TP (DD-05, un consultorio por despliegue) y se anota la extensión de publicar los hashes.

**Evidencia**
- [IP detrás de un proxy] → Sin `forward-headers-strategy`, se guarda la IP del proxy. No se confía en `X-Forwarded-For` sin una lista de proxies de confianza (PA-49). La IP es evidencia de contexto, no identifica a la persona.
- [User-Agent falsificable] → Es una declaración del navegador; vale como contexto, no como prueba de identidad.

**Privacidad**
- [La evidencia guarda DNI, IP y dispositivo] → Solo la ven el odontólogo y el `ADMIN`; no va a logs ni a errores; se guarda con una finalidad declarada. El texto de un consentimiento revela el tratamiento, así que es un dato de salud (RN-CL-01).

**Otros**
- [Textos largos en la respuesta] → Hasta 20 000 caracteres por documento; el listado no trae el texto.
- [Título único solo en la aplicación] → Dos `ADMIN` que crean a la vez la misma plantilla podrían duplicar el título. Es improbable en un consultorio chico y no afecta la validez de nada firmado.

## Migration Plan

1. Mergear el PR. Flyway aplica `V18__consentimientos.sql` con out-of-order y no toca tablas existentes.
2. No hay variables de entorno nuevas ni datos de seed: el `ADMIN` carga las plantillas. Los tests crean las suyas con textos ficticios.
3. **Rollback**: las tablas son nuevas. Se puede revertir el código y dejar las tablas, o borrarlas con una migración nueva si el equipo lo decide; ningún dato firmado se borra sin esa decisión. Las migraciones mergeadas no se editan.

## Open Questions

Las propuestas que Emanuel tiene que confirmar están en `knowledge-base/10_preguntas_abiertas.md` (PA-43 a PA-52). Ninguna bloquea el apply: cada una tiene un valor por defecto aplicado en las specs, y cambiarlo después es un `opsx update` del change.
