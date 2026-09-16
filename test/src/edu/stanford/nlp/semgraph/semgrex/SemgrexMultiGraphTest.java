package edu.stanford.nlp.semgraph.semgrex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import edu.stanford.nlp.semgraph.SemanticGraph;

/**
 * Tests matching against a sentence which has more than one graph of it.
 *<br>
 * A relation may name the graph it wants, as {@code >nsubj@enhanced}, and
 * the relations written below it are matched in that graph as well until
 * something names a different one.
 *<br>
 * The two graphs here are deliberately not the same shape: "enh" and the
 * nodes below it are in the enhanced graph only, and "bas" is in both, so
 * a match says which graph was being searched.
 *
 * @author John Bauer
 */
public class SemgrexMultiGraphTest {

  static final SemanticGraph BASIC =
    SemanticGraph.valueOf("[root-1 shared> bas-2]");

  static final SemanticGraph ENHANCED =
    SemanticGraph.valueOf("[root-1 shared> bas-2 extra> [enh-3 deeper> low-4]]");

  /** A sentence with both graphs of it */
  static SemgrexGraphs both() {
    return SemgrexGraphs.of(BASIC, ENHANCED);
  }

  /** A sentence which was never given an enhanced graph */
  static SemgrexGraphs basicOnly() {
    return SemgrexGraphs.of(BASIC, null);
  }

  static List<String> findAll(String pattern, SemgrexGraphs graphs) {
    SemgrexMatcher matcher = SemgrexPattern.compile(pattern).matcher(graphs);
    List<String> matches = new ArrayList<>();
    while (matcher.find()) {
      matches.add(matcher.getMatch().word());
    }
    Collections.sort(matches);
    return matches;
  }

  static void assertMatches(String pattern, SemgrexGraphs graphs, String ... expected) {
    List<String> want = new ArrayList<>();
    Collections.addAll(want, expected);
    Collections.sort(want);
    assertEquals("Wrong matches for " + pattern, want, findAll(pattern, graphs));
  }

  /**
   * Without a graph name, the search stays in the basic graph
   */
  @Test
  public void testDefaultsToBasic() {
    assertMatches("{word:root} >shared {}", both(), "root");

    // "extra" is an edge of the enhanced graph only
    assertMatches("{word:root} >extra {}", both());
  }

  /**
   * A relation with a graph name is matched in that graph
   */
  @Test
  public void testNamedGraph() {
    assertMatches("{word:root} >extra@enhanced {}", both(), "root");

    // a relation which exists in both is found either way
    assertMatches("{word:root} >shared@enhanced {}", both(), "root");
    assertMatches("{word:root} >shared@basic {}", both(), "root");
  }

  /**
   * The relations below a named graph are matched in it as well
   *<br>
   * "deeper" is an edge of the enhanced graph, and "enh" is not a node of
   * the basic graph at all, so this can only match if the search stayed
   * where the first relation put it.
   */
  @Test
  public void testGraphChoiceIsSticky() {
    assertMatches("{word:root} >extra@enhanced ({word:enh} >deeper {})", both(), "root");

    // two levels below the switch, still in the enhanced graph
    assertMatches("{word:root} >extra@enhanced ({word:enh} >deeper ({word:low}))", both(), "root");
  }

  /**
   * A relation may name the basic graph to come back out again
   */
  @Test
  public void testSwitchingBack() {
    // "deeper" is not an edge of the basic graph, so asking for it there finds nothing
    assertMatches("{word:root} >extra@enhanced ({word:enh} >deeper@basic {})", both());

    // and a node of the enhanced graph only is not there to be found either
    assertMatches("{word:root} >extra@enhanced ({word:enh} >deeper@basic {word:low})", both());
  }

  /**
   * A relation written beside the named one, rather than below it, is unaffected
   *<br>
   * A run of relations after a node are siblings, so the second one hangs
   * off "root" and is matched in whichever graph "root" was found in, not
   * in the one its neighbour asked for.
   */
  @Test
  public void testSiblingsAreUnaffected() {
    assertMatches("{word:root} >extra@enhanced ({word:enh}) >shared {}", both(), "root");

    // the sibling really is in the basic graph: "deeper" is not an edge there
    assertMatches("{word:root} >extra@enhanced ({word:enh}) >deeper {}", both());
  }

