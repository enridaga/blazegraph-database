# Blazegraph compatibility acceptance tests

A standalone test suite whose **only purpose is to check compatibility**: that
a Blazegraph build behaves, from the point of view of its users, exactly as the
reference build did. It was written to guard the Java 21 / Jetty 12 / security
dependency upgrades (see [`docs/issues`](../docs/issues)), and pins the
observable behaviour of the baseline (Java 21 + Jetty 9.4, commit `4625b28f`):
status codes, media types, redirects, headers, result formats and data
semantics. It does not test internals and does not replace the unit tests of
the main build.

## Design

- **Independent.** This directory is its own Maven project. It is not a module
  of the Blazegraph reactor, does not inherit the Blazegraph parent POM, and
  targets Java 21 (`maven.compiler.release=21`, JUnit 5).
- **Artifacts under test:** the executable jar
  (`blazegraph-jar-<version>.jar`), which is
  - launched in a separate JVM for the black-box HTTP tests
    (`com.blazegraph.acceptance.http`), and
  - put on the test classpath for the Java API tests
    (`com.blazegraph.acceptance.javaapi`, `com.blazegraph.acceptance.embedded`);

  and the WAR (`blazegraph-war-<version>.war`), deployed in Apache Tomcat 9,
  an independent `javax.servlet` container, started in its own JVM by
  `src/test/launcher/TomcatWarLauncher.java` with only Tomcat on its class
  path (`com.blazegraph.acceptance.war`). The WAR tests are skipped when the
  WAR has not been built.
- **No HTTP stack from the product.** HTTP tests use `java.net.http.HttpClient`,
  so they observe the server exactly as an external client does. No library
  besides JUnit is added to the classpath, so nothing shadows the classes
  bundled in the jar (a minimal JSON parser lives in `support/Json.java`).
- **Isolation.** HTTP tests share one server per run (`SharedServer`); each
  test creates its own namespace (`Namespaces`). Tests that need a dedicated
  server (restart) start their own.

## Running

Prerequisites: JDK 21 and Maven 3.9. Build the executable jar first, from the
repository root:

```bash
mvn -P Deployment -DskipTests install -pl blazegraph-jar,blazegraph-war -am
```

Then run the suite from this directory:

```bash
mvn test
```

Options:

| Property | Default | Meaning |
|---|---|---|
| `blazegraph.jar` | `../blazegraph-jar/target/blazegraph-jar-${blazegraph.version}.jar` | Executable jar under test |
| `blazegraph.war` | `../blazegraph-war/target/blazegraph-war-${blazegraph.version}.war` | WAR under test |
| `blazegraph.version` | `2.1.6-SNAPSHOT` | Version used to locate the default jar and WAR |
| `tomcat.version` | `9.0.124` | Tomcat used to deploy the WAR |
| `blazegraph.java` | the JVM running Maven | `java` executable used to launch the server |

Examples:

```bash
mvn test -Dblazegraph.jar=/path/to/blazegraph.jar
```

```bash
mvn test -Dgroups=http
```

JUnit tags: `http` (black-box over HTTP), `java-api` (remote Java client),
`embedded` (in-process database and server), `war` (WAR in Tomcat). Server logs are written to
`target/work/*/server.log`.

## Tests by topic

Test IDs appear in each test's `@DisplayName` and in the surefire report.

### Server and web application — `http/ServerWebAppTest`

| ID | Verifies |
|---|---|
| SRV-01 | `GET /blazegraph/status` returns 200 `text/html` with build version and query counters |
| SRV-02 | `/blazegraph/` serves the workbench (`html/index.html` welcome file) |
| SRV-03 | Static workbench resources (CSS, JS, PNG, ICO) are served with their media types |
| SRV-04 | `/` redirects with 301 to `/blazegraph/`, keeping the query string |
| SRV-05 | Legacy `/bigdata/...` paths redirect with 301 to `/blazegraph/...`, keeping path and query |
| SRV-06 | `/blazegraph` (no trailing slash) redirects to `/blazegraph/` |
| SRV-07 | `X-Frame-Options: SAMEORIGIN` is sent on all responses (rewrite rule in `jetty.xml`) |
| SRV-08 | Unknown resources return 404 |
| SRV-09 | `GET /blazegraph/counters` serves the performance counters page |
| SRV-10 | `status?showQueries` lists running queries as HTML, and as JSON with `Accept: application/json` |
| SRV-11 | A `Server` response header is sent (`jetty.send.server.version` defaults to true) |

