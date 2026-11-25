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

/** Blazegraph REST API (NanoSparqlServer) data operations on /sparql. */
@Tag("http")
@DisplayName("REST API")
class RestApiTest {

    static BlazegraphServer server;

    static final String NT = """
            <http://example.org/s1> <http://example.org/p> "one" .
            <http://example.org/s2> <http://example.org/p> "two" .
            """;

    @BeforeAll
    static void setUp() {
        server = SharedServer.get();
    }

    private static String triples() {
        return server.sparqlUrl(Namespaces.triples(server, "rest"));
    }

    private static String quads() {
        return server.sparqlUrl(Namespaces.quads(server, "restq"));
    }

    @Test
    @DisplayName("RST-01 INSERT with an RDF body in N-Triples, Turtle, RDF/XML and JSON-LD")
    void insertTriplesFormats() {
        String ep = triples();
        assertEquals(2, Sparql.modified(Http.post(ep, "text/plain", NT)));
        assertEquals(Fixtures.PEOPLE_SIZE, Sparql.modified(Http.post(ep, "text/turtle", Fixtures.PEOPLE_TTL)));
        String rdfxml = """
                <?xml version="1.0"?>
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns:ex="http://example.org/">
                  <rdf:Description rdf:about="http://example.org/x"><ex:p>xml</ex:p></rdf:Description>
                </rdf:RDF>""";
        assertEquals(1, Sparql.modified(Http.post(ep, "application/rdf+xml", rdfxml)));
        String jsonld = "{\"@id\":\"http://example.org/j\",\"http://example.org/p\":\"json\"}";
        assertEquals(1, Sparql.modified(Http.post(ep, "application/ld+json", jsonld)));
        assertEquals(2 + Fixtures.PEOPLE_SIZE + 2, Sparql.size(ep));
    }

    @Test
    @DisplayName("RST-02 INSERT with an RDF body in N-Quads and TriG (quads mode)")
    void insertQuadsFormats() {
        String ep = quads();
        assertEquals(1, Sparql.modified(Http.post(ep, "text/x-nquads",
                "<http://example.org/a> <http://example.org/p> \"q\" <http://example.org/g1> .\n")));
        assertEquals(1, Sparql.modified(Http.post(ep, "application/x-trig",
                "@prefix ex: <http://example.org/> . ex:g2 { ex:b ex:p \"t\" . }")));
        assertTrue(Sparql.ask(ep, PREFIXES + "ASK { GRAPH ex:g1 { ex:a ex:p \"q\" } GRAPH ex:g2 { ex:b ex:p \"t\" } }"));
    }

    @Test
    @DisplayName("RST-03 INSERT with context-uri puts triples in the named graph")
    void insertWithContext() {
        String ep = quads();
        assertEquals(2, Sparql.modified(Http.post(
                Http.withQuery(ep, Http.params("context-uri", "http://example.org/g")), "text/plain", NT)));
        assertEquals(2, Sparql.count(ep, PREFIXES + "SELECT (COUNT(*) AS ?n) { GRAPH ex:g { ?s ?p ?o } }"));
    }

    @Test
    @DisplayName("RST-04 unsupported content type is rejected with 400")
    void unsupportedContentType() {
        HttpResponse<String> r = Http.post(triples(), "application/x-unknown", "xx");
        assertEquals(400, r.statusCode());
        assertTrue(r.body().contains("Content-Type not recognized as RDF"), r::body);
    }

    @Test
    @DisplayName("RST-05 malformed RDF body is rejected with 500 and a parse error; nothing is written")
    void malformedRdf() {
        String ep = triples();
        HttpResponse<String> r = Http.post(ep, "text/turtle", "this is not turtle");
        assertEquals(500, r.statusCode());
        assertTrue(r.body().contains("RDFParseException"), r::body);
        assertEquals(0, Sparql.size(ep));
    }

    @Test
    @DisplayName("RST-06 INSERT from a URL with ?uri=")
    void insertFromUri() throws IOException {
        String ep = triples();
        Path file = server.workDir().resolve("rest-" + System.nanoTime() + ".nt");
        Files.writeString(file, NT);
        assertEquals(2, Sparql.modified(Http.postForm(ep, Http.params("uri", file.toUri().toString()))));
        assertEquals(2, Sparql.size(ep));
    }

    @Test
    @DisplayName("RST-07 DELETE with a CONSTRUCT query")
    void deleteWithQuery() {
        String ep = triples();
        Sparql.modified(Http.post(ep, "text/plain", NT));
        HttpResponse<String> r = Http.delete(Http.withQuery(ep, Http.params("query",
                "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o FILTER(?s = <http://example.org/s1>) }")));
        assertEquals(1, Sparql.modified(r));
        assertEquals(1, Sparql.size(ep));
    }

    @Test
    @DisplayName("RST-08 DELETE with an RDF body (POST ?delete)")
    void deleteWithBody() {
        String ep = triples();
        Sparql.modified(Http.post(ep, "text/plain", NT));
        assertEquals(1, Sparql.modified(Http.post(ep + "?delete", "text/plain",
                "<http://example.org/s2> <http://example.org/p> \"two\" .")));
        assertFalse(Sparql.ask(ep, "ASK { <http://example.org/s2> ?p ?o }"));
    }

