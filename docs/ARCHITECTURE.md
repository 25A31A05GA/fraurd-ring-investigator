# Architecture

One idea, three layers: **simulate → detect → act**. Everything is plain Java 17, the JDK's built-in `HttpServer`, and no third-party libraries.

## Data flow

```
World.step()                       DetectionEngine.run()                CaseBook.raise()
  generateTraffic()  ─┐              sharedDevices()  ─┐                   de-duplicate by key
  spawnRing()         ├─► ledger ─►  fanInAndCashout()├─► alerts ───────► merge into a case if it
  spawnFarm()        ─┘  (Tx list)   cycles()  (DFS)  ─┘                   shares any entity
                                       markFoundRings()  (evaluation only)  rescore() = Σ distinct weights, cap 100
```

## Classes

| Class | Responsibility |
|---|---|
| `World` | Synthetic population (100 phones, 20 shared by a family), background traffic, planted rings and farms, the transaction ledger |
| `Entity`, `Tx`, `Alert`, `Ring` | Small data types. `Ring` is **ground truth** and the detector never reads it |
| `DetectionEngine` | The four pattern checks over sliding windows (60 s for fan-in and cash-out, 120 s for loops) |
| `CaseBook`, `FraudCase`, `PatternType` | De-duplication, merging, scoring (weights 35/25/25/15), status and audit trail |
| `Performance` | Recall, precision, false alarms, detection lag against the planted ground truth, with a 30-second grace window |
| `DeviceFarmLab`, `Ledger` | Farm discovery, hang/release, signup gate, serial verifier, evidence report |
| `ApiServer`, `Json` | REST API and a tiny JSON writer (records, maps, lists) |
| `Engine`, `Main` | Wiring, plus the `demo / bench / farm / serve` commands |

## Detection rules

| Pattern | Rule | Complexity |
|---|---|---|
| Shared device | ≥ 3 person accounts sent money from one phone **and** ≥ 2 distinct surnames | O(devices) |
| Fan-in | ≥ 4 different people paid one account, total ≥ ₹50,000, average ≥ ₹8,000, within 60 s | O(window) |
| Rapid cash-out | ≥ 80% of the last-30-second inflow (≥ ₹50,000) leaves within −1…+15 s | O(window) |
| Circular transfer | Directed graph of person-to-person transfers ≥ ₹10,000 in the last 120 s; DFS from each node, path length ≤ 4, cycle length ≥ 3, canonical start (smallest id) to avoid duplicate cycles | O(V · d⁴) worst case, tiny in practice |

Merchants and the ATM sink are never counted as "people", so shopping is ignored.

## Design decisions

- **Explainability over accuracy-at-any-cost.** Fixed points per pattern and a visible reason for each. An investigator can defend a score.
- **Cases, not alerts.** Alerts that share an entity merge, so one ring is one case, not twelve notifications.
- **Hidden ground truth.** The simulator keeps the labels; the detector cannot see them. Evaluation is separate from detection.
- **Determinism.** One `java.util.Random(seed)` per world, so any result can be reproduced exactly.
- **No dependencies.** Anyone with a JDK can build and run it in one command.
- **Lock per engine.** The API holds a single monitor around tick and requests: simple and correct for a demo; a real system would use immutable snapshots or a queue.

## Where it would grow

Replace `World` with a CSV/Kafka reader; persist `CaseBook` in a database; add learned scoring beside the rules; stream the case list to the dashboard over server-sent events.
