package com.blazegraph.acceptance.http;

import static com.blazegraph.acceptance.support.Fixtures.PREFIXES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

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

/** SPARQL 1.1 Query protocol: request forms, result formats, errors, query engine. */
@Tag("http")
@DisplayName("SPARQL query")
class SparqlQueryTest {

    static BlazegraphServer server;
    static String endpoint;

    static final String SELECT_NAMES = PREFIXES + "SELECT ?name WHERE { ?p foaf:name ?name } ORDER BY ?name";

    @BeforeAll
    static void setUp() {
        server = SharedServer.get();
        String ns = Namespaces.triples(server, "query");
        endpoint = server.sparqlUrl(ns);
        Sparql.modified(Http.post(endpoint, "text/turtle", Fixtures.PEOPLE_TTL));
    }

    private static List<String> names(String json) {
        return Sparql.bindings(json).stream().map(m -> m.get("name")).toList();
    }

    @Test
    @DisplayName("QRY-01 SELECT using HTTP GET")
    void selectGet() {
        HttpResponse<String> r = Http.get(endpoint, Http.params("query", SELECT_NAMES), "Accept", Sparql.JSON);
        assertEquals(200, r.statusCode(), r::body);
        assertEquals(List.of("Alice", "Bob", "Carol", "Dave"), names(r.body()));
    }

    @Test
    @DisplayName("QRY-02 SELECT using HTTP POST with a URL-encoded form")
    void selectPostForm() {
        HttpResponse<String> r = Http.postForm(endpoint, Http.params("query", SELECT_NAMES), "Accept", Sparql.JSON);
        assertEquals(200, r.statusCode(), r::body);
        assertEquals(List.of("Alice", "Bob", "Carol", "Dave"), names(r.body()));
    }

    @Test
    @DisplayName("QRY-03 SELECT using HTTP POST with an application/sparql-query body")
    void selectPostDirect() {
        HttpResponse<String> r = Http.post(endpoint, "application/sparql-query", SELECT_NAMES, "Accept", Sparql.JSON);
        assertEquals(200, r.statusCode(), r::body);
        assertEquals(List.of("Alice", "Bob", "Carol", "Dave"), names(r.body()));
    }

    @Test
    @DisplayName("QRY-04 SELECT result formats: JSON, XML (default), CSV, TSV")
    void selectFormats() {
        Map<String, String> q = Http.params("query", SELECT_NAMES);

        HttpResponse<String> xml = Http.get(endpoint, q, "Accept", Sparql.XML);
        assertEquals("application/sparql-results+xml", Http.mediaType(xml));
        assertTrue(xml.body().contains("<literal>Alice</literal>"), xml::body);

        HttpResponse<String> dflt = Http.get(endpoint, q);
        assertEquals("application/sparql-results+xml", Http.mediaType(dflt), "default format");

        HttpResponse<String> csv = Http.get(endpoint, q, "Accept", "text/csv");
        assertEquals("text/csv", Http.mediaType(csv));
        assertEquals(List.of("name", "Alice", "Bob", "Carol", "Dave"), csv.body().lines().toList());

        HttpResponse<String> tsv = Http.get(endpoint, q, "Accept", "text/tab-separated-values");
        assertEquals("text/tab-separated-values", Http.mediaType(tsv));
        assertEquals("?name", tsv.body().lines().findFirst().orElseThrow());
        assertEquals(List.of("?name", "Alice", "Bob", "Carol", "Dave"), tsv.body().lines().toList());

        HttpResponse<String> json = Http.get(endpoint, q, "Accept", Sparql.JSON);
        assertEquals(Sparql.JSON, Http.mediaType(json));
    }

    @Test
    @DisplayName("QRY-05 ASK returns a boolean in JSON and XML")
    void ask() {
        assertTrue(Sparql.ask(endpoint, PREFIXES + "ASK { ex:alice foaf:knows ex:bob }"));
        assertFalse(Sparql.ask(endpoint, PREFIXES + "ASK { ex:bob foaf:knows ex:alice }"));
        HttpResponse<String> xml = Http.get(endpoint,
                Http.params("query", PREFIXES + "ASK { ex:alice foaf:knows ex:bob }"), "Accept", Sparql.XML);
        assertEquals("application/sparql-results+xml", Http.mediaType(xml));
        assertTrue(xml.body().contains("<boolean>true</boolean>"), xml::body);
    }

