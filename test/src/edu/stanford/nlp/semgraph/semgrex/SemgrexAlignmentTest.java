package edu.stanford.nlp.semgraph.semgrex;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;

/**
 * Tests the alignment relation, {@code @}, which matches across two
 * graphs joined by an {@link Alignment}.
 *<br>
 * The only users of this are two featurizers in the old RTE code,
 * {@code MarriageFeaturizer} and {@code NewStructureFeaturizer}, so
 * these tests aim at what those need rather than at everything the
 * grammar allows.  In particular the text to hypothesis direction, which
 * a pattern reaches by starting with a bare {@code @}, is not covered,
 * because no RTE pattern uses it.
 *<br>
 * The fixture imitates the shape RTE works on: a hypothesis sentence and
 * a text sentence which say the same thing differently.
 *
 * @author John Bauer
 */
public class SemgrexAlignmentTest {

  /** "Bill married Mary" */
  static final SemanticGraph HYP =
    SemanticGraph.valueOf("[married-2 nsubjpass> Bill-1 prep_to> Mary-3]");

  /** "Bill's wife Mary" */
  static final SemanticGraph TXT =
    SemanticGraph.valueOf("[wife-3 poss> his-2 nn> Mary-4 nsubj> Bill-1]");

  /**
   * Builds an alignment from hypothesis words to text words, by word.
   *<br>
   * When total is set, a hypothesis word with no partner is mapped to
   * {@link IndexedWord#NO_WORD} rather than being left out.  That is
   * what RTE does -- see StochasticTextAligner, which puts NO_WORD for
   * every hypothesis word it fails to align -- and it matters, since the
   * two cases behave differently.  See testMissingEntry and
   * testNoWordSentinel below.
   */
  static Alignment alignment(boolean total, String ... hypThenTxt) {
    Map<String, String> pairs = new HashMap<>();
    for (int i = 0; i < hypThenTxt.length; i += 2) {
      pairs.put(hypThenTxt[i], hypThenTxt[i + 1]);
    }

    Map<IndexedWord, IndexedWord> map = new HashMap<>();
    for (IndexedWord hypWord : HYP.vertexSet()) {
      String txtWord = pairs.get(hypWord.word());
      if (txtWord == null) {
        if (total) {
          map.put(hypWord, IndexedWord.NO_WORD);
        }
        continue;
      }
      for (IndexedWord candidate : TXT.vertexSet()) {
        if (candidate.word().equals(txtWord)) {
          map.put(hypWord, candidate);
        }
      }
    }
    return new Alignment(map, 1.0, "test alignment");
  }

  /** Everything aligns: married/wife, Bill/Bill, Mary/Mary */
  static Alignment fullAlignment() {
    return alignment(true, "married", "wife", "Bill", "Bill", "Mary", "Mary");
  }

  /**
   * Every match of the pattern, rendered as the named nodes it bound.
   *<br>
   * Sorted, since the matcher does not promise an order.
   */
  static List<String> findAll(String pattern, Alignment alignment, String ... names) {
    SemgrexMatcher matcher = SemgrexPattern.compile(pattern).matcher(HYP, alignment, TXT);
    List<String> matches = new ArrayList<>();
    while (matcher.find()) {
      StringBuilder sb = new StringBuilder();
      for (String name : names) {
        if (sb.length() > 0) {
          sb.append(" ");
        }
        sb.append(name).append("=").append(describe(matcher.getNode(name)));
      }
      matches.add(sb.toString());
    }
    Collections.sort(matches);
    return matches;
  }

  static String describe(IndexedWord node) {
    if (node == null) {
      return "unbound";
    }
    if (node.equals(IndexedWord.NO_WORD)) {
      return "NO_WORD";
    }
    return node.word() + "-" + node.index();
  }

  static void assertMatches(List<String> matches, String ... expected) {
    List<String> want = new ArrayList<>(Arrays.asList(expected));
    Collections.sort(want);
    assertEquals(want, matches);
  }

  // ------------------------------------------------------------------
  // the basics
  // ------------------------------------------------------------------

  /**
   * {@code @} walks from a hypothesis node to the text node it aligns to
   */
  @Test
  public void testAlignmentMap() {
    assertMatches(findAll("{}=h @ {}=t", fullAlignment(), "h", "t"),
                  "h=married-2 t=wife-3",
                  "h=Bill-1 t=Bill-1",
                  "h=Mary-3 t=Mary-4");
  }

  /**
   * Relations on either side of an {@code @} resolve in their own graph
   *<br>
   * This is the whole point of the feature and the thing most worth
   * pinning: nsubjpass is an edge of the hypothesis graph, poss is an
   * edge of the text graph, and one pattern asks about both.
   */
  @Test
  public void testRelationsOnBothSides() {
    assertMatches(findAll("({}=hgov >nsubjpass {}=hdep) @ ({}=t >poss {}=tposs)",
                          fullAlignment(), "hgov", "hdep", "t", "tposs"),
                  "hgov=married-2 hdep=Bill-1 t=wife-3 tposs=his-2");

    // poss is a text edge, so asking for it on the hypothesis side finds nothing
    assertMatches(findAll("{}=hgov >poss {}=hdep", fullAlignment(), "hgov", "hdep"));
  }

