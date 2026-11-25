package com.blazegraph.acceptance.javaapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.query.BindingSet;
import org.openrdf.query.GraphQueryResult;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.rio.RDFFormat;

import com.bigdata.rdf.sail.remote.BigdataSailRemoteRepository;
import com.bigdata.rdf.sail.webapp.client.HttpException;
import com.bigdata.rdf.sail.webapp.client.IRemoteTx;
import com.bigdata.rdf.sail.webapp.client.RemoteRepository;
import com.bigdata.rdf.sail.webapp.client.RemoteRepository.AddOp;
import com.bigdata.rdf.sail.webapp.client.RemoteRepository.RemoveOp;
import com.bigdata.rdf.sail.webapp.client.RemoteRepositoryManager;
import com.bigdata.rdf.sail.webapp.client.RemoteTransactionManager;
import com.blazegraph.acceptance.support.BlazegraphServer;
import com.blazegraph.acceptance.support.Fixtures;
import com.blazegraph.acceptance.support.Namespaces;
import com.blazegraph.acceptance.support.SharedServer;

/** The Java remote client API (bigdata-client) against a running server. */
@Tag("java-api")
@DisplayName("Java remote client API")
class RemoteClientTest {

    static BlazegraphServer server;
    static RemoteRepositoryManager manager;
    static final ValueFactory VF = ValueFactoryImpl.getInstance();

    @BeforeAll
    static void setUp() {
        server = SharedServer.get();
        manager = new RemoteRepositoryManager(server.baseUrl());
    }

    @AfterAll
    static void tearDown() throws Exception {
        manager.close();
    }

    private static String newNamespace(String prefix, String... kv) throws Exception {
        String ns = Namespaces.uniqueName(prefix);
        Properties p = new Properties();
        p.setProperty(Namespaces.NAMESPACE, ns);
        p.setProperty(Namespaces.QUADS, "false");
        p.setProperty(Namespaces.TRUTH_MAINTENANCE, "false");
        p.setProperty(Namespaces.AXIOMS, "com.bigdata.rdf.axioms.NoAxioms");
        for (int i = 0; i < kv.length; i += 2) {
            p.setProperty(kv[i], kv[i + 1]);
        }
        manager.createRepository(ns, p);
        return ns;
    }

    private static List<String> column(TupleQueryResult r, String var) throws Exception {
        List<String> out = new ArrayList<>();
        try {
            while (r.hasNext()) {
                BindingSet b = r.next();
                out.add(b.getValue(var).stringValue());
            }
        } finally {
            r.close();
        }
        return out;
    }

    private static org.openrdf.model.Literal integer(int i) {
        return VF.createLiteral(Integer.toString(i), org.openrdf.model.vocabulary.XMLSchema.INTEGER);
    }

    private static URI ex(String local) {
        return VF.createURI(Fixtures.EX + local);
    }

    @Test
    @DisplayName("JAPI-01 manage namespaces: create, describe, properties, delete")
    void namespaces() throws Exception {
        String ns = newNamespace("japi");
        GraphQueryResult desc = manager.getRepositoryDescriptions();
        boolean found = false;
        try {
            while (desc.hasNext()) {
                if (desc.next().getObject().stringValue().equals(ns)) {
                    found = true;
                }
            }
        } finally {
            desc.close();
        }
        assertTrue(found, "new namespace is described");
        Properties props = manager.getRepositoryProperties(ns);
        assertEquals("false", props.getProperty(Namespaces.QUADS));
        manager.deleteRepository(ns);
        HttpException e = assertThrows(HttpException.class, () -> manager.getRepositoryProperties(ns));
        assertEquals(404, e.getStatusCode());
    }

    @Test
    @DisplayName("JAPI-02 add data (statements, byte[] body, file) and count it")
    void add() throws Exception {
        RemoteRepository repo = manager.getRepositoryForNamespace(newNamespace("japi"));
        List<Statement> stmts = List.of(VF.createStatement(ex("a"), ex("p"), VF.createLiteral("x")));
        assertEquals(1, repo.add(new AddOp(stmts)));
        assertEquals(Fixtures.PEOPLE_SIZE, repo.add(new AddOp(
                Fixtures.PEOPLE_TTL.getBytes(StandardCharsets.UTF_8), RDFFormat.TURTLE)));
        File f = File.createTempFile("japi", ".nt", server.workDir().toFile());
        Files.writeString(f.toPath(), "<http://example.org/f> <http://example.org/p> \"file\" .\n");
        assertEquals(1, repo.add(new AddOp(f, RDFFormat.NTRIPLES)));
        assertEquals(Fixtures.PEOPLE_SIZE + 2, repo.size());
        assertEquals(4, repo.rangeCount(ex("alice"), null, null));
    }