    @Test
    @DisplayName("QRY-06 CONSTRUCT result formats: RDF/XML (default), Turtle, N-Triples, JSON-LD")
    void construct() {
        Map<String, String> q = Http.params("query",
                PREFIXES + "CONSTRUCT { ?a foaf:knows ?b } WHERE { ?a foaf:knows ?b }");

        HttpResponse<String> rdfxml = Http.get(endpoint, q);
        assertEquals(200, rdfxml.statusCode());
        assertEquals("application/rdf+xml", Http.mediaType(rdfxml));
        assertTrue(rdfxml.body().contains("rdf:RDF"), rdfxml::body);

        HttpResponse<String> ttl = Http.get(endpoint, q, "Accept", "text/turtle");
        assertEquals("text/turtle", Http.mediaType(ttl));
        assertTrue(ttl.body().contains("ex:carol"), ttl::body);

        HttpResponse<String> nt = Http.get(endpoint, q, "Accept", "text/plain");
        assertEquals("text/plain", Http.mediaType(nt));
        assertEquals(3, nt.body().lines().filter(l -> !l.isBlank()).count(), nt::body);
        assertTrue(nt.body().contains(
                "<http://example.org/alice> <http://xmlns.com/foaf/0.1/knows> <http://example.org/bob> ."), nt::body);

        HttpResponse<String> jsonld = Http.get(endpoint, q, "Accept", "application/ld+json");
        assertEquals("application/ld+json", Http.mediaType(jsonld));
        assertTrue(jsonld.body().contains("http://example.org/dave"), jsonld::body);
    }

