package edu.stanford.nlp.semgraph.semgrex;

import org.junit.Test;
import static org.junit.Assert.*;
import junit.framework.AssertionFailedError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
public class SemgrexTest {

  @Test
  public void testMatchAll() {
    SemanticGraph graph =
      SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");
    Set<IndexedWord> words = graph.vertexSet();

    SemgrexPattern pattern = compile("{}");
    SemgrexMatcher matcher = pattern.matcher(graph);
    String[] expectedMatches = {"ate", "Bill", "muffins", "blueberry"};
    for (int i = 0; i < expectedMatches.length; ++i) {
      assertTrue(matcher.findNextMatchingNode());
    }
    assertFalse(matcher.findNextMatchingNode());
  }

  @Test
  public void testTest() {
    runTest("{}", "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "ate", "Bill", "muffins", "blueberry");

    assertThrows(AssertionFailedError.class, () ->
      runTest("{}", "[ate subj>Bill obj>[muffins compound>blueberry]]",
              "ate", "Bill", "muffins", "foo"));

    assertThrows(AssertionFailedError.class, () ->
      runTest("{}", "[ate subj>Bill obj>[muffins compound>blueberry]]",
              "ate", "Bill", "muffins"));

    assertThrows(AssertionFailedError.class, () ->
      runTest("{}", "[ate subj>Bill obj>[muffins compound>blueberry]]",
              "ate", "Bill", "muffins", "blueberry", "blueberry"));
  }

  /**
   * This also tests negated node matches
   */
  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testNegatedRegex() {
    runTest("{word!:/Bill/}", "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "ate", "blueberry", "muffins");
    runTest("{word!:/.*i.*/}", "[ate subj>Bill obj>[muffins compound>blueberry]]",
            "ate", "blueberry");
  }

  @Test
  public void testBrokenContainsExpression() {
    // word is a String, not a Map, so this should throw a parse exception
    assertThrows(SemgrexParseException.class, () ->
      SemgrexPattern.compile("{word:{foo:bar}}"));

    // this one should work.  we run it here to verify the test was
    // valid, as opposed to getting a SemgrexParseException because
    // this wasn't even following proper contains syntax
    SemgrexPattern pattern = compile("{morphofeatures:{foo:bar}}");
  }

  @Test
  public void testContainsExpression() {
    SemanticGraph graph = makeComplicatedGraph();
    Set<IndexedWord> vertices = graph.vertexSet();
    for (IndexedWord iw : vertices) {
      if (iw.value().equals("D") || iw.value().equals("F")) {
        CoNLLUFeatures feats = new CoNLLUFeatures();
        feats.put("foo", "bar");
        iw.set(CoreAnnotations.CoNLLUFeats.class, feats);
      }
    }
    // morphofeatures is a Map, which is the requirement for contains attributes
    runTest("{morphofeatures:{foo:bar}}", graph, "D", "F");
  }

  @Test
  public void testContainsRegexKeyExpression() {
    SemanticGraph graph = makeComplicatedGraph();
    Set<IndexedWord> vertices = graph.vertexSet();
    for (IndexedWord iw : vertices) {
      if (iw.value().equals("D") || iw.value().equals("F")) {
        CoNLLUFeatures feats = new CoNLLUFeatures();
        feats.put("foo", "bar");
        iw.set(CoreAnnotations.CoNLLUFeats.class, feats);
      }
    }
    // morphofeatures is a Map, which is the requirement for contains attributes
    runTest("{morphofeatures:{/foo/:bar}}", graph, "D", "F");
  }

  @Test
  public void testContainsRegexKeyPartialMatchExpression() {
    SemanticGraph graph = makeComplicatedGraph();
    Set<IndexedWord> vertices = graph.vertexSet();
    for (IndexedWord iw : vertices) {
      if (iw.value().equals("D") || iw.value().equals("F")) {
        CoNLLUFeatures feats = new CoNLLUFeatures();
        feats.put("foo", "bar");
        iw.set(CoreAnnotations.CoNLLUFeats.class, feats);
      }
    }
    // morphofeatures is a Map, which is the requirement for contains attributes
    runTest("{morphofeatures:{/.*o.*/:bar}}", graph, "D", "F");
  }

  @Test
  public void testContainsRegexKeyMultipleMatchExpression() {
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
    // morphofeatures is a Map, which is the requirement for contains attributes
    runTest("{morphofeatures:{/.*o.*/:bar}}", graph, "D", "F");
  }

  @Test
  public void testContainsRegexKeyNegatedMatchExpression() {
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
    // morphofeatures is a Map, which is the requirement for contains attributes
    runTest("{morphofeatures:{/.*o.*/!:bar}}", graph, "A", "B", "C", "E", "G", "H", "I", "J");
  }

  @Test
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
    runTest("{morphofeatures:{foo:/bar[BD]/}}", graph, "B", "D");

    // test a negative regex
    // should match both the ones that don't have features
    // and the ones that have a non-matching feature
    runTest("{morphofeatures:{foo!:/bar[BD]/}}", graph, "A", "C", "E", "F", "G", "H", "I", "J");
  }

  /**
   * morphofeatures is a Map, so this will work for contains operations
   */
  @Test
  public void testDoubleContainsExpression() {
    SemanticGraph graph = makeComplicatedGraphWithFeatures();

    // test a positive regex
    runTest("{morphofeatures:{foo:/bar/;name:/[BD]/}}", graph, "B", "D");

    // test one positive, one negative regex
    runTest("{morphofeatures:{foo:/bar/;name!:/[BD]/}}", graph, "F");
  }

  @Test
  public void testMorphoVarGroups() {
    SemanticGraph graph = makeComplicatedGraphWithFeatures();

    String[] expectedNames = {"B", "D"};
    List<String> foundNames = new ArrayList<>();
    List<SemgrexMatch> matches = runTest("{morphofeatures:{foo:__#0%foo;name:/[BD]/#0%name}}", graph, "B", "D");
    assertEquals(expectedNames.length, matches.size());
    for (int i = 0; i < expectedNames.length; ++i) {
      SemgrexMatch match = matches.get(i);
      IndexedWord word = match.getMatch();

      CoNLLUFeatures feats = word.get(CoreAnnotations.CoNLLUFeats.class);
      assertTrue(feats.containsKey("foo"));
      assertEquals("bar", feats.get("foo"));
      assertNotNull(match.getVariableString("foo"));
      assertEquals(feats.get("foo"), match.getVariableString("foo"));

      assertTrue(feats.containsKey("name"));
      assertNotNull(match.getVariableString("name"));
      assertEquals(feats.get("name"), match.getVariableString("name"));
      foundNames.add(feats.get("name"));
    }

    Collections.sort(foundNames);
    assertEquals(foundNames.size(), expectedNames.length);
    for (int i = 0; i < expectedNames.length; ++i) {
      assertEquals(foundNames.get(i), expectedNames[i]);
    }
  }

  @Test
  public void testOptionalMorphoVarGroupsWrongMatch() {
    SemanticGraph graph = makeComplicatedGraphWithFeatures();
    // should match B & D, but not F, with the optional match
    runTest("{morphofeatures:{foo:__#0%foo;name?:/[BD]/#0%name}}", graph, "B", "D");
  }

  @Test
  public void testOptionalMorphoVarGroupsMissingMatches() {
    SemanticGraph graph = makeComplicatedGraphWithFeatures();
    IndexedWord word = graph.getNodeByIndex(1);
    assertEquals("A", word.word());

    CoNLLUFeatures feats = new CoNLLUFeatures();
    feats.put("foo", "bar");
    word.set(CoreAnnotations.CoNLLUFeats.class, feats);

    // should match B & D, but not F, with the optional match
    // also, the optional match should find A, since the value isn't there at all
    runTest("{morphofeatures:{foo:__#0%foo;name?:/[BD]/#0%name}}", graph, "A", "B", "D");

    // all of them should show up if any value is allowed
    // of course, only the words with "foo" set on it, not the whole graph
    runTest("{morphofeatures:{foo:__#0%foo;name?:__#0%name}}", graph, "A", "B", "D", "F");
  }

  @Test
  public void testRejectKeyVarGroup() {
    // if this feature is added, we should update this test with a
    // check that the functionality actually works
    SemgrexPattern pattern = compile("{morphofeatures:{PronType:__#1%pron}}=word");
    // and the rejection
    assertThrows(SemgrexParseException.class, () ->
                 SemgrexPattern.compile("{morphofeatures:{/Pron.*/:__#1%pron}}"));
  }

  @Test
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

  public static SemanticGraph makeComplicatedGraphWithFeatures() {
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
    return graph;
  }

  /**
   * Test that governors, dependents, ancestors, descendants are all
   * returned with multiplicity 1 if there are multiple paths to the
   * same node.
   */
  @Test
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

  @Test
  public void testRelationType() {
    SemanticGraph graph = makeComplicatedGraph();
    runTest("{} <<mod {}", graph,
            "B", "E", "F", "G", "H", "I", "I", "J", "J");

    runTest("{} >>det {}", graph,
            "A", "B", "C", "D", "E", "F", "G", "H", "J");

    runTest("{} >>det {word:I}", graph,
            "A", "B", "C", "D", "E", "F", "G", "H", "J");
  }

  @Test
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
    runTest("{} 2>> {word:I}", graph, "E", "H");

    runTest("{} 1,2>> {word:I}", graph, "E", "H", "J");

    // one bound and two equal bounds mean the same thing, and each prints
    // the way it was written rather than being rewritten into the other
    runTest("{} 3<< {word:A}", graph, "F", "G", "J");
    runTest("{} 3,3<< {word:A}", graph, "F", "G", "J");
  }

