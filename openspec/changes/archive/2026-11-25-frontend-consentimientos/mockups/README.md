# Mockups de C-48 `frontend-consentimientos`

- **Design system revision**: 5, aprobada. No suma tokens: `consentimientos.css` usa solo los de la Revision 5 (design D10).
- **Approval**: approved (2026-11-25, by user: Emanuel), con las preguntas 1 a 13 de `design.md` respondidas. Revisados el 2026-11-24 con las respuestas de Emanuel a las preguntas 1 a 10 de `design.md` (firma con el nombre del paciente y el DNI vacío, bloqueo "Devolvé el equipo al profesional", comprobación del envío y aviso de doble firma). La líder los revisa en el PR. Son el criterio de aceptación visual.
- **Preview**: rendered (2026-11-24; las pantallas de firma, bloqueo, resultado y ficha se volvieron a capturar después de la revisión). Capturas de página completa en `previews/`, a 1440 × 900 y 390 × 844, con Playwright sobre un servidor estático local. Ninguna pantalla tiene desplazamiento horizontal a 390 px (ancho del documento 375 = ancho visible), ni errores o advertencias de consola, ni pedidos fallidos, ni controles de menos de 44 px.

## Cómo verlos

Los mockups cargan `design-system/tokens.css`, `frontend/src/styles.css` tal como está en la rama y `consentimientos.css`, que es la propuesta del módulo `NN-consentimientos.css`; sus reglas marcadas "Solo mockups" no pasan al módulo. `mockup.js` arma el encabezado y la barra lateral (como el de C-47, sin el odontograma), escribe los contadores "N/20.000" con el formato de `es-AR` y suma los íconos `firma`, `verificado`, `alterado` y `candado`. `design-system/brand.js` pone el nombre de la marca.

Para evitar bloqueos de `file://`, serví la raíz del repo y abrí la página desde ahí:

```powershell
python -m http.server 8765 --bind 127.0.0.1
```

Después abrí, por ejemplo, `http://127.0.0.1:8765/openspec/changes/frontend-consentimientos/mockups/firma-vacia.html`.

## Brief

- **Para quién**:
  - el administrador, que carga y mantiene los textos de los consentimientos del consultorio;
  - el odontólogo, que hace firmar al paciente en la computadora del consultorio, consulta, verifica y revoca;
  - el paciente, que solo ve la vista de firma y después el bloqueo, nunca la ficha;
  - el administrador también consulta y verifica lo firmado, en solo lectura.
- **Qué tienen que poder hacer**: escribir un texto largo sin perder nada; saber qué versión está vigente y cuáles hubo; que el paciente lea y firme sin ver datos clínicos; saber de un vistazo si un documento es íntegro, está alterado o fue revocado.
- **Dónde**: pestaña "Consentimientos" de Configuración (solo `ADMIN`) y sección "Consentimientos" de la ficha (`#/pacientes`), después de "Imágenes y estudios". Sin entrada nueva en la barra lateral.
- **Tamaños**: 390 × 844 (una columna, botones a lo ancho) y 1440 × 900 (acciones a la derecha; la verificación a la derecha del documento desde 64 rem).
- **Restricciones**: tokens del design system; estados escritos ("Activa", "Vigente", "Revocado", "Íntegro", "Alterado") y no solo por color; controles de 44 px; diálogos con el molde de `shared/ui/dialogo.ts`; "firma electrónica", nunca "firma digital"; texto plano con sus saltos de línea; datos ficticios ("Paciente Uno", DNI 99000001, `ana.ejemplo`, `admin`, IP de documentación 192.0.2.10).

## Pantallas

