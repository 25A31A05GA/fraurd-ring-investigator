# Case 3: The family that shares a phone

**Tested by:** `a family (same surname) sharing one phone is NOT flagged` and `normal traffic only → zero cases over 20 seeds`

## The situation
In the synthetic world, 20 of the 100 phones are shared by two relatives. In the test, four people with the same surname pay merchants from one phone. A naïve "≥ 3 accounts on a phone" rule would flag them.

## What the engine does
The shared-device rule needs **three or more accounts and at least two different surnames**. Same-surname households are treated as normal. Result: **no case**.

## Why this case is here
Detection that cries wolf is ignored. The project keeps a **control run** (20 seeds of purely normal traffic, nothing planted) and requires **zero cases**. It would fail the build if a rule started flagging normal life.

## What could fool it
Real life is messier: unrelated flatmates, a shop phone used by staff, relatives with different surnames. That is why the output is a lead for a human, never an automatic freeze.
