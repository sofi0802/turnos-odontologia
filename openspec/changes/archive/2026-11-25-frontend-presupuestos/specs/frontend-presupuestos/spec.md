# Spec Delta

## Purpose

Definir las pantallas de presupuestos del frontend sobre la API de C-19:
- el odontólogo los arma, emite, acepta y rechaza en la ficha;
- el administrador y la recepción los consultan;
- el paciente ve los suyos en "Mi cuenta".

Ninguna pieza, cara ni importe sale de la pantalla.

Datos ficticios de los escenarios (salvo que el escenario diga otra cosa):

- **Usuarios:** `admin` (`ADMIN`), `recepcion` (`RECEPCIONISTA`), `ana.ejemplo` (`ODONTOLOGO`, "Dra. Ana Ejemplo"), `paciente.uno` y `paciente.dos` (`PACIENTE`).
- **Aranceles y cobertura:** los de `frontend-aranceles`. "Plan Base" cubre `N-0101` al 100 % y `N-0201` al 50 %; "Plan Oro" cubre `N-0101`, `N-0201` y `N-0301` al 80 %.
- **Pacientes:**
  - "Paciente Uno" (id 1), con "Plan Base" vigente, `OSE-000001`;
  - "Paciente Dos" (id 2), particular.
- **Odontograma de Paciente Uno:** la pieza 16 cara `O` con `CARIES` y la pieza 36 con `A_TRATAR`.
- **Presupuesto 41 de Paciente Uno:**
  - emitido el 2026-11-24 por "Dra. Ana Ejemplo", vence el 2026-12-24, `PENDIENTE`, con "Plan Base";
  - ítems: "Consulta o control" (`N-0101`, 15000.00, 100 %, cobertura 15000.00, copago 0.00) y "Limpieza (profilaxis)" (`N-0201`, 25000.00, 50 %, cobertura 12500.00, copago 12500.00);
  - totales: precio 40000.00, cobertura 27500.00 y copago 12500.00.
- **Presupuesto 40 de Paciente Uno:** emitido el 2026-10-01, vence el 2026-10-31, `PENDIENTE` y vencido, con un ítem "Extracción simple" en la pieza 36.
- **Hoy:** 2026-11-24 en `America/Argentina/Buenos_Aires`.

## ADDED Requirements

### Requirement: Sección "Presupuestos" en la ficha
En la ficha de `#/pacientes`, la aplicación SHALL mostrar la sección "Presupuestos" debajo de "Cobertura" y arriba del odontograma.
- Pide `GET /api/v1/pacientes/{id}/presupuestos` aparte del resto de la ficha.
- Si falla, MUST mostrar el error y "Reintentar" solo en la sección; el resto de la ficha se sigue viendo.
- Al cambiar de paciente, la sección MUST descartar las respuestas que lleguen tarde.

#### Scenario: La ficha muestra los presupuestos
- **GIVEN** una sesión de `ana.ejemplo` y la ficha de "Paciente Uno" abierta
- **WHEN** la API responde los presupuestos 41 y 40
- **THEN** la sección "Presupuestos" se ve debajo de "Cobertura" y arriba del odontograma

#### Scenario: Falla de los presupuestos en la ficha
- **GIVEN** una sesión de `ana.ejemplo` y la ficha de "Paciente Uno"
- **WHEN** `GET /api/v1/pacientes/1/presupuestos` responde `500`
- **THEN** la sección "Presupuestos" muestra un aviso `role="alert"` y "Reintentar"
- **AND** la cobertura, el odontograma y la ficha básica se siguen viendo

### Requirement: Lista de presupuestos del paciente
La sección SHALL listar los presupuestos en el orden de la API, cada uno con la fecha, el vencimiento, el estado escrito ("Pendiente", "Vencido" para un `PENDIENTE` con `vencido: true`, "Aceptado" o "Rechazado"), el profesional, la cobertura usada ("Obra Social Ejemplo · Plan Base · N.º OSE-000001") o "Particular", y los totales de precio, cobertura y copago en pesos ("$ 12.500,00"). Sin presupuestos, MUST decir "Este paciente no tiene presupuestos.".

