# frontend-sistema-visual Specification

## Purpose

Definir las reglas visuales comunes de toda la aplicación que se pueden comprobar: el color y el contraste del encabezado y el pie, los estados de los botones, el movimiento, el uso exclusivo de las variables del design system, la organización de los estilos y la uniformidad de los diálogos. Los valores salen de `design-system/tokens.css`.

## Requirements

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
Los estilos SHALL estar repartidos en archivos CSS por tema (base, componentes y una por pantalla o grupo de pantallas) que `styles.css` importa en un orden fijo (el de las reglas del archivo único anterior, para no alterar la cascada), sin cambiar el aspecto ni el comportamiento de ninguna pantalla.

#### Scenario: Módulos importados en orden
- **WHEN** se lee `styles.css`
- **THEN** solo contiene importaciones, primero la de los tokens y después la de cada módulo en un orden fijo
- **AND** cada módulo importado existe, no está vacío y no hay módulos sin importar

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

### Requirement: Agenda con el color de cada turno
Cada turno de la agenda del personal SHALL tener un borde izquierdo de 4 px con el color de su estado (`--ds-turno-*`; un sobreturno lleva el suyo), y la vista semanal SHALL acomodar los días en columnas que se ajustan al ancho, sin huecos cuando unos días tienen más turnos que otros, y manteniendo los días en orden de lunes a domingo.

#### Scenario: El borde del turno según su estado
- **GIVEN** la agenda de un día con un turno reservado, uno confirmado y un sobreturno
- **WHEN** se muestra
- **THEN** cada turno tiene la clase de su estado, y el borde izquierdo usa el color del estado correspondiente

#### Scenario: Semana con días de distinta cantidad de turnos
- **GIVEN** la vista semanal con un día con tres turnos y otros con uno o ninguno
- **WHEN** se muestra en una pantalla de 1440 px
- **THEN** los siete días están en orden de lunes a domingo
- **AND** cada día queda debajo del anterior de su columna, sin dejar un hueco del alto del día más largo

### Requirement: Pantallas del paciente con fondo suave y jerarquía de títulos
Las pantallas del paciente (portada, Reservar turno, Mi cuenta, Mis turnos y Confirmar asistencia) SHALL verse sobre un fondo suave (`--ds-superficie`) con tarjetas blancas, y sus títulos SHALL seguir una jerarquía común: `h1` en `--ds-texto-xl`, `h2` en `--ds-texto-lg` y `h3` en `--ds-texto-base`. Su columna de contenido SHALL medir como máximo 44 rem.

#### Scenario: Fondo suave en las pantallas del paciente
- **GIVEN** cada pantalla del paciente abierta
- **WHEN** se mira el contenedor de la página
- **THEN** lleva el fondo suave

#### Scenario: Jerarquía de títulos
- **WHEN** se leen las reglas de los títulos
- **THEN** `h1`, `h2` y `h3` usan los tamaños `--ds-texto-xl`, `--ds-texto-lg` y `--ds-texto-base`, con el interlineado de títulos

### Requirement: Pantallas del personal con fondo suave y tarjeta de turno uniforme
Las pantallas del personal (agenda y Configuración) SHALL llevar el mismo fondo suave que las del paciente; en la agenda, cada turno MUST ser una tarjeta blanca con el nombre del paciente como texto propio (sin reutilizar la tarjeta de "Mis turnos"), y "Cancelar turno" MUST ser un botón de peligro y "Reprogramar" uno secundario.

#### Scenario: Fondo suave en las pantallas del personal
- **GIVEN** una persona del personal con la agenda o Configuración abiertas
- **WHEN** se mira el contenedor de la página
- **THEN** lleva el fondo suave

#### Scenario: Tarjeta del turno y sus acciones
- **GIVEN** la agenda con un turno
- **WHEN** se mira su tarjeta
- **THEN** el paciente está en `.turno-persona` y no en `.turno-paciente`
- **AND** "Cancelar turno" tiene la clase de botón de peligro y "Reprogramar" la de botón secundario

### Requirement: Selector de fecha de la aplicación
Los campos de fecha SHALL usar el calendario de la aplicación en un desplegable, no el selector nativo del navegador; el valor MUST seguir en el campo `AAAA-MM-DD` y el campo opcional MUST poder borrarse.

#### Scenario: Elegir un día con el calendario
- **GIVEN** un campo de fecha con día mínimo
- **WHEN** se abre el calendario y se toca un día
- **THEN** el campo toma ese día, avisa el cambio y el calendario se cierra
- **AND** los días anteriores al mínimo no se pueden elegir

#### Scenario: Fecha opcional
- **GIVEN** un campo de fecha opcional con un día elegido
- **WHEN** se toca "Borrar" en el calendario
- **THEN** el campo queda vacío y el botón muestra "Elegí un día"

### Requirement: Agenda semanal en grilla horaria
La vista semanal SHALL ser una grilla de siete columnas fijas con una escala horaria compartida, y cada turno MUST ubicarse según su inicio y su duración. Dos o más turnos a la misma hora MUST agruparse en un bloque que los lista. Cada turno MUST ser una tarjeta compacta con el mismo contenido siempre (el horario y un nombre), cuyo detalle, con sus acciones y los mismos permisos de la lista del día, se abre en un diálogo.

#### Scenario: Siete columnas fijas con encabezados
- **GIVEN** la agenda de una semana con turnos en días distintos
- **WHEN** se abre la vista semanal
- **THEN** hay siete columnas, de lunes a domingo, cada una con su encabezado
- **AND** un día sin turnos queda en blanco, sin carteles

#### Scenario: Escala horaria compartida y posición según la hora y la duración
- **GIVEN** un turno de 10:00 a 10:45
- **WHEN** se dibuja la semana
- **THEN** la escala va de 08:00 a 18:00 y el turno se ubica a 120 minutos del comienzo con 45 minutos de alto
- **AND** la escala se extiende si algún turno queda fuera de ese horario

#### Scenario: Dos turnos a la misma hora se agrupan en un bloque
- **GIVEN** dos turnos del mismo día que se superponen, de profesionales y sillones distintos
- **WHEN** se dibuja la semana
- **THEN** se ve un solo bloque "2 turnos" que cubre desde el primer inicio hasta el último fin, y al abrirlo se listan con sus acciones

#### Scenario: Todas las tarjetas muestran lo mismo
- **GIVEN** turnos de 15, 30 y 120 minutos
- **WHEN** se dibuja la semana
- **THEN** todas las tarjetas tienen el mismo contenido: el horario y un nombre

#### Scenario: Nombre de la tarjeta según quién mira
- **GIVEN** un turno en la agenda de la recepción o la administración, y el mismo turno en la agenda de un odontólogo
- **WHEN** se dibuja la semana
- **THEN** la tarjeta de la recepción muestra el horario y el profesional, y la del odontólogo, el horario y el paciente
- **AND** en los dos casos el nombre accesible lleva el detalle completo y el estado por escrito

#### Scenario: El detalle del turno ofrece las acciones y cierra antes de ejecutarlas
- **GIVEN** un turno que se puede cancelar y reprogramar
- **WHEN** se toca su tarjeta y después "Cancelar turno"
- **THEN** el detalle se cierra y se ejecuta la acción de cancelar
- **AND** un turno atendido no ofrece cancelar ni reprogramar

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
