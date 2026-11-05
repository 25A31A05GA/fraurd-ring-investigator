# REST API

Start it: `java -jar build/fraud-ring-investigator.jar serve --port 8080` (or `docker run -p 8080:8080 fraud-ring`). A background clock advances the simulation one second per real second. Responses are JSON with CORS enabled.

| Method & path | Returns |
|---|---|
| `GET /api/health` | `{"status":"ok","simulatedSeconds":N}` |
| `GET /api/summary` | transactions, value (INR), alerts, cases, active cases, blocked devices |
| `GET /api/cases` | all cases: id, title, score, level, status, patterns |
| `GET /api/cases/{id}` | one case with reasons, suspects, victims, notes, audit trail |
| `POST /api/cases/{id}/status?value=RESOLVED` | change status (`NEW`, `UNDER_INVESTIGATION`, `NEEDS_REVIEW`, `RESOLVED`, `FALSE_POSITIVE`); `400` on a bad value |
| `GET /api/performance` | planted, caught, cases, falseAlarms, recall, precision, avgLagSeconds |
| `GET /api/farms?threshold=4` | phones with ≥ N accounts: accounts, orders, returns, swaps, exposure |
| `POST /api/farms/{device}/hang` | freeze every account on the phone |
| `POST /api/farms/{device}/release` | undo |
| `GET /api/gate?device=DEV-3000&threshold=4` | `ALLOWED` / `REVIEW` / `BLOCKED` with a reason |
| `GET /api/serial?value=SN123456` | `GENUINE` / `SWAP` / `SENT_BUT_SWAPPED` / `UNKNOWN` |

```bash
curl localhost:8080/api/summary
curl localhost:8080/api/cases/CASE-001
curl -X POST "localhost:8080/api/cases/CASE-001/status?value=UNDER_INVESTIGATION"
curl "localhost:8080/api/gate?device=DEV-810"
```

All data is synthetic. The API has no authentication and is for demos only.
