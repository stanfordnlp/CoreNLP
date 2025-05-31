
package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.AnnotationLookup;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.trees.EnglishGrammaticalRelations;
import edu.stanford.nlp.util.XMLUtils;

public class SsurgeonTest {

  @Test
  public void readXMLEmptyPattern() {
    String newline = System.getProperty("line.separator");
    String doc = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>37</uid>",
                             "    <notes>This is a simple test</notes>",
                             "    <semgrex>{}=a1 &gt;appos=e1 {}=a2 &lt;nsubj=e2 {}=a3</semgrex>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    Ssurgeon inst = Ssurgeon.inst();
    List<SsurgeonPattern> pattern = inst.readFromString(doc);
    assertEquals(pattern.size(), 1);
  }

  static final String newline = System.getProperty("line.separator");

  @Test
  public void readXMLAddEdgeExecute() {
    String doc = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>This is a simple test of addEdge</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{}=a1 > {}=a2") + "</semgrex>",
                             "    <edit-list>addEdge -gov a1 -dep a2 -reln dep -weight 0.5</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    Ssurgeon inst = Ssurgeon.inst();
    List<SsurgeonPattern> patterns = inst.readFromString(doc);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern pattern = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[A obj> B obj> C]");
    Collection<SemanticGraph> newSgs = pattern.execute(sg);
    assertEquals(newSgs.size(), 2);
  }


  /**
   * Test that AddEdge, when iterated, adds exactly one more edge
   * between each parent/child pair if they matched the target relation
   */
  @Test
  public void readXMLAddEdgeIterate() {
    String doc = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>This is a simple test of addEdge</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{}=a1 >obj {}=a2") + "</semgrex>",
                             "    <edit-list>addEdge -gov a1 -dep a2 -reln dep -weight 0.5</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    Ssurgeon inst = Ssurgeon.inst();
    List<SsurgeonPattern> patterns = inst.readFromString(doc);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern pattern = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[A obj> B obj> C nsubj> [D obj> E]]");
    SemanticGraph newSg = pattern.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[A-0 obj> B-1 dep> B-1 obj> C-2 dep> C-2 nsubj> [D-3 obj> E-4 dep> E-4]]");

    assertEquals(newSg, expected);
  }

  /**
   * Test that removing an edge given its endpoints and the dependency type will remove all matching edges
   */
  @Test
  public void readXMLRemoveEdgeIterate() {
    String doc = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>This is a simple test of removeEdge</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{}=a1 > {}=a2") + "</semgrex>",
                             "    <edit-list>removeEdge -gov a1 -dep a2 -reln dep</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    Ssurgeon inst = Ssurgeon.inst();
    List<SsurgeonPattern> patterns = inst.readFromString(doc);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern pattern = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[A-0 obj> B-1 obj> C-2 nsubj> [D-3 obj> E-4]]");
    SemanticGraph newSg = pattern.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[A-0 obj> B-1 obj> C-2 nsubj> [D-3 obj> E-4]]");
    assertEquals(newSg, sg);

    sg = SemanticGraph.valueOf("[A-0 obj> B-1 dep> B-1 obj> C-2 nsubj> [D-3 obj> E-4]]");
    newSg = pattern.iterate(sg).first;
    assertEquals(newSg, expected);

    sg = SemanticGraph.valueOf("[A-0 obj> B-1 dep> B-1 obj> C-2 dep> C-2 nsubj> [D-3 obj> E-4 dep> E-4]]");
    newSg = pattern.iterate(sg).first;
    assertEquals(newSg, expected);
  }


  /**
   * Test that removing an edge given its endpoints and the dependency type will remove all matching edges
   */
  @Test
  public void readXMLRemoveEdgeNoRelationIterate() {
    String doc = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Test removing an edge with no relation set</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{word:B}=a1 > {word:E}=a2") + "</semgrex>",
                             "    <edit-list>removeEdge -gov a1 -dep a2</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    Ssurgeon inst = Ssurgeon.inst();
    List<SsurgeonPattern> patterns = inst.readFromString(doc);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern pattern = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[A-0 obj> [B-1 nsubj> E-4] obj> C-2 nsubj> [D-3 obj> E-4]]");
    SemanticGraph newSg = pattern.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[A-0 obj> B-1 obj> C-2 nsubj> [D-3 obj> E-4]]");
    assertEquals(newSg, expected);
  }


  /**
   * Test that removing a named edge will remove all matching edges
   */
  @Test
  public void readXMLRemoveNamedEdgeIterate() {
    String doc = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>This is a simple test of RemoveNamedEdge</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{}=a1 >dep=foo {}=a2") + "</semgrex>",
                             "    <edit-list>removeNamedEdge -edge foo</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    Ssurgeon inst = Ssurgeon.inst();
    List<SsurgeonPattern> patterns = inst.readFromString(doc);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern pattern = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[A-0 obj> B-1 obj> C-2 nsubj> [D-3 obj> E-4]]");
    SemanticGraph newSg = pattern.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[A-0 obj> B-1 obj> C-2 nsubj> [D-3 obj> E-4]]");
    assertEquals(newSg, sg);

    sg = SemanticGraph.valueOf("[A-0 obj> B-1 dep> B-1 obj> C-2 nsubj> [D-3 obj> E-4]]");
    newSg = pattern.iterate(sg).first;
    assertEquals(newSg, expected);

    sg = SemanticGraph.valueOf("[A-0 obj> B-1 dep> B-1 obj> C-2 dep> C-2 nsubj> [D-3 obj> E-4 dep> E-4]]");
    newSg = pattern.iterate(sg).first;
    assertEquals(newSg, expected);
  }

  /**
   * Test a few various relabel edge operations
   */
  @Test
  public void readXMLRelabelEdgeIterate() {
    String doc = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>This is a simple test of RelabelNamedEdge</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{}=a1 >obj=foo {}=a2") + "</semgrex>",
                             "    <edit-list>relabelNamedEdge -edge foo -reln dep</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    Ssurgeon inst = Ssurgeon.inst();
    List<SsurgeonPattern> patterns = inst.readFromString(doc);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern pattern = patterns.get(0);

    // check a simple case of relabeling
    SemanticGraph sg = SemanticGraph.valueOf("[A-0 obj> B-1]");
    SemanticGraph expected = SemanticGraph.valueOf("[A-0 dep> B-1]");
    SemanticGraph newSg = pattern.iterate(sg).first;
    assertEquals(newSg, expected);

    // check iteration over multiple edges
    sg = SemanticGraph.valueOf("[A-0 obj> [B-1 obj> C-2]]");
    expected = SemanticGraph.valueOf("[A-0 dep> [B-1 dep> C-2]]");
    newSg = pattern.iterate(sg).first;
    assertEquals(newSg, expected);

    // check that relabeling doesn't change a non-matching edge
    // (how would it?)
    sg = SemanticGraph.valueOf("[A-0 iobj> B-1]");
    expected = SemanticGraph.valueOf("[A-0 iobj> B-1]");
    newSg = pattern.iterate(sg).first;
    assertEquals(newSg, expected);

    // check that you don't get double edges if an update
    // makes two edges into the same edge
    sg = SemanticGraph.valueOf("[A-0 obj> B-1 dep> B-1]");
    expected = SemanticGraph.valueOf("[A-0 dep> B-1]");
    newSg = pattern.iterate(sg).first;
    assertEquals(newSg, expected);
  }

  /**
   * Test that the relabel named edge operation is bomb-proof.
   * The initial version would relabel an edge to the new edge name
   * even if the existing edge had the same name
   */
  @Test
  public void readXMLRelabelEdgeBombProof() {
    String doc = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>This is a simple test of RelabelNamedEdge</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{}=a1 >=foo {}=a2") + "</semgrex>",
                             "    <edit-list>relabelNamedEdge -edge foo -reln dep</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    Ssurgeon inst = Ssurgeon.inst();
    List<SsurgeonPattern> patterns = inst.readFromString(doc);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern pattern = patterns.get(0);

    // check a simple case of relabeling
    SemanticGraph sg = SemanticGraph.valueOf("[A-0 obj> B-1]");
    SemanticGraph expected = SemanticGraph.valueOf("[A-0 dep> B-1]");
    SemanticGraph newSg = pattern.iterate(sg).first;
    assertEquals(newSg, expected);
  }

  /**
   * Test that the RelabelNamedEdge operation updates the name of the edge in the SemgrexMatcher
   */
  @Test
  public void readXMLRelabelEdgeUpdateNamedEdge() {
    String doc = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>This is a simple test of RelabelNamedEdge</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{}=a1 >obj=foo {}=a2") + "</semgrex>",
                             "    <edit-list>relabelNamedEdge -edge foo -reln dep</edit-list>",
                             "    <edit-list>relabelNamedEdge -edge foo -reln gov</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    Ssurgeon inst = Ssurgeon.inst();
    List<SsurgeonPattern> patterns = inst.readFromString(doc);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern pattern = patterns.get(0);

    // check the result of a double relabel - should wind up as gov
    SemanticGraph sg = SemanticGraph.valueOf("[A-0 obj> B-1]");
    SemanticGraph expected = SemanticGraph.valueOf("[A-0 gov> B-1]");
    SemanticGraph newSg = pattern.iterate(sg).first;
    assertEquals(newSg, expected);

    // in this case, the dep should acquire the name of the obj
    // in the SemgrexMatcher.  the subsequent operation will pick up
    // that edge (with the original edge being deleted) and the
    // result will be one edge with the name "gov"
    // if the matcher did not update the named edge as expected,
    // the second operation would not fire
    sg = SemanticGraph.valueOf("[A-0 obj> B-1 dep> B-1]");
    expected = SemanticGraph.valueOf("[A-0 gov> B-1]");
    newSg = pattern.iterate(sg).first;
    assertEquals(newSg, expected);
  }

  /**
   * Check that cutting a graph with two nodes into two pieces, then
   * pruning any disjoint pieces, results in a graph with just the root
   * when done as a single operation
   */
  @Test
  public void readXMLPruneNodesIterate() {
    Ssurgeon inst = Ssurgeon.inst();

    String cut = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Remove dep edges</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{}=a1 > {}=a2") + "</semgrex>",
                             "    <edit-list>removeEdge -gov a1 -dep a2 -reln dep</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(cut);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern ssurgeonCut = patterns.get(0);

    String prune = String.join(newline,
                               "<ssurgeon-pattern-list>",
                               "  <ssurgeon-pattern>",
                               "    <uid>38</uid>",
                               "    <notes>This semgrex detects disjoint nodes</notes>",
                               "    <semgrex>" + XMLUtils.escapeXML("{}=disjoint !== {$} !<< {$}") + "</semgrex>",
                               "    <edit-list>delete -node disjoint</edit-list>",
                               "  </ssurgeon-pattern>",
                               "</ssurgeon-pattern-list>");
    patterns = inst.readFromString(prune);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern ssurgeonPrune = patterns.get(0);

    // Test a two node only version
    SemanticGraph sg = SemanticGraph.valueOf("[A dep> B]");
    SemanticGraph cutSG = ssurgeonCut.iterate(sg).first;
    assertEquals(cutSG.vertexSet().size(), 2);
    SemanticGraph pruneSG = ssurgeonPrune.iterate(cutSG).first;
    SemanticGraph expected = SemanticGraph.valueOf("[A]");
    assertEquals(pruneSG, expected);

    // Test a chain cut at the start
    sg = SemanticGraph.valueOf("[A dep> [B obj> C]]");
    cutSG = ssurgeonCut.iterate(sg).first;
    assertEquals(cutSG.vertexSet().size(), 3);
    pruneSG = ssurgeonPrune.iterate(cutSG).first;
    assertEquals(pruneSG, expected);

    // Test the chain cut at the bottom
    sg = SemanticGraph.valueOf("[A obj> [B dep> C]]");
    cutSG = ssurgeonCut.iterate(sg).first;
    assertEquals(cutSG.vertexSet().size(), 3);
    pruneSG = ssurgeonPrune.iterate(cutSG).first;
    assertEquals(pruneSG, SemanticGraph.valueOf("[A obj> B]"));

    // Test a chain cut at the start
    // Only the root will be left at the end
    sg = SemanticGraph.valueOf("[A dep> B dep> C]");
    cutSG = ssurgeonCut.iterate(sg).first;
    assertEquals(cutSG.vertexSet().size(), 3);
    pruneSG = ssurgeonPrune.iterate(cutSG).first;
    assertEquals(pruneSG, expected);
  }