  /**
   * Each branch of a disjunction of relations is matched in the graph it names
   */
  @Test
  public void testDisjunctionOfGraphs() {
    // the first branch cannot match in the basic graph, the second can in
    // the enhanced one, so the disjunction as a whole matches
    assertMatches("{word:root} [>extra {} | >extra@enhanced {}]", both(), "root");

    // neither branch can match, since "deeper" does not leave "root"
    assertMatches("{word:root} [>deeper {} | >deeper@enhanced {}]", both());
  }

  /**
   * Asking for a graph the sentence does not have is an error, not an empty result
   *<br>
   * Quietly matching nothing would look the same as a pattern which simply
   * did not apply, which is the more likely reading and the wrong one.
   */
  @Test
  public void testMissingGraph() {
    IllegalStateException e =
      assertThrows(IllegalStateException.class,
                   () -> findAll("{word:root} >extra@enhanced {}", basicOnly()));
    assertTrue(e.getMessage().contains("ENHANCED"));

    // the basic graph is there, so a pattern which stays in it is fine
    assertMatches("{word:root} >shared {}", basicOnly(), "root");
  }

  /**
   * The graphs of a sentence can be collected without building a map
   *<br>
   * A graph which the sentence does not have is left out rather than
   * rejected here, so that a caller does not have to work out in advance
   * which graphs the pattern is going to ask for.
   */
  @Test
  public void testGraphsFactory() {
    SemgrexPattern pattern = SemgrexPattern.compile("{word:root} >extra@enhanced {}");
    assertTrue(pattern.matcher(SemgrexGraphs.of(BASIC, ENHANCED)).find());

    // a missing enhanced graph is only an error once a pattern asks for it
    assertTrue(SemgrexPattern.compile("{word:root} >shared {}")
               .matcher(SemgrexGraphs.of(BASIC, null)).find());
    assertThrows(IllegalStateException.class,
                 () -> pattern.matcher(SemgrexGraphs.of(BASIC, null)).find());

    // the search starts in the basic graph, so a sentence without one has
    // nowhere to start even if the pattern only ever asks for the enhanced
    assertThrows(IllegalStateException.class,
                 () -> pattern.matcher(SemgrexGraphs.of(null, ENHANCED)).find());
  }

  /**
   * The search can start in the enhanced graph, over the nodes only it has
   *<br>
   * Which graph the search begins in decides which nodes it can begin at.
   * Starting in the basic graph, "enh" is never reached, because the scan
   * only ever visits nodes of the graph it started in.  A pattern rooted
   * at an extra node of the enhanced graph therefore has to start there.
   */
  @Test
  public void testStartingInTheEnhancedGraph() {
    // a pattern with no relations says nothing about where it may start,
    // so every graph is searched and the extra node is among the candidates
    assertMatches("{word:enh}", both(), "enh");

    // and a pattern whose first relation names the enhanced graph starts
    // there, so it can begin at a node only that graph has
    assertMatches("{word:enh} >deeper@enhanced {word:low}", both(), "enh");

    // the relations below carry on in the graph it started in
    assertMatches("{word:enh} >deeper@enhanced ({word:low})", both(), "enh");
  }

