# Case 5: The ring that slows down (a known limit)

## The scenario
A smarter ring: payments from victims are spread over ten minutes instead of one, no single hop reaches ₹10,000, and each mule uses its own phone. The cash-out is split into small ATM withdrawals.

## What the current engine sees
| Rule | Why it does not fire |
|---|---|
| Shared device | Every account has its own phone |
| Fan-in | Inflow is spread beyond the 60-second window |
| Rapid cash-out | Outflow is split and delayed |
| Circular transfers | Hops are below ₹10,000 |

**Result: no alert.** This is a miss by design, and the project says so rather than hiding it.

## What would help
- Longer, multi-scale time windows and amount bands
- Features that survive evasion: account age, many accounts opened from one location, common beneficiary banks
- Graph community detection rather than hand-written rules
- A feedback loop from confirmed cases

## Why include it
Every detector has an evasion. Showing that you know yours is more credible than a perfect-looking benchmark. See the Limitations section of the [report](../REPORT.md).
