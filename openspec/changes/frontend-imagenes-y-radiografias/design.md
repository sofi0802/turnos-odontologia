# Design

## Context

C-26 dejó en el backend la API de imágenes clínicas: carga multipart en `POST /api/v1/pacientes/{id}/imagenes`, listado con `?pieza=&incluirAnuladas=`, el archivo en `GET …/{imagenId}/archivo?descarga=` (con `no-store` y `nosniff`) y la anulación en `POST …/{imagenId}/anulacion`. Los errores de negocio llegan como `ProblemDetail` con los códigos `TIPO_ARCHIVO_NO_PERMITIDO`, `ARCHIVO_DEMASIADO_GRANDE` (413) e `IMAGEN_YA_ANULADA` (409).

La ficha del frontend es de C-44:
- `pages/pacientes.ts` arma la página y monta las secciones de `frontend/src/ficha/`.
- La confirmación de "Cambiar de paciente" usa `abrirDialogo` y junta los `sinGuardar()` de las secciones.
- C-46 suma el periodontograma con el mismo patrón.

El cliente `shared/api/cliente.ts` hoy solo manda y recibe JSON, y su `traducirError` convierte un 413 en `ErrorGenerico`.

## Goals / Non-Goals

**Goals:**
- Que el odontólogo vea, abra, descargue, cargue y anule las imágenes del paciente sin salir de la ficha.
- Que el administrador las consulte y descargue en solo lectura.
- Que ningún archivo ni dato de salud quede en la dirección, el almacenamiento del navegador ni la consola.

**Non-Goals:**
- Miniaturas, galería, comparar imágenes, anotar o recortar.
- Ver los PDF dentro de la aplicación: solo se descargan.
- Cargar varias imágenes a la vez o arrastrar archivos.
- Cambiar el backend de C-26 o el resto de la ficha de C-44 y C-46.

## Decisions

### D1. Capacidad nueva
Las pantallas van en una capacidad nueva, `frontend-imagenes-clinicas`, y no como requisitos nuevos de `frontend-ficha-clinica`. El delta de C-46 sobre `frontend-ficha-clinica` todavía no está archivado; una capacidad aparte evita dos deltas abiertos sobre el mismo spec.

Los cambios del cliente sí modifican `frontend-cliente-api`, porque son comportamiento general del cliente.

### D2. Módulos
- `ficha/imagenes-api.ts`:
  - pedidos `listarImagenes`, `cargarImagen` (arma el `FormData`), `descargarArchivo` (devuelve un `Blob`) y `anularImagen`;
  - lectura defensiva como `ficha/api.ts`: descarta valores con otro tipo y nunca escribe en la consola.
- `ficha/imagenes-modelo.ts`, funciones puras que se prueban sin DOM:
  - validación del archivo, la pieza, la fecha y la descripción;
  - nombres de las categorías;
  - nombres accesibles ("Radiografía de la pieza 36 del 30/11/2026");
  - formato del peso ("312 KB", "1,2 MB") y de las fechas.
- `ficha/imagenes.ts`: la sección. Exporta `crearImagenes(...)` y devuelve `{ contenedor, sinGuardar(): boolean, destruir(): void }`.
- `pages/pacientes.ts` solo la monta debajo del periodontograma, suma `sinGuardar()` a la confirmación de "Cambiar de paciente" y llama a `destruir()` al cambiar de paciente o salir de la página.

### D3. Extensión del cliente
`shared/api/cliente.ts` suma dos funciones, sin tocar las de JSON:
- `enviarFormulario(ruta, datos: FormData)`:
  - no fija `Content-Type`, para que el navegador ponga el `boundary`;
  - manda la cabecera CSRF y `credentials: 'include'` igual que los otros pedidos.
- `obtenerArchivo(ruta)` devuelve el `Blob` de la respuesta.

`traducirError` mapea el 413 a `ErrorValidacion` con el código `ARCHIVO_DEMASIADO_GRANDE`, aunque el cuerpo no sea un `ProblemDetail`. Si el servidor corta la subida antes del controlador, igual se muestra "El archivo supera los 10 MB.".

*Alternativa*: un cliente aparte para archivos. Se descarta porque duplicaría la sesión, el CSRF y el manejo de 401 y 403.

### D4. Carga independiente
- La sección pide el listado al montarse, con su propio `AbortController` y el contador de dibujo de la página: un pedido viejo no dibuja nada.
- Un error se muestra solo en la sección, con "Reintentar". El resto de la ficha se sigue viendo.
- El filtro de pieza y "Mostrar anuladas" vuelven a pedir el listado con `?pieza=&incluirAnuladas=`, en vez de filtrar en el navegador. Así la respuesta es la misma que valida el backend.
- Las opciones del filtro de pieza salen de las piezas del último listado sin filtrar, más "Todas las piezas".

### D5. Visor y descarga
- **Sin miniaturas**: el archivo solo se pide al elegir "Ver" o "Descargar". Un listado de 30 imágenes no baja 30 archivos.
- **"Ver"** (solo JPEG y PNG):
  - pide el archivo, crea un object URL con `URL.createObjectURL` y lo muestra en un `<img>` dentro de un `<dialog>` con fondo oscuro;
  - el `alt` es el nombre accesible de D2;
  - el object URL se revoca al cerrar el visor y en `destruir()`.
- **"Descargar"**:
  - pide el archivo con `?descarga=true` y lo baja con un `<a download>` temporal;
  - usa el nombre genérico `imagen-<id>.<ext>` que manda el backend, sin el nombre del paciente ni la pieza;
  - revoca el object URL apenas empieza la descarga.
