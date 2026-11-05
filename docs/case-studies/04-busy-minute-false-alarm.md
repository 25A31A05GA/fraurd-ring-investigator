# Case 4: The busy minute (a failure we found)

**Reproduce the original problem:** run the benchmark at `--rate 40` on the first version of the fan-in rule.

## What happened
The first version of the fan-in rule was: *4+ different people pay one account, ₹50,000+ in total, within a minute.* At 5 events/s it was perfect. At 40 events/s **precision dropped to 84.9%**: 60 false cases over 20 runs, **every one fan-in only**.

```
FAN_IN: 16 different people paid Rahul Kumar a combined INR 51,089 within a minute.
FAN_IN: 14 different people paid Naveen Iyer a combined INR 52,226 within a minute.
```

Sixteen ordinary payments of about ₹3,200 sum to ₹51,000. That is not a ring.

## The diagnosis
All the false cases were low-score (15, LOW), so a real analyst queue would have buried them rather than escalated them. But the rule was **volume-sensitive**: the busier the system, the more coincidences.

## The fix
Mule inflows are *large tickets* (₹12,000–₹48,000 each in the simulation). Added a minimum **average payment of ₹8,000**. Added the regression test `fan-in ignores many SMALL payments`. Re-ran 100 seeds at 5 and 40 events/s: **100% precision, 0 false alarms**.

## Lesson
A threshold that works at one traffic level can fail at another. Always benchmark at more than one volume. In production, thresholds should scale with traffic or be learned from data. A fixed ₹8,000 is a prototype fix, not a final one.