    @Test
    @DisplayName("QRY-07 DESCRIBE returns the description of a resource")
    void describe() {
        HttpResponse<String> r = Http.get(endpoint, Http.params("query", PREFIXES + "DESCRIBE ex:dave"),
                "Accept", "text/plain");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("<http://example.org/dave> <http://xmlns.com/foaf/0.1/name> \"Dave\""), r::body);
    }

    @Test
    @DisplayName("QRY-08 malformed query is rejected with 400 and a diagnostic")
    void malformedQuery() {
        HttpResponse<String> r = Http.get(endpoint, Http.params("query", "SELEC * {"));
        assertEquals(400, r.statusCode());
        assertEquals("text/plain", Http.mediaType(r));
        assertTrue(r.body().contains("MalformedQueryException"), r::body);
    }

    @Test
    @DisplayName("QRY-09 query against an unknown namespace returns 404")
    void unknownNamespace() {
        HttpResponse<String> r = Http.get(server.sparqlUrl("no_such_namespace"), Http.params("query", "ASK {}"));
        assertEquals(404, r.statusCode());
        assertTrue(r.body().contains("DatasetNotFoundException"), r::body);
    }

    @Test
    @DisplayName("QRY-10 service description is returned for GET without a query")
    void serviceDescription() {
        HttpResponse<String> r = Http.get(endpoint);
        assertEquals(200, r.statusCode());
        assertEquals("application/rdf+xml", Http.mediaType(r));
        assertTrue(r.body().contains("http://www.w3.org/ns/sparql-service-description#Service"), r::body);
        HttpResponse<String> ttl = Http.get(endpoint, "Accept", "text/turtle");
        assertEquals("text/turtle", Http.mediaType(ttl));
    }

    @Test
    @DisplayName("QRY-11 explain returns an HTML query plan")
    void explain() {
        HttpResponse<String> r = Http.get(endpoint, Http.params("query", SELECT_NAMES, "explain", null));
        assertEquals(200, r.statusCode());
        assertEquals("text/html", Http.mediaType(r));
        assertTrue(r.body().contains("<h1>Query</h1>") || r.body().contains("Query"), r::body);
    }

    @Test
    @DisplayName("QRY-12 non-ASCII literals round-trip in UTF-8 through query and results")
    void unicode() {
        String ns = Namespaces.triples(server, "unicode");
        String ep = server.sparqlUrl(ns);
        String text = "Grüße 東京 — ¿qué? 😀";
        Sparql.update(ep, "INSERT DATA { <http://example.org/u> <http://example.org/label> \"" + text + "\" }");
        List<Map<String, String>> rows = Sparql.select(ep,
                "SELECT ?l WHERE { ?s <http://example.org/label> ?l FILTER(CONTAINS(?l, \"東京\")) }");
        assertEquals(List.of(Map.of("l", text)), rows);
        HttpResponse<String> csv = Http.get(ep, Http.params("query", "SELECT ?l { ?s ?p ?l }"), "Accept", "text/csv");
        assertTrue(csv.body().contains(text), csv::body);
    }

    @Test
    @DisplayName("QRY-13 long queries (4 KB) are accepted with GET")
    void longGetQuery() {
        StringBuilder values = new StringBuilder();
        for (int i = 0; values.length() < 4000; i++) {
            values.append(" \"n").append(i).append('"');
        }
        String q = PREFIXES + "SELECT ?name WHERE { ?p foaf:name ?name VALUES ?x {" + values + "} } LIMIT 1";
        HttpResponse<String> r = Http.get(endpoint, Http.params("query", q), "Accept", Sparql.JSON);
        assertEquals(200, r.statusCode(), r::body);
    }

    @Test
    @DisplayName("QRY-14 query engine: FILTER, OPTIONAL, aggregates, property paths, BIND, VALUES, subqueries")
    void queryEngine() {
        assertEquals(List.of("Alice"), names(Http.get(endpoint, Http.params("query",
                PREFIXES + "SELECT ?name { ?p foaf:name ?name ; foaf:age ?a FILTER(?a > 26) }"),
                "Accept", Sparql.JSON).body()));

        List<Map<String, String>> opt = Sparql.select(endpoint, PREFIXES
                + "SELECT ?name ?age { ?p foaf:name ?name OPTIONAL { ?p foaf:age ?age } } ORDER BY ?name");
        assertEquals(4, opt.size());
        assertEquals("30", opt.get(0).get("age"));
        assertFalse(opt.get(3).containsKey("age"));

        assertEquals(55, Sparql.count(endpoint, PREFIXES + "SELECT (SUM(?a) AS ?s) { ?p foaf:age ?a }"));
        assertEquals(3, Sparql.count(endpoint, PREFIXES + "SELECT (COUNT(?b) AS ?n) { ex:alice foaf:knows+ ?b }"));

        List<Map<String, String>> grouped = Sparql.select(endpoint, PREFIXES
                + "SELECT ?t (COUNT(?s) AS ?n) { ?s a ?t } GROUP BY ?t HAVING (COUNT(?s) > 1)");
        assertEquals(List.of(Map.of("t", "http://xmlns.com/foaf/0.1/Person", "n", "2")), grouped);

        assertEquals(List.of(Map.of("u", "ALICE")), Sparql.select(endpoint, PREFIXES
                + "SELECT ?u { ex:alice foaf:name ?n BIND(UCASE(?n) AS ?u) }"));

        assertEquals(List.of("Bob", "Dave"), names(Http.get(endpoint, Http.params("query", PREFIXES
                + "SELECT ?name { VALUES ?p { ex:bob ex:dave } ?p foaf:name ?name } ORDER BY ?name"),
                "Accept", Sparql.JSON).body()));

        assertEquals(List.of(Map.of("name", "Alice")), Sparql.select(endpoint, PREFIXES
                + "SELECT ?name { ?p foaf:name ?name { SELECT (MAX(?a) AS ?m) { ?x foaf:age ?a } } ?p foaf:age ?m }"));

        assertEquals(List.of("Carol", "Dave"), names(Http.get(endpoint, Http.params("query", PREFIXES
                + "SELECT ?name { ?p foaf:name ?name FILTER NOT EXISTS { ?p a foaf:Person } } ORDER BY ?name"),
                "Accept", Sparql.JSON).body()));
    }

    @Test
    @DisplayName("QRY-15 the default namespace is served at /blazegraph/sparql")
    void defaultNamespace() {
        HttpResponse<String> r = Http.get(server.sparqlUrl(), Http.params("query", "ASK {}"), "Accept", Sparql.JSON);
        assertEquals(200, r.statusCode());
        assertEquals(Boolean.TRUE, Sparql.json(r.body()).get("boolean"));
    }
}
