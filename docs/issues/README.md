# Issues

GitHub Issues are disabled for this repository, so the high-level issues driving
the Java 21 / Jetty 12 modernisation are tracked here. Each commit references
its issue as `[ISSUE-n]` in the subject line.

Workflow per issue: (a) requirements → (b) implementation commits →
(c) evaluation with the acceptance tests in [`/acceptance-tests`](../../acceptance-tests).
Findings from (c) become new requirements (new issues or amendments).

| Id | Title | Status |
|----|-------|--------|
| [ISSUE-1](ISSUE-1-java-21-build.md) | Build and run on Java 21 | Done |
| [ISSUE-2](ISSUE-2-acceptance-tests.md) | Compatibility acceptance test suite | Done |
