# Mockups de C-46 `frontend-periodontograma`

- Design system revision: 5 propuesta (design D7). Suma los tokens `--ds-perio-*`: tres reusan colores existentes (sano, leve y severo) y moderado es nuevo (`#9a3412`, 7.3:1 sobre blanco). Mientras no estén en `design-system/tokens.css`, se definen al principio de `periodontograma.css`. La Revision 4 está aprobada.
- Approval: pending. Los aprueba la líder antes del apply (modo `system-and-mockups`), junto con la Revision 5.
- Preview: rendered (2026-11-24). Capturas de página completa en `previews/`, a 1440 × 900 y 390 × 844, con Playwright. Ninguna pantalla tiene desplazamiento horizontal a 390 px ni errores de consola.

## Cómo verlos

Los mockups cargan `frontend/src/styles.css` tal como está en la rama, más `periodontograma.css`, que es la propuesta del módulo `19-periodontograma.css`. `mockup.js` es la copia del de C-44 (encabezado y barra lateral) y `perio-mockup.js` dibuja los dientes del mapa, la leyenda, las vistas grandes y las tablas desde atributos `data-*`.

Para evitar bloqueos de `file://`, serví la raíz del repo y abrí la página desde ahí:

```powershell
python -m http.server 8765 --bind 127.0.0.1
```

Después abrí `http://127.0.0.1:8765/openspec/changes/frontend-periodontograma/mockups/periodontograma-consulta.html`.

## Brief

- **Para quién:** el odontólogo carga el periodontograma en la consulta, desde la computadora del consultorio o una tablet; el administrador solo lo consulta.
- **Qué tienen que poder hacer:** ver de un vistazo qué piezas están peor, mirar los sitios de una pieza, abrir una versión anterior y cargar la visita de hoy partiendo de la anterior.
- **Dónde:** sección "Periodontograma" de la ficha (`#/pacientes`), debajo del odontograma de C-44 (decisión de la líder). Sin entrada nueva en la barra lateral.
- **Tamaños:** 390 × 844 (cuatro piezas por fila y una tarjeta por sitio) y 1440 × 900 (16 piezas por fila y la tabla de sitios).
- **Restricciones:** tokens del design system; el estado se lee por texto, no solo por color; controles de 44 px; datos ficticios; ningún dato de salud en la dirección ni en el navegador.

## Pantallas

| Archivo | Qué muestra | Requisitos del spec |
|---|---|---|
| `periodontograma-consulta.html` | Odontólogo: la versión más nueva, el selector de versiones, "Nueva versión", el mapa con los estados y el detalle de la pieza 16 | Periodontograma en la ficha, Mapa de piezas con su estado, Detalle de una pieza, Versiones anteriores, Periodontograma accesible |
| `periodontograma-nueva-version.html` | Nueva versión copiada de la última, pieza 36 elegida con un sitio vacío después de "Guardar versión": error arriba, pieza marcada en el mapa y errores en los campos; ayuda de la recesión | Nueva versión precargada, Cargar, marcar ausente y quitar piezas, Convención de la recesión, Validación antes de guardar |
| `periodontograma-admin.html` | Administrador con una versión anterior abierta, en solo lectura y sin "Nueva versión" | Solo lectura para el administrador, Versiones anteriores |
| `periodontograma-estados.html` | Sin periodontograma, primera versión sin piezas ("Cargar pieza" y "Cargar como ausente"), guardada, rechazos de validación y del servidor, falla de la carga con "Reintentar" y el diálogo de descartar | Periodontograma en la ficha, Guardar la versión, Descartar una carga sin guardar |

## Decisiones visuales

- **Dientes dibujados** (pedido de la líder, 2026-11-24): cada botón del mapa tiene un dibujo propio y simplificado del diente según su tipo, con la raíz hacia afuera de la boca, como en el periodontograma en papel. Sobre la cara vestibular van el margen de la encía (rojo) y el fondo de la bolsa (azul); la distancia entre el límite amelocementario punteado y el fondo es la pérdida de inserción. Ausente se tacha y "Sin cargar" va punteado. El número y el estado siguen en texto.
- **Vistas grandes:** la pieza elegida se ve por vestibular y por palatino o lingual con grilla de 1 mm, arriba de la tabla de sitios.
- **En la nueva versión** cada pieza muestra el estado de la versión copiada hasta guardar; el estado nuevo lo calcula el sistema al guardar (design, Non-Goals).
- **La pérdida de inserción** se muestra por sitio como suma para leer (profundidad + recesión); el estado no se calcula en la pantalla.
- **En 390 px** el formulario de una pieza es largo (una tarjeta por sitio). Se acepta porque se edita de a una pieza.
