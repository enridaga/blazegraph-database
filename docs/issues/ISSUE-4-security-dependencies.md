# ISSUE-4: Security updates of third-party dependencies

## Context
Besides Jetty (ISSUE-3), the distribution ships third-party libraries with
published vulnerabilities. The vulnerability database (OSV) could not be
queried from the build environment (network policy), so the list below is
based on published CVEs for the exact versions resolved in the executable jar
and the WAR (`mvn dependency:list`).

## Requirements
1. Upgrade vulnerable libraries to fixed versions that are drop-in compatible
   (same API, Java 8+), one library per commit.
2. No functional change. Libraries whose upgrade would change persisted data
   or query semantics are not upgraded, and are listed as residual risk.

## Changes
| Library | From | To | Fixes |
|---|---|---|---|
| log4j 1.2 → reload4j | log4j:log4j 1.2.17 | ch.qos.reload4j:reload4j 1.2.27 | CVE-2019-17571, CVE-2021-4104, CVE-2022-23302, CVE-2022-23305, CVE-2022-23307, CVE-2023-26464 |
| Commons FileUpload | 1.3.1 | 1.6.0 | CVE-2016-1000031, CVE-2016-3092, CVE-2023-24998, CVE-2025-48976 |
| Commons IO | 2.1 | 2.22.0 | CVE-2021-29425, CVE-2024-47554 |
| Jackson (core, databind, annotations) | 2.2.3 | 2.22.4 | CVE-2017-7525 and the later databind deserialization CVEs, CVE-2020-36518, CVE-2022-42003, CVE-2022-42004, CVE-2025-52999 |
| Jettison | 1.3.3 | 1.5.7 | CVE-2022-40149, CVE-2022-40150, CVE-2022-45685, CVE-2022-45693, CVE-2023-1436 |
| ZooKeeper (HA only) | 3.4.5 | 3.4.14, without Netty 3.2.2 | CVE-2018-8012, CVE-2019-0201; drops Netty 3.2.2 (CVE-2014-3488, CVE-2015-2156, CVE-2019-16869, ...) |
| JUnit (tests) | 4.11 | 4.13.2 | CVE-2020-15250 |

Notes:
- reload4j does not include log4j's `org.apache.log4j.jmx` package, which
  Blazegraph uses to expose log levels over JMX. It is replaced by an own
  `com.bigdata.jmx.Log4jHierarchyMBean` with the same object names and level
  attributes (`threshold`, `addLoggerMBean`, `name`, `priority`); log4j's
  JMX appender/layout management operations are not offered any more.
- `log4j:log4j` and the SLF4J 1.x `slf4j-log4j12` binding also arrived
  transitively (ZooKeeper, Rexster): excluded in the parent's
  `dependencyManagement`, so reload4j is the only `org.apache.log4j` on the
  class path.
- Since 2.20, `jackson-annotations` is versioned `major.minor` (2.22).

## Residual risk (not upgraded)
- **ICU4J 4.8**: collation keys are persisted in the lexicon indices; another
  ICU version can produce different keys for existing journals.
- **Lucene 5.5.0**: on-disk index format.
- **Sesame 2.7.12** and its `commons-httpclient` 3.1: no maintained 2.x line;
  moving to RDF4J is an API migration, not a security update.
- **Rexster/Blueprints 2.5.0** stack (Jersey 1.17, Codehaus Jackson 1.9.2,
  org.json): end of life with no fixed releases; only used by the Blueprints
  servlet.
- ZooKeeper 3.5+ is not used because it embeds Jetty 9.

## Evaluation
- All upgrades were built in a separate clone (full reactor including test
  modules, then jar and WAR) before being replayed here one library per
  commit; the replayed POMs and sources are identical to the tested ones.
- Acceptance tests: 87/87 pass (jar, WAR in Tomcat 9, Java APIs), including
  EMB-06 for JMX log management, which also passes on the baseline jar.
- `dependency:tree` of the Deployment reactor: no `log4j:log4j`,
  `slf4j-log4j12`, Netty 3 or second servlet API.
- The tarball distribution starts with `bin/blazegraph.sh`, serves the
  workbench, SPARQL update/query and the root redirect, and stops cleanly.
