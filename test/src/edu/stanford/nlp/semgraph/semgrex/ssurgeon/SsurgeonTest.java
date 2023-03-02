
package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.List;

import org.junit.Test;

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
    SemanticGraph newSg = pattern.iterate(sg);
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
                             "    <notes>This is a simple test of addEdge</notes>",
                             "    <semgrex>" + XMLUtils.escapeXML("{}=a1 > {}=a2") + "</semgrex>",
                             "    <edit-list>removeEdge -gov a1 -dep a2 -reln dep</edit-list>",
                             "  </ssurgeon-pattern>",
                             "</ssurgeon-pattern-list>");
    Ssurgeon inst = Ssurgeon.inst();
    List<SsurgeonPattern> patterns = inst.readFromString(doc);
    assertEquals(patterns.size(), 1);
    SsurgeonPattern pattern = patterns.get(0);

    SemanticGraph sg = SemanticGraph.valueOf("[A-0 obj> B-1 obj> C-2 nsubj> [D-3 obj> E-4]]");
    SemanticGraph newSg = pattern.iterate(sg);
    SemanticGraph expected = SemanticGraph.valueOf("[A-0 obj> B-1 obj> C-2 nsubj> [D-3 obj> E-4]]");
    assertEquals(newSg, sg);

    sg = SemanticGraph.valueOf("[A-0 obj> B-1 dep> B-1 obj> C-2 nsubj> [D-3 obj> E-4]]");
    newSg = pattern.iterate(sg);
    assertEquals(newSg, expected);

    sg = SemanticGraph.valueOf("[A-0 obj> B-1 dep> B-1 obj> C-2 dep> C-2 nsubj> [D-3 obj> E-4 dep> E-4]]");
    newSg = pattern.iterate(sg);
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
    SemanticGraph newSg = pattern.iterate(sg);
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
    SemanticGraph newSg = pattern.iterate(sg);
    SemanticGraph expected = SemanticGraph.valueOf("[A-0 obj> B-1 obj> C-2 nsubj> [D-3 obj> E-4]]");
    assertEquals(newSg, sg);

    sg = SemanticGraph.valueOf("[A-0 obj> B-1 dep> B-1 obj> C-2 nsubj> [D-3 obj> E-4]]");
    newSg = pattern.iterate(sg);
    assertEquals(newSg, expected);

    sg = SemanticGraph.valueOf("[A-0 obj> B-1 dep> B-1 obj> C-2 dep> C-2 nsubj> [D-3 obj> E-4 dep> E-4]]");
    newSg = pattern.iterate(sg);
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
    SemanticGraph newSg = pattern.iterate(sg);
    assertEquals(newSg, expected);

    // check iteration over multiple edges
    sg = SemanticGraph.valueOf("[A-0 obj> [B-1 obj> C-2]]");
    expected = SemanticGraph.valueOf("[A-0 dep> [B-1 dep> C-2]]");
    newSg = pattern.iterate(sg);
    assertEquals(newSg, expected);

    // check that relabeling doesn't change a non-matching edge
    // (how would it?)
    sg = SemanticGraph.valueOf("[A-0 iobj> B-1]");
    expected = SemanticGraph.valueOf("[A-0 iobj> B-1]");
    newSg = pattern.iterate(sg);
    assertEquals(newSg, expected);

    // check that you don't get double edges if an update
    // makes two edges into the same edge
    sg = SemanticGraph.valueOf("[A-0 obj> B-1 dep> B-1]");
    expected = SemanticGraph.valueOf("[A-0 dep> B-1]");
    newSg = pattern.iterate(sg);
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
    SemanticGraph newSg = pattern.iterate(sg);
    assertEquals(newSg, expected);

    // in this case, the dep should acquire the name of the obj
    // in the SemgrexMatcher.  the subsequent operation will pick up
    // that edge (with the original edge being deleted) and the
    // result will be one edge with the name "gov"
    // if the matcher did not update the named edge as expected,
    // the second operation would not fire
    sg = SemanticGraph.valueOf("[A-0 obj> B-1 dep> B-1]");
    expected = SemanticGraph.valueOf("[A-0 gov> B-1]");
    newSg = pattern.iterate(sg);
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
    SemanticGraph cutSG = ssurgeonCut.iterate(sg);
    assertEquals(cutSG.vertexSet().size(), 2);
    SemanticGraph pruneSG = ssurgeonPrune.iterate(cutSG);
    SemanticGraph expected = SemanticGraph.valueOf("[A]");
    assertEquals(pruneSG, expected);

    // Test a chain cut at the start
    sg = SemanticGraph.valueOf("[A dep> [B obj> C]]");
    cutSG = ssurgeonCut.iterate(sg);
    assertEquals(cutSG.vertexSet().size(), 3);
    pruneSG = ssurgeonPrune.iterate(cutSG);
    assertEquals(pruneSG, expected);

    // Test the chain cut at the bottom
    sg = SemanticGraph.valueOf("[A obj> [B dep> C]]");
    cutSG = ssurgeonCut.iterate(sg);
    assertEquals(cutSG.vertexSet().size(), 3);
    pruneSG = ssurgeonPrune.iterate(cutSG);
    assertEquals(pruneSG, SemanticGraph.valueOf("[A obj> B]"));

    // Test a chain cut at the start
    // Only the root will be left at the end
    sg = SemanticGraph.valueOf("[A dep> B dep> C]");
    cutSG = ssurgeonCut.iterate(sg);
    assertEquals(cutSG.vertexSet().size(), 3);
    pruneSG = ssurgeonPrune.iterate(cutSG);
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
    SemanticGraph cutSG = ssurgeon.iterate(sg);
    SemanticGraph expected = SemanticGraph.valueOf("[A]");
    assertEquals(1, cutSG.vertexSet().size());
    assertEquals(expected, cutSG);

    // Test a chain cut at the start
    sg = SemanticGraph.valueOf("[A dep> [B obj> C]]");
    cutSG = ssurgeon.iterate(sg);
    assertEquals(expected, cutSG);

    // Test the chain cut at the bottom
    sg = SemanticGraph.valueOf("[A obj> [B dep> C]]");
    cutSG = ssurgeon.iterate(sg);
    assertEquals(SemanticGraph.valueOf("[A obj> B]"), cutSG);

    // Test a chain cut at the start
    // Only the root will be left at the end
    sg = SemanticGraph.valueOf("[A dep> B dep> C]");
    cutSG = ssurgeon.iterate(sg);
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
    SemanticGraph cutSG = ssurgeonCut.iterate(sg);
    assertEquals(2, cutSG.vertexSet().size());
    SemanticGraph pruneSG = ssurgeonPrune.iterate(cutSG);
    // note that for now, the prune operation doesn't renumber nodes in any way
    SemanticGraph expected = SemanticGraph.valueOf("[B-1]");
    assertEquals(expected, pruneSG);

    // Test the chain cut at the bottom
    sg = SemanticGraph.valueOf("[A obj> [B dep> C]]");
    cutSG = ssurgeonCut.iterate(sg);
    assertEquals(cutSG.vertexSet().size(), 3);
    pruneSG = ssurgeonPrune.iterate(cutSG);
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
    SemanticGraph cutSG = ssurgeonCut.iterate(sg);
    assertEquals(cutSG.vertexSet().size(), 2);
    SemanticGraph pruneSG = ssurgeonPrune.iterate(cutSG);
    SemanticGraph expected = SemanticGraph.valueOf("[A]");
    assertEquals(pruneSG, expected);

    // Test a chain cut at the start
    sg = SemanticGraph.valueOf("[A dep> [B obj> C]]");
    cutSG = ssurgeonCut.iterate(sg);
    assertEquals(cutSG.vertexSet().size(), 3);
    pruneSG = ssurgeonPrune.iterate(cutSG);
    assertEquals(pruneSG, expected);

    // Test the chain cut at the bottom
    sg = SemanticGraph.valueOf("[A obj> [B dep> C]]");
    cutSG = ssurgeonCut.iterate(sg);
    assertEquals(cutSG.vertexSet().size(), 3);
    pruneSG = ssurgeonPrune.iterate(cutSG);
    assertEquals(pruneSG, SemanticGraph.valueOf("[A obj> B]"));

    // Test a chain cut at the start
    // Only the root will be left at the end
    sg = SemanticGraph.valueOf("[A dep> B dep> C]");
    cutSG = ssurgeonCut.iterate(sg);
    assertEquals(cutSG.vertexSet().size(), 3);
    pruneSG = ssurgeonPrune.iterate(cutSG);
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
    SemanticGraph cutSG = ssurgeonCut.iterate(sg);
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
    SemanticGraph newSG = rearrange.iterate(sg);
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
      SemanticGraph newSG = rearrange.iterate(sg);
      throw new AssertionError("Expected a specific exception SsurgeonRuntimeException here");
    } catch (SsurgeonRuntimeException e) {
      // yay
    }
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
    IndexedWord isNode = new IndexedWord();
    isNode.set(CoreAnnotations.TextAnnotation.class, "is");
    isNode.set(CoreAnnotations.LemmaAnnotation.class, "is");
    isNode.set(CoreAnnotations.OriginalTextAnnotation.class, "is");
    isNode.set(CoreAnnotations.PartOfSpeechAnnotation.class, "VBN");
    SsurgeonEdit addCopula = new AddDep("a2", EnglishGrammaticalRelations.COPULA, isNode);
    pattern.addEdit(addCopula);

    // Destroy subgraph
    SsurgeonEdit destroySubgraph = new DeleteGraphFromNode("a3");
    pattern.addEdit(destroySubgraph);

    // Process and output modified
    Collection<SemanticGraph> newSgs = pattern.execute(sg);
    for (SemanticGraph newSg : newSgs)
      System.out.println("Modified = "+newSg.toCompactString());
    String firstGraphString = newSgs.iterator().next().toCompactString().trim();
    assertEquals(firstGraphString, "[bartender cop>is nsubj>Joe det>the]");
  }
}
