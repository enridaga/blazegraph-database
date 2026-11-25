# ISSUE-3: Upgrade Jetty 9.4 to Jetty 12.1

## Context
Blazegraph embedded Jetty 9.4.12 (2018), which is end of life and affected by
many published vulnerabilities (e.g. CVE-2019-10241, CVE-2019-10247,
CVE-2020-27216, CVE-2020-27218, CVE-2021-28165, CVE-2021-28169,
CVE-2021-34428, CVE-2022-2047, CVE-2023-26048, CVE-2023-26049, CVE-2023-36478,
CVE-2023-40167, CVE-2024-8184). Jetty is used both as the embedded HTTP server
(NanoSparqlServer, executable jar, deb/rpm/tgz) and as the HTTP client
(`RemoteRepositoryManager`, federated `SERVICE`, Solr full-text search, HA
load balancer proxy).

## Requirements
1. Use the latest Jetty release (12.1.15).
2. Keep the `javax.servlet` web application unchanged: run it on Jetty 12's
   **ee8** environment (Servlet 4.0, `javax.*`), so the WAR stays deployable in
   `javax` servlet containers and no `jakarta` migration is needed.
3. Keep observable behaviour identical: context paths, redirects,
   `X-Frame-Options`, form size limit, ports and system properties
   (`jetty.port`, `jetty.home`, `jetty.overrideWebXml`, `jettyXml`, ...).
4. Keep the public Java APIs source compatible where Jetty types do not leak;
   where they do, map 1:1 to the Jetty 12 type.

## Changes
- POMs: Jetty 12.1.15; `jetty-webapp`, `jetty-servlet`, `jetty-proxy` →
  `jetty-ee8-*`; `jetty-continuation` (removed upstream) dropped; servlet API
  → `org.eclipse.jetty.toolchain:jetty-servlet-api:4.0.9` (what ee8 is built
  on); SLF4J 2.0.17 (Jetty 12 logs through SLF4J 2), binding
  `slf4j-reload4j` routed to the existing log4j 1.2, pinned in the parent.
- Client: `org.eclipse.jetty.client.api.*`/`util.*` → `org.eclipse.jetty.client.*`;
  `ContentProvider` → `Request.Content` (`EntityContentProvider`,
  `MultipartContentProvider`); `SslContextFactory` → `SslContextFactory.Client`;
  `FutureResponseListener` → `CompletableResponseListener`; `B64Code` →
  `java.util.Base64`.
- Server: `WebAppContext` → `org.eclipse.jetty.ee8.webapp.WebAppContext`,
  located through its `CoreContextHandler`; `jetty.xml` read with
  `ResourceFactory.newMemoryResource`; `ProxyServlet` → ee8 `ProxyServlet`.
- `jetty.xml` (executable jars, deb/rpm/tgz, test fixtures):
  `HandlerCollection` → `Handler$Sequence`; `MovedContextHandler` properties
  `newContextURL`/`permanent`/`discardPathInfo` → `redirectURI`/`statusCode`/
  `discardPathInContext`; `HeaderPatternRule` `name`/`value` →
  `headerName`/`headerValue`; `Server` arg `threadpool` → `threadPool`;
  duplicate XML id `moved` made unique (Jetty 12 validates the DTD);
  removed options that no longer exist (`rewriteRequestURI`,
  `rewritePathInfo`, `MsieSslRule` for MSIE 5/6); relative `descriptor`
  removed: Jetty 12 resolves it against the working directory, Jetty 9 fell
  back to the war's `WEB-INF/web.xml`, which is Jetty 12's default.
- Tests of the main build ported (`MockRemoteRepository`, multipart content,
  `ConcurrentHashSet` → `ConcurrentHashMap.newKeySet()`).

## Behaviour notes
- The `Server` header now reads `Jetty(12.1.15)`.
- Client TLS with a configured keystore
  (`DefaultHttpClientFactory.SSLKeystorePath`) now verifies the server host
  name (Jetty client default since 9.4.15) — a security fix. The default
  trust-all mode is unchanged, but Jetty 12 now logs a `WARN` when such a
  trust-all client starts (it flags the insecure configuration).
- Request paths with ambiguous empty segments (e.g. `/namespace//properties`,
  produced by an empty namespace name) are rejected by Jetty 12 with `400`
  instead of reaching the servlet (`404`): Jetty's URI-compliance hardening
  (CVE-2021-34429). Kept as is; `TestMultiTenancyAPI.test_create03` accepts it.

## Evaluation
- Default and Deployment builds succeed on JDK 21.
- Acceptance tests: 80/80 pass against the Jetty 12 executable jar
  (same suite, unchanged, that passed on Jetty 9.4).
- Main build unit suite `com.bigdata.rdf.sail.webapp.TestAll` (2197 runs over
  the triples/sids/quads modes): 16 distinct failing tests, none caused by
  Jetty 12: 14 in `TestFederatedQuery` (test resource lookup broken on
  Java 9+, fixed under ISSUE-1), `testServiceNodeBindings` (needs internet
  access to dbpedia.org) and `test_create03` (see behaviour notes).
- New requirement found: WAR deployment in a servlet container is not covered
  by the acceptance tests → ISSUE-5.
