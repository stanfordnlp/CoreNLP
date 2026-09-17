package edu.stanford.nlp.trees.ud;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.neural.Embedding;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.Pair;

/**
 * What UniversalEnhancer does to a few sentences, written down.
 *<br>
 * These record the behavior rather than argue for it: the expected values
 * are what the code produced when the test was written.  That is what makes
 * them useful for changing how the file is read underneath, since anything
 * which moves shows up here as a diff rather than as a treebank which is
 * subtly different three months later.
 *<br>
 * The sentences are picked for what they reach.  gapping has an empty word
 * and an orphan, which is the only way into copyEmptyNodes.  relcl has a
 * relative pronoun and no DEPS at all, which is addRef and collapseReferent,
 * and also the reader handing back the basic graph as the enhanced one.
 * coord has a case marker and a conjunction, which is propagateConjuncts,
 * addCaseMarkerInformation and addConjInformation.
 *
 * @author John Bauer
 */
public class UniversalEnhancerTest {

  private static final Pattern RELATIVE_PRONOUNS =
      Pattern.compile("(?i:that|what|which|who|whom|whose)");

  private static final String FIXTURE = String.join("\n",
      "# sent_id = gapping",
      "# text = John bought apples and Mary pears.",
      "1\tJohn\tJohn\tPROPN\tNNP\tNumber=Sing\t2\tnsubj\t2:nsubj\t_",
      "2\tbought\tbuy\tVERB\tVBD\tTense=Past\t0\troot\t0:root\t_",
      "3\tapples\tapple\tNOUN\tNNS\tNumber=Plur\t2\tobj\t2:obj\t_",
      "4\tand\tand\tCCONJ\tCC\t_\t5\tcc\t5:cc\t_",
      "5\tMary\tMary\tPROPN\tNNP\tNumber=Sing\t2\tconj\t5.1:nsubj\t_",
      "5.1\tbought\tbuy\tVERB\tVBD\tTense=Past\t_\t_\t2:conj\t_",
      "6\tpears\tpear\tNOUN\tNNS\tNumber=Plur\t5\torphan\t5.1:obj\tSpaceAfter=No",
      "7\t.\t.\tPUNCT\t.\t_\t2\tpunct\t2:punct\t_",
      "",
      "# sent_id = relcl",
      "# text = the man who I saw",
      "1\tthe\tthe\tDET\tDT\tDefinite=Def\t2\tdet\t_\t_",
      "2\tman\tman\tNOUN\tNN\tNumber=Sing\t0\troot\t_\t_",
      "3\twho\twho\tPRON\tWP\tPronType=Rel\t5\tobj\t_\t_",
      "4\tI\tI\tPRON\tPRP\tNumber=Sing\t5\tnsubj\t_\t_",
      "5\tsaw\tsee\tVERB\tVBD\tTense=Past\t2\tacl:relcl\t_\t_",
      "",
      "# sent_id = coord",
      "# text = He works in Paris and London.",
      "1\tHe\the\tPRON\tPRP\tNumber=Sing\t2\tnsubj\t_\t_",
      "2\tworks\twork\tVERB\tVBZ\tNumber=Sing\t0\troot\t_\t_",
      "3\tin\tin\tADP\tIN\t_\t4\tcase\t_\t_",
      "4\tParis\tParis\tPROPN\tNNP\tNumber=Sing\t2\tobl\t_\t_",
      "5\tand\tand\tCCONJ\tCC\t_\t6\tcc\t_\t_",
      "6\tLondon\tLondon\tPROPN\tNNP\tNumber=Sing\t4\tconj\t_\tSpaceAfter=No",
      "7\t.\t.\tPUNCT\t.\t_\t2\tpunct\t_\t_",
      "",
      "");

  private static final int GAPPING = 0;
  private static final int RELCL = 1;
  private static final int COORD = 2;

  /**
   * The fixture is read fresh for each test, since enhancing a sentence
   * puts edges into graphs which came from the reader
   */
  private static List<Pair<SemanticGraph, SemanticGraph>> readFixture() {
    List<Pair<SemanticGraph, SemanticGraph>> sentences = new ArrayList<>();
    Iterator<Pair<SemanticGraph, SemanticGraph>> iterator =
        new CoNLLUDocumentReader().getIterator(new StringReader(FIXTURE));
    while (iterator.hasNext()) {
      sentences.add(iterator.next());
    }
    return sentences;
  }

  private static Pair<SemanticGraph, SemanticGraph> sentence(int which) {
    return readFixture().get(which);
  }

