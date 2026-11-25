package com.blazegraph.acceptance.http;

import static com.blazegraph.acceptance.support.Fixtures.PREFIXES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.blazegraph.acceptance.support.BlazegraphServer;
import com.blazegraph.acceptance.support.Fixtures;
import com.blazegraph.acceptance.support.Http;
import com.blazegraph.acceptance.support.Namespaces;
import com.blazegraph.acceptance.support.SharedServer;
import com.blazegraph.acceptance.support.Sparql;

/** Blazegraph-specific database features exposed over HTTP. */
@Tag("http")
@DisplayName("Database features")
class FeaturesTest {

    static BlazegraphServer server;

    @BeforeAll
    static void setUp() {
        server = SharedServer.get();
    }

    @Test
    @DisplayName("FEA-01 full-text search with bds:search, relevance and rank")
    void fullTextSearch() {
        String ep = server.sparqlUrl(Namespaces.create(server, "fts", Map.of(Namespaces.QUADS, "false",
                Namespaces.TEXT_INDEX, "true", Namespaces.AXIOMS, "com.bigdata.rdf.axioms.NoAxioms")));
        Sparql.update(ep, PREFIXES + """
                INSERT DATA {
                  ex:d1 rdfs:label "the quick brown fox" .
                  ex:d2 rdfs:label "a quick brown dog" .
                  ex:d3 rdfs:label "lazy cat" .
                }""");
        List<Map<String, String>> rows = Sparql.select(ep, PREFIXES + """
                PREFIX bds: <http://www.bigdata.com/rdf/search#>
                SELECT ?s ?rank WHERE {
                  ?o bds:search "quick fox" ; bds:rank ?rank ; bds:relevance ?score .
                  ?s rdfs:label ?o .
                } ORDER BY ?rank""");
        assertEquals(2, rows.size(), rows::toString);
        assertEquals("http://example.org/d1", rows.get(0).get("s"), "best match first");
        assertEquals("1", rows.get(0).get("rank"));
        List<Map<String, String>> all = Sparql.select(ep, PREFIXES + """
                PREFIX bds: <http://www.bigdata.com/rdf/search#>
                SELECT ?s WHERE { ?o bds:search "quick fox" ; bds:matchAllTerms true . ?s rdfs:label ?o }""");
        assertEquals(List.of(Map.of("s", "http://example.org/d1")), all);
    }

    @Test
    @DisplayName("FEA-02 RDFS inference with incremental truth maintenance")
    void inference() {
        String ep = server.sparqlUrl(Namespaces.create(server, "inf", Map.of(
                Namespaces.QUADS, "false",
                Namespaces.AXIOMS, "com.bigdata.rdf.axioms.RdfsAxioms",
                Namespaces.TRUTH_MAINTENANCE, "true")));
        Sparql.update(ep, PREFIXES + "INSERT DATA { ex:Cat rdfs:subClassOf ex:Animal . ex:tom a ex:Cat }");
        assertTrue(Sparql.ask(ep, PREFIXES + "ASK { ex:tom a ex:Animal }"), "entailed");
        Sparql.update(ep, PREFIXES + "DELETE DATA { ex:Cat rdfs:subClassOf ex:Animal }");
        assertFalse(Sparql.ask(ep, PREFIXES + "ASK { ex:tom a ex:Animal }"), "retracted");
        HttpResponse<String> explicit = Http.get(ep, Http.params("query",
                PREFIXES + "ASK { ex:tom a ex:Cat }", "includeInferred", "false"), "Accept", Sparql.JSON);
        assertEquals(Boolean.TRUE, Sparql.json(explicit.body()).get("boolean"));
    }

