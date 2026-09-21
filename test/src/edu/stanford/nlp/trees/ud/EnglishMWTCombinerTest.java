package edu.stanford.nlp.trees.ud;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.CoNLLUReader;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.Pair;

/**
 * What EnglishMWTCombiner does to a few contractions, written down.
 *<br>
 * The combiner joins words back into the multiword token they came from,
 * by marking the words rather than by changing the graph: the edges come
 * out exactly as they went in.  The patterns match on word order and word
 * forms, not on the dependencies, so the trees here do not need to be
 * right for the words to combine.
 *<br>
 * There are two kinds of test here.  The first few build a graph from its
 * string form, which is the quickest way to show a pattern firing: a
 * contraction in capitals, and wanna, whose pattern also rewrites the
 * lemmas of the words it joins.  The rest read their graphs from CoNLL-U,
 * the way the converter and the tools built on it do, which is what shows
 * the comments and the spacing coming through.
 *
 * @author John Bauer
 */
public class EnglishMWTCombinerTest {

  static final String newline = System.getProperty("line.separator");

  public static final String expectedITS = String.join(newline,
      "1-2\tit's\t_\t_\t_\t_\t_\t_\t_\t_",
      "1\tit\t_\t_\t_\t_\t4\tnsubj\t_\t_",
      "2\t's\t_\t_\t_\t_\t4\tcop\t_\t_",
      "3\tyours\t_\t_\t_\t_\t4\tadvmod\t_\t_",
      "4\tyours\t_\t_\t_\t_\t0\troot\t_\t_",
      "5\t!\t_\t_\t_\t_\t4\tpunct\t_\t_");

  public static final String expectedCANNOT = String.join(newline,
      "1\tI\t_\t_\t_\t_\t4\tnsubj\t_\t_",
      "2-3\tCANNOT\t_\t_\t_\t_\t_\t_\t_\t_",
      "2\tCAN\t_\t_\t_\t_\t4\taux\t_\t_",
      "3\tNOT\t_\t_\t_\t_\t4\tadvmod\t_\t_",
      "4\tbelieve\t_\t_\t_\t_\t0\troot\t_\t_",
      "5\tit\t_\t_\t_\t_\t4\tobj\t_\t_",
      "6\t!\t_\t_\t_\t_\t4\tpunct\t_\t_");

  public static final String expectedWANNA = String.join(newline,
      "1\tI\t_\t_\t_\t_\t2\tnsubj\t_\t_",
      "2-3\twanna\t_\t_\t_\t_\t_\t_\t_\t_",
      "2\twan\twant\t_\t_\t_\t0\troot\t_\t_",
      "3\tna\tto\t_\t_\t_\t4\tmark\t_\t_",
      "4\tfix\t_\t_\t_\t_\t2\txcomp\t_\t_",
      "5\tthis\t_\t_\t_\t_\t4\tobj\t_\t_");

  private static final String FIXTURE = String.join("\n",
      "# sent_id = nt",
      "# text = it isn't fine",
      "1\tit\tit\tPRON\tPRP\tNumber=Sing\t4\tnsubj\t_\t_",
      "2\tis\tbe\tAUX\tVBZ\tNumber=Sing\t4\tcop\t_\tSpaceAfter=No",
      "3\tn't\tnot\tPART\tRB\t_\t4\tadvmod\t_\t_",
      "4\tfine\tfine\tADJ\tJJ\t_\t0\troot\t_\tSpaceAfter=No",
      "",
      "# sent_id = apostrophe-s",
      "# text = it's fine",
      "1\tit\tit\tPRON\tPRP\tNumber=Sing\t3\tnsubj\t_\tSpaceAfter=No",
      "2\t's\tbe\tAUX\tVBZ\tNumber=Sing\t3\tcop\t_\t_",
      "3\tfine\tfine\tADJ\tJJ\t_\t0\troot\t_\tSpaceAfter=No",
      "",
      "# sent_id = cannot",
      "# text = I cannot tell",
      "1\tI\tI\tPRON\tPRP\tNumber=Sing\t3\tnsubj\t_\t_",
      "2\tcan\tcan\tAUX\tMD\t_\t3\taux\t_\tSpaceAfter=No",
      "3\tnot\tnot\tPART\tRB\t_\t4\tadvmod\t_\t_",
      "4\ttell\ttell\tVERB\tVB\t_\t0\troot\t_\tSpaceAfter=No",
      "",
      "# sent_id = nothing-to-do",
      "# text = it is fine",
      "1\tit\tit\tPRON\tPRP\tNumber=Sing\t3\tnsubj\t_\t_",
      "2\tis\tbe\tAUX\tVBZ\tNumber=Sing\t3\tcop\t_\t_",
      "3\tfine\tfine\tADJ\tJJ\t_\t0\troot\t_\tSpaceAfter=No",
      "",
      "");