  private static String describeNode(IndexedWord word) {
    return word.value() + "-" + word.toCopyOrEmptyIndex();
  }

  /** The edges as sorted strings, so the order the graph stores them in does not matter */
  private static List<String> describeEdges(SemanticGraph graph) {
    List<String> described = new ArrayList<>();
    for (SemanticGraphEdge edge : graph.edgeIterable()) {
      described.add(edge.getRelation().toString() + "(" +
                    describeNode(edge.getGovernor()) + ", " +
                    describeNode(edge.getDependent()) + ")");
    }
    Collections.sort(described);
    return described;
  }

  private static List<String> describeRoots(SemanticGraph graph) {
    List<String> described = new ArrayList<>();
    for (IndexedWord root : graph.getRoots()) {
      described.add(describeNode(root));
    }
    Collections.sort(described);
    return described;
  }

  private static List<String> sorted(String... edges) {
    List<String> list = new ArrayList<>(Arrays.asList(edges));
    Collections.sort(list);
    return list;
  }

  /** The lines of a CoNLL-U sentence, as printSemanticGraph writes them */
  private static String written(String... lines) {
    StringBuilder text = new StringBuilder();
    for (String line : lines) {
      text.append(line).append(System.lineSeparator());
    }
    // printSemanticGraph ends a sentence with the blank line which separates it from the next
    text.append(System.lineSeparator());
    return text.toString();
  }

  private static SemanticGraph enhance(Pair<SemanticGraph, SemanticGraph> sentence, boolean keepEmptyNodes) {
    return UniversalEnhancer.enhanceGraph(sentence.first(), sentence.second(), keepEmptyNodes,
                                          (Embedding) null, RELATIVE_PRONOUNS);
  }

  // ------------------------------------------------------------------
  // what the reader hands over
  // ------------------------------------------------------------------

  @Test
  public void testFixtureReads() {
    List<Pair<SemanticGraph, SemanticGraph>> sentences = readFixture();
    assertEquals(3, sentences.size());
    assertEquals(Arrays.asList("# sent_id = gapping", "# text = John bought apples and Mary pears."),
                 sentences.get(GAPPING).first().getComments());
    assertEquals(Arrays.asList("# sent_id = relcl", "# text = the man who I saw"),
                 sentences.get(RELCL).first().getComments());
  }

  @Test
  public void testBasicGraphs() {
    assertEquals(sorted("cc(Mary-5, and-4)",
                        "conj(bought-2, Mary-5)",
                        "nsubj(bought-2, John-1)",
                        "obj(bought-2, apples-3)",
                        "orphan(Mary-5, pears-6)",
                        "punct(bought-2, .-7)"),
                 describeEdges(sentence(GAPPING).first()));
    assertEquals(Collections.singletonList("bought-2"), describeRoots(sentence(GAPPING).first()));

    assertEquals(sorted("acl:relcl(man-2, saw-5)",
                        "det(man-2, the-1)",
                        "nsubj(saw-5, I-4)",
                        "obj(saw-5, who-3)"),
                 describeEdges(sentence(RELCL).first()));

    assertEquals(sorted("case(Paris-4, in-3)",
                        "cc(London-6, and-5)",
                        "conj(Paris-4, London-6)",
                        "nsubj(works-2, He-1)",
                        "obl(works-2, Paris-4)",
                        "punct(works-2, .-7)"),
                 describeEdges(sentence(COORD).first()));
  }

  @Test
  public void testEnhancedGraphAsRead() {
    // the gapping sentence has DEPS of its own, and the empty word is in them
    assertEquals(sorted("cc(Mary-5, and-4)",
                        "conj(bought-2, bought-5.1)",
                        "nsubj(bought-2, John-1)",
                        "nsubj(bought-5.1, Mary-5)",
                        "obj(bought-2, apples-3)",
                        "obj(bought-5.1, pears-6)",
                        "punct(bought-2, .-7)"),
                 describeEdges(sentence(GAPPING).second()));
  }

  @Test
  public void testSentenceWithNoDepsGetsTheBasicGraph() {
    // CoNLLUDocumentReader falls back per word to the basic dependency, so
    // a sentence with an empty DEPS column still has an enhanced graph
    for (int which : new int[] {RELCL, COORD}) {
      Pair<SemanticGraph, SemanticGraph> sentence = sentence(which);
      assertNotNull(sentence.second());
      assertEquals(describeEdges(sentence.first()), describeEdges(sentence.second()));
    }
  }

