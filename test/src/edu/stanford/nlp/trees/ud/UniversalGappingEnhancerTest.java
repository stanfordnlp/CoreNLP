package edu.stanford.nlp.trees.ud;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.CoNLLUReader;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.Pair;

/**
 * What UniversalGappingEnhancer does to a gapped sentence, written down.
 *<br>
 * The embeddings are null throughout.  They only choose between candidate
 * alignments of the arguments; the copy nodes and the order they sit in are
 * made either way, and that is what these cover.
 *<br>
 * Each case reads the fixture again rather than sharing one.  makeSoftCopy
 * is ++numCopies on the word being copied, and a graph built from
 * typedDependencies holds the same IndexedWord objects, so enhancing twice
 * from one reading gives a copy node numbered 2 rather than 1.
 *
 * @author John Bauer
 */
public class UniversalGappingEnhancerTest {

  private static final String FIXTURE = String.join("\n",
      "# sent_id = gapped-with-empty",
      "# text = John bought apples and Mary pears.",
      "1\tJohn\tJohn\tPROPN\tNNP\t_\t2\tnsubj\t2:nsubj\t_",
      "2\tbought\tbuy\tVERB\tVBD\t_\t0\troot\t0:root\t_",
      "3\tapples\tapple\tNOUN\tNNS\t_\t2\tobj\t2:obj\t_",
      "4\tand\tand\tCCONJ\tCC\t_\t5\tcc\t5:cc\t_",
      "5\tMary\tMary\tPROPN\tNNP\t_\t2\tconj\t5.1:nsubj\t_",
      "5.1\tbought\tbuy\tVERB\tVBD\t_\t_\t_\t2:conj\t_",
      "6\tpears\tpear\tNOUN\tNNS\t_\t5\torphan\t5.1:obj\tSpaceAfter=No",
      "7\t.\t.\tPUNCT\t.\t_\t2\tpunct\t2:punct\t_",
      "",
      "# sent_id = gapped-no-deps",
      "# text = John bought apples and Mary pears.",
      "1\tJohn\tJohn\tPROPN\tNNP\t_\t2\tnsubj\t_\t_",
      "2\tbought\tbuy\tVERB\tVBD\t_\t0\troot\t_\t_",
      "3\tapples\tapple\tNOUN\tNNS\t_\t2\tobj\t_\t_",
      "4\tand\tand\tCCONJ\tCC\t_\t5\tcc\t_\t_",
      "5\tMary\tMary\tPROPN\tNNP\t_\t2\tconj\t_\t_",
      "6\tpears\tpear\tNOUN\tNNS\t_\t5\torphan\t_\tSpaceAfter=No",
      "7\t.\t.\tPUNCT\t.\t_\t2\tpunct\t_\t_",
      "",
      "# sent_id = two-orphans",
      "# text = John gave Mary apples and Bill Sue pears.",
      "1\tJohn\tJohn\tPROPN\tNNP\t_\t2\tnsubj\t_\t_",
      "2\tgave\tgive\tVERB\tVBD\t_\t0\troot\t_\t_",
      "3\tMary\tMary\tPROPN\tNNP\t_\t2\tiobj\t_\t_",
      "4\tapples\tapple\tNOUN\tNNS\t_\t2\tobj\t_\t_",
      "5\tand\tand\tCCONJ\tCC\t_\t6\tcc\t_\t_",
      "6\tBill\tBill\tPROPN\tNNP\t_\t2\tconj\t_\t_",
      "7\tSue\tSue\tPROPN\tNNP\t_\t6\torphan\t_\t_",
      "8\tpears\tpear\tNOUN\tNNS\t_\t6\torphan\t_\tSpaceAfter=No",
      "9\t.\t.\tPUNCT\t.\t_\t2\tpunct\t_\tSpaceAfter=No",
      "",
      "");

  private static final int WITH_EMPTY = 0;
  private static final int NO_DEPS = 1;
  private static final int TWO_ORPHANS = 2;