  /**
   * Check that cutting a graph with two nodes into two pieces, then
   * pruning any disjoint pieces, results in a graph with just the root,
   * this time done as one combined operation
   */
  @Test
  public void readXMLTwoStepPruneIterate() {
    Ssurgeon inst = Ssurgeon.inst();

    String xml = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Remove dep edges and prune</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{}=a1 >dep {}=a2") + "</semgrex>",
                             "    <edit-list>removeEdge -gov a1 -dep a2 -reln dep</edit-list>",
                             "    <edit-list>delete -node a2</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(xml);
    assertEquals(1, patterns.size());
    SsurgeonPattern ssurgeon = patterns.get(0);
    assertEquals(2, ssurgeon.editScript.size());

    // Test a two node only version
    SemanticGraph sg = SemanticGraph.valueOf("[A dep> B]");
    SemanticGraph cutSG = ssurgeon.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[A]");
    assertEquals(1, cutSG.vertexSet().size());
    assertEquals(expected, cutSG);

    // Test a chain cut at the start
    sg = SemanticGraph.valueOf("[A dep> [B obj> C]]");
    cutSG = ssurgeon.iterate(sg).first;
    assertEquals(expected, cutSG);

    // Test the chain cut at the bottom
    sg = SemanticGraph.valueOf("[A obj> [B dep> C]]");
    cutSG = ssurgeon.iterate(sg).first;
    assertEquals(SemanticGraph.valueOf("[A obj> B]"), cutSG);

    // Test a chain cut at the start
    // Only the root will be left at the end
    sg = SemanticGraph.valueOf("[A dep> B dep> C]");
    cutSG = ssurgeon.iterate(sg).first;
    assertEquals(expected, cutSG);
  }

  /**
   * Test that if the root is removed by a prune operation,
   * the roots on the graph are reset
   */
  @Test
  public void readXMLPruneNodesResetRoots() {
    Ssurgeon inst = Ssurgeon.inst();

    String cut = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Remove any dep edges from the graph</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{}=a1 > {}=a2") + "</semgrex>",
                             "    <edit-list>removeEdge -gov a1 -dep a2 -reln dep</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(cut);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern ssurgeonCut = patterns.get(0);

    String prune = String.join(newline,
                               "<ssurgeon-pattern-list>",
                               "  <ssurgeon-pattern>",
                               "    <uid>38</uid>",
                               "    <notes>This semgrex detects disjoint roots</notes>",
                               "    <semgrex>" + XMLUtils.escapeXML("{$}=root : {} !== {}=root !>> {}=root") + "</semgrex>",
                               "    <edit-list>delete -node root</edit-list>",
                               "  </ssurgeon-pattern>",
                               "</ssurgeon-pattern-list>");
    patterns = inst.readFromString(prune);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern ssurgeonPrune = patterns.get(0);

    // Test a two node only version
    SemanticGraph sg = SemanticGraph.valueOf("[A dep> B]");
    SemanticGraph cutSG = ssurgeonCut.iterate(sg).first;
    assertEquals(2, cutSG.vertexSet().size());
    SemanticGraph pruneSG = ssurgeonPrune.iterate(cutSG).first;
    // note that for now, the prune operation doesn't renumber nodes in any way
    SemanticGraph expected = SemanticGraph.valueOf("[B-1]");
    assertEquals(expected, pruneSG);

    // Test the chain cut at the bottom
    sg = SemanticGraph.valueOf("[A obj> [B dep> C]]");
    cutSG = ssurgeonCut.iterate(sg).first;
    assertEquals(cutSG.vertexSet().size(), 3);
    pruneSG = ssurgeonPrune.iterate(cutSG).first;
    assertEquals(SemanticGraph.valueOf("[C-2]"), pruneSG);
  }

  /**
   * Check that cutting a graph with two nodes into two pieces, then
   * pruning any disjoint pieces, results in a graph with just the root
   */
  @Test
  public void readXMLKillNonRootedIterate() {
    Ssurgeon inst = Ssurgeon.inst();

    String cut = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Remove dep edges</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{}=a1 > {}=a2") + "</semgrex>",
                             "    <edit-list>removeEdge -gov a1 -dep a2 -reln dep</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(cut);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern ssurgeonCut = patterns.get(0);

    String prune = String.join(newline,
                               "<ssurgeon-pattern-list>",
                               "  <ssurgeon-pattern>",
                               "    <uid>38</uid>",
                               "    <notes>Match every graph, kill unrooted nodes</notes>",
                               "    <semgrex>" + XMLUtils.escapeXML("{$}") + "</semgrex>",
                               "    <edit-list>killNonRooted</edit-list>",
                               "  </ssurgeon-pattern>",
                               "</ssurgeon-pattern-list>");
    patterns = inst.readFromString(prune);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern ssurgeonPrune = patterns.get(0);

    // Test a two node only version
    SemanticGraph sg = SemanticGraph.valueOf("[A dep> B]");
    SemanticGraph cutSG = ssurgeonCut.iterate(sg).first;
    assertEquals(cutSG.vertexSet().size(), 2);
    SemanticGraph pruneSG = ssurgeonPrune.iterate(cutSG).first;
    SemanticGraph expected = SemanticGraph.valueOf("[A]");
    assertEquals(pruneSG, expected);

    // Test a chain cut at the start
    sg = SemanticGraph.valueOf("[A dep> [B obj> C]]");
    cutSG = ssurgeonCut.iterate(sg).first;
    assertEquals(cutSG.vertexSet().size(), 3);
    pruneSG = ssurgeonPrune.iterate(cutSG).first;
    assertEquals(pruneSG, expected);

    // Test the chain cut at the bottom
    sg = SemanticGraph.valueOf("[A obj> [B dep> C]]");
    cutSG = ssurgeonCut.iterate(sg).first;
    assertEquals(cutSG.vertexSet().size(), 3);
    pruneSG = ssurgeonPrune.iterate(cutSG).first;
    assertEquals(pruneSG, SemanticGraph.valueOf("[A obj> B]"));

    // Test a chain cut at the start
    // Only the root will be left at the end
    sg = SemanticGraph.valueOf("[A dep> B dep> C]");
    cutSG = ssurgeonCut.iterate(sg).first;
    assertEquals(cutSG.vertexSet().size(), 3);
    pruneSG = ssurgeonPrune.iterate(cutSG).first;
    assertEquals(pruneSG, expected);
  }

  @Test
  public void readXMLKillIncomingEdges() {
    Ssurgeon inst = Ssurgeon.inst();

    String cut = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Remove all incoming edges for a node</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{}=a1 >dep {}=a2") + "</semgrex>",
                             "    <edit-list>killAllIncomingEdges -node a2</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(cut);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern ssurgeonCut = patterns.get(0);

    // Test a two node only version
    SemanticGraph sg = SemanticGraph.valueOf("[A dep> B]");
    SemanticGraph cutSG = ssurgeonCut.iterate(sg).first;
    assertEquals(2, cutSG.vertexSet().size());
    cutSG.resetRoots();
    assertEquals(2, cutSG.getRoots().size());
  }

  /**
   * Check the result of rearranging an edge and then setting the root to a new label
   */
  @Test
  public void readXMLSetRoots() {
    Ssurgeon inst = Ssurgeon.inst();

    String cut = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Test the effects of setRoots on a simple change</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{word:A}=a >dep=dep {word:B}=b") + "</semgrex>",
                             "    <edit-list>removeNamedEdge -edge dep</edit-list>",
                             "    <edit-list>addEdge -gov b -dep a -reln dep</edit-list>",
                             "    <edit-list>setRoots b</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(cut);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern rearrange = patterns.get(0);

    // Test a two node only version
    SemanticGraph sg = SemanticGraph.valueOf("[A-1 dep> B-2]");
    SemanticGraph newSG = rearrange.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[B-2 dep> A-1]");
    assertEquals(expected, newSG);
  }


  /**
   * Check that a readable exception is thrown if the expected node doesn't exist for setRoots
   */
  @Test
  public void readXMLSetRootsException() {
    Ssurgeon inst = Ssurgeon.inst();

    String cut = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Remove all incoming edges for a node</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{word:A}=a >dep~dep {word:B}=b") + "</semgrex>",
                             "    <edit-list>removeNamedEdge -edge dep</edit-list>",
                             "    <edit-list>addEdge -gov b -dep a -reln dep</edit-list>",
                             "    <edit-list>setRoots c</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(cut);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern rearrange = patterns.get(0);

    // Test a two node only version
    SemanticGraph sg = SemanticGraph.valueOf("[A-1 dep> B-2]");
    try {
      SemanticGraph newSG = rearrange.iterate(sg).first;
      throw new AssertionError("Expected a specific exception SsurgeonRuntimeException here");
    } catch (SsurgeonRuntimeException e) {
      // yay
    }
  }

