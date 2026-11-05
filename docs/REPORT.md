# Project Report: Fraud Ring Investigator

**Author:** [YOUR NAME] · **Date:** November 2026 · **Status:** v1.0, synthetic-data prototype

---

## 1. Summary

Fraud rings hide in plain sight. Each payment looks normal; the pattern across accounts, phones and time does not. This project builds a Java engine that reads payments as a stream, links accounts through shared phones and money flows, and opens an explainable case with a 0–100 risk score. It also has a device-farm lab for e-commerce return fraud, a REST API, and an interactive dashboard that shows the same logic to non-programmers.

**Headline result:** on 100 random simulations (about 162,000 transactions, 1,629 planted fraud groups) the engine caught 100% of them with 0 false-alarm cases, in about 3.8 simulated seconds on average. At 8× the traffic (40 events/s) the result held. This is a self-made benchmark and must be read that way (Section 7).

**Most useful finding:** the first version looked perfect at low traffic and **dropped to 85% precision when traffic got busy**. Benchmarking at several volumes, not just one, exposed it (attempt 3 below).

## 2. The problem

In India, UPI and IMPS make money move in seconds. Mule accounts (accounts lent or opened for criminals) take advantage: victims pay in, money loops through several accounts, and it is withdrawn before anyone can react. Per-transaction rules ("flag anything above ₹50,000") miss rings, because each hop is small and ordinary.

On the e-commerce side, one phone can host many accounts that order high-value electronics and return swapped parts. Each account looks like a normal buyer; the *phone* is the link.

**Core idea:** stop judging payments one by one; judge the *network*.

## 3. Inspiration

> ✏️ **Edit this section with your own story.** Recruiters connect most with a real reason, and only you know it. Prompts: Did you or someone you know receive a "wrong number, please send it back" or fake-KYC call? Did a news report about mule accounts or cyber fraud in your state catch your attention? Which course, paper or video first showed you graph thinking? Write 3–4 honest sentences here.

Public sources that shaped the design (all freely available; read them and cite what you actually use):

- India's national cybercrime reporting portal and the **1930** helpline, which frame how victims report UPI fraud
- Public RBI and NPCI material on digital-payment fraud and mule accounts
- Introductory graph-analytics material on connected components and cycle detection, the maths behind "shared phone" and "money loop"
- Widely reported e-commerce abuse patterns (return item-swap, refund abuse, account farms), written up in the dashboard's *Fraud playbook* as general patterns, never as accusations against any person or company

## 4. Approach

1. **Model the world.** Accounts, phones, payments, cash-out points, merchants.
2. **Make a fair test.** A simulator creates normal payments (70% shopping, 30% person-to-person, 20 families sharing a phone) and plants labelled rings and device farms the engine is never told about.
3. **Detect four patterns.** Shared device, fan-in, rapid cash-out, circular transfers.
4. **Merge into cases.** Alerts that touch the same account or phone become one case.
5. **Explain the score.** Each pattern adds fixed points and every point is shown with its evidence.
6. **Act.** Hang a device (freeze all its accounts), gate new signups, verify returned serial numbers, export evidence.
7. **Measure honestly.** Recall (groups caught), precision (cases that were real), speed, and a zero-fraud control run.

## 5. Attempts, failures and fixes

An honest log. Entries 1–7 are from building and hardening the Java version. Rows marked ✏️ are for you to complete from your own earlier work on the dashboard (v1 → v3 and beyond).

