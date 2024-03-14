package edu.stanford.nlp.trees;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.trees.Tree;

public class UniversalPOSMapperTest {
  @Test
  public void testMap() {
    Tree tree = Tree.valueOf("(ROOT (S (NP (DT This)) (VP (VBZ is) (NP (DT a) (JJ simple) (NN test)))))");
    Tree newTree = UniversalPOSMapper.mapTree(tree);
    Tree expected = Tree.valueOf("(ROOT (S (NP (PRON This)) (VP (AUX is) (NP (DET a) (ADJ simple) (NOUN test)))))");
    Assert.assertEquals(expected, newTree);
  }
}

