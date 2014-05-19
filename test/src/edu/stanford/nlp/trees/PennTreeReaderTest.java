package edu.stanford.nlp.trees;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.StringReader;

/**
 * Very simple test case - checks that reading from a reader produces
 * the right number of trees with the right text.
 *
 * @author John Bauer
 */
public class PennTreeReaderTest extends TestCase {
  public void testRead() throws IOException {
    String treeText = "(1 (2 This)) (3 (4 is) (5 a)) (6 (\\* small) (7 \\/test))";
    StringReader reader = new StringReader(treeText);
    PennTreeReader treeReader = new PennTreeReader(reader);

    String[] expected = { "(1 (2 This))",
                          "(3 (4 is) (5 a))",
                          "(6 (* small) (7 /test))" };

    for (int i = 0; i < expected.length; ++i) {
      Tree tree = treeReader.readTree();
      assertTrue(tree != null);
      assertEquals(expected[i], tree.toString());
    }
    Tree tree = treeReader.readTree();
    assertFalse(tree != null);
  }
}
