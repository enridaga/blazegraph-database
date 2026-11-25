# ISSUE-5: Verify WAR deployability

## Context
Found while evaluating ISSUE-3: the acceptance suite exercised the executable
jar and the Java APIs, but not the WAR, which is a primary distribution and
whose `WEB-INF/lib` changed with the Jetty 12 upgrade (Jetty client, ee8
modules, servlet API 4.0 jar).

## Requirements
1. The acceptance suite deploys `blazegraph-war-<version>.war` in an
   independent `javax.servlet` container (Apache Tomcat 9), in its own JVM,
   without adding the container to the test class path.
2. Cover start-up, static resources, SPARQL query/update, multi-tenancy,
   REST/multipart, and the bundled HTTP client (federated `SERVICE`).
3. The tests pass on the baseline WAR (Jetty 9.4) and on the current WAR.

## Changes
- `acceptance-tests/src/test/launcher/TomcatWarLauncher.java`, `BlazegraphServer.startWar`,
  `war/WarDeploymentTest` (WAR-01..06); Tomcat jars copied to `target/tomcat`.

## Evaluation
- 6/6 pass on the baseline WAR (Jetty 9.4.12) and on the Jetty 12 WAR.
