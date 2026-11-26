# ADR-0010: Payments through provider adapters; companies are merchant of record; commission invoiced

**Status:** Accepted (26 November 2026)
**Source:** R-21, R-38, R-41 in [../engineering/recommendations.md](../engineering/recommendations.md)

## Context
Zambia market: MTN MoMo and Airtel Money first. Mobile money is asynchronous (PIN prompt, delayed callbacks). TransitHub must not hold passenger money.

## Decision
- BP-09 defines a `PaymentProvider` port, with adapters for MTN MoMo and Airtel Money first and cash at ticket offices. Card payments come later.
- **Merchant of record is the transport company**: marketplace payments go into the company's own merchant account. TransitHub records commission per marketplace booking (rate snapshot from the commercial agreement) and **invoices** companies.
- Initiating a payment is never treated as paid. A booking is confirmed and a ticket issued only on payment confirmed by BP-09.
- Late payment after a reservation expired: re-seat if a comparable seat is free, otherwise refund automatically and notify by SMS. Reservations are extended while a payment is in flight.
- Provider callbacks are verified and idempotent; status is polled if no callback arrives; reconciliation runs daily.

## Consequences
No payment licence is needed for TransitHub itself. Commission collection depends on invoicing and enforcement through subscription status (to be specified in BP-01.04 / BP-09).
