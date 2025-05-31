package edu.stanford.nlp.semgraph.semgrex;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.trees.UniversalEnglishGrammaticalRelations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.ud.CoNLLUFeatures;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;


/**
 * @author John Bauer
 */
public class SemgrexTest extends TestCase {

  public void testMatchAll() {
    SemanticGraph graph =
      SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");
    Set<IndexedWord> words = graph.vertexSet();

    SemgrexPattern pattern = SemgrexPattern.compile("{}");
    SemgrexMatcher matcher = pattern.matcher(graph);
    String[] expectedMatches = {"ate", "Bill", "muffins", "blueberry"};
    for (int i = 0; i < expectedMatches.length; ++i) {
      assertTrue(matcher.findNextMatchingNode());
    }
    assertFalse(matcher.findNextMatchingNode());
  }

  public void testTest() {
    runTest("{}", "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "ate", "Bill", "muffins", "blueberry");

    try {
      runTest("{}", "[ate subj>Bill obj>[muffins compound>blueberry]]",
              "ate", "Bill", "muffins", "foo");
      throw new RuntimeException();
    } catch (AssertionFailedError e) {
      // yay
    }

    try {
      runTest("{}", "[ate subj>Bill obj>[muffins compound>blueberry]]",
              "ate", "Bill", "muffins");
      throw new RuntimeException();
    } catch (AssertionFailedError e) {
      // yay
    }

    try {
      runTest("{}", "[ate subj>Bill obj>[muffins compound>blueberry]]",
              "ate", "Bill", "muffins", "blueberry", "blueberry");
      throw new RuntimeException();
    } catch (AssertionFailedError e) {
      // yay
    }
  }

  /**
   * This also tests negated node matches
   */
  public void testWordMatch() {
    runTest("{word:Bill}", "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "Bill");
    runTest("!{word:Bill}",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "ate", "muffins", "blueberry");
    runTest("!{word:Fred}",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "ate", "Bill", "muffins", "blueberry");
    runTest("!{word:ate}",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "Bill", "muffins", "blueberry");
    runTest("{word:/^(?!Bill).*$/}",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "ate", "muffins", "blueberry");
    runTest("{word:/^(?!Fred).*$/}",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "ate", "Bill", "muffins", "blueberry");
    runTest("{word:/^(?!ate).*$/}",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "Bill", "muffins", "blueberry");
    runTest("{word:muffins} >compound {word:blueberry}",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "muffins");
    runTest("{} << {word:ate}=a",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "Bill", "muffins", "blueberry");
    runTest("{} << !{word:ate}=a",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "blueberry");
    // blueberry should match twice because it has two ancestors
    runTest("{} << {}=a",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "Bill", "muffins", "blueberry", "blueberry");
  }

  public void testSimpleDependency() {
    // blueberry has two ancestors
    runTest("{} << {}", "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "Bill", "muffins", "blueberry", "blueberry");
    // ate has three descendants
    runTest("{} >> {}", "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "ate", "ate", "ate", "muffins");
    runTest("{} < {}", "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "Bill", "muffins", "blueberry");
    runTest("{} > {}", "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "ate", "ate", "muffins");
  }

  public void testConnected() {
    // the root should connect to all its children
    runTest("{} <> {word:ate}", "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "Bill", "muffins");
    // a node in the middle should connect to both its children and its parent
    runTest("{} <> {word:muffins}", "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "ate", "blueberry");
    // a leaf should connect to its parent
    runTest("{} <> {word:blueberry}", "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "muffins");
  }

  public void testMultipleAttributes() {
    runTest("{} >> {word:Bill}",
            "[ate subj>Bill/NNP obj>[muffins compound>blueberry]]",
            "ate");
    runTest("{} >> {tag:NNP}",
            "[ate subj>Bill/NNP obj>[muffins compound>blueberry]]",
            "ate");
    runTest("{} >> {word:Bill;tag:NNP}",
            "[ate subj>Bill/NNP obj>[muffins compound>blueberry]]",
            "ate");
    runTest("{} >> {word:Bill;tag:NNZ}",
            "[ate subj>Bill/NNP obj>[muffins compound>blueberry]]");
    runTest("{} >> {word:Ragavaniskillinglegacy;tag:NNP}",
            "[ate subj>Bill/NNP obj>[muffins compound>blueberry]]");
    runTest("{} >> {tag:NNP;word:Bill}",
            "[ate subj>Bill/NNP obj>[muffins compound>blueberry]]",
            "ate");
    runTest("{} >> {tag:NNZ;word:Bill}",
            "[ate subj>Bill/NNP obj>[muffins compound>blueberry]]");
    runTest("{} >> {tag:NNP;word:UnbanMoxOpal}",
            "[ate subj>Bill/NNP obj>[muffins compound>blueberry]]");
  }

  public void testNamedDependency() {
    runTest("{} << {word:ate}",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "Bill", "muffins", "blueberry");
    runTest("{} >> {word:blueberry}",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "ate", "muffins");
    runTest("{} >> {word:Bill}",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "ate");
    runTest("{} < {word:ate}",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "Bill", "muffins");
    runTest("{} > {word:blueberry}",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "muffins");
    runTest("{} > {word:muffins}",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "ate");
  }

  public void testNamedGovernor() {
    runTest("{word:blueberry} << {}",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "blueberry");
    runTest("{word:ate} << {}",
            "[ate subj>Bill obj>[muffins compound>blueberry]]");
    runTest("{word:blueberry} >> {}",
            "[ate subj>Bill obj>[muffins compound>blueberry]]");
    runTest("{word:muffins} >> {}",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "muffins");
    runTest("{word:Bill} >> {}",
            "[ate subj>Bill obj>[muffins compound>blueberry]]");
    runTest("{word:muffins} < {}",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "muffins");
    runTest("{word:muffins} > {}",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "muffins");
  }

  public void testTwoDependencies() {
    runTest("{} >> ({} >> {})",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "ate");
    runTest("{} >> {word:Bill} >> {word:muffins}",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "ate");
    runTest("{}=a >> {}=b >> {word:muffins}=c",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "ate", "ate", "ate");
    runTest("{}=a >> {word:Bill}=b >> {}=c",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "ate", "ate", "ate");
    runTest("{}=a >> {}=b >> {}=c",
            "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "ate", "ate", "ate", "ate", "ate",
            "ate", "ate", "ate", "ate", "muffins");
  }

  public void testRegex() {
    runTest("{word:/Bill/}", "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "Bill");

    runTest("{word:/ill/}", "[ate subj>Bill obj>[muffins compound>blueberry]]");

    runTest("{word:/.*ill/}", "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "Bill");

    runTest("{word:/.*il/}", "[ate subj>Bill obj>[muffins compound>blueberry]]");

    runTest("{word:/.*il.*/}", "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "Bill");
  }

  public void testNegatedRegex() {
    runTest("{word!:/Bill/}", "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "ate", "blueberry", "muffins");
    runTest("{word!:/.*i.*/}", "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "ate", "blueberry");
  }

  public void testBrokenContainsExpression() {
    try {
      // word is a String, not a Map, so this should throw a parse exception
      SemgrexPattern pattern = SemgrexPattern.compile("{word:{foo:bar}}");
      throw new AssertionError("Expected a SemgrexParseException");
    } catch (SemgrexParseException e) {
      // good
    }

    // this one should work.  we run it here to verify the test was
    // valid, as opposed to getting a SemgrexParseException because
    // this wasn't even following proper contains syntax
    SemgrexPattern pattern = SemgrexPattern.compile("{morphofeatures:{foo:bar}}");
  }

  public void testContainsExpression() {
    // morphofeatures is a Map, so this should work
    SemgrexPattern pattern = SemgrexPattern.compile("{morphofeatures:{foo:bar}}");
    SemanticGraph graph = makeComplicatedGraph();
    Set<IndexedWord> vertices = graph.vertexSet();
    for (IndexedWord iw : vertices) {
      if (iw.value().equals("D") || iw.value().equals("F")) {
        CoNLLUFeatures feats = new CoNLLUFeatures();
        feats.put("foo", "bar");
        iw.set(CoreAnnotations.CoNLLUFeats.class, feats);
      }
    }
    runTest(pattern, graph, "D", "F");
  }

  public void testContainsRegexKeyExpression() {
    // morphofeatures is a Map, so this should work
    SemgrexPattern pattern = SemgrexPattern.compile("{morphofeatures:{/foo/:bar}}");
    SemanticGraph graph = makeComplicatedGraph();
    Set<IndexedWord> vertices = graph.vertexSet();
    for (IndexedWord iw : vertices) {
      if (iw.value().equals("D") || iw.value().equals("F")) {
        CoNLLUFeatures feats = new CoNLLUFeatures();
        feats.put("foo", "bar");
        iw.set(CoreAnnotations.CoNLLUFeats.class, feats);
      }
    }
    runTest(pattern, graph, "D", "F");
  }

  public void testContainsRegexKeyPartialMatchExpression() {
    // morphofeatures is a Map, so this should work
    SemgrexPattern pattern = SemgrexPattern.compile("{morphofeatures:{/.*o.*/:bar}}");
    SemanticGraph graph = makeComplicatedGraph();
    Set<IndexedWord> vertices = graph.vertexSet();
    for (IndexedWord iw : vertices) {
      if (iw.value().equals("D") || iw.value().equals("F")) {
        CoNLLUFeatures feats = new CoNLLUFeatures();
        feats.put("foo", "bar");
        iw.set(CoreAnnotations.CoNLLUFeats.class, feats);
      }
    }
    runTest(pattern, graph, "D", "F");
  }

  public void testContainsRegexKeyMultipleMatchExpression() {
    // morphofeatures is a Map, so this should work
    SemgrexPattern pattern = SemgrexPattern.compile("{morphofeatures:{/.*o.*/:bar}}");
    SemanticGraph graph = makeComplicatedGraph();
    Set<IndexedWord> vertices = graph.vertexSet();
    for (IndexedWord iw : vertices) {
      if (iw.value().equals("D") || iw.value().equals("F")) {
        CoNLLUFeatures feats = new CoNLLUFeatures();
        feats.put("zoo", "car");
        feats.put("foo", "bar");
        iw.set(CoreAnnotations.CoNLLUFeats.class, feats);
      }
      if (iw.value().equals("C") || iw.value().equals("E")) {
        CoNLLUFeatures feats = new CoNLLUFeatures();
        feats.put("zoo", "car");
        iw.set(CoreAnnotations.CoNLLUFeats.class, feats);
      }
    }
    runTest(pattern, graph, "D", "F");
  }

  public void testContainsRegexKeyNegatedMatchExpression() {
    // morphofeatures is a Map, so this should work
    SemgrexPattern pattern = SemgrexPattern.compile("{morphofeatures:{/.*o.*/!:bar}}");
    SemanticGraph graph = makeComplicatedGraph();
    Set<IndexedWord> vertices = graph.vertexSet();
    for (IndexedWord iw : vertices) {
      if (iw.value().equals("D") || iw.value().equals("F")) {
        CoNLLUFeatures feats = new CoNLLUFeatures();
        feats.put("zoo", "car");
        feats.put("foo", "bar");
        iw.set(CoreAnnotations.CoNLLUFeats.class, feats);
      }
      if (iw.value().equals("C") || iw.value().equals("E")) {
        CoNLLUFeatures feats = new CoNLLUFeatures();
        feats.put("zoo", "car");
        iw.set(CoreAnnotations.CoNLLUFeats.class, feats);
      }
    }
    runTest(pattern, graph, "A", "B", "C", "E", "G", "H", "I", "J");
  }

  public void testContainsRegexExpression() {
    // morphofeatures is a Map, so this should work
    SemanticGraph graph = makeComplicatedGraph();
    Set<IndexedWord> vertices = graph.vertexSet();
    for (IndexedWord iw : vertices) {
      if (iw.value().equals("B") || iw.value().equals("D") || iw.value().equals("F")) {
        CoNLLUFeatures feats = new CoNLLUFeatures();
        feats.put("foo", "bar" + iw.value());
        iw.set(CoreAnnotations.CoNLLUFeats.class, feats);
      }
    }

    // test a positive regex
    SemgrexPattern pattern = SemgrexPattern.compile("{morphofeatures:{foo:/bar[BD]/}}");
    runTest(pattern, graph, "B", "D");

    // test a negative regex
    // should match both the ones that don't have features
    // and the ones that have a non-matching feature
    pattern = SemgrexPattern.compile("{morphofeatures:{foo!:/bar[BD]/}}");
    runTest(pattern, graph, "A", "C", "E", "F", "G", "H", "I", "J");
  }

  public void testDoubleContainsExpression() {
    // morphofeatures is a Map, so this should work
    SemanticGraph graph = makeComplicatedGraph();
    Set<IndexedWord> vertices = graph.vertexSet();
    for (IndexedWord iw : vertices) {
      if (iw.value().equals("B") || iw.value().equals("D") || iw.value().equals("F")) {
        CoNLLUFeatures feats = new CoNLLUFeatures();
        feats.put("foo", "bar");
        feats.put("name", iw.value());
        iw.set(CoreAnnotations.CoNLLUFeats.class, feats);
      }
    }

    // test a positive regex
    SemgrexPattern pattern = SemgrexPattern.compile("{morphofeatures:{foo:/bar/;name:/[BD]/}}");
    runTest(pattern, graph, "B", "D");

    // test one positive, one negative regex
    pattern = SemgrexPattern.compile("{morphofeatures:{foo:/bar/;name!:/[BD]/}}");
    runTest(pattern, graph, "F");
  }

  public void testReferencedRegex() {
    runTest("{word:/Bill/}", "[ate subj>Bill obj>[bill det>the]]",
            "Bill");

    runTest("{word:/.*ill/}", "[ate subj>Bill obj>[bill det>the]]",
            "Bill", "bill");

    runTest("{word:/[Bb]ill/}", "[ate subj>Bill obj>[bill det>the]]",
            "Bill", "bill");

    // TODO: implement referencing regexes
  }

  /**
   * Make a fake graph with a bunch of random dependencies
   *<br>
   * All dependencies go from an earlier letter to a later letter except J to I.
   * Having at least one dependency go the other way allows for testing
   * of certain relationships involving direction
   */
  public static SemanticGraph makeComplicatedGraph() {
    SemanticGraph graph = new SemanticGraph();
    String[] words = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J"};
    IndexedWord[] nodes = new IndexedWord[words.length];
    for (int i = 0; i < words.length; ++i) {
      IndexedWord word = new IndexedWord("test", 1, i + 1);
      word.setWord(words[i]);
      word.setValue(words[i]);
      nodes[i] = word;
      graph.addVertex(word);
    }
    graph.setRoot(nodes[0]);
    // this graph isn't supposed to make sense
    graph.addEdge(nodes[0], nodes[1],
                  UniversalEnglishGrammaticalRelations.MODIFIER, 1.0, false);
    graph.addEdge(nodes[0], nodes[2],
                  UniversalEnglishGrammaticalRelations.DIRECT_OBJECT, 1.0, false);
    graph.addEdge(nodes[0], nodes[3],
                  UniversalEnglishGrammaticalRelations.INDIRECT_OBJECT, 1.0, false);
    graph.addEdge(nodes[1], nodes[4],
                  UniversalEnglishGrammaticalRelations.MARKER, 1.0, false);
    graph.addEdge(nodes[2], nodes[4],
                  UniversalEnglishGrammaticalRelations.EXPLETIVE, 1.0, false);
    graph.addEdge(nodes[3], nodes[4],
                  UniversalEnglishGrammaticalRelations.CLAUSAL_COMPLEMENT, 1.0, false);
    graph.addEdge(nodes[4], nodes[5],
                  UniversalEnglishGrammaticalRelations.ADJECTIVAL_MODIFIER, 1.0, false);
    graph.addEdge(nodes[4], nodes[6],
                  UniversalEnglishGrammaticalRelations.ADVERBIAL_MODIFIER, 1.0, false);
    graph.addEdge(nodes[4], nodes[9],
                  UniversalEnglishGrammaticalRelations.MODIFIER, 1.0, false);
    graph.addEdge(nodes[5], nodes[7],
                  UniversalEnglishGrammaticalRelations.POSSESSION_MODIFIER, 1.0, false);
    graph.addEdge(nodes[6], nodes[7],
                  UniversalEnglishGrammaticalRelations.CASE_MARKER, 1.0, false);
    graph.addEdge(nodes[7], nodes[9],
                  UniversalEnglishGrammaticalRelations.AGENT, 1.0, false);
    graph.addEdge(nodes[9], nodes[8],
                  UniversalEnglishGrammaticalRelations.DETERMINER, 1.0, false);

    return graph;
  }

  /**
   * Test that governors, dependents, ancestors, descendants are all
   * returned with multiplicity 1 if there are multiple paths to the
   * same node.
   */
  public void testComplicatedGraph() {
    SemanticGraph graph = makeComplicatedGraph();

    runTest("{} < {word:A}", graph,
            "B", "C", "D");

    runTest("{} > {word:E}", graph,
            "B", "C", "D");

    runTest("{} > {word:I}", graph,
            "J");

    runTest("{} < {word:E}", graph,
            "F", "G", "J");

    runTest("{} < {word:J}", graph,
            "I");

    runTest("{} << {word:A}", graph,
            "B", "C", "D", "E", "F", "G", "H", "I", "J");

    runTest("{} << {word:B}", graph,
            "E", "F", "G", "H", "I", "J");

    runTest("{} << {word:C}", graph,
            "E", "F", "G", "H", "I", "J");

    runTest("{} << {word:D}", graph,
            "E", "F", "G", "H", "I", "J");

    runTest("{} << {word:E}", graph,
            "F", "G", "H", "I", "J");

    runTest("{} << {word:F}", graph,
            "H", "I", "J");

    runTest("{} << {word:G}", graph,
            "H", "I", "J");

    runTest("{} << {word:H}", graph,
            "I", "J");

    runTest("{} << {word:J}", graph,
            "I");

    runTest("{} << {word:I}", graph);

    runTest("{} << {word:K}", graph);

    runTest("{} >> {word:A}", graph);

    runTest("{} >> {word:B}", graph, "A");

    runTest("{} >> {word:C}", graph, "A");

    runTest("{} >> {word:D}", graph, "A");

    runTest("{} >> {word:E}", graph,
            "A", "B", "C", "D");

    runTest("{} >> {word:F}", graph,
            "A", "B", "C", "D", "E");

    runTest("{} >> {word:G}", graph,
            "A", "B", "C", "D", "E");

    runTest("{} >> {word:H}", graph,
            "A", "B", "C", "D", "E", "F", "G");

    runTest("{} >> {word:J}", graph,
            "A", "B", "C", "D", "E", "F", "G", "H");

    runTest("{} >> {word:I}", graph,
            "A", "B", "C", "D", "E", "F", "G", "H", "J");

    runTest("{} >> {word:K}", graph);
  }

  public void testRelationType() {
    SemanticGraph graph = makeComplicatedGraph();
    runTest("{} <<mod {}", graph,
            "B", "E", "F", "G", "H", "I", "I", "J", "J");

    runTest("{} >>det {}", graph,
            "A", "B", "C", "D", "E", "F", "G", "H", "J");

    runTest("{} >>det {word:I}", graph,
            "A", "B", "C", "D", "E", "F", "G", "H", "J");
  }

  public void testExactDepthRelations() {
    SemanticGraph graph = makeComplicatedGraph();
    runTest("{} 2,3<< {word:A}", graph, "E", "F", "G", "J");

    runTest("{} 2,2<< {word:A}", graph, "E");

    runTest("{} 1,2<< {word:A}", graph, "B", "C", "D", "E");

    runTest("{} 0,2<< {word:A}", graph, "B", "C", "D", "E");

    runTest("{} 0,10<< {word:A}", graph,
            "B", "C", "D", "E", "F", "G", "H", "I", "J");

    runTest("{} 0,10>> {word:I}", graph,
            "A", "B", "C", "D", "E", "F", "G", "H", "J");

    runTest("{} 2,3>> {word:I}", graph,
            "B", "C", "D", "E", "F", "G", "H");

    runTest("{} 2,2>> {word:I}", graph,
            "E", "H");

    // use this method to avoid the toString() test, since we expect it
    // to use 2,2>> instead of 2>>
    runTest(SemgrexPattern.compile("{} 2>> {word:I}"), graph,
            "E", "H");

    runTest("{} 1,2>> {word:I}", graph,
            "E", "H", "J");
  }

  /**
   * Tests that if there are different paths from A to I, those paths show up for exactly the right depths
   */
  public void testMultipleDepths() {
    SemanticGraph graph = makeComplicatedGraph();
    runTest("{} 3,3<< {word:A}", graph, "F", "G", "J");
    runTest("{} 4,4<< {word:A}", graph, "H", "I");
    runTest("{} 5,5<< {word:A}", graph, "J");
    runTest("{} 6,6<< {word:A}", graph, "I");
  }

  /** After making UNIQ a separate token in the parser, we should verify that "uniq" can be treated as an identifier as well */
  public void testUniqNamedNode() {
    SemanticGraph graph = makeComplicatedGraph();

    runTest("{} >obj ({} >expl {})", graph, "A");

    SemgrexPattern pattern =
      SemgrexPattern.compile("{} >obj ({} >expl {}=uniq)");
    SemgrexMatcher matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals(1, matcher.getNodeNames().size());
    assertEquals("E", matcher.getNode("uniq").toString());
    assertEquals("A", matcher.getMatch().toString());
    assertFalse(matcher.find());
  }

  public void testNamedNode() {
    SemanticGraph graph = makeComplicatedGraph();

    runTest("{} >obj ({} >expl {})", graph, "A");

    SemgrexPattern pattern =
      SemgrexPattern.compile("{} >obj ({} >expl {}=foo)");
    SemgrexMatcher matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals(1, matcher.getNodeNames().size());
    assertEquals("E", matcher.getNode("foo").toString());
    assertEquals("A", matcher.getMatch().toString());
    assertFalse(matcher.find());

    pattern = SemgrexPattern.compile("{} >obj ({} >expl {}=foo) >mod {}");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals(1, matcher.getNodeNames().size());
    assertEquals("E", matcher.getNode("foo").toString());
    assertEquals("A", matcher.getMatch().toString());
    assertFalse(matcher.find());

    pattern =
      SemgrexPattern.compile("{} >obj ({} >expl {}=foo) >mod ({} >mark {})");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals(1, matcher.getNodeNames().size());
    assertEquals("E", matcher.getNode("foo").toString());
    assertEquals("A", matcher.getMatch().toString());
    assertFalse(matcher.find());

    pattern =
      SemgrexPattern.compile("{} >obj ({} >expl {}=foo) >mod ({} > {})");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals(1, matcher.getNodeNames().size());
    assertEquals("E", matcher.getNode("foo").toString());
    assertEquals("A", matcher.getMatch().toString());
    assertFalse(matcher.find());

    pattern =
      SemgrexPattern.compile("{} >obj ({} >expl {}=foo) >mod ({} > {}=foo)");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals(1, matcher.getNodeNames().size());
    assertEquals("E", matcher.getNode("foo").toString());
    assertEquals("A", matcher.getMatch().toString());
    assertFalse(matcher.find());

    pattern =
      SemgrexPattern.compile("{} >obj ({} >expl {}=foo) >mod ({}=foo > {})");
    matcher = pattern.matcher(graph);
    assertFalse(matcher.find());
  }

  public void testPartition() {
    SemanticGraph graph = makeComplicatedGraph();

    runTest("{}=a >> {word:E}", graph, "A", "B", "C", "D");
    SemgrexPattern pattern = SemgrexPattern.compile("{}=a >> {word:E} : {}=a >> {word:B}");
    runTest("{}=a >> {word:E} : {}=a >> {word:B}", graph, "A");
  }

  public void testEqualsRelation() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");
    SemgrexPattern pattern = SemgrexPattern.compile("{} >> ({}=a == {}=b)");
    SemgrexMatcher matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals(2, matcher.getNodeNames().size());
    assertEquals("ate", matcher.getMatch().toString());
    assertEquals("Bill", matcher.getNode("a").toString());
    assertEquals("Bill", matcher.getNode("b").toString());

    assertTrue(matcher.find());
    assertEquals(2, matcher.getNodeNames().size());
    assertEquals("ate", matcher.getMatch().toString());
    assertEquals("muffins", matcher.getNode("a").toString());
    assertEquals("muffins", matcher.getNode("b").toString());

    assertTrue(matcher.find());
    assertEquals(2, matcher.getNodeNames().size());
    assertEquals("ate", matcher.getMatch().toString());
    assertEquals("blueberry", matcher.getNode("a").toString());
    assertEquals("blueberry", matcher.getNode("b").toString());

    assertTrue(matcher.find());
    assertEquals(2, matcher.getNodeNames().size());
    assertEquals("muffins", matcher.getMatch().toString());
    assertEquals("blueberry", matcher.getNode("a").toString());
    assertEquals("blueberry", matcher.getNode("b").toString());

    assertFalse(matcher.find());

    // This split pattern should also work
    pattern = SemgrexPattern.compile("{} >> {}=a >> {}=b : {}=a == {}=b");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals(2, matcher.getNodeNames().size());
    assertEquals("ate", matcher.getMatch().toString());
    assertEquals("Bill", matcher.getNode("a").toString());
    assertEquals("Bill", matcher.getNode("b").toString());

    assertTrue(matcher.find());
    assertEquals(2, matcher.getNodeNames().size());
    assertEquals("ate", matcher.getMatch().toString());
    assertEquals("muffins", matcher.getNode("a").toString());
    assertEquals("muffins", matcher.getNode("b").toString());

    assertTrue(matcher.find());
    assertEquals(2, matcher.getNodeNames().size());
    assertEquals("ate", matcher.getMatch().toString());
    assertEquals("blueberry", matcher.getNode("a").toString());
    assertEquals("blueberry", matcher.getNode("b").toString());

    assertTrue(matcher.find());
    assertEquals(2, matcher.getNodeNames().size());
    assertEquals("muffins", matcher.getMatch().toString());
    assertEquals("blueberry", matcher.getNode("a").toString());
    assertEquals("blueberry", matcher.getNode("b").toString());

    assertFalse(matcher.find());
  }

  /**
   * In this test, the graph should find matches with pairs of nodes
   * which are different from each other.  Since "muffins" only has
   * one dependent, there should not be any matches with "muffins" as
   * the head, for example.
   */
  public void testNotEquals() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");

    SemgrexPattern pattern = SemgrexPattern.compile("{} >> {}=a >> {}=b : {}=a !== {}=b");
    SemgrexMatcher matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals(2, matcher.getNodeNames().size());
    assertEquals("ate", matcher.getMatch().toString());
    assertEquals("Bill", matcher.getNode("a").toString());
    assertEquals("muffins", matcher.getNode("b").toString());

    assertTrue(matcher.find());
    assertEquals(2, matcher.getNodeNames().size());
    assertEquals("ate", matcher.getMatch().toString());
    assertEquals("Bill", matcher.getNode("a").toString());
    assertEquals("blueberry", matcher.getNode("b").toString());

    assertTrue(matcher.find());
    assertEquals(2, matcher.getNodeNames().size());
    assertEquals("ate", matcher.getMatch().toString());
    assertEquals("muffins", matcher.getNode("a").toString());
    assertEquals("Bill", matcher.getNode("b").toString());

    assertTrue(matcher.find());
    assertEquals(2, matcher.getNodeNames().size());
    assertEquals("ate", matcher.getMatch().toString());
    assertEquals("muffins", matcher.getNode("a").toString());
    assertEquals("blueberry", matcher.getNode("b").toString());

    assertTrue(matcher.find());
    assertEquals(2, matcher.getNodeNames().size());
    assertEquals("ate", matcher.getMatch().toString());
    assertEquals("blueberry", matcher.getNode("a").toString());
    assertEquals("Bill", matcher.getNode("b").toString());

    assertTrue(matcher.find());
    assertEquals(2, matcher.getNodeNames().size());
    assertEquals("ate", matcher.getMatch().toString());
    assertEquals("blueberry", matcher.getNode("a").toString());
    assertEquals("muffins", matcher.getNode("b").toString());

    assertFalse(matcher.find());

    // same as the first test, essentially, but with a more compact expression
    pattern = SemgrexPattern.compile("{} >> {}=a >> ({}=b !== {}=a)");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals(2, matcher.getNodeNames().size());
    assertEquals("ate", matcher.getMatch().toString());
    assertEquals("Bill", matcher.getNode("a").toString());
    assertEquals("muffins", matcher.getNode("b").toString());

    assertTrue(matcher.find());
    assertEquals(2, matcher.getNodeNames().size());
    assertEquals("ate", matcher.getMatch().toString());
    assertEquals("Bill", matcher.getNode("a").toString());
    assertEquals("blueberry", matcher.getNode("b").toString());

    assertTrue(matcher.find());
    assertEquals(2, matcher.getNodeNames().size());
    assertEquals("ate", matcher.getMatch().toString());
    assertEquals("muffins", matcher.getNode("a").toString());
    assertEquals("Bill", matcher.getNode("b").toString());

    assertTrue(matcher.find());
    assertEquals(2, matcher.getNodeNames().size());
    assertEquals("ate", matcher.getMatch().toString());
    assertEquals("muffins", matcher.getNode("a").toString());
    assertEquals("blueberry", matcher.getNode("b").toString());

    assertTrue(matcher.find());
    assertEquals(2, matcher.getNodeNames().size());
    assertEquals("ate", matcher.getMatch().toString());
    assertEquals("blueberry", matcher.getNode("a").toString());
    assertEquals("Bill", matcher.getNode("b").toString());

    assertTrue(matcher.find());
    assertEquals(2, matcher.getNodeNames().size());
    assertEquals("ate", matcher.getMatch().toString());
    assertEquals("blueberry", matcher.getNode("a").toString());
    assertEquals("muffins", matcher.getNode("b").toString());

    assertFalse(matcher.find());
  }

  public void testInitialConditions() {
    SemanticGraph graph = makeComplicatedGraph();

    SemgrexPattern pattern =
      SemgrexPattern.compile("{}=a >> {}=b : {}=a >> {}=c");
    Map<String, IndexedWord> variables = new HashMap<>();
    variables.put("b", graph.getNodeByIndex(5));
    variables.put("c", graph.getNodeByIndex(2));
    SemgrexMatcher matcher = pattern.matcher(graph, variables);
    assertTrue(matcher.find());
    assertEquals(3, matcher.getNodeNames().size());
    assertEquals("A", matcher.getNode("a").toString());
    assertEquals("E", matcher.getNode("b").toString());
    assertEquals("B", matcher.getNode("c").toString());
    assertEquals("A", matcher.getMatch().toString());
    assertFalse(matcher.find());
  }

  /**
   * Test that a particular AnnotationLookup is honored
   */
  public void testIndex() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");
    runTest("{idx:0}", graph, "ate");
    runTest("{idx:1}", graph, "Bill");
    runTest("{idx:2}", graph, "muffins");
    runTest("{idx:3}", graph, "blueberry");
    runTest("{idx:4}", graph);
  }

  public void testLemma() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");
    for (IndexedWord word : graph.vertexSet()) {
      word.setLemma(word.word());
    }
    runTest("{lemma:ate}", graph, "ate");
    runTest("{lemma:Bill}", graph, "Bill");
  }

  /** tests a deprecated version - might as well check that it still functions, since it still exists */
  public void testCCLemma() {
    Tree tree = Tree.valueOf("(ROOT (S (NP (PRP I)) (VP (VBP love) (NP (DT the) (NN display))) (. .)))");
    @SuppressWarnings("deprecation")
    SemanticGraph graph = SemanticGraphFactory.generateCCProcessedDependencies(tree);
    for (IndexedWord word : graph.vertexSet()) {
      word.setLemma(word.word());
    }
    // This set of three tests also provides some coverage for a
    // bizarre error a user found where multiple copies of the same
    // IndexedWord were created
    runTest("{}=Obj <obj {lemma:love}=Pred", graph, "display/NN");
    runTest("{}=Obj <obj {}=Pred", graph, "display/NN");
    runTest("{lemma:love}=Pred >obj {}=Obj ", graph, "love/VBP");
  }

  public void testNamedRelation() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");
    SemgrexPattern pattern = SemgrexPattern.compile("{idx:0}=gov >>~foo {idx:3}=dep");
    SemgrexMatcher matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals("ate", matcher.getNode("gov").toString());
    assertEquals("blueberry", matcher.getNode("dep").toString());
    assertEquals("compound", matcher.getRelnString("foo"));
    assertFalse(matcher.find());

    pattern = SemgrexPattern.compile("{idx:3}=dep <<~foo {idx:0}=gov");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals("ate", matcher.getNode("gov").toString());
    assertEquals("blueberry", matcher.getNode("dep").toString());
    assertEquals("obj", matcher.getRelnString("foo"));
    assertFalse(matcher.find());

    pattern = SemgrexPattern.compile("{idx:3}=dep <~foo {idx:2}=gov");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals("muffins", matcher.getNode("gov").toString());
    assertEquals("blueberry", matcher.getNode("dep").toString());
    assertEquals("compound", matcher.getRelnString("foo"));
    assertFalse(matcher.find());

    pattern = SemgrexPattern.compile("{idx:2}=gov >~foo {idx:3}=dep");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals("muffins", matcher.getNode("gov").toString());
    assertEquals("blueberry", matcher.getNode("dep").toString());
    assertEquals("compound", matcher.getRelnString("foo"));
    assertFalse(matcher.find());
  }

  /** named edges should also have the named reference functionality, as long as you are testing a parent or child relation */
  public void testNamedRelationEdge() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");
    SemgrexPattern pattern = SemgrexPattern.compile("{idx:2}=gov >=foo {idx:3}=dep");
    SemgrexMatcher matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals("muffins", matcher.getNode("gov").toString());
    assertEquals("blueberry", matcher.getNode("dep").toString());
    assertEquals("compound", matcher.getRelnString("foo"));
    assertFalse(matcher.find());

    // it should accept this once, going from root to the dep node,
    // as the ~foo will require that it have the same edge label as =foo
    pattern = SemgrexPattern.compile("{idx:2}=gov >=foo {idx:3}=dep : {$}=root >>~foo {}");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals("ate", matcher.getNode("root").toString());
    assertEquals("muffins", matcher.getNode("gov").toString());
    assertEquals("blueberry", matcher.getNode("dep").toString());
    assertEquals("compound", matcher.getRelnString("foo"));
    assertFalse(matcher.find());
  }

  /**
   * The named relation feature should incorporate backreferences
   */
  public void testNamedRelationBackreference() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");

    SemgrexPattern pattern = SemgrexPattern.compile("{}=A >~foo ({}=B >~foo {}=C)");
    SemgrexMatcher matcher = pattern.matcher(graph);
    assertFalse(matcher.find());

    graph = SemanticGraph.valueOf("[ate dep> [Bill dep> cat]]");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals("ate", matcher.getNode("A").toString());
    assertEquals("Bill", matcher.getNode("B").toString());
    assertEquals("cat", matcher.getNode("C").toString());
    assertEquals("dep", matcher.getRelnString("foo"));
    assertFalse(matcher.find());

    graph = SemanticGraph.valueOf("[ate cop> [Bill dep> cat]]");
    matcher = pattern.matcher(graph);
    assertFalse(matcher.find());

    graph = SemanticGraph.valueOf("[ate dep> [Bill cop> cat]]");
    matcher = pattern.matcher(graph);
    assertFalse(matcher.find());

    graph = SemanticGraph.valueOf("[antennae amod> big amod> blue]");
    pattern = SemgrexPattern.compile("{}=A >~foo {}=B >~foo ({}=C !== {}=B)");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals("antennae", matcher.getNode("A").toString());
    assertEquals("big", matcher.getNode("B").toString());
    assertEquals("blue", matcher.getNode("C").toString());
    assertEquals("amod", matcher.getRelnString("foo"));

    assertTrue(matcher.find());
    assertEquals("antennae", matcher.getNode("A").toString());
    assertEquals("blue", matcher.getNode("B").toString());
    assertEquals("big", matcher.getNode("C").toString());
    assertEquals("amod", matcher.getRelnString("foo"));

    assertFalse(matcher.find());

    graph = SemanticGraph.valueOf("[antennae amod> big dep> blue]");
    pattern = SemgrexPattern.compile("{}=A >~foo {}=B >~foo ({}=C !== {}=B)");
    matcher = pattern.matcher(graph);
    assertFalse(matcher.find());
  }

  /**
   * Test the named edge feature, including backreferences
   */
  public void testNamedEdgeGovernor() {
    // Test a simple version of the named edge search
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");
    SemgrexPattern pattern = SemgrexPattern.compile("{}=A >subj=foo {}=B");
    SemgrexMatcher matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    SemanticGraphEdge edge = matcher.getEdge("foo");
    assertTrue(edge != null);
    assertEquals(edge.getSource(), matcher.getNode("A"));
    assertEquals(edge.getSource().word(), "ate");
    assertEquals(edge.getTarget(), matcher.getNode("B"));
    assertEquals(edge.getTarget().word(), "Bill");
    assertEquals(edge.getRelation().getShortName(), "subj");
    assertFalse(matcher.find());

    // Test the expected behavior of a pattern without backref
    graph = SemanticGraph.valueOf("[antennae amod> big amod> blue]");
    pattern = SemgrexPattern.compile("{}=A > {}=B > {}=C");
    matcher = pattern.matcher(graph);
    // two children, iterate for both halves of the expression,
    // so there should be 4 matches total
    assertTrue(matcher.find());
    assertTrue(matcher.find());
    assertTrue(matcher.find());
    assertTrue(matcher.find());
    assertFalse(matcher.find());

    // Test the expected behavior of a pattern *with* backref
    graph = SemanticGraph.valueOf("[antennae amod> big amod> blue]");
    pattern = SemgrexPattern.compile("{}=A >=foo {}=B >=foo {}=C");
    matcher = pattern.matcher(graph);
    // this time it should only accept when the edges are the same
    assertTrue(matcher.find());
    edge = matcher.getEdge("foo");
    assertTrue(edge != null);
    assertEquals(edge.getSource(), matcher.getNode("A"));
    assertEquals(edge.getTarget(), matcher.getNode("B"));
    assertEquals(matcher.getNode("B"), matcher.getNode("C"));
    assertTrue(matcher.find());
    edge = matcher.getEdge("foo");
    assertTrue(edge != null);
    assertEquals(edge.getSource(), matcher.getNode("A"));
    assertEquals(edge.getTarget(), matcher.getNode("C"));
    assertEquals(matcher.getNode("B"), matcher.getNode("C"));
    assertFalse(matcher.find());
  }

  /**
   * Short test that the dependent edge matching is working as well
   */
  public void testNamedEdgeDependent() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");
    SemgrexPattern pattern = SemgrexPattern.compile("{}=A <subj=foo {}=B");
    SemgrexMatcher matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    SemanticGraphEdge edge = matcher.getEdge("foo");
    assertTrue(edge != null);
    assertEquals(edge.getSource(), matcher.getNode("B"));
    assertEquals(edge.getSource().word(), "ate");
    assertEquals(edge.getTarget(), matcher.getNode("A"));
    assertEquals(edge.getTarget().word(), "Bill");
    assertEquals(edge.getRelation().getShortName(), "subj");
    assertFalse(matcher.find());
  }

  /**
   * Short test that the left/right versions of governor and dependent do the right thing
   */
  public void testNamedEdgeLeftRight() {
    SemanticGraph graph = SemanticGraph.valueOf("[antennae-2 amod> blue-1 nmod> [head-5 case> on-3 nmod:poss> her-4]]");
    SemgrexPattern pattern = SemgrexPattern.compile("{$}=A >--=foo {}=B");
    SemgrexMatcher matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    SemanticGraphEdge edge = matcher.getEdge("foo");
    assertTrue(edge != null);
    assertEquals(edge.getSource().word(), "antennae");
    assertEquals(edge.getTarget().word(), "blue");
    assertFalse(matcher.find());

    pattern = SemgrexPattern.compile("{$}=A >++=foo {}=B");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    edge = matcher.getEdge("foo");
    assertTrue(edge != null);
    assertEquals(edge.getSource().word(), "antennae");
    assertEquals(edge.getTarget().word(), "head");
    assertFalse(matcher.find());

    pattern = SemgrexPattern.compile("{}=A <++=foo {$}=B");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    edge = matcher.getEdge("foo");
    assertTrue(edge != null);
    assertEquals(edge.getSource().word(), "antennae");
    assertEquals(edge.getTarget().word(), "blue");
    assertFalse(matcher.find());

    pattern = SemgrexPattern.compile("{}=A <--=foo {$}=B");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    edge = matcher.getEdge("foo");
    assertTrue(edge != null);
    assertEquals(edge.getSource().word(), "antennae");
    assertEquals(edge.getTarget().word(), "head");
    assertFalse(matcher.find());
  }

  /**
   * Relations other than the gov / dep relations should not allow a named edge
   */
  public void testNamedEdgeException() {
    try {
      SemgrexPattern pattern = SemgrexPattern.compile("{} <<=foo {}");
      // an unexpected exception would not be caught
      // failing to throw an exception falls through to the error
      throw new AssertionError("Expected a SemgrexParseException");
    } catch (SemgrexParseException e) {
      // good
    }
  }

  public void testAttributeConjunction() {
    // A possible user submitted error: https://github.com/stanfordnlp/CoreNLP/issues/552
    // A match with both POS and word labeled should have both attributes on the same node
    String pattern = "{$} > {pos:JJS;word:most}";
    // check that the compiled pattern is the same as the input pattern    
    comparePatternToString(pattern);
    SemgrexPattern semgrex = SemgrexPattern.compile(pattern);

    // root is "foo", has 3 children with various relations
    SemanticGraph graph = SemanticGraph.valueOf("[foo obj> most subj> bar dep> asdf]");
    // with no POS, should have no matches
    runTest(semgrex, graph);
    // index 1 is "most".  should match at the root
    graph.getNodeByIndex(1).setTag("JJS");
    runTest(semgrex, graph, "foo");
    // sanity check: should stop matching with the child set differently
    graph.getNodeByIndex(1).setTag("NN");
    runTest(semgrex, graph);
    // 1st word "most", 2nd word "_JJS".  Should not match
    graph.getNodeByIndex(2).setTag("JJS");
    runTest(semgrex, graph);
    // should now match at the root, as the second word is now "most_JJS"
    graph.getNodeByIndex(2).setWord("most");
    runTest(semgrex, graph, "foo");
  }

  /** Test some variations on negated attributes using negative lookahead regex */
  public void testNegatedAttribute() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");
    runTest("{word:/^(?!Bill).*$/}", graph,
            "ate", "muffins", "blueberry");
    graph.getNodeByIndex(0).setTag("NN");
    graph.getNodeByIndex(1).setTag("NN");
    graph.getNodeByIndex(2).setTag("JJS");
    graph.getNodeByIndex(3).setTag("NN");

    // find the JJS
    runTest("{pos:JJS}", graph,
            "muffins/JJS");
    // find any JJS with the text "muffins"
    runTest("{pos:JJS;word:muffins}", graph,
            "muffins/JJS");
    // find any JJS which is not "Bill"
    runTest("{pos:JJS;word:/^(?!Bill).*$/}", graph,
            "muffins/JJS");
    // find any JJS which is not "muffins": should be empty
    runTest("{pos:JJS;word:/^(?!muffins).*$/}", graph);      
    // find any not NN which is "muffins"
    runTest("{pos:/^(?!NN).*$/;word:muffins}", graph,
            "muffins/JJS");
    // find any not JJS which is not "Bill"
    runTest("{pos:/^(?!JJS).*$/;word:/^(?!Bill).*$/}", graph,
            "ate/NN", "blueberry/NN");
  }

  public void testTwoWordConstraints() {
    // Another part of issue 552:
    // "{$} > { word:She; word:hello }"
    // it shouldn't find anything because of conflicting constraints
    // originally it did because the attributes were stored in a map,
    // which meant word:hello clobbered word:She
    // We fix this issue by making such a state throw an exception.
    SemanticGraph graph = SemanticGraph.valueOf("[said subj>She obj>hello]");
    String pattern = "{$} > {word:She;word:hello}";
    try {
      SemgrexPattern semgrex = SemgrexPattern.compile(pattern);
      throw new RuntimeException("This was supposed to fail horribly");
    } catch (SemgrexParseException e) {
      // yay
    }
  }

  /**
   * Verify that this is working for a KBP query which wasn't working
   * for some reason... at least it wasn't the semgrex
   */
  public void testNERAttribute() {
    SemanticGraph graph = SemanticGraph.valueOf("[Young appos>[director nmod:of>Association]]]");
    graph.getNodeByIndex(0).setNER("PERSON");
    graph.getNodeByIndex(1).setNER("TITLE");
    graph.getNodeByIndex(2).setNER("ORGANIZATION");

    SemgrexPattern pattern = SemgrexPattern.compile("{}=entity  >appos ({ner:/TITLE/}  >/(nmod:|obl:|prep_)of/ {ner:/ORGANIZATION|LOCATION|COUNTRY|STATE_OR_PROVINCE|CITY|NATIONALITY/}=slot)");
    SemgrexMatcher matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals("Young", matcher.getNode("entity").toString());
    assertEquals("Association", matcher.getNode("slot").toString());

    assertFalse(matcher.find());

    graph = SemanticGraph.valueOf("[Young appos>[director nmod:of>Association] appos>[group nmod:of>utilities]]");
    graph.getNodeByIndex(0).setNER("PERSON");
    graph.getNodeByIndex(1).setNER("TITLE");
    graph.getNodeByIndex(2).setNER("ORGANIZATION");
    graph.getNodeByIndex(3).setNER("O");
    graph.getNodeByIndex(4).setNER("O");

    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals("Young", matcher.getNode("entity").toString());
    assertEquals("Association", matcher.getNode("slot").toString());
    assertFalse(matcher.find());

    graph = SemanticGraph.valueOf("[Young appos>[group nmod:of>utilities] appos>[director nmod:of>Association]]");
    graph.getNodeByIndex(0).setNER("PERSON");
    graph.getNodeByIndex(1).setNER("O");
    graph.getNodeByIndex(2).setNER("O");
    graph.getNodeByIndex(3).setNER("TITLE");
    graph.getNodeByIndex(4).setNER("ORGANIZATION");

    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals("Young", matcher.getNode("entity").toString());
    assertEquals("Association", matcher.getNode("slot").toString());
    assertFalse(matcher.find());
  }

  public void testRoot() {
    // A few various tests that the $ node attribute works
    runTest("{$} > {word:Bill}",
            "[ate subj>Bill obj>[muffins compound>Bill]]",
            "ate");
    runTest("{} > {word:Bill}",
            "[ate subj>Bill obj>[muffins compound>Bill]]",
            "ate", "muffins");

    // Combine $ with some word attributes
    runTest("{word:ate;$} > {word:Bill}",
            "[ate subj>Bill obj>[muffins compound>Bill]]",
            "ate");
    runTest("{word:zzz;$} > {word:Bill}",
            "[ate subj>Bill obj>[muffins compound>Bill]]");

    // Another verification that $ works with other attributes
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");
    graph.getNodeByIndex(0).setTag("NN");
    graph.getNodeByIndex(1).setTag("NN");
    graph.getNodeByIndex(2).setTag("JJS");
    graph.getNodeByIndex(3).setTag("NN");
    runTest("{tag:NN}", graph,
            "ate/NN", "Bill/NN", "blueberry/NN");
    runTest("{tag:NN;$}", graph,
            "ate/NN");

    // It shouldn't matter if $ is first or last
    SemgrexPattern dollarFirst = SemgrexPattern.compile("{$;tag:NN}");
    SemgrexPattern dollarLast = SemgrexPattern.compile("{tag:NN;$}");
    assertEquals(dollarFirst, dollarLast);
    runTest(dollarFirst, graph,
            "ate/NN");
    runTest(dollarLast, graph,
            "ate/NN");
  }

  public void testDoubleEquals() {
    // Tests a relation with double equals on it.
    // Note that this also tests the () printing when outputting
    // semgrex patterns as a side effect
    String pattern = "({$} == { pos:/VB.*/ }) > ({ pos:NN } == !{ word:Doug })";
    SemgrexPattern semgrex = SemgrexPattern.compile(pattern);
    runTest(semgrex,
            "[ate/VBD subj>Bill/NN obj>[muffins compound>blueberry]]",
            "ate/VBD");
    // This is technically the same expression as above, as the parser will
    // ask for a root node with two relations: == _/VB and > !Doug/NN
    String pattern2 = "{$} == {pos:/VB.*/} > ({pos:NN} == !{word:Doug})";
    SemgrexPattern semgrex2 = SemgrexPattern.compile(pattern2);
    assertEquals(semgrex.toString(), semgrex2.toString());
    runTest(pattern2,
            "[ate/VBD subj>Bill/NN obj>[muffins compound>blueberry]]",
            "ate/VBD");
    runTest(pattern2,
            "[ate/VBD subj>Doug/NN obj>[muffins compound>blueberry]]");
    runTest(pattern2,
            "[ate/VBD subj>Bill/NNP obj>[muffins compound>blueberry]]");
    runTest(pattern2,
            "[ate/NN subj>Bill/NN obj>[muffins compound>blueberry]]");
  }

  /** 
   * Test a couple expressions which should now be illegal.
   * <br>
   * Node conjugation is now illegal, as it has unclear semantics.
   * <br>
   * &amp; on relations is now illegal as it is both redundant and confusing.
   */
  public void testIllegal() {
    try {
      String pattern = "{word:unban} > [{word:mox} {word:opal}]";
      SemgrexPattern semgrex = SemgrexPattern.compile(pattern);
      throw new RuntimeException("This expression is now illegal");
    } catch (SemgrexParseException e) {
      // yay
    }

    try {
      String pattern = "{word:unban} > [{word:mox} & {word:opal}]";
      SemgrexPattern semgrex = SemgrexPattern.compile(pattern);
      throw new RuntimeException("This expression is now illegal");
    } catch (SemgrexParseException e) {
      // yay
    }

    try {
      String pattern = "{}=unban ![>det {}] & > {word:/^(?!mox).*$/}=opal";
      SemgrexPattern semgrex = SemgrexPattern.compile(pattern);
      throw new RuntimeException("This expression is now illegal");
    } catch (SemgrexParseException e) {
      // yay
    }
  }

  public void testDuplicateConstraints() {
    // There should be an exception if the same attribute shows up
    // twice as a positive attribute
    // Although it isn't clear that's necessary,
    // since both portions could be regex which match different things
    try {
      String pattern = "{word:foo;word:bar}";
      SemgrexPattern semgrex = SemgrexPattern.compile(pattern);
      throw new RuntimeException("This expression is now illegal");
    } catch (SemgrexParseException e) {
      // yay
    }

    // this should parse since negative constraints which
    // match positive constraints are allowed
    String pattern = "{word:/.*i.*/;word!:/.*m.*/}";
    SemgrexPattern semgrex = SemgrexPattern.compile(pattern);
    runTest(pattern,
            "[ate/NN subj>Bill/NN obj>[muffins compound>blueberry]]",
            "Bill/NN");

    pattern = "{word:/.*i.*/;word!:/.*z.*/}";
    semgrex = SemgrexPattern.compile(pattern);
    runTest(pattern,
            "[ate/NN subj>Bill/NN obj>[muffins compound>blueberry]]",
            "Bill/NN", "muffins");
  }

  public void testAdjacent() {
    // test using a colon expression so that the targeted nodes
    // are the nodes which show up
    SemanticGraph graph = makeComplicatedGraph();
    runTest("{}=foo : {word:A} . {}=foo", graph, "B");

    runTest("{}=foo : {word:B} - {}=foo", graph, "A");
  }

  public void testRightLeft() {
    // test using a colon expression so that the targeted nodes
    // are the nodes which show up
    SemanticGraph graph = makeComplicatedGraph();
    runTest("{}=foo : {word:E} .. {}=foo", graph, "F", "G", "H", "I", "J");

    runTest("{}=foo : {word:E} -- {}=foo", graph, "A", "B", "C", "D");
  }

  public void testGovernor() {
    SemanticGraph graph = makeComplicatedGraph();
    runTest("{}=foo : {word:A} > {}=foo", graph, "B", "C", "D");
    runTest("{}=foo : {word:I} > {}=foo", graph);

    runTest("{}=foo : {word:J} > {}=foo", graph, "I");
    runTest("{}=foo : {word:J} >-- {}=foo", graph, "I");
    runTest("{}=foo : {word:J} >++ {}=foo", graph);

    runTest("{}=foo : {word:A} >++ {}=foo", graph, "B", "C", "D");
  }

  public void testDependent() {
    SemanticGraph graph = makeComplicatedGraph();
    runTest("{}=foo < {word:A}", graph, "B", "C", "D");
    runTest("{}=foo < {word:I}", graph);

    runTest("{}=foo < {word:J}", graph, "I");
    runTest("{}=foo <++ {word:J}", graph, "I");
    runTest("{}=foo <-- {word:J}", graph);
    runTest("{}=foo <++ {word:I}", graph);
    runTest("{}=foo <++ {word:A}", graph);
    runTest("{}=foo <-- {word:A}", graph, "B", "C", "D");
  }

  /** Various bracketing tests: | and &amp; */
  public void testBrackets() {
    runTest("{word:ate} [ > {word:Bill} | > {word:muffins}]",
            "[ate/VBD subj>Bill/NNP obj>[muffins compound>blueberry]]",
            "ate/VBD");
    runTest("{word:ate} [ > {word:Bill} | > {word:muffins}]",
            "[ate/VBD subj>foo/NNP obj>[muffins compound>blueberry]]",
            "ate/VBD");
    runTest("{word:ate} [ > {word:Bill} | > {word:muffins}]",
            "[ate/VBD subj>Bill/NNP obj>[bar compound>blueberry]]",
            "ate/VBD");
    runTest("{word:ate} [ > {word:Bill} | > {word:muffins}]",
            "[ate/VBD subj>foo/NNP obj>[bar compound>blueberry]]");

    // These should be equivalent expressions
    String pattern = "{word:ate} > [{word:Bill} | {word:muffins}]";
    String pattern2 = "{word:ate} [ > {word:Bill} | > {word:muffins}]";
    SemgrexPattern semgrex = SemgrexPattern.compile(pattern);
    SemgrexPattern semgrex2 = SemgrexPattern.compile(pattern2);
    assertEquals(semgrex.toString(), semgrex2.toString());

    runTest(semgrex,
            "[ate/VBD subj>Bill/NNP obj>[muffins compound>blueberry]]",
            "ate/VBD");
    runTest(semgrex,
            "[ate/VBD subj>foo/NNP obj>[muffins compound>blueberry]]",
            "ate/VBD");
    runTest(semgrex,
            "[ate/VBD subj>Bill/NNP obj>[bar compound>blueberry]]",
            "ate/VBD");
    runTest(semgrex,
            "[ate/VBD subj>foo/NNP obj>[bar compound>blueberry]]");

    // These should be equivalent expressions
    pattern = "{word:ate} [ > {word:Bill} > {word:muffins}]";
    pattern2 = "{word:ate} > {word:Bill} > {word:muffins}";
    semgrex = SemgrexPattern.compile(pattern);
    semgrex2 = SemgrexPattern.compile(pattern2);
    assertEquals(semgrex.toString(), semgrex2.toString());

    runTest(semgrex,
            "[ate/VBD subj>Bill/NNP obj>[muffins compound>blueberry]]",
            "ate/VBD");
    runTest(semgrex,
            "[ate/VBD subj>foo/NNP obj>[muffins compound>blueberry]]");
    runTest(semgrex,
            "[ate/VBD subj>Bill/NNP obj>[bar compound>blueberry]]");
    runTest(semgrex,
            "[ate/VBD subj>foo/NNP obj>[bar compound>blueberry]]");

    runTest(pattern2,
            "[ate/VBD subj>Bill/NNP obj>[muffins compound>blueberry]]",
            "ate/VBD");
    runTest(pattern2,
            "[ate/VBD subj>foo/NNP obj>[muffins compound>blueberry]]");
    runTest(pattern2,
            "[ate/VBD subj>Bill/NNP obj>[bar compound>blueberry]]");
    runTest(pattern2,
            "[ate/VBD subj>foo/NNP obj>[bar compound>blueberry]]");

    // An OR pattern leading to some nesting
    pattern = "{word:ate} [ > {word:Bill} | > ({word:muffins} > {word:blueberry})]";
    runTest(pattern,
            "[ate/VBD subj>Bill/NNP obj>[muffins compound>blueberry]]",
            "ate/VBD");
    runTest(pattern,
            "[ate/VBD subj>Billz/NNP obj>[muffins compound>blueberry]]",
            "ate/VBD");
    runTest(pattern,
            "[ate/VBD subj>Bill/NNP obj>[muffins compound>strawberry]]",
            "ate/VBD");
    runTest(pattern,
            "[ate/VBD subj>Billz/NNP obj>[muffins compound>strawberry]]");
  }

  String[] BATCH_PARSES = {
    "[foo-1 nmod> bar-2]",
    "[foo-1 obj> bar-2]",
    "[bar-1 compound> baz-2]",
    "[foo-1 nmod> baz-2 obj> bar-3]",
  };

  /**
   * Build a list of sentences with BasicDependenciesAnnotation
   */
  public List<CoreMap> buildSmallBatch() {
    List<CoreMap> sentences = new ArrayList<>();
    for (String parse : BATCH_PARSES) {
      SemanticGraph graph = SemanticGraph.valueOf(parse);
      CoreMap sentence = new ArrayCoreMap();
      sentence.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, graph);
      sentence.set(CoreAnnotations.TextAnnotation.class, parse);
      sentences.add(sentence);
    }
    return sentences;
  }

  /**
   * A simple test of the batch search - should return 3 of the 4 sentences
   */
  public void testBatchSearch() {
    List<CoreMap> sentences = buildSmallBatch();
    SemgrexPattern semgrex = SemgrexPattern.compile("{word:foo}=x > {}=y");
    List<Pair<CoreMap, List<SemgrexMatch>>> matches = semgrex.matchSentences(sentences, false);
    String[] expectedMatches = {
      BATCH_PARSES[0],
      BATCH_PARSES[1],
      BATCH_PARSES[3],
    };
    int[] expectedCount = {1, 1, 2};
    assertEquals(expectedMatches.length, matches.size());
    for (int i = 0; i < expectedMatches.length; ++i) {
      assertEquals(expectedMatches[i], matches.get(i).first().get(CoreAnnotations.TextAnnotation.class));
      assertEquals(expectedCount[i], matches.get(i).second().size());
    }
  }

  /**
   * Test that an illegal uniq expression throws an exception
   *<br>
   * Specifically, the expectation is for a SemgrexParseException
   */
  public void testBrokenUniq() {
    try {
      String pattern = "{word:foo}=foo :: uniq bar";
      SemgrexPattern semgrex = SemgrexPattern.compile(pattern);
      throw new RuntimeException("This expression should fail because the node name is unknown");
    } catch (SemgrexParseException e) {
      // yay
    }
  }

  /**
   * Test that a simple uniq expression is correctly parsed
   */
  public void testParsesUniq() {
    String pattern = "{word:foo}=foo :: uniq foo";
    SemgrexPattern semgrex = SemgrexPattern.compile(pattern);
  }

  /**
   * Test the uniq functionality on a few simple parses
   */
  public void testBatchUniq() {
    List<CoreMap> sentences = buildSmallBatch();
    SemgrexPattern semgrex = SemgrexPattern.compile("{word:foo}=x > {}=y :: uniq x");
    List<Pair<CoreMap, List<SemgrexMatch>>> matches = semgrex.matchSentences(sentences, false);
    // only the first foo sentence should match when using "uniq x"
    assertEquals(1, matches.size());
    assertEquals(BATCH_PARSES[0], matches.get(0).first().get(CoreAnnotations.TextAnnotation.class));
    assertEquals(1, matches.get(0).second().size());

    semgrex = SemgrexPattern.compile("{word:foo}=x > {}=y :: uniq");
    matches = semgrex.matchSentences(sentences, false);
    // same thing happens when using "uniq" and no nodes - only one match will occur
    assertEquals(1, matches.size());
    assertEquals(BATCH_PARSES[0], matches.get(0).first().get(CoreAnnotations.TextAnnotation.class));
    assertEquals(1, matches.get(0).second().size());

    semgrex = SemgrexPattern.compile("{word:foo}=x > {}=y :: uniq y");
    matches = semgrex.matchSentences(sentences, false);
    // now it should match both foo>bar and foo>baz
    assertEquals(2, matches.size());
    assertEquals(BATCH_PARSES[0], matches.get(0).first().get(CoreAnnotations.TextAnnotation.class));
    assertEquals(1, matches.get(0).second().size());
    assertEquals(BATCH_PARSES[3], matches.get(1).first().get(CoreAnnotations.TextAnnotation.class));
    assertEquals(1, matches.get(1).second().size());

    semgrex = SemgrexPattern.compile("{}=x > {}=y :: uniq x y");
    matches = semgrex.matchSentences(sentences, false);
    // now it should batch each of foo>bar, bar>baz, foo>baz
    assertEquals(3, matches.size());
    assertEquals(BATCH_PARSES[0], matches.get(0).first().get(CoreAnnotations.TextAnnotation.class));
    assertEquals(1, matches.get(0).second().size());
    assertEquals(BATCH_PARSES[2], matches.get(1).first().get(CoreAnnotations.TextAnnotation.class));
    assertEquals(1, matches.get(1).second().size());
    assertEquals(BATCH_PARSES[3], matches.get(2).first().get(CoreAnnotations.TextAnnotation.class));
    assertEquals(1, matches.get(2).second().size());
  }

  public static void outputBatchResults(SemgrexPattern pattern, List<CoreMap> sentences) {
    List<Pair<CoreMap, List<SemgrexMatch>>> matches = pattern.matchSentences(sentences, false);
    for (Pair<CoreMap, List<SemgrexMatch>> sentenceMatch : matches) {
      System.out.println("Pattern matched at:");
      System.out.println(sentenceMatch.first());
      for (SemgrexMatch match : sentenceMatch.second()) {
        System.out.println(match);
      }
    }
  }

  public static void outputResults(String pattern, String graph,
                                   String ... ignored) {
    outputResults(SemgrexPattern.compile(pattern),
                  SemanticGraph.valueOf(graph));
  }

  public static void outputResults(String pattern, SemanticGraph graph,
                                   String ... ignored) {
    outputResults(SemgrexPattern.compile(pattern), graph);
  }

  public static void outputResults(SemgrexPattern pattern, SemanticGraph graph,
                                   String ... ignored) {
    System.out.println("Matching pattern " + pattern + " to\n" + graph +
                       "  :" + (pattern.matcher(graph).matches() ?
                                "matches" : "doesn't match"));
    System.out.println();
    pattern.prettyPrint();
    System.out.println();
    SemgrexMatcher matcher = pattern.matcher(graph);
    while (matcher.find()) {
      System.out.println("  " + matcher.getMatch());
      Set<String> nodeNames = matcher.getNodeNames();
      if (nodeNames != null && nodeNames.size() > 0) {
        for (String name : nodeNames) {
          System.out.println("    " + name + ": " + matcher.getNode(name));
        }
      }

      Set<String> relNames = matcher.getRelationNames();
      if (relNames != null) {
        for (String name : relNames) {
          System.out.println("    " + name + ": " + matcher.getRelnString(name));
        }
      }
    }
  }

  /** Verify that the semgrex pattern gets compiled without being changed */
  public static void comparePatternToString(String pattern) {
    SemgrexPattern semgrex = SemgrexPattern.compile(pattern);
    String tostring = semgrex.toString();
    tostring = tostring.replaceAll(" +", " ");
    assertEquals(pattern.trim(), tostring.trim());
  }

  public static void runTest(String pattern, String graph,
                             String... expectedMatches) {
    comparePatternToString(pattern);
    runTest(SemgrexPattern.compile(pattern), SemanticGraph.valueOf(graph),
            expectedMatches);
  }

  public static void runTest(String pattern, SemanticGraph graph,
                             String... expectedMatches) {
    comparePatternToString(pattern);
    runTest(SemgrexPattern.compile(pattern), graph, expectedMatches);
  }

  public static void runTest(SemgrexPattern pattern, String graph,
                             String... expectedMatches) {
    runTest(pattern, SemanticGraph.valueOf(graph), expectedMatches);
  }

  public static void runTest(SemgrexPattern pattern, SemanticGraph graph,
                             String... expectedMatches) {
    // results are not in the order I would expect.  Using a counter
    // allows them to be in any order
    IntCounter<String> counts = new IntCounter<>();
    for (int i = 0; i < expectedMatches.length; ++i) {
      counts.incrementCount(expectedMatches[i]);
    }
    IntCounter<String> originalCounts = new IntCounter<>(counts);

    SemgrexMatcher matcher = pattern.matcher(graph);

    for (int i = 0; i < expectedMatches.length; ++i) {
      if (!matcher.find()) {
        throw new AssertionFailedError("Expected " + expectedMatches.length +
                                       " matches for pattern " + pattern +
                                       " on " + graph + ", only got " + i);
      }
      String match = matcher.getMatch().toString();
      if (!counts.containsKey(match)) {
        throw new AssertionFailedError("Unexpected match " + match +
                                       " for pattern " + pattern +
                                       " on " + graph);
      }
      counts.decrementCount(match);
      if (counts.getCount(match) < 0) {
        throw new AssertionFailedError("Found too many matches for " + match +
                                       " for pattern " + pattern +
                                       " on " + graph);
      }
    }
    if (matcher.findNextMatchingNode()) {
      throw new AssertionFailedError("Found more than " +
                                     expectedMatches.length +
                                     " matches for pattern " + pattern +
                                     " on " + graph + "... extra match is " +
                                     matcher.getMatch());
    }
  }

}