  private static final int ISNT = 0;
  private static final int ITS = 1;
  private static final int CANNOT = 2;
  private static final int NOTHING = 3;

  /** The sentences of the fixture, as the reader gives them */
  private static List<SemanticGraph> read() throws Exception {
    File file = IOUtils.writeStringToTempFile(FIXTURE, "mwtcombinertest", "UTF-8");
    file.deleteOnExit();
    List<SemanticGraph> graphs = new ArrayList<>();
    try (CoNLLUReader.GraphIterator iterator = new CoNLLUReader().graphIterator(file.getPath())) {
      while (iterator.hasNext()) {
        Pair<SemanticGraph, SemanticGraph> pair = iterator.next();
        graphs.add(pair.first());
      }
    } finally {
      file.delete();
    }
    return graphs;
  }

  private static SemanticGraph combined(int which) throws Exception {
    return new EnglishMWTCombiner().combineMWTs(read().get(which));
  }

  private static String describeNode(IndexedWord word) {
    return word.value() + "-" + word.index();
  }

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

  /**
   * How each word of a graph was marked, in order: the word, and after it
   * the MWT it belongs to with a star on the first word of one
   */
  private static List<String> describeMWTs(SemanticGraph graph) {
    List<String> described = new ArrayList<>();
    for (IndexedWord word : graph.vertexListSorted()) {
      Boolean isMWT = word.get(CoreAnnotations.IsMultiWordTokenAnnotation.class);
      if (isMWT == null || !isMWT) {
        described.add(word.value());
      } else {
        Boolean first = word.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class);
        described.add(word.value() + " in " + word.get(CoreAnnotations.MWTTokenTextAnnotation.class) +
                      (first != null && first ? "*" : ""));
      }
    }
    return described;
  }

  /** The rows printSemanticGraph writes for a basic graph, ending with the blank line */
  private static String written(String... lines) {
    StringBuilder text = new StringBuilder();
    for (String line : lines) {
      text.append(line).append(System.lineSeparator());
    }
    text.append(System.lineSeparator());
    return text.toString();
  }

  private static String write(SemanticGraph graph) {
    return new CoNLLUDocumentWriter().printSemanticGraph(graph, null);
  }

  // ------------------------------------------------------------------
  // graphs built from their string form
  // ------------------------------------------------------------------

  @Test
  public void testApostropheSFromAGraph() {
    SemanticGraph sg = SemanticGraph.valueOf("[yours-4 nsubj> it-1 cop> 's-2 advmod> yours-3 punct> !-5]");
    sg.getNodeByIndexSafe(1).setAfter("");
    sg = new EnglishMWTCombiner().combineMWTs(sg);
    assertEquals(expectedITS, new CoNLLUDocumentWriter().printSemanticGraph(sg).trim());
  }

  @Test
  public void testCannotInCapitals() {
    // the patterns ignore case, and the words keep the case they had
    SemanticGraph sg = SemanticGraph.valueOf("[believe-4 nsubj> I-1 aux> CAN-2 advmod> NOT-3 obj> it-5 punct> !-6]");
    sg.getNodeByIndexSafe(2).setAfter("");
    sg = new EnglishMWTCombiner().combineMWTs(sg);
    assertEquals(expectedCANNOT, new CoNLLUDocumentWriter().printSemanticGraph(sg).trim());
  }

  @Test
  public void testWannaRewritesTheLemmas() {
    // wan and na are not words with lemmas of their own, so joining them
    // into wanna also gives them the lemmas want and to
    SemanticGraph sg = SemanticGraph.valueOf("[wan-2 nsubj> I-1 xcomp> [fix-4 mark> na-3 obj> this-5]]");
    sg.getNodeByIndexSafe(2).setAfter("");
    sg = new EnglishMWTCombiner().combineMWTs(sg);
    assertEquals(expectedWANNA, new CoNLLUDocumentWriter().printSemanticGraph(sg).trim());
  }

  // ------------------------------------------------------------------
  // which words are joined
  // ------------------------------------------------------------------

  @Test
  public void testNegativeContraction() throws Exception {
    assertEquals(Arrays.asList("it", "is in isn't*", "n't in isn't", "fine"),
                 describeMWTs(combined(ISNT)));
  }

  @Test
  public void testApostropheS() throws Exception {
    assertEquals(Arrays.asList("it in it's*", "'s in it's", "fine"),
                 describeMWTs(combined(ITS)));
  }

  @Test
  public void testCannot() throws Exception {
    // cannot needs can to have no space after it, which the fixture gives it
    assertEquals(Arrays.asList("I", "can in cannot*", "not in cannot", "tell"),
                 describeMWTs(combined(CANNOT)));
  }

  @Test
  public void testNothingToCombine() throws Exception {
    assertEquals(Arrays.asList("it", "is", "fine"), describeMWTs(combined(NOTHING)));
  }

  // ------------------------------------------------------------------
  // what joining them does and does not change
  // ------------------------------------------------------------------

  @Test
  public void testTheEdgesAreLeftAlone() throws Exception {
    // an MWT is a matter of how the words are written, not of how they
    // relate, so the graph keeps every edge it had
    for (int which = 0; which < 4; which++) {
      SemanticGraph before = read().get(which);
      assertEquals("sentence " + which, describeEdges(before),
                   describeEdges(new EnglishMWTCombiner().combineMWTs(before)));
    }
  }

  @Test
  public void testWrittenNegativeContraction() throws Exception {
    assertEquals(written(
        "# sent_id = nt",
        "# text = it isn't fine",
        "1\tit\tit\tPRON\tPRP\tNumber=Sing\t4\tnsubj\t_\t_",
        "2-3\tisn't\t_\t_\t_\t_\t_\t_\t_\t_",
        "2\tis\tbe\tAUX\tVBZ\tNumber=Sing\t4\tcop\t_\t_",
        "3\tn't\tnot\tPART\tRB\t_\t4\tadvmod\t_\t_",
        "4\tfine\tfine\tADJ\tJJ\t_\t0\troot\t_\tSpaceAfter=No"),
        write(combined(ISNT)));
  }

  @Test
  public void testWrittenApostropheS() throws Exception {
    assertEquals(written(
        "# sent_id = apostrophe-s",
        "# text = it's fine",
        "1-2\tit's\t_\t_\t_\t_\t_\t_\t_\t_",
        "1\tit\tit\tPRON\tPRP\tNumber=Sing\t3\tnsubj\t_\t_",
        "2\t's\tbe\tAUX\tVBZ\tNumber=Sing\t3\tcop\t_\t_",
        "3\tfine\tfine\tADJ\tJJ\t_\t0\troot\t_\tSpaceAfter=No"),
        write(combined(ITS)));
  }

  @Test
  public void testWrittenCannot() throws Exception {
    assertEquals(written(
        "# sent_id = cannot",
        "# text = I cannot tell",
        "1\tI\tI\tPRON\tPRP\tNumber=Sing\t3\tnsubj\t_\t_",
        "2-3\tcannot\t_\t_\t_\t_\t_\t_\t_\t_",
        "2\tcan\tcan\tAUX\tMD\t_\t3\taux\t_\t_",
        "3\tnot\tnot\tPART\tRB\t_\t4\tadvmod\t_\t_",
        "4\ttell\ttell\tVERB\tVB\t_\t0\troot\t_\tSpaceAfter=No"),
        write(combined(CANNOT)));
  }

  @Test
  public void testWrittenNothingToCombine() throws Exception {
    assertEquals(written(
        "# sent_id = nothing-to-do",
        "# text = it is fine",
        "1\tit\tit\tPRON\tPRP\tNumber=Sing\t3\tnsubj\t_\t_",
        "2\tis\tbe\tAUX\tVBZ\tNumber=Sing\t3\tcop\t_\t_",
        "3\tfine\tfine\tADJ\tJJ\t_\t0\troot\t_\tSpaceAfter=No"),
        write(combined(NOTHING)));
  }

  @Test
  public void testTheSentenceKeepsItsComments() throws Exception {
    // each Ssurgeon pattern works on a copy of the graph, so the comments
    // have to come through every one of those copies for the sent_id and
    // text of a sentence to still be there once it has been combined
    for (int which = 0; which < 4; which++) {
      SemanticGraph before = read().get(which);
      assertFalse(before.getComments().isEmpty());
      assertEquals("sentence " + which, before.getComments(),
                   new EnglishMWTCombiner().combineMWTs(before).getComments());
    }
  }
}