  /** The graph enhanceGraph would hand to the gapping enhancer, read fresh */
  private static SemanticGraph readyToEnhance(int which, boolean keepEmptyNodes) {
    List<Pair<SemanticGraph, SemanticGraph>> sentences = new ArrayList<>();
    try {
      File file = IOUtils.writeStringToTempFile(FIXTURE, "gappingtest", "UTF-8");
      file.deleteOnExit();
      try (CoNLLUReader.GraphIterator graphs = new CoNLLUReader().graphIterator(file.getPath())) {
        while (graphs.hasNext()) {
          sentences.add(graphs.next());
        }
      } finally {
        file.delete();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    Pair<SemanticGraph, SemanticGraph> sentence = sentences.get(which);
    SemanticGraph graph = new SemanticGraph(sentence.first().typedDependencies());
    if (keepEmptyNodes) {
      UniversalEnhancer.copyEmptyNodes(sentence.second(), graph);
    }
    return graph;
  }

  private static SemanticGraph enhanced(int which, boolean keepEmptyNodes) {
    SemanticGraph graph = readyToEnhance(which, keepEmptyNodes);
    UniversalGappingEnhancer.addEnhancements(graph, null);
    return graph;
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

  /** The vertices in the order the graph sorts them, which is the point of a copy count */
  private static List<String> describeSortedVertices(SemanticGraph graph) {
    List<String> described = new ArrayList<>();
    for (IndexedWord word : graph.vertexListSorted()) {
      described.add(describeNode(word));
    }
    return described;
  }

  private static List<String> sorted(String... edges) {
    List<String> list = new ArrayList<>(Arrays.asList(edges));
    Collections.sort(list);
    return list;
  }

  // ------------------------------------------------------------------
  // a gapped clause becomes a copy of the verb with the orphans hung off it
  // ------------------------------------------------------------------

  @Test
  public void testGappedClauseGetsACopyOfTheVerb() {
    SemanticGraph graph = enhanced(NO_DEPS, false);
    assertEquals(sorted("cc(bought-2.1, and-4)",
                        "conj(bought-2, bought-2.1)",
                        "nsubj(bought-2, John-1)",
                        "nsubj(bought-2.1, Mary-5)",
                        "obj(bought-2, apples-3)",
                        "obj(bought-2.1, pears-6)",
                        "punct(bought-2, .-7)"),
                 describeEdges(graph));
    assertEquals(Collections.singletonList("bought-2"),
                 Collections.singletonList(describeNode(graph.getFirstRoot())));
  }

  @Test
  public void testTheCopySortsAfterTheWordItCopies() {
    // the copy of bought goes between bought and apples, not at the end
    assertEquals(Arrays.asList("John-1", "bought-2", "bought-2.1", "apples-3",
                               "and-4", "Mary-5", "pears-6", ".-7"),
                 describeSortedVertices(enhanced(NO_DEPS, false)));
  }

  @Test
  public void testOrphansAreReplaced() {
    // the orphan is what says a clause is gapped, so none should be left
    for (String edge : describeEdges(enhanced(NO_DEPS, false))) {
      assertEquals(edge, false, edge.startsWith("orphan("));
    }
  }

  @Test
  public void testTwoOrphans() {
    SemanticGraph graph = enhanced(TWO_ORPHANS, false);
    assertEquals(sorted("cc(gave-2.1, and-5)",
                        "conj(gave-2, gave-2.1)",
                        "iobj(gave-2, Mary-3)",
                        "iobj(gave-2.1, Sue-7)",
                        "nsubj(gave-2, John-1)",
                        "nsubj(gave-2.1, Bill-6)",
                        "obj(gave-2, apples-4)",
                        "obj(gave-2.1, pears-8)",
                        "punct(gave-2, .-9)"),
                 describeEdges(graph));
    assertEquals(Arrays.asList("John-1", "gave-2", "gave-2.1", "Mary-3", "apples-4",
                               "and-5", "Bill-6", "Sue-7", "pears-8", ".-9"),
                 describeSortedVertices(graph));
  }

  // ------------------------------------------------------------------
  // empty words and copy nodes
  // ------------------------------------------------------------------

  @Test
  public void testASentenceWhichAlreadyHasItsEmptyWordIsLeftAlone() {
    // copyEmptyNodes took the orphan out and put the empty word in, and an
    // orphan is the only thing the gapping enhancer acts on, so there is
    // nothing left for it to do
    List<String> before = describeEdges(readyToEnhance(WITH_EMPTY, true));
    assertEquals(before, describeEdges(enhanced(WITH_EMPTY, true)));
  }

  @Test
  public void testEmptyWordSortsWhereItBelongs() {
    assertEquals(Arrays.asList("John-1", "bought-2", "apples-3", "and-4",
                               "Mary-5", "bought-5.1", "pears-6", ".-7"),
                 describeSortedVertices(readyToEnhance(WITH_EMPTY, true)));
  }

  @Test
  public void testWithoutTheEmptyWordTheGappingEnhancerRuns() {
    // the same sentence read without keeping its empty word still has its
    // orphan, so a copy node is made instead
    assertEquals(describeEdges(enhanced(NO_DEPS, false)),
                 describeEdges(enhanced(WITH_EMPTY, false)));
  }

  @Test
  public void testKeepEmptyNodesDoesNotMatterWithoutEmptyWords() {
    // gapped-no-deps has no empty word to keep, so asking for one changes
    // nothing about what the gapping enhancer does
    assertEquals(describeEdges(enhanced(NO_DEPS, false)),
                 describeEdges(enhanced(NO_DEPS, true)));
    assertEquals(describeSortedVertices(enhanced(NO_DEPS, false)),
                 describeSortedVertices(enhanced(NO_DEPS, true)));
  }
}