| Archivo | Qué muestra | Requisitos |
|---|---|---|
| `plantillas-lista.html` | Admin, Configuración → "Consentimientos": dos plantillas activas y una inactiva, con "Editar", "Versiones" y "Desactivar"/"Activar"; aviso de guardado | Pestaña "Consentimientos" solo para el administrador, Lista de plantillas, Activar y desactivar una plantilla |
| `plantillas-editor.html` | "Nueva plantilla" vacía; "Editar" con el aviso de versión nueva y el contador; título vacío y texto de 20.001 caracteres; `PLANTILLA_DUPLICADA`; `VERSION_DESACTUALIZADA` con lo escrito conservado; descartar cambios; avisos de creada, versión nueva y sin cambios | Crear una plantilla, Editor de texto plano con contador, Editar una plantilla crea una versión nueva, Conflicto de versión al editar, Descartar una edición sin guardar |
| `plantillas-estados.html` | Versiones (la 1 desplegada, con una marca de HTML mostrada literal); sin plantillas; cargando; falla con "Reintentar"; plantilla desactivada | Ver las versiones de una plantilla, Texto de la plantilla como texto plano, Lista de plantillas, Activar y desactivar |
| `consentimientos-ficha.html` | Odontólogo, después de desbloquear: sección con "Firmar consentimiento", aviso "Consentimiento firmado: …", uno "Vigente" y uno "Revocado" (sin "Revocar"); diálogo para elegir la plantilla | Sección de consentimientos en la ficha, Listado de consentimientos firmados, Elegir la plantilla para firmar |
| `consentimiento-ver.html` | Documento firmado con "Íntegro" al lado, "Firma electrónica", firmante, DNI, fecha, profesional; "Evidencia de la firma" plegada y desplegada (IP, dispositivo, hash) | Ver un consentimiento firmado, Verificar al abrir |
| `consentimientos-admin.html` | Admin: el listado sin "Firmar consentimiento" ni "Revocar"; documento revocado con su motivo y verificación "Alterado" con los dos hash | Solo lectura para el administrador, Ver un consentimiento firmado, Verificar al abrir |
| `consentimientos-estados.html` | Sin consentimientos; cargando; falla con "Reintentar"; sin plantillas activas; plantilla no disponible (404); revocar con el motivo vacío; avisos de revocado y ya revocado; "Verificando…"; verificación fallida con "Verificar de nuevo" | Sección de consentimientos en la ficha, Elegir la plantilla para firmar, Revocar un consentimiento, Verificar al abrir |
| `firma-vacia.html` | Vista de firma a pantalla completa recién abierta: solo el título, la versión, "Para: Paciente Uno" (nunca el DNI), el texto, los dos campos vacíos, la explicación de la firma electrónica, la casilla, "Cancelar" y "Firmar" | Vista de firma a pantalla completa, Nombre y DNI escritos por el paciente, Firma electrónica, no firma digital |
| `firma-completa.html` | La misma vista con los datos completos y la casilla marcada | Firmar el consentimiento |
| `firma-errores.html` | Validación previa: nombre corto, DNI con letras y casilla sin marcar | Validar la firma antes de enviarla |
| `firma-estados.html` | `VERSION_DESACTUALIZADA` (texto de la versión 4 y casilla desmarcada), `FIRMANTE_NO_COINCIDE` (sin repetir el DNI), `PLANTILLA_INACTIVA` (solo "Terminar"), cancelar con datos y "Firmando…" con "Firmar" y "Cancelar" deshabilitados | El texto cambió mientras se leía, DNI que no coincide, Plantilla desactivada durante la firma, Firmar el consentimiento ("Un solo envío"), Cancelar la firma, Cierre de la vista por el navegador |
| `firma-bloqueo.html` | "Devolvé el equipo al profesional" a pantalla completa después de una firma correcta: usuario fijo, "Contraseña" y "Desbloquear"; nada del paciente | Firmar el consentimiento, Devolver el equipo al profesional, Desbloquear con la contraseña del profesional |
| `firma-bloqueo-estados.html` | Bloqueo después de cancelar, de "Terminar" o una sesión vencida, y de un envío dudoso; contraseña incorrecta (2 y 1 intentos); sin conexión; desbloqueando; ingreso después de tres fallos; ingreso al arrancar con la marca de firma en curso | Cancelar la firma, Sesión vencida durante la firma, Envío dudoso de la firma, Contraseña incorrecta en el bloqueo, Recargar durante la firma o el bloqueo |
| `firma-resultado.html` | Ficha después de desbloquear: "Comprobando…", "La firma quedó guardada." con "Ver", "La firma no se guardó." con "Hacer firmar de nuevo", "No pudimos comprobar…" con "Comprobar de nuevo", sesión vencida; pregunta "Ya firmó este consentimiento hoy" | Comprobar si la firma quedó guardada, Sesión vencida durante la firma, Aviso antes de firmar dos veces |

## Decisiones visuales

- **La vista de firma no se parece a la ficha**: sin encabezado, sin menú y con fondo opaco; arriba, el rótulo "Consentimiento informado · Firma electrónica"; el texto en letra grande en el flujo de la página y, al final, el bloque de la firma con borde superior de acento. En los mockups es una página; en la aplicación es un `<dialog>` modal que ocupa toda la pantalla (design D5).
- **Estados escritos**: "Vigente" en `--ds-ok`, "Revocado" en `--ds-error` con la fila punteada sobre `--ds-superficie-2` (el patrón de "Anulada" de C-47).
- **Verificación al lado del documento**: una caja "Íntegro" verde con ícono de escudo o "Alterado" roja con ícono de alerta, borde de 2 px y los dos hash en `--ds-fuente-mono`. Mientras no responde, "Verificando…"; si falla, "Sin verificar", nunca "Íntegro".
- **Evidencia plegada**: IP, dispositivo y hash en un `<details>` "Evidencia de la firma", cerrado al abrir (design D7).
- **Editor**: área de texto de 24 rem de alto en la computadora y 18 rem en el celular, con el contador "N/20.000" que se pone rojo al pasarse; sin `maxlength` (design D4).
- **Tira de pestañas**: con "Consentimientos", Configuración tiene diez pestañas y a 1440 px la tira se desplaza; el mockup la desplaza hasta la elegida. Ver los hallazgos del propose.
- **Bloqueo**: tarjeta centrada sobre fondo opaco, con un candado, el título "Devolvé el equipo al profesional" en letra grande para quien tiene el equipo y, separada por una línea, la parte del profesional (usuario fijo en `--ds-fuente-mono`, contraseña y "Desbloquear" a lo ancho). No es un `<dialog>`: vive fuera de la raíz de la aplicación (design D12).
- **Resultado de la comprobación**: una caja con borde izquierdo y fondo del estado ("quedó guardada" en `--ds-ok`, "no se guardó" en `--ds-error`, "comprobando" y "no pudimos comprobar" en `--ds-alerta`), siempre con texto y no solo color.
