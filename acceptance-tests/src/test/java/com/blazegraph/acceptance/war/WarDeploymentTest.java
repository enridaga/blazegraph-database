package com.blazegraph.acceptance.war;

import static com.blazegraph.acceptance.support.Fixtures.PREFIXES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.blazegraph.acceptance.support.BlazegraphServer;
import com.blazegraph.acceptance.support.Fixtures;
import com.blazegraph.acceptance.support.Http;
import com.blazegraph.acceptance.support.Namespaces;
import com.blazegraph.acceptance.support.Sparql;

/**
 * The WAR deployed in an independent javax.servlet container (Apache Tomcat 9),
 * at context path /blazegraph. Skipped when the WAR has not been built.
 */
@Tag("war")
@DisplayName("WAR deployment (Tomcat 9)")
class WarDeploymentTest {

    static BlazegraphServer server;

    @BeforeAll
    static void deploy() {
        assumeTrue(BlazegraphServer.warOrNull() != null,
                "WAR not built (mvn -P Deployment install), skipping: " + System.getProperty("blazegraph.war"));
        server = BlazegraphServer.startWar("war");
    }

    @AfterAll
    static void undeploy() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("WAR-01 the web application starts: status page and workbench are served")
    void startsAndServesWorkbench() {
        HttpResponse<String> status = Http.get(server.baseUrl() + "/status");
        assertEquals(200, status.statusCode());
        assertTrue(status.body().contains("Accepted query count"), status::body);
        HttpResponse<String> welcome = Http.get(server.baseUrl() + "/");
        assertEquals(200, welcome.statusCode());
        assertEquals("text/html", Http.mediaType(welcome));
        HttpResponse<String> css = Http.get(server.baseUrl() + "/html/css/style.css");
        assertEquals(200, css.statusCode());
        assertEquals("text/css", Http.mediaType(css));
    }

    @Test
    @DisplayName("WAR-02 SPARQL query and update on the default namespace")
    void queryAndUpdate() {
        String ep = server.sparqlUrl();
        Sparql.update(ep, "INSERT DATA { <http://example.org/war> <http://example.org/p> \"deployed\" }");
        assertEquals(List.of(Map.of("o", "deployed")),
                Sparql.select(ep, "SELECT ?o { <http://example.org/war> ?p ?o }"));
        HttpResponse<String> bad = Http.get(ep, Http.params("query", "SELEC * {"));
        assertEquals(400, bad.statusCode());
    }

    @Test
    @DisplayName("WAR-03 multi-tenancy: create, use and delete a namespace")
    void namespaces() {
        String ns = Namespaces.triples(server, "war");
        Sparql.modified(Http.post(server.sparqlUrl(ns), "text/turtle", Fixtures.PEOPLE_TTL));
        assertEquals(Fixtures.PEOPLE_SIZE, Sparql.size(server.sparqlUrl(ns)));
        assertEquals(200, Http.delete(server.baseUrl() + "/namespace/" + ns).statusCode());
        assertEquals(404, Http.get(server.sparqlUrl(ns), Http.params("query", "ASK {}")).statusCode());
    }

    @Test
    @DisplayName("WAR-04 REST API: RDF body insert, multipart update and access path operations")
    void restApi() {
        String ep = server.sparqlUrl(Namespaces.triples(server, "warrest"));
        assertEquals(2, Sparql.modified(Http.post(ep, "text/plain",
                "<http://example.org/a> <http://example.org/p> \"1\" .\n<http://example.org/b> <http://example.org/p> \"2\" .\n")));
        assertEquals(2, Sparql.modified(Http.postMultipart(ep + "?updatePost",
                new String[] { "add", "text/plain", "<http://example.org/c> <http://example.org/p> \"3\" .\n" },
                new String[] { "remove", "text/plain", "<http://example.org/a> <http://example.org/p> \"1\" .\n" })));
        HttpResponse<String> card = Http.get(ep, Http.params("ESTCARD", null));
        assertTrue(card.body().contains("rangeCount=\"2\""), card::body);
        assertEquals(1, Sparql.modified(Http.delete(Http.withQuery(ep, Http.params("s", "<http://example.org/b>")))));
        assertFalse(Sparql.ask(ep, "ASK { <http://example.org/b> ?p ?o }"));
    }

    @Test
    @DisplayName("WAR-05 federated SERVICE query from inside the container")
    void federatedQuery() {
        String remote = server.sparqlUrl(Namespaces.triples(server, "warfedR"));
        String local = server.sparqlUrl(Namespaces.triples(server, "warfedL"));
        Sparql.modified(Http.post(remote, "text/turtle", Fixtures.PEOPLE_TTL));
        Sparql.update(local, PREFIXES + "INSERT DATA { ex:bob ex:role \"user\" }");
        assertEquals(List.of(Map.of("name", "Bob", "role", "user")), Sparql.select(local, PREFIXES
                + "SELECT ?name ?role { ?p ex:role ?role SERVICE <" + remote + "> { ?p foaf:name ?name } }"));
    }

    @Test
    @DisplayName("WAR-06 Location of a created namespace points into the deployed context")
    void createdLocation() {
        String name = Namespaces.uniqueName("warloc");
        HttpResponse<String> r = Http.post(server.baseUrl() + "/namespace", "text/plain",
                Namespaces.NAMESPACE + "=" + name + "\n" + Namespaces.QUADS + "=false\n");
        assertEquals(201, r.statusCode(), r::body);
        assertEquals("/blazegraph/namespace/" + name + "/sparql", URI.create(Http.header(r, "Location")).getPath());
    }
}
