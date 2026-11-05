# Contributing

Ideas are welcome. This is a learning and portfolio project built on synthetic data.

1. Fork, then create a branch: `git checkout -b feature/my-idea`
2. Run `./scripts/build.sh` and make sure all tests pass
3. Add a test for any new detection rule (see `src/test/java/com/frauddetect/TestRunner.java`)
4. Open a pull request and describe the fraud pattern, why it matters, and how you avoid false alarms

Good first issues: a new pattern (promo abuse, refund abuse), a CSV importer, a refund-abuse rule, a JUnit 5 migration.

**Rules:** no real personal data, no real people's names or numbers, and no accusing real individuals or companies.
