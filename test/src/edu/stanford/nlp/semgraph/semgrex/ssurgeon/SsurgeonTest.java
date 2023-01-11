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
    SsurgeonEdit apposSnip = new RemoveNamedEdge("e1", "a1", "a2");
    pattern.addEdit(apposSnip);

    SsurgeonEdit nsubjSnip = new RemoveNamedEdge("e2", "a3", "a1");
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
