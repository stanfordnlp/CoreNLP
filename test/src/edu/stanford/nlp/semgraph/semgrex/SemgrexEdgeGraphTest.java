package edu.stanford.nlp.semgraph.semgrex;

import static org.junit.Assert.*;


import org.junit.Test;

import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;

/**
 * Tests that a named edge remembers which graph it was found in.
 *<br>
 * The two graphs share the arc "shared", so the edge on its own is the
 * same in both, while "extra" is only in the enhanced graph.
 *
 * @author John Bauer
 */
public class SemgrexEdgeGraphTest {

  static SemgrexGraphs both() {
    SemanticGraph basic = SemanticGraph.valueOf("[root-1 shared> bas-2]");
    SemanticGraph enhanced = SemanticGraph.valueOf("[root-1 shared> bas-2 extra> enh-3]");
    return SemgrexGraphs.of(basic, enhanced);
  }

  @Test
  public void basicEdge() {
    SemgrexMatcher matcher = SemgrexPattern.compile("{} >shared=e {}").matcher(both());
    assertTrue(matcher.find());
    assertEquals("shared", matcher.getEdge("e").getRelation().toString());
    assertEquals(SemgrexGraphName.BASIC, matcher.getEdgeGraph("e"));
    assertNull(matcher.getEdgeGraph("f"));
    assertFalse(matcher.find());
  }

  @Test
  public void enhancedEdge() {
    SemgrexMatcher matcher = SemgrexPattern.compile("{} >extra@enhanced=e {}").matcher(both());
    assertTrue(matcher.find());
    assertEquals("extra", matcher.getEdge("e").getRelation().toString());
    assertEquals(SemgrexGraphName.ENHANCED, matcher.getEdgeGraph("e"));
  }

  @Test
  public void sameArcInBothGraphs() {
    // the shared arc is found in whichever graph the relation searched
    SemgrexMatcher matcher = SemgrexPattern.compile("{} >shared=b {} >shared@enhanced=e {}").matcher(both());
    assertTrue(matcher.find());
    assertEquals(matcher.getEdge("b"), matcher.getEdge("e"));
    assertEquals(SemgrexGraphName.BASIC, matcher.getEdgeGraph("b"));
    assertEquals(SemgrexGraphName.ENHANCED, matcher.getEdgeGraph("e"));
  }

  @Test
  public void backreferenceNeedsTheSameGraph() {
    // an edge named twice in the same graph is the same edge
    SemgrexMatcher matcher = SemgrexPattern.compile("{} >shared=e {} >shared=e {}").matcher(both());
    assertTrue(matcher.find());

    // the equal arc in the other graph is a different edge
    matcher = SemgrexPattern.compile("{} >shared=e {} >shared@enhanced=e {}").matcher(both());
    assertFalse(matcher.find());
  }

  @Test
  public void singleGraph() {
    SemanticGraph graph = SemanticGraph.valueOf("[root-1 shared> bas-2]");
    SemgrexMatcher matcher = SemgrexPattern.compile("{} >shared=e {}").matcher(graph);
    assertTrue(matcher.find());
    assertEquals(SemgrexGraphName.BASIC, matcher.getEdgeGraph("e"));
  }

  @Test
  public void match() {
    SemgrexPattern pattern = SemgrexPattern.compile("{} >shared=b {} >extra@enhanced=e {}");
    SemgrexMatcher matcher = pattern.matcher(both());
    assertTrue(matcher.find());
    SemgrexMatch match = new SemgrexMatch(pattern, matcher);
    assertEquals(SemgrexGraphName.BASIC, match.getEdgeGraph("b"));
    assertEquals(SemgrexGraphName.ENHANCED, match.getEdgeGraph("e"));
    assertEquals("extra", match.getEdge("e").getRelation().toString());
    assertNull(match.getEdgeGraph("f"));
  }

  @Test
  public void putNamedEdge() {
    SemgrexMatcher matcher = SemgrexPattern.compile("{} >extra@enhanced=e {}").matcher(both());
    assertTrue(matcher.find());
    SemanticGraphEdge original = matcher.getEdge("e");
    SemanticGraphEdge replacement = new SemanticGraphEdge(original.getGovernor(), original.getDependent(),
                                                          original.getRelation(), 2.0, false);

    // replacing an edge keeps the graph it was found in
    assertSame(original, matcher.putNamedEdge("e", replacement));
    assertSame(replacement, matcher.getEdge("e"));
    assertEquals(SemgrexGraphName.ENHANCED, matcher.getEdgeGraph("e"));

    // a new name is in the basic graph unless told otherwise
    assertNull(matcher.putNamedEdge("f", replacement));
    assertEquals(SemgrexGraphName.BASIC, matcher.getEdgeGraph("f"));
    matcher.putNamedEdge("g", replacement, SemgrexGraphName.ENHANCED);
    assertEquals(SemgrexGraphName.ENHANCED, matcher.getEdgeGraph("g"));
  }

  @Test
  public void matchedEdgeEquality() {
    SemanticGraphEdge edge = both().getDefault().edgeListSorted().get(0);
    assertEquals(new MatchedEdge(edge, SemgrexGraphName.BASIC), new MatchedEdge(edge, SemgrexGraphName.BASIC));
    assertNotEquals(new MatchedEdge(edge, SemgrexGraphName.BASIC), new MatchedEdge(edge, SemgrexGraphName.ENHANCED));
    assertEquals(edge.toString(), new MatchedEdge(edge, SemgrexGraphName.BASIC).toString());
    assertEquals(edge + "@enhanced", new MatchedEdge(edge, SemgrexGraphName.ENHANCED).toString());
  }
}