#### Scenario: Presupuesto pendiente con obra social
- **GIVEN** el presupuesto 41
- **WHEN** se muestra la sección
- **THEN** se ven "Pendiente", "24/11/2026", "Vence el 24/12/2026", "Dra. Ana Ejemplo" y "Obra Social Ejemplo · Plan Base · N.º OSE-000001"
- **AND** los totales "$ 40.000,00", "$ 27.500,00" y "$ 12.500,00"

#### Scenario: Presupuesto vencido
- **GIVEN** el presupuesto 40
- **WHEN** se muestra la sección
- **THEN** su etiqueta dice "Vencido" y no "Pendiente"

#### Scenario: Presupuesto particular
- **GIVEN** un presupuesto de "Paciente Dos" marcado como particular
- **WHEN** se muestra la sección
- **THEN** su cobertura dice "Particular"

#### Scenario: Sin presupuestos
- **GIVEN** que `GET /api/v1/pacientes/2/presupuestos` responde una lista vacía
- **WHEN** se muestra la sección
- **THEN** se ve "Este paciente no tiene presupuestos."

### Requirement: Detalle de un presupuesto
"Ver detalle" SHALL mostrar los ítems con la prestación, el código del nomenclador o "Sin código", la pieza y la cara si las hay, la cantidad, el precio unitario, el porcentaje, el importe, la cobertura y el copago, y marcar el botón con `aria-expanded`.

#### Scenario: Detalle de los ítems
- **GIVEN** el presupuesto 41 en la sección
- **WHEN** elige "Ver detalle"
- **THEN** se ve "Limpieza (profilaxis)" con `N-0201`, cantidad 1, "50 %", "$ 25.000,00", "$ 12.500,00" y "$ 12.500,00"
- **AND** el botón tiene `aria-expanded="true"`

### Requirement: Armar un presupuesto
Con sesión `ODONTOLOGO`, "Nuevo presupuesto" SHALL abrir un diálogo que pide `GET /api/v1/aranceles`, `GET /api/v1/nomenclador` y la cobertura de hoy del paciente. Cada ítem tiene "Prestación" (solo las activas con precio), "Pieza" y "Cara" opcionales y "Cantidad" de 1 a 99, con 1 por defecto. "Agregar ítem" suma uno y "Quitar" lo saca; el diálogo MUST tener de 1 a 50 ítems.

#### Scenario: Abrir el armado
- **GIVEN** una sesión de `ana.ejemplo` en la ficha de "Paciente Uno"
- **WHEN** elige "Nuevo presupuesto"
- **THEN** el diálogo tiene un ítem vacío con "Cantidad" en 1
- **AND** el selector "Prestación" ofrece "Consulta o control", "Limpieza (profilaxis)", "Tratamiento de conducto" y "Extracción simple"

#### Scenario: Prestación sin precio
- **GIVEN** que "Tratamiento de conducto" no tiene precio
- **WHEN** se abre "Nuevo presupuesto"
- **THEN** "Prestación" no ofrece "Tratamiento de conducto"
- **AND** el diálogo dice "Las prestaciones sin precio no se pueden presupuestar."

#### Scenario: Agregar y quitar ítems
- **GIVEN** el diálogo con un ítem
- **WHEN** elige "Agregar ítem" y después "Quitar" en el primero
- **THEN** queda un solo ítem y el foco va a su "Prestación"

### Requirement: Código de cada ítem
Si la prestación de un ítem tiene más de un código activo, el ítem SHALL mostrar el selector "Código" sin ninguno elegido. Si tiene uno, MUST usarlo y mostrarlo; si no tiene ninguno, MUST mostrar "Sin código: no tiene cobertura".

#### Scenario: Prestación con varios códigos
- **GIVEN** que "Consulta o control" tiene los códigos activos `N-0101` y `N-0102`
- **WHEN** elige "Consulta o control" en un ítem
- **THEN** aparece el selector "Código" con `N-0101` y `N-0102`, sin ninguno elegido

### Requirement: Piezas sugeridas del odontograma
En el armado, "Agregar piezas sugeridas" SHALL pedir `GET /api/v1/pacientes/{id}/presupuestos/sugerencias` y sumar un ítem por cada pieza y cara sugerida, con la pieza y la cara cargadas, la prestación sin elegir y el estado del odontograma escrito ("Caries", "A tratar"). Un ítem vacío MUST reemplazarse por la primera sugerencia. Sin sugerencias, MUST decir "No hay caries ni piezas a tratar en el odontograma.".

