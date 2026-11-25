package com.blazegraph.acceptance.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.blazegraph.acceptance.support.BlazegraphServer;
import com.blazegraph.acceptance.support.Http;
import com.blazegraph.acceptance.support.Namespaces;
import com.blazegraph.acceptance.support.SharedServer;
import com.blazegraph.acceptance.support.Sparql;

/** Multi-tenancy API (/namespace): lifecycle and isolation of knowledge bases. */
@Tag("http")
@DisplayName("Multi-tenancy (namespaces)")
class MultiTenancyTest {

    static BlazegraphServer server;

    @BeforeAll
    static void setUp() {
        server = SharedServer.get();
    }

    private static String props(String name, String... extra) {
        StringBuilder sb = new StringBuilder(Namespaces.NAMESPACE + "=" + name + "\n");
        for (String e : extra) {
            sb.append(e).append('\n');
        }
        return sb.toString();
    }

    @Test
    @DisplayName("NS-01 create returns 201 with the Location of the new SPARQL endpoint")
    void create() {
        String name = Namespaces.uniqueName("ns");
        HttpResponse<String> r = Http.post(server.baseUrl() + "/namespace", "text/plain",
                props(name, Namespaces.QUADS + "=false"));
        assertEquals(201, r.statusCode(), r::body);
        assertTrue(r.body().contains("CREATED: " + name), r::body);
        URI loc = URI.create(Http.header(r, "Location"));
        assertEquals("/blazegraph/namespace/" + name + "/sparql", loc.getPath());
        assertEquals(200, Http.get(loc.toString(), Http.params("query", "ASK {}")).statusCode());
    }

    @Test
    @DisplayName("NS-02 creating an existing namespace returns 409")
    void createDuplicate() {
        String name = Namespaces.triples(server, "dup");
        HttpResponse<String> r = Http.post(server.baseUrl() + "/namespace", "text/plain", props(name));
        assertEquals(409, r.statusCode());
        assertTrue(r.body().contains("EXISTS: " + name), r::body);
    }

    @Test
    @DisplayName("NS-03 list namespaces as a VoID description including the default kb")
    void list() {
        String name = Namespaces.triples(server, "list");
        HttpResponse<String> r = Http.get(server.baseUrl() + "/namespace");
        assertEquals(200, r.statusCode());
        assertEquals("application/rdf+xml", Http.mediaType(r));
        assertTrue(r.body().contains("http://rdfs.org/ns/void#Dataset"), r::body);
        assertTrue(r.body().contains(">" + name + "<"), "lists the new namespace");
        assertTrue(r.body().contains(">kb<"), "lists the default namespace");
        HttpResponse<String> ttl = Http.get(server.baseUrl() + "/namespace", "Accept", "text/turtle");
        assertEquals("text/turtle", Http.mediaType(ttl));
    }

    @Test
    @DisplayName("NS-04 namespace properties are returned as a Java XML properties document")
    void properties() {
        String name = Namespaces.create(server, "props", java.util.Map.of(
                Namespaces.QUADS, "false", Namespaces.TEXT_INDEX, "true"));
        HttpResponse<String> r = Http.get(server.baseUrl() + "/namespace/" + name + "/properties");
        assertEquals(200, r.statusCode());
        assertEquals("application/xml", Http.mediaType(r));
        assertTrue(r.body().contains("<entry key=\"" + Namespaces.NAMESPACE + "\">" + name + "</entry>"), r::body);
        assertTrue(r.body().contains("<entry key=\"" + Namespaces.TEXT_INDEX + "\">true</entry>"), r::body);
        assertTrue(r.body().contains("<entry key=\"" + Namespaces.QUADS + "\">false</entry>"), r::body);
    }

    @Test
    @DisplayName("NS-05 GET on a single namespace returns an RDF document")
    void describe() {
        String name = Namespaces.triples(server, "desc");
        HttpResponse<String> r = Http.get(server.baseUrl() + "/namespace/" + name);
        assertEquals(200, r.statusCode());
        assertEquals("application/rdf+xml", Http.mediaType(r));
        assertTrue(r.body().contains("rdf:RDF"), r::body);
        HttpResponse<String> ttl = Http.get(server.baseUrl() + "/namespace/" + name, "Accept", "text/turtle");
        assertEquals(200, ttl.statusCode());
        assertEquals("text/turtle", Http.mediaType(ttl));
    }

    @Test
    @DisplayName("NS-06 delete returns 200, a second delete 404, and the endpoint disappears")
    void delete() {
        String name = Namespaces.triples(server, "del");
        HttpResponse<String> r = Http.delete(server.baseUrl() + "/namespace/" + name);
        assertEquals(200, r.statusCode(), r::body);
        assertTrue(r.body().contains("DELETED: " + name), r::body);
        assertEquals(404, Http.delete(server.baseUrl() + "/namespace/" + name).statusCode());
        assertEquals(404, Http.get(server.sparqlUrl(name), Http.params("query", "ASK {}")).statusCode());
        assertFalse(Http.get(server.baseUrl() + "/namespace").body().contains(">" + name + "<"));
    }

    @Test
    @DisplayName("NS-07 prepareProperties returns the effective configuration without creating a namespace")
    void prepareProperties() {
        String name = Namespaces.uniqueName("prep");
        HttpResponse<String> r = Http.post(server.baseUrl() + "/namespace/prepareProperties", "text/plain",
                props(name));
        assertEquals(200, r.statusCode(), r::body);
        assertEquals("application/xml", Http.mediaType(r));
        assertTrue(r.body().contains(Namespaces.QUADS), r::body);
        assertEquals(404, Http.get(server.sparqlUrl(name), Http.params("query", "ASK {}")).statusCode());
    }

    @Test
    @DisplayName("NS-08 data is isolated between namespaces")
    void isolation() {
        String a = server.sparqlUrl(Namespaces.triples(server, "isoA"));
        String b = server.sparqlUrl(Namespaces.triples(server, "isoB"));
        Sparql.update(a, "INSERT DATA { <x:a> <x:b> <x:c> }");
        assertEquals(1, Sparql.size(a));
        assertEquals(0, Sparql.size(b));
    }
}