  /**
   * Two spellings of the same numeric relation are equal and hash alike
   *<br>
   * The symbol records how the relation was written, so that a pattern
   * prints the way it was typed, but "2&gt;&gt;" and "2,2&gt;&gt;" mean the
   * same thing and compare that way.
   */
  @Test
  public void testNumericRelationEquality() {
    GraphRelation oneBound = GraphRelation.getRelation(">>", null, 2, null, null);
    GraphRelation twoBounds = GraphRelation.getRelation(">>", null, 2, 2, null, null);
    GraphRelation wider = GraphRelation.getRelation(">>", null, 2, 3, null, null);
    GraphRelation otherWay = GraphRelation.getRelation("<<", null, 2, null, null);

    // each prints the way it was written
    assertEquals("2>>", oneBound.toString());
    assertEquals("2,2>>", twoBounds.toString());

    // but they are the same relation
    assertEquals(oneBound, twoBounds);
    assertEquals(twoBounds, oneBound);
    assertEquals(oneBound.hashCode(), twoBounds.hashCode());

    // and these are not
    assertNotEquals(oneBound, wider);
    assertNotEquals(oneBound, otherWay);

    Set<GraphRelation> relations = new HashSet<>(Arrays.asList(oneBound, twoBounds, wider, otherWay));
    assertEquals(3, relations.size());
  }

  /**
   * Tests that if there are different paths from A to I, those paths show up for exactly the right depths
   */
  @Test
  public void testMultipleDepths() {
    SemanticGraph graph = makeComplicatedGraph();
    runTest("{} 3,3<< {word:A}", graph, "F", "G", "J");
    runTest("{} 4,4<< {word:A}", graph, "H", "I");
    runTest("{} 5,5<< {word:A}", graph, "J");
    runTest("{} 6,6<< {word:A}", graph, "I");
  }