| # | What we tried | What went wrong | What we changed |
|---|---|---|---|
| 1 | Build with Maven and JUnit | The build environment could not download Maven plugins, so the build failed before any code ran | Made the project **dependency-free**: plain `javac` and a small custom test runner. A `pom.xml` is still provided |
| 2 | Two separate Java engines ended up in the repo (a small first one and a richer second one) | Duplicate code, two sets of docs, confusing for a reader | Kept the richer engine (4 patterns + device lab + API), archived the first |
| 3 | Benchmark at 5 events/s showed 100% precision, then repeated at **40 events/s** | **Precision fell to 84.9%.** 60 false cases over 20 runs, all *fan-in only*. In a busy minute, 14–16 ordinary people paying a few thousand rupees each add up to ₹50,000 | Added a minimum average ticket (₹8,000): mule inflows are large tickets, busy-minute noise is small. Re-ran 100 seeds at both rates: **100% precision, 0 false alarms**. Added a regression test |
| 4 | First full-simulation test over 10 seeds | **Recall 94% on seed 3.** The "missed" ring had been planted 1 second before the run ended | Not a detector bug, a measurement bug. Groups younger than 30 s at measurement time are not yet "due". Same lesson the earlier engine taught |
| 5 | Smoke-tested the live REST API with `curl` | Performance, gate and serial results came back as Java `toString()` text, not JSON. **My test had passed anyway** because it only checked that the word "recall" appeared | Added record-to-JSON serialisation and tightened the tests to check exact JSON keys |
| 6 | Looked at the dashboard's own *Detection performance* screen | It showed **50% precision, 2 false alarms**. The "false alarms" were the planted device farms, which the dashboard's scorer did not treat as real fraud | Counted farm accounts as ground truth in the dashboard scorer. Now 100% / 0 |
| 7 | Captured the network-graph screenshot in a headless browser | The graph was blank because the graph library is loaded from a CDN that the sandbox blocks | Served a local copy of the same library version for the screenshot only. The published page is unchanged |
| ✏️ | *(Your dashboard iterations: layout, scoring, bugs, redesigns)* | | |
| ✏️ | *(Add your own count, e.g. "v1 → v3 took N attempts, N failed builds")* | | |

**Lessons**
- Test at more than one traffic level. A rule that is safe at low volume can fail when it gets busy (attempt 3).
- Check whether a "failure" is in the thing measured or in how it is measured (attempts 4 and 6).
- A passing test is only as good as its assertion: `contains("recall")` passed while the JSON was wrong (attempt 5).
- Fewer dependencies mean fewer ways for a build to break and an easier first run for a recruiter.
- Publish limitations yourself; do not leave them for a reviewer to find.

## 6. Results

| Test | Result |
|---|---|
| Unit and integration tests | **26 / 26 pass**: each pattern, each false-positive guard, score maths, case merging, audit trail, determinism, device lab, JSON, REST API |
| Control run | 20 seeds of normal traffic with **nothing planted → 0 cases** |
| Benchmark, 100 seeds × 300 s at 5 events/s | 161,733 events; 1,629 planted; **100% recall; 100% precision; 0 false alarms; 3.79 s average detection** |
| Benchmark, 100 seeds × 300 s at 40 events/s | 1,211,677 events; 1,618 planted; **100% / 100% / 0**; 3.79 s average detection |
| Speed | About 38,000 events/s at low volume and about 112,000 events/s at high volume on the build machine (includes JVM warm-up) |

## 7. Limitations (please read)

1. **The benchmark is self-confirming.** The simulator plants exactly the patterns the detector hunts. 100% here does **not** predict real-world accuracy.
2. **Hand-set thresholds** (₹10,000, 4 senders, 80%, ₹8,000) are guesses, and attempt 3 shows how brittle fixed thresholds can be. Real data would need tuning.
3. **Adaptive criminals.** Rings can slow down, split amounts or rotate phones to dodge fixed rules.
4. **Legitimate look-alikes.** Shared office phones, community collections and wedding gifts can look like fan-in or sharing. A human reviewer is essential.
5. **Privacy and fairness.** A score should never be the only reason to freeze an account.
6. The Java engine covers four payment patterns plus the device-farm lab; refund abuse appears in the dashboard only.
7. **Synthetic return ledgers.** Serial-swap data is generated deterministically, not observed.

## 8. What would make it real

- Test on a labelled, privacy-safe dataset and report precision/recall with confidence intervals
- Add features: account age, velocity, geography, device change
- Combine rules with graph machine learning and compare
- Add an analyst feedback loop to learn from confirmed and rejected cases
- Persist cases in a database and add an alert queue

## 9. Reproduce

```bash
./scripts/run.sh                                                      # build + 26 tests + demo
java -jar build/fraud-ring-investigator.jar bench --runs 100 --rate 5
java -jar build/fraud-ring-investigator.jar bench --runs 100 --rate 40
```

The numbers above come from these commands with seeds 1–100. The same seed always gives the same result.