  /**
   * Check some various ways of adding nodes work as expected
   */
  @Test
  public void readXMLAddDep() {
    Ssurgeon inst = Ssurgeon.inst();

    // use "dep" as the dependency so as to be language-agnostic in this test
    String add = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Remove all incoming edges for a node</notes>",
                             // have to bomb-proof the pattern
                             "    <semgrex>" + XMLUtils.escapeXML("{word:antennae}=antennae !> {word:blue}") + "</semgrex>",
                             "    <edit-list>addDep -gov antennae -reln dep -word blue</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(add);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern addSsurgeon = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> antennae-3]");
    IndexedWord blueVertex = sg.getNodeByIndexSafe(4);
    assertNull(blueVertex);
    SemanticGraph newSG = addSsurgeon.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-3 dep> blue-4]]");
    assertEquals(expected, newSG);
    // the Ssurgeon we just created should not put a tag on the word
    blueVertex = newSG.getNodeByIndexSafe(4);
    assertNotNull(blueVertex);
    assertNull(blueVertex.tag());
    // the 4th word should be "blue" if position was not set
    assertEquals("blue", blueVertex.value());

    // This time, we expect that there will be a tag
    add = String.join(newline,
                      "<ssurgeon-pattern-list>",
                      "  <ssurgeon-pattern>",
                      "    <uid>38</uid>",
                      "    <notes>Remove all incoming edges for a node</notes>",
                      // have to bomb-proof the pattern
                      "    <semgrex>" + XMLUtils.escapeXML("{word:antennae}=antennae !> {word:blue}") + "</semgrex>",
                      "    <edit-list>addDep -gov antennae -reln dep -word blue -tag JJ</edit-list>",
                      "  </ssurgeon-pattern>",
                      "</ssurgeon-pattern-list>");
    patterns = inst.readFromString(add);
    assertEquals(patterns.size(), 1);
    addSsurgeon = patterns.get(0);

    sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> antennae-3]");
    newSG = addSsurgeon.iterate(sg).first;
    expected = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-3 dep> blue-4]]");
    assertEquals(expected, newSG);
    // this new Ssurgeon SHOULD put a tag on the word
    blueVertex = newSG.getNodeByIndexSafe(4);
    assertNotNull(blueVertex);
    assertEquals("JJ", blueVertex.tag());
    // the 4th word should be "blue" if position was not set
    assertEquals("blue", blueVertex.value());
  }

  /**
   * Check that adding a word to the start of a sentence works as expected
   */
  @Test
  public void readXMLAddDepStartPosition() {
    Ssurgeon inst = Ssurgeon.inst();

    // use "dep" as the dependency so as to be language-agnostic in this test
    String add = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Remove all incoming edges for a node</notes>",
                             // have to bomb-proof the pattern
                             "    <semgrex>" + XMLUtils.escapeXML("{word:antennae}=antennae !> {word:blue}") + "</semgrex>",
                             "    <edit-list>addDep -gov antennae -reln dep -word blue -position -</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(add);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern addSsurgeon = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> antennae-3]");
    IndexedWord blueVertex = sg.getNodeByIndexSafe(4);
    assertNull(blueVertex);
    SemanticGraph newSG = addSsurgeon.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[has-3 nsubj> Jennifer-2 obj> [antennae-4 dep> blue-1]]");
    assertEquals(expected, newSG);
    // the Ssurgeon we just created should not put a tag on the word
    // but it SHOULD put blue at the start of the sentence
    blueVertex = newSG.getNodeByIndexSafe(1);
    assertNotNull(blueVertex);
    assertNull(blueVertex.tag());
    assertEquals("blue", blueVertex.value());
  }

  /**
   * Check that adding a word before or after a named node works as expected
   */
  @Test
  public void readXMLAddDepRelativePosition() {
    Ssurgeon inst = Ssurgeon.inst();

    // use "dep" as the dependency so as to be language-agnostic in this test
    String add = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Add a word before antennae using the position</notes>",
                             // have to bomb-proof the pattern
                             "    <semgrex>" + XMLUtils.escapeXML("{word:antennae}=antennae !> {word:blue}") + "</semgrex>",
                             "    <edit-list>addDep -gov antennae -reln dep -word blue -position -antennae</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(add);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern addSsurgeon = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> antennae-3]");
    IndexedWord blueVertex = sg.getNodeByIndexSafe(4);
    assertNull(blueVertex);
    SemanticGraph newSG = addSsurgeon.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-4 dep> blue-3]]");
    assertEquals(expected, newSG);
    // the Ssurgeon we just created should not put a tag on the word
    // but it SHOULD put blue immediately before antennae
    blueVertex = newSG.getNodeByIndexSafe(3);
    assertNotNull(blueVertex);
    assertNull(blueVertex.tag());
    assertEquals("blue", blueVertex.value());

    // use "dep" as the dependency so as to be language-agnostic in this test
    add = String.join(newline,
                      "<ssurgeon-pattern-list>",
                      "  <ssurgeon-pattern>",
                      "    <uid>38</uid>",
                      "    <notes>Add a word after the word before antennae (just to test the position)</notes>",
                      // have to bomb-proof the pattern
                      "    <semgrex>" + XMLUtils.escapeXML("{word:antennae}=antennae - {}=prev !> {word:blue}") + "</semgrex>",
                      "    <edit-list>addDep -gov antennae -reln dep -word blue -position +prev</edit-list>",
                      "  </ssurgeon-pattern>",
                      "</ssurgeon-pattern-list>");
    patterns = inst.readFromString(add);
    assertEquals(patterns.size(), 1);
    addSsurgeon = patterns.get(0);

    sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> antennae-3]");
    blueVertex = sg.getNodeByIndexSafe(4);
    assertNull(blueVertex);
    newSG = addSsurgeon.iterate(sg).first;
    expected = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-4 dep> blue-3]]");
    assertEquals(expected, newSG);
    // the Ssurgeon we just created should not put a tag on the word
    // but it SHOULD put blue immediately before antennae
    blueVertex = newSG.getNodeByIndexSafe(3);
    assertNotNull(blueVertex);
    assertNull(blueVertex.tag());
    assertEquals("blue", blueVertex.value());
  }


  /**
   * Check that adding a word using the attributes of the edit-list works as expected
   */
  @Test
  public void readXMLAddDepNodeAttributes() {
    Ssurgeon inst = Ssurgeon.inst();

    // use "dep" as the dependency so as to be language-agnostic in this test
    String add = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Add a word using the attributes of the edit-list node</notes>",
                             // have to bomb-proof the pattern
                             "    <semgrex>" + XMLUtils.escapeXML("{word:antennae}=antennae !> {word:blue}") + "</semgrex>",
                             "    <edit-list word=\"blue\" reln=\"dep\">addDep -gov antennae -position -antennae</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(add);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern addSsurgeon = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> antennae-3]");
    IndexedWord blueVertex = sg.getNodeByIndexSafe(4);
    assertNull(blueVertex);
    SemanticGraph newSG = addSsurgeon.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-4 dep> blue-3]]");
    assertEquals(expected, newSG);
    // the Ssurgeon we just created should not put a tag on the word
    // but it SHOULD put blue immediately before antennae
    blueVertex = newSG.getNodeByIndexSafe(3);
    assertNotNull(blueVertex);
    assertNull(blueVertex.tag());
    assertEquals("blue", blueVertex.value());


    // use "dep" as the dependency so as to be language-agnostic in this test
    // this time, be cheeky and use some whitespace in the word
    add = String.join(newline,
                      "<ssurgeon-pattern-list>",
                      "  <ssurgeon-pattern>",
                      "    <uid>38</uid>",
                      "    <notes>Add a word using the attributes of the edit-list node</notes>",
                      // have to bomb-proof the pattern
                      "    <semgrex>" + XMLUtils.escapeXML("{word:antennae}=antennae !> {word:/bl ue/}") + "</semgrex>",
                      "    <edit-list word=\"bl ue\" reln=\"dep\">addDep -gov antennae -position -antennae</edit-list>",
                      "  </ssurgeon-pattern>",
                      "</ssurgeon-pattern-list>");
    patterns = inst.readFromString(add);
    assertEquals(patterns.size(), 1);
    addSsurgeon = patterns.get(0);

    sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> antennae-3]");
    blueVertex = sg.getNodeByIndexSafe(4);
    assertNull(blueVertex);
    newSG = addSsurgeon.iterate(sg).first;
    expected = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-4 dep> blue-3]]");
    assertEquals(expected, newSG);
    // the Ssurgeon we just created should not put a tag on the word
    // but it SHOULD put blue immediately before antennae
    blueVertex = newSG.getNodeByIndexSafe(3);
    assertNotNull(blueVertex);
    assertNull(blueVertex.tag());
    assertEquals("bl ue", blueVertex.value());
  }


  /**
   * Check that adding a word using quotes for the attributes
   */
  @Test
  public void readXMLAddDepQuotedAttributes() {
    Ssurgeon inst = Ssurgeon.inst();

    // use "dep" as the dependency so as to be language-agnostic in this test
    String add = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Add a word using the attributes of the edit-list node</notes>",
                             // have to bomb-proof the pattern
                             "    <semgrex>" + XMLUtils.escapeXML("{word:antennae}=antennae !> {word:blue}") + "</semgrex>",
                             "    <edit-list>addDep -gov antennae -reln \"dep\" -word \"blue\" -position -antennae</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(add);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern addSsurgeon = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> antennae-3]");
    IndexedWord blueVertex = sg.getNodeByIndexSafe(4);
    assertNull(blueVertex);
    SemanticGraph newSG = addSsurgeon.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-4 dep> blue-3]]");
    assertEquals(expected, newSG);
    // the Ssurgeon we just created should not put a tag on the word
    // but it SHOULD put blue immediately before antennae
    blueVertex = newSG.getNodeByIndexSafe(3);
    assertNotNull(blueVertex);
    assertNull(blueVertex.tag());
    assertEquals("blue", blueVertex.value());

    // use "dep" as the dependency so as to be language-agnostic in this test
    // this time, be cheeky and use some whitespace in the word
    add = String.join(newline,
                      "<ssurgeon-pattern-list>",
                      "  <ssurgeon-pattern>",
                      "    <uid>38</uid>",
                      "    <notes>Add a word using the attributes of the edit-list node</notes>",
                      // have to bomb-proof the pattern
                      "    <semgrex>" + XMLUtils.escapeXML("{word:antennae}=antennae !> {word:/bl ue/}") + "</semgrex>",
                      "    <edit-list>addDep -gov antennae -reln \"dep\" -word \"bl ue\" -position -antennae</edit-list>",
                      "  </ssurgeon-pattern>",
                      "</ssurgeon-pattern-list>");
    patterns = inst.readFromString(add);
    assertEquals(patterns.size(), 1);
    addSsurgeon = patterns.get(0);

    sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> antennae-3]");
    blueVertex = sg.getNodeByIndexSafe(4);
    assertNull(blueVertex);
    newSG = addSsurgeon.iterate(sg).first;
    expected = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-4 dep> blue-3]]");
    assertEquals(expected, newSG);
    // the Ssurgeon we just created should not put a tag on the word
    // but it SHOULD put blue immediately before antennae
    blueVertex = newSG.getNodeByIndexSafe(3);
    assertNotNull(blueVertex);
    assertNull(blueVertex.tag());
    assertEquals("bl ue", blueVertex.value());
  }


  /**
   * Add a word, this time setting the morphological features as well
   */
  @Test
  public void readXMLAddDepMorphoFeatures() {
    Ssurgeon inst = Ssurgeon.inst();

    // use "dep" as the dependency so as to be language-agnostic in this test
    String add = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Add a word before antennae using the position</notes>",
                             // have to bomb-proof the pattern
                             "    <semgrex>" + XMLUtils.escapeXML("{word:antennae}=antennae !> {word:blue}") + "</semgrex>",
                             "    <edit-list>addDep -gov antennae -reln dep -word blue -position -antennae -morphofeatures a=b|c=d</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(add);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern addSsurgeon = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> antennae-3]");
    IndexedWord blueVertex = sg.getNodeByIndexSafe(4);
    assertNull(blueVertex);
    SemanticGraph newSG = addSsurgeon.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-4 dep> blue-3]]");
    assertEquals(expected, newSG);
    // the Ssurgeon we just created should not put a tag on the word
    // but it SHOULD put blue immediately before antennae
    blueVertex = newSG.getNodeByIndexSafe(3);
    assertNotNull(blueVertex);
    assertNull(blueVertex.tag());
    assertEquals("blue", blueVertex.value());

    Map<String, String> expectedFeatures = new HashMap<String, String>() {{
        put("a", "b");
        put("c", "d");
      }};
    assertEquals(expectedFeatures, blueVertex.get(CoreAnnotations.CoNLLUFeats.class));
  }

  /**
   * Set the language when adding a dep.  Should create a UniversalEnglish dependency
   */
  @Test
  public void readXMLAddUniversalDep() {
    Ssurgeon inst = Ssurgeon.inst();

    String add = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Add a word before antennae using the position using a UniversalEnglish dependency</notes>",
                             "    <language>UniversalEnglish</language>",
                             // have to bomb-proof the pattern
                             "    <semgrex>" + XMLUtils.escapeXML("{word:antennae}=antennae !> {word:blue}") + "</semgrex>",
                             "    <edit-list>addDep -gov antennae -reln amod -word blue -position -antennae</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(add);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern addSsurgeon = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> antennae-3]", Language.UniversalEnglish);
    IndexedWord blueVertex = sg.getNodeByIndexSafe(4);
    assertNull(blueVertex);
    SemanticGraph newSG = addSsurgeon.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-4 amod> blue-3]]", Language.UniversalEnglish);
    for (SemanticGraphEdge edge : expected.edgeIterable()) {
      assertEquals(Language.UniversalEnglish, edge.getRelation().getLanguage());
    }
    for (SemanticGraphEdge edge : newSG.edgeIterable()) {
      assertEquals(Language.UniversalEnglish, edge.getRelation().getLanguage());
    }
    assertEquals(expected, newSG);
    // the Ssurgeon we just created should not put a tag on the word
    // but it SHOULD put blue immediately before antennae
    blueVertex = newSG.getNodeByIndexSafe(3);
    assertNotNull(blueVertex);
    assertNull(blueVertex.tag());
    assertEquals("blue", blueVertex.value());


    // If we repeat the same test with English (SD, not UD) it should fail horribly
    // this is because SemanticGraph.valueOf will use UniversalEnglish dependencies by default
    add = String.join(newline,
                      "<ssurgeon-pattern-list>",
                      "  <ssurgeon-pattern>",
                      "    <uid>38</uid>",
                      "    <notes>Add a word before antennae using the position using an English dependency</notes>",
                      "    <language>English</language>",
                      // have to bomb-proof the pattern
                      "    <semgrex>" + XMLUtils.escapeXML("{word:antennae}=antennae !> {word:blue}") + "</semgrex>",
                      "    <edit-list>addDep -gov antennae -reln amod -word blue -position -antennae</edit-list>",
                      "  </ssurgeon-pattern>",
                      "</ssurgeon-pattern-list>");
    patterns = inst.readFromString(add);
    assertEquals(patterns.size(), 1);
    addSsurgeon = patterns.get(0);

    sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> antennae-3]", Language.UniversalEnglish);
    blueVertex = sg.getNodeByIndexSafe(4);
    assertNull(blueVertex);
    newSG = addSsurgeon.iterate(sg).first;
    expected = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-4 amod> blue-3]]", Language.UniversalEnglish);
    for (SemanticGraphEdge edge : expected.edgeIterable()) {
      assertEquals(Language.UniversalEnglish, edge.getRelation().getLanguage());
    }
    // they look the same, but they're really not
    // the edge is of a different Relation formalism
    assertEquals(expected.toString(), newSG.toString());
    assertNotEquals(expected, newSG);

    // In this third version, now valueOf is creating an English graph,
    // not a UniversalEnglish graph, so it should work again
    sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> antennae-3]", Language.English);
    blueVertex = sg.getNodeByIndexSafe(4);
    assertNull(blueVertex);
    newSG = addSsurgeon.iterate(sg).first;
    expected = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-4 amod> blue-3]]", Language.English);
    for (SemanticGraphEdge edge : expected.edgeIterable()) {
      assertEquals(Language.English, edge.getRelation().getLanguage());
    }
    assertEquals(expected, newSG);
  }

  /**
   * There should be an exception for an annotation key that does not exist
   */
  @Test
  public void readXMLAddDepBrokenAnnotation() {
    String missingKey = "zzzzzz";
    assertNull(AnnotationLookup.toCoreKey(missingKey));
    Ssurgeon inst = Ssurgeon.inst();

    // use "dep" as the dependency so as to be language-agnostic in this test
    String add = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Remove all incoming edges for a node</notes>",
                             // have to bomb-proof the pattern
                             "    <semgrex>" + XMLUtils.escapeXML("{word:antennae}=antennae !> {word:blue}") + "</semgrex>",
                             "    <edit-list>addDep -gov antennae -reln dep -" + missingKey + " blue</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    try {
      List<SsurgeonPattern> patterns = inst.readFromString(add);
      throw new AssertionError("Expected a failure because of missingKey " + missingKey);
    } catch (SsurgeonParseException e) {
      // yay
    }
  }

  /**
   * Test a basic case of two nodes that should be merged
   *<br>
   * The indices should be changed as well
   */
  @Test
  public void readXMLMergeNodes() {
    Ssurgeon inst = Ssurgeon.inst();

    // Test the head word being the first word
    String merge = String.join(newline,
                               "<ssurgeon-pattern-list>",
                               "  <ssurgeon-pattern>",
                               "    <uid>38</uid>",
                               "    <notes>Merge two nodes that should not have been split</notes>",
                               "    <semgrex>" + XMLUtils.escapeXML("{word:prof}=source >punct ({}=punct . {} !> {})") + "</semgrex>",
                               "    <edit-list>mergeNodes -node source -node punct</edit-list>",
                               "  </ssurgeon-pattern>",
                               "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(merge);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern mergeSsurgeon = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[fare-7 aux> potrebbe-6 nsubj> [prof-3 det> Il-2 punct> .-4 nmod> Fotticchia-5] obj> [gag-9 det> una-8] obl> [situazione-12 case> su-10 det> la-11]]", Language.UniversalEnglish);
    SemanticGraph newSG = mergeSsurgeon.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[fare-6 aux> potrebbe-5 nsubj> [prof.-3 det> Il-2 nmod> Fotticchia-4] obj> [gag-8 det> una-7] obl> [situazione-11 case> su-9 det> la-10]]", Language.UniversalEnglish);
    assertEquals(expected, newSG);
    IndexedWord prof = sg.getNodeByIndexSafe(3);
    assertNotNull(prof);
    assertEquals("prof.", prof.word());
    assertEquals("prof.", prof.value());
    assertNull(prof.lemma());

    // Same test, but this time test merging the lemmas
    sg = SemanticGraph.valueOf("[fare-7 aux> potrebbe-6 nsubj> [prof-3 det> Il-2 punct> .-4 nmod> Fotticchia-5] obj> [gag-9 det> una-8] obl> [situazione-12 case> su-10 det> la-11]]", Language.UniversalEnglish);
    sg.getNodeByIndexSafe(3).setLemma("prof");
    sg.getNodeByIndexSafe(4).setLemma(".");
    newSG = mergeSsurgeon.iterate(sg).first;
    assertEquals(expected, newSG);
    prof = sg.getNodeByIndexSafe(3);
    assertEquals("prof.", prof.lemma());

    // Test the head word being the second word
    merge = String.join(newline,
                        "<ssurgeon-pattern-list>",
                        "  <ssurgeon-pattern>",
                        "    <uid>38</uid>",
                        "    <notes>Merge two nodes that should not have been split</notes>",
                        "    <semgrex>" + XMLUtils.escapeXML("{word:prof}=source >punct ({}=punct . {} !> {})") + "</semgrex>",
                        "    <edit-list>mergeNodes -node source -node punct</edit-list>",
                        "  </ssurgeon-pattern>",
                        "</ssurgeon-pattern-list>");
    patterns = inst.readFromString(merge);
    assertEquals(patterns.size(), 1);
    mergeSsurgeon = patterns.get(0);

    // Check what happens if the root of the phrase is on the right and the dep is on the left
    // The words & lemmas should still hopefully be merged in order
    sg = SemanticGraph.valueOf("[fare-7 aux> potrebbe-6 nsubj> [prof-4 det> Il-2 punct> .-3 nmod> Fotticchia-5] obj> [gag-9 det> una-8] obl> [situazione-12 case> su-10 det> la-11]]", Language.UniversalEnglish);
    sg.getNodeByIndexSafe(3).setLemma(".");
    assertEquals(".", sg.getNodeByIndexSafe(3).word());
    sg.getNodeByIndexSafe(4).setLemma("prof");
    newSG = mergeSsurgeon.iterate(sg).first;
    expected = SemanticGraph.valueOf("[fare-6 aux> potrebbe-5 nsubj> [.prof-3 det> Il-2 nmod> Fotticchia-4] obj> [gag-8 det> una-7] obl> [situazione-11 case> su-9 det> la-10]]", Language.UniversalEnglish);
    assertEquals(expected, newSG);
    prof = newSG.getNodeByIndexSafe(3);
    assertEquals(".prof", prof.word());
    assertEquals(".prof", prof.lemma());
  }

  /**
   * Test merging three nodes at once
   *<br>
   * The indices should be changed as well
   */
  @Test
  public void readXMLMergeNodesMultiple() {
    Ssurgeon inst = Ssurgeon.inst();

    // Test the head word being the first word
    String merge = String.join(newline,
                               "<ssurgeon-pattern-list>",
                               "  <ssurgeon-pattern>",
                               "    <uid>38</uid>",
                               "    <notes>Merge three nodes that should not have been split</notes>",
                               "    <semgrex>" + XMLUtils.escapeXML("{word:prof}=source >punct ({}=punct . {} !> {}) >nmod ({}=nmod !> {})") + "</semgrex>",
                               "    <edit-list>mergeNodes -node source -node punct -node nmod</edit-list>",
                               "  </ssurgeon-pattern>",
                               "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(merge);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern mergeSsurgeon = patterns.get(0);

    // nodes 3, 4, 5 are in order in the same unit, so we should be able to merge them
    SemanticGraph sg = SemanticGraph.valueOf("[fare-7 aux> potrebbe-6 nsubj> [prof-3 det> Il-2 punct> .-4 nmod> Fotticchia-5] obj> [gag-9 det> una-8] obl> [situazione-12 case> su-10 det> la-11]]", Language.UniversalEnglish);
    SemanticGraph expected = SemanticGraph.valueOf("[fare-5 aux> potrebbe-4 nsubj> [prof.Fotticchia-3 det> Il-2] obj> [gag-7 det> una-6] obl> [situazione-10 case> su-8 det> la-9]]", Language.UniversalEnglish);
    sg.getNodeByIndexSafe(3).setLemma("prof");
    sg.getNodeByIndexSafe(4).setLemma(".");
    sg.getNodeByIndexSafe(5).setLemma("Fotticchia");
    SemanticGraph newSG = mergeSsurgeon.iterate(sg).first;
    assertEquals(expected, newSG);
    IndexedWord prof = sg.getNodeByIndexSafe(3);
    assertEquals("prof.Fotticchia", prof.lemma());
  }

  /**
   * A simple test sent to us from a user (unbelievably, ssurgeon apparently has users)
   */
  @Test
  public void readXMLMergeNodesIceCream() {
    Ssurgeon inst = Ssurgeon.inst();

    // demostrate merging with the order ice, cream -> icecream
    String merge = String.join(newline,
                               "<ssurgeon-pattern-list>",
                               "  <ssurgeon-pattern>",
                               "    <uid>38</uid>",
                               "    <notes>Merge two nodes that should not have been split</notes>",
                               "    <semgrex>" + XMLUtils.escapeXML("{}=gov >obj ({word:cream}=node1 >compound {word:ice}=node2)") + "</semgrex>",
                               "    <edit-list>mergeNodes -node node2 -node node1</edit-list>",
                               "  </ssurgeon-pattern>",
                               "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(merge);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern mergeSsurgeon = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[likes-3 nsubj> [child-2 det> The-1] obj> [cream-5 compound> ice-4]", Language.UniversalEnglish);
    SemanticGraph newSG = mergeSsurgeon.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[likes-3 nsubj> [child-2 det> The-1] obj> icecream-4]", Language.UniversalEnglish);
    assertEquals(expected, newSG);

    merge = String.join(newline,
                        "<ssurgeon-pattern-list>",
                        "  <ssurgeon-pattern>",
                        "    <uid>38</uid>",
                        "    <notes>Merge two nodes that should not have been split</notes>",
                        "    <semgrex>" + XMLUtils.escapeXML("{}=gov >obj ({word:cream}=node1 >compound {word:ice}=node2)") + "</semgrex>",
                        "    <edit-list>mergeNodes -node node1 -node node2</edit-list>",
                        "  </ssurgeon-pattern>",
                        "</ssurgeon-pattern-list>");
    patterns = inst.readFromString(merge);
    assertEquals(patterns.size(), 1);
    mergeSsurgeon = patterns.get(0);

    sg = SemanticGraph.valueOf("[likes-3 nsubj> [child-2 det> The-1] obj> [cream-5 compound> ice-4]", Language.UniversalEnglish);
    newSG = mergeSsurgeon.iterate(sg).first;
    expected = SemanticGraph.valueOf("[likes-3 nsubj> [child-2 det> The-1] obj> icecream-4]", Language.UniversalEnglish);
    assertEquals(expected, newSG);
  }

  /**
   * Test a basic case of two nodes that should be merged
   *<br>
   * The indices should be changed as well
   */
  @Test
  public void readXMLMergeNodesAttributes() {
    Ssurgeon inst = Ssurgeon.inst();

    // Test the head word being the first word
    String merge = String.join(newline,
                               "<ssurgeon-pattern-list>",
                               "  <ssurgeon-pattern>",
                               "    <uid>38</uid>",
                               "    <notes>Merge two nodes that should not have been split</notes>",
                               "    <semgrex>" + XMLUtils.escapeXML("{word:prof}=source >punct ({}=punct . {} !> {})") + "</semgrex>",
                               "    <edit-list>mergeNodes -node source -node punct -word foo -lemma bar</edit-list>",
                               "  </ssurgeon-pattern>",
                               "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(merge);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern mergeSsurgeon = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[fare-7 aux> potrebbe-6 nsubj> [prof-3 det> Il-2 punct> .-4 nmod> Fotticchia-5] obj> [gag-9 det> una-8] obl> [situazione-12 case> su-10 det> la-11]]", Language.UniversalEnglish);
    SemanticGraph newSG = mergeSsurgeon.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[fare-6 aux> potrebbe-5 nsubj> [foo-3 det> Il-2 nmod> Fotticchia-4] obj> [gag-8 det> una-7] obl> [situazione-11 case> su-9 det> la-10]]", Language.UniversalEnglish);
    assertEquals(expected, newSG);
    IndexedWord prof = sg.getNodeByIndexSafe(3);
    assertNotNull(prof);
    assertEquals("foo", prof.word());
    assertEquals("foo", prof.value());
    assertEquals("bar", prof.lemma());

    // Test the head word being the first word
    merge = String.join(newline,
                        "<ssurgeon-pattern-list>",
                        "  <ssurgeon-pattern>",
                        "    <uid>38</uid>",
                        "    <notes>Merge two nodes that should not have been split</notes>",
                        "    <semgrex>" + XMLUtils.escapeXML("{word:prof}=source >punct ({}=punct . {} !> {})") + "</semgrex>",
                        "    <edit-list>mergeNodes -node source -node punct -lemma bar</edit-list>",
                        "  </ssurgeon-pattern>",
                        "</ssurgeon-pattern-list>");
    patterns = inst.readFromString(merge);
    assertEquals(patterns.size(), 1);
    mergeSsurgeon = patterns.get(0);

    sg = SemanticGraph.valueOf("[fare-7 aux> potrebbe-6 nsubj> [prof-3 det> Il-2 punct> .-4 nmod> Fotticchia-5] obj> [gag-9 det> una-8] obl> [situazione-12 case> su-10 det> la-11]]", Language.UniversalEnglish);
    newSG = mergeSsurgeon.iterate(sg).first;
    expected = SemanticGraph.valueOf("[fare-6 aux> potrebbe-5 nsubj> [foo-3 det> Il-2 nmod> Fotticchia-4] obj> [gag-8 det> una-7] obl> [situazione-11 case> su-9 det> la-10]]", Language.UniversalEnglish);
    assertEquals(expected, newSG);
    prof = sg.getNodeByIndexSafe(3);
    assertNotNull(prof);
    assertEquals("prof.", prof.word());
    assertEquals("prof.", prof.value());
    assertEquals("bar", prof.lemma());
  }

  /**
   * Test a basic case of two nodes that should be merged
   *<br>
   * The indices should be changed as well
   */
  @Test
  public void readXMLMergeNodesFailCases() {
    Ssurgeon inst = Ssurgeon.inst();

    // use "dep" as the dependency so as to be language-agnostic in this test
    String merge = String.join(newline,
                               "<ssurgeon-pattern-list>",
                               "  <ssurgeon-pattern>",
                               "    <uid>38</uid>",
                               "    <notes>Merge two nodes that should not have been split</notes>",
                               "    <semgrex>" + XMLUtils.escapeXML("{word:prof}=source >punct ({}=punct . {} !> {})") + "</semgrex>",
                               "    <edit-list>mergeNodes -node source -node punct</edit-list>",
                               "  </ssurgeon-pattern>",
                               "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(merge);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern mergeSsurgeon = patterns.get(0);

    // Add an extra edge from the punct we want to squash to somewhere else
    // The graph should not be changed
    SemanticGraph sg = SemanticGraph.valueOf("[fare-7 aux> potrebbe-6 nsubj> [prof-3 det> Il-2 nmod> Fotticchia-5 punct> [.-4 nmod> Fotticchia-5]] obj> [gag-9 det> una-8] obl> [situazione-12 case> su-10 det> la-11]]", Language.UniversalEnglish);
    SemanticGraph newSG = mergeSsurgeon.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[fare-7 aux> potrebbe-6 nsubj> [prof-3 det> Il-2 nmod> Fotticchia-5 punct> [.-4 nmod> Fotticchia-5]] obj> [gag-9 det> una-8] obl> [situazione-12 case> su-10 det> la-11]]", Language.UniversalEnglish);
    assertEquals(expected, newSG);

    sg = SemanticGraph.valueOf("[fare-7 aux> potrebbe-6 nsubj> [prof-3 det> Il-2 nmod> [Fotticchia-5 punct> .-4] punct> .-4] obj> [gag-9 det> una-8] obl> [situazione-12 case> su-10 det> la-11]]", Language.UniversalEnglish);
    newSG = mergeSsurgeon.iterate(sg).first;
    expected = SemanticGraph.valueOf("[fare-7 aux> potrebbe-6 nsubj> [prof-3 det> Il-2 nmod> [Fotticchia-5 punct> .-4] punct> .-4] obj> [gag-9 det> una-8] obl> [situazione-12 case> su-10 det> la-11]]", Language.UniversalEnglish);
    assertEquals(expected, newSG);
  }

  /**
   * The AddDep should update the matches in the SemgrexMatcher.
   * If that isn't done correctly, then moving the words first
   * and then trying to update the word that was moved
   * would not work
   */
  @Test
  public void readXMLCheckSMNodes() {
    Ssurgeon inst = Ssurgeon.inst();

    // use "dep" as the dependency so as to be language-agnostic in this test
    String add = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Add a word before antennae using the position</notes>",
                             // have to bomb-proof the pattern
                             "    <semgrex>" + XMLUtils.escapeXML("{word:antenna}=antennae !> {word:blue}") + "</semgrex>",
                             "    <edit-list>addDep -gov antennae -reln dep -word blue -position -antennae</edit-list>",
                             "    <edit-list>editNode -node antennae -word antennae</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(add);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern addSsurgeon = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> antenna-3]");
    IndexedWord blueVertex = sg.getNodeByIndexSafe(4);
    assertNull(blueVertex);
    SemanticGraph newSG = addSsurgeon.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-4 dep> blue-3]]");
    assertEquals(expected, newSG);
    // the Ssurgeon we just created should not put a tag on the word
    // but it SHOULD put blue immediately before antennae
    blueVertex = newSG.getNodeByIndexSafe(3);
    assertNotNull(blueVertex);
    assertNull(blueVertex.tag());
    assertEquals("blue", blueVertex.value());
  }


  /**
   * The AddDep should update the edge matches in the SemgrexMatcher.
   * If that isn't done correctly, then moving the words first
   * and then trying to update an edge that matched would not work
   */
  @Test
  public void readXMLCheckSMEdges() {
    Ssurgeon inst = Ssurgeon.inst();

    // use "dep" as the dependency so as to be language-agnostic in this test
    String add = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Add a word before antennae using the position</notes>",
                             // have to bomb-proof the pattern
                             "    <semgrex>" + XMLUtils.escapeXML("{word:antennae}=antennae <obj=obj {} !> {word:blue}") + "</semgrex>",
                             "    <edit-list>addDep -gov antennae -reln dep -word blue -position -antennae</edit-list>",
                             "    <edit-list>relabelNamedEdge -edge obj -reln dep</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(add);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern addSsurgeon = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> antennae-3]");
    IndexedWord blueVertex = sg.getNodeByIndexSafe(4);
    assertNull(blueVertex);
    SemanticGraph newSG = addSsurgeon.iterate(sg).first;
    // the edge update to change the name of the edge to "dep" should fire
    SemanticGraph expected = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 dep> [antennae-4 dep> blue-3]]");
    assertEquals(expected, newSG);
    // the Ssurgeon we just created should not put a tag on the word
    // but it SHOULD put blue immediately before antennae
    blueVertex = newSG.getNodeByIndexSafe(3);
    assertNotNull(blueVertex);
    assertNull(blueVertex.tag());
    assertEquals("blue", blueVertex.value());
  }

  /**
   * Test that types which can't be converted from String
   * are detected when making an AddDep
   */
  @Test
  public void checkAnnotationConversionErrors() {
    Ssurgeon inst = Ssurgeon.inst();

    // this should exist, but a string will not produce it
    assertNotNull(AnnotationLookup.toCoreKey("SPAN"));

    // This will fail because IntPair cannot be converted from a String
    String add = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Remove all incoming edges for a node</notes>",
                             // have to bomb-proof the pattern
                             "    <semgrex>" + XMLUtils.escapeXML("{word:antennae}=antennae !> {word:blue}") + "</semgrex>",
                             "    <edit-list>addDep -gov antennae -reln dep -SPAN blue</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");

    try {
      List<SsurgeonPattern> patterns = inst.readFromString(add);
      throw new AssertionError("Expected a failure because IntPair is not readable from a String in CoreLabel");
    } catch (SsurgeonParseException e) {
      // yay
    }
    
    assertNotNull(AnnotationLookup.toCoreKey("headidx"));
    // This will also fail, this time because the property set is an Integer and the value is not legal
    add = String.join(newline,
                      "<ssurgeon-pattern-list>",
                      "  <ssurgeon-pattern>",
                      "    <uid>38</uid>",
                      "    <notes>Remove all incoming edges for a node</notes>",
                      // have to bomb-proof the pattern
                      "    <semgrex>" + XMLUtils.escapeXML("{word:antennae}=antennae !> {word:blue}") + "</semgrex>",
                      "    <edit-list>addDep -gov antennae -reln dep -headidx blue</edit-list>",
                      "  </ssurgeon-pattern>",
                      "</ssurgeon-pattern-list>");

    try {
      List<SsurgeonPattern> patterns = inst.readFromString(add);
      throw new AssertionError("Expected a failure in CoreLabel because the String given should not have been turned into an Integer");
    } catch (SsurgeonParseException e) {
      // yay
    }
  }


  /**
   * Check that the edit which puts a lemma on a node redoes the lemma on the nodes it targets
   */
  @Test
  public void readXMLLemmatize() {
    Ssurgeon inst = Ssurgeon.inst();

    // use "dep" as the dependency so as to be language-agnostic in this test
    String lemma = String.join(newline,
                               "<ssurgeon-pattern-list>",
                               "  <ssurgeon-pattern>",
                               "    <uid>38</uid>",
                               "    <notes>Edit a node</notes>",
                               "    <semgrex>" + XMLUtils.escapeXML("!{lemma:/.+/}=nolemma") + "</semgrex>",
                               "    <edit-list>lemmatize -node nolemma</edit-list>",
                               "  </ssurgeon-pattern>",
                               "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(lemma);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern lemmatizeSsurgeon = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[has/VBZ-2 nsubj> Jennifer/NNP-1 obj> [antennae/NNS-4 dep> green/JJ-3]]");
    for (IndexedWord word : sg.vertexSet()) {
      assertNull(word.lemma());
    }
    SemanticGraph newSG = lemmatizeSsurgeon.iterate(sg).first;
    String[] expectedLemmas = {"Jennifer", "have", "green", "antenna"};
    for (IndexedWord word : newSG.vertexSet()) {
      assertEquals(expectedLemmas[word.index() - 1], word.lemma());
    }

    // this version would bomb if lemmatize were not bomb-proof
    lemma = String.join(newline,
                        "<ssurgeon-pattern-list>",
                        "  <ssurgeon-pattern>",
                        "    <uid>38</uid>",
                        "    <notes>Edit a node</notes>",
                        "    <semgrex>" + XMLUtils.escapeXML("{}=nolemma") + "</semgrex>",
                        "    <edit-list>lemmatize -node nolemma</edit-list>",
                        "  </ssurgeon-pattern>",
                        "</ssurgeon-pattern-list>");
    patterns = inst.readFromString(lemma);
    assertEquals(patterns.size(), 1);
    lemmatizeSsurgeon = patterns.get(0);

    sg = SemanticGraph.valueOf("[has/VBZ-2 nsubj> Jennifer/NNP-1 obj> [antennae/NNS-4 dep> green/JJ-3]]");
    for (IndexedWord word : sg.vertexSet()) {
      assertNull(word.lemma());
    }
    newSG = lemmatizeSsurgeon.iterate(sg).first;
    for (IndexedWord word : newSG.vertexSet()) {
      assertEquals(expectedLemmas[word.index() - 1], word.lemma());
    }
  }

  /*
   * Check that a basic edit script works as expected
   */
  @Test
  public void readXMLEditNode() {
    Ssurgeon inst = Ssurgeon.inst();

    // use "dep" as the dependency so as to be language-agnostic in this test
    String add = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Edit a node</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{word:green}=blue") + "</semgrex>",
                             "    <edit-list>EditNode -node blue -word blue</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(add);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern editSsurgeon = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-4 dep> green-3]]");
    IndexedWord blueVertex = sg.getNodeByIndexSafe(3);
    assertEquals("green", blueVertex.value());
    SemanticGraph newSG = editSsurgeon.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-4 dep> blue-3]]");
    assertEquals(expected, newSG);
    // this ssurgeon will fix the color of the antennae
    blueVertex = newSG.getNodeByIndexSafe(3);
    assertNotNull(blueVertex);
    assertNull(blueVertex.tag());
    assertEquals("blue", blueVertex.value());
  }

  /**
   * Test that trying to build an EditNode with an illegal removed attribute fails
   */
  @Test
  public void readXMLEditNodeIllegalRemove() {
    // sanity check that the key we will use does not actually mean anything
    String missingKey = "zzzzzz";
    assertNull(AnnotationLookup.toCoreKey(missingKey));

    try {
      Ssurgeon inst = Ssurgeon.inst();
      String remove = String.join(newline,
                                  "<ssurgeon-pattern-list>",
                                  "  <ssurgeon-pattern>",
                                  "    <uid>38</uid>",
                                  "    <notes>Edit a node</notes>",
                                  "    <semgrex>" + XMLUtils.escapeXML("{word:blue}=blue") + "</semgrex>",
                                  "    <edit-list>EditNode -node blue -remove " + missingKey + "</edit-list>",
                                  "  </ssurgeon-pattern>",
                                  "</ssurgeon-pattern-list>");
      inst.readFromString(remove);
      throw new AssertionError("Expected a parse exception!");
    } catch(SsurgeonParseException e) {
      // yay
    }
  }

  /**
   * Check that we can add and remove lemmas using EditNode
   *
   * Specially testing that the remove functionality works
   */
  @Test
  public void readXMLEditNodeRemove() {
    Ssurgeon inst = Ssurgeon.inst();

    // use "dep" as the dependency so as to be language-agnostic in this test
    String add = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Edit a node</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{word:blue}=blue") + "</semgrex>",
                             "    <edit-list>EditNode -node blue -lemma blue</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(add);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern editSsurgeon = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-4 dep> blue-3]]");
    IndexedWord blueVertex = sg.getNodeByIndexSafe(3);
    assertEquals("blue", blueVertex.value());
    assertNull(blueVertex.lemma());

    SemanticGraph newSG = editSsurgeon.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-4 dep> blue-3]]");
    assertEquals(expected, newSG);
    // this ssurgeon will fix the color of the antennae
    blueVertex = newSG.getNodeByIndexSafe(3);
    assertNotNull(blueVertex);
    assertNull(blueVertex.tag());
    assertEquals("blue", blueVertex.value());
    assertEquals("blue", blueVertex.lemma());

    String remove = String.join(newline,
                                "<ssurgeon-pattern-list>",
                                "  <ssurgeon-pattern>",
                                "    <uid>38</uid>",
                                "    <notes>Edit a node</notes>",
                                "    <semgrex>" + XMLUtils.escapeXML("{word:blue}=blue") + "</semgrex>",
                                "    <edit-list>EditNode -node blue -remove lemma</edit-list>",
                                "  </ssurgeon-pattern>",
                                "</ssurgeon-pattern-list>");
    patterns = inst.readFromString(remove);
    assertEquals(patterns.size(), 1);
    editSsurgeon = patterns.get(0);

    SemanticGraph noLemmaSG = editSsurgeon.iterate(newSG).first;
    assertEquals(expected, noLemmaSG);
    blueVertex = noLemmaSG.getNodeByIndexSafe(3);
    assertNotNull(blueVertex);
    assertNull(blueVertex.tag());
    assertEquals("blue", blueVertex.value());
    assertNull(blueVertex.lemma());
  }

  /**
   * A couple tests of updating the morpho map on a word using EditNode
   * <br>
   * -updateMorphoFeatures should update w/o overwriting the whole map
   */
  @Test
  public void readXMLEditNodeUpdateMorpho() {
    Ssurgeon inst = Ssurgeon.inst();

    // This should add two morpho features to the word
    // (and should not crash even though the word previously had no features)
    String editPattern = String.join(newline,
                                     "<ssurgeon-pattern-list>",
                                     "  <ssurgeon-pattern>",
                                     "    <uid>38</uid>",
                                     "    <notes>Edit a node's morpho</notes>",
                                     "    <semgrex>" + XMLUtils.escapeXML("{word:/antennae/}=word") + "</semgrex>",
                                     "    <edit-list>EditNode -node word -updateMorphoFeatures a=b|c=d</edit-list>",
                                     "  </ssurgeon-pattern>",
                                     "</ssurgeon-pattern-list>");

    List<SsurgeonPattern> patterns = inst.readFromString(editPattern);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern editSsurgeon = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-4 dep> green-3]]");
    SemanticGraph newSG = editSsurgeon.iterate(sg).first;
    IndexedWord vertex = sg.getNodeByIndexSafe(4);
    assertEquals("antennae", vertex.value());
    assertEquals(vertex.get(CoreAnnotations.CoNLLUFeats.class).toString(), "a=b|c=d");

    // This will add a third feature and update one of the existing features,
    // leaving the other feature unchanged
    editPattern = String.join(newline,
                              "<ssurgeon-pattern-list>",
                              "  <ssurgeon-pattern>",
                              "    <uid>38</uid>",
                              "    <notes>Edit a node's morpho</notes>",
                              "    <semgrex>" + XMLUtils.escapeXML("{word:/antennae/}=word") + "</semgrex>",
                              "    <edit-list>EditNode -node word -updateMorphoFeatures a=zzz|e=f</edit-list>",
                              "  </ssurgeon-pattern>",
                              "</ssurgeon-pattern-list>");

    patterns = inst.readFromString(editPattern);
    assertEquals(patterns.size(), 1);
    editSsurgeon = patterns.get(0);

    newSG = editSsurgeon.iterate(sg).first;
    assertEquals(vertex.get(CoreAnnotations.CoNLLUFeats.class).toString(), "a=zzz|c=d|e=f");
  }

  /**
   * A couple tests of setting the morpho features on a word using EditNode
   */
  @Test
  public void readXMLEditNodeMorpho() {
    Ssurgeon inst = Ssurgeon.inst();

    String editPattern = String.join(newline,
                                     "<ssurgeon-pattern-list>",
                                     "  <ssurgeon-pattern>",
                                     "    <uid>38</uid>",
                                     "    <notes>Edit a node's morpho</notes>",
                                     "    <semgrex>" + XMLUtils.escapeXML("{word:/antennae/}=word") + "</semgrex>",
                                     "    <edit-list>EditNode -node word -morphofeatures foo=asdf</edit-list>",
                                     "  </ssurgeon-pattern>",
                                     "</ssurgeon-pattern-list>");

    List<SsurgeonPattern> patterns = inst.readFromString(editPattern);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern editSsurgeon = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-4 dep> green-3]]");
    IndexedWord vertex = sg.getNodeByIndexSafe(4);
    assertEquals(vertex.get(CoreAnnotations.CoNLLUFeats.class), null);
    assertEquals("antennae", vertex.value());
    SemanticGraph newSG = editSsurgeon.iterate(sg).first;
    IndexedWord newVertex = newSG.getNodeByIndexSafe(4);
    assertSame(vertex, newVertex);
    assertEquals(vertex.get(CoreAnnotations.CoNLLUFeats.class).toString(), "foo=asdf");

    editPattern = String.join(newline,
                              "<ssurgeon-pattern-list>",
                              "  <ssurgeon-pattern>",
                              "    <uid>38</uid>",
                              "    <notes>Edit a node's morpho</notes>",
                              "    <semgrex>" + XMLUtils.escapeXML("{word:/antennae/}=word") + "</semgrex>",
                              "    <edit-list>EditNode -node word -morphofeatures bar=zzzz</edit-list>",
                              "  </ssurgeon-pattern>",
                              "</ssurgeon-pattern-list>");

    patterns = inst.readFromString(editPattern);
    assertEquals(patterns.size(), 1);
    editSsurgeon = patterns.get(0);

    newSG = editSsurgeon.iterate(newSG).first;
    assertEquals(vertex.get(CoreAnnotations.CoNLLUFeats.class).toString(), "bar=zzzz");

    editPattern = String.join(newline,
                              "<ssurgeon-pattern-list>",
                              "  <ssurgeon-pattern>",
                              "    <uid>38</uid>",
                              "    <notes>Edit a node's morpho</notes>",
                              "    <semgrex>" + XMLUtils.escapeXML("{word:/antennae/}=word") + "</semgrex>",
                              "    <edit-list>EditNode -node word -morphofeatures foo=asdf|bar=zzzz</edit-list>",
                              "  </ssurgeon-pattern>",
                              "</ssurgeon-pattern-list>");

    patterns = inst.readFromString(editPattern);
    assertEquals(patterns.size(), 1);
    editSsurgeon = patterns.get(0);

    newSG = editSsurgeon.iterate(newSG).first;
    // eager test!  checking that the features are sorted
    assertEquals(vertex.get(CoreAnnotations.CoNLLUFeats.class).toString(), "bar=zzzz|foo=asdf");
    assertEquals(vertex.get(CoreAnnotations.CoNLLUFeats.class).size(), 2);
  }

  /**
   * Put MWT annotations on a couple nodes using EditNode
   */
  @Test
  public void readXMLEditNodeMWT() {
    Ssurgeon inst = Ssurgeon.inst();

    // use "dep" as the dependency so as to be language-agnostic in this test
    String add = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Edit a node's MWT</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{word:/[iI]t/}=it . {word:/'s/}=s") + "</semgrex>",
                             "    <edit-list>EditNode -node it -is_mwt true  -is_first_mwt true  -mwt_text it's</edit-list>",
                             "    <edit-list>EditNode -node s  -is_mwt true  -is_first_mwt false -mwt_text it's</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(add);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern editSsurgeon = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[yours-4 nsubj> it-1 cop> 's-2 advmod> yours-3 punct> !-5]");

    // check the original values
    IndexedWord itVertex = sg.getNodeByIndexSafe(1);
    assertEquals(null, itVertex.get(CoreAnnotations.IsMultiWordTokenAnnotation.class));
    assertEquals(null, itVertex.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class));
    assertEquals(null, itVertex.get(CoreAnnotations.MWTTokenTextAnnotation.class));
    IndexedWord sVertex = sg.getNodeByIndexSafe(2);
    assertEquals(null, sVertex.get(CoreAnnotations.IsMultiWordTokenAnnotation.class));
    assertEquals(null, sVertex.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class));
    assertEquals(null, sVertex.get(CoreAnnotations.MWTTokenTextAnnotation.class));

    SemanticGraph newSG = editSsurgeon.iterate(sg).first;
    // the high level graph structure won't change
    SemanticGraph expected = SemanticGraph.valueOf("[yours-4 nsubj> it-1 cop> 's-2 advmod> yours-3 punct> !-5]");
    assertEquals(expected, newSG);

    // check the updates
    itVertex = newSG.getNodeByIndexSafe(1);
    assertTrue(itVertex.get(CoreAnnotations.IsMultiWordTokenAnnotation.class));
    assertTrue(itVertex.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class));
    assertEquals("it's", itVertex.get(CoreAnnotations.MWTTokenTextAnnotation.class));
    sVertex = newSG.getNodeByIndexSafe(2);
    assertTrue(sVertex.get(CoreAnnotations.IsMultiWordTokenAnnotation.class));
    assertFalse(sVertex.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class));
    assertEquals("it's", sVertex.get(CoreAnnotations.MWTTokenTextAnnotation.class));
  }


  /**
   * Put MWT annotations on a couple nodes using CombineMWT
   */
  @Test
  public void readXMLCombineMWT() {
    Ssurgeon inst = Ssurgeon.inst();

    // combine using the CombineMWT operation, using the default concatenation for the MWT text
    String mwt = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Edit a node's MWT</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{word:/[iI]t/}=it . {word:/'s/}=s") + "</semgrex>",
                             "    <edit-list>CombineMWT -node it -node s</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    List<SsurgeonPattern> patterns = inst.readFromString(mwt);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern editSsurgeon = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[yours-4 nsubj> it-1 cop> 's-2 advmod> yours-3 punct> !-5]");

    // check the original values
    IndexedWord itVertex = sg.getNodeByIndexSafe(1);
    assertEquals(null, itVertex.get(CoreAnnotations.IsMultiWordTokenAnnotation.class));
    assertEquals(null, itVertex.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class));
    assertEquals(null, itVertex.get(CoreAnnotations.MWTTokenTextAnnotation.class));
    IndexedWord sVertex = sg.getNodeByIndexSafe(2);
    assertEquals(null, sVertex.get(CoreAnnotations.IsMultiWordTokenAnnotation.class));
    assertEquals(null, sVertex.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class));
    assertEquals(null, sVertex.get(CoreAnnotations.MWTTokenTextAnnotation.class));

    SemanticGraph newSG = editSsurgeon.iterate(sg).first;
    // the high level graph structure won't change
    SemanticGraph expected = SemanticGraph.valueOf("[yours-4 nsubj> it-1 cop> 's-2 advmod> yours-3 punct> !-5]");
    assertEquals(expected, newSG);

    // check the updates
    itVertex = newSG.getNodeByIndexSafe(1);
    assertTrue(itVertex.get(CoreAnnotations.IsMultiWordTokenAnnotation.class));
    assertTrue(itVertex.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class));
    assertEquals("it's", itVertex.get(CoreAnnotations.MWTTokenTextAnnotation.class));
    sVertex = newSG.getNodeByIndexSafe(2);
    assertTrue(sVertex.get(CoreAnnotations.IsMultiWordTokenAnnotation.class));
    assertFalse(sVertex.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class));
    assertEquals("it's", sVertex.get(CoreAnnotations.MWTTokenTextAnnotation.class));
    for (int i = 3; i <= 5; ++i) {
      IndexedWord vertex = newSG.getNodeByIndexSafe(i);
      assertNull(vertex.get(CoreAnnotations.IsMultiWordTokenAnnotation.class));
      assertNull(vertex.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class));
      assertNull(vertex.get(CoreAnnotations.MWTTokenTextAnnotation.class));
    }


    // This time, we use a custom -word
    mwt = String.join(newline,
                      "<ssurgeon-pattern-list>",
                      "  <ssurgeon-pattern>",
                      "    <uid>38</uid>",
                      "    <notes>Edit a node's MWT</notes>",
                      "    <semgrex>" + XMLUtils.escapeXML("{word:/[iI]t/}=it . {word:/'s/}=s") + "</semgrex>",
                      "    <edit-list>CombineMWT -node it -node s -word foo</edit-list>",
                      "  </ssurgeon-pattern>",
                      "</ssurgeon-pattern-list>");
    patterns = inst.readFromString(mwt);
    assertEquals(patterns.size(), 1);
    editSsurgeon = patterns.get(0);

    sg = SemanticGraph.valueOf("[yours-4 nsubj> it-1 cop> 's-2 advmod> yours-3 punct> !-5]");

    // check the original values
    itVertex = sg.getNodeByIndexSafe(1);
    assertEquals(null, itVertex.get(CoreAnnotations.IsMultiWordTokenAnnotation.class));
    assertEquals(null, itVertex.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class));
    assertEquals(null, itVertex.get(CoreAnnotations.MWTTokenTextAnnotation.class));
    sVertex = sg.getNodeByIndexSafe(2);
    assertEquals(null, sVertex.get(CoreAnnotations.IsMultiWordTokenAnnotation.class));
    assertEquals(null, sVertex.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class));
    assertEquals(null, sVertex.get(CoreAnnotations.MWTTokenTextAnnotation.class));

    newSG = editSsurgeon.iterate(sg).first;
    // the high level graph structure won't change
    expected = SemanticGraph.valueOf("[yours-4 nsubj> it-1 cop> 's-2 advmod> yours-3 punct> !-5]");
    assertEquals(expected, newSG);

    // check the updates
    itVertex = newSG.getNodeByIndexSafe(1);
    assertTrue(itVertex.get(CoreAnnotations.IsMultiWordTokenAnnotation.class));
    assertTrue(itVertex.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class));
    assertEquals("foo", itVertex.get(CoreAnnotations.MWTTokenTextAnnotation.class));
    sVertex = newSG.getNodeByIndexSafe(2);
    assertTrue(sVertex.get(CoreAnnotations.IsMultiWordTokenAnnotation.class));
    assertFalse(sVertex.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class));
    assertEquals("foo", sVertex.get(CoreAnnotations.MWTTokenTextAnnotation.class));
    for (int i = 3; i <= 5; ++i) {
      IndexedWord vertex = newSG.getNodeByIndexSafe(i);
      assertNull(vertex.get(CoreAnnotations.IsMultiWordTokenAnnotation.class));
      assertNull(vertex.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class));
      assertNull(vertex.get(CoreAnnotations.MWTTokenTextAnnotation.class));
    }


    // This time, we put tags on the words... check that a bug in the
    // initial implementation is fixed
    mwt = String.join(newline,
                      "<ssurgeon-pattern-list>",
                      "  <ssurgeon-pattern>",
                      "    <uid>38</uid>",
                      "    <notes>Edit a node's MWT</notes>",
                      "    <semgrex>" + XMLUtils.escapeXML("{word:/[iI]t/}=it . {word:/'s/}=s") + "</semgrex>",
                      "    <edit-list>CombineMWT -node it -node s</edit-list>",
                      "  </ssurgeon-pattern>",
                      "</ssurgeon-pattern-list>");
    patterns = inst.readFromString(mwt);
    assertEquals(patterns.size(), 1);
    editSsurgeon = patterns.get(0);

    sg = SemanticGraph.valueOf("[yours-4 nsubj> it/PRP-1 cop> 's/VBZ-2 advmod> yours-3 punct> !-5]");

    // check the original values
    itVertex = sg.getNodeByIndexSafe(1);
    assertEquals(null, itVertex.get(CoreAnnotations.IsMultiWordTokenAnnotation.class));
    assertEquals(null, itVertex.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class));
    assertEquals(null, itVertex.get(CoreAnnotations.MWTTokenTextAnnotation.class));
    sVertex = sg.getNodeByIndexSafe(2);
    assertEquals(null, sVertex.get(CoreAnnotations.IsMultiWordTokenAnnotation.class));
    assertEquals(null, sVertex.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class));
    assertEquals(null, sVertex.get(CoreAnnotations.MWTTokenTextAnnotation.class));

    newSG = editSsurgeon.iterate(sg).first;
    // the high level graph structure won't change
    expected = SemanticGraph.valueOf("[yours-4 nsubj> it-1 cop> 's-2 advmod> yours-3 punct> !-5]");
    assertEquals(expected, newSG);

    // check the updates
    itVertex = newSG.getNodeByIndexSafe(1);
    assertTrue(itVertex.get(CoreAnnotations.IsMultiWordTokenAnnotation.class));
    assertTrue(itVertex.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class));
    assertEquals("it's", itVertex.get(CoreAnnotations.MWTTokenTextAnnotation.class));
    sVertex = newSG.getNodeByIndexSafe(2);
    assertTrue(sVertex.get(CoreAnnotations.IsMultiWordTokenAnnotation.class));
    assertFalse(sVertex.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class));
    assertEquals("it's", sVertex.get(CoreAnnotations.MWTTokenTextAnnotation.class));
    for (int i = 3; i <= 5; ++i) {
      IndexedWord vertex = newSG.getNodeByIndexSafe(i);
      assertNull(vertex.get(CoreAnnotations.IsMultiWordTokenAnnotation.class));
      assertNull(vertex.get(CoreAnnotations.IsFirstWordOfMWTAnnotation.class));
      assertNull(vertex.get(CoreAnnotations.MWTTokenTextAnnotation.class));
    }
  }


  /**
   * Test that we don't allow changing a word index, for example, in EditNode or AddDep
   */
  @Test
  public void forbidIllegalAttributes() {
    Ssurgeon inst = Ssurgeon.inst();

    // use "dep" as the dependency so as to be language-agnostic in this test
    String add = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Edit a node</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{word:green}=blue") + "</semgrex>",
                             "    <edit-list>EditNode -node blue -idx 5</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    try {
      List<SsurgeonPattern> patterns = inst.readFromString(add);
      throw new AssertionError("Expected a parse exception!");
    } catch(SsurgeonParseException e) {
      // yay
    }
    add = String.join(newline,
                      "<ssurgeon-pattern-list>",
                      "  <ssurgeon-pattern>",
                      "    <uid>38</uid>",
                      "    <notes>Edit a node</notes>",
                      "    <semgrex>" + XMLUtils.escapeXML("{word:green}=blue") + "</semgrex>",
                      "    <edit-list>addDep -gov antennae -reln dep -headidx blue -idx 5</edit-list>",
                      "  </ssurgeon-pattern>",
                      "</ssurgeon-pattern-list>");
    try {
      List<SsurgeonPattern> patterns = inst.readFromString(add);
      throw new AssertionError("Expected a parse exception!");
    } catch(SsurgeonParseException e) {
      // yay
    }
  }

  /**
   * Test a two step process to reattach an edge elsewhere
   *<br>
   * Uses a real example from UD_English-Pronouns
   */
  @Test
  public void readXMLTwoStepReattach() {
    String doc = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>This tests the two step process of reattaching an edge</notes>",
                             "    <language>UniversalEnglish</language>",
                             "    <semgrex>" + XMLUtils.escapeXML("{word:/[.]/}=punct <punct=bad {}=parent << {$}=root : {}=parent << {}=root") + "</semgrex>",
                             "    <edit-list>removeNamedEdge -edge bad</edit-list>",
                             "    <edit-list>addEdge -gov root -dep punct -reln punct</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    Ssurgeon inst = Ssurgeon.inst();
    List<SsurgeonPattern> patterns = inst.readFromString(doc);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern pattern = patterns.get(0);

    // check a simple case of relabeling
    SemanticGraph sg = SemanticGraph.valueOf("[easy-3 nsubj> Hers-1 cop> is-2 csubj> [clean-5 mark> to-4 punct> .-6]]");
    SemanticGraph expected = SemanticGraph.valueOf("[easy-3 nsubj> Hers-1 cop> is-2 punct> .-6 csubj> [clean-5 mark> to-4]]");
    SemanticGraph newSg = pattern.iterate(sg).first;
    assertEquals(newSg, expected);
  }

  /**
   * Test reattachNamedEdge, which is a one step version of reattaching where an edge goes
   *<br>
   * Uses a real example from UD_English-Pronouns
   */
  @Test
  public void readXMLOneStepReattach() {
    String doc = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>This tests the two step process of reattaching an edge</notes>",
                             "    <language>UniversalEnglish</language>",
                             "    <semgrex>" + XMLUtils.escapeXML("{word:/[.]/}=punct <punct=bad {}=parent << {$}=root : {}=parent << {}=root") + "</semgrex>",
                             "    <edit-list>reattachNamedEdge -edge bad -gov root</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    Ssurgeon inst = Ssurgeon.inst();
    List<SsurgeonPattern> patterns = inst.readFromString(doc);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern pattern = patterns.get(0);

    // check a simple case of relabeling
    SemanticGraph sg = SemanticGraph.valueOf("[easy-3 nsubj> Hers-1 cop> is-2 csubj> [clean-5 mark> to-4 punct> .-6]]");
    SemanticGraph expected = SemanticGraph.valueOf("[easy-3 nsubj> Hers-1 cop> is-2 punct> .-6 csubj> [clean-5 mark> to-4]]");
    SemanticGraph newSg = pattern.iterate(sg).first;
    assertEquals(newSg, expected);

    // this tests -gov and -dep both set
    doc = String.join(newline,
                      "<ssurgeon-pattern-list>",
                      "  <ssurgeon-pattern>",
                      "    <uid>38</uid>",
                      "    <notes>This tests the two step process of reattaching an edge</notes>",
                      "    <language>UniversalEnglish</language>",
                      "    <semgrex>" + XMLUtils.escapeXML("{word:/[.]/}=punct <punct=bad {}=parent << {$}=root : {}=parent << {}=root") + "</semgrex>",
                      "    <edit-list>reattachNamedEdge -edge bad -gov root -dep punct</edit-list>",
                      "  </ssurgeon-pattern>",
                      "</ssurgeon-pattern-list>");
    inst = Ssurgeon.inst();
    patterns = inst.readFromString(doc);
    assertEquals(patterns.size(), 1);
    pattern = patterns.get(0);

    // check a simple case of relabeling, this time with the (unnecessary) -dep specifier as well
    sg = SemanticGraph.valueOf("[easy-3 nsubj> Hers-1 cop> is-2 csubj> [clean-5 mark> to-4 punct> .-6]]");
    expected = SemanticGraph.valueOf("[easy-3 nsubj> Hers-1 cop> is-2 punct> .-6 csubj> [clean-5 mark> to-4]]");
    newSg = pattern.iterate(sg).first;
    assertEquals(newSg, expected);

    // this tests -dep set by itself (although the operation itself is nonsense)
    doc = String.join(newline,
                      "<ssurgeon-pattern-list>",
                      "  <ssurgeon-pattern>",
                      "    <uid>38</uid>",
                      "    <notes>This tests the two step process of reattaching an edge</notes>",
                      "    <language>UniversalEnglish</language>",
                      "    <semgrex>" + XMLUtils.escapeXML("{$}=root >csubj=foo ({word:clean}=n1 >mark=bar {}=n2)") + "</semgrex>",
                      "    <edit-list>reattachNamedEdge -edge foo -dep n2</edit-list>",
                      "    <edit-list>reattachNamedEdge -edge bar -gov n2 -dep n1</edit-list>",
                      "  </ssurgeon-pattern>",
                      "</ssurgeon-pattern-list>");
    inst = Ssurgeon.inst();
    patterns = inst.readFromString(doc);
    assertEquals(patterns.size(), 1);
    pattern = patterns.get(0);

    // do some random rearranging to force a test of reattachNamedEdge with -dep set
    sg = SemanticGraph.valueOf("[easy-3 nsubj> Hers-1 cop> is-2 csubj> [clean-5 mark> to-4 punct> .-6]]");
    expected = SemanticGraph.valueOf("[easy-3 nsubj> Hers-1 cop> is-2 csubj> [to-4 mark> [clean-5 punct> .-6]]]");
    newSg = pattern.iterate(sg).first;
    assertEquals(newSg, expected);
  }


  /**
   * Test deleteLeaf, which removes an unwanted leaf and its edges, then renumbers everything
   *<br>
   * Uses a real example from UD_Portuguese-GSD
   */
  @Test
  public void readXMLDeleteLeaf() {
    String doc = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Test deleting a leaf (only if it's a leaf)</notes>",
                             "    <language>UniversalEnglish</language>",
                             // the real life example used POS tags to make sure "verb" and "clitic" are the right pieces
                             "    <semgrex>" + XMLUtils.escapeXML("{}=verb . ({word:/-/}=dash . {word:se}=clitic)") + "</semgrex>",
                             "    <edit-list>combineMWT -node verb -node dash -node clitic</edit-list>",
                             "    <edit-list>deleteLeaf -node dash</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    Ssurgeon inst = Ssurgeon.inst();
    List<SsurgeonPattern> patterns = inst.readFromString(doc);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern pattern = patterns.get(0);

    // the dash should be removed and all words with an index after the dash should have that index decremented
    SemanticGraph sg =       SemanticGraph.valueOf("[nobre-6 nmod> [decreto-9 case> com-7 det> o-8] cop> fez-3 punct> --4 expl:pv> [se-5 advmod> [Assim punct> ,-2]]]");
    SemanticGraph expected = SemanticGraph.valueOf("[nobre-5 nmod> [decreto-8 case> com-6 det> o-7] cop> fez-3 expl:pv> [se-4 advmod> [Assim punct> ,-2]]]");
    SemanticGraph newSg = pattern.iterate(sg).first;
    assertEquals(newSg, expected);

    // here, the dash isn't a leaf any more, so it shouldn't be deleted
    sg =       SemanticGraph.valueOf("[nobre-6 nmod> [decreto-9 case> com-7 det> o-8] cop> fez-3 punct> [--4 expl:pv> [se-5 advmod> [Assim punct> ,-2]]]]");
    expected = SemanticGraph.valueOf("[nobre-6 nmod> [decreto-9 case> com-7 det> o-8] cop> fez-3 punct> [--4 expl:pv> [se-5 advmod> [Assim punct> ,-2]]]]");
    newSg = pattern.iterate(sg).first;
    assertEquals(newSg, expected);
  }

  @Test
  public void readXMLSetPhraseHead() {
    String doc = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Test resetting a phrase's internal and external links</notes>",
                             "    <language>UniversalEnglish</language>",
                             "    <semgrex>" + XMLUtils.escapeXML("{word:John}=n1 . {word:Bauer}=n2") + "</semgrex>",
                             "    <edit-list>SetPhraseHead -node n1 -node n2 -headIndex 0 -reln flat</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    Ssurgeon inst = Ssurgeon.inst();
    List<SsurgeonPattern> patterns = inst.readFromString(doc);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern pattern = patterns.get(0);

    // test where the new phrase is not the root
    SemanticGraph sg = SemanticGraph.valueOf("[works-4 obl> [Stanford-6 case> at-5] nsubj> [Bauer-3 flat> John-2 nmod> Earl-1]]");
    SemanticGraph newSg = pattern.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[works-4 obl> [Stanford-6 case> at-5] nsubj> [John-2 flat> Bauer-3 nmod> Earl-1]]");
    assertEquals(newSg, expected);

    // test where the new phrase IS the root
    sg = SemanticGraph.valueOf("[Bauer-5 flat> John-4 cop> is-3 nsubj> [programmer-2 det> The-1]]");
    newSg = pattern.iterate(sg).first;
    expected = SemanticGraph.valueOf("[John-4 flat> Bauer-5 cop> is-3 nsubj> [programmer-2 det> The-1]]");
    assertEquals(newSg, expected);
  }

  /**
   * Test splitWord, which should split a word into pieces based on regex matches, with the head at position 0
   */
  @Test
  public void readXMLSplitTwoWords() {
    String doc = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Test splitting a word into two pieces with the head at the start</notes>",
                             "    <language>UniversalEnglish</language>",
                             "    <semgrex>" + XMLUtils.escapeXML("{word:/foobar/}=split") + "</semgrex>",
                             "    <edit-list>splitWord -node split -regex ^(foo)bar$ -regex ^foo(bar)$ -reln dep -headIndex 0</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    Ssurgeon inst = Ssurgeon.inst();
    List<SsurgeonPattern> patterns = inst.readFromString(doc);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern pattern = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[example-3 det> the-1 amod> foobar-2]");
    SemanticGraph newSg = pattern.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[example-4 det> the-1 amod> [foo-2 dep> bar-3]]");
    assertEquals(newSg, expected);
  }

  /**
   * Test a splitWord which will split words based on exact pieces
   */
  @Test
  public void readXMLSplitTwoWordsExact() {
    String doc = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Test splitting a word into two pieces with the head at the start</notes>",
                             "    <language>UniversalEnglish</language>",
                             "    <semgrex>" + XMLUtils.escapeXML("{word:/foobar/}=split") + "</semgrex>",
                             "    <edit-list>splitWord -node split -exact foo -exact bar -reln dep -headIndex 0</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    Ssurgeon inst = Ssurgeon.inst();
    List<SsurgeonPattern> patterns = inst.readFromString(doc);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern pattern = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[example-3 det> the-1 amod> foobar-2]");
    SemanticGraph newSg = pattern.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[example-4 det> the-1 amod> [foo-2 dep> bar-3]]");
    assertEquals(newSg, expected);
  }

  /**
   * Test splitWord, which should split a word into pieces based on regex matches, with the head at position 1
   */
  @Test
  public void readXMLSplitTwoWordsAfter() {
    String doc = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Test splitting a word into two pieces with the head at the start</notes>",
                             "    <language>UniversalEnglish</language>",
                             "    <semgrex>" + XMLUtils.escapeXML("{word:/foobar/}=split") + "</semgrex>",
                             "    <edit-list>splitWord -node split -regex ^(foo)bar$ -regex ^foo(bar)$ -reln dep -headIndex 1</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    Ssurgeon inst = Ssurgeon.inst();
    List<SsurgeonPattern> patterns = inst.readFromString(doc);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern pattern = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[example-3 det> the-1 amod> foobar-2]");
    SemanticGraph newSg = pattern.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[example-4 det> the-1 amod> [bar-3 dep> foo-2]]");
    assertEquals(newSg, expected);
  }

  /**
   * Test splitWord, which should split a word into pieces based on regex matches, with the head at position 1
   */
  @Test
  public void readXMLSplitTwoWordsNamed() {
    String doc = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Test splitting a word into two pieces with the head at the start</notes>",
                             "    <language>UniversalEnglish</language>",
                             "    <semgrex>" + XMLUtils.escapeXML("{word:/foobar/}=split") + "</semgrex>",
                             "    <edit-list>splitWord -node split -regex ^(foo)bar$ -regex ^foo(bar)$ -reln dep -headIndex 1 -name 0=asdf</edit-list>",
                             "    <edit-list>editNode -node asdf -pos ADJ</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    Ssurgeon inst = Ssurgeon.inst();
    List<SsurgeonPattern> patterns = inst.readFromString(doc);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern pattern = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[example-3 det> the-1 amod> foobar-2]");
    SemanticGraph newSg = pattern.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[example-4 det> the-1 amod> [bar-3 dep> foo-2]]");
    assertEquals(newSg, expected);

    boolean found = false;
    for (IndexedWord word : newSg.vertexSet()) {
      if (word.index() == 2) {
        assertEquals("ADJ", word.get(CoreAnnotations.PartOfSpeechAnnotation.class));
        found = true;
      } else {
        assertEquals(null, word.get(CoreAnnotations.PartOfSpeechAnnotation.class));
      }
    }
    assertTrue(found);
  }

  /**
   * Test splitWord, which should split a word into pieces based on regex matches, with the head at position 1
   */
  @Test
  public void readXMLReindexGraph() {
    String doc = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Reindex all nodes to have a base index of 1</notes>",
                             "    <language>UniversalEnglish</language>",
                             "    <semgrex>" + XMLUtils.escapeXML("{$}") + "</semgrex>",
                             "    <edit-list>reindexGraph</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    Ssurgeon inst = Ssurgeon.inst();
    List<SsurgeonPattern> patterns = inst.readFromString(doc);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern pattern = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[example-5 det> the-2 amod> foobar-4]");
    SemanticGraph newSg = pattern.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[example-3 det> the-1 amod> foobar-2]");

    Map<String, Integer> expectedIndices = new HashMap<String, Integer>() {{
        put("example", 3);
        put("the", 1);
        put("foobar", 2);
      }};
    // iterate & assert the indices separately so that if something goes wrong,
    // it is clear what the error is
    // the indices are supposed to be remapped to be 1, 2, 3
    for (IndexedWord vertex : newSg.vertexSet()) {
      assertTrue(expectedIndices.containsKey(vertex.word()));
      int index = vertex.index();
      int expectedIndex = expectedIndices.get(vertex.word());
      assertEquals(index, expectedIndex);
    }

    assertEquals(newSg, expected);
  }

  /**
   * Test splitWord, which should split a word into pieces based on regex matches, with three pieces
   */
  @Test
  public void readXMLSplitThreeWords() {
    String doc = String.join(newline,
                             "<ssurgeon-pattern-list>",
                             "  <ssurgeon-pattern>",
                             "    <uid>38</uid>",
                             "    <notes>Test splitting a word into two pieces with the head at the start</notes>",
                             "    <language>UniversalEnglish</language>",
                             "    <semgrex>" + XMLUtils.escapeXML("{word:/foobarbaz/}=split") + "</semgrex>",
                             "    <edit-list>splitWord -node split -regex ^(foo)barbaz$ -regex ^foo(bar)baz$ -regex ^foobar(baz)$ -reln dep -headIndex 1</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    Ssurgeon inst = Ssurgeon.inst();
    List<SsurgeonPattern> patterns = inst.readFromString(doc);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern pattern = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[example-3 det> the-1 amod> foobarbaz-2]");
    SemanticGraph newSg = pattern.iterate(sg).first;
    SemanticGraph expected = SemanticGraph.valueOf("[example-5 det> the-1 amod> [bar-3 dep> foo-2 dep>baz-4]]");
    assertEquals(newSg, expected);
  }

  /**
   * Simple test of an Ssurgeon edit script.  This instances a simple semantic graph,
   * a semgrex pattern, and then the resulting actions over the named nodes in the
   * semgrex match.
   */
  @Test
  public void simpleTest() {
    SemanticGraph sg = SemanticGraph.valueOf("[mixed/VBN nsubj>[Joe/NNP appos>[bartender/NN det>the/DT]]  obj>[drink/NN det>a/DT]]");
    SemgrexPattern semgrexPattern = SemgrexPattern.compile("{}=a1 >appos=e1 {}=a2 <nsubj=e2 {}=a3");
    SsurgeonPattern pattern = new SsurgeonPattern(semgrexPattern);

    System.out.println("Start = "+sg.toCompactString());

    // Find and snip the appos and root to nsubj links
    SsurgeonEdit apposSnip = new RemoveNamedEdge("e1");
    pattern.addEdit(apposSnip);

    SsurgeonEdit nsubjSnip = new RemoveNamedEdge("e2");
    pattern.addEdit(nsubjSnip);

    // Attach Joe to be the nsubj of bartender
    SsurgeonEdit reattachSubj = new AddEdge("a2", "a1", EnglishGrammaticalRelations.NOMINAL_SUBJECT);
    pattern.addEdit(reattachSubj);

    // Attach copula
    Map<String, String> attributes = new HashMap<>();
    attributes.put("word", "is");
    attributes.put("lemma", "is");
    attributes.put("current", "is");
    attributes.put("pos", "VBN");
    SsurgeonEdit addCopula = new AddDep("a2", EnglishGrammaticalRelations.COPULA, attributes, null, 0.0);
    pattern.addEdit(addCopula);

    // Destroy subgraph
    SsurgeonEdit destroySubgraph = new DeleteGraphFromNode("a3");
    pattern.addEdit(destroySubgraph);

    // Process and output modified
    Collection<SemanticGraph> newSgs = pattern.execute(sg);
    for (SemanticGraph newSg : newSgs)
      System.out.println("Modified = "+newSg.toCompactString());
    String firstGraphString = newSgs.iterator().next().toCompactString().trim();
    assertEquals("[bartender nsubj>Joe det>the cop>is]", firstGraphString);
  }

  /**
   * Test that a couple fields used in Ssurgeon don't conflict with annotation keys in AnnotationLookup
   */
  @Test
  public void annotationNamesTest() {
    assertNull(AnnotationLookup.toCoreKey(Ssurgeon.REMOVE));
    assertNull(AnnotationLookup.toCoreKey(Ssurgeon.UPDATE_MORPHO_FEATURES));
    assertNull(AnnotationLookup.toCoreKey(Ssurgeon.UPDATE_MORPHO_FEATURES_LOWER));
  }
}