#### Scenario: Sumar las sugeridas
- **GIVEN** el armado de "Paciente Uno" con un ítem vacío
- **WHEN** elige "Agregar piezas sugeridas"
- **THEN** el diálogo tiene dos ítems: pieza 16 cara `O` con "Caries" y pieza 36 sin cara con "A tratar", en ese orden
- **AND** ninguno tiene la prestación elegida

#### Scenario: Sin sugerencias
- **GIVEN** que la API responde una lista vacía de sugerencias
- **WHEN** elige "Agregar piezas sugeridas"
- **THEN** un `role="status"` dice "No hay caries ni piezas a tratar en el odontograma."

### Requirement: Estimado antes de emitir
Mientras arma, el diálogo SHALL mostrar en cada ítem completo y en los totales un estimado del importe, la cobertura y el copago, con la cobertura que se va a usar o "Particular" y el texto "Estimado: el importe final lo calcula el sistema al emitir.". Después de emitir, la sección MUST mostrar los valores que devuelve la API, no el estimado.

#### Scenario: Estimado de Paciente Uno
- **GIVEN** el armado de "Paciente Uno" con "Consulta o control" y "Limpieza (profilaxis)", cantidad 1
- **WHEN** se muestran los ítems
- **THEN** "Consulta o control" estima cobertura "$ 15.000,00" y copago "$ 0,00"
- **AND** "Limpieza (profilaxis)" estima cobertura "$ 12.500,00" y copago "$ 12.500,00"
- **AND** los totales estimados son "$ 40.000,00", "$ 27.500,00" y "$ 12.500,00", con "Obra Social Ejemplo · Plan Base"

#### Scenario: Estimado de un paciente particular
- **GIVEN** el armado de "Paciente Dos" con "Consulta o control"
- **WHEN** se muestra el ítem
- **THEN** estima cobertura "$ 0,00" y copago "$ 15.000,00" y el estimado dice "Particular"

### Requirement: Cálculo del estimado
El estimado MUST calcularse como en RN-PR-03: importe = precio particular × cantidad; cobertura = importe × porcentaje / 100, redondeada a centavos *half up*; copago = importe − cobertura. El porcentaje es el del plan vigente hoy para el código del ítem, y 0 si el plan no lo cubre, si el ítem no tiene código o si el paciente es particular.

#### Scenario: Redondeo half up
- **GIVEN** que "Limpieza (profilaxis)" cuesta 25000.15 y "Plan Base" cubre `N-0201` al 50 %
- **WHEN** se arma un ítem de "Limpieza (profilaxis)" para "Paciente Uno"
- **THEN** estima cobertura "$ 12.500,08" y copago "$ 12.500,07"

#### Scenario: Cantidad mayor que uno
- **GIVEN** el armado de "Paciente Uno" con "Extracción simple"
- **WHEN** pone "Cantidad" en 2
- **THEN** estima importe "$ 60.000,00", cobertura "$ 0,00" y copago "$ 60.000,00", porque "Plan Base" no cubre `N-0301`

### Requirement: Validar el presupuesto antes de emitirlo
Antes de enviar, el diálogo MUST validar, en cada ítem:
- que haya prestación;
- que haya código si la prestación tiene varios;
- que la cantidad sea un entero de 1 a 99;
- que la pieza, si está, exista en la notación FDI;
- que la cara, si está, vaya con pieza y corresponda a esa pieza, con las reglas del odontograma.

Si algo falla, MUST NOT enviar nada, marcar cada campo con `aria-invalid` y su mensaje, y llevar el foco al primero con error.

#### Scenario: Ítem sin prestación
- **GIVEN** el armado con un ítem vacío
- **WHEN** elige "Emitir presupuesto"
- **THEN** el campo "Prestación" muestra "Elegí la prestación."
- **AND** no se envía ningún pedido y el foco queda en ese campo

#### Scenario: Falta el código
- **GIVEN** un ítem de "Consulta o control", con dos códigos activos, sin código elegido
- **WHEN** elige "Emitir presupuesto"
- **THEN** el campo "Código" muestra "Elegí el código del nomenclador."

#### Scenario: Pieza fuera de la notación FDI
- **GIVEN** un ítem de "Extracción simple"
- **WHEN** escribe la pieza 19 y elige "Emitir presupuesto"
- **THEN** el campo "Pieza" muestra "La pieza no existe en la notación FDI."

