# Mockups de C-47 `frontend-imagenes-y-radiografias`

- **Design system revision**: 5, aprobada. No suma tokens: `imagenes.css` usa solo los de la Revision 5.
- **Approval**: approved (2026-11-24, by user: Emanuel). Son el criterio de aceptación visual del apply; falta la revisión de la líder en el PR #119.
- **Preview**: rendered (2026-11-24). Las capturas de página completa están en `previews/`, a 1440 × 900 y 390 × 844, hechas con Playwright. Ninguna pantalla tiene desplazamiento horizontal a 390 px ni errores de consola.

## Cómo verlos

Los mockups cargan `frontend/src/styles.css` tal como está en la rama, más `imagenes.css`, que es la propuesta del módulo `21-imagenes.css`; sus reglas marcadas "Solo mockups" no pasan al módulo. `mockup.js` es la copia del de C-46 (encabezado y barra lateral) con cinco íconos más: imagen, documento, descarga, ver y subir.

Para evitar bloqueos de `file://`, serví la raíz del repo y abrí la página desde ahí:

```powershell
python -m http.server 8765 --bind 127.0.0.1
```

Después abrí `http://127.0.0.1:8765/openspec/changes/frontend-imagenes-y-radiografias/mockups/imagenes-consulta.html`.

## Brief

- **Para quién**:
  - El odontólogo, que guarda las radiografías, fotos y estudios del paciente durante la consulta o después, desde la computadora del consultorio.
  - El administrador, que solo los consulta.
- **Qué tienen que poder hacer**:
  - Ver qué imágenes tiene el paciente y de qué pieza son.
  - Abrir una radiografía en grande y descargarla.
  - Cargar una nueva sin salir de la ficha.
  - Anular una cargada por error, sin borrarla.
- **Dónde**: en una sección "Imágenes y estudios" de la ficha (`#/pacientes`), debajo del periodontograma de C-46. Sin entrada nueva en la barra lateral.
- **Tamaños**: 390 × 844, con una tarjeta por imagen y los botones a lo ancho, y 1440 × 900, con una fila por imagen y los botones a la derecha.
- **Decisiones de Emanuel (2026-11-24)**:
  - El listado va sin miniaturas.
  - Los PDF solo se descargan.
  - La carga se hace en un diálogo que se abre con un botón.
- **Restricciones**:
  - Tokens del design system.
  - La anulación se lee por la etiqueta y el texto, no solo por el estilo.
  - Controles de 44 px.
  - Datos ficticios; la radiografía del visor es un dibujo CSS, no una imagen real.
  - Ningún dato de salud en la dirección ni en el navegador.

## Pantallas

| Archivo | Qué muestra | Requisitos del spec |
|---|---|---|
| `imagenes-consulta.html` | Odontólogo: sección con "Cargar imagen", aviso de éxito, filtros, cuatro imágenes (una de ellas un PDF, sin "Ver") y el visor abierto con una radiografía | Sección de imágenes en la ficha, Listado de imágenes, Filtrar por pieza y mostrar anuladas, Ver una imagen, Descargar un archivo, Sección de imágenes accesible |
| `imagenes-carga.html` | Diálogo de carga recién abierto, con los datos completos y con errores de validación; otros mensajes del archivo y del servidor | Cargar una imagen, Validar el archivo antes de enviarlo, Errores de la API al cargar |
| `imagenes-admin.html` | Administrador con "Mostrar anuladas": ve, filtra y descarga, sin "Cargar imagen" ni "Anular"; la anulada con quién, cuándo y el motivo | Solo lectura para el administrador, Filtrar por pieza y mostrar anuladas |
| `imagenes-estados.html` | Sin imágenes, cargando, falla con "Reintentar", filtro vacío, diálogo de anulación con motivo, avisos de la anulación, descartar una carga y falla del visor | Sección de imágenes en la ficha, Anular una imagen, Descartar una carga sin guardar, Ver una imagen |

## Decisiones visuales

- **Tipo de archivo con ícono y nombre**: imagen en acento y PDF en info, siempre con el nombre de la categoría escrito al lado.
- **Pieza y formato en etiquetas**: `Pieza 36` o `PDF` en una etiqueta, para leerlos rápido; la fecha, el profesional, el tipo y el peso van en la línea de metadatos.
- **Anulada**:
  - la etiqueta "Anulada";
  - el nombre de la categoría tachado;
  - el borde punteado sobre la superficie gris;
  - una línea en rojo con quién la anuló, cuándo y el motivo.
- **Visor oscuro** para que las radiografías se lean bien. El texto alternativo dice la categoría, la pieza y la fecha.
- **Diálogo de carga**: la zona del archivo es una caja punteada con "Elegir archivo" y la ayuda "JPEG, PNG o PDF de hasta 10 MB.". La descripción usa el campo suave de C-44 con su contador.