    @Test
    @DisplayName("FEA-03 RDR (statement identifiers): Turtle-RDR insert and << >> queries")
    void rdr() {
        String ep = server.sparqlUrl(Namespaces.create(server, "rdr", Map.of(Namespaces.QUADS, "false",
                Namespaces.SIDS, "true", Namespaces.AXIOMS, "com.bigdata.rdf.axioms.NoAxioms")));
        Sparql.modified(Http.post(ep, "application/x-turtle-RDR", """
                @prefix ex: <http://example.org/> .
                ex:alice ex:knows ex:bob .
                <<ex:alice ex:knows ex:bob>> ex:since 2001 .
                """));
        assertEquals(List.of(Map.of("y", "2001")), Sparql.select(ep,
                PREFIXES + "SELECT ?y { << ex:alice ex:knows ex:bob >> ex:since ?y }"));
        Sparql.update(ep, PREFIXES + "INSERT DATA { << ex:alice ex:knows ex:bob >> ex:source ex:web }");
        assertEquals(List.of(Map.of("s", "http://example.org/web")), Sparql.select(ep,
                PREFIXES + "SELECT ?s { ?t ex:source ?s . ?t ex:since 2001 }"));
    }

    @Test
    @DisplayName("FEA-04 SPARQL protocol dataset parameters (default-graph-uri, named-graph-uri)")
    void protocolDataset() {
        String ep = server.sparqlUrl(Namespaces.quads(server, "ds"));
        Sparql.update(ep, PREFIXES + "INSERT DATA { GRAPH ex:g1 { ex:a ex:p 1 } GRAPH ex:g2 { ex:b ex:p 2 } }");
        HttpResponse<String> r = Http.get(ep, Http.params("query", "SELECT ?s { ?s ?p ?o }",
                "default-graph-uri", "http://example.org/g1"), "Accept", Sparql.JSON);
        assertEquals(List.of(Map.of("s", "http://example.org/a")), Sparql.bindings(r.body()));
        HttpResponse<String> n = Http.get(ep, Http.params("query", "SELECT ?g { GRAPH ?g { ?s ?p ?o } }",
                "named-graph-uri", "http://example.org/g2"), "Accept", Sparql.JSON);
        assertEquals(List.of(Map.of("g", "http://example.org/g2")), Sparql.bindings(n.body()));
    }

    @Test
    @DisplayName("FEA-05 federated query with SERVICE to another SPARQL endpoint")
    void federatedQuery() {
        String remote = server.sparqlUrl(Namespaces.triples(server, "fedR"));
        String local = server.sparqlUrl(Namespaces.triples(server, "fedL"));
        Sparql.modified(Http.post(remote, "text/turtle", Fixtures.PEOPLE_TTL));
        Sparql.update(local, PREFIXES + "INSERT DATA { ex:alice ex:role \"admin\" }");
        List<Map<String, String>> rows = Sparql.select(local, PREFIXES
                + "SELECT ?name ?role { ?p ex:role ?role SERVICE <" + remote + "> { ?p foaf:name ?name } }");
        assertEquals(List.of(Map.of("name", "Alice", "role", "admin")), rows);
    }

    private static final Pattern TX_ID = Pattern.compile("txId=\"(-?\\d+)\"");

    private static String txId(HttpResponse<String> r) {
        Matcher m = TX_ID.matcher(r.body());
        assertTrue(m.find(), r::body);
        return m.group(1);
    }

