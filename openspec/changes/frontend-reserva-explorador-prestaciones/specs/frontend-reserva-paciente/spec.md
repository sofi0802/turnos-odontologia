# Spec Delta

## MODIFIED Requirements

### Requirement: Elección de la prestación
El paso 1 SHALL cargar las prestaciones de `GET /api/v1/public/prestaciones` y ofrecerlas en un selector desplegable con buscador, con la etiqueta "Prestación". Cada opción MUST mostrar el nombre y la duración de la prestación ("Consulta · 30 minutos"). Si no hay prestaciones o el pedido falla, MUST mostrar "No pudimos cargar las prestaciones. Probá de nuevo en unos minutos." sin el selector ni el botón "Continuar".

#### Scenario: Prestaciones a elegir
- **GIVEN** la página `#/reserva` abierta sin sesión
- **WHEN** la API de prestaciones responde "Consulta" con 30 minutos y se abre el selector "Prestación"
- **THEN** el selector ofrece la opción "Consulta · 30 minutos"

## ADDED Requirements

### Requirement: Buscar la prestación
El selector SHALL filtrar sus opciones mientras se escribe, sin pedir nada a la API, y mostrar solo las prestaciones cuyo nombre contiene el texto, sin distinguir mayúsculas ni tildes. Con el campo vacío SHALL mostrar todas. Si ninguna coincide, MUST mostrar "No hay prestaciones que coincidan con «<texto>»." en lugar de la lista.

#### Scenario: Buscar por parte del nombre
- **GIVEN** la API de prestaciones responde "Consulta", "Limpieza dental" y "Extracción", y el selector está abierto
- **WHEN** se escribe "limp"
- **THEN** el selector ofrece solo "Limpieza dental · 45 minutos"

#### Scenario: Buscar sin mayúsculas ni tildes
- **GIVEN** la misma lista de prestaciones y el selector abierto
- **WHEN** se escribe "EXTRACCION"
- **THEN** el selector ofrece "Extracción"

#### Scenario: Sin coincidencias
- **GIVEN** el selector abierto con las mismas prestaciones
- **WHEN** se escribe "xyz"
- **THEN** no se ofrece ninguna opción
- **AND** se ve el mensaje "No hay prestaciones que coincidan con «xyz»."

#### Scenario: Borrar la búsqueda muestra todas
- **GIVEN** el selector con "limp" escrito
- **WHEN** se borra el texto
- **THEN** el selector ofrece otra vez las tres prestaciones

### Requirement: Elegir la prestación con el teclado y con el toque
El selector SHALL permitir elegir una prestación tocándola, o con el teclado: las flechas mueven la opción activa, "Enter" la elige y "Escape" cierra la lista sin elegir. Al elegir, el campo MUST mostrar el nombre de la prestación, la lista se cierra y "Continuar" sigue con esa prestación. Cambiar el texto del campo después de elegir MUST quitar la elección.

#### Scenario: Elegir con el teclado
- **GIVEN** el selector enfocado con las prestaciones "Consulta", "Limpieza dental" y "Extracción"
- **WHEN** se oprime la flecha hacia abajo dos veces y "Enter"
- **THEN** el campo muestra "Limpieza dental"
- **AND** la lista se cierra

#### Scenario: Elegir con el toque
- **GIVEN** el selector abierto
- **WHEN** se toca la opción "Consulta · 30 minutos"
- **THEN** el campo muestra "Consulta"
- **AND** al elegir "Continuar" se muestra el paso "Elegí día y horario" de "Consulta"

#### Scenario: Cerrar sin elegir
- **GIVEN** el selector abierto
- **WHEN** se oprime "Escape"
- **THEN** la lista se cierra
- **AND** no hay ninguna prestación elegida

#### Scenario: Cambiar el texto quita la elección
- **GIVEN** "Consulta" elegida en el selector
- **WHEN** se escribe una letra más en el campo y se elige "Continuar"
- **THEN** se sigue en el paso 1 con el mensaje "Elegí una prestación."

#### Scenario: Lo que se pide en el paso 2 no cambia
- **GIVEN** "Limpieza dental" (id 2) elegida en el selector
- **WHEN** se elige "Continuar"
- **THEN** se pide `GET /api/v1/public/profesionales` para la prestación 2
- **AND** el paso 2 muestra "Limpieza dental · 45 minutos"

### Requirement: Selector accesible
El selector SHALL ser un combobox accesible: el campo con rol `combobox` y la etiqueta "Prestación", `aria-expanded` según la lista esté abierta, `aria-controls` hacia la lista (rol `listbox`), opciones con rol `option` y `aria-activedescendant` en la opción activa. El resultado de la búsqueda MUST anunciarse a los lectores de pantalla.

#### Scenario: Roles y estados del selector
- **GIVEN** la página `#/reserva` con prestaciones cargadas
- **WHEN** se abre el selector y se mueve a la primera opción con la flecha hacia abajo
- **THEN** el campo "Prestación" tiene rol `combobox` con `aria-expanded="true"`
- **AND** `aria-activedescendant` apunta a una opción con rol `option` dentro del `listbox`

### Requirement: Selector cómodo en el celular
En una pantalla de 390 × 844 px, la lista del selector SHALL tener opciones de al menos 44 px de alto, quedar dentro del ancho de la pantalla y desplazarse por dentro cuando es larga, y la página MUST NOT tener desplazamiento horizontal.

#### Scenario: Selector en el celular
- **GIVEN** el paso 1 de `#/reserva` en una pantalla de 390 × 844 px con el selector abierto
- **WHEN** se miden la lista y la página
- **THEN** cada opción mide al menos 44 px de alto
- **AND** el ancho del documento no supera el de la pantalla