  /**
   * A second {@code @} goes back to the hypothesis graph
   */
  @Test
  public void testDoubleAlignmentReturns() {
    assertMatches(findAll("{}=h @ ({}=t @ {}=back)", fullAlignment(), "h", "t", "back"),
                  "h=married-2 t=wife-3 back=married-2",
                  "h=Bill-1 t=Bill-1 back=Bill-1",
                  "h=Mary-3 t=Mary-4 back=Mary-3");
  }

  /**
   * A relation written after an {@code @} group belongs to the graph the
   * group started in, not the one it crossed to
   *<br>
   * The flip applies to the children of the aligned node, not to its
   * siblings, so in {@code (A @ B) >reln C} the C is still a hypothesis
   * node.  NewStructureFeaturizer's argument mismatch pattern is built
   * on this: it writes {@code ({}=hVerb @ {}=tVerb) >/nsubj|dobj|.../ ...}
   * and expects the arguments to come from the hypothesis.
   */
  @Test
  public void testSiblingRelationStaysInFirstGraph() {
    assertMatches(findAll("({}=h @ {}=t) >prep_to {}=hsib", fullAlignment(), "h", "t", "hsib"),
                  "h=married-2 t=wife-3 hsib=Mary-3");
  }

  /**
   * The description on the far side of an {@code @} constrains the partner
   *<br>
   * {@code @} is a relation whose one candidate is the aligned node, so
   * attributes written there are checked against that node.  If they
   * disagree the pattern simply fails; there is no way to bind a
   * different text node to that name.
   */
  @Test
  public void testAlignedPartnerIsConstrained() {
    assertMatches(findAll("{word:married}=h @ {word:wife}=t", fullAlignment(), "h", "t"),
                  "h=married-2 t=wife-3");

    // "married" aligns to "wife", so asking for "Bill" there fails
    assertMatches(findAll("{word:married}=h @ {word:Bill}=t", fullAlignment(), "h", "t"));
  }

  /**
   * The graph switch reaches the aligned node's descendants, not its siblings
   *<br>
   * Which matters more than it sounds, because a run of relations after a
   * node description are siblings rather than a chain -- see
   * SemgrexTest.testRelationChainIsSiblings.  So a relation written after
   * an {@code @} is usually still asking about the first graph, and
   * parentheses are what puts it into the second.
   */
  @Test
  public void testAlignmentScopeIsDescendantsOnly() {
    // nested inside the @, so poss is asked of the text graph
    assertMatches(findAll("{}=h @ ({}=t >poss {}=p)", fullAlignment(), "h", "t", "p"),
                  "h=married-2 t=wife-3 p=his-2");

    // a sibling of the @ at the same node, so prep_to is asked of the
    // hypothesis graph -- note the text graph has no prep_to at all
    assertMatches(findAll("({}=h @ {}=t) >prep_to {}=hsib", fullAlignment(), "h", "t", "hsib"),
                  "h=married-2 t=wife-3 hsib=Mary-3");

    // and without parentheses everything hangs off "married": the @ and
    // the prep_to are siblings, so c is married's partner rather than
    // Bill's, and d comes from the hypothesis graph
    assertMatches(findAll("{}=a >nsubjpass {}=b @ {}=c >prep_to {}=d",
                          fullAlignment(), "a", "b", "c", "d"),
                  "a=married-2 b=Bill-1 c=wife-3 d=Mary-3");
  }

  // ------------------------------------------------------------------
  // hypothesis words with no text partner
  // ------------------------------------------------------------------

  /**
   * A hypothesis word simply absent from the map does not match {@code @}
   *<br>
   * Note that it fails to match rather than throwing.  GraphRelation's
   * ALIGNMENT iterator reads the map with get and lets a null mean "no
   * candidate", so a partial map is safe.
   */
  @Test
  public void testMissingEntry() {
    Alignment partial = alignment(false, "Bill", "Bill", "Mary", "Mary");
    assertMatches(findAll("{word:married}=h @ {}=t", partial, "h", "t"));
  }

  /**
   * {@code ?@} lets the hypothesis side match even with no partner, leaving the name unbound
   */
  @Test
  public void testOptionalAlignment() {
    Alignment partial = alignment(false, "Bill", "Bill", "Mary", "Mary");
    assertMatches(findAll("{word:married}=h ?@ {}=t", partial, "h", "t"),
                  "h=married-2 t=unbound");
  }

