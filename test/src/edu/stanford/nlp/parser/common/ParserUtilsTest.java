package edu.stanford.nlp.parser.common;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import edu.stanford.nlp.trees.Tree;

/**
 * Test the general utility methods in ParserUtils
 *
 * @author John Bauer
 */
public class ParserUtilsTest extends TestCase {
  public void testFlattenTallTrees() {
    List<Tree> trees = new ArrayList<Tree>() {{
      add(Tree.valueOf("(5)"));
      add(Tree.valueOf("(1 (2 (3 (4 (5 (6 (7 (8 (9 (10 11))))))))))"));
    }};

    List<Tree> flattened = new ArrayList<Tree>() {{
      add(Tree.valueOf("(5)"));
      add(Tree.valueOf("(X (10 11))"));
    }};

    // should be skipped because of the argument
    List<Tree> newTrees = ParserUtils.flattenTallTrees(0, trees);
    assertEquals(trees, newTrees);

    // none of the samples are > 20
    newTrees = ParserUtils.flattenTallTrees(20, trees);
    assertEquals(trees, newTrees);

    // this one should flatten the tall tree
    newTrees = ParserUtils.flattenTallTrees(5, trees);
    assertEquals(flattened, newTrees);
  }
}