    @Test
    @DisplayName("JAPI-03 tuple, graph and boolean queries")
    void queries() throws Exception {
        RemoteRepository repo = manager.getRepositoryForNamespace(newNamespace("japi"));
        repo.add(new AddOp(Fixtures.PEOPLE_TTL.getBytes(StandardCharsets.UTF_8), RDFFormat.TURTLE));
        assertEquals(List.of("Alice", "Bob", "Carol", "Dave"), column(repo.prepareTupleQuery(
                Fixtures.PREFIXES + "SELECT ?n { ?p foaf:name ?n } ORDER BY ?n").evaluate(), "n"));
        GraphQueryResult g = repo.prepareGraphQuery(
                Fixtures.PREFIXES + "CONSTRUCT WHERE { ?a foaf:knows ?b }").evaluate();
        int n = 0;
        try {
            while (g.hasNext()) {
                g.next();
                n++;
            }
        } finally {
            g.close();
        }
        assertEquals(3, n);
        assertTrue(repo.prepareBooleanQuery(Fixtures.PREFIXES + "ASK { ex:alice foaf:knows ex:bob }").evaluate());
    }

    @Test
    @DisplayName("JAPI-04 SPARQL update, remove, update and statement access")
    void mutations() throws Exception {
        RemoteRepository repo = manager.getRepositoryForNamespace(newNamespace("japi"));
        repo.prepareUpdate(Fixtures.PREFIXES + "INSERT DATA { ex:a ex:p 1 , 2 , 3 }").evaluate();
        assertEquals(3, repo.size());
        assertTrue(repo.hasStatement(ex("a"), ex("p"), integer(2), false));
        assertEquals(1, repo.remove(new RemoveOp(ex("a"), ex("p"), integer(2))));
        assertEquals(2, repo.update(
                new RemoveOp(Fixtures.PREFIXES + "CONSTRUCT { ex:a ex:p 1 } WHERE { ex:a ex:p 1 }"),
                new AddOp(List.of(VF.createStatement(ex("b"), ex("p"), integer(4))))));
        GraphQueryResult stmts = repo.getStatements(ex("b"), null, null, false);
        try {
            assertTrue(stmts.hasNext());
            assertEquals("4", stmts.next().getObject().stringValue());
        } finally {
            stmts.close();
        }
        assertEquals(2, repo.size());
    }

    @Test
    @DisplayName("JAPI-05 Sesame Repository API over HTTP (BigdataSailRemoteRepository)")
    void sesameRepository() throws Exception {
        RemoteRepository remote = manager.getRepositoryForNamespace(newNamespace("japi"));
        BigdataSailRemoteRepository repo = remote.getBigdataSailRemoteRepository();
        RepositoryConnection cx = repo.getConnection();
        try {
            cx.add(ex("s"), ex("p"), VF.createLiteral("o"));
            assertEquals(1, cx.size());
            RepositoryResult<Statement> it = cx.getStatements(ex("s"), null, null, false);
            try {
                assertTrue(it.hasNext());
                assertEquals("o", it.next().getObject().stringValue());
            } finally {
                it.close();
            }
            TupleQueryResult r = cx.prepareTupleQuery(org.openrdf.query.QueryLanguage.SPARQL,
                    "SELECT ?o { ?s ?p ?o }").evaluate();
            assertEquals(List.of("o"), column(r, "o"));
            cx.remove(ex("s"), null, null);
            assertEquals(0, cx.size());
        } finally {
            cx.close();
        }
    }

    @Test
    @DisplayName("JAPI-06 remote transactions: read/write commit and abort, read-only snapshot")
    void remoteTransactions() throws Exception {
        String ns = newNamespace("japitx", Namespaces.ISOLATABLE, "true", Namespaces.JUSTIFY, "false");
        RemoteRepository repo = manager.getRepositoryForNamespace(ns);
        RemoteTransactionManager txm = manager.getTransactionManager();

        IRemoteTx rw = txm.createTx(RemoteTransactionManager.UNISOLATED);
        assertFalse(rw.isReadOnly());
        assertTrue(rw.isActive());
        rw.commit();
        assertFalse(rw.isActive());
        IRemoteTx rw2 = txm.createTx(RemoteTransactionManager.UNISOLATED);
        rw2.abort();
        assertFalse(rw2.isActive());

        IRemoteTx ro = txm.createTx(RemoteTransactionManager.READ_COMMITTED);
        assertTrue(ro.isReadOnly());
        repo.prepareUpdate("INSERT DATA { <x:a> <x:b> <x:c> }").evaluate();
        RemoteRepository snapshot = manager.getRepositoryForURL(server.sparqlUrl(ns), ro);
        assertEquals(1, repo.size());
        assertFalse(snapshot.prepareBooleanQuery("ASK { <x:a> <x:b> <x:c> }").evaluate(), "snapshot isolation");
        ro.abort();
    }
}