  /**
   * Only the graphs a match could start in are searched
   *<br>
   * Which graphs those are is read off the relations written directly at
   * the start of the pattern, each of which names the graph it is looked
   * for in.  What lies beyond one of those relations is matched from the
   * node it found, so it has no say in where a match may begin.
   */
  @Test
  public void testStartingGraphs() {
    // an ordinary pattern begins in the basic graph
    assertEquals(EnumSet.of(SemgrexGraphName.BASIC),
                 SemgrexPattern.compile("{word:root} >shared {}").startingGraphs());

    // one which reaches across begins in the graph it names
    assertEquals(EnumSet.of(SemgrexGraphName.ENHANCED),
                 SemgrexPattern.compile("{word:root} >extra@enhanced {}").startingGraphs());

    // either, when the first relation is a choice between them
    assertEquals(EnumSet.of(SemgrexGraphName.BASIC, SemgrexGraphName.ENHANCED),
                 SemgrexPattern.compile("{word:root} [>shared {} | >extra@enhanced {}]")
                 .startingGraphs());

    // two relations written side by side are both at the start
    assertEquals(EnumSet.of(SemgrexGraphName.BASIC, SemgrexGraphName.ENHANCED),
                 SemgrexPattern.compile("{word:root} >shared {} >extra@enhanced {}")
                 .startingGraphs());

    // but a graph named below the first relation says nothing about the start
    assertEquals(EnumSet.of(SemgrexGraphName.BASIC),
                 SemgrexPattern.compile("{word:root} >shared ({} >deeper@enhanced {})")
                 .startingGraphs());

    // a pattern with no relations is unconstrained
    assertEquals(EnumSet.noneOf(SemgrexGraphName.class),
                 SemgrexPattern.compile("{word:root}").startingGraphs());
  }

  /**
   * A pattern which can only start in a graph the sentence has not got says so
   */
  @Test
  public void testNoStartingGraphPresent() {
    // named at the start
    IllegalStateException e =
      assertThrows(IllegalStateException.class,
                   () -> findAll("{word:root} >extra@enhanced {}", basicOnly()));
    assertTrue(e.getMessage().contains("ENHANCED"));

    // named below the first relation, where the search could have started
    // but the pattern still asks for a graph which is not there
    e = assertThrows(IllegalStateException.class,
                     () -> findAll("{word:root} >shared ({} >deeper@enhanced {})", basicOnly()));
    assertTrue(e.getMessage().contains("ENHANCED"));

    // and named in one branch of a choice, where the other branch could
    // have matched: the pattern as written cannot be satisfied here
    e = assertThrows(IllegalStateException.class,
                     () -> findAll("{word:root} [>shared {} | >extra@enhanced {}]", basicOnly()));
    assertTrue(e.getMessage().contains("ENHANCED"));

    // every graph the pattern names, wherever it appears
    assertEquals(EnumSet.of(SemgrexGraphName.BASIC, SemgrexGraphName.ENHANCED),
                 SemgrexPattern.compile("{word:root} >shared ({} >deeper@enhanced {})")
                 .requiredGraphs());
  }

  /**
   * An ordinary pattern still visits the nodes in dependency order
   *<br>
   * Only one graph is searched for it, so the topological sort which was
   * always used still applies.  The union of two graphs has no such order,
   * and is visited in sentence order instead.
   */
  @Test
  public void testOrdinaryPatternKeepsItsOrder() {
    List<String> order = new ArrayList<>();
    SemgrexMatcher matcher = SemgrexPattern.compile("{} >shared {}").matcher(both());
    while (matcher.find()) {
      order.add(matcher.getMatch().word());
    }
    assertEquals(Collections.singletonList("root"), order);

    // every node of the basic graph is a candidate, in dependency order
    order.clear();
    matcher = SemgrexPattern.compile("{}=any >shared {}").matcher(SemgrexGraphs.of(BASIC, null));
    while (matcher.find()) {
      order.add(matcher.getMatch().word());
    }
    assertEquals(Collections.singletonList("root"), order);
  }

  /**
   * The graph name survives printing the pattern back out
   */
  @Test
  public void testGraphNameRoundTrips() {
    for (String pattern : new String[] {"{word:root} >extra@enhanced {}",
                                        "{word:root} >extra@basic {}",
                                        "{word:root} >extra@enhanced ({} >deeper {})",
                                        "{word:root} [>extra {} | >extra@enhanced {}]"}) {
      assertEquals(SemgrexTest.withoutIdleWhitespace(pattern),
                   SemgrexTest.withoutIdleWhitespace(SemgrexPattern.compile(pattern).toString()));
    }
  }
}