- **PDF**: no tiene "Ver". Abrir un PDF de un `blob:` en el navegador depende del visor de cada navegador y puede abrirse en otra pestaña sin control.

### D6. Diálogo de carga
- "Cargar imagen" abre un diálogo con `abrirDialogo` y su `contenido`, con:
  - el archivo, en un `input type="file"` oculto para lectores detrás de un `<label class="btn">`, con `accept` de JPEG, PNG y PDF;
  - la categoría;
  - la fecha de la toma, que arranca en hoy;
  - la pieza (opcional);
  - la descripción (opcional), con contador.
- Validación antes de enviar, en `imagenes-modelo.ts`:
  - tipo por MIME o extensión;
  - tamaño ≤ 10 MB;
  - pieza FDI con la misma regla que `PiezaDental` (permanentes 11–48 y temporales 51–85);
  - fecha no futura en la zona del consultorio;
  - descripción de hasta 200 caracteres.
  - Si falla algo, no se pide nada, cada campo lleva `aria-invalid` y su mensaje, y el foco va al primero con error.
- El backend vuelve a validar el tipo por los primeros bytes; su `TIPO_ARCHIVO_NO_PERMITIDO` se muestra arriba del diálogo.
- Mientras se envía, "Cargar" dice "Cargando…" y queda deshabilitado.
- Con `201`:
  - se cierra el diálogo;
  - se vuelve a pedir el listado;
  - se avisa "Imagen cargada." en un `role="status"`.
- Con error, el diálogo queda abierto con los datos.
- `sinGuardar()` es verdadero si hay un archivo elegido o texto en la pieza o la descripción. "Cancelar" pregunta "Hay una imagen sin cargar. ¿Descartarla?" solo en ese caso.

### D7. Anulación
- "Anular" abre un diálogo con el resumen de la imagen y el motivo obligatorio (1 a 200 caracteres), con el botón de peligro.
- Con éxito, se vuelve a pedir el listado y se avisa "Imagen anulada.".
- Con `409 IMAGEN_YA_ANULADA` (otra persona la anuló antes), se muestra "La imagen ya estaba anulada." y también se vuelve a pedir el listado.
- Las anuladas solo se ven con "Mostrar anuladas", con:
  - la etiqueta "Anulada";
  - el texto tachado;
  - el borde punteado;
  - la línea "Anulada por … el … a las …: motivo".

### D8. Mensajes
Mensajes nuevos en `MENSAJES_POR_CODIGO`:
- `TIPO_ARCHIVO_NO_PERMITIDO`: "Solo se aceptan archivos JPEG, PNG o PDF.";
- `ARCHIVO_DEMASIADO_GRANDE`: "El archivo supera los 10 MB.";
- `IMAGEN_YA_ANULADA`: "La imagen ya estaba anulada.".

`PIEZA_INVALIDA` y `DATOS_INVALIDOS` ya tienen el suyo de C-44.

### D9. Estilos
- Un módulo nuevo, `frontend/src/styles/21-imagenes.css`, importado al final de `styles.css` y registrado en `MODULOS` y `REGLAS_POR_MODULO` del test de estilos.
- Es el `mockups/imagenes.css` sin las reglas marcadas "Solo mockups".
- Solo usa tokens de la Revision 5, sin `outline: none`. El design system no cambia de revisión.

### D10. Íconos
`shared/ui/iconos.ts` suma `imagen`, `documento`, `descarga`, `ver` y `subir`, con el mismo trazo que los existentes y `aria-hidden`.

## Risks / Trade-offs

- **[Riesgo] Un archivo de 10 MB en memoria por cada "Ver".** → Un solo visor abierto a la vez; el object URL se revoca al cerrar y al salir de la ficha.
- **[Riesgo] Un object URL sin revocar deja el archivo en memoria mientras la pestaña siga abierta.** → `destruir()` revoca todo lo pendiente. Un test verifica que `URL.revokeObjectURL` se llama al cerrar el visor y al cambiar de paciente.
- **[Riesgo] Validar en el frontend duplica reglas del backend.** → Son límites fijos (tipos, 10 MB, FDI, 200 caracteres) con tests de los bordes; los errores del backend se siguen mostrando.
- **[Trade-off] Sin miniaturas, el odontólogo no reconoce una radiografía de un vistazo.** → La categoría, la pieza, la fecha y la descripción van en cada fila, y el visor está a un clic.
- **[Riesgo] Conflictos de archivos compartidos.**
  - `frontend/` es de Enzo y `pacientes.ts` lo toca también C-46. → Se avisa en el PR.
  - El cambio en `pacientes.ts` se limita a montar la sección, la confirmación y `destruir()`.
  - Si C-46 se mergea antes, se rebasa sobre él.

## Migration Plan

Sin migraciones ni cambios de backend. Rollback: revertir el PR deja la ficha sin la sección de imágenes; las imágenes ya cargadas siguen en el backend.

## UI Mockups

- Design system revision: 5 (sin tokens nuevos).
- Decision: yes (2026-11-24). Modo `system-and-mockups`.
- Mockups: `openspec/changes/frontend-imagenes-y-radiografias/mockups/` (ver su `README.md`).
- Approval: approved (2026-11-24, by user: Emanuel). Falta la revisión de la líder en el PR #119.
- Preview: rendered (2026-11-24). Capturas en `mockups/previews/`, a 390 × 844 y 1440 × 900, sin desplazamiento horizontal ni errores de consola.
