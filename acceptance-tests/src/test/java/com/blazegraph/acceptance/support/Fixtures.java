package com.blazegraph.acceptance.support;

/** Shared test data. */
public final class Fixtures {

    private Fixtures() {
    }

    public static final String EX = "http://example.org/";

    /** Small social graph of 4 people (11 statements). */
    public static final String PEOPLE_TTL = """
            @prefix ex: <http://example.org/> .
            @prefix foaf: <http://xmlns.com/foaf/0.1/> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
            ex:alice a foaf:Person ; foaf:name "Alice" ; foaf:age 30 ; foaf:knows ex:bob .
            ex:bob   a foaf:Person ; foaf:name "Bob"   ; foaf:age 25 ; foaf:knows ex:carol .
            ex:carol foaf:name "Carol" ; foaf:knows ex:dave .
            ex:dave  foaf:name "Dave" .
            """;

    /** Number of statements in {@link #PEOPLE_TTL}. */
    public static final int PEOPLE_SIZE = 11;

    public static final String PREFIXES = """
            PREFIX ex: <http://example.org/>
            PREFIX foaf: <http://xmlns.com/foaf/0.1/>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            """;
}
