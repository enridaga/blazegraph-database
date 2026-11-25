package com.blazegraph.acceptance.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.blazegraph.acceptance.support.BlazegraphServer;
import com.blazegraph.acceptance.support.Http;
import com.blazegraph.acceptance.support.SharedServer;

/** Server start-up, web application layout, redirects and response headers. */
@Tag("http")
@DisplayName("Server and web application")
class ServerWebAppTest {

    static BlazegraphServer server;

    @BeforeAll
    static void start() {
        server = SharedServer.get();
    }

    @Test
    @DisplayName("SRV-01 status page is served")
    void statusPage() {
        HttpResponse<String> r = Http.get(server.baseUrl() + "/status");
        assertEquals(200, r.statusCode());
        assertEquals("text/html", Http.mediaType(r));
        assertTrue(r.body().contains("Accepted query count"), r::body);
        assertTrue(r.body().contains("buildVersion"), r::body);
    }

    @Test
    @DisplayName("SRV-02 workbench is the welcome page of /blazegraph/")
    void workbenchWelcomePage() {
        HttpResponse<String> r = Http.get(server.baseUrl() + "/");
        assertEquals(200, r.statusCode());
        assertEquals("text/html", Http.mediaType(r));
        HttpResponse<String> index = Http.get(server.baseUrl() + "/html/index.html");
        assertEquals(200, index.statusCode());
        assertEquals(index.body(), r.body(), "welcome page must be html/index.html");
    }

    @Test
    @DisplayName("SRV-03 static workbench resources are served with their media type")
    void staticResources() {
        String[][] resources = {
                { "/html/css/style.css", "text/css" },
                { "/html/js/workbench.js", "javascript" },
                { "/html/images/logo.png", "image/png" },
                { "/html/favicon.ico", "image" },
        };
        for (String[] res : resources) {
            HttpResponse<String> r = Http.get(server.baseUrl() + res[0]);
            assertEquals(200, r.statusCode(), res[0]);
            assertTrue(Http.mediaType(r).contains(res[1]), res[0] + " -> " + Http.mediaType(r));
            assertTrue(r.body().length() > 0, res[0]);
        }
    }

    @Test
    @DisplayName("SRV-04 context root redirects permanently to /blazegraph keeping the query string")
    void rootRedirect() {
        HttpResponse<String> r = Http.get(server.rootUrl() + "/?a=1");
        assertEquals(301, r.statusCode());
        URI loc = URI.create(server.rootUrl() + "/").resolve(Http.header(r, "Location"));
        assertEquals("/blazegraph/", loc.getPath());
        assertEquals("a=1", loc.getQuery());
    }

    @Test
    @DisplayName("SRV-05 legacy /bigdata paths redirect permanently to /blazegraph")
    void legacyContextRedirect() {
        HttpResponse<String> r = Http.get(server.rootUrl() + "/bigdata/sparql?query=ASK%7B%7D");
        assertEquals(301, r.statusCode());
        URI loc = URI.create(server.rootUrl() + "/").resolve(Http.header(r, "Location"));
        assertEquals("/blazegraph/sparql", loc.getPath());
        assertEquals("query=ASK{}", loc.getQuery());
    }

    @Test
    @DisplayName("SRV-06 /blazegraph without trailing slash redirects to /blazegraph/")
    void contextPathSlashRedirect() {
        HttpResponse<String> r = Http.get(server.rootUrl() + "/blazegraph");
        assertTrue(r.statusCode() == 301 || r.statusCode() == 302, "redirect expected: " + r.statusCode());
        URI loc = URI.create(server.rootUrl() + "/").resolve(Http.header(r, "Location"));
        assertEquals("/blazegraph/", loc.getPath());
    }

    @Test
    @DisplayName("SRV-07 X-Frame-Options: SAMEORIGIN is sent on all responses")
    void frameOptionsHeader() {
        for (String path : new String[] { "/status", "/html/index.html", "/sparql", "/does-not-exist" }) {
            HttpResponse<String> r = Http.get(server.baseUrl() + path);
            assertEquals("SAMEORIGIN", Http.header(r, "X-Frame-Options"), path);
        }
        assertEquals("SAMEORIGIN", Http.header(Http.get(server.rootUrl() + "/"), "X-Frame-Options"));
    }

    @Test
    @DisplayName("SRV-08 unknown resources return 404")
    void notFound() {
        assertEquals(404, Http.get(server.baseUrl() + "/does-not-exist").statusCode());
        assertEquals(404, Http.get(server.baseUrl() + "/html/does-not-exist.html").statusCode());
    }

    @Test
    @DisplayName("SRV-09 performance counters page is served")
    void counters() {
        HttpResponse<String> r = Http.get(server.baseUrl() + "/counters");
        assertEquals(200, r.statusCode());
        assertEquals("text/html", Http.mediaType(r));
        assertTrue(r.body().contains("<html"), r::body);
    }

    @Test
    @DisplayName("SRV-10 running queries can be listed on the status page, as HTML or JSON")
    void showQueries() {
        HttpResponse<String> r = Http.get(server.baseUrl() + "/status?showQueries=details");
        assertEquals(200, r.statusCode());
        assertEquals("text/html", Http.mediaType(r));
        HttpResponse<String> json = Http.get(server.baseUrl() + "/status?showQueries", "Accept", "application/json");
        assertEquals(200, json.statusCode());
        assertEquals("application/json", Http.mediaType(json));
        com.blazegraph.acceptance.support.Json.parse(json.body());
    }

    @Test
    @DisplayName("SRV-11 the HTTP Server header is present")
    void serverHeader() {
        HttpResponse<String> r = Http.get(server.baseUrl() + "/status");
        assertNotNull(Http.header(r, "Server"), "jetty.send.server.version defaults to true");
    }
}
