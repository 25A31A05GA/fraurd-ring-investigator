# Case 2: One phone, five buyers, swapped laptops

**Reproduce:** `java -jar build/fraud-ring-investigator.jar farm --seed 5 --seconds 150`

## The pattern
Several shopping accounts, opened days earlier, are all operated from the **same phone**. Each buys high-value electronics, then returns the item claiming "not as described". The unit that comes back has a different serial number: an inside part was swapped, or the whole unit was replaced with a dummy. The refund is paid before anyone checks the serial.

## What the lab finds
```
DEVICE FARM EVIDENCE REPORT (demo data, all people and addresses are synthetic)
Device: DEV-810 (Samsung Galaxy M34)
Accounts on device: 5
Orders: 24, returns: 21, swapped returns: 19
Refund exposure: INR 787,550

ACC-1126  Aarav Khan  (opened 5 days ago)
   ORD-54613 Smartwatch: sent SN675563, returned SN816883 (display swapped)
   ORD-35104 Laptop 15": sent SN667524, returned SN959024 (battery swapped)
```

## What the toolkit does about it
| Tool | Use |
|---|---|
| `findFarms(threshold)` | List phones with ≥ N accounts, worst first |
| `verifySerial("SN...")` | Compare a returned serial with the dispatched one: `GENUINE`, `SWAP`, `SENT_BUT_SWAPPED`, `UNKNOWN` |
| `hang(device)` / `release(device)` | Freeze every account on a phone in one call, or undo it |
| `gate(device)` | New signup from this phone: `ALLOWED`, `REVIEW` (many accounts) or `BLOCKED` (on the hang list) |
| `evidenceReport(device)` | Plain-text file for platform trust-and-safety or police |

## Why it matters
Stopping the refund at the warehouse (compare serials) is cheaper than recovering money afterwards, and hanging the phone stops the *next* ten accounts.

## What could fool it
Farms that rotate phones, or use few accounts per phone. Real families and shared office devices also have several accounts, so `REVIEW` goes to a human, not an automatic ban.
