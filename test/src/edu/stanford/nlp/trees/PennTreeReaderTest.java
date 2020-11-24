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
    String treeText = "(1 (2 This)) (3 (4 is) (5 a)) (6 (\\* small) (7 \\/test)) (-LRB- -RRB-) (-LRB- =RRB=) (-LRB- =LRB=-LRB-) (asdf-LRB-asdf asdf-RRB-asdf)";
    StringReader reader = new StringReader(treeText);
    PennTreeReader treeReader = new PennTreeReader(reader);

    String[] expected = { "(1 (2 This))",
                          "(3 (4 is) (5 a))",
                          "(6 (* small) (7 /test))",
                          "(-LRB- -RRB-)",
                          "(-LRB- -RRB-)", // test =RRB= for ancora / spanish trees
                          "(-LRB- -LRB--LRB-)",
                          "(asdf-LRB-asdf asdf-RRB-asdf)" };

    for (int i = 0; i < expected.length; ++i) {
      Tree tree = treeReader.readTree();
      assertTrue(tree != null);
      assertEquals(expected[i], tree.toString());
    }
    Tree tree = treeReader.readTree();
    assertFalse(tree != null);
  }

  public void testParens() throws IOException {
    String treeText = "(-LRB- -RRB-)";
    StringReader reader = new StringReader(treeText);
    PennTreeReader treeReader = new PennTreeReader(reader);
    Tree tree = treeReader.readTree();
    assertTrue(tree != null);
    assertEquals("-LRB-", tree.label().value());
    assertEquals(")", tree.children()[0].label().value());

    assertEquals(tree.toString(), treeText);
    assertEquals(tree.pennString(), treeText + System.lineSeparator());

    assertTrue(treeReader.readTree() == null);
  }
}