#### Scenario: Cara palatina en una pieza inferior
- **GIVEN** un ítem de "Consulta o control" en la pieza 36
- **WHEN** elige la cara `P` y "Emitir presupuesto"
- **THEN** el campo "Cara" muestra "Esa cara no corresponde a la pieza."

#### Scenario: Cara sin pieza
- **GIVEN** un ítem de "Consulta o control" sin pieza
- **WHEN** elige la cara `O` y "Emitir presupuesto"
- **THEN** el campo "Cara" muestra "Indicá la pieza para elegir una cara."

### Requirement: Emitir un presupuesto
"Emitir presupuesto" SHALL enviar `POST /api/v1/pacientes/{id}/presupuestos` con `{"items":[{"prestacionId", "nomencladorId", "pieza", "cara", "cantidad"}]}`.
- `nomencladorId`, `pieza` y `cara` van en `null` cuando no corresponden.
- El cuerpo MUST NOT llevar profesional, precios ni porcentajes.
- Al recibir `201`, cierra el diálogo, vuelve a pedir la lista, avisa "Presupuesto emitido." y abre el detalle del emitido con los valores de la respuesta.

#### Scenario: Emitir el presupuesto de Paciente Uno
- **GIVEN** el armado de "Paciente Uno" con "Consulta o control" y "Limpieza (profilaxis)"
- **WHEN** elige "Emitir presupuesto"
- **THEN** se envía `POST /api/v1/pacientes/1/presupuestos` con dos ítems, cada uno con su `prestacionId`, el `nomencladorId` del único código, `pieza` y `cara` en `null` y `cantidad` 1
- **AND** el cuerpo no tiene `profesionalId`, `precio` ni `porcentaje`
- **AND** se vuelve a pedir la lista y un `role="status"` dice "Presupuesto emitido."

#### Scenario: Ítem de una pieza sugerida
- **GIVEN** el ítem sugerido de la pieza 16 cara `O` con "Consulta o control"
- **WHEN** se emite
- **THEN** ese ítem va con `"pieza":16` y `"cara":"O"`

#### Scenario: Los valores finales son los de la API
- **GIVEN** que el estimado de un ítem decía copago "$ 12.500,00"
- **WHEN** el `201` devuelve copago 13000.00 para ese ítem porque cambió el arancel
- **THEN** el detalle del presupuesto emitido muestra "$ 13.000,00"

### Requirement: Errores de la API al emitir
Un rechazo MUST dejar el diálogo abierto con los ítems y mostrar el mensaje:
- arriba del diálogo: `ARANCEL_INEXISTENTE`, `PRESTACION_INACTIVA`, `PRESTACION_INEXISTENTE`, `CODIGO_NOMENCLADOR_REQUERIDO` y `CODIGO_NOMENCLADOR_INVALIDO`;
- junto al campo del ítem que indica la API: `DATOS_INVALIDOS` con un campo `items[i].<campo>`.

Ningún mensaje MUST repetir piezas, caras ni importes.

#### Scenario: Prestación sin arancel al emitir
- **GIVEN** el armado de "Paciente Uno" con "Limpieza (profilaxis)"
- **WHEN** emite y la API responde `409` con `ARANCEL_INEXISTENTE`
- **THEN** el diálogo sigue abierto y dice "Una de las prestaciones ya no tiene precio. Revisá los aranceles."

#### Scenario: Código requerido
- **WHEN** emite y la API responde `422` con `CODIGO_NOMENCLADOR_REQUERIDO`
- **THEN** el diálogo dice "Una de las prestaciones tiene varios códigos: elegí uno."

#### Scenario: Campo de un ítem
- **GIVEN** el armado con dos ítems
- **WHEN** emite y la API responde `422` con `DATOS_INVALIDOS` y el campo `items[1].pieza`
- **THEN** el campo "Pieza" del segundo ítem queda con `aria-invalid="true"` y "La pieza no existe en la notación FDI."

### Requirement: Aceptar o rechazar un presupuesto
Con sesión `ODONTOLOGO`, un presupuesto "Pendiente" SHALL ofrecer "Aceptar" y "Rechazar", uno "Vencido" solo "Rechazar", y uno "Aceptado" o "Rechazado" ninguna acción. Cada acción pide confirmación, porque no se puede deshacer, y envía `POST .../presupuestos/{presupuestoId}/aceptar` o `/rechazar`; al terminar bien recarga la lista y avisa "Presupuesto aceptado." o "Presupuesto rechazado.".

