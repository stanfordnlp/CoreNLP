package edu.stanford.nlp.trees.ud;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.CoNLLUReader;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.Pair;

/**
 * Tests for the enhancements in UniversalGrammaticalStructure.
 *<br>
 * Only addCaseMarkerInformation for now.  The graphs come out of the DEPS
 * column rather than out of the enhancements which would normally build
 * them, so that a test says something about the one method it names.
 *
 * @author John Bauer
 */
public class UniversalGrammaticalStructureTest {

  /**
   * A shared argument: soltura hangs off both conjoined verbs, and the one
   * preposition in front of it belongs on both of those relations
   */
  private static final String SHARED_ARGUMENT = String.join("\n",
      "# sent_id = shared-argument",
      "# text = Habla y escribe con soltura.",
      "1\tHabla\thablar\tVERB\t_\t_\t0\troot\t0:root\t_",
      "2\ty\ty\tCCONJ\t_\t_\t3\tcc\t3:cc\t_",
      "3\tescribe\tescribir\tVERB\t_\t_\t1\tconj\t1:conj:y\t_",
      "4\tcon\tcon\tADP\t_\t_\t5\tcase\t5:case\t_",
      "5\tsoltura\tsoltura\tNOUN\t_\t_\t1\tobl\t1:obl|3:obl\tSpaceAfter=No",
      "6\t.\t.\tPUNCT\t_\t_\t1\tpunct\t1:punct\t_",
      "",
      "");

  /** One governor, one preposition, which is the ordinary case */
  private static final String SINGLE_GOVERNOR = String.join("\n",
      "# sent_id = single-governor",
      "# text = Trabaja en Madrid.",
      "1\tTrabaja\ttrabajar\tVERB\t_\t_\t0\troot\t0:root\t_",
      "2\ten\ten\tADP\t_\t_\t3\tcase\t3:case\t_",
      "3\tMadrid\tMadrid\tPROPN\t_\t_\t1\tobl\t1:obl\tSpaceAfter=No",
      "4\t.\t.\tPUNCT\t_\t_\t1\tpunct\t1:punct\t_",
      "",
      "");

  /** The enhanced graph of the one sentence in the text, as written in its DEPS */
  private static SemanticGraph enhancedGraph(String conllu) throws Exception {
    File file = IOUtils.writeStringToTempFile(conllu, "ugstest", "UTF-8");
    file.deleteOnExit();
    try (CoNLLUReader.GraphIterator graphs = new CoNLLUReader().graphIterator(file.getPath())) {
      Pair<SemanticGraph, SemanticGraph> sentence = graphs.next();
      return sentence.second();
    } finally {
      file.delete();
    }
  }

  private static String describeNode(IndexedWord word) {
    return word.value() + "-" + word.toCopyOrEmptyIndex();
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

  private static List<String> sorted(String... edges) {
    List<String> list = new ArrayList<>(Arrays.asList(edges));
    Collections.sort(list);
    return list;
  }

  @Test
  public void testSimpleCaseMarker() throws Exception {
    SemanticGraph graph = enhancedGraph(SINGLE_GOVERNOR);
    UniversalGrammaticalStructure.addCaseMarkerInformation(graph);
    assertEquals(sorted("case(Madrid-3, en-2)",
                        "obl:en(Trabaja-1, Madrid-3)",
                        "punct(Trabaja-1, .-4)"),
                 describeEdges(graph));
  }

  @Test
  public void testSharedArgumentIsMarkedOnBothRelations() throws Exception {
    SemanticGraph graph = enhancedGraph(SHARED_ARGUMENT);

    // the fixture has to give soltura two governors for this to be a test
    // of anything, so say so before running the method
    assertEquals("expected soltura to hang off both verbs",
                 sorted("obl(Habla-1, soltura-5)", "obl(escribe-3, soltura-5)"),
                 oblEdges(graph));

    UniversalGrammaticalStructure.addCaseMarkerInformation(graph);

    // both of them carry the preposition, not just whichever was matched first
    assertEquals(sorted("obl:con(Habla-1, soltura-5)", "obl:con(escribe-3, soltura-5)"),
                 oblEdges(graph));
  }

  @Test
  public void testTheCaseMarkerIsAddedOnlyOnce() throws Exception {
    // the relation is obl:con rather than obl:con:con, whether the method
    // reaches an edge once or more than once
    SemanticGraph graph = enhancedGraph(SHARED_ARGUMENT);
    UniversalGrammaticalStructure.addCaseMarkerInformation(graph);
    UniversalGrammaticalStructure.addCaseMarkerInformation(graph);
    assertEquals(sorted("obl:con(Habla-1, soltura-5)", "obl:con(escribe-3, soltura-5)"),
                 oblEdges(graph));
  }

  @Test
  public void testNoCaseMarkedRelationIsLeftBare() throws Exception {
    // the property the two tests above are examples of: an nmod or obl
    // whose dependent has a case child should say which case
    for (String conllu : new String[] {SINGLE_GOVERNOR, SHARED_ARGUMENT}) {
      SemanticGraph graph = enhancedGraph(conllu);
      UniversalGrammaticalStructure.addCaseMarkerInformation(graph);
      for (SemanticGraphEdge edge : graph.edgeIterable()) {
        String shortName = edge.getRelation().getShortName();
        if (!shortName.equals("nmod") && !shortName.equals("obl")) {
          continue;
        }
        boolean hasCaseMarker = false;
        for (SemanticGraphEdge child : graph.outgoingEdgeIterable(edge.getDependent())) {
          if (child.getRelation().getShortName().equals("case")) {
            hasCaseMarker = true;
          }
        }
        if (hasCaseMarker) {
          assertNotNull(edge.getRelation().toString() + " should name its case marker",
                        edge.getRelation().getSpecific());
        }
      }
    }
  }

  /** Just the nmod and obl edges, which are the ones a case marker lands on */
  private static List<String> oblEdges(SemanticGraph graph) {
    List<String> described = new ArrayList<>();
    for (SemanticGraphEdge edge : graph.edgeIterable()) {
      String shortName = edge.getRelation().getShortName();
      if (shortName.equals("nmod") || shortName.equals("obl")) {
        described.add(edge.getRelation().toString() + "(" +
                      describeNode(edge.getGovernor()) + ", " +
                      describeNode(edge.getDependent()) + ")");
      }
    }
    Collections.sort(described);
    return described;
  }
}