  // ------------------------------------------------------------------
  // how an empty word is represented
  //
  // this is the before picture for getting rid of pseudoPosition: the
  // reader marks an empty word by setting a fractional pseudoPosition and
  // does not set EmptyIndexAnnotation at all, which is why isEmptyNode
  // tests the fraction
  // ------------------------------------------------------------------

  @Test
  public void testEmptyWordIsMarkedWithPseudoPosition() {
    IndexedWord empty = null;
    for (IndexedWord word : sentence(GAPPING).second().vertexListSorted()) {
      if (word.toCopyOrEmptyIndex().equals("5.1")) {
        empty = word;
      }
    }
    assertNotNull("expected an empty word 5.1 in the gapping sentence", empty);
    assertEquals("bought", empty.value());
    assertEquals(5, empty.index());
    assertEquals(5.1, empty.pseudoPosition(), 1e-9);
    // neither of these is what marks it: both are what a plain word has
    assertEquals(0, empty.getEmptyIndex());
    assertEquals(0, empty.copyCount());
  }

  @Test
  public void testPlainWordsHaveAWholePseudoPosition() {
    for (IndexedWord word : sentence(COORD).second().vertexListSorted()) {
      assertEquals(describeNode(word), (double) word.index(), word.pseudoPosition(), 1e-9);
      assertEquals(describeNode(word), 0, word.getEmptyIndex());
      assertEquals(describeNode(word), 0, word.copyCount());
    }
  }

  // ------------------------------------------------------------------
  // copyEmptyNodes
  // ------------------------------------------------------------------

  @Test
  public void testCopyEmptyNodes() {
    Pair<SemanticGraph, SemanticGraph> gapping = sentence(GAPPING);
    SemanticGraph target = new SemanticGraph(gapping.first().typedDependencies());
    UniversalEnhancer.copyEmptyNodes(gapping.second(), target);
    // the empty word and its edges are brought over, and the orphan which
    // stood in for them is taken out
    assertEquals(sorted("cc(Mary-5, and-4)",
                        "conj(bought-2, Mary-5)",
                        "conj(bought-2, bought-5.1)",
                        "nsubj(bought-2, John-1)",
                        "nsubj(bought-5.1, Mary-5)",
                        "obj(bought-2, apples-3)",
                        "obj(bought-5.1, pears-6)",
                        "punct(bought-2, .-7)"),
                 describeEdges(target));
  }

  @Test
  public void testCopyEmptyNodesWithNothingToCopy() {
    Pair<SemanticGraph, SemanticGraph> coord = sentence(COORD);
    SemanticGraph target = new SemanticGraph(coord.first().typedDependencies());
    List<String> before = describeEdges(target);
    UniversalEnhancer.copyEmptyNodes(coord.second(), target);
    assertEquals(before, describeEdges(target));
  }

  // ------------------------------------------------------------------
  // enhanceGraph
  // ------------------------------------------------------------------

  @Test
  public void testEnhanceGappingKeepingEmptyWords() {
    SemanticGraph enhanced = enhance(sentence(GAPPING), true);
    assertEquals(sorted("cc(Mary-5, and-4)",
                        "conj:and(bought-2, Mary-5)",
                        "conj:and(bought-2, bought-5.1)",
                        "nsubj(Mary-5, John-1)",
                        "nsubj(bought-2, John-1)",
                        "nsubj(bought-5.1, Mary-5)",
                        "obj(bought-2, apples-3)",
                        "obj(bought-5.1, pears-6)",
                        "punct(bought-2, .-7)"),
                 describeEdges(enhanced));
    assertEquals(Collections.singletonList("bought-2"), describeRoots(enhanced));
  }

  @Test
  public void testEnhanceGappingDroppingEmptyWords() {
    // without the empty word there is nothing for pears to hang off, so the
    // orphan stays where it was
    SemanticGraph enhanced = enhance(sentence(GAPPING), false);
    assertEquals(sorted("cc(Mary-5, and-4)",
                        "conj:and(bought-2, Mary-5)",
                        "nsubj(Mary-5, John-1)",
                        "nsubj(bought-2, John-1)",
                        "obj(bought-2, apples-3)",
                        "orphan(Mary-5, pears-6)",
                        "punct(bought-2, .-7)"),
                 describeEdges(enhanced));
  }

  @Test
  public void testEnhanceRelativeClause() {
    // addRef makes who a ref of man, and collapseReferent hangs the clause
    // off man with the relative object relation
    List<String> expected = sorted("acl:relcl(man-2, saw-5)",
                                   "det(man-2, the-1)",
                                   "nsubj(saw-5, I-4)",
                                   "obl:relobj(saw-5, man-2)",
                                   "ref(man-2, who-3)");
    assertEquals(expected, describeEdges(enhance(sentence(RELCL), true)));
    // there are no empty words here, so keepEmptyNodes changes nothing
    assertEquals(expected, describeEdges(enhance(sentence(RELCL), false)));
  }

