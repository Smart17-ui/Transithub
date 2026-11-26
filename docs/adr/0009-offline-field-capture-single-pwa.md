# ADR-0009: One installable web app (PWA) now, including offline field capture; device trial first

**Status:** Accepted (26 November 2026)
**Source:** R-23, R-24, R-25 in [../engineering/recommendations.md](../engineering/recommendations.md)

## Context
Registration Officers capture registrations offline on **platform-issued, managed Android tablets**. One developer cannot maintain three frontend apps from day one.

## Decision
- One React + TypeScript + Vite **PWA** (`transithub-frontend`), with back-office and field screens shown by role.
- Offline: IndexedDB (via Dexie) for queued records and evidence, encrypted at rest with WebCrypto, and a background sync queue with idempotent resend.
- **A 1–2 week device trial** on the actual tablets comes before the field screens are built. It covers offline capture, camera evidence, storage limits, encryption, and sync after a reboot. If the PWA fails the trial, the field app switches to React Native / Expo.
- Tablets are managed by device-management software (lock, wipe, app updates).
- The public marketplace becomes a separate, server-rendered app at Release 3.

## Consequences
One codebase to start. The main risk (offline behaviour on real devices) is tested before investment.