  /**
   * Should be able to make a keyword part of a regex or name without a parser error
   */
  @Test
  public void testKeywordRegex() {
    SemgrexPattern pattern = compile("{word:uniq}");
    pattern = compile("{word:sort}");
    pattern = compile("{word:rsort}");
  }

  /** After making UNIQ a separate token in the parser, we should verify that "uniq" can be treated as an identifier as well */
  @Test
  public void testUniqNamedNode() {
    SemanticGraph graph = makeComplicatedGraph();

    runTest("{} >obj ({} >expl {})", graph, "A");

    SemgrexPattern pattern =
      compile("{} >obj ({} >expl {}=uniq)");
    SemgrexMatcher matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals(1, matcher.getNodeNames().size());
    assertEquals("E", matcher.getNode("uniq").toString());
    assertEquals("A", matcher.getMatch().toString());
    assertFalse(matcher.find());
  }

  @Test
  public void testNamedNode() {
    SemanticGraph graph = makeComplicatedGraph();

    runTest("{} >obj ({} >expl {})", graph, "A");

    SemgrexPattern pattern = compile("{} >obj ({} >expl {}=foo)");
    SemgrexMatcher matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals(1, matcher.getNodeNames().size());
    assertEquals("E", matcher.getNode("foo").toString());
    assertEquals("A", matcher.getMatch().toString());
    assertFalse(matcher.find());

    pattern = compile("{} >obj ({} >expl {}=foo) >mod {}");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals(1, matcher.getNodeNames().size());
    assertEquals("E", matcher.getNode("foo").toString());
    assertEquals("A", matcher.getMatch().toString());
    assertFalse(matcher.find());

    pattern =
      compile("{} >obj ({} >expl {}=foo) >mod ({} >mark {})");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals(1, matcher.getNodeNames().size());
    assertEquals("E", matcher.getNode("foo").toString());
    assertEquals("A", matcher.getMatch().toString());
    assertFalse(matcher.find());

    pattern =
      compile("{} >obj ({} >expl {}=foo) >mod ({} > {})");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals(1, matcher.getNodeNames().size());
    assertEquals("E", matcher.getNode("foo").toString());
    assertEquals("A", matcher.getMatch().toString());
    assertFalse(matcher.find());

    pattern =
      compile("{} >obj ({} >expl {}=foo) >mod ({} > {}=foo)");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals(1, matcher.getNodeNames().size());
    assertEquals("E", matcher.getNode("foo").toString());
    assertEquals("A", matcher.getMatch().toString());
    assertFalse(matcher.find());

    pattern =
      compile("{} >obj ({} >expl {}=foo) >mod ({}=foo > {})");
    matcher = pattern.matcher(graph);
    assertFalse(matcher.find());
  }

  @Test
  public void testPartition() {
    SemanticGraph graph = makeComplicatedGraph();

    runTest("{}=a >> {word:E}", graph, "A", "B", "C", "D");
    SemgrexPattern pattern = compile("{}=a >> {word:E} : {}=a >> {word:B}");
    runTest("{}=a >> {word:E} : {}=a >> {word:B}", graph, "A");
  }

  @Test
  public void testEqualsRelation() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");
    SemgrexPattern pattern = compile("{} >> ({}=a == {}=b)");
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
    pattern = compile("{} >> {}=a >> {}=b : {}=a == {}=b");
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
  @Test
  public void testNotEquals() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");

    SemgrexPattern pattern = compile("{} >> {}=a >> {}=b : {}=a !== {}=b");
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
    pattern = compile("{} >> {}=a >> ({}=b !== {}=a)");
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

  @Test
  public void testInitialConditions() {
    SemanticGraph graph = makeComplicatedGraph();

    SemgrexPattern pattern =
      compile("{}=a >> {}=b : {}=a >> {}=c");
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
  @Test
  public void testIndex() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");
    runTest("{idx:0}", graph, "ate");
    runTest("{idx:1}", graph, "Bill");
    runTest("{idx:2}", graph, "muffins");
    runTest("{idx:3}", graph, "blueberry");
    runTest("{idx:4}", graph);
  }

