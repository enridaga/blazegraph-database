# ISSUE-2: Compatibility acceptance test suite

## Context
Upgrading the JDK, Jetty and third-party libraries must not change what users
observe. The existing unit tests are slow, internal, and part of the reactor
being changed, so they cannot act as an independent compatibility oracle.

## Requirements
1. A dedicated folder at the repository root (`/acceptance-tests`) holding a
   standalone Maven project that targets Java 21 and runs independently of the
   Blazegraph reactor.
2. Tests have the sole purpose of checking compatibility: they pin observable
   behaviour of the baseline build (Java 21 + Jetty 9.4) and must keep passing
   after each upgrade.
3. Cover the core features: server/web application, SPARQL query and update
   protocol, REST API, multi-tenancy, full-text search, inference, RDR, quads,
   federation, transactions, backup, bulk loading, persistence, the Java remote
   client API and embedded use.
4. Each test is documented in a README, grouped by topic.

## Changes
- `acceptance-tests/`: JUnit 5 suite (80 tests) against the executable jar,
  black-box over `java.net.http` plus Java API tests on the same jar.
- `acceptance-tests/README.md`: design, how to run, test catalogue by topic,
  pinned baseline behaviours.

## Evaluation
- 80/80 tests pass on the baseline (Java 21, Jetty 9.4.12), on two consecutive runs.
- New requirements found while writing the tests are recorded in the README
  ("Baseline behaviours worth knowing"); they are pre-existing and out of scope
  (no functional changes).
