package com.blazegraph.acceptance.embedded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Properties;

import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bigdata.journal.IIndexManager;
import com.bigdata.journal.Options;
import com.bigdata.rdf.sail.BigdataSail;
import com.bigdata.rdf.sail.webapp.NanoSparqlServer;
import com.blazegraph.acceptance.support.Fixtures;
import com.blazegraph.acceptance.support.Http;
import com.blazegraph.acceptance.support.Sparql;

/**
 * Embedding the NanoSparqlServer (HTTP endpoint) in an application, on an
 * application-owned database, following the NSSEmbeddedExample sample.
 */
@Tag("embedded")
@DisplayName("Embedded NanoSparqlServer")
class EmbeddedServerTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("EMB-05 NanoSparqlServer.newInstance serves the REST API on an application-owned database")
    void embeddedServer() throws Exception {
        Properties p = new Properties();
        p.setProperty(Options.FILE, dir.resolve("nss.jnl").toString());
        p.setProperty(Options.BUFFER_MODE, "DiskRW");
        p.setProperty(BigdataSail.Options.QUADS_MODE, "false");
        p.setProperty(BigdataSail.Options.TRUTH_MAINTENANCE, "false");
        p.setProperty(BigdataSail.Options.AXIOMS_CLASS, "com.bigdata.rdf.axioms.NoAxioms");
        BigdataSail sail = new BigdataSail(p);
        sail.initialize();
        Server server = null;
        try {
            IIndexManager indexManager = sail.getIndexManager();
            // Serve the web application bundled in the executable jar, as
            // StandaloneNanoSparqlServer does.
            System.setProperty("jetty.home", ClassLoader.getSystemResource("war").toExternalForm());
            System.setProperty("jetty.overrideWebXml",
                    ClassLoader.getSystemResource("war/WEB-INF/override-web.xml").toExternalForm());
            server = NanoSparqlServer.newInstance(0 /* random port */, indexManager, new LinkedHashMap<>());
            NanoSparqlServer.awaitServerStart(server);
            int port = NanoSparqlServer.getLocalPort(server);
            assertTrue(port > 0);
            String ep = "http://localhost:" + port + "/blazegraph/sparql";

            Sparql.modified(Http.post(ep, "text/turtle", Fixtures.PEOPLE_TTL));
            assertEquals(Fixtures.PEOPLE_SIZE, Sparql.size(ep));
            HttpResponse<String> status = Http.get("http://localhost:" + port + "/blazegraph/status");
            assertEquals(200, status.statusCode());
        } finally {
            if (server != null) {
                server.stop();
            }
            sail.shutDown();
        }
    }
}
