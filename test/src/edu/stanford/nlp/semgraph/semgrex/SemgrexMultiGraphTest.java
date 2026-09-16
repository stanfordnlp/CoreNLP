package edu.stanford.nlp.semgraph.semgrex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
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
