
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
    SemanticGraph newSG = addSsurgeon.iterate(sg);
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
    newSG = addSsurgeon.iterate(sg);
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
    SemanticGraph newSG = addSsurgeon.iterate(sg);
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
    SemanticGraph newSG = addSsurgeon.iterate(sg);
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
    newSG = addSsurgeon.iterate(sg);
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
    SemanticGraph newSG = addSsurgeon.iterate(sg);
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
    newSG = addSsurgeon.iterate(sg);
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
    SemanticGraph newSG = addSsurgeon.iterate(sg);
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
    newSG = addSsurgeon.iterate(sg);
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
    SemanticGraph newSG = addSsurgeon.iterate(sg);
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
    SemanticGraph newSG = addSsurgeon.iterate(sg);
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
    newSG = addSsurgeon.iterate(sg);
    expected = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-4 amod> blue-3]]", Language.UniversalEnglish);
    for (SemanticGraphEdge edge : expected.edgeIterable()) {
      assertEquals(Language.UniversalEnglish, edge.getRelation().getLanguage());
    }
    // they look the same, but they're really not
    assertEquals(expected.toString(), newSG.toString());
    assertNotEquals(expected, newSG);

    // In this third version, now valueOf is creating an English graph,
    // not a UniversalEnglish graph, so it should work again
    sg = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> antennae-3]", Language.English);
    blueVertex = sg.getNodeByIndexSafe(4);
    assertNull(blueVertex);
    newSG = addSsurgeon.iterate(sg);
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
    SemanticGraph newSG = addSsurgeon.iterate(sg);
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
    SemanticGraph newSG = addSsurgeon.iterate(sg);
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
    // This will also fail, this time because Integer cannot be converted from a String
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
      throw new AssertionError("Expected a failure because IntPair is not readable from a String in CoreLabel");
    } catch (SsurgeonParseException e) {
      // yay
    }
  }


  /**
   * Check that adding a word to the start of a sentence works as expected
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
    SemanticGraph newSG = editSsurgeon.iterate(sg);
    SemanticGraph expected = SemanticGraph.valueOf("[has-2 nsubj> Jennifer-1 obj> [antennae-4 dep> blue-3]]");
    assertEquals(expected, newSG);
    // the Ssurgeon we just created should not put a tag on the word
    // but it SHOULD put blue at the start of the sentence
    blueVertex = newSG.getNodeByIndexSafe(3);
    assertNotNull(blueVertex);
    assertNull(blueVertex.tag());
    assertEquals("blue", blueVertex.value());
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

    SemanticGraph newSG = editSsurgeon.iterate(sg);
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
}
