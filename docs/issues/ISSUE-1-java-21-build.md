# ISSUE-1: Build and run on Java 21

## Context
The build targeted Java 1.7 (`maven-compiler-plugin` 3.3, `source/target 1.7`).
JDK 21 cannot compile for 1.7, so the project did not build on a supported LTS JDK.

## Requirements
1. The default (`Development`) reactor and the `Deployment` packaging modules
   compile on JDK 21 with `--release 21`.
2. Maven build plugins are upgraded to versions that work with JDK 21 and
   Maven 3.9; plugin versions are centralised as properties in the parent POM.
3. Source changes are limited to what JDK 21 requires (removed APIs, new
   keywords, new overloads). No functional changes.
4. The executable jar starts and serves SPARQL on JDK 21.
5. Jetty stays at 9.4 in this issue (see ISSUE-3).

## Changes
- Parent POM: `maven.compiler.release=21`, compiler 3.13.0, plugin version properties.
- `_` used as an identifier (keyword since Java 9) renamed in colt demos and a test.
- `javax.xml.bind.DatatypeConverter` (removed in Java 11) → `java.util.HexFormat`
  with identical semantics (case-insensitive parse, upper-case output).
- Unused `org.omg.CORBA` import removed (CORBA removed in Java 11).
- Test-only `javax.activation` added as a dependency of `bigdata-sails-test`.
- Ambiguous calls caused by new JDK overloads disambiguated in tests.
- Executable jar: `String.class.getResource("/war")` returns `null` on Java 9+
  (JPMS); replaced with `ClassLoader.getSystemResource("war")`, the exact
  Java 8 behaviour.
- `rpm-maven-plugin` 2.3.0 declared once as a plugin with extensions (Maven 3.9
  realm conflict).

## Evaluation
- `mvn -DskipTests install` and `mvn -P Deployment -DskipTests install` succeed
  on JDK 21 (the RPM step additionally needs `rpmbuild` on the host; on
  Ubuntu's rpm 4.18 it stops on pre-existing unpackaged-file checks,
  independent of Java).
- Acceptance tests: see [`/acceptance-tests`](../../acceptance-tests).