    @Test
    @DisplayName("RST-09 DELETE by access path (s, p, o, c parameters)")
    void deleteByAccessPath() {
        String ep = triples();
        Sparql.modified(Http.post(ep, "text/plain", NT));
        assertEquals(1, Sparql.modified(Http.delete(Http.withQuery(ep, Http.params("s", "<http://example.org/s1>")))));
        assertEquals(1, Sparql.size(ep));
    }

    @Test
    @DisplayName("RST-10 UPDATE (PUT) replaces the statements matched by a query with a body")
    void putUpdate() {
        String ep = triples();
        Sparql.modified(Http.post(ep, "text/plain", NT));
        HttpResponse<String> r = Http.put(Http.withQuery(ep, Http.params("query",
                "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o FILTER(?s = <http://example.org/s1>) }")),
                "text/plain", "<http://example.org/s9> <http://example.org/p> \"nine\" .");
        assertEquals(2, Sparql.modified(r), "1 removed + 1 added");
        assertTrue(Sparql.ask(ep, "ASK { <http://example.org/s9> ?p \"nine\" }"));
        assertFalse(Sparql.ask(ep, "ASK { <http://example.org/s1> ?p ?o }"));
    }

    @Test
    @DisplayName("RST-11 UPDATE with a multipart/form-data body (?updatePost, add and remove parts)")
    void multipartUpdate() {
        String ep = triples();
        Sparql.modified(Http.post(ep, "text/plain", NT));
        HttpResponse<String> r = Http.postMultipart(ep + "?updatePost",
                new String[] { "add", "text/plain", "<http://example.org/m> <http://example.org/p> \"m\" .\n" },
                new String[] { "remove", "text/plain", "<http://example.org/s2> <http://example.org/p> \"two\" .\n" });
        assertEquals(2, Sparql.modified(r));
        assertTrue(Sparql.ask(ep, "ASK { <http://example.org/m> ?p \"m\" }"));
        assertFalse(Sparql.ask(ep, "ASK { <http://example.org/s2> ?p ?o }"));
    }

    @Test
    @DisplayName("RST-12 ESTCARD returns the range count of an access path")
    void estcard() {
        String ep = triples();
        Sparql.modified(Http.post(ep, "text/turtle", Fixtures.PEOPLE_TTL));
        HttpResponse<String> all = Http.get(ep, Http.params("ESTCARD", null));
        assertEquals(200, all.statusCode());
        assertEquals("application/xml", Http.mediaType(all));
        assertTrue(all.body().contains("rangeCount=\"" + Fixtures.PEOPLE_SIZE + "\""), all::body);
        HttpResponse<String> s = Http.get(ep, Http.params("ESTCARD", null, "s", "<http://example.org/alice>"));
        assertTrue(s.body().contains("rangeCount=\"4\""), s::body);
    }

    @Test
    @DisplayName("RST-13 HASSTMT tests for the existence of a statement")
    void hasStmt() {
        String ep = triples();
        Sparql.modified(Http.post(ep, "text/turtle", Fixtures.PEOPLE_TTL));
        HttpResponse<String> yes = Http.get(ep, Http.params("HASSTMT", null, "s", "<http://example.org/alice>",
                "p", "<http://xmlns.com/foaf/0.1/knows>"));
        assertEquals(200, yes.statusCode());
        assertTrue(yes.body().contains("result=\"true\""), yes::body);
        HttpResponse<String> no = Http.get(ep, Http.params("HASSTMT", null, "s", "<http://example.org/nobody>"));
        assertTrue(no.body().contains("result=\"false\""), no::body);
    }

    @Test
    @DisplayName("RST-14 GETSTMTS returns the statements of an access path")
    void getStmts() {
        String ep = triples();
        Sparql.modified(Http.post(ep, "text/turtle", Fixtures.PEOPLE_TTL));
        HttpResponse<String> r = Http.get(ep, Http.params("GETSTMTS", null, "s", "<http://example.org/dave>"),
                "Accept", "text/plain");
        assertEquals(200, r.statusCode());
        assertEquals(1, r.body().lines().filter(l -> !l.isBlank()).count(), r::body);
        assertTrue(r.body().contains("\"Dave\""), r::body);
    }

    @Test
    @DisplayName("RST-15 CONTEXTS lists the named graphs (quads mode)")
    void contexts() {
        String ep = quads();
        Sparql.update(ep, "INSERT DATA { GRAPH <http://example.org/g1> { <x:a> <x:b> 1 } "
                + "GRAPH <http://example.org/g2> { <x:a> <x:b> 2 } }");
        HttpResponse<String> r = Http.get(ep, Http.params("CONTEXTS", null));
        assertEquals(200, r.statusCode());
        String body = r.body().replace("&#47;", "/");
        assertTrue(body.contains("<context uri=\"http://example.org/g1\"/>"), r::body);
        assertTrue(body.contains("<context uri=\"http://example.org/g2\"/>"), r::body);
    }

    @Test
    @DisplayName("RST-16 mutation responses are XML <data modified=... milliseconds=.../>")
    void mutationResponseFormat() {
        HttpResponse<String> r = Http.post(triples(), "text/plain", NT);
        assertEquals(200, r.statusCode());
        assertEquals("application/xml", Http.mediaType(r));
        assertTrue(r.body().matches("(?s)<\\?xml version=\"1.0\"\\?><data modified=\"2\" milliseconds=\"\\d+\"/>\\s*"),
                r::body);
    }
}
