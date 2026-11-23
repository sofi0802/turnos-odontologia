# Spec Delta

## Purpose

Definir las reglas visuales comunes de toda la aplicación que se pueden comprobar: el color y el contraste del encabezado y el pie, los estados de los botones, el movimiento, el uso exclusivo de las variables del design system, la organización de los estilos y la uniformidad de los diálogos. Los valores salen de `design-system/tokens.css`.

## ADDED Requirements

### Requirement: Encabezado y pie con el color de la marca
El encabezado y el pie SHALL tener como fondo el acento del design system (`--ds-acento-500`) y texto en `--ds-acento-texto` (blanco), con un contraste de al menos 4,5:1. El pie SHALL mostrar el nombre de la aplicación y estar en todas las páginas, al final del contenido o de la pantalla.

#### Scenario: Contraste del encabezado y del pie
- **GIVEN** los valores de `--ds-acento-500` y `--ds-acento-texto` de `tokens.css`
- **WHEN** se calcula el contraste entre el texto y el fondo del encabezado y del pie
- **THEN** es de al menos 4,5:1 en ambos

#### Scenario: El pie en todas las páginas
- **WHEN** se abre cada una de las páginas de la aplicación
- **THEN** el pie está al final de la página, con el nombre "Turnos odontología"

### Requirement: Estados de los botones
Todo botón y enlace con aspecto de botón SHALL tener un estado al pasar el mouse (hover), un estado de foco visible con el teclado (`:focus-visible`, con el anillo de `--ds-foco-*`), un estado presionado y un estado deshabilitado, según su estilo: principal, secundario, peligro, fantasma, botón de horario (`.chip`) y opción elegible.

#### Scenario: Hover y presionado del botón principal
- **WHEN** se lee la regla del botón principal
- **THEN** tiene colores distintos para el estado normal, hover y presionado, todos con variables `--ds-*`

#### Scenario: Foco visible con el teclado
- **GIVEN** un botón de cada estilo
- **WHEN** se lee la regla de `:focus-visible` de cada uno
- **THEN** todos usan el anillo de foco del design system y ninguno lo elimina con `outline: none` sin reemplazarlo

#### Scenario: Botón deshabilitado
- **GIVEN** un botón deshabilitado
- **WHEN** se lee su regla
- **THEN** usa los colores de deshabilitado y el cursor "no permitido", sin efecto de hover

### Requirement: Movimiento reducido
Las transiciones SHALL usar las duraciones del design system y MUST anularse cuando la persona pide movimiento reducido (`prefers-reduced-motion: reduce`).

#### Scenario: Transiciones con movimiento reducido
- **WHEN** se leen las reglas con `transition`
- **THEN** usan `--ds-duracion` o `--ds-duracion-rapida`
- **AND** existe una regla de `prefers-reduced-motion: reduce` que las desactiva

### Requirement: Solo variables del design system
Los estilos y `tokens.css` SHALL usar únicamente variables `--ds-*`; MUST NOT quedar los alias `--color-*`, `--espacio-*` ni `--radio`, ni colores escritos a mano fuera de `tokens.css`.

#### Scenario: Sin alias ni colores sueltos
- **WHEN** se leen `tokens.css` y todos los módulos de estilos
- **THEN** no hay definiciones ni usos de `--color-*`, `--espacio-*` ni `--radio`
- **AND** no hay colores `#rrggbb` ni `rgb()` fuera de `tokens.css`

### Requirement: Estilos en módulos
Los estilos SHALL estar repartidos en archivos CSS por tema (base, componentes y una por pantalla o grupo de pantallas) que `styles.css` importa en un orden fijo, sin cambiar el aspecto ni el comportamiento de ninguna pantalla.

#### Scenario: Módulos importados en orden
- **WHEN** se lee `styles.css`
- **THEN** solo contiene importaciones de módulos, en el orden base, componentes y pantallas
- **AND** cada módulo importado existe

#### Scenario: Sin cambios de comportamiento
- **WHEN** se corren todos los tests de la aplicación después de partir los estilos
- **THEN** pasan los mismos tests que antes

### Requirement: Diálogos uniformes
Todos los diálogos SHALL poner el mensaje de error o de aviso en el mismo lugar (debajo del título y sobre los campos) y ordenar los botones igual: la acción que descarta o cierra ("Cancelar" o "Cerrar") a la izquierda y la acción principal ("Confirmar", "Guardar", "Crear") a la derecha en la computadora, y apiladas con la acción principal arriba en el celular.

#### Scenario: Orden de los botones en los diálogos
- **GIVEN** cada diálogo de la aplicación abierto
- **WHEN** se lee el orden de sus botones
- **THEN** el botón que descarta va antes que el botón principal en todos

#### Scenario: Lugar del aviso en los diálogos
- **GIVEN** un diálogo con un mensaje de error
- **WHEN** se muestra el mensaje
- **THEN** está debajo del título del diálogo y antes del primer campo, en todos los diálogos

### Requirement: Estados vacíos y de carga
Las listas sin elementos y las pantallas que están cargando datos SHALL mostrar un estado propio con una ilustración o un indicador SVG propio, un texto breve y, si corresponde, la acción siguiente; las ilustraciones decorativas MUST estar ocultas para los lectores de pantalla y la carga MUST anunciarse con `aria-busy`.

#### Scenario: Lista vacía con ilustración
- **GIVEN** una persona sin turnos
- **WHEN** abre "Mis turnos"
- **THEN** se ve un estado vacío con una ilustración con `aria-hidden="true"` y el texto del mensaje
- **AND** el estado ofrece el enlace "Reservar turno"

#### Scenario: Carga con indicador
- **GIVEN** una pantalla que espera una respuesta de la API
- **WHEN** se mira mientras carga
- **THEN** el contenedor tiene `aria-busy="true"` y se ve un indicador de carga