  @Test
  public void testEnhanceCoordination() {
    // the case marker goes into the relation, the conjunction goes into
    // conj, and the obl is propagated to the second conjunct
    List<String> expected = sorted("case(Paris-4, in-3)",
                                   "cc(London-6, and-5)",
                                   "conj:and(Paris-4, London-6)",
                                   "nsubj(works-2, He-1)",
                                   "obl:in(works-2, London-6)",
                                   "obl:in(works-2, Paris-4)",
                                   "punct(works-2, .-7)");
    assertEquals(expected, describeEdges(enhance(sentence(COORD), true)));
    assertEquals(expected, describeEdges(enhance(sentence(COORD), false)));
  }

  // ------------------------------------------------------------------
  // read, enhance, write
  //
  // the whole path a treebank takes through the tool, which is what has to
  // stay the same when the reading underneath it changes
  // ------------------------------------------------------------------

  private static String enhanceAndWrite(int which) {
    Pair<SemanticGraph, SemanticGraph> sentence = sentence(which);
    SemanticGraph enhanced = enhance(sentence, true);
    return new CoNLLUDocumentWriter().printSemanticGraph(sentence.first(), enhanced);
  }

  @Test
  public void testWriteGapping() {
    assertEquals(written(
        "# sent_id = gapping",
        "# text = John bought apples and Mary pears.",
        "1\tJohn\tJohn\tPROPN\tNNP\tNumber=Sing\t2\tnsubj\t2:nsubj|5:nsubj\t_",
        "2\tbought\tbuy\tVERB\tVBD\tTense=Past\t0\troot\t0:root\t_",
        "3\tapples\tapple\tNOUN\tNNS\tNumber=Plur\t2\tobj\t2:obj\t_",
        "4\tand\tand\tCCONJ\tCC\t_\t5\tcc\t5:cc\t_",
        "5\tMary\tMary\tPROPN\tNNP\tNumber=Sing\t2\tconj\t2:conj:and|5.1:nsubj\t_",
        "5.1\tbought\tbuy\tVERB\tVBD\tTense=Past\t_\t_\t2:conj:and\t_",
        "6\tpears\tpear\tNOUN\tNNS\tNumber=Plur\t5\torphan\t5.1:obj\tSpaceAfter=No",
        "7\t.\t.\tPUNCT\t.\t_\t2\tpunct\t2:punct\t_"),
        enhanceAndWrite(GAPPING));
  }

  @Test
  public void testWriteRelativeClause() {
    assertEquals(written(
        "# sent_id = relcl",
        "# text = the man who I saw",
        "1\tthe\tthe\tDET\tDT\tDefinite=Def\t2\tdet\t2:det\t_",
        "2\tman\tman\tNOUN\tNN\tNumber=Sing\t0\troot\t0:root|5:obl:relobj\t_",
        "3\twho\twho\tPRON\tWP\tPronType=Rel\t5\tobj\t2:ref\t_",
        "4\tI\tI\tPRON\tPRP\tNumber=Sing\t5\tnsubj\t5:nsubj\t_",
        "5\tsaw\tsee\tVERB\tVBD\tTense=Past\t2\tacl:relcl\t2:acl:relcl\t_"),
        enhanceAndWrite(RELCL));
  }

  @Test
  public void testWriteCoordination() {
    assertEquals(written(
        "# sent_id = coord",
        "# text = He works in Paris and London.",
        "1\tHe\the\tPRON\tPRP\tNumber=Sing\t2\tnsubj\t2:nsubj\t_",
        "2\tworks\twork\tVERB\tVBZ\tNumber=Sing\t0\troot\t0:root\t_",
        "3\tin\tin\tADP\tIN\t_\t4\tcase\t4:case\t_",
        "4\tParis\tParis\tPROPN\tNNP\tNumber=Sing\t2\tobl\t2:obl:in\t_",
        "5\tand\tand\tCCONJ\tCC\t_\t6\tcc\t6:cc\t_",
        "6\tLondon\tLondon\tPROPN\tNNP\tNumber=Sing\t4\tconj\t2:obl:in|4:conj:and\tSpaceAfter=No",
        "7\t.\t.\tPUNCT\t.\t_\t2\tpunct\t2:punct\t_"),
        enhanceAndWrite(COORD));
  }
}