#### Scenario: Aceptar un presupuesto pendiente
- **GIVEN** una sesión de `ana.ejemplo` con el presupuesto 41
- **WHEN** elige "Aceptar" y confirma
- **THEN** se envía `POST /api/v1/pacientes/1/presupuestos/41/aceptar`
- **AND** se vuelve a pedir la lista y un `role="status"` dice "Presupuesto aceptado."

#### Scenario: Cancelar la confirmación
- **GIVEN** el diálogo "¿Aceptar el presupuesto?" abierto
- **WHEN** elige "Cancelar"
- **THEN** no se envía ningún pedido y el foco vuelve al botón "Aceptar"

#### Scenario: Un vencido solo se rechaza
- **GIVEN** el presupuesto 40, vencido
- **WHEN** se muestra la sección
- **THEN** ofrece "Rechazar" y no "Aceptar"

### Requirement: Rechazos al aceptar o rechazar
Con `409` `PRESUPUESTO_VENCIDO` o `PRESUPUESTO_CERRADO`, la aplicación MUST cerrar el diálogo de confirmación, recargar la lista y mostrar el mensaje del código.

#### Scenario: Venció mientras estaba abierto
- **GIVEN** el diálogo para aceptar el presupuesto 41
- **WHEN** confirma y la API responde `409` con `PRESUPUESTO_VENCIDO`
- **THEN** el diálogo se cierra, se recarga la lista y se ve "El presupuesto venció: ya no se puede aceptar. Emití uno nuevo."

#### Scenario: Otra persona ya lo cerró
- **WHEN** confirma el rechazo y la API responde `409` con `PRESUPUESTO_CERRADO`
- **THEN** el diálogo se cierra, se recarga la lista y se ve "Ese presupuesto ya estaba aceptado o rechazado."

### Requirement: Solo lectura para el administrador en la ficha
Con sesión `ADMIN`, la sección de la ficha SHALL mostrar la lista y los detalles con el aviso "Solo lectura: los presupuestos los arma el odontólogo.". La sección MUST NOT ofrecer "Nuevo presupuesto", "Aceptar" ni "Rechazar", y MUST NOT pedir `/sugerencias`.

#### Scenario: El administrador ve los presupuestos
- **GIVEN** una sesión de `admin` y la ficha de "Paciente Uno"
- **WHEN** se muestra la sección "Presupuestos"
- **THEN** se ve el presupuesto 41 con sus totales
- **AND** no hay botones "Nuevo presupuesto", "Aceptar" ni "Rechazar"

### Requirement: Presupuestos en la página "Coberturas"
En `#/coberturas`, con un paciente elegido, la página SHALL mostrar debajo de "Cobertura" la sección "Presupuestos" en solo lectura, sobre `GET /api/v1/pacientes/{id}/presupuestos`.
- La sección MUST NOT pedir `/sugerencias` ni ninguna ruta clínica.
- Con sesión `RECEPCIONISTA` MUST NOT mostrar pieza ni cara aunque la respuesta las trajera.

#### Scenario: La recepción ve los presupuestos sin pieza ni cara
- **GIVEN** una sesión de `recepcion` con "Paciente Uno" elegido en `#/coberturas`
- **WHEN** abre el detalle del presupuesto 40
- **THEN** se ve "Extracción simple" con su importe, cobertura y copago
- **AND** la sección no contiene "Pieza" ni "36"

#### Scenario: Ningún pedido clínico en Coberturas
- **GIVEN** una sesión de `recepcion` con "Paciente Uno" elegido en `#/coberturas`
- **WHEN** se muestran la cobertura y los presupuestos
- **THEN** no se envía ningún pedido a `/ficha`, `/odontograma`, `/periodontograma`, `/imagenes`, `/historial` ni `/sugerencias`

### Requirement: Mis presupuestos del paciente
Con sesión `PACIENTE`, "Mi cuenta" SHALL mostrar "Mis presupuestos" debajo de "Mi cobertura", con la respuesta de `GET /api/v1/paciente/presupuestos`: fecha, vencimiento, estado escrito, cobertura usada o "Particular", totales y el detalle de los ítems con la pieza y la cara si las hay. La sección MUST ser de solo lectura y MUST NOT mandar ids en el pedido.

