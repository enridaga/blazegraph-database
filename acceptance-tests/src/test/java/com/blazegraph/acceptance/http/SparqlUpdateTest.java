package com.blazegraph.acceptance.http;

import static com.blazegraph.acceptance.support.Fixtures.PREFIXES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

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

/** SPARQL 1.1 Update protocol and operations. */
@Tag("http")
@DisplayName("SPARQL update")
class SparqlUpdateTest {

    static BlazegraphServer server;

    @BeforeAll
    static void setUp() {
        server = SharedServer.get();
    }

    @Test
    @DisplayName("UPD-01 INSERT DATA and DELETE DATA with a URL-encoded form")
    void insertDeleteData() {
        String ep = server.sparqlUrl(Namespaces.triples(server, "upd"));
        HttpResponse<String> r = Sparql.update(ep, PREFIXES + "INSERT DATA { ex:a ex:p 1 , 2 , 3 }");
        assertEquals("text/html", Http.mediaType(r));
        assertEquals(3, Sparql.size(ep));
        Sparql.update(ep, PREFIXES + "DELETE DATA { ex:a ex:p 2 }");
        assertEquals(2, Sparql.size(ep));
    }

    @Test
    @DisplayName("UPD-02 update with an application/sparql-update body")
    void directUpdateBody() {
        String ep = server.sparqlUrl(Namespaces.triples(server, "upd"));
        HttpResponse<String> r = Http.post(ep, "application/sparql-update",
                PREFIXES + "INSERT DATA { ex:a ex:p ex:b }");
        assertEquals(200, r.statusCode(), r::body);
        assertTrue(Sparql.ask(ep, PREFIXES + "ASK { ex:a ex:p ex:b }"));
    }

    @Test
    @DisplayName("UPD-03 DELETE/INSERT WHERE rewrites matching data")
    void deleteInsertWhere() {
        String ep = server.sparqlUrl(Namespaces.triples(server, "upd"));
        Sparql.modified(Http.post(ep, "text/turtle", Fixtures.PEOPLE_TTL));
        Sparql.update(ep, PREFIXES + """
                DELETE { ?p foaf:age ?a } INSERT { ?p foaf:age ?b }
                WHERE { ?p foaf:age ?a BIND(?a + 1 AS ?b) }""");
        assertEquals(57, Sparql.count(ep, PREFIXES + "SELECT (SUM(?a) AS ?s) { ?p foaf:age ?a }"));
        assertEquals(Fixtures.PEOPLE_SIZE, Sparql.size(ep));
    }

    @Test
    @DisplayName("UPD-04 LOAD reads RDF from a URL")
    void load() throws IOException {
        String ep = server.sparqlUrl(Namespaces.triples(server, "upd"));
        Path file = server.workDir().resolve("load-" + System.nanoTime() + ".ttl");
        Files.writeString(file, Fixtures.PEOPLE_TTL);
        Sparql.update(ep, "LOAD <" + file.toUri() + ">");
        assertEquals(Fixtures.PEOPLE_SIZE, Sparql.size(ep));
    }

    @Test
    @DisplayName("UPD-05 CLEAR ALL and DROP ALL remove all data")
    void clearAndDrop() {
        String ep = server.sparqlUrl(Namespaces.triples(server, "upd"));
        Sparql.modified(Http.post(ep, "text/turtle", Fixtures.PEOPLE_TTL));
        Sparql.update(ep, "CLEAR ALL");
        assertEquals(0, Sparql.size(ep));
        Sparql.modified(Http.post(ep, "text/turtle", Fixtures.PEOPLE_TTL));
        Sparql.update(ep, "DROP ALL");
        assertEquals(0, Sparql.size(ep));
    }

    @Test
    @DisplayName("UPD-06 malformed update is rejected with 400 and a diagnostic")
    void malformedUpdate() {
        String ep = server.sparqlUrl(Namespaces.triples(server, "upd"));
        HttpResponse<String> r = Http.postForm(ep, Http.params("update", "INSERT DATTA { }"));
        assertEquals(400, r.statusCode());
        assertTrue(r.body().contains("MalformedQueryException"), r::body);
    }

    @Test
    @DisplayName("UPD-07 an update sent with GET does not modify data")
    void updateViaGetIsIgnored() {
        String ep = server.sparqlUrl(Namespaces.triples(server, "upd"));
        HttpResponse<String> r = Http.get(ep, Http.params("update", "INSERT DATA { <x:a> <x:b> <x:c> }"));
        assertEquals(200, r.statusCode(), "service description is returned");
        assertEquals(0, Sparql.size(ep));
    }

    @Test
    @DisplayName("UPD-08 large (2 MB) form-encoded updates are accepted")
    void largeFormUpdate() {
        String ep = server.sparqlUrl(Namespaces.triples(server, "upd"));
        StringBuilder sb = new StringBuilder("INSERT DATA {\n");
        int n = 0;
        while (sb.length() < 2_000_000) {
            sb.append("<http://example.org/s").append(n).append("> <http://example.org/p> \"")
              .append("x".repeat(40)).append(n).append("\" .\n");
            n++;
        }
        sb.append('}');
        HttpResponse<String> r = Http.postForm(ep, Http.params("update", sb.toString()));
        assertEquals(200, r.statusCode(), () -> r.body().substring(0, Math.min(500, r.body().length())));
        assertEquals(n, Sparql.size(ep));
    }

    @Test
    @DisplayName("UPD-09 graph management in quads mode: INSERT into GRAPH, COPY, MOVE, DROP GRAPH")
    void graphManagement() {
        String ep = server.sparqlUrl(Namespaces.quads(server, "updq"));
        Sparql.update(ep, PREFIXES + "INSERT DATA { GRAPH ex:g1 { ex:a ex:p 1 . ex:a ex:p 2 } }");
        Sparql.update(ep, PREFIXES + "COPY ex:g1 TO ex:g2");
        assertEquals(2, Sparql.count(ep, PREFIXES + "SELECT (COUNT(*) AS ?n) { GRAPH ex:g2 { ?s ?p ?o } }"));
        Sparql.update(ep, PREFIXES + "MOVE ex:g2 TO ex:g3");
        assertEquals(0, Sparql.count(ep, PREFIXES + "SELECT (COUNT(*) AS ?n) { GRAPH ex:g2 { ?s ?p ?o } }"));
        assertEquals(2, Sparql.count(ep, PREFIXES + "SELECT (COUNT(*) AS ?n) { GRAPH ex:g3 { ?s ?p ?o } }"));
        Sparql.update(ep, PREFIXES + "DROP GRAPH ex:g1");
        assertFalse(Sparql.ask(ep, PREFIXES + "ASK { GRAPH ex:g1 { ?s ?p ?o } }"));
        assertTrue(Sparql.ask(ep, PREFIXES + "ASK { GRAPH ex:g3 { ?s ?p ?o } }"));
    }
}
