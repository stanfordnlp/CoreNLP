package edu.stanford.nlp.trees.treebank;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.trees.FilteringTreebank;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;

import junit.framework.TestCase;

public class DuplicateTreeStringFilterTest extends TestCase {
  public void testSimpleFilter() {
    MemoryTreebank treebank = new MemoryTreebank();
    treebank.add(Tree.valueOf("(ROOT (NP (NNP Chris)))"));
    treebank.add(Tree.valueOf("(ROOT (NP (NNP Chris)))"));
    treebank.add(Tree.valueOf("(ROOT (NP (NNP John)))"));

    FilteringTreebank filtered = new FilteringTreebank(treebank, new DuplicateTreeStringFilter());
    List<Tree> trees = new ArrayList<>();
    for (Tree tree : filtered) {
      trees.add(tree);
    }
    assertEquals(2, trees.size());

    assertEquals("(ROOT (NP (NNP Chris)))", trees.get(0).toString());
    assertEquals("(ROOT (NP (NNP John)))",  trees.get(1).toString());
  }  
}

