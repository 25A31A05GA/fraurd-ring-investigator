# Case 1: The ten-second mule ring

**Reproduce:** `java -jar build/fraud-ring-investigator.jar demo --seed 7 --seconds 120`

## What the fraudsters did
Five victims each send ₹12,000–₹48,000 over UPI to one new account (A). A passes ₹20,000 round a loop A → B → C → A, and about 97% of the total leaves through an ATM within roughly nine seconds. Six mule accounts, opened a few days earlier, share two phones.

## What each system sees
| Viewpoint | Verdict |
|---|---|
| A per-transaction rule ("flag above ₹50,000") | Nothing. Every payment is under the limit |
| The network engine | **Risk 100 / HIGH** in about 4 seconds |

## The engine's evidence (real output)
```
CASE-002  risk 100 HIGH   Suspected mule network: Lakshmi Chowdary
    +35 Shared device network: 3 unrelated accounts (Lakshmi Chowdary, Fatima Varma, Deepika Gupta) all send money from one phone, a iPhone 13.
    +25 Rapid cash-out: Lakshmi Chowdary received INR 144,892 and moved INR 140,546 out within seconds.
    +25 Circular transfers: Money moved in a loop: Lakshmi Chowdary -> Fatima Varma -> Deepika Gupta -> Lakshmi Chowdary.
    +15 Fan-in pattern: 4 different people paid Lakshmi Chowdary a combined INR 99,139 within a minute.
```

## What an investigator does next
1. Open the case, read the four reasons, check the money trail
2. Contact the victims (the case lists them) and advise reporting at cybercrime.gov.in / 1930
3. Ask the bank for KYC and account-opening details of the linked accounts
4. Hang the shared phones so no new account can start from them
5. Record notes and set status (`UNDER_INVESTIGATION`, later `RESOLVED`). Every change is in the audit trail

## What could fool it
Slower rings, split amounts under ₹10,000, or a different phone per account (see Case 5).
