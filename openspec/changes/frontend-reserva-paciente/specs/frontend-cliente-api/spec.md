# Spec Delta

## ADDED Requirements

### Requirement: Horarios alternativos en un error de negocio
Ante un `409` cuyo `ProblemDetail` trae la propiedad `alternativas`, el error de negocio SHALL incluir esos horarios, cada uno con su `inicio` y su `fin` tal como los envió la API. MUST descartar los elementos que no tengan los dos valores, y sin la propiedad SHALL entregar una lista vacía.

#### Scenario: Rechazo con alternativas
- **WHEN** un pedido recibe `409` con el código `SIN_DISPONIBILIDAD` y `alternativas` con un horario de `2026-12-01T11:00:00-03:00` a `2026-12-01T11:30:00-03:00`
- **THEN** el cliente entrega un error de negocio con el código `SIN_DISPONIBILIDAD`
- **AND** sus alternativas tienen un horario con `inicio` `2026-12-01T11:00:00-03:00` y `fin` `2026-12-01T11:30:00-03:00`

#### Scenario: Rechazo sin alternativas
- **WHEN** un pedido recibe `409` con el código `SOLAPAMIENTO_PROFESIONAL` y sin la propiedad `alternativas`
- **THEN** el error de negocio tiene una lista de alternativas vacía

#### Scenario: Alternativas incompletas
- **WHEN** un pedido recibe `409` con `alternativas` que contiene un elemento sin `fin`
- **THEN** ese elemento no aparece en las alternativas del error
