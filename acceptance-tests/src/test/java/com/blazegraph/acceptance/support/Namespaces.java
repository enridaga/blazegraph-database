package com.blazegraph.acceptance.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates isolated namespaces (knowledge bases) through the Multi-Tenancy API,
 * so tests sharing a server do not see each other's data.
 */
public final class Namespaces {

    private static final AtomicInteger SEQ = new AtomicInteger();

    public static final String QUADS = "com.bigdata.rdf.store.AbstractTripleStore.quads";
    public static final String SIDS = "com.bigdata.rdf.store.AbstractTripleStore.statementIdentifiers";
    public static final String TEXT_INDEX = "com.bigdata.rdf.store.AbstractTripleStore.textIndex";
    public static final String AXIOMS = "com.bigdata.rdf.store.AbstractTripleStore.axiomsClass";
    public static final String TRUTH_MAINTENANCE = "com.bigdata.rdf.sail.truthMaintenance";
    public static final String JUSTIFY = "com.bigdata.rdf.store.AbstractTripleStore.justify";
    public static final String ISOLATABLE = "com.bigdata.rdf.sail.isolatableIndices";
    public static final String NAMESPACE = "com.bigdata.rdf.sail.namespace";

    private Namespaces() {
    }

    /** A unique namespace name with the given prefix. */
    public static String uniqueName(String prefix) {
        return prefix + "_" + SEQ.incrementAndGet() + "_" + Long.toString(System.nanoTime() % 100000, 36);
    }

    /** Creates a triples-mode namespace with no inference. */
    public static String triples(BlazegraphServer server, String prefix) {
        return create(server, prefix, Map.of(QUADS, "false", AXIOMS, "com.bigdata.rdf.axioms.NoAxioms",
                TRUTH_MAINTENANCE, "false"));
    }

    /** Creates a quads-mode namespace. */
    public static String quads(BlazegraphServer server, String prefix) {
        return create(server, prefix, Map.of(QUADS, "true", AXIOMS, "com.bigdata.rdf.axioms.NoAxioms",
                TRUTH_MAINTENANCE, "false"));
    }

    public static String create(BlazegraphServer server, String prefix, Map<String, String> props) {
        String name = uniqueName(prefix);
        Map<String, String> all = new TreeMap<>(props);
        all.put(NAMESPACE, name);
        StringBuilder body = new StringBuilder();
        all.forEach((k, v) -> body.append(k).append('=').append(v).append('\n'));
        HttpResponse<String> r = Http.post(server.baseUrl() + "/namespace", "text/plain", body.toString());
        assertEquals(201, r.statusCode(), () -> "namespace creation failed: " + r.body());
        return name;
    }
}
