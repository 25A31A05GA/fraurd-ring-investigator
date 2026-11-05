# Recruiter & portfolio kit

Copy-paste material for your resume, LinkedIn and interviews. Everything below is true of this repository today. Edit the ✏️ parts so they are true of **you**.

## Resume bullets (pick 3)

- Built a **zero-dependency Java fraud-detection engine** that finds mule rings and device farms in a live payment stream using graph/cycle detection (DFS), fan-in and rapid cash-out rules; explainable 0–100 risk scores with per-pattern evidence.
- Designed a **hidden-ground-truth benchmark**: 100 seeded simulations, ~1,600 planted fraud groups, **100% recall, 0 false alarms**, ~3.8 s mean time-to-detect; **found and fixed a precision collapse (85%) that only appeared at 8× traffic**.
- Shipped **26 automated tests** (false-positive guards, control run with no fraud, determinism, REST API), CI on GitHub Actions, Docker image, and a live **GitHub Pages** investigation dashboard.
- Built a **REST API** on the JDK's built-in `HttpServer` (cases, status workflow with audit trail, device hang/gate, serial-swap verifier) without any framework.
- Wrote an honest project report with a failure log, limitations and case studies, including a documented **evasion the detector misses**.

## One-line pitch
"I built a Java engine that spots fraud rings from the *shape* of the money, tested it against hidden planted fraud, and wrote down every way it failed."

## LinkedIn post (draft)

> 🕵️ I just open-sourced **Fraud Ring Investigator**: a Java engine + live dashboard that catches UPI mule rings and e-commerce device farms.
>
> The problem: every payment in a ring looks normal. The *pattern* (shared phone → money loop → ATM cash-out in ~10 seconds) is the giveaway.
>
> What I'm proudest of is not the 100% score. It's what broke along the way:
> • At low traffic precision was perfect. At 8× traffic it fell to 85%, because 15 ordinary payments can add up to look like a ring. Fixed, tested, documented.
> • One "false alarm" was really my scorer ignoring the farm I had planted.
> • A test passed while my API returned the wrong JSON, because the assertion was too loose.
>
> 26 tests, zero dependencies, runs with just a JDK. Synthetic data only, and the README says plainly what it can't do.
>
> 🔗 [repo link] · 📊 [live demo link]
> #Java #FraudDetection #GraphAlgorithms #OpenSource #Fintech

## Interview questions this project prepares you for

| Question | Where your answer lives |
|---|---|
| "Walk me through the architecture." | `docs/ARCHITECTURE.md`: simulate → detect → act |
| "How do you detect a cycle in a transaction graph?" | DFS with a canonical start node, path ≤ 4, `DetectionEngine.cycles()` |
| "How did you avoid false positives?" | Family phones, merchants, small loops, small-ticket fan-in, plus a zero-fraud control run |
| "What went wrong?" | Report section 5: the 85% precision at high traffic, the measurement bugs |
| "Is 100% accuracy realistic?" | No. Self-confirming benchmark; see limitations and case study 5 |
| "How would you make it real?" | Labelled data, adaptive thresholds, graph ML, feedback loop, persistence |
| "Why no Maven or JUnit?" | The build environment could not download plugins; dependency-free also makes first-run easy |

## Make it find readers

1. Replace `YOUR-USERNAME` and `[YOUR NAME]` (README, LICENSE, REPORT, slides)
2. Add the repo **topics**: `fraud-detection`, `java`, `graph-algorithms`, `upi`, `fintech`, `cybersecurity`
3. Pin the repo on your GitHub profile
4. Put the live-demo link at the top of the GitHub "About" box
5. Post the LinkedIn draft with a screenshot from `docs/assets/`
6. Fill the ✏️ sections in the report with your real story. A genuine reason beats any template

## Honest framing
Say "synthetic-data prototype" every time. Interviewers trust candidates who state limits before they are asked.