    @Test
    @DisplayName("FEA-06 REST transaction API: lifecycle (create, status, list, commit, abort) and snapshot reads")
    void transactions() {
        String ns = Namespaces.create(server, "tx", Map.of(Namespaces.QUADS, "false", Namespaces.ISOLATABLE, "true",
                Namespaces.TRUTH_MAINTENANCE, "false", Namespaces.JUSTIFY, "false",
                Namespaces.AXIOMS, "com.bigdata.rdf.axioms.NoAxioms"));
        String ep = server.sparqlUrl(ns);
        String txApi = server.baseUrl() + "/tx";

        // read/write transaction lifecycle
        HttpResponse<String> created = Http.postEmpty(txApi + "?timestamp=0");
        assertEquals(201, created.statusCode(), created::body);
        String tx = txId(created);
        assertTrue(created.body().contains("readOnly=\"false\""), created::body);
        assertEquals("/blazegraph/tx/" + tx, URI.create(Http.header(created, "Location")).getPath());
        assertEquals(200, Http.get(txApi + "/" + tx + "?STATUS").statusCode());
        assertTrue(Http.get(txApi).body().contains("txId=\"" + tx + "\""), "listed");
        HttpResponse<String> commit = Http.postEmpty(txApi + "/" + tx + "?COMMIT");
        assertEquals(200, commit.statusCode(), commit::body);
        assertEquals(410, Http.get(txApi + "/" + tx + "?STATUS").statusCode(), "gone after commit");

        String tx2 = txId(Http.postEmpty(txApi + "?timestamp=0"));
        HttpResponse<String> abort = Http.postEmpty(txApi + "/" + tx2 + "?ABORT");
        assertEquals(200, abort.statusCode(), abort::body);
        assertEquals(410, Http.get(txApi + "/" + tx2 + "?STATUS").statusCode(), "gone after abort");

        // a read-only transaction reads a stable snapshot
        HttpResponse<String> ro = Http.postEmpty(txApi + "?timestamp=-1");
        assertEquals(201, ro.statusCode(), ro::body);
        assertTrue(ro.body().contains("readOnly=\"true\""), ro::body);
        String snapshot = txId(ro);
        Sparql.update(ep, "INSERT DATA { <x:a> <x:b> <x:c> }");
        assertEquals(1, Sparql.size(ep), "unisolated view sees the write");
        assertEquals(0, Sparql.size(ep + "?timestamp=" + snapshot), "snapshot does not");
        assertEquals(200, Http.postEmpty(txApi + "/" + snapshot + "?ABORT").statusCode());
    }

    @Test
    @DisplayName("FEA-07 online backup writes a journal copy")
    void backup() {
        Path file = server.workDir().resolve("backup-" + System.nanoTime() + ".jnl");
        HttpResponse<String> r = Http.postEmpty(server.baseUrl() + "/backup?file=" + file);
        assertEquals(200, r.statusCode(), r::body);
        assertTrue(r.body().contains("Backup created"), r::body);
        assertTrue(Files.isRegularFile(file));
    }

    @Test
    @DisplayName("FEA-08 bulk data loader servlet loads files into a namespace")
    void dataLoader() throws IOException {
        String ns = Namespaces.triples(server, "dl");
        Path dir = Files.createDirectories(server.workDir().resolve("dl-" + System.nanoTime()));
        Files.writeString(dir.resolve("people.ttl"), Fixtures.PEOPLE_TTL);
        Path propsFile = dir.resolve("loader.properties");
        Files.writeString(propsFile, Namespaces.QUADS + "=false\n");
        String body = "namespace=" + ns + "\n"
                + "fileOrDirs=" + dir.resolve("people.ttl") + "\n"
                + "propertyFile=" + propsFile + "\n"
                + "format=turtle\n";
        HttpResponse<String> r = Http.post(server.baseUrl() + "/dataloader", "text/plain", body);
        assertEquals(200, r.statusCode(), r::body);
        assertEquals(Fixtures.PEOPLE_SIZE, Sparql.size(server.sparqlUrl(ns)));
    }

    @Test
    @DisplayName("FEA-09 query hints and analytic mode are accepted")
    void queryHints() {
        String ep = server.sparqlUrl(Namespaces.triples(server, "hint"));
        Sparql.modified(Http.post(ep, "text/turtle", Fixtures.PEOPLE_TTL));
        assertEquals(4, Sparql.count(ep, PREFIXES + """
                PREFIX hint: <http://www.bigdata.com/queryHints#>
                SELECT (COUNT(*) AS ?n) {
                  hint:Query hint:analytic "true" .
                  hint:Query hint:optimizer "None" .
                  ?p foaf:name ?name
                }"""));
    }
}