### SPARQL query — `http/SparqlQueryTest`

| ID | Verifies |
|---|---|
| QRY-01 | SELECT with HTTP GET |
| QRY-02 | SELECT with HTTP POST, `application/x-www-form-urlencoded` |
| QRY-03 | SELECT with HTTP POST, `application/sparql-query` body |
| QRY-04 | SELECT result formats: SPARQL JSON, SPARQL XML (default), CSV, TSV (exact media types and content) |
| QRY-05 | ASK returns booleans in JSON and XML |
| QRY-06 | CONSTRUCT formats: RDF/XML (default), Turtle, N-Triples (`text/plain`), JSON-LD |
| QRY-07 | DESCRIBE returns the resource description |
| QRY-08 | Malformed query: 400 `text/plain` with `MalformedQueryException` |
| QRY-09 | Query on an unknown namespace: 404 with `DatasetNotFoundException` |
| QRY-10 | `GET /sparql` without a query returns the SPARQL service description (RDF/XML, Turtle) |
| QRY-11 | `explain` returns an HTML query plan |
| QRY-12 | Non-ASCII literals round-trip in UTF-8 (form encoding, JSON and CSV results) |
| QRY-13 | 4 KB queries are accepted with GET (request header size limit) |
| QRY-14 | Query engine semantics: FILTER, OPTIONAL, SUM/COUNT, GROUP BY/HAVING, property paths, BIND, VALUES, subqueries, NOT EXISTS |
| QRY-15 | The default namespace (`kb`) is served at `/blazegraph/sparql` |

### SPARQL update — `http/SparqlUpdateTest`

| ID | Verifies |
|---|---|
| UPD-01 | INSERT DATA / DELETE DATA with a form-encoded `update` parameter |
| UPD-02 | Update with an `application/sparql-update` body |
| UPD-03 | DELETE/INSERT WHERE |
| UPD-04 | LOAD from a `file:` URL |
| UPD-05 | CLEAR ALL and DROP ALL |
| UPD-06 | Malformed update: 400 with `MalformedQueryException` |
| UPD-07 | An `update` parameter sent with GET does not modify data (service description is returned) |
| UPD-08 | A 2 MB form-encoded update is accepted (`maxFormContentSize` = 10 MB in `jetty.xml`) |
| UPD-09 | Graph management in quads mode: INSERT DATA into GRAPH, COPY, MOVE, DROP GRAPH |

### REST API — `http/RestApiTest`

| ID | Verifies |
|---|---|
| RST-01 | POST an RDF body as N-Triples, Turtle, RDF/XML, JSON-LD |
| RST-02 | POST an RDF body as N-Quads and TriG (quads mode) |
| RST-03 | `context-uri` loads triples into a named graph |
| RST-04 | Unsupported `Content-Type`: 400 |
| RST-05 | Malformed RDF body: 500 with `RDFParseException`; nothing written |
| RST-06 | INSERT from a URL with `?uri=` |
| RST-07 | DELETE with a CONSTRUCT `query` |
| RST-08 | DELETE with an RDF body (`POST ?delete`) |
| RST-09 | DELETE by access path (`s`, `p`, `o`, `c`) |
| RST-10 | UPDATE with PUT: query selects what to remove, body is added |
| RST-11 | UPDATE with a `multipart/form-data` body (`?updatePost`, parts `add` and `remove`; Commons FileUpload) |
| RST-12 | `ESTCARD` range counts |
| RST-13 | `HASSTMT` existence test |
| RST-14 | `GETSTMTS` statement retrieval |
| RST-15 | `CONTEXTS` lists named graphs |
| RST-16 | Mutation response document: `application/xml`, `<data modified="n" milliseconds="t"/>` |

### Multi-tenancy (namespaces) — `http/MultiTenancyTest`

| ID | Verifies |
|---|---|
| NS-01 | Create: 201, `CREATED: name`, `Location` of the new SPARQL endpoint |
| NS-02 | Create an existing namespace: 409 `EXISTS: name` |
| NS-03 | List namespaces as VoID (RDF/XML, Turtle), including the default `kb` |
| NS-04 | `/namespace/{ns}/properties` returns a Java XML properties document reflecting the configuration |
| NS-05 | `GET /namespace/{ns}` returns an RDF document |
| NS-06 | Delete: 200 `DELETED: name`; second delete 404; endpoint returns 404 |
| NS-07 | `prepareProperties` returns the effective configuration and creates nothing |
| NS-08 | Data is isolated between namespaces |

