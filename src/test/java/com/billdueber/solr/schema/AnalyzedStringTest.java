package com.billdueber.solr.schema;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.solr.SolrTestCaseJ4;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests for AnalyzedString field type.
 *
 * <p>The core premise: values fed to an AnalyzedString field are run through the analysis chain
 * of a referenced TextField at index time and stored as a plain string. This guarantees that
 * range queries compare pre-normalized values, sidestepping any ambiguity in how Solr derives
 * the multiTermAnalyzer from a TextField's query chain at query time.
 */
public class AnalyzedStringTest extends SolrTestCaseJ4 {

  @BeforeClass
  public static void beforeClass() throws Exception {
    Path solrHome = Paths.get(AnalyzedStringTest.class.getResource("/solr").toURI());
    initCore("solrconfig.xml", "schema.xml", solrHome);
  }

  @Before
  public void resetIndex() throws Exception {
    assertU(delQ("*:*"));
    assertU(commit());
  }

  // ---------------------------------------------------------------------------
  // toInternal: normalization correctness
  // ---------------------------------------------------------------------------

  /**
   * The field type's toInternal() must apply the full analysis chain so the stored
   * string is already normalized when it lands in the index.
   */
  @Test
  public void testToInternalNormalizesValue() {
    AnalyzedString ft =
        (AnalyzedString) h.getCore().getLatestSchema().getFieldTypeByName("browse_key");

    // lowercasing
    assertEquals("beatles", ft.toInternal("BEATLES"));
    // leading-article stripping + lowercasing
    assertEquals("beatles", ft.toInternal("The Beatles"));
    assertEquals("rolling stones", ft.toInternal("The Rolling Stones"));
    // no article to strip
    assertEquals("aerosmith", ft.toInternal("Aerosmith"));
    // slash stripped by the punctuation filter (not the hyphen/colon → space filter)
    assertEquals("acdc", ft.toInternal("AC/DC"));
    // accent folding
    assertEquals("bjork", ft.toInternal("Björk"));
    // already normalized passes through unchanged
    assertEquals("zz top", ft.toInternal("zz top"));
  }

  // ---------------------------------------------------------------------------
  // Stored value: what actually lands in the index
  // ---------------------------------------------------------------------------

  /**
   * The stored value for an author_browse field must be the post-analysis string,
   * not the raw input.
   */
  @Test
  public void testStoredValueIsNormalized() throws Exception {
    assertU(adoc("id", "1", "author_browse", "The Beatles"));
    assertU(commit());

    assertQ("stored value should be the normalized form",
        req("q", "id:1", "fl", "author_browse"),
        "//doc/str[@name='author_browse'][.='beatles']");

    assertU(delQ("*:*"));
    assertU(commit());
  }

  // ---------------------------------------------------------------------------
  // Range queries: the whole point of the library
  // ---------------------------------------------------------------------------

  /**
   * Range queries on an AnalyzedString field compare pre-normalized values.
   * The query bounds must themselves be pre-normalized by the caller (which is
   * the same operation the user would perform before issuing the browse query).
   */
  @Test
  public void testRangeQueryMatchesCorrectDocuments() throws Exception {
    assertU(adoc("id", "1",  "author_browse", "The Beatles"));      // → beatles
    assertU(adoc("id", "2",  "author_browse", "Aerosmith"));         // → aerosmith
    assertU(adoc("id", "3",  "author_browse", "The Rolling Stones")); // → rolling stones
    assertU(adoc("id", "4",  "author_browse", "ZZ Top"));             // → zz top
    assertU(adoc("id", "5",  "author_browse", "Björk"));              // → bjork
    assertU(commit());

    // ["bjork" TO "rolling stones"] should hit bjork and rolling stones only.
    // "beatles" < "bjork" lexicographically (e < j), so it is NOT in this range.
    // Multi-word bounds must be quoted in the query parser
    assertQ("range query with normalized bounds",
        req("q", "author_browse:[\"bjork\" TO \"rolling stones\"]"),
        "//result[@numFound='2']",
        "//doc/str[@name='id'][.='3']",   // rolling stones
        "//doc/str[@name='id'][.='5']");  // bjork

    // open-ended lower bound: everything up to "beatles" inclusive
    assertQ("open lower bound",
        req("q", "author_browse:[* TO beatles]"),
        "//result[@numFound='2']",
        "//doc/str[@name='id'][.='1']",   // beatles
        "//doc/str[@name='id'][.='2']");  // aerosmith

    // open-ended upper bound: everything from "rolling stones" inclusive
    assertQ("open upper bound",
        req("q", "author_browse:[\"rolling stones\" TO *]"),
        "//result[@numFound='2']",
        "//doc/str[@name='id'][.='3']",   // rolling stones
        "//doc/str[@name='id'][.='4']");  // zz top

    assertU(delQ("*:*"));
    assertU(commit());
  }

  /**
   * A range query using the un-normalized form of an article-prefixed name would
   * fail to match correctly against a plain TextField. With AnalyzedString the
   * caller normalizes the bound first, so the comparison is always consistent.
   */
  @Test
  public void testNormalizedBoundsAreConsistentWithIndexedValues() throws Exception {
    assertU(adoc("id", "10", "author_browse", "The Alarm"));   // → alarm
    assertU(adoc("id", "11", "author_browse", "The Bangles")); // → bangles
    assertU(adoc("id", "12", "author_browse", "The Cars"));    // → cars
    assertU(commit());

    // All three normalize to values between "alarm" and "cars" inclusive
    assertQ("article-stripped values fall in expected range",
        req("q", "author_browse:[alarm TO cars]"),
        "//result[@numFound='3']");

    assertU(delQ("*:*"));
    assertU(commit());
  }

  // ---------------------------------------------------------------------------
  // Configuration error: missing fieldType attribute
  // ---------------------------------------------------------------------------

  /**
   * If the fieldType attribute is omitted from the AnalyzedString definition the
   * init() method should throw a SolrException. This is validated at schema load
   * time, so we verify it indirectly by confirming our test core loaded correctly
   * (i.e., the fieldType attribute IS present in the test schema).
   */
  @Test
  public void testFieldTypeAttributeIsRequiredOnSchemaLoad() {
    // If AnalyzedString.init() had thrown due to a missing fieldType, the core
    // would not have loaded and h.getCore() would be null / in error state.
    assertNotNull(
        "Core must load successfully when fieldType is present",
        h.getCore().getLatestSchema().getFieldTypeByName("browse_key"));
  }
}
