package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.CoNLLUReader;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphName;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphs;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.CoreMap;

/** Tests moving nodes with {@link SsurgeonUtils} */
public class SsurgeonUtilsTest {

  /** The relations of the edges from gov to dep */
  static List<String> relations(SemanticGraph graph, IndexedWord gov, IndexedWord dep) {
    List<String> relations = new ArrayList<>();
    for (SemanticGraphEdge edge : graph.getAllEdges(gov, dep)) {
      relations.add(edge.getRelation().toString());
    }
    Collections.sort(relations);
    return relations;
  }

  /** Whether the edge is one of the edges in the graph, rather than merely equal to one */
  static boolean inGraph(SemanticGraph graph, SemanticGraphEdge edge) {
    for (SemanticGraphEdge other : graph.getAllEdges(edge.getSource(), edge.getTarget())) {
      if (other == edge) {
        return true;
      }
    }
    return false;
  }

  /** A named edge refers to the edge in the graph after one of its words moves */
  @Test
  public void namedEdgeAfterMove() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate-1 obj> cake-2]");
    SemgrexMatcher matcher = SemgrexPattern.compile("{}=a >obj=e {}=b").matcher(graph);
    assertTrue(matcher.find());

    SsurgeonUtils.moveNode(graph, matcher, matcher.getNode("b"), 5);
    assertEquals(5, matcher.getNode("b").index());
    assertTrue(inGraph(graph, matcher.getEdge("e")));
    assertEquals(5, matcher.getEdge("e").getTarget().index());
  }

  /**
   * When both words of a named edge move, the edge is replaced twice, and
   * the name still refers to the edge in the graph afterwards
   */
  @Test
  public void namedEdgeAfterMovingBothWords() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate-1 obj> cake-2]");
    SemgrexMatcher matcher = SemgrexPattern.compile("{}=a >obj=e {}=b").matcher(graph);
    assertTrue(matcher.find());

    SsurgeonUtils.moveNodes(graph, matcher, x -> x >= 1, x -> x + 1, true);
    SemanticGraphEdge edge = matcher.getEdge("e");
    assertTrue(inGraph(graph, edge));
    assertEquals(2, edge.getSource().index());
    assertEquals(3, edge.getTarget().index());
  }

  /** A copy node keeps its copy count when it moves, rather than becoming equal to the word it copies */
  @Test
  public void copyCountKept() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate-1 obj> cake-2]");
    IndexedWord ate = graph.getNodeByIndex(1);
    IndexedWord cake = graph.getNodeByIndex(2);
    IndexedWord copy = new IndexedWord(new CoreLabel(cake.backingLabel()));
    copy.setCopyCount(1);
    graph.addEdge(ate, copy, GrammaticalRelation.valueOf(Language.UniversalEnglish, "dep"), 1.0, false);
    assertEquals(3, graph.size());

    SemgrexMatcher matcher = SemgrexPattern.compile("{word:ate}").matcher(graph);
    assertTrue(matcher.find());
    SsurgeonUtils.moveNode(graph, matcher, copy, 5);

    assertEquals(3, graph.size());
    IndexedWord moved = null;
    for (IndexedWord word : graph.vertexSet()) {
      if (word.index() == 5) {
        moved = word;
      }
    }
    assertNotNull(moved);
    assertEquals(1, moved.copyCount());
    assertEquals(Collections.singletonList("dep"), relations(graph, ate, moved));
    assertEquals(Collections.singletonList("obj"), relations(graph, ate, cake));
  }

  // ------------------------------------------------------------------
  // moving nodes in every graph of a sentence
  // ------------------------------------------------------------------

  /** "Sue likes coffee and Bill tea", where the enhanced graph has an empty word 5.1 for the elided "likes" */
  private static final String SENTENCE = String.join("\n",
    "# text = Sue likes coffee and Bill tea",
    "1\tSue\tSue\tPROPN\t_\t_\t2\tnsubj\t2:nsubj\t_",
    "2\tlikes\tlike\tVERB\t_\t_\t0\troot\t0:root\t_",
    "3\tcoffee\tcoffee\tNOUN\t_\t_\t2\tobj\t2:obj\t_",
    "4\tand\tand\tCCONJ\t_\t_\t5\tcc\t5.1:cc\t_",
    "5\tBill\tBill\tPROPN\t_\t_\t2\tconj\t5.1:nsubj\t_",
    "5.1\tlikes\tlike\tVERB\t_\t_\t_\t_\t2:conj:and\t_",
    "6\ttea\ttea\tNOUN\t_\t_\t5\torphan\t5.1:obj\t_",
    "",
    "");

  private static SemgrexGraphs readGraphs() throws IOException {
    return readGraphs(SENTENCE);
  }

  private static SemgrexGraphs readGraphs(String conllu) throws IOException {
    File file = File.createTempFile("ssurgeonutils", ".conllu");
    file.deleteOnExit();
    Files.write(file.toPath(), conllu.getBytes(StandardCharsets.UTF_8));
    CoreMap sentence = new CoNLLUReader().readCoNLLUFile(file.getPath()).get(0).get(CoreAnnotations.SentencesAnnotation.class).get(0);
    return SemgrexGraphs.of(sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class),
                            sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class));
  }

  /** The word with the given index and empty index in a graph */
  private static IndexedWord word(SemanticGraph graph, int index, int emptyIndex) {
    for (IndexedWord word : graph.vertexSet()) {
      if (word.index() == index && word.getEmptyIndex() == emptyIndex) {
        return word;
      }
    }
    return null;
  }

  @Test
  public void moveNodesInBothGraphs() throws IOException {
    SemgrexGraphs graphs = readGraphs();
    SemanticGraph basic = graphs.getDefault();
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    SemgrexMatcher matcher = SemgrexPattern.compile("{word:tea}=t <orphan=bo {word:Bill}=b <obj@enhanced=eo {}=x").matcher(graphs);
    assertTrue(matcher.find());

    // make room for a word at 4, as AddDep would
    SsurgeonUtils.moveNodes(graphs, matcher, x -> x >= 4, x -> x + 1);

    assertEquals("and", basic.getNodeByIndex(5).word());
    assertEquals("Bill", basic.getNodeByIndex(6).word());
    assertEquals("tea", basic.getNodeByIndex(7).word());
    assertNull(basic.getNodeByIndexSafe(4));
    // the empty word follows Bill, so 5.1 becomes 6.1
    IndexedWord empty = word(enhanced, 6, 1);
    assertNotNull(empty);
    assertNull(word(enhanced, 5, 1));
    // the six words and the empty word
    assertEquals(7, enhanced.size());

    // one IndexedWord per word, shared by both graphs
    SsurgeonPattern.checkSharedWords(graphs);
    IndexedWord bill = basic.getNodeByIndex(6);
    IndexedWord tea = basic.getNodeByIndex(7);
    assertTrue(enhanced.containsVertex(tea));

    // the edges moved with their words, in each graph
    assertEquals(Collections.singletonList("orphan"), relations(basic, bill, tea));
    assertEquals(Collections.singletonList("obj"), relations(enhanced, empty, tea));
    assertEquals(Collections.singletonList("nsubj"), relations(enhanced, empty, bill));
    assertEquals(Collections.singletonList("conj:and"), relations(enhanced, basic.getNodeByIndex(2), empty));
    assertEquals(Collections.emptyList(), relations(basic, empty, tea));

    // named nodes and edges refer to the moved words and edges, each edge in its own graph
    assertSame(tea, matcher.getNode("t"));
    assertSame(bill, matcher.getNode("b"));
    assertSame(empty, matcher.getNode("x"));
    assertEquals(SemgrexGraphName.BASIC, matcher.getEdgeGraph("bo"));
    assertTrue(inGraph(basic, matcher.getEdge("bo")));
    assertEquals(SemgrexGraphName.ENHANCED, matcher.getEdgeGraph("eo"));
    assertTrue(inGraph(enhanced, matcher.getEdge("eo")));
    assertSame(empty, matcher.getEdge("eo").getGovernor());

    // the root did not move
    assertEquals(Collections.singleton(basic.getNodeByIndex(2)), basic.getRoots());
    assertEquals(Collections.singleton(basic.getNodeByIndex(2)), enhanced.getRoots());
  }

  /** Moving the root moves it in both graphs, to the same new IndexedWord */
  @Test
  public void moveRoot() throws IOException {
    SemgrexGraphs graphs = readGraphs();
    SemanticGraph basic = graphs.getDefault();
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    SemgrexMatcher matcher = SemgrexPattern.compile("{word:likes}=v >nsubj {word:Sue}").matcher(graphs);
    assertTrue(matcher.find());

    IndexedWord moved = SsurgeonUtils.moveNode(graphs, matcher, matcher.getNode("v"), 10);
    assertEquals(10, moved.index());
    assertEquals(Collections.singleton(moved), basic.getRoots());
    assertSame(moved, basic.getFirstRoot());
    assertSame(moved, enhanced.getFirstRoot());
    assertSame(moved, matcher.getNode("v"));
    SsurgeonPattern.checkSharedWords(graphs);
    assertEquals(Collections.singletonList("nsubj"), relations(basic, moved, basic.getNodeByIndex(1)));
  }

  /**
   * A copy which shares its CoreLabel with the word it copies has the same
   * index, so moveNodes moves the two together
   */
  @Test
  public void copySharingLabelMovesTogether() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate-1 obj> cake-2]");
    IndexedWord ate = graph.getNodeByIndex(1);
    IndexedWord cake = graph.getNodeByIndex(2);
    IndexedWord copy = new IndexedWord(cake.backingLabel());
    copy.setCopyCount(1);
    graph.addEdge(ate, copy, GrammaticalRelation.valueOf(Language.UniversalEnglish, "dep"), 1.0, false);
    assertEquals(3, graph.size());

    SemgrexMatcher matcher = SemgrexPattern.compile("{word:ate}").matcher(graph);
    assertTrue(matcher.find());
    SsurgeonUtils.moveNodes(graph, matcher, x -> x >= 2, x -> x + 3, false);

    assertEquals(3, graph.size());
    IndexedWord newCake = null;
    IndexedWord newCopy = null;
    for (IndexedWord word : graph.vertexSet()) {
      if (word.copyCount() == 1) {
        newCopy = word;
      } else if (word.word().equals("cake")) {
        newCake = word;
      }
      assertTrue(graph.containsVertex(word));
    }
    assertEquals(5, newCake.index());
    assertEquals(5, newCopy.index());
    assertEquals(Collections.singletonList("obj"), relations(graph, ate, newCake));
    assertEquals(Collections.singletonList("dep"), relations(graph, ate, newCopy));
  }

  // ------------------------------------------------------------------
  // renumbering empty words
  // ------------------------------------------------------------------

  /** "Sue sleeps very soundly", with three empty words */
  private static final String EMPTY_WORDS_SENTENCE = String.join("\n",
    "# text = Sue sleeps very soundly",
    "1\tSue\tSue\tPROPN\t_\t_\t2\tnsubj\t2:nsubj\t_",
    "2\tsleeps\tsleep\tVERB\t_\t_\t0\troot\t0:root\t_",
    "2.1\tsnores\tsnore\tVERB\t_\t_\t_\t_\t2:conj\t_",
    "2.2\tdreams\tdream\tVERB\t_\t_\t_\t_\t2:conj\t_",
    "3\tvery\tvery\tADV\t_\t_\t4\tadvmod\t4:advmod\t_",
    "3.1\tdeeply\tdeeply\tADV\t_\t_\t_\t_\t2:advmod\t_",
    "4\tsoundly\tsoundly\tADV\t_\t_\t2\tadvmod\t2:advmod\t_",
    "",
    "");

  /** The words of a graph in order, each as its index and text, such as {@code 2.1=snores} */
  private static String wordsOf(SemanticGraph graph) {
    List<String> words = new ArrayList<>();
    for (IndexedWord word : graph.vertexListSorted()) {
      words.add(word.toCopyOrEmptyIndex() + "=" + word.word());
    }
    return String.join(" ", words);
  }

  /** Takes a word out of every graph of the sentence */
  private static void remove(SemgrexGraphs graphs, IndexedWord word) {
    for (SemgrexGraphName name : SemgrexGraphName.values()) {
      SemanticGraph graph = graphs.get(name);
      if (graph != null) {
        graph.removeVertex(word);
      }
    }
  }

  /**
   * With "very" gone, "deeply" follows "sleeps", and goes after the empty
   * words already there, and "soundly" moves into the place of "very"
   */
  @Test
  public void emptyWordsFollowTheWordBefore() throws IOException {
    SemgrexGraphs graphs = readGraphs(EMPTY_WORDS_SENTENCE);
    SemanticGraph basic = graphs.getDefault();
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    SemgrexMatcher matcher = SemgrexPattern.compile("{word:very}=v . {word:soundly}=s").matcher(graphs);
    assertTrue(matcher.find());
    // a semgrex pattern cannot name an empty word by position, so name it directly
    IndexedWord deeply = word(enhanced, 3, 1);
    matcher.putNode("d", deeply);

    remove(graphs, matcher.getNode("v"));
    SsurgeonUtils.moveNodes(graphs, matcher, x -> x >= 3, x -> x - 1);

    assertEquals("1=Sue 2=sleeps 2.1=snores 2.2=dreams 2.3=deeply 3=soundly", wordsOf(enhanced));
    assertEquals("1=Sue 2=sleeps 3=soundly", wordsOf(basic));
    SsurgeonPattern.checkSharedWords(graphs);
    IndexedWord newDeeply = word(enhanced, 2, 3);
    assertSame(newDeeply, matcher.getNode("d"));
    assertSame(word(enhanced, 3, 0), matcher.getNode("s"));
    assertEquals(Collections.singletonList("advmod"), relations(enhanced, word(enhanced, 2, 0), newDeeply));
  }

  /** Every call numbers the empty words after each word from 1, even one which moves no word */
  @Test
  public void emptyWordsNumberedFromOne() throws IOException {
    SemgrexGraphs graphs = readGraphs(EMPTY_WORDS_SENTENCE);
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    SemgrexMatcher matcher = SemgrexPattern.compile("{word:sleeps}=s").matcher(graphs);
    assertTrue(matcher.find());
    IndexedWord dreams = word(enhanced, 2, 2);
    matcher.putNode("d", dreams);

    remove(graphs, word(enhanced, 2, 1));
    SsurgeonUtils.moveNodes(graphs, matcher, x -> false, x -> x);

    assertEquals("1=Sue 2=sleeps 2.1=dreams 3=very 3.1=deeply 4=soundly", wordsOf(enhanced));
    SsurgeonPattern.checkSharedWords(graphs);
    assertSame(word(enhanced, 2, 1), matcher.getNode("d"));
    assertEquals(Collections.singletonList("conj"), relations(enhanced, word(enhanced, 2, 0), matcher.getNode("d")));
    // the words which did not move are the same IndexedWords
    assertSame(matcher.getNode("s"), word(enhanced, 2, 0));
  }

  /** An empty word whose words are all gone follows the nearest word left before it */
  @Test
  public void emptyWordsSkipSeveralGoneWords() throws IOException {
    SemgrexGraphs graphs = readGraphs(EMPTY_WORDS_SENTENCE);
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    SemgrexMatcher matcher = SemgrexPattern.compile("{word:soundly}=s").matcher(graphs);
    assertTrue(matcher.find());

    // taking out the root leaves the roots alone, which moveNodes does not look at
    remove(graphs, word(enhanced, 2, 0));
    remove(graphs, word(enhanced, 3, 0));
    SsurgeonUtils.moveNodes(graphs, matcher, x -> x >= 4, x -> x - 2);

    assertEquals("1=Sue 1.1=snores 1.2=dreams 1.3=deeply 2=soundly", wordsOf(enhanced));
    SsurgeonPattern.checkSharedWords(graphs);
  }

  /** An empty word before the first word stays before it */
  @Test
  public void emptyWordBeforeFirstWord() throws IOException {
    String conllu = String.join("\n",
      "# text = Sue sleeps",
      "0.1\tit\tit\tPRON\t_\t_\t_\t_\t2:expl\t_",
      "1\tSue\tSue\tPROPN\t_\t_\t2\tnsubj\t2:nsubj\t_",
      "2\tsleeps\tsleep\tVERB\t_\t_\t0\troot\t0:root\t_",
      "",
      "");
    SemgrexGraphs graphs = readGraphs(conllu);
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    SemgrexMatcher matcher = SemgrexPattern.compile("{word:Sue}=s").matcher(graphs);
    assertTrue(matcher.find());
    // make room for a word at 1, as addDep -position - would
    SsurgeonUtils.moveNodes(graphs, matcher, x -> x >= 1, x -> x + 1);
    assertEquals("0.1=it 2=Sue 3=sleeps", wordsOf(enhanced));
  }
}
