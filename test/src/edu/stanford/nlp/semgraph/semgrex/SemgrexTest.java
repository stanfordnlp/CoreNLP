package edu.stanford.nlp.semgraph.semgrex;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.trees.EnglishGrammaticalRelations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;


/**
 * @author John Bauer
 */
public class SemgrexTest extends TestCase {

  public void testMatchAll() {
    SemanticGraph graph =
      SemanticGraph.valueOf("[ate subj>Bill dobj>[muffins compound>blueberry]]");
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
    runTest("{}", "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "ate", "Bill", "muffins", "blueberry");

    try {
      runTest("{}", "[ate subj>Bill dobj>[muffins compound>blueberry]]",
              "ate", "Bill", "muffins", "foo");
      throw new RuntimeException();
    } catch (AssertionFailedError e) {
      // yay
    }

    try {
      runTest("{}", "[ate subj>Bill dobj>[muffins compound>blueberry]]",
              "ate", "Bill", "muffins");
      throw new RuntimeException();
    } catch (AssertionFailedError e) {
      // yay
    }

    try {
      runTest("{}", "[ate subj>Bill dobj>[muffins compound>blueberry]]",
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
    runTest("{word:Bill}", "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "Bill");
    runTest("!{word:Bill}",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "ate", "muffins", "blueberry");
    runTest("!{word:Fred}",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "ate", "Bill", "muffins", "blueberry");
    runTest("!{word:ate}",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "Bill", "muffins", "blueberry");
    runTest("{word:/^(?!Bill).*$/}",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "ate", "muffins", "blueberry");
    runTest("{word:/^(?!Fred).*$/}",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "ate", "Bill", "muffins", "blueberry");
    runTest("{word:/^(?!ate).*$/}",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "Bill", "muffins", "blueberry");
    runTest("{word:muffins} >compound {word:blueberry}",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "muffins");
    runTest("{} << {word:ate}=a",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "Bill", "muffins", "blueberry");
    runTest("{} << !{word:ate}=a",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "blueberry");
    // blueberry should match twice because it has two ancestors
    runTest("{} << {}=a",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "Bill", "muffins", "blueberry", "blueberry");
  }

  public void testSimpleDependency() {
    // blueberry has two ancestors
    runTest("{} << {}", "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "Bill", "muffins", "blueberry", "blueberry");
    // ate has three descendants
    runTest("{} >> {}", "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "ate", "ate", "ate", "muffins");
    runTest("{} < {}", "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "Bill", "muffins", "blueberry");
    runTest("{} > {}", "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "ate", "ate", "muffins");
  }

  public void testNamedDependency() {
    runTest("{} << {word:ate}",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "Bill", "muffins", "blueberry");
    runTest("{} >> {word:blueberry}",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "ate", "muffins");
    runTest("{} >> {word:Bill}",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "ate");
    runTest("{} < {word:ate}",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "Bill", "muffins");
    runTest("{} > {word:blueberry}",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "muffins");
    runTest("{} > {word:muffins}",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "ate");
  }

  public void testNamedGovernor() {
    runTest("{word:blueberry} << {}",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "blueberry");
    runTest("{word:ate} << {}",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]");
    runTest("{word:blueberry} >> {}",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]");
    runTest("{word:muffins} >> {}",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "muffins");
    runTest("{word:Bill} >> {}",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]");
    runTest("{word:muffins} < {}",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "muffins");
    runTest("{word:muffins} > {}",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "muffins");
  }

  public void testTwoDependencies() {
    runTest("{} >> ({} >> {})",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "ate");
    runTest("{} >> {word:Bill} >> {word:muffins}",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "ate");
    runTest("{}=a >> {}=b >> {word:muffins}=c",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "ate", "ate", "ate");
    runTest("{}=a >> {word:Bill}=b >> {}=c",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "ate", "ate", "ate");
    runTest("{}=a >> {}=b >> {}=c",
            "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "ate", "ate", "ate", "ate", "ate",
            "ate", "ate", "ate", "ate", "muffins");
  }

  public void testRegex() {
    runTest("{word:/Bill/}", "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "Bill");

    runTest("{word:/ill/}", "[ate subj>Bill dobj>[muffins compound>blueberry]]");

    runTest("{word:/.*ill/}", "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "Bill");

    runTest("{word:/.*il/}", "[ate subj>Bill dobj>[muffins compound>blueberry]]");

    runTest("{word:/.*il.*/}", "[ate subj>Bill dobj>[muffins compound>blueberry]]",
            "Bill");
  }

  public void testReferencedRegex() {
    runTest("{word:/Bill/}", "[ate subj>Bill dobj>[bill det>the]]",
            "Bill");

    runTest("{word:/.*ill/}", "[ate subj>Bill dobj>[bill det>the]]",
            "Bill", "bill");

    runTest("{word:/[Bb]ill/}", "[ate subj>Bill dobj>[bill det>the]]",
            "Bill", "bill");

    // TODO: implement referencing regexes
  }

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
                  EnglishGrammaticalRelations.MODIFIER, 1.0, false);
    graph.addEdge(nodes[0], nodes[2],
                  EnglishGrammaticalRelations.DIRECT_OBJECT, 1.0, false);
    graph.addEdge(nodes[0], nodes[3],
                  EnglishGrammaticalRelations.INDIRECT_OBJECT, 1.0, false);
    graph.addEdge(nodes[1], nodes[4],
                  EnglishGrammaticalRelations.MARKER, 1.0, false);
    graph.addEdge(nodes[2], nodes[4],
                  EnglishGrammaticalRelations.EXPLETIVE, 1.0, false);
    graph.addEdge(nodes[3], nodes[4],
                  EnglishGrammaticalRelations.ADJECTIVAL_COMPLEMENT, 1.0, false);
    graph.addEdge(nodes[4], nodes[5],
                  EnglishGrammaticalRelations.ADJECTIVAL_MODIFIER, 1.0, false);
    graph.addEdge(nodes[4], nodes[6],
                  EnglishGrammaticalRelations.ADVERBIAL_MODIFIER, 1.0, false);
    graph.addEdge(nodes[4], nodes[8],
                  EnglishGrammaticalRelations.MODIFIER, 1.0, false);
    graph.addEdge(nodes[5], nodes[7],
                  EnglishGrammaticalRelations.POSSESSION_MODIFIER, 1.0, false);
    graph.addEdge(nodes[6], nodes[7],
                  EnglishGrammaticalRelations.POSSESSIVE_MODIFIER, 1.0, false);
    graph.addEdge(nodes[7], nodes[8],
                  EnglishGrammaticalRelations.AGENT, 1.0, false);
    graph.addEdge(nodes[8], nodes[9],
                  EnglishGrammaticalRelations.DETERMINER, 1.0, false);

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

    runTest("{} > {word:J}", graph,
            "I");

    runTest("{} < {word:E}", graph,
            "F", "G", "I");

    runTest("{} < {word:I}", graph,
            "J");

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

    runTest("{} << {word:I}", graph,
            "J");

    runTest("{} << {word:J}", graph);

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

    runTest("{} >> {word:I}", graph,
            "A", "B", "C", "D", "E", "F", "G", "H");

    runTest("{} >> {word:J}", graph,
            "A", "B", "C", "D", "E", "F", "G", "H", "I");

    runTest("{} >> {word:K}", graph);
  }

  public void testRelationType() {
    SemanticGraph graph = makeComplicatedGraph();
    runTest("{} <<mod {}", graph,
            "B", "E", "F", "G", "H", "I", "I", "J", "J");

    runTest("{} >>det {}", graph,
            "A", "B", "C", "D", "E", "F", "G", "H", "I");

    runTest("{} >>det {word:J}", graph,
            "A", "B", "C", "D", "E", "F", "G", "H", "I");
  }

  public void testExactDepthRelations() {
    SemanticGraph graph = makeComplicatedGraph();
    runTest("{} 2,3<< {word:A}", graph, "E", "F", "G", "I");

    runTest("{} 2,2<< {word:A}", graph, "E");

    runTest("{} 1,2<< {word:A}", graph, "B", "C", "D", "E");

    runTest("{} 0,2<< {word:A}", graph, "B", "C", "D", "E");

    runTest("{} 0,10<< {word:A}", graph,
            "B", "C", "D", "E", "F", "G", "H", "I", "J");

    runTest("{} 0,10>> {word:J}", graph,
            "A", "B", "C", "D", "E", "F", "G", "H", "I");

    runTest("{} 2,3>> {word:J}", graph,
            "B", "C", "D", "E", "F", "G", "H");

    runTest("{} 2,2>> {word:J}", graph,
            "E", "H");

    // use this method to avoid the toString() test, since we expect it
    // to use 2,2>> instead of 2>>
    runTest(SemgrexPattern.compile("{} 2>> {word:J}"), graph,
            "E", "H");

    runTest("{} 1,2>> {word:J}", graph,
            "E", "H", "I");
  }

  /**
   * Tests that if there are different paths from A to I, those paths show up for exactly the right depths
   */
  public void testMultipleDepths() {
    SemanticGraph graph = makeComplicatedGraph();
    runTest("{} 3,3<< {word:A}", graph, "F", "G", "I");
    runTest("{} 4,4<< {word:A}", graph, "H", "J");
    runTest("{} 5,5<< {word:A}", graph, "I");
    runTest("{} 6,6<< {word:A}", graph, "J");
  }

  public void testNamedNode() {
    SemanticGraph graph = makeComplicatedGraph();

    runTest("{} >dobj ({} >expl {})", graph, "A");

    SemgrexPattern pattern =
      SemgrexPattern.compile("{} >dobj ({} >expl {}=foo)");
    SemgrexMatcher matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals(1, matcher.getNodeNames().size());
    assertEquals("E", matcher.getNode("foo").toString());
    assertEquals("A", matcher.getMatch().toString());
    assertFalse(matcher.find());

    pattern = SemgrexPattern.compile("{} >dobj ({} >expl {}=foo) >mod {}");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals(1, matcher.getNodeNames().size());
    assertEquals("E", matcher.getNode("foo").toString());
    assertEquals("A", matcher.getMatch().toString());
    assertFalse(matcher.find());

    pattern =
      SemgrexPattern.compile("{} >dobj ({} >expl {}=foo) >mod ({} >mark {})");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals(1, matcher.getNodeNames().size());
    assertEquals("E", matcher.getNode("foo").toString());
    assertEquals("A", matcher.getMatch().toString());
    assertFalse(matcher.find());

    pattern =
      SemgrexPattern.compile("{} >dobj ({} >expl {}=foo) >mod ({} > {})");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals(1, matcher.getNodeNames().size());
    assertEquals("E", matcher.getNode("foo").toString());
    assertEquals("A", matcher.getMatch().toString());
    assertFalse(matcher.find());

    pattern =
      SemgrexPattern.compile("{} >dobj ({} >expl {}=foo) >mod ({} > {}=foo)");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals(1, matcher.getNodeNames().size());
    assertEquals("E", matcher.getNode("foo").toString());
    assertEquals("A", matcher.getMatch().toString());
    assertFalse(matcher.find());

    pattern =
      SemgrexPattern.compile("{} >dobj ({} >expl {}=foo) >mod ({}=foo > {})");
    matcher = pattern.matcher(graph);
    assertFalse(matcher.find());
  }

  public void testPartition() {
    SemanticGraph graph = makeComplicatedGraph();

    runTest("{}=a >> {word:E}", graph, "A", "B", "C", "D");
    runTest("{}=a >> {word:E} : {}=a >> {word:B}", graph, "A");
  }

  public void testEqualsRelation() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill dobj>[muffins compound>blueberry]]");
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
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill dobj>[muffins compound>blueberry]]");

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
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill dobj>[muffins compound>blueberry]]");
    runTest("{idx:0}", graph, "ate");
    runTest("{idx:1}", graph, "Bill");
    runTest("{idx:2}", graph, "muffins");
    runTest("{idx:3}", graph, "blueberry");
    runTest("{idx:4}", graph);
  }

  public void testLemma() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill dobj>[muffins compound>blueberry]]");
    for (IndexedWord word : graph.vertexSet()) {
      word.setLemma(word.word());
    }
    runTest("{lemma:ate}", graph, "ate");

    Tree tree = Tree.valueOf("(ROOT (S (NP (PRP I)) (VP (VBP love) (NP (DT the) (NN display))) (. .)))");
    graph = SemanticGraphFactory.generateCCProcessedDependencies(tree);
    for (IndexedWord word : graph.vertexSet()) {
      word.setLemma(word.word());
    }
    // This set of three tests also provides some coverage for a
    // bizarre error a user found where multiple copies of the same
    // IndexedWord were created
    runTest("{}=Obj <dobj {lemma:love}=Pred", graph, "display/NN");
    runTest("{}=Obj <dobj {}=Pred", graph, "display/NN");
    runTest("{lemma:love}=Pred >dobj {}=Obj ", graph, "love/VBP");
  }

  public void testNamedRelation() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill dobj>[muffins compound>blueberry]]");
    SemgrexPattern pattern = SemgrexPattern.compile("{idx:0}=gov >>=foo {idx:3}=dep");
    SemgrexMatcher matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals("ate", matcher.getNode("gov").toString());
    assertEquals("blueberry", matcher.getNode("dep").toString());
    assertEquals("compound", matcher.getRelnString("foo"));
    assertFalse(matcher.find());

    pattern = SemgrexPattern.compile("{idx:3}=dep <<=foo {idx:0}=gov");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals("ate", matcher.getNode("gov").toString());
    assertEquals("blueberry", matcher.getNode("dep").toString());
    assertEquals("dobj", matcher.getRelnString("foo"));
    assertFalse(matcher.find());

    pattern = SemgrexPattern.compile("{idx:3}=dep <=foo {idx:2}=gov");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals("muffins", matcher.getNode("gov").toString());
    assertEquals("blueberry", matcher.getNode("dep").toString());
    assertEquals("compound", matcher.getRelnString("foo"));
    assertFalse(matcher.find());

    pattern = SemgrexPattern.compile("{idx:2}=gov >=foo {idx:3}=dep");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals("muffins", matcher.getNode("gov").toString());
    assertEquals("blueberry", matcher.getNode("dep").toString());
    assertEquals("compound", matcher.getRelnString("foo"));
    assertFalse(matcher.find());
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

