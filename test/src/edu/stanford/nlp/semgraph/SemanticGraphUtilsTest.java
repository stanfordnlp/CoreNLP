package edu.stanford.nlp.semgraph;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.function.Function;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.trees.EnglishGrammaticalRelations;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;

/**
 *
 * @author Sonal Gupta
 */
public class SemanticGraphUtilsTest {

  @Test
  public void testCreateSemgrexPattern(){
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill]");

    Function<IndexedWord, String> transformNode = o ->{
      return "{word: " + o.word().toLowerCase() + "; tag: " + o.tag() +"; ner: " + o.ner() + "}";
    };

    String pat = SemanticGraphUtils.semgrexFromGraphOrderedNodes(graph, null, null, transformNode);
    assertEquals("{word: ate; tag: null; ner: null}=ate  >subj=E1 {word: bill; tag: null; ner: null}=Bill", pat.trim());
  }

  @Test
  public void testMaxIndex() {
    SemanticGraph sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-4 dep> blue-3]]");
    assertEquals(4, SemanticGraphUtils.maxIndex(sg));

    // with a weird index
    sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-5 dep> blue-3]]");
    assertEquals(5, SemanticGraphUtils.maxIndex(sg));

    sg = SemanticGraph.valueOf("[has-1 nsubj> Jennifer-0 obj> [antennae-3 dep> blue-2]]");
    assertEquals(3, SemanticGraphUtils.maxIndex(sg));

    sg = SemanticGraph.valueOf("[foo-1]");
    assertEquals(1, SemanticGraphUtils.maxIndex(sg));
  }

  @Test
  public void testMinIndex() {
    SemanticGraph sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-4 dep> blue-3]]");
    assertEquals(1, SemanticGraphUtils.minIndex(sg));

    sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-5 dep> blue-3]]");
    assertEquals(1, SemanticGraphUtils.minIndex(sg));

    sg = SemanticGraph.valueOf("[has-1 nsubj> Jennifer-0 obj> [antennae-3 dep> blue-2]]");
    assertEquals(0, SemanticGraphUtils.minIndex(sg));

    sg = SemanticGraph.valueOf("[foo-1]");
    assertEquals(1, SemanticGraphUtils.minIndex(sg));
  }
}