  /**
   * A hypothesis word mapped to NO_WORD matches {@code @}, binding the sentinel
   *<br>
   * <b>This is the case which needs care.</b>  RTE builds a total map, so
   * an unaligned hypothesis word is mapped to NO_WORD rather than being
   * left out, and {@code @} therefore always produces a candidate.  The
   * candidate is a sentinel which is not a vertex of the text graph at
   * all, so the two halves of the pattern behave quite differently:
   *<ul>
   * <li>a positive relation from it finds nothing, because every
   *     searchNodeIterator opens by checking for NO_WORD and stopping;
   * <li>a <i>negated</i> relation from it succeeds, precisely because
   *     there is nothing there to find.
   *</ul>
   * NewStructureFeaturizer's argument mismatch pattern ends its
   * disjunction with {@code ![ < {}=tVerb]}, so an unaligned hypothesis
   * word matches that branch and reports a mismatch with tWord bound to
   * the sentinel.  Whether that was intended is not recorded anywhere;
   * it may well be, since a hypothesis argument aligned to nothing is a
   * kind of mismatch.  Either way it is the behaviour RTE has today, and
   * anything which reworks how the two graphs are carried around needs
   * to preserve it or change it deliberately.
   */
  @Test
  public void testNoWordSentinel() {
    Alignment sentinel = alignment(true, "Bill", "Bill", "Mary", "Mary");

    // the sentinel is produced as a match
    assertMatches(findAll("{word:married}=h @ {}=t", sentinel, "h", "t"),
                  "h=married-2 t=NO_WORD");

    // a positive relation from the sentinel finds nothing
    assertMatches(findAll("{word:married}=h @ ({}=t > {})", sentinel, "h", "t"));

    // but a negated one succeeds, which is the branch RTE relies on
    assertMatches(findAll("{word:married}=h @ ({}=t ![ > {} ])", sentinel, "h", "t"),
                  "h=married-2 t=NO_WORD");

    // and the same negation fails for a word which really is aligned to
    // a text node with the relation in question, which is the control
    assertMatches(findAll("{word:Bill}=h @ ({}=t ![ < {} ])", fullAlignment(), "h", "t"));
  }

  /**
   * The shape NewStructureFeaturizer actually uses, reduced to its bones
   *<br>
   * A hypothesis verb aligned to a text verb, where the hypothesis
   * argument's partner is not an argument of that text verb.  Here
   * "married" has no text partner at all, so the negated branch fires
   * and the mismatch is reported against the sentinel.
   */
  @Test
  public void testArgumentMismatchShape() {
    Alignment sentinel = alignment(true, "Bill", "Bill", "Mary", "Mary");
    assertMatches(findAll("({}=hVerb @ {}=tVerb) >nsubjpass ({}=hWord @ ({}=tWord ![ < {}=tVerb ]))",
                          sentinel, "hVerb", "tVerb", "hWord", "tWord"),
                  "hVerb=married-2 tVerb=NO_WORD hWord=Bill-1 tWord=Bill-1");
  }

  // ------------------------------------------------------------------
  // degenerate cases
  // ------------------------------------------------------------------

  /**
   * An {@code @} pattern run through the single graph matcher matches nothing
   *<br>
   * ALIGNMENT's iterator returns without producing anything when there is
   * no alignment, so this is quiet rather than an error.  Worth knowing,
   * since a pattern which silently matches nothing is easy to mistake
   * for a pattern which is simply wrong.
   */
  @Test
  public void testNoAlignmentAtAll() {
    SemgrexPattern pattern = SemgrexPattern.compile("{}=h @ {}=t");
    SemgrexMatcher matcher = pattern.matcher(HYP);
    assertFalse(matcher.find());
  }

  /**
   * The root attribute on the far side of an {@code @} consults the wrong graph
   *<br>
   * <b>Commented out because it fails today.</b>  "wife-3" really is the
   * root of the text graph, so this ought to match, and it does not.
   *<br>
   * The cause is that NodePattern.resetChild decides the graph from the
   * relation of the <i>parent</i> node, so the node an {@code @} lands on
   * still carries the previous graph when its own attributes are checked.
   * That is invisible for every other attribute, since only the root
   * attribute consults the graph rather than the IndexedWord -- see
   * NodePattern's use of sg.getRoots().  RTE avoids it by accident:
   * MarriageFeaturizer writes its root attribute one level further down,
   * where the graph has already flipped.
   *<br>
   * Folding the graphs into a single object, so that a matcher holds the
   * graph for its own level and nodeAttrMatch uses that, should make this
   * pass.  It is the one place where that work changes behaviour rather
   * than only moving code, so it is worth uncommenting to check.
   */
  // @Test
  // public void testRootAttributeAcrossAlignment() {
  //   assertMatches(findAll("{word:married}=h @ {$}=t", fullAlignment(), "h", "t"),
  //                 "h=married-2 t=wife-3");
  // }

  /**
   * An empty alignment matches nothing, and does not throw
   */
  @Test
  public void testEmptyAlignment() {
    Alignment empty = new Alignment(new HashMap<>(), 0.0, "empty");
    assertMatches(findAll("{}=h @ {}=t", empty, "h", "t"));
  }
}
