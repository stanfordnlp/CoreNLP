package edu.stanford.nlp.semgraph;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.function.Function;

import junit.framework.TestCase;
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
public class SemanticGraphUtilsTest extends TestCase {

  SemanticGraph graph;

  public void testCreateSemgrexPattern(){
    try{
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill]");

    Function<IndexedWord, String> transformNode = o ->{
      return "{word: " + o.word().toLowerCase() + "; tag: " + o.tag() +"; ner: " + o.ner() + "}";
      };

    String pat = SemanticGraphUtils.semgrexFromGraphOrderedNodes(graph, null, null, transformNode);
    assertEquals("{word: ate; tag: null; ner: null}=ate  >subj=E1 {word: bill; tag: null; ner: null}=Bill", pat.trim());
    }catch(Exception e){
      e.printStackTrace();
    }
  }

}