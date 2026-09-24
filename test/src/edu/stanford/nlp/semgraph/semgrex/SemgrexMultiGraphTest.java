package edu.stanford.nlp.semgraph.semgrex;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.CoNLLUReader;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
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
   * A sentence with an empty node, as an enhanced UD treebank has
   *<br>
   * Word 2.1 is a dropped pronoun: the enhanced graph gives it an
   * incoming edge, and the basic graph does not have it at all.  This is
   * the shape the pattern below is for.
   */
  static final String EMPTY_NODE_SENTENCE = String.join("\n",
      "# sent_id = dropped-pronoun",
      "# text = Salió fuera",
      "1\tSalió\tsalir\tVERB\t_\t_\t0\troot\t0:root\t_",
      "2\tfuera\tfuera\tADV\t_\t_\t1\tadvmod\t1:advmod\t_",
      "2.1\t_\t_\tPRON\tp\t_\t_\t_\t1:nsubj\t_",
      "", "");

  /**
   * Reads sentences from CoNLL-U text, the way the command line tool does
   */
  static List<CoreMap> readSentences(String conllu) throws IOException {
    File file = File.createTempFile("semgrex", ".conllu");
    file.deleteOnExit();
    try (Writer out = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
      out.write(conllu);
    }
    List<CoreMap> sentences = new ArrayList<>();
    for (Annotation doc : new CoNLLUReader().readCoNLLUFile(file.toString())) {
      sentences.addAll(doc.get(CoreAnnotations.SentencesAnnotation.class));
    }
    return sentences;
  }

  /**
   * The empty nodes of an enhanced graph, found through matchSentences
   *<br>
   * An empty node has a governor in the enhanced graph and none in the
   * basic graph, which is another way of saying the basic graph has not
   * got it.  Finding one needs the whole of the multiple graph support at
   * once: both graphs read from the sentence, the search covering the
   * graphs the pattern names rather than the basic one alone, and the
   * relations resolved in the graph each of them asks for.
   */
  @Test
  public void testEmptyNodesThroughMatchSentences() throws IOException {
    List<CoreMap> sentences = readSentences(EMPTY_NODE_SENTENCE);
    assertEquals(1, sentences.size());

    SemgrexPattern pattern = SemgrexPattern.compile("{} <@enhanced {} !< {}");
    List<Pair<CoreMap, List<SemgrexMatch>>> matches = pattern.matchSentences(sentences, false);
    assertEquals(1, matches.size());

    List<SemgrexMatch> found = matches.get(0).second();
    assertEquals(1, found.size());
    IndexedWord empty = found.get(0).getMatch();
    assertEquals(2, empty.index());
    assertEquals(1, empty.getEmptyIndex());

    // a pattern with no relations searches every graph, so it sees the two
    // words of the basic graph and the empty node the enhanced graph adds
    SemgrexPattern everything = SemgrexPattern.compile("{}");
    assertEquals(3, everything.matchSentences(sentences, false).get(0).second().size());

    // whereas one which stays in the basic graph sees only the two words
    SemgrexPattern basicOnly = SemgrexPattern.compile("{} <<{}");
    assertEquals(1, basicOnly.matchSentences(sentences, false).get(0).second().size());
  }

  /**
   * A sentence from a CoNLL-U file which is missing a graph is named by its sent_id
   */
  @Test
  public void testMissingGraphNamesTheSentId() throws IOException {
    String conllu = String.join("\n",
        "# sent_id = no-enhanced",
        "# text = Unban Mox Opal",
        "1\tUnban\tunban\tVERB\t_\t_\t0\troot\t_\t_",
        "2\tMox\tMox\tPROPN\t_\t_\t3\tcompound\t_\t_",
        "3\tOpal\tOpal\tPROPN\t_\t_\t1\tobj\t_\t_",
        "", "");
    List<CoreMap> sentences = readSentences(EMPTY_NODE_SENTENCE + conllu);
    assertEquals(2, sentences.size());

    SemgrexPattern pattern = SemgrexPattern.compile("{} <@enhanced {}");
    IllegalStateException e = assertThrows(IllegalStateException.class,
                                           () -> pattern.matchSentences(sentences, false));
    assertTrue(e.getMessage(), e.getMessage().contains("|# sent_id = no-enhanced|"));
  }

  /**
   * How a sentence is named when it has not got a sent_id
   *<br>
   * Its index and its words, with any empty words left out, as they are
   * not part of the text; the text itself when there are no tokens.
   * A sent_id, either kind, comes before any of that.
   */
  @Test
  public void testDescribeSentence() {
    CoreLabel sue = new CoreLabel();
    sue.setWord("Sue");
    CoreLabel likes = new CoreLabel();
    likes.setWord("likes");
    CoreLabel empty = new CoreLabel();
    empty.setWord("likes");
    empty.setEmptyIndex(1);
    CoreLabel tea = new CoreLabel();
    tea.setWord("tea");

    CoreMap sentence = new ArrayCoreMap();
    assertEquals("a sentence", SemgrexPattern.describe(sentence));

    sentence.set(CoreAnnotations.SentenceIndexAnnotation.class, 3);
    assertEquals("the sentence at index 3", SemgrexPattern.describe(sentence));

    sentence.set(CoreAnnotations.TokensAnnotation.class, Arrays.asList(sue, likes, empty, tea));
    assertEquals("the sentence at index 3, |Sue likes tea|,", SemgrexPattern.describe(sentence));

    CoreMap text = new ArrayCoreMap();
    text.set(CoreAnnotations.TextAnnotation.class, "Sue likes tea.");
    assertEquals("the sentence |Sue likes tea.|", SemgrexPattern.describe(text));

    // a sent_id, such as a Stanza request sends, is used before the index
    sentence.set(CoreAnnotations.SentenceIDAnnotation.class, "sue-1");
    assertEquals("the sentence with sent_id |sue-1|", SemgrexPattern.describe(sentence));

    // and the sent_id comment of a CoNLL-U file before that
    sentence.set(CoreAnnotations.CommentsAnnotation.class, Arrays.asList("# sent_id = sue-1"));
    assertEquals("the sentence at |# sent_id = sue-1|", SemgrexPattern.describe(sentence));
  }

  /**
   * A relation after a group is matched in the graph the group's branch used
   *<br>
   * The alternatives of a group may reach into different graphs, so which
   * graph the relation after it belongs to is not settled by reading the
   * pattern: it is whichever the alternative that matched was in.
   *<br>
   * The two graphs here share only "foo", and each has its own chain above
   * it, so a relation can only be satisfied in the graph which has it.
   */
  @Test
  public void testGroupCarriesItsBranchesGraph() {
    SemanticGraph basic = SemanticGraph.valueOf("[root-9 c> [bup-4 x> [bhead-2 x> foo-1]]]");
    SemanticGraph enhanced = SemanticGraph.valueOf("[root-9 c> [eup-5 y> [ehead-3 y> foo-1]]]");
    SemgrexGraphs graphs = SemgrexGraphs.of(basic, enhanced);

    // only the basic alternative can match, so the relation after the
    // group is asked of the basic graph: bup is there, eup is not
    assertTrue(SemgrexPattern.compile("{word:foo} ([<@basic {word:bhead} | <@enhanced {word:zzz}] < {word:bup})")
               .matcher(graphs).find());
    assertFalse(SemgrexPattern.compile("{word:foo} ([<@basic {word:bhead} | <@enhanced {word:zzz}] < {word:eup})")
                .matcher(graphs).find());

    // and with only the enhanced alternative able to match, the other way round
    assertTrue(SemgrexPattern.compile("{word:foo} ([<@basic {word:zzz} | <@enhanced {word:ehead}] < {word:eup})")
               .matcher(graphs).find());
    assertFalse(SemgrexPattern.compile("{word:foo} ([<@basic {word:zzz} | <@enhanced {word:ehead}] < {word:bup})")
                .matcher(graphs).find());
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
