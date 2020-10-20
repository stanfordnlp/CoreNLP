package edu.stanford.nlp.pipeline;

import junit.framework.TestCase;

import java.util.Properties;

/**
 * This doesn't actually test any relation extractor abilities, actually.
 * Just tests the property initialization so far.
 *
 * @author John Bauer
 */
public class RelationExtractorAnnotatorTest extends TestCase {
  public void testVerbose() {
    assertTrue(RelationExtractorAnnotator.getVerbose(new Properties() {{
      setProperty("sup.relation.verbose", "true");
    }}));
    assertTrue(RelationExtractorAnnotator.getVerbose(new Properties() {{
      setProperty("relation.verbose", "true");
    }}));
    assertFalse(RelationExtractorAnnotator.getVerbose(new Properties()));
  }

  public void testModelName() {
    assertEquals("foo",
                 RelationExtractorAnnotator.getModelName(new Properties() {{
                   setProperty("sup.relation.model", "foo");
                 }}));
    assertEquals("foo",
                 RelationExtractorAnnotator.getModelName(new Properties() {{
                   setProperty("relation.model", "foo");
                 }}));
    assertEquals(DefaultPaths.DEFAULT_SUP_RELATION_EX_RELATION_MODEL,
                 RelationExtractorAnnotator.getModelName(new Properties()));
                 
  }
}