  @Test
  public void testLemma() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");
    for (IndexedWord word : graph.vertexSet()) {
      word.setLemma(word.word());
    }
    runTest("{lemma:ate}", graph, "ate");
    runTest("{lemma:Bill}", graph, "Bill");
  }

  /** tests a deprecated version - might as well check that it still functions, since it still exists */
  @Test
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

  @Test
  public void testNamedRelation() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");
    SemgrexPattern pattern = compile("{idx:0}=gov >>~foo {idx:3}=dep");
    SemgrexMatcher matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals("ate", matcher.getNode("gov").toString());
    assertEquals("blueberry", matcher.getNode("dep").toString());
    assertEquals("compound", matcher.getRelnString("foo"));
    assertFalse(matcher.find());

    pattern = compile("{idx:3}=dep <<~foo {idx:0}=gov");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals("ate", matcher.getNode("gov").toString());
    assertEquals("blueberry", matcher.getNode("dep").toString());
    assertEquals("obj", matcher.getRelnString("foo"));
    assertFalse(matcher.find());

    pattern = compile("{idx:3}=dep <~foo {idx:2}=gov");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals("muffins", matcher.getNode("gov").toString());
    assertEquals("blueberry", matcher.getNode("dep").toString());
    assertEquals("compound", matcher.getRelnString("foo"));
    assertFalse(matcher.find());

    pattern = compile("{idx:2}=gov >~foo {idx:3}=dep");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals("muffins", matcher.getNode("gov").toString());
    assertEquals("blueberry", matcher.getNode("dep").toString());
    assertEquals("compound", matcher.getRelnString("foo"));
    assertFalse(matcher.find());
  }

  /** named edges should also have the named reference functionality, as long as you are testing a parent or child relation */
  @Test
  public void testNamedRelationEdge() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");
    String patternString = "{idx:2}=gov >=foo {idx:3}=dep";
    // test two different mechanisms so we can make sure the SemgrexMatch pattern is working
    List<SemgrexMatch> matches = runTest(patternString, graph, "muffins");
    assertEquals(1, matches.size());
    SemgrexMatch match = matches.get(0);
    assertEquals("muffins", match.getNode("gov").toString());
    assertEquals("blueberry", match.getNode("dep").toString());
    assertEquals("compound", match.getRelnString("foo"));

    SemgrexPattern pattern = compile(patternString);
    SemgrexMatcher matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals("muffins", matcher.getNode("gov").toString());
    assertEquals("blueberry", matcher.getNode("dep").toString());
    assertEquals("compound", matcher.getRelnString("foo"));
    assertFalse(matcher.find());

    // it should accept this once, going from root to the dep node,
    // as the ~foo will require that it have the same edge label as =foo
    matches = runTest("{idx:2}=gov >=foo {idx:3}=dep : {$}=root >>~foo {}", graph, "muffins");
    assertEquals(1, matches.size());
    match = matches.get(0);
    assertEquals("ate", match.getNode("root").toString());
    assertEquals("muffins", match.getNode("gov").toString());
    assertEquals("blueberry", match.getNode("dep").toString());
    assertEquals("compound", match.getRelnString("foo"));
  }

  /**
   * The named relation feature should incorporate backreferences
   */
  @Test
  public void testNamedRelationBackreference() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");

    SemgrexPattern pattern = compile("{}=A >~foo ({}=B >~foo {}=C)");
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
    pattern = compile("{}=A >~foo {}=B >~foo ({}=C !== {}=B)");
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
    pattern = compile("{}=A >~foo {}=B >~foo ({}=C !== {}=B)");
    matcher = pattern.matcher(graph);
    assertFalse(matcher.find());
  }

  /**
   * Test the named edge feature, including backreferences
   */
  @Test
  public void testNamedEdgeGovernor() {
    // Test a simple version of the named edge search
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");
    SemgrexPattern pattern = compile("{}=A >subj=foo {}=B");
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
    pattern = compile("{}=A > {}=B > {}=C");
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
    pattern = compile("{}=A >=foo {}=B >=foo {}=C");
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
  @Test
  public void testNamedEdgeDependent() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");
    SemgrexPattern pattern = compile("{}=A <subj=foo {}=B");
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
  @Test
  public void testNamedEdgeLeftRight() {
    SemanticGraph graph = SemanticGraph.valueOf("[antennae-2 amod> blue-1 nmod> [head-5 case> on-3 nmod:poss> her-4]]");
    SemgrexPattern pattern = compile("{$}=A >--=foo {}=B");
    SemgrexMatcher matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    SemanticGraphEdge edge = matcher.getEdge("foo");
    assertTrue(edge != null);
    assertEquals(edge.getSource().word(), "antennae");
    assertEquals(edge.getTarget().word(), "blue");
    assertFalse(matcher.find());

    pattern = compile("{$}=A >++=foo {}=B");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    edge = matcher.getEdge("foo");
    assertTrue(edge != null);
    assertEquals(edge.getSource().word(), "antennae");
    assertEquals(edge.getTarget().word(), "head");
    assertFalse(matcher.find());

    pattern = compile("{}=A <++=foo {$}=B");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    edge = matcher.getEdge("foo");
    assertTrue(edge != null);
    assertEquals(edge.getSource().word(), "antennae");
    assertEquals(edge.getTarget().word(), "blue");
    assertFalse(matcher.find());

    pattern = compile("{}=A <--=foo {$}=B");
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
  @Test
  public void testNamedEdgeException() {
    // Relations other than the gov / dep relations should not allow a named edge
    assertThrows(SemgrexParseException.class, () ->
      SemgrexPattern.compile("{} <<=foo {}"));
  }

  @Test
  public void testAttributeConjunction() {
    // A possible user submitted error: https://github.com/stanfordnlp/CoreNLP/issues/552
    // A match with both POS and word labeled should have both attributes on the same node
    String pattern = "{$} > {pos:JJS;word:most}";
    SemgrexPattern semgrex = compile(pattern);

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
  @Test
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

  @Test
  public void testTwoWordConstraints() {
    // Another part of issue 552:
    // "{$} > { word:She; word:hello }"
    // it shouldn't find anything because of conflicting constraints
    // originally it did because the attributes were stored in a map,
    // which meant word:hello clobbered word:She
    // We fix this issue by making such a state throw an exception.
    SemanticGraph graph = SemanticGraph.valueOf("[said subj>She obj>hello]");
    String pattern = "{$} > {word:She;word:hello}";
    // This was supposed to fail horribly: conflicting word constraints should throw
    assertThrows(SemgrexParseException.class, () ->
      SemgrexPattern.compile(pattern));
  }

  /**
   * Verify that this is working for a KBP query which wasn't working
   * for some reason... at least it wasn't the semgrex
   */
  @Test
  public void testNERAttribute() {
    SemanticGraph graph = SemanticGraph.valueOf("[Young appos>[director nmod:of>Association]]]");
    graph.getNodeByIndex(0).setNER("PERSON");
    graph.getNodeByIndex(1).setNER("TITLE");
    graph.getNodeByIndex(2).setNER("ORGANIZATION");

    SemgrexPattern pattern = compile("{}=entity >appos ({ner:/TITLE/} >/(nmod:|obl:|prep_)of/ {ner:/ORGANIZATION|LOCATION|COUNTRY|STATE_OR_PROVINCE|CITY|NATIONALITY/}=slot)");
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

  @Test
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
    // TODO: "{$;tag:NN}" does not round trip, so it cannot use compile()
    // yet.  The root attribute is always printed last, so it comes back as
    // "{tag:NN;$}" -- the same pattern, written the other way round
    SemgrexPattern dollarFirst = SemgrexPattern.compile("{$;tag:NN}");
    SemgrexPattern dollarLast = compile("{tag:NN;$}");
    assertEquals(dollarFirst, dollarLast);
    runTest(dollarFirst, graph,
            "ate/NN");
    runTest(dollarLast, graph,
            "ate/NN");
  }

  @Test
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
    SemgrexPattern semgrex2 = compile(pattern2);
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
  @Test
  public void testIllegal() {
    // This expression is now illegal: node conjugation has unclear semantics
    assertThrows(SemgrexParseException.class, () ->
      SemgrexPattern.compile("{word:unban} > [{word:mox} {word:opal}]"));

    // This expression is now illegal: node conjugation has unclear semantics
    assertThrows(SemgrexParseException.class, () ->
      SemgrexPattern.compile("{word:unban} > [{word:mox} & {word:opal}]"));

    // This expression is now illegal: & on relations is redundant and confusing
    assertThrows(SemgrexParseException.class, () ->
      SemgrexPattern.compile("{}=unban ![>det {}] & > {word:/^(?!mox).*$/}=opal"));
  }

  @Test
  public void testDuplicateConstraints() {
    // There should be an exception if the same attribute shows up
    // twice as a positive attribute
    // Although it isn't clear that's necessary,
    // since both portions could be regex which match different things
    // This expression is now illegal: same attribute cannot appear twice as a positive constraint
    assertThrows(SemgrexParseException.class, () ->
      SemgrexPattern.compile("{word:foo;word:bar}"));

    // this should parse since negative constraints which
    // match positive constraints are allowed
    String pattern = "{word:/.*i.*/;word!:/.*m.*/}";
    SemgrexPattern semgrex = compile(pattern);
    runTest(pattern,
            "[ate/NN subj>Bill/NN obj>[muffins compound>blueberry]]",
            "Bill/NN");

    pattern = "{word:/.*i.*/;word!:/.*z.*/}";
    semgrex = compile(pattern);
    runTest(pattern,
            "[ate/NN subj>Bill/NN obj>[muffins compound>blueberry]]",
            "Bill/NN", "muffins");
  }

  @Test
  public void testAdjacent() {
    // test using a colon expression so that the targeted nodes
    // are the nodes which show up
    SemanticGraph graph = makeComplicatedGraph();
    runTest("{}=foo : {word:A} . {}=foo", graph, "B");

    runTest("{}=foo : {word:B} - {}=foo", graph, "A");
  }

  @Test
  public void testRightLeft() {
    // test using a colon expression so that the targeted nodes
    // are the nodes which show up
    SemanticGraph graph = makeComplicatedGraph();
    runTest("{}=foo : {word:E} .. {}=foo", graph, "F", "G", "H", "I", "J");

    runTest("{}=foo : {word:E} -- {}=foo", graph, "A", "B", "C", "D");
  }

  @Test
  public void testGovernor() {
    SemanticGraph graph = makeComplicatedGraph();
    runTest("{}=foo : {word:A} > {}=foo", graph, "B", "C", "D");
    runTest("{}=foo : {word:I} > {}=foo", graph);

    runTest("{}=foo : {word:J} > {}=foo", graph, "I");
    runTest("{}=foo : {word:J} >-- {}=foo", graph, "I");
    runTest("{}=foo : {word:J} >++ {}=foo", graph);

    runTest("{}=foo : {word:A} >++ {}=foo", graph, "B", "C", "D");
  }

  @Test
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
  @Test
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
    // TODO: does not round trip, so it cannot use compile() yet: "{word:ate} > [...|...]" prints as "[ > ... | > ... ]"
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
    // TODO: does not round trip, so it cannot use compile() yet: "[ > A > B ]" prints without the brackets, as "> A > B"
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
  @Test
  public void testBatchSearch() {
    List<CoreMap> sentences = buildSmallBatch();
    SemgrexPattern semgrex = compile("{word:foo}=x > {}=y");
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
  @Test
  public void testBrokenUniq() {
    // This expression should fail because the node name is unknown
    assertThrows(SemgrexParseException.class, () ->
      SemgrexPattern.compile("{word:foo}=foo :: uniq bar"));
  }

  /**
   * Test that an illegal uniq expression throws an exception when at least two of node, regex, and edge names are the same
   *<br>
   * Specifically, the expectation is for a SemgrexParseException
   */
  @Test
  public void testOverlappingUniq() {
    // This expression should fail because the node name and regex name overlap
    assertThrows(SemgrexParseException.class, () ->
      SemgrexPattern.compile("{word:__#1%foo}=foo :: uniq foo"));

    // This expression should fail because the edge name and regex name overlap
    assertThrows(SemgrexParseException.class, () ->
      SemgrexPattern.compile("{word:__#1%foo} <=foo {} :: uniq foo"));

    // This expression should fail because the node name and edge name overlap
    assertThrows(SemgrexParseException.class, () ->
      SemgrexPattern.compile("{word:__}=foo <=foo {} :: uniq foo"));
  }

  /**
   * Test that a simple uniq expression is correctly parsed
   */
  @Test
  public void testParsesUniq() {
    // Test the basic node name compilation
    String pattern = "{word:foo}=foo :: uniq foo";
    SemgrexPattern semgrex = compile(pattern);

    // Test the basic regex compilation
    pattern = "{word:__#1%foo} :: uniq foo";
    semgrex = SemgrexPattern.compile(pattern);
  }

  /**
   * Test the uniq functionality on a few simple parses
   */
  @Test
  public void testBatchUniq() {
    List<CoreMap> sentences = buildSmallBatch();
    SemgrexPattern semgrex = compile("{word:foo}=x > {}=y :: uniq x");
    List<Pair<CoreMap, List<SemgrexMatch>>> matches = semgrex.matchSentences(sentences, false);
    // only the first foo sentence should match when using "uniq x"
    assertEquals(1, matches.size());
    assertEquals(BATCH_PARSES[0], matches.get(0).first().get(CoreAnnotations.TextAnnotation.class));
    assertEquals(1, matches.get(0).second().size());

    semgrex = compile("{word:foo}=x > {}=y :: uniq");
    matches = semgrex.matchSentences(sentences, false);
    // same thing happens when using "uniq" and no nodes - only one match will occur
    assertEquals(1, matches.size());
    assertEquals(BATCH_PARSES[0], matches.get(0).first().get(CoreAnnotations.TextAnnotation.class));
    assertEquals(1, matches.get(0).second().size());

    semgrex = compile("{word:foo}=x > {}=y :: uniq y");
    matches = semgrex.matchSentences(sentences, false);
    // now it should match both foo>bar and foo>baz
    assertEquals(2, matches.size());
    assertEquals(BATCH_PARSES[0], matches.get(0).first().get(CoreAnnotations.TextAnnotation.class));
    assertEquals(1, matches.get(0).second().size());
    assertEquals(BATCH_PARSES[3], matches.get(1).first().get(CoreAnnotations.TextAnnotation.class));
    assertEquals(1, matches.get(1).second().size());

    semgrex = compile("{}=x > {}=y :: uniq x y");
    matches = semgrex.matchSentences(sentences, false);
    // now it should batch each of foo>bar, bar>baz, foo>baz
    assertEquals(3, matches.size());
    assertEquals(BATCH_PARSES[0], matches.get(0).first().get(CoreAnnotations.TextAnnotation.class));
    assertEquals(1, matches.get(0).second().size());
    assertEquals(BATCH_PARSES[2], matches.get(1).first().get(CoreAnnotations.TextAnnotation.class));
    assertEquals(1, matches.get(1).second().size());
    assertEquals(BATCH_PARSES[3], matches.get(2).first().get(CoreAnnotations.TextAnnotation.class));
    assertEquals(1, matches.get(2).second().size());

    // test the uniq operator on a regex match
    semgrex = compile("{word:__#1%x} !< {} :: uniq x");
    matches = semgrex.matchSentences(sentences, false);
    assertEquals(2, matches.size());
    assertEquals(BATCH_PARSES[0], matches.get(0).first().get(CoreAnnotations.TextAnnotation.class));
    assertEquals(1, matches.get(0).second().size());
    assertEquals("foo", matches.get(0).second().get(0).getVariableString("x"));
    assertEquals(BATCH_PARSES[2], matches.get(1).first().get(CoreAnnotations.TextAnnotation.class));
    assertEquals(1, matches.get(1).second().size());
    assertEquals("bar", matches.get(1).second().get(0).getVariableString("x"));

    // test the uniq operator on an edge
    semgrex = compile("{} !< {} >=edge {} :: uniq edge");
    matches = semgrex.matchSentences(sentences, false);
    assertEquals(3, matches.size());
    // sentence 0 should match because the root has a child with nmod
    assertEquals(BATCH_PARSES[0], matches.get(0).first().get(CoreAnnotations.TextAnnotation.class));
    assertEquals(1, matches.get(0).second().size());
    assertEquals("nmod", matches.get(0).second().get(0).getEdge("edge").getRelation().toString());
    // sentence 1 should match because the root has a child with obj
    assertEquals(BATCH_PARSES[1], matches.get(1).first().get(CoreAnnotations.TextAnnotation.class));
    assertEquals(1, matches.get(1).second().size());
    assertEquals("obj", matches.get(1).second().get(0).getEdge("edge").getRelation().toString());
    // sentence 2 should match because the root has a child with compound
    assertEquals(BATCH_PARSES[2], matches.get(2).first().get(CoreAnnotations.TextAnnotation.class));
    assertEquals(1, matches.get(2).second().size());
    assertEquals("compound", matches.get(2).second().get(0).getEdge("edge").getRelation().toString());
    // sentence 3 should not match because both nmod and obj were already seen
  }

  @Test
  public void testCaseInsensitive() {
    List<CoreMap> sentences = buildSmallBatch();
    // TODO: does not round trip, so it cannot use compile() yet.  The case
    // insensitive modifier is not reproduced: the pattern prints as
    // "{word:/FOO/}", which is not the same pattern
    SemgrexPattern semgrex = SemgrexPattern.compile("(?i: {word:FOO} )");
    List<Pair<CoreMap, List<SemgrexMatch>>> matches = semgrex.matchSentences(sentences, false);
    assertEquals(3, matches.size());

    semgrex = compile("{word:FOO}");
    matches = semgrex.matchSentences(sentences, false);
    assertEquals(0, matches.size());
  }

  @Test
  public void testIllegalFlag() {
    assertThrows(SemgrexParseException.class, () ->
      SemgrexPattern.compile("(?z: {word:FOO} )"));
  }


  @Test
  public void testBatchSort() {
    List<CoreMap> sentences = buildSmallBatch();
    SemgrexPattern semgrex = compile("{word:foo}=x >=edge {}=y :: sort edge");
    List<Pair<CoreMap, List<SemgrexMatch>>> matches = semgrex.matchSentences(sentences, false);
    assertEquals(3, matches.size());

    // After sorting, the results should be in edge order
    // (and it should have been a stable sort)
    assertEquals(BATCH_PARSES[0], matches.get(0).first().get(CoreAnnotations.TextAnnotation.class));
    assertEquals(1, matches.get(0).second().size());
    assertEquals("nmod", matches.get(0).second().get(0).getEdge("edge").getRelation().toString());

    assertEquals(BATCH_PARSES[3], matches.get(1).first().get(CoreAnnotations.TextAnnotation.class));
    assertEquals(2, matches.get(1).second().size());
    assertEquals("nmod", matches.get(1).second().get(0).getEdge("edge").getRelation().toString());

    assertEquals(BATCH_PARSES[1], matches.get(2).first().get(CoreAnnotations.TextAnnotation.class));
    assertEquals(1, matches.get(2).second().size());
    assertEquals("obj", matches.get(2).second().get(0).getEdge("edge").getRelation().toString());
  }

  @Test
  public void testRegexVariableGroups() {
    // first, a basic test that it is capturing the variable groups correctly
    SemgrexPattern pattern = compile("{word:/(.*ill.*)/#1%name}");
    SemanticGraph graph = SemanticGraph.valueOf("[ate-2 subj> Bill-1 obj>[muffins-6 compound> Blueberry-3 compound> Flueberry-4 compound> filled-5]]");
    Set<String> matches = new HashSet<>();
    SemgrexMatcher matcher = pattern.matcher(graph);
    while (matcher.find()) {
      // TODO: check the size of the variableStrings here
      assertNotNull(matcher.variableStrings.getString("name"));
      matches.add(matcher.variableStrings.getString("name"));
    }
    Set<String> expectedMatches = Stream.of("Bill", "filled").collect(Collectors.toCollection(HashSet::new));
    assertEquals(expectedMatches, matches);

    // test a basic use case of a single variable string matching
    pattern = compile("{word:/(.*)ill/#1%name} .. {word:/(.*)lueberry/#1%name}");
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals("B", matcher.variableStrings.getString("name"));
    // this should not match Flueberry
    assertFalse(matcher.find());

    // this time, because the variable names are different,
    // both Blueberry and Flueberry should match
    pattern = compile("{word:/(.*)ill/#1%name} .. {word:/(.*)lueberry/#1%letter}");
    matcher = pattern.matcher(graph);
    matches.clear();
    assertTrue(matcher.find());
    assertEquals("B", matcher.variableStrings.getString("name"));
    assertNotNull(matcher.variableStrings.getString("letter"));
    matches.add(matcher.variableStrings.getString("letter"));
    assertTrue(matcher.find());
    assertEquals("B", matcher.variableStrings.getString("name"));
    assertNotNull(matcher.variableStrings.getString("letter"));
    matches.add(matcher.variableStrings.getString("letter"));
    assertFalse(matcher.find());
    expectedMatches = Stream.of("B", "F").collect(Collectors.toCollection(HashSet::new));
    assertEquals(expectedMatches, matches);
  }

  @Test
  public void testExactVariableGroups() {
    SemgrexPattern pattern = compile("{word:__#1%name} .. {word:__#1%name}");
    SemanticGraph graph = SemanticGraph.valueOf("[ate-2 subj> Bill-1 obj>[muffins-6 compound> Blueberry-3 compound> Bill-4 compound> filled-5]]");

    // This should match exactly once, for Bill & Bill
    SemgrexMatcher matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals("Bill", matcher.variableStrings.getString("name"));
    assertFalse(matcher.find());

    pattern = compile("{word:Bill#1%name} .. {word:__#1%name}");

    // This should match exactly once, for Bill & Bill
    matcher = pattern.matcher(graph);
    assertTrue(matcher.find());
    assertEquals("Bill", matcher.variableStrings.getString("name"));
    assertFalse(matcher.find());
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

  /**
   * A run of relations after a node description are siblings of that
   * node, not a chain descending from one another
   *<br>
   * {@code {a} >r1 {b} >r2 {c}} asks for a node with an r1 child and an
   * r2 child, not for a grandchild.  Parentheses are what nests, and
   * they are the only thing that does.  This is easy to get backwards
   * when reading a long pattern, and it decides which node a relation is
   * asked of, so it is worth stating outright.
   */
  @Test
  public void testRelationChainIsSiblings() {
    String graph = "[married-2 nsubjpass> Bill-1 prep_to> [Mary-3 amod> tall-4]]";

    // both relations are asked of "married"
    runTest("{}=a >nsubjpass {}=b >prep_to {}=c", graph, "married");

    // so this asks "married" for an amod, which it does not have,
    // rather than asking "Mary"
    runTest("{}=a >prep_to {}=b >amod {}=c", graph);

    // parentheses are what makes it a chain
    runTest("{}=a >prep_to ({}=b >amod {}=c)", graph, "married");
  }

  /**
   * A graph where "ate" has two objects, so an optional relation has more
   * than one way to be satisfied
   */
  static final String TWO_OBJECTS = "[ate-1 nsubj> Bill-2 obj> cake-3 obj> pie-4 nmod> Mary-5]";

  /**
   * An optional relation which cannot be satisfied still matches, leaving its names unbound
   */
  @Test
  public void testOptionalNoMatch() {
    runTest("{word:ate}=a ?>advmod {}=b", TWO_OBJECTS, "ate");

    // and a node with no such child at all
    runTest("{word:Bill}=a ?>obj {}=b", TWO_OBJECTS, "Bill");

    // the name really is unbound rather than bound to something
    SemgrexMatcher matcher = compile("{word:ate}=a ?>advmod {}=b").matcher(SemanticGraph.valueOf(TWO_OBJECTS));
    assertTrue(matcher.find());
    assertNull(matcher.getNode("b"));
    assertFalse(matcher.find());
  }

  /**
   * An optional relation produces one match per satisfying edge, the same as a required one
   *<br>
   * It does not stop after the first.  "ate" has two objects, so both are
   * returned, and there is no extra unbound match on the end: the unbound
   * case is only for a node where the relation matched nothing at all.
   */
  @Test
  public void testOptionalMultipleMatches() {
    runTest("{word:ate}=a >obj {}=b", TWO_OBJECTS, "ate", "ate");
    runTest("{word:ate}=a ?>obj {}=b", TWO_OBJECTS, "ate", "ate");

    Set<String> objects = new HashSet<>();
    SemgrexMatcher matcher = compile("{word:ate}=a ?>obj {}=b").matcher(SemanticGraph.valueOf(TWO_OBJECTS));
    while (matcher.find()) {
      objects.add(matcher.getNode("b").word());
    }
    assertEquals(new HashSet<>(Arrays.asList("cake", "pie")), objects);
  }

  /**
   * The description on the far side of an optional relation still has to match
   *<br>
   * If it does not, the relation has matched nothing and the unbound case
   * is what comes back -- not a match with the wrong node bound.
   */
  @Test
  public void testOptionalDescriptionMustMatch() {
    runTest("{word:ate}=a ?>obj {word:cake}=b", TWO_OBJECTS, "ate");
    runTest("{word:ate}=a ?>obj {word:soup}=b", TWO_OBJECTS, "ate");

    SemgrexMatcher matcher = compile("{word:ate}=a ?>obj {word:soup}=b").matcher(SemanticGraph.valueOf(TWO_OBJECTS));
    assertTrue(matcher.find());
    assertNull(matcher.getNode("b"));
  }

  /**
   * An optional relation never removes matches which a required one would find
   *<br>
   * Every node matches, since the relation is optional; the nodes which
   * do have a parent bind it, and the rest come back unbound.
   */
  @Test
  public void testOptionalIsASuperset() {
    runTest("{}=a <obj {}=b", TWO_OBJECTS, "cake", "pie");
    runTest("{}=a ?<obj {}=b", TWO_OBJECTS, "ate", "Bill", "cake", "pie", "Mary");
  }

  /**
   * Optional relations combine with required ones and with each other
   */
  @Test
  public void testOptionalCombined() {
    // the required nsubj pins one match; the optional obj multiplies it
    runTest("{word:ate}=a >nsubj {}=s ?>obj {}=b", TWO_OBJECTS, "ate", "ate");

    // two optionals: two objects and one nmod gives two matches
    runTest("{word:ate}=a ?>obj {}=b ?>nmod {}=c", TWO_OBJECTS, "ate", "ate");

    // an optional whose relation is named still names the edge
    SemgrexMatcher matcher = compile("{word:ate}=a ?>obj=e {}=b").matcher(SemanticGraph.valueOf(TWO_OBJECTS));
    assertTrue(matcher.find());
    assertNotNull(matcher.getEdge("e"));
  }

  /**
   * An optional relation prints the way it was written
   *<br>
   * The marker and the space around it used to come out the other way
   * round, so "?&gt;obj" printed as "? &gt;obj".  Negation had the same
   * problem.  runTest checks the round trip for every pattern above, but
   * these are the shapes worth naming.
   */
  @Test
  public void testOptionalToString() {
    comparePatternToString("{}=a ?>obj {}=b");
    comparePatternToString("{}=a ?<obj {}=b");
    comparePatternToString("{}=a !>obj {}");
    comparePatternToString("{}=a >nsubj {}=s ?>obj {}=b");
  }

  /**
   * Compile a pattern, checking that it prints the way it was written.
   *<br>
   * Tests should use this rather than SemgrexPattern.compile so that every
   * pattern in this file exercises the round trip, without anyone having to
   * maintain a separate list of patterns to check.  A few patterns do not
   * round trip yet; those still call SemgrexPattern.compile directly and are
   * marked with a TODO where they appear.
   */
  public static SemgrexPattern compile(String pattern) {
    comparePatternToString(pattern);
    return SemgrexPattern.compile(pattern);
  }

  /** Verify that the semgrex pattern gets compiled without being changed */
  public static void comparePatternToString(String pattern) {
    SemgrexPattern semgrex = SemgrexPattern.compile(pattern);
    String tostring = semgrex.toString();
    tostring = tostring.replaceAll(" +", " ");
    assertEquals(pattern.trim(), tostring.trim());
  }

  public static List<SemgrexMatch> runTest(String pattern, String graph,
                                           String... expectedMatches) {
    comparePatternToString(pattern);
    return runTest(SemgrexPattern.compile(pattern), SemanticGraph.valueOf(graph),
                   expectedMatches);
  }

  public static List<SemgrexMatch> runTest(String pattern, SemanticGraph graph,
                                           String... expectedMatches) {
    comparePatternToString(pattern);
    return runTest(SemgrexPattern.compile(pattern), graph, expectedMatches);
  }

  public static List<SemgrexMatch> runTest(SemgrexPattern pattern, String graph,
                                           String... expectedMatches) {
    return runTest(pattern, SemanticGraph.valueOf(graph), expectedMatches);
  }

  public static List<SemgrexMatch> runTest(SemgrexPattern pattern, SemanticGraph graph,
                                           String... expectedMatches) {
    // results are not in the order I would expect.  Using a counter
    // allows them to be in any order
    IntCounter<String> counts = new IntCounter<>();
    for (int i = 0; i < expectedMatches.length; ++i) {
      counts.incrementCount(expectedMatches[i]);
    }
    IntCounter<String> originalCounts = new IntCounter<>(counts);

    SemgrexMatcher matcher = pattern.matcher(graph);
    List<SemgrexMatch> matches = new ArrayList<>();

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
      matches.add(new SemgrexMatch(pattern, matcher));
    }
    if (matcher.findNextMatchingNode()) {
      throw new AssertionFailedError("Found more than " +
                                     expectedMatches.length +
                                     " matches for pattern " + pattern +
                                     " on " + graph + "... extra match is " +
                                     matcher.getMatch());
    }

    return matches;
  }

}