#### Scenario: El paciente ve sus presupuestos
- **GIVEN** una sesión de `paciente.uno` con los presupuestos 41 y 40
- **WHEN** se abre `#/mi-cuenta`
- **THEN** se pide `GET /api/v1/paciente/presupuestos` sin parámetros
- **AND** "Mis presupuestos" muestra el 41 "Pendiente" con copago "$ 12.500,00" y el 40 "Vencido", sin botones para aceptarlos ni rechazarlos

### Requirement: Mis presupuestos vacíos o con error
Sin presupuestos, "Mis presupuestos" MUST decir "Todavía no tenés presupuestos.". Si el pedido falla, MUST mostrar el error y "Reintentar" sin afectar "Mi cobertura".

#### Scenario: Paciente sin presupuestos
- **GIVEN** una sesión de `paciente.dos` sin presupuestos
- **WHEN** se abre `#/mi-cuenta`
- **THEN** "Mis presupuestos" dice "Todavía no tenés presupuestos."

#### Scenario: Falla de Mis presupuestos
- **GIVEN** una sesión de `paciente.uno`
- **WHEN** `GET /api/v1/paciente/presupuestos` responde `500`
- **THEN** "Mis presupuestos" muestra un aviso `role="alert"` y "Reintentar"
- **AND** "Mi cobertura" se sigue viendo

### Requirement: Presupuestos accesibles
Cada sección y diálogo de presupuestos SHALL tener un título `h2` o `h3`, una etiqueta visible en cada control, los estados escritos y no solo por color, los importes con "$" y su rótulo, los avisos en `role="status"` o `role="alert"` y controles de al menos 44 px. Los controles de cada ítem MUST nombrar su número para los lectores de pantalla.

#### Scenario: Etiquetas de los ítems
- **GIVEN** el armado con dos ítems
- **WHEN** se inspeccionan los controles del segundo
- **THEN** sus nombres accesibles son "Prestación del ítem 2", "Pieza del ítem 2", "Cara del ítem 2" y "Cantidad del ítem 2"

#### Scenario: Importes con rótulo
- **GIVEN** el presupuesto 41 en la sección
- **WHEN** se inspeccionan los totales
- **THEN** cada importe va con su rótulo: "Precio", "Cobertura" y "Copago"

### Requirement: Foco y pantallas chicas
Al cerrar un diálogo de presupuestos, el foco MUST volver al botón que lo abrió. Con 390 px de ancho la página MUST NOT desplazarse en horizontal: los ítems del armado y del detalle se apilan como tarjetas.

#### Scenario: Foco al cerrar el armado
- **GIVEN** el diálogo "Nuevo presupuesto" abierto desde su botón
- **WHEN** elige "Cancelar"
- **THEN** el foco vuelve al botón "Nuevo presupuesto"

#### Scenario: Sin desplazamiento horizontal a 390 px
- **GIVEN** el armado de "Paciente Uno" con tres ítems a 390 px de ancho
- **WHEN** se muestra el diálogo
- **THEN** el ancho del documento no supera el ancho visible

### Requirement: Presupuestos solo en la pantalla
Las piezas, las caras, los importes, la cobertura y el paciente elegido MUST NOT quedar en la dirección, en `localStorage`, en `sessionStorage` ni en la consola (RN-PR-07, reglas duras 1 y 6). Un borrador sin emitir MUST perderse al cerrar el diálogo o recargar la página.

#### Scenario: Nada fuera de la pantalla al emitir
- **GIVEN** una sesión de `ana.ejemplo` en la ficha de "Paciente Uno"
- **WHEN** emite un presupuesto con la pieza 16 cara `O`
- **THEN** la dirección sigue siendo `#/pacientes`
- **AND** `localStorage` y `sessionStorage` no tienen datos nuevos y la consola no recibió mensajes con "16", "12500" ni "Paciente Uno"

#### Scenario: Nada fuera de la pantalla en Mis presupuestos
- **GIVEN** una sesión de `paciente.uno`
- **WHEN** abre el detalle del presupuesto 40 en "Mi cuenta"
- **THEN** la dirección sigue siendo `#/mi-cuenta` y la consola no recibió mensajes con "36" ni "30000"
