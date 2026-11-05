# Case studies

Five worked cases from the engine, each with what the fraudsters did, what the engine saw, the score, what a human investigator should do next, and what could fool it. All people, phones and amounts are **synthetic** and every output is reproducible with the seed shown.

| # | Case | Patterns | Lesson |
|---|---|---|---|
| [1](01-mule-ring.md) | The ten-second mule ring | All four | Judge the network, not the payment |
| [2](02-device-farm-return-swap.md) | One phone, five buyers, swapped laptops | Device farm + serial check | The phone is the link |
| [3](03-family-phone-false-alarm.md) | The family that shares a phone | None (correctly) | Knowing what *not* to flag |
| [4](04-busy-minute-false-alarm.md) | The busy minute | Fan-in (wrongly, then fixed) | A failure we found by benchmarking |
| [5](05-slow-ring-evasion.md) | The ring that slows down | Missed by design | The honest limit of fixed rules |

A score is an **investigation lead, not proof of a crime**. Cases 1 and 2 are patterns widely described in public fraud awareness material; they are not accusations against any real person or company.
