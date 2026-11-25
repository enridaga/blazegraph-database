package com.blazegraph.acceptance.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.blazegraph.acceptance.support.BlazegraphServer;
import com.blazegraph.acceptance.support.Fixtures;
import com.blazegraph.acceptance.support.Http;
import com.blazegraph.acceptance.support.Namespaces;
import com.blazegraph.acceptance.support.Sparql;

/** Durability of the journal across server restarts (dedicated server). */
@Tag("http")
@DisplayName("Persistence")
class PersistenceTest {

    @Test
    @DisplayName("PER-01 data and namespaces survive a graceful restart on the same journal")
    void restart() {
        Path dir = BlazegraphServer.newWorkDir("persist");
        String ns;
        try (BlazegraphServer s = BlazegraphServer.start(dir)) {
            ns = Namespaces.triples(s, "persist");
            Sparql.modified(Http.post(s.sparqlUrl(ns), "text/turtle", Fixtures.PEOPLE_TTL));
            Sparql.update(s.sparqlUrl(), "INSERT DATA { <x:default> <x:p> \"kb\" }");
        }
        assertTrue(Files.isRegularFile(dir.resolve("blazegraph.jnl")), "journal in working directory");
        try (BlazegraphServer s = BlazegraphServer.start(dir)) {
            assertEquals(Fixtures.PEOPLE_SIZE, Sparql.size(s.sparqlUrl(ns)));
            assertTrue(Sparql.ask(s.sparqlUrl(), "ASK { <x:default> <x:p> \"kb\" }"));
        }
    }
}