### Database features — `http/FeaturesTest`

| ID | Verifies |
|---|---|
| FEA-01 | Full-text search (`bds:search`, `bds:rank`, `bds:relevance`, `bds:matchAllTerms`) |
| FEA-02 | RDFS inference with incremental truth maintenance, including retraction and `includeInferred=false` |
| FEA-03 | RDR / statement identifiers: Turtle-RDR (`application/x-turtle-RDR`) and `<< >>` in SPARQL |
| FEA-04 | Protocol dataset parameters `default-graph-uri` and `named-graph-uri` |
| FEA-05 | Federated SPARQL `SERVICE` to another endpoint (server-side HTTP client) |
| FEA-06 | `/tx` API: read/write tx lifecycle (201 + `Location`, STATUS, list, COMMIT, ABORT, 410 when finished) and read-only snapshot reads with `?timestamp=` |
| FEA-07 | Online backup (`POST /backup?file=`) writes a journal file |
| FEA-08 | Bulk data loader servlet (`POST /dataloader`) |
| FEA-09 | Query hints (`hint:analytic`, `hint:optimizer`) |

### Persistence — `http/PersistenceTest`

| ID | Verifies |
|---|---|
| PER-01 | Data and namespaces survive a graceful stop/start on the same journal (dedicated server) |

### Java remote client API — `javaapi/RemoteClientTest`

| ID | Verifies |
|---|---|
| JAPI-01 | `RemoteRepositoryManager`: create, describe, get properties, delete namespaces; 404 surfaced as `HttpException` |
| JAPI-02 | `RemoteRepository.add` with statements, byte[] and file bodies; `size`, `rangeCount` |
| JAPI-03 | Tuple, graph and boolean queries |
| JAPI-04 | `prepareUpdate`, `remove`, `update`, `hasStatement`, `getStatements` |
| JAPI-05 | Sesame `Repository` API over HTTP (`BigdataSailRemoteRepository`) |
| JAPI-06 | `RemoteTransactionManager`: read/write commit and abort; read-only snapshot isolation |

### Embedded use — `embedded/EmbeddedDatabaseTest`, `embedded/EmbeddedServerTest`

| ID | Verifies |
|---|---|
| EMB-01 | `BigdataSailRepository`: add, query, commit |
| EMB-02 | Rollback discards uncommitted changes |
| EMB-03 | SPARQL UPDATE through a connection |
| EMB-04 | Committed data persists when the journal is reopened |
| EMB-05 | `NanoSparqlServer.newInstance(port, indexManager, initParams)` serves the REST API in-process (returns a Jetty `Server`) |

### WAR deployment — `war/WarDeploymentTest`

| ID | Verifies |
|---|---|
| WAR-01 | The web application starts in Tomcat 9: status page, workbench welcome page, static resources |
| WAR-02 | SPARQL query and update on the default namespace; malformed query is 400 |
| WAR-03 | Create, load, delete a namespace |
| WAR-04 | REST insert, multipart `?updatePost` (Commons FileUpload), `ESTCARD`, access-path delete |
| WAR-05 | Federated `SERVICE` query (Blazegraph's bundled HTTP client running inside Tomcat) |
| WAR-06 | The `Location` of a created namespace points into the deployed context |

## Baseline behaviours worth knowing

These were observed on the reference build and are pinned as-is, because the
goal is compatibility, not correction:

- REST mutations and SPARQL UPDATE always run unisolated, even with
  `?timestamp=<txId>`; transactions isolate reads (FEA-06, JAPI-06).
- `GET /namespace/{ns}` returns an empty RDF document (NS-05).
- `RemoteRepositoryManager.showQueries()` issues a POST, but the JSON listing
  is only produced for GET, so that client method fails with "Expected MIME_TYPE
  application/json"; it is therefore not covered here (the JSON listing is
  covered over HTTP by SRV-10).
- Embedding `NanoSparqlServer` from the executable jar requires `jetty.home`
  and `jetty.overrideWebXml` to point at the bundled `war` directory, as
  `StandaloneNanoSparqlServer` does (EMB-05).
- Media types are compared without their `charset` parameter.

## Adding tests

Keep tests black-box and compatibility-focused: assert what a client can
observe, use a fresh namespace, give the test the next free ID of its topic in
`@DisplayName`, and add it to the tables above.
