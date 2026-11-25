package com.blazegraph.acceptance.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** SPARQL protocol helpers over {@link Http}. */
public final class Sparql {

    public static final String JSON = "application/sparql-results+json";
    public static final String XML = "application/sparql-results+xml";
    private static final Pattern MODIFIED = Pattern.compile("modified=\"(\\d+)\"");

    private Sparql() {
    }

    /** Runs a SELECT query (POST, form encoded) and returns the bindings as strings. */
    public static List<Map<String, String>> select(String endpoint, String query) {
        HttpResponse<String> r = Http.postForm(endpoint, Http.params("query", query), "Accept", JSON);
        assertEquals(200, r.statusCode(), r::body);
        return bindings(r.body());
    }

    public static boolean ask(String endpoint, String query) {
        HttpResponse<String> r = Http.postForm(endpoint, Http.params("query", query), "Accept", JSON);
        assertEquals(200, r.statusCode(), r::body);
        return (Boolean) json(r.body()).get("boolean");
    }

    /** Runs a SELECT returning a single numeric value in its first column. */
    public static long count(String endpoint, String query) {
        List<Map<String, String>> rows = select(endpoint, query);
        assertEquals(1, rows.size(), "one row expected");
        return Long.parseLong(rows.get(0).values().iterator().next());
    }

    /** Number of statements visible in the namespace (in quads mode the default graph is the union of all graphs). */
    public static long size(String endpoint) {
        return count(endpoint, "SELECT (COUNT(*) AS ?n) WHERE { ?s ?p ?o }");
    }

    public static HttpResponse<String> update(String endpoint, String update) {
        HttpResponse<String> r = Http.postForm(endpoint, Http.params("update", update));
        assertEquals(200, r.statusCode(), r::body);
        return r;
    }

    public static Map<String, Object> json(String body) {
        try {
            return Json.object(Json.parse(body));
        } catch (RuntimeException e) {
            throw new AssertionError("Not JSON: " + body, e);
        }
    }

    /** SELECT result rows, each mapping variable name to the binding's value. */
    public static List<Map<String, String>> bindings(String jsonBody) {
        Map<String, Object> root = json(jsonBody);
        List<Map<String, String>> out = new ArrayList<>();
        for (Object b : Json.array(Json.object(root.get("results")).get("bindings"))) {
            Map<String, String> row = new java.util.LinkedHashMap<>();
            Json.object(b).forEach((k, v) -> row.put(k, (String) Json.object(v).get("value")));
            out.add(row);
        }
        return out;
    }

    /** Parses the {@code modified} count of a REST API mutation response. */
    public static long modified(HttpResponse<String> r) {
        assertEquals(200, r.statusCode(), r::body);
        Matcher m = MODIFIED.matcher(r.body());
        if (!m.find()) {
            throw new AssertionError("No mutation count in: " + r.body());
        }
        return Long.parseLong(m.group(1));
    }
}
