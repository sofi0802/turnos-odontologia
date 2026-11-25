# Mockups de C-49 `frontend-obras-sociales`

- **Design system revision**: 5, aprobada. No suma tokens: `coberturas.css` usa solo los de la Revision 5.
- **Approval**: pending. Los aprueba la líder (Sofia Indovino) antes del apply; son el criterio de aceptación visual.
- **Preview**: rendered (2026-11-24). Capturas de página completa en `previews/`, a 1440 × 900 y 390 × 844, hechas con Playwright sobre un servidor estático local. Ninguna pantalla tiene desplazamiento horizontal a 390 px (ancho de documento 375 = ancho visible), ni errores o advertencias de consola, ni pedidos fallidos.

## Cómo verlos

Los mockups cargan `design-system/tokens.css`, `frontend/src/styles.css` tal como está en la rama y `coberturas.css`, que es la propuesta del módulo `22-coberturas.css`; sus reglas marcadas "Solo mockups" no pasan al módulo. `mockup.js` arma el encabezado y la barra lateral según el rol de la página (`data-rol`), con el menú propuesto, y suma los íconos `credencial` y `cuenta`. `design-system/brand.js` pone el nombre de la marca.

Para evitar bloqueos de `file://`, serví la raíz del repo y abrí la página desde ahí:

```powershell
python -m http.server 8767 --bind 127.0.0.1
```

Después abrí, por ejemplo, `http://127.0.0.1:8767/openspec/changes/frontend-obras-sociales/mockups/config-obras-sociales.html`.

## Brief

- **Para quién**:
  - el administrador, que carga el catálogo de obras sociales, planes y nomenclador, y también afilia;
  - la recepción, que afilia al paciente al darlo de alta o cuando llega, sin ver datos clínicos (RN-CL-01);
  - el odontólogo, que solo mira la cobertura en la ficha;
  - el paciente, que consulta su cobertura en "Mi cuenta" (PA-21).
- **Qué tienen que poder hacer**: mantener el catálogo sin borrar nada; ver de un vistazo si el paciente tiene cobertura hoy o es particular; afiliar, corregir y cerrar una afiliación; entender por qué la API rechazó algo.
- **Tamaños**: 390 × 844 (botones a lo ancho, todo en una columna) y 1440 × 900 (acciones a la derecha de cada fila).
- **Restricciones**: tokens del design system; estados escritos y no solo por color; controles de 44 px; diálogos con el molde de `shared/ui/dialogo.ts`; datos ficticios del seed de la KB; ningún dato de cobertura en la dirección ni en el navegador.

## Pantallas

| Archivo | Qué muestra | Requisitos del spec `frontend-coberturas` |
|---|---|---|
| `config-obras-sociales.html` | Admin, Configuración → "Obras sociales": OSE con "Plan Base" y "Plan Integral" (inactivo), "Prepaga Prueba" con "Plan Oro", y el nomenclador N-0101, N-0201 y N-0301 (inactivo) | Pestaña "Obras sociales" solo para el administrador, Lista de obras sociales y planes, Nomenclador en la pestaña, Activar y desactivar sin borrar |
| `config-formularios.html` | Diálogos "Agregar obra social" (vacío, nombre vacío, `OBRA_SOCIAL_EXISTENTE`), "Agregar plan" y "Agregar código" (con prestación, `CODIGO_NOMENCLADOR_EXISTENTE` y descripción larga) | Alta y edición de obras sociales, de planes y de códigos, Errores del catálogo |
| `config-estados.html` | Cargando, falla con "Reintentar", catálogo y nomenclador vacíos, obra social inactiva con su plan activo, falla de una acción, obra social sin planes | Errores del catálogo, Lista de obras sociales y planes, Activar y desactivar sin borrar |
| `cobertura-ficha.html` | Admin en la ficha de Paciente Uno: "Cobertura" arriba del odontograma, vigente (OSE, Plan Base, OSE-000001) e historial con "Corregir" y "Cerrar afiliación" | Sección "Cobertura" en la ficha, Cobertura vigente o particular, Historial de afiliaciones |
| `cobertura-odontologo.html` | Odontólogo: la misma sección en solo lectura | Solo lectura para el odontólogo |
| `cobertura-formularios.html` | "Afiliar" recién abierto, validación previa, `VIGENCIA_SUPERPUESTA`, `AFILIADO_DUPLICADO`, `PLAN_INACTIVO`, "Corregir" con plan inactivo y "Cerrar afiliación" (normal y con fecha inválida) | Afiliar a un paciente, Validar la afiliación antes de enviarla, Errores de la API al afiliar o corregir, Corregir una afiliación, Cerrar una afiliación |
| `cobertura-estados.html` | Particular sin afiliaciones, particular con una finalizada (Paciente Dos), cambio de cobertura con una "Próxima", cargando, falla con "Reintentar" y `AFILIACION_CERRADA` | Cobertura vigente o particular, Historial de afiliaciones, Cerrar una afiliación, Sección "Cobertura" en la ficha |
| `coberturas-recepcion.html` | Recepción en `#/coberturas`: buscador y, con Paciente Dos elegido, solo su cobertura. Menú con "Coberturas" | Página "Coberturas" sin datos clínicos; delta de `frontend-navegacion` |
| `mi-cuenta-cobertura.html` | Paciente en `#/mi-cuenta`: "Mi cobertura" vigente (Paciente Uno), particular (Paciente Dos) y falla. Menú con "Mi cuenta" | Mi cobertura del paciente; deltas de `frontend-cuenta-paciente` y `frontend-navegacion` |

Capturas: `previews/<archivo>-1440.png` y `previews/<archivo>-390.png` para cada archivo de la tabla.

## Decisiones visuales

- **Planes anidados** debajo de su obra social, con sangría y un borde de acento a la izquierda: se lee a qué obra social pertenece cada plan sin repetir el nombre.
- **Tipo como etiqueta** ("Obra social" / "Prepaga") y la sigla en monoespaciada, igual que los códigos del nomenclador y el número de afiliado.
- **Tarjeta "Cobertura de hoy"**: fondo de acento con el ícono de credencial si hay cobertura; gris neutro con "Se atiende como particular" si no. Siempre se lee por el texto.
- **Etiquetas del historial**: "Vigente" (verde), "Próxima" (info) y "Finalizada" (gris), escritas.
- **Acciones en el historial**, no en la tarjeta: "Corregir" en todas, "Cerrar afiliación" solo en las que no tienen fecha de fin. Así no hay dos botones para lo mismo.
- **Errores**: los de negocio arriba del diálogo (`role="alert"`), los de un campo debajo de él; el número de afiliado nunca se repite en un mensaje.
