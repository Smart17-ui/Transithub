# ADR-0002: Modular monolith with automatically enforced module boundaries

**Status:** Accepted (26 November 2026)
**Source:** R-05 in [../engineering/recommendations.md](../engineering/recommendations.md)

## Context
TransitHub has 11 business processes with strict data ownership ("each process owns its records"). A single developer cannot afford microservices, but an unstructured monolith would let modules write into each other's data.

## Decision
- One Spring Boot application, organised **package by module**: `com.transithub.<module>` (e.g. `onboarding`, `identity`, `governance`, `ticketing`, `finance`). Each module has a public API package; everything else is internal.
- Modules talk through their public application services or domain events, never through another module's repositories or tables.
- An **architecture test** (Spring Modulith verification or ArchUnit) fails the build on any boundary violation.
- The existing `company` package is renamed to `onboarding`.

## Consequences
Boundaries are enforced by the build rather than by discipline. A module can later be extracted into a service if ever needed, without first untangling it.
