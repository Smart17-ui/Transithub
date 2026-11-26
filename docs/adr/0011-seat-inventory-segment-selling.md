# ADR-0011: Seat inventory with segment selling, enforced by the database

**Status:** Accepted (26 November 2026)
**Source:** R-20, R-42 in [../engineering/recommendations.md](../engineering/recommendations.md)

## Context
Approved: one authoritative availability across all channels. Decided: a seat can be sold for separate segments of the same trip.

## Decision
- A seat allocation covers a **range of stop sequences** on a trip (from-stop index, to-stop index).
- PostgreSQL enforces no overlap with an **exclusion constraint** (`btree_gist`) on `(trip_id, seat_id, int4range(from_seq, to_seq))` for active reservations and bookings.
- No "check, then insert" logic in Java.
- Fares are versioned tables owned by BP-03, per origin–destination pair. A booking stores the fare actually charged.

## Consequences
Double-selling is impossible across channels and segments. Search and fare tables are more complex, which is accepted for the extra revenue.
