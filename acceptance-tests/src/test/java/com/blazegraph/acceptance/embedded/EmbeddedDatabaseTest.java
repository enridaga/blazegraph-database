package com.blazegraph.acceptance.embedded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
import org.openrdf.query.BindingSet;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.rio.RDFFormat;

import com.bigdata.journal.Options;
import com.bigdata.rdf.sail.BigdataSail;
import com.bigdata.rdf.sail.BigdataSailRepository;
import com.blazegraph.acceptance.support.Fixtures;

/** Embedded use of the database through the Sesame (openrdf) Repository API. */
@Tag("embedded")
@DisplayName("Embedded database (Sesame API)")
class EmbeddedDatabaseTest {

    @TempDir
    Path dir;

    private Properties props(String journal) {
        Properties p = new Properties();
        p.setProperty(Options.FILE, dir.resolve(journal).toString());
        p.setProperty(Options.BUFFER_MODE, "DiskRW");
        p.setProperty(BigdataSail.Options.TRUTH_MAINTENANCE, "false");
        p.setProperty(BigdataSail.Options.QUADS_MODE, "true");
        p.setProperty(BigdataSail.Options.AXIOMS_CLASS, "com.bigdata.rdf.axioms.NoAxioms");
        return p;
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

    private static BigdataSailRepository open(Properties p) throws Exception {
        BigdataSailRepository repo = new BigdataSailRepository(new BigdataSail(p));
        repo.initialize();
        return repo;
    }

    @Test
    @DisplayName("EMB-01 add, query and commit through a repository connection")
    void addQueryCommit() throws Exception {
        BigdataSailRepository repo = open(props("emb1.jnl"));
        try {
            RepositoryConnection cx = repo.getConnection();
            try {
                cx.begin();
                cx.add(new StringReader(Fixtures.PEOPLE_TTL), Fixtures.EX, RDFFormat.TURTLE);
                cx.commit();
                assertEquals(Fixtures.PEOPLE_SIZE, cx.size());
                assertEquals(List.of("Alice", "Bob", "Carol", "Dave"), column(cx.prepareTupleQuery(
                        QueryLanguage.SPARQL, Fixtures.PREFIXES + "SELECT ?n { ?p foaf:name ?n } ORDER BY ?n")
                        .evaluate(), "n"));
            } finally {
                cx.close();
            }
        } finally {
            repo.shutDown();
        }
    }

    @Test
    @DisplayName("EMB-02 rollback discards uncommitted changes")
    void rollback() throws Exception {
        BigdataSailRepository repo = open(props("emb2.jnl"));
        try {
            RepositoryConnection cx = repo.getConnection();
            try {
                ValueFactory vf = cx.getValueFactory();
                URI s = vf.createURI(Fixtures.EX + "s");
                cx.begin();
                cx.add(s, s, s);
                cx.commit();
                cx.begin();
                cx.add(vf.createURI(Fixtures.EX + "t"), s, s);
                cx.rollback();
                assertEquals(1, cx.size());
            } finally {
                cx.close();
            }
        } finally {
            repo.shutDown();
        }
    }

    @Test
    @DisplayName("EMB-03 SPARQL UPDATE through a repository connection")
    void sparqlUpdate() throws Exception {
        BigdataSailRepository repo = open(props("emb3.jnl"));
        try {
            RepositoryConnection cx = repo.getConnection();
            try {
                cx.begin();
                cx.prepareUpdate(QueryLanguage.SPARQL, Fixtures.PREFIXES
                        + "INSERT DATA { GRAPH ex:g { ex:a ex:p 1 , 2 } }").execute();
                cx.commit();
                assertTrue(cx.prepareBooleanQuery(QueryLanguage.SPARQL,
                        Fixtures.PREFIXES + "ASK { GRAPH ex:g { ex:a ex:p 2 } }").evaluate());
            } finally {
                cx.close();
            }
        } finally {
            repo.shutDown();
        }
    }

    @Test
    @DisplayName("EMB-04 committed data persists when the journal is reopened")
    void reopen() throws Exception {
        Properties p = props("emb4.jnl");
        BigdataSailRepository repo = open(p);
        try {
            RepositoryConnection cx = repo.getConnection();
            try {
                cx.begin();
                cx.add(new StringReader(Fixtures.PEOPLE_TTL), Fixtures.EX, RDFFormat.TURTLE);
                cx.commit();
            } finally {
                cx.close();
            }
        } finally {
            repo.shutDown();
        }
        BigdataSailRepository again = open(p);
        try {
            RepositoryConnection cx = again.getReadOnlyConnection();
            try {
                assertEquals(Fixtures.PEOPLE_SIZE, cx.size());
                assertFalse(cx.isEmpty());
            } finally {
                cx.close();
            }
        } finally {
            again.shutDown();
        }
    }
}
