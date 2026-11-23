# frontend-navegacion Specification

## Purpose
Definir la estructura navegable de la aplicación web del consultorio: qué páginas existen, cómo se pasa de una a otra sin recargar y cómo se adapta la navegación a celulares y computadoras. Las pantallas con datos (agenda, reserva, configuración) se suman en changes posteriores sobre esta estructura.

## Requirements

### Requirement: Páginas disponibles
La aplicación SHALL ofrecer tres páginas en el menú, cada una con su dirección: Agenda (`#/agenda`), Reserva (`#/reserva`) y Configuración (`#/configuracion`), y además la página de ingreso (`#/ingreso`), que no figura en el menú. La página Agenda muestra la agenda del personal; Reserva y Configuración siguen mostrando solo su título.

#### Scenario: Abrir la página de reserva por su dirección
- **WHEN** se abre la aplicación en la dirección `#/reserva`
- **THEN** se muestra la página con el título "Reserva"

#### Scenario: Abrir la página de ingreso por su dirección
- **WHEN** se abre la aplicación en la dirección `#/ingreso`
- **THEN** se muestra la página con el título "Ingresar"
- **AND** el menú no tiene un enlace a "Ingresar"

### Requirement: Página inicial y direcciones desconocidas
Sin dirección, o con una que no corresponde a ninguna página, la aplicación SHALL mostrar la página Agenda y actualizar la dirección a `#/agenda`.

#### Scenario: Abrir la aplicación sin dirección
- **WHEN** se abre la aplicación sin dirección de página
- **THEN** se muestra la página "Agenda"
- **AND** la dirección pasa a ser `#/agenda`

#### Scenario: Dirección desconocida
- **WHEN** se abre la aplicación en la dirección `#/no-existe`
- **THEN** se muestra la página "Agenda"
- **AND** la dirección pasa a ser `#/agenda`

### Requirement: Navegación sin recargar
El menú SHALL tener un enlace por página. Al elegir uno, la aplicación MUST mostrar esa página sin recargar el documento y marcar el enlace actual como la página activa para tecnologías de asistencia (`aria-current="page"`). Los botones Atrás y Adelante del navegador SHALL funcionar.

#### Scenario: Ir de Agenda a Configuración
- **GIVEN** la página "Agenda" abierta
- **WHEN** se elige el enlace "Configuración" del menú
- **THEN** se muestra la página "Configuración" sin recargar el documento
- **AND** solo el enlace "Configuración" queda marcado como página activa

#### Scenario: Volver con el botón Atrás
- **GIVEN** que se navegó de "Agenda" a "Configuración"
- **WHEN** se usa el botón Atrás del navegador
- **THEN** se muestra otra vez la página "Agenda"

### Requirement: Menú adaptable a pantallas chicas
En pantallas de menos de 768 px de ancho, el menú SHALL quedar oculto detrás de un botón que lo abre y lo cierra e informa su estado (`aria-expanded`); al elegir una página, el menú se cierra. Desde 768 px el menú SHALL verse siempre, sin botón. En ningún ancho desde 320 px la página MUST NOT tener desplazamiento horizontal.

#### Scenario: Abrir el menú en el celular
- **GIVEN** la aplicación abierta en una pantalla de 390 × 844 px con el menú cerrado
- **WHEN** se toca el botón del menú
- **THEN** se ven los tres enlaces
- **AND** el botón informa que el menú está expandido

#### Scenario: Elegir una página en el celular
- **GIVEN** el menú abierto en una pantalla de 390 × 844 px
- **WHEN** se elige el enlace "Reserva"
- **THEN** se muestra la página "Reserva"
- **AND** el menú se cierra

#### Scenario: Menú siempre visible en la computadora
- **WHEN** se abre la aplicación en una pantalla de 1440 × 900 px
- **THEN** los tres enlaces se ven sin tocar ningún botón
- **AND** la página no tiene desplazamiento horizontal
