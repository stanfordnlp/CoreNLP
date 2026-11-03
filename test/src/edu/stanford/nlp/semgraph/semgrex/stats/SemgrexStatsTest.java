package edu.stanford.nlp.semgraph.semgrex.stats;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.CoNLLUReader;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.semgrex.SemgrexParseException;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

/**
 * @author John Bauer
 */
public class SemgrexStatsTest {
  List<String> CONLLU_EXTENSIONS = List.of(".conllu");

  /**
   * A tiny corpus with a couple of repeated relations, so that the
   * charts have something to add up.
   *<br>
   * "his" is the interesting word: it is the dependent of an nmod in
   * the first sentence and the dependent of an nsubj in the second.
   * That is the case a single pattern cannot ask about, since the
   * sentences where a word is possessed are not all of the sentences
   * containing that word.
   */
  String[] BATCH_PARSES = {
    "[ate-1 nsubj> Bill-2 obj> [cake-3 nmod> his-4]]",
    "[saw-1 nsubj> his-2]",
    "[ran-1 nsubj> Bill-2]",
    "[found-1 nsubj> Bill-2 obj> [book-3 nmod> her-4]]",
  };

  /**
   * Build a list of sentences with BasicDependenciesAnnotation
   */
  public List<CoreMap> buildSmallBatch() {
    List<CoreMap> sentences = new ArrayList<>();
    for (String parse : BATCH_PARSES) {
      SemanticGraph graph = SemanticGraph.valueOf(parse);
      CoreMap sentence = new ArrayCoreMap();
      sentence.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, graph);
      sentence.set(CoreAnnotations.TextAnnotation.class, parse);
      sentences.add(sentence);
    }
    return sentences;
  }

  /**
   * Parse a script and run every stage of it over the small batch.
   *<br>
   * This is the same loop SemgrexStats.main runs, minus the file reading.
   */
  public SemgrexStats runScript(String script) {
    List<CoreMap> sentences = buildSmallBatch();
    SemgrexStats stats = SemgrexStats.parse(script);
    for (int stage = 0; stage < stats.numStages(); ++stage) {
      stats.accumulate(stage, sentences);
    }
    return stats;
  }

  /**
   * The CountStat built by one command of a script, so that a test can
   * check the tally itself rather than its rendering
   */
  public static CountStat countStat(SemgrexStats stats, int stage, int index) {
    return (CountStat) stats.getStages().get(stage).getStats().get(index);
  }

  /**
   * Find the row of a chart which starts with this label, split into its fields.
   *<br>
   * Checking the fields rather than the whole line means these tests
   * survive a change to the column widths, which are computed from the
   * data and would otherwise make every test brittle.
   */
  public static List<String> chartRow(String report, String label) {
    for (String line : report.split("\\R")) {
      String[] pieces = line.trim().split("\\s+");
      if (pieces.length > 0 && pieces[0].equals(label)) {
        return Arrays.asList(pieces);
      }
    }
    return null;
  }

  /**
   * The labels of the data rows of the chart printed for one command, in order.
   *<br>
   * The chart is found by the "# command" line which introduces it and
   * ends at the blank line before the next one, so this works on a
   * report holding several charts.  Boilerplate is skipped by shape
   * rather than by exact text: comment lines, rules of dashes, the
   * header (always a chart's first row) and the TOTAL row (always its
   * last, and absent from the flat form).  A change to the column
   * widths or to the header wording therefore doesn't break every test
   * which checks what a chart contains.
   */
  public static List<String> chartRows(String report, String command) {
    List<String> rows = new ArrayList<>();
    boolean inChart = false;
    for (String line : report.split("\\R")) {
      String trimmed = line.trim();
      if (!inChart) {
        inChart = trimmed.equals("# " + command);
        continue;
      }
      if (trimmed.isEmpty()) {
        break;
      }
      // the "# rows:/columns:" note of a 2D chart, and the note
      // explaining a fall back to the flat form
      if (trimmed.startsWith("#")) {
        continue;
      }
      // a rule of dashes
      if (trimmed.matches("[-\\s]+")) {
        continue;
      }
      rows.add(trimmed.split("\\s+")[0]);
    }
    assertFalse("No chart for '" + command + "' in\n" + report, rows.isEmpty());

    // the header, which is labeled with the first key
    rows.remove(0);
    if (!rows.isEmpty() && rows.get(rows.size() - 1).equals("TOTAL")) {
      rows.remove(rows.size() - 1);
    }
    return rows;
  }

  /**
   * Assert that a chart holds exactly these rows, in this order
   *<br>
   * This is what keeps a spurious row from going unnoticed: the
   * per-row assertions below only check the rows a test thought to name.
   */
  public static void assertChartRows(String report, String command, String ... expected) {
    assertEquals("Rows of '" + command + "' in\n" + report,
                 Arrays.asList(expected), chartRows(report, command));
  }

  /**
   * As above, but naming the command by the stat which produced it
   *<br>
   * Worth using whenever a command's toString carries something a test
   * shouldn't have to know, such as how a restriction reports the size
   * of its set.
   */
  public static void assertChartRows(String report, SemgrexStat stat, String ... expected) {
    assertChartRows(report, stat.toString(), expected);
  }

  /**
   * Assert that a chart row exists and has exactly these fields after its label
   */
  public static void assertChartRow(String report, String label, String ... expected) {
    List<String> row = chartRow(report, label);
    assertNotNull("No row labeled '" + label + "' in\n" + report, row);
    List<String> values = row.subList(1, row.size());
    assertEquals("Row '" + label + "' in\n" + report, Arrays.asList(expected), values);
  }

  public static String lines(String ... lines) {
    StringBuilder sb = new StringBuilder();
    for (String line : lines) {
      sb.append(line).append(System.lineSeparator());
    }
    return sb.toString();
  }

  // ------------------------------------------------------------------
  // script parsing
  // ------------------------------------------------------------------

  /**
   * A script is one command per line, with "pattern" opening a stage
   */
  @Test
  public void testSimpleScript() {
    SemgrexStats stats = SemgrexStats.parse(lines("pattern {}=word <=relation {}",
                                                  "count relation"));
    assertEquals(1, stats.numStages());
    assertEquals(1, stats.getStages().get(0).getStats().size());
    assertEquals("count", stats.getStages().get(0).getStats().get(0).name());
  }

  /**
   * Blank lines and comments carry no meaning, so a script may be laid out however it reads best
   */
  @Test
  public void testCommentsAndBlanks() {
    SemgrexStats stats = SemgrexStats.parse(lines("# what relations are used?",
                                                  "",
                                                  "pattern {}=word <=relation {}",
                                                  "",
                                                  "count relation",
                                                  "",
                                                  "# and by which words?",
                                                  "count word relation",
                                                  ""));
    assertEquals(1, stats.numStages());
    assertEquals(2, stats.getStages().get(0).getStats().size());
  }

  /**
   * A pattern may contain #, the empty node attribute, without being
   * mistaken for a comment
   *<br>
   * Note that # is an attribute rather than a node of its own, so an
   * empty node is written {#} and a pattern can never actually begin
   * with a #.  A line which does is always a comment.
   */
  @Test
  public void testEmptyNodePattern() {
    SemgrexStats stats = SemgrexStats.parse(lines("# a comment, which is discarded",
                                                  "pattern {#} >=relation {}=word",
                                                  "count relation"));
    assertEquals(1, stats.numStages());

    // the root attribute is written the same way
    SemgrexStats.parse(lines("pattern {$} >=relation {}=word",
                             "count relation"));
  }

  /**
   * Every stage needs its "pattern" line, and every pattern needs at least one command
   */
  @Test
  public void testBrokenScript() {
    // a command with no pattern before it
    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("count relation")));

    // a bare pattern, without the keyword, is read as an unknown command
    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("{}=word <=relation {}",
                               "count relation")));

    // a pattern with nothing after it
    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("pattern {}=word <=relation {}")));

    // ... including when a second pattern follows it
    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("pattern {}=word <=relation {}",
                               "pattern {}=word <nmod {}",
                               "count word")));

    // a pattern keyword with no pattern
    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("pattern",
                               "count relation")));

    // an unknown command
    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("pattern {}=word <=relation {}",
                               "mean relation")));

    // nothing at all
    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("# only a comment", "")));
  }

  // ------------------------------------------------------------------
  // key validation
  // ------------------------------------------------------------------

  /**
   * Counting a name which is not in the pattern is a parse error, not a chart full of unbound rows
   */
  @Test
  public void testBrokenCount() {
    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("pattern {}=word <=relation {}",
                               "count reln")));

    // count with no keys at all
    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("pattern {}=word <=relation {}",
                               "count")));
  }

  /**
   * A name which is ambiguous between node, regex, and edge would
   * resolve to whichever getKey happens to check first, so it is
   * rejected the same way ::uniq rejects it
   */
  @Test
  public void testOverlappingCount() {
    // node name and regex name overlap
    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("pattern {word:__#1%foo}=foo <=relation {}",
                               "count foo")));

    // node name and edge name overlap
    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("pattern {}=foo <=foo {}",
                               "count foo")));
  }

  /**
   * Nodes, edges, and regex groups are all countable
   */
  @Test
  public void testCountableNames() {
    SemgrexStats.parse(lines("pattern {}=word <=relation {}", "count word"));
    SemgrexStats.parse(lines("pattern {}=word <=relation {}", "count relation"));
    SemgrexStats.parse(lines("pattern {word:__#1%text} <=relation {}", "count text"));
  }

  /**
   * The options a count takes are checked when the script is parsed
   */
  @Test
  public void testBrokenCountOptions() {
    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("pattern {}=word <=relation {}",
                               "count -maxColumn 4 word")));

    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("pattern {}=word <=relation {}",
                               "count -maxColumns four word")));

    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("pattern {}=word <=relation {}",
                               "count -maxColumns 0 word")));

    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("pattern {}=word <=relation {}",
                               "count -maxColumns word")));
  }

  // ------------------------------------------------------------------
  // counting
  // ------------------------------------------------------------------

  /**
   * A single key gives a ranked list of the values that key took
   *<br>
   * The batch has 4 nsubj, 2 nmod, and 2 obj edges.
   */
  @Test
  public void testCountOneKey() {
    SemgrexStats stats = runScript(lines("pattern {}=word <=relation {}",
                                         "count relation"));
    String report = stats.report();
    // exactly these three rows, so a fourth relation appearing is a failure
    assertChartRows(report, "count relation", "nsubj", "nmod", "obj");
    assertChartRow(report, "nsubj", "4", "50.00%");
    assertChartRow(report, "nmod", "2", "25.00%");
    assertChartRow(report, "obj", "2", "25.00%");
    assertChartRow(report, "TOTAL", "8");

    // and the same check against the tally rather than its rendering,
    // which would catch a row the formatter dropped on the floor
    assertEquals(3, countStat(stats, 0, 0).getDistinctKeys());
    assertEquals(8, countStat(stats, 0, 0).getTotalMatches());
  }

  /**
   * Rows are ordered by count descending, ties broken alphabetically
   */
  @Test
  public void testCountOrdering() {
    String report = runScript(lines("pattern {}=word <=relation {}",
                                    "count relation")).report();
    // nsubj is the most common; nmod and obj are tied at 2 and so sort alphabetically
    assertChartRows(report, "count relation", "nsubj", "nmod", "obj");
  }

  /**
   * The exact rendering of a one key chart
   *<br>
   * Unlike the other tests here this one is deliberately brittle: the
   * chart is the thing the tool produces, so a change to it should be
   * a deliberate change to this test rather than something that slips
   * through.
   */
  @Test
  public void testCountRendering() {
    String report = runScript(lines("pattern {}=word <=relation {}",
                                    "count relation")).report();
    String expected = lines("# count relation",
                            "relation  count     pct",
                            "--------  -----  ------",
                            "nsubj         4  50.00%",
                            "nmod          2  25.00%",
                            "obj           2  25.00%",
                            "--------  -----  ------",
                            "TOTAL         8");
    assertEquals(expected, report);
  }

  /**
   * Two keys give a contingency table with marginals
   */
  @Test
  public void testCountTwoKeys() {
    SemgrexStats stats = runScript(lines("pattern {}=word <=relation {}",
                                         "count word relation"));
    String report = stats.report();
    assertChartRows(report, "count word relation", "Bill", "his", "book", "cake", "her");
    // the header row names the columns, in column total order: nsubj,
    // then nmod and obj tied and so alphabetical
    assertChartRow(report, "word", "nsubj", "nmod", "obj", "TOTAL");
    assertChartRow(report, "Bill", "3", "0", "0", "3");
    assertChartRow(report, "his", "1", "1", "0", "2");
    assertChartRow(report, "her", "0", "1", "0", "1");
    assertChartRow(report, "cake", "0", "0", "1", "1");
    assertChartRow(report, "book", "0", "0", "1", "1");
    assertChartRow(report, "TOTAL", "4", "2", "2", "8");

    // 6 of the 15 cells are nonzero, so a spurious cell fails here even
    // if it landed in a row and column which already existed
    assertEquals(6, countStat(stats, 0, 0).getDistinctKeys());
  }

  /**
   * More distinct values than maxColumns falls back to the flat listing
   */
  @Test
  public void testMaxColumns() {
    String wide = runScript(lines("pattern {}=word <=relation {}",
                                  "count -maxColumns 2 word relation")).report();
    // the flat form has no TOTAL row, and one line per nonzero cell
    assertNull(chartRow(wide, "TOTAL"));
    assertChartRows(wide, "count -maxColumns 2 word relation",
                    "Bill", "book", "cake", "her", "his", "his");
    assertChartRow(wide, "Bill", "nsubj", "3", "37.50%");

    // one column is under the limit, so the chart is drawn
    String narrow = runScript(lines("pattern {}=word <nmod=relation {}",
                                    "count -maxColumns 2 word relation")).report();
    assertChartRows(narrow, "count -maxColumns 2 word relation", "her", "his");
    assertChartRow(narrow, "word", "nmod", "TOTAL");
    assertChartRow(narrow, "TOTAL", "2", "2");
  }

  /**
   * -flat asks for the tuple listing even when a chart would fit
   */
  @Test
  public void testFlat() {
    String report = runScript(lines("pattern {}=word <=relation {}",
                                    "count -flat word relation")).report();
    assertNull(chartRow(report, "TOTAL"));
    assertChartRows(report, "count -flat word relation",
                    "Bill", "book", "cake", "her", "his", "his");
    assertChartRow(report, "Bill", "nsubj", "3", "37.50%");
    assertChartRow(report, "his", "nmod", "1", "12.50%");
  }

  /**
   * Three keys are always flat, since n dimensional ascii art is not a solved problem
   */
  @Test
  public void testThreeKeys() {
    SemgrexStats stats = runScript(lines("pattern {}=gov >=relation {}=dep",
                                         "count gov relation dep"));
    String report = stats.report();

    // the last key is the columns; the keys before it nest leftward, so
    // the first groups the rows into blocks
    assertTrue(report.contains("# blocks: gov, rows: relation, columns: dep"));

    // the header names the columns, in column total order.  they are
    // shared by every block, which is what makes blocks comparable
    assertChartRow(report, "gov", "Bill", "his", "book", "cake", "her", "TOTAL");

    // a block's label sits on a line of its own with its rows indented
    // beneath it, so the label line has no counts on it
    assertChartRow(report, "ate");
    assertChartRow(report, "nsubj", "1", "0", "0", "0", "0", "1");

    // and the grand total is still every edge
    assertEquals(8, countStat(stats, 0, 0).getTotalMatches());
    assertChartRow(report, "TOTAL", "3", "2", "1", "1", "1", "8");
  }

  /**
   * -flat still gives the tuple listing for three keys
   */
  @Test
  public void testThreeKeysFlat() {
    SemgrexStats stats = runScript(lines("pattern {}=gov >=relation {}=dep",
                                         "count -flat gov relation dep"));
    String report = stats.report();
    assertNull(chartRow(report, "TOTAL"));
    // all 8 edges are distinct triples
    assertEquals(8, countStat(stats, 0, 0).getDistinctKeys());
    assertEquals(8, chartRows(report, "count -flat gov relation dep").size());
    assertChartRow(report, "ate", "nsubj", "Bill", "1", "12.50%");
  }

  /**
   * Blocks are ragged: each shows only the rows which occur in it
   *<br>
   * Padding every block to a common row set would let you scan a fixed
   * row position down the chart, but for the data this is useful on the
   * padding is nearly all zeros, since a governor has its own dependents
   * and nobody else's.
   */
  @Test
  public void testBlocksAreRagged() {
    SemgrexStats stats = runScript(lines("pattern {}=gov >=relation {}=dep",
                                         "count gov relation dep"));
    List<String> labels = new ArrayList<>();
    for (String line : stats.report().split("\\R")) {
      String trimmed = line.trim();
      if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.matches("[-\\s]+")) {
        labels.add(trimmed.split("\\s+")[0]);
      }
    }
    assertEquals("gov", labels.get(0));
    assertEquals("TOTAL", labels.get(labels.size() - 1));

    // six governors, so six blocks and six subtotals
    assertEquals(6, Collections.frequency(labels, "subtotal"));

    // "ate" governs two relations and "saw" one, so the chart comes to
    // 1 header + 6 labels + 8 rows + 6 subtotals + 1 total
    assertEquals(1 + 6 + 8 + 6 + 1, labels.size());
  }

  /**
   * A fourth key is past what a chart can show, so it falls back to the flat listing
   */
  @Test
  public void testFourKeys() {
    SemgrexStats stats = runScript(lines("pattern {}=gov >=relation {}=dep",
                                         "count gov relation dep gov"));
    String report = stats.report();
    assertFalse(report.contains("# blocks:"));
    assertNull(chartRow(report, "TOTAL"));
  }

  /**
   * A key which was not bound in a match is counted as unbound rather than dropped
   */
  @Test
  public void testUnboundKey() {
    String report = runScript(lines("pattern {}=word ?>nmod {}=poss",
                                    "count poss")).report();
    // all 12 nodes match; only cake and book have an nmod child
    assertChartRows(report, "count poss", CountStat.UNBOUND, "her", "his");
    assertChartRow(report, CountStat.UNBOUND, "10", "83.33%");
    assertChartRow(report, "his", "1", "8.33%");
    assertChartRow(report, "her", "1", "8.33%");
  }

  /**
   * Several commands in one stage share a single pass over the corpus
   */
  @Test
  public void testMultipleCommands() {
    String report = runScript(lines("pattern {}=word <=relation {}",
                                    "count relation",
                                    "count word")).report();
    // each command gets its own chart out of the one pass
    assertChartRows(report, "count relation", "nsubj", "nmod", "obj");
    assertChartRows(report, "count word", "Bill", "his", "book", "cake", "her");
    assertChartRow(report, "nsubj", "4", "50.00%");
    assertChartRow(report, "Bill", "3", "37.50%");
  }

  // ------------------------------------------------------------------
  // collect and restrict
  // ------------------------------------------------------------------

  /**
   * The two stage question: which relations do the possessed words take part in?
   *<br>
   * "his" is collected in the first stage and keeps its nsubj from the
   * second sentence, which the first pattern never saw.  "Bill" is
   * never possessed and so does not appear at all.
   */
  @Test
  public void testCollectAndRestrict() {
    SemgrexStats stats = runScript(lines("pattern {}=word <nmod {}",
                                         "collect word as possessed",
                                         "",
                                         "pattern {}=word <=relation {}",
                                         "count -restrict word=possessed word relation"));
    String report = stats.report();
    // Bill, cake and book are all excluded by the restriction
    assertChartRows(report, stats.getStages().get(1).getStats().get(0), "his", "her");
    assertChartRow(report, "his", "1", "1", "2");
    assertChartRow(report, "her", "1", "0", "1");
    assertNull(chartRow(report, "Bill"));
    assertNull(chartRow(report, "cake"));
    assertChartRow(report, "TOTAL", "2", "1", "3");
  }

  /**
   * The collected set is available for inspection, and reports its size
   */
  @Test
  public void testCollect() {
    List<CoreMap> sentences = buildSmallBatch();
    SemgrexStats stats = SemgrexStats.parse(lines("pattern {}=word <nmod {}",
                                                  "collect word as possessed"));
    stats.accumulate(0, sentences);

    CollectStat collect = (CollectStat) stats.getStages().get(0).getStats().get(0);
    assertEquals("possessed", collect.getName());
    assertEquals(2, collect.getValues().size());
    assertTrue(collect.getValues().contains("his"));
    assertTrue(collect.getValues().contains("her"));

    assertTrue(stats.report().contains("2 distinct values"));
  }

  /**
   * A restriction is checked when the script is parsed, not when it runs
   */
  @Test
  public void testBrokenRestrict() {
    // a set which was never collected
    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("pattern {}=word <=relation {}",
                               "count -restrict word=nope word")));

    // a set collected in this same stage is still being filled, so the
    // results would depend on the order the sentences arrived in
    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("pattern {}=word <=relation {}",
                               "collect word as seen",
                               "count -restrict word=seen word")));

    // restricting on a key which is not in this stage's pattern
    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("pattern {}=word <nmod {}",
                               "collect word as possessed",
                               "pattern {}=other <=relation {}",
                               "count -restrict word=possessed other")));

    // malformed KEY=SET
    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("pattern {}=word <nmod {}",
                               "collect word as possessed",
                               "pattern {}=word <=relation {}",
                               "count -restrict possessed word")));
  }

  /**
   * Collecting into a name twice would silently merge two different questions
   */
  @Test
  public void testDuplicateCollect() {
    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("pattern {}=word <nmod {}",
                               "collect word as possessed",
                               "pattern {}=word <nsubj {}",
                               "collect word as possessed")));
  }

  /**
   * collect takes exactly "collect KEY as NAME"
   */
  @Test
  public void testBrokenCollect() {
    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("pattern {}=word <nmod {}",
                               "collect word possessed")));

    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("pattern {}=word <nmod {}",
                               "collect word as")));

    // an unknown key, same as for count
    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("pattern {}=word <nmod {}",
                               "collect wrod as possessed")));
  }

   /**
   * Sets outlive the stage after the one which collected them, and two
   * restrictions on one key intersect
   *<br>
   * "his" is the only word which is both possessed and used as a
   * subject, so it is all that survives both restrictions.  Note that
   * the first set is used two stages later, not one.
   */
  @Test
  public void testChainedStages() {
    SemgrexStats stats = runScript(lines("pattern {}=word <nmod {}",
                                         "collect word as possessed",
                                         "",
                                         "pattern {}=word <nsubj {}",
                                         "collect word as subjects",
                                         "",
                                         "pattern {}=word <=relation {}",
                                         "count -restrict word=possessed -restrict word=subjects word relation"));
    String report = stats.report();
    // possessed is {his, her} and subjects is {Bill, his}
    assertChartRows(report, stats.getStages().get(2).getStats().get(0), "his");
    assertChartRow(report, "his", "1", "1", "2");
    assertNull(chartRow(report, "her"));
    assertNull(chartRow(report, "Bill"));
    assertChartRow(report, "TOTAL", "1", "1", "2");
  }

  /**
   * Each stage's report is labeled when there is more than one
   */
  @Test
  public void testStageLabels() {
    String report = runScript(lines("pattern {}=word <nmod {}",
                                    "collect word as possessed",
                                    "pattern {}=word <=relation {}",
                                    "count -restrict word=possessed word")).report();
    assertTrue(report.contains("stage 1"));
    assertTrue(report.contains("stage 2"));

    String single = runScript(lines("pattern {}=word <=relation {}",
                                    "count word")).report();
    assertFalse(single.contains("stage 1"));
  }

  /**
   * The comment lines directly above a pattern label its stage in the
   * report.  A comment separated from the pattern by a blank line, or one
   * among the commands, is left out.
   */
  @Test
  public void testStageCommentLabels() {
    SemgrexStats stats = runScript(lines("# a header for the whole script",
                                         "",
                                         "# possessed words",
                                         "#",
                                         "# from nmod",
                                         "pattern {}=word <nmod {}",
                                         "# not a label",
                                         "collect word as possessed",
                                         "",
                                         "pattern {}=word <=relation {}",
                                         "count -restrict word=possessed word"));
    assertEquals(Arrays.asList("possessed words", "", "from nmod"), stats.getStages().get(0).getLabels());
    assertEquals(Collections.emptyList(), stats.getStages().get(1).getLabels());

    List<String> report = Arrays.asList(stats.report().split("\\R"));
    assertEquals(Arrays.asList("## stage 1", "## possessed words", "##", "## from nmod", "# collect word as possessed"),
                 report.subList(0, 5));
    assertFalse(report.toString(), report.contains("## a header for the whole script"));
    assertFalse(report.toString(), report.contains("## not a label"));
    int stage2 = report.indexOf("## stage 2");
    assertTrue(report.toString(), report.get(stage2 + 1).startsWith("# count"));

    // a script of one stage has no stage header, but keeps its label
    report = Arrays.asList(runScript(lines("# relations",
                                           "pattern {}=word <=relation {}",
                                           "count relation")).report().split("\\R"));
    assertEquals(Arrays.asList("## relations", "# count relation"), report.subList(0, 2));
  }

  /**
   * -sort orders a key's values alphabetically rather than by count, as
   * rows, as columns, or in a flat listing.  The batch has 4 nsubj, 2
   * nmod, and 2 obj, so by count nsubj comes first.
   */
  @Test
  public void testCountSort() {
    String report = runScript(lines("pattern {}=word <=relation {}",
                                    "count relation")).report();
    assertChartRows(report, "count relation", "nsubj", "nmod", "obj");
    report = runScript(lines("pattern {}=word <=relation {}",
                             "count -sort relation relation")).report();
    assertChartRows(report, "count -sort relation relation", "nmod", "nsubj", "obj");

    // the columns are sorted, and the rows, which are not, keep their counts' order
    report = runScript(lines("pattern {}=word <=relation {}",
                             "count -sort relation word relation")).report();
    assertEquals(Arrays.asList("word", "nmod", "nsubj", "obj", "TOTAL"), chartRow(report, "word"));
    assertChartRows(report, "count -sort relation word relation", "Bill", "his", "book", "cake", "her");
    report = runScript(lines("pattern {}=word <=relation {}",
                             "count -sort word -sort relation word relation")).report();
    assertEquals(Arrays.asList("word", "nmod", "nsubj", "obj", "TOTAL"), chartRow(report, "word"));
    assertChartRows(report, "count -sort word -sort relation word relation", "Bill", "book", "cake", "her", "his");

    // a flat listing is ordered by the sorted key first, then by count
    report = runScript(lines("pattern {}=word <=relation {}",
                             "count -flat -sort relation relation word")).report();
    assertChartRows(report, "count -flat -sort relation relation word", "nmod", "nmod", "nsubj", "nsubj", "obj", "obj");
    assertEquals(Arrays.asList("nsubj", "Bill", "3", "37.50%"), chartRow(report, "nsubj"));

    for (String command : new String[] { "count -sort", "count -sort -flat relation", "count -sort word relation" }) {
      assertThrows(command, SemgrexParseException.class, () ->
        SemgrexStats.parse(lines("pattern {}=word <=relation {}", command)));
    }
  }

  /**
   * A chart with a right to left value starts each line with a left to
   * right mark and isolates each cell, so the columns stay in order.  A
   * chart with none has no controls, and -noBidi turns them off.
   */
  @Test
  public void testBidi() {
    List<CoreMap> corpus = sentences(new String[] { "[\u05e1\u05e4\u05e8-1 amod> \u05d8\u05d5\u05d1-2]", "[book-1 amod> good-2]" }, null);
    for (String command : new String[] { "count head", "count head edge", "count head dep edge", "count -flat head edge" }) {
      String report = runScript(lines("pattern {}=head >=edge {}=dep", command), corpus).report();
      for (String line : report.split("\\R")) {
        if (line.isEmpty() || line.startsWith("# " + command.split(" ")[0])) {
          continue;
        }
        assertTrue(command + ": " + line, line.startsWith("\u200E"));
      }
      // a cell is isolated; a block label is alone on its line after the mark
      assertTrue(command, report.contains("\u2068\u05e1\u05e4\u05e8\u2069") || report.contains("\u200E\u05e1\u05e4\u05e8" + System.lineSeparator()));

      String plain = runScript(lines("pattern {}=head >=edge {}=dep", command.replace("count", "count -noBidi")), corpus).report();
      assertFalse(command, plain.contains("\u200E") || plain.contains("\u2068"));
    }
    String ascii = runScript(lines("pattern {}=head >=edge {}", "count head"), sentences(new String[] { "[book-1 amod> good-2]" }, null)).report();
    assertFalse(ascii.contains("\u200E") || ascii.contains("\u2068"));
  }

  /**
   * -noTotal leaves out the TOTAL row and column and the subtotals, and
   * keeps everything else
   */
  @Test
  public void testCountNoTotal() {
    String report = runScript(lines("pattern {}=word <=relation {}",
                                    "count -noTotal relation")).report();
    assertChartRows(report, "count -noTotal relation", "nsubj", "nmod", "obj");
    assertChartRow(report, "nsubj", "4", "50.00%");
    assertFalse(report, report.contains("TOTAL"));
    // the command, the header, the rule under it, and the three rows: the rule above the total goes
    assertEquals(6, report.split("\\R").length);

    report = runScript(lines("pattern {}=word <=relation {}",
                             "count -noTotal word relation")).report();
    assertEquals(Arrays.asList("word", "nsubj", "nmod", "obj"), chartRow(report, "word"));
    assertChartRow(report, "Bill", "3", "0", "0");
    assertFalse(report, report.contains("TOTAL"));

    report = runScript(lines("pattern {}=gov >=relation {}=word",
                             "count -noTotal gov word relation")).report();
    assertFalse(report, report.contains("TOTAL"));
    assertFalse(report, report.contains("subtotal"));
    assertChartRow(report, "his", "0", "1", "0");

    // the flat listing has no totals to leave out
    String flat = lines("pattern {}=word <=relation {}", "count -flat word relation");
    assertEquals(runScript(flat).report().replace("# count -flat", "# count -flat -noTotal"),
                 runScript(flat.replace("-flat", "-flat -noTotal")).report());
  }

  /**
   * Leaving the totals out of a whole run gives the charts count -noTotal
   * gives, but for the command lines, in text and in LaTeX, and a chart
   * which already leaves them out is the same either way
   */
  @Test
  public void testRunNoTotal() {
    String[] commands = { "count relation", "count word relation", "count gov word relation", "count -flat word relation" };
    for (String command : commands) {
      String pattern = command.startsWith("count gov") ? "pattern {}=gov >=relation {}=word" : "pattern {}=word <=relation {}";
      String script = lines(pattern, command);
      String noTotalScript = lines(pattern, command.replace("count", "count -noTotal"));
      for (SemgrexStats.Format format : SemgrexStats.Format.values()) {
        SemgrexStats.Presentation run = new SemgrexStats.Presentation(format, false);
        String expected = runScript(noTotalScript).report(format).replace(" -noTotal", "");
        assertEquals(command + " " + format, expected, runScript(script).report(run));
        assertEquals(command + " " + format, runScript(noTotalScript).report(format), runScript(noTotalScript).report(run));
      }
    }

    // with the totals, a presentation is the same as the format alone
    SemgrexStats stats = runScript(lines("pattern {}=word <=relation {}", "count word relation"));
    assertEquals(stats.report(), stats.report(SemgrexStats.Presentation.DEFAULT));
    assertTrue(stats.report(), stats.report().contains("TOTAL"));
  }

  /** The characters LaTeX treats specially come out as written */
  @Test
  public void testEscapeLatex() {
    assertEquals("UD\\_English-EWT", Chart.escapeLatex("UD_English-EWT"));
    assertEquals("50.00\\%", Chart.escapeLatex("50.00%"));
    assertEquals("\\$corpus \\& \\# \\{x\\}", Chart.escapeLatex("$corpus & # {x}"));
    assertEquals("a\\textbackslash{}b\\textasciitilde{}c\\textasciicircum{}d", Chart.escapeLatex("a\\b~c^d"));
    assertEquals("acl:relcl", Chart.escapeLatex("acl:relcl"));
  }

  /** The formats as they are named on the command line */
  @Test
  public void testFormatFromName() {
    assertEquals(SemgrexStats.Format.TEXT, SemgrexStats.Format.fromName("text"));
    assertEquals(SemgrexStats.Format.LATEX, SemgrexStats.Format.fromName("latex"));
    assertEquals(SemgrexStats.Format.LATEX_FLOAT, SemgrexStats.Format.fromName("latexFloat"));
    assertEquals(SemgrexStats.Format.LATEX_FLOAT, SemgrexStats.Format.fromName("LATEXFLOAT"));
    assertThrows(IllegalArgumentException.class, () -> SemgrexStats.Format.fromName("html"));
  }

  /**
   * In LaTeX, a chart is a booktabs tabular, and its notes and the
   * command are % comments
   */
  @Test
  public void testCountLatex() {
    List<String> report = Arrays.asList(runScript(lines("pattern {}=word <=relation {}",
                                                        "count relation")).report(SemgrexStats.Format.LATEX).split("\\R"));
    assertEquals(Arrays.asList("% count relation",
                               "\\begin{tabular}{lrr}",
                               "\\toprule",
                               "relation & count & pct \\\\",
                               "\\midrule",
                               "nsubj & 4 & 50.00\\% \\\\",
                               "nmod & 2 & 25.00\\% \\\\",
                               "obj & 2 & 25.00\\% \\\\",
                               "\\midrule",
                               "TOTAL & 8 &  \\\\",
                               "\\bottomrule",
                               "\\end{tabular}"),
                 report);

    // a grid without its totals
    report = Arrays.asList(runScript(lines("pattern {}=word <=relation {}",
                                           "count -noTotal word relation")).report(SemgrexStats.Format.LATEX).split("\\R"));
    assertEquals("% rows: word, columns: relation", report.get(1));
    assertEquals("\\begin{tabular}{lrrr}", report.get(2));
    assertEquals("word & nsubj & nmod & obj \\\\", report.get(4));
    assertTrue(report.toString(), report.contains("Bill & 3 & 0 & 0 \\\\"));
    assertFalse(report.toString(), report.toString().contains("TOTAL"));

    // blocks
    String blocks = runScript(lines("pattern {}=gov >=relation {}=word",
                                    "count gov word relation")).report(SemgrexStats.Format.LATEX);
    assertTrue(blocks, blocks.contains("\\multicolumn{5}{l}{ate} \\\\"));
    assertTrue(blocks, blocks.contains("\\quad Bill & 1 & 0 & 0 & 1 \\\\"));
    assertTrue(blocks, blocks.contains("\\addlinespace"));

    // the same script still writes text by default
    SemgrexStats stats = runScript(lines("pattern {}=word <=relation {}", "count relation"));
    assertEquals(stats.report(), stats.report(SemgrexStats.Format.TEXT));
    assertTrue(stats.report(), stats.report().startsWith("# count relation"));
  }

  /**
   * In a LaTeX float, the tabular is in a table, captioned with the label
   * of the stage, or the command without one.  The stage lines are %
   * comments, so the whole report is LaTeX.
   */
  @Test
  public void testCountLatexFloat() {
    String report = runScript(lines("# relations of the words",
                                    "pattern {}=word <=relation {}",
                                    "count relation",
                                    "",
                                    "pattern {}=word <nsubj {}",
                                    "count word")).report(SemgrexStats.Format.LATEX_FLOAT);
    List<String> lines = Arrays.asList(report.split("\\R"));
    assertEquals("%% stage 1", lines.get(0));
    assertEquals("%% relations of the words", lines.get(1));
    assertEquals("% count relation", lines.get(2));
    assertEquals("\\begin{table}[htbp]", lines.get(3));
    assertTrue(report, report.contains("\\caption{relations of the words}"));
    assertTrue(report, report.contains("%% stage 2"));
    // the second stage has no label, so its caption is its command
    assertTrue(report, report.contains("\\caption{count word}"));
    for (String line : lines) {
      assertFalse(line, line.startsWith("#"));
    }
  }

  /**
   * The commands with no LaTeX form of their own, and a count with
   * nothing to count, are written as % comments, so the report is still
   * LaTeX
   */
  @Test
  public void testLatexComments() {
    String script = lines("pattern {}=word <=relation {}",
                          "collect relation as relations",
                          "sample -count 2 relation",
                          "",
                          "pattern {}=word <xcomp {}",
                          "count word");
    SemgrexStats stats = runScript(script);
    List<String> textLines = Arrays.asList(stats.report().split("\\R"));
    List<String> latexLines = Arrays.asList(stats.report(SemgrexStats.Format.LATEX).split("\\R"));
    assertEquals(textLines.size(), latexLines.size());
    for (int i = 0; i < textLines.size(); ++i) {
      String line = textLines.get(i);
      // the leading # markers become %, every other line is commented
      // out, a blank line inside a command's report is a bare %, and the
      // blank lines between commands and between stages stay blank
      if (line.isEmpty()) {
        assertTrue(latexLines.get(i), latexLines.get(i).isEmpty() || latexLines.get(i).equals("%"));
      } else if (line.startsWith("#")) {
        assertEquals(line.replaceFirst("^##?", line.startsWith("##") ? "%%" : "%"), latexLines.get(i));
      } else {
        assertEquals("% " + line, latexLines.get(i));
      }
    }
    // sample separates its combinations with blank lines
    assertTrue(latexLines.toString(), latexLines.contains("%"));
    assertTrue(latexLines.toString(), latexLines.contains("% No matches, nothing to count."));
  }

  /** Sentences whose objects are the same clitic spelled three ways, and one other */
  private static List<CoreMap> cliticSentences() {
    List<CoreMap> sentences = new ArrayList<>();
    for (String parse : new String[] { "[vi-1 obj> lo-2]", "[vi-1 obj> Lo-2]", "[vi-1 obj> LO-2]", "[vi-1 iobj> la-2]" }) {
      CoreMap sentence = new ArrayCoreMap();
      sentence.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, SemanticGraph.valueOf(parse));
      sentence.set(CoreAnnotations.TextAnnotation.class, parse);
      sentences.add(sentence);
    }
    return sentences;
  }

  /** -lower counts the values of a key lowercased, and only that key */
  @Test
  public void testCountLower() {
    String report = runScript(lines("pattern {}=word <=relation {}",
                                    "count word"), cliticSentences()).report();
    assertChartRows(report, "count word", "LO", "Lo", "la", "lo");

    SemgrexStats stats = runScript(lines("pattern {}=word <=relation {}",
                                         "count -lower word word"), cliticSentences());
    report = stats.report();
    assertChartRows(report, "count -lower word word", "lo", "la");
    assertChartRow(report, "lo", "3", "75.00%");
    assertChartRow(report, "la", "1", "25.00%");

    // the relation, which is not lowered, keeps its own values
    report = runScript(lines("pattern {}=word <=relation {}",
                             "count -lower word word relation"), cliticSentences()).report();
    assertChartRow(report, "lo", "3", "0", "3");
    assertChartRow(report, "la", "0", "1", "1");
  }

  /**
   * A set collected with -lower holds the lowercased values, which a count
   * with -lower on the same key restricts against
   */
  @Test
  public void testCollectLower() {
    String script = lines("pattern {}=word <obj {}",
                          "collect -lower word word as objects",
                          "",
                          "pattern {}=word <=relation {}",
                          "count -lower word -restrict word=objects word");
    SemgrexStats stats = runScript(script, cliticSentences());
    assertEquals(new HashSet<>(Arrays.asList("lo")),
                 ((CollectStat) stats.getStages().get(0).getStats().get(0)).getValues());
    assertEquals("collect -lower word word as objects", stats.getStages().get(0).getStats().get(0).toString());
    String report = stats.report();
    assertChartRows(report, stats.getStages().get(1).getStats().get(0), "lo");
    assertChartRow(report, "lo", "3", "100.00%");

    // without -lower, only the spelling which is in the set gets through
    report = runScript(script.replace("count -lower word", "count"), cliticSentences()).report();
    assertChartRow(report, "lo", "1", "100.00%");
    assertNull(chartRow(report, "Lo"));
  }

  /** -lower has to name a key which the command uses */
  @Test
  public void testLowerErrors() {
    for (String command : new String[] { "count -lower", "count -lower relation word", "count -lower nothing word",
                                         "collect -lower relation word as objects", "collect -lower" }) {
      assertThrows(command, SemgrexParseException.class, () ->
        SemgrexStats.parse(lines("pattern {}=word <=relation {}", command)));
    }
    // a key used only by a restriction can be lowered
    SemgrexStats.parse(lines("pattern {}=word <obj {}",
                             "collect -lower word word as objects",
                             "pattern {}=word <=relation {}",
                             "count -lower word -restrict word=objects relation"));
  }

  /** Sentences from graphs, each with a sent_id comment if one is given */
  private static List<CoreMap> sentences(String[] parses, String[] ids) {
    List<CoreMap> sentences = new ArrayList<>();
    for (int i = 0; i < parses.length; ++i) {
      CoreMap sentence = new ArrayCoreMap();
      sentence.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, SemanticGraph.valueOf(parses[i]));
      if (ids != null && ids[i] != null) {
        sentence.set(CoreAnnotations.CommentsAnnotation.class, Collections.singletonList("# sent_id = " + ids[i]));
      }
      sentences.add(sentence);
    }
    return sentences;
  }

  private static SampleStat sampleStat(SemgrexStats stats) {
    return (SampleStat) stats.getStages().get(0).getStats().get(0);
  }

  /**
   * sample keeps the sentences of each combination in corpus order, counts
   * a sentence with two matches once, and marks both matches
   */
  @Test
  public void testSample() {
    List<CoreMap> corpus = sentences(new String[] { "[vi-1 obj> lo-2]", "[vi-1 obj> lo-2 iobj> lo-3]", "[vi-1 iobj> la-2]", "[vi-1 obj> Lo-2]" },
                                     new String[] { "s1", "s2", null, "s4" });
    SemgrexStats stats = runScript(lines("pattern {}=word <=relation {}",
                                         "sample word"), corpus);
    SampleStat sample = sampleStat(stats);
    assertEquals(3, sample.getMatches(Arrays.asList("lo")));
    assertEquals(Arrays.asList("s1", "s2"), sample.getSampleIds(Arrays.asList("lo")));
    // a sentence with no sent_id is named by its place in the corpus
    assertEquals(Arrays.asList("sentence 3"), sample.getSampleIds(Arrays.asList("la")));
    assertEquals(Arrays.asList("s4"), sample.getSampleIds(Arrays.asList("Lo")));
    assertEquals(Collections.emptyList(), sample.getSampleIds(Arrays.asList("le")));

    List<String> report = Arrays.asList(stats.report().split("\\R"));
    assertEquals(Arrays.asList("# sample word",
                               "word=lo: 3 matches in 2 sentences",
                               "  s1  vi [[lo]]",
                               "  s2  vi [[lo]] [[lo]]",
                               "",
                               "word=Lo: 1 match in 1 sentence",
                               "  s4  vi [[Lo]]",
                               "",
                               "word=la: 1 match in 1 sentence",
                               "  sentence 3  vi [[la]]"),
                 report);

    // -lower puts lo and Lo together, and the relation splits them again
    stats = runScript(lines("pattern {}=word <=relation {}",
                            "sample -lower word word relation"), corpus);
    sample = sampleStat(stats);
    assertEquals(Arrays.asList("s1", "s2", "s4"), sample.getSampleIds(Arrays.asList("lo", "obj")));
    assertEquals(Arrays.asList("s2"), sample.getSampleIds(Arrays.asList("lo", "iobj")));
    assertTrue(stats.report().contains("word=lo relation=obj: 3 matches in 3 sentences"));
  }

  /**
   * With more sentences than -count, sample chooses that many, the same
   * ones each time, and prints them in corpus order
   */
  @Test
  public void testSampleChoice() {
    String[] parses = new String[100];
    String[] ids = new String[100];
    for (int i = 0; i < parses.length; ++i) {
      parses[i] = "[vi-1 obj> lo-2]";
      ids[i] = String.format("s%03d", i);
    }
    List<CoreMap> corpus = sentences(parses, ids);
    String script = lines("pattern {}=word <=relation {}",
                          "sample -count 5 word");
    List<String> chosen = sampleStat(runScript(script, corpus)).getSampleIds(Arrays.asList("lo"));
    assertEquals(5, chosen.size());
    List<String> sorted = new ArrayList<>(chosen);
    Collections.sort(sorted);
    assertEquals(sorted, chosen);
    assertEquals(chosen, sampleStat(runScript(script, corpus)).getSampleIds(Arrays.asList("lo")));
    // not simply the first five
    assertNotEquals(Arrays.asList("s000", "s001", "s002", "s003", "s004"), chosen);

    // another seed, another sample
    List<String> other = sampleStat(runScript(script.replace("-count 5", "-count 5 -seed 99"), corpus)).getSampleIds(Arrays.asList("lo"));
    assertEquals(5, other.size());
    assertNotEquals(chosen, other);
    assertEquals(100, sampleStat(runScript(script, corpus)).getMatches(Arrays.asList("lo")));
  }

  /**
   * With -conllu, each sentence is written in CoNLL-U, with its
   * combination and its marked words as comments
   */
  @Test
  public void testSampleConllu() {
    List<CoreMap> corpus = sentences(new String[] { "[vi-1 obj> lo-2 iobj> la-3]" }, new String[] { "s1" });
    String report = runScript(lines("pattern {}=word <obj {}",
                                    "sample -conllu word"), corpus).report();
    List<String> lines = Arrays.asList(report.split("\\R"));
    assertEquals(Arrays.asList("# sample -conllu word",
                               "# sent_id = s1",
                               "# sample = word=lo: 1 match in 1 sentence",
                               "# highlight tokens = 2"),
                 lines.subList(0, 4));
    assertTrue(lines.get(4), lines.get(4).startsWith("1\tvi\t"));
  }

  /** The options of sample are checked when the script is read */
  @Test
  public void testSampleErrors() {
    for (String command : new String[] { "sample", "sample -count 0 word", "sample -count many word", "sample -count",
                                         "sample -seed x word", "sample -sort word", "sample -lower relation word", "sample nothing" }) {
      assertThrows(command, SemgrexParseException.class, () ->
        SemgrexStats.parse(lines("pattern {}=word <=relation {}", command)));
    }
    assertEquals("sample -count 3 -seed 7 -conllu -lower word word relation",
                 SemgrexStats.parse(lines("pattern {}=word <=relation {}",
                                          "sample -conllu -count 3 -lower word -seed 7 word relation")).getStages().get(0).getStats().get(0).toString());
  }

  // ------------------------------------------------------------------
  // expanding the -conlluFile arguments
  // ------------------------------------------------------------------

  /**
   * $corpus is the corpus each sentence came from, which any command can
   * use as a key, so one chart can compare two treebanks, with 0 where
   * one has a value the other has not
   */
  @Test
  public void testCorpusKey() {
    List<CoreMap> first = sentences(new String[] { "[vi-1 obj> lo-2]", "[vi-1 obj> la-2]" }, null);
    List<CoreMap> second = sentences(new String[] { "[vi-1 obj> lo-2]", "[vi-1 iobj> le-2]" }, null);
    SemgrexStats.setCorpus(first, "UD_Spanish-AnCora");
    SemgrexStats.setCorpus(second, "UD_Spanish-GSD");
    List<CoreMap> both = new ArrayList<>(first);
    both.addAll(second);

    SemgrexStats stats = runScript(lines("pattern {}=word <=relation {}",
                                         "count word $corpus",
                                         "sample relation $corpus"), both);
    String report = stats.report();
    assertEquals(Arrays.asList("word", "UD_Spanish-AnCora", "UD_Spanish-GSD", "TOTAL"), chartRow(report, "word"));
    assertChartRow(report, "lo", "1", "1", "2");
    assertChartRow(report, "la", "1", "0", "1");
    assertChartRow(report, "le", "0", "1", "1");
    SampleStat sample = (SampleStat) stats.getStages().get(0).getStats().get(1);
    assertEquals(1, sample.getMatches(Arrays.asList("iobj", "UD_Spanish-GSD")));

    // a set can be collected per corpus, and a sentence with no corpus is unbound
    stats = runScript(lines("pattern {}=word <=relation {}",
                            "collect $corpus as corpora",
                            "count $corpus"), sentences(new String[] { "[vi-1 obj> lo-2]" }, null));
    assertChartRows(stats.report(), "count $corpus", CountStat.UNBOUND);
    stats = runScript(lines("pattern {}=word <=relation {}",
                            "collect $corpus as corpora"), both);
    assertEquals(new HashSet<>(Arrays.asList("UD_Spanish-AnCora", "UD_Spanish-GSD")),
                 ((CollectStat) stats.getStages().get(0).getStats().get(0)).getValues());

    // it can be lowercased and sorted like any key, and is the only $ key there is
    SemgrexStats.parse(lines("pattern {}=word <=relation {}", "count -lower $corpus -sort $corpus $corpus word"));
    assertThrows(SemgrexParseException.class, () ->
      SemgrexStats.parse(lines("pattern {}=word <=relation {}", "count $treebank")));
  }

  /**
   * Each path names a corpus by its last component: every file of a
   * directory is in the directory's corpus, and a file named on its own
   * is in a corpus of its own name
   */
  @Test
  public void testExpandCorpora() throws IOException {
    File parent = Files.createTempDirectory("semgrexStats").toFile();
    parent.deleteOnExit();
    File treebank = new File(parent, "UD_English-EWT");
    assertTrue(treebank.mkdir());
    treebank.deleteOnExit();
    for (String name : new String[] {"en_ewt-ud-train.conllu", "en_ewt-ud-dev.conllu"}) {
      File file = new File(treebank, name);
      file.createNewFile();
      file.deleteOnExit();
    }
    File single = new File(parent, "extra.conllu");
    single.createNewFile();
    single.deleteOnExit();

    List<Pair<File, String>> corpora = SemgrexStats.expandCorpora(List.of(treebank + File.separator, single.toString()), CONLLU_EXTENSIONS);
    assertEquals(3, corpora.size());
    assertEquals("en_ewt-ud-dev.conllu", corpora.get(0).first().getName());
    assertEquals("UD_English-EWT", corpora.get(0).second());
    assertEquals("UD_English-EWT", corpora.get(1).second());
    assertEquals("extra.conllu", corpora.get(2).second());

    // a path with no last component of its own is named by the directory it means
    assertEquals(new File("").getAbsoluteFile().getName(), SemgrexStats.corpusName(new File(".")));
  }

  /**
   * A directory contributes its .conllu files, sorted, and skips the
   * README and LICENSE which every UD treebank also contains
   */
  @Test
  public void testExpandDirectory() throws IOException {
    File dir = Files.createTempDirectory("semgrexStats").toFile();
    dir.deleteOnExit();
    for (String name : new String[] {"xx-ud-train.conllu", "xx-ud-dev.conllu",
                                     "README.md", "LICENSE.txt"}) {
      File file = new File(dir, name);
      file.createNewFile();
      file.deleteOnExit();
    }

    List<File> files = SemgrexStats.expandConlluFiles(List.of(dir.toString()), CONLLU_EXTENSIONS);
    assertEquals(2, files.size());
    assertEquals("xx-ud-dev.conllu", files.get(0).getName());
    assertEquals("xx-ud-train.conllu", files.get(1).getName());
  }

  /**
   * Extensions are matched in a fixed locale, not the default one
   *<br>
   * The Turkish locale maps i and I to a dotted and a dotless letter,
   * so a default locale conversion would case the filename and the
   * extension differently and the two would stop agreeing.  This is the
   * only test which touches the default locale, so it puts it back.
   */
  @Test
  public void testExtensionLocale() throws IOException {
    File dir = Files.createTempDirectory("semgrexStats").toFile();
    dir.deleteOnExit();
    for (String name : new String[] {"xx-ud-train.mini", "README.md"}) {
      File file = new File(dir, name);
      file.createNewFile();
      file.deleteOnExit();
    }

    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(new Locale("tr", "TR"));

      // the default locale would give ".m" plus a dotless i, which no
      // ascii filename can end with
      List<String> extensions = SemgrexStats.parseExtensions("MINI");
      assertEquals(List.of(".mini"), extensions);

      List<File> files = SemgrexStats.expandConlluFiles(List.of(dir.toString()), extensions);
      assertEquals(1, files.size());
      assertEquals("xx-ud-train.mini", files.get(0).getName());

      // and the other direction: an extension which was never passed
      // through parseExtensions, as the .conllu default is
      files = SemgrexStats.expandConlluFiles(List.of(new File(dir, "xx-ud-train.mini").toString()),
                                             List.of(".mini"));
      assertEquals(1, files.size());
    } finally {
      Locale.setDefault(previous);
    }
  }

  /**
   * Filenames may be repeated, comma separated, or semicolon separated
   */
  @Test
  public void testExpandFiles() throws IOException {
    File dir = Files.createTempDirectory("semgrexStats").toFile();
    dir.deleteOnExit();
    File train = new File(dir, "xx-ud-train.conllu");
    File dev = new File(dir, "xx-ud-dev.conllu");
    // a file named explicitly is used whatever it is called
    File odd = new File(dir, "extra.txt");
    for (File file : new File[] {train, dev, odd}) {
      file.createNewFile();
      file.deleteOnExit();
    }

    assertEquals(Arrays.asList(train, dev),
                 SemgrexStats.expandConlluFiles(List.of(train.toString(), dev.toString()), CONLLU_EXTENSIONS));
    assertEquals(Arrays.asList(train, dev),
                 SemgrexStats.expandConlluFiles(List.of(train + "," + dev), CONLLU_EXTENSIONS));
    assertEquals(Arrays.asList(train, dev),
                 SemgrexStats.expandConlluFiles(List.of(train + " ; " + dev), CONLLU_EXTENSIONS));
    assertEquals(Arrays.asList(odd),
                 SemgrexStats.expandConlluFiles(List.of(odd.toString()), CONLLU_EXTENSIONS));
  }

  /**
   * A path which is neither a file nor a directory is reported before any counting starts
   */
  @Test
  public void testExpandMissing() throws IOException {
    File dir = Files.createTempDirectory("semgrexStats").toFile();
    dir.deleteOnExit();

    // an empty directory has no conllu files in it
    assertThrows(IllegalArgumentException.class, () ->
                 SemgrexStats.expandConlluFiles(List.of(dir.toString()), CONLLU_EXTENSIONS));

    assertThrows(IllegalArgumentException.class, () ->
                 SemgrexStats.expandConlluFiles(List.of(new File(dir, "nope.conllu").toString()), CONLLU_EXTENSIONS));

    assertThrows(IllegalArgumentException.class, () ->
                 SemgrexStats.expandConlluFiles(List.of(""), CONLLU_EXTENSIONS));
  }

  // ------------------------------------------------------------------
  // sentences with more than one graph
  // ------------------------------------------------------------------

  /**
   * "Sue likes coffee and Bill tea", where the enhanced graph has an
   * empty word 5.1 for the elided "likes"
   */
  static final String ENHANCED_SENTENCE = String.join("\n",
    "# text = Sue likes coffee and Bill tea",
    "1\tSue\tSue\tPROPN\t_\t_\t2\tnsubj\t2:nsubj\t_",
    "2\tlikes\tlike\tVERB\t_\t_\t0\troot\t0:root\t_",
    "3\tcoffee\tcoffee\tNOUN\t_\t_\t2\tobj\t2:obj\t_",
    "4\tand\tand\tCCONJ\t_\t_\t5\tcc\t5.1:cc\t_",
    "5\tBill\tBill\tPROPN\t_\t_\t2\tconj\t5.1:nsubj\t_",
    "5.1\tlikes\tlike\tVERB\t_\t_\t_\t_\t2:conj:and\t_",
    "6\ttea\ttea\tNOUN\t_\t_\t5\torphan\t5.1:obj\t_",
    "",
    "");

  /** The sentence above, read from CoNLL-U the way SemgrexStats.main reads a file, with both of its graphs */
  public static List<CoreMap> readEnhancedSentence() throws IOException {
    File file = File.createTempFile("semgrexstats", ".conllu");
    file.deleteOnExit();
    Files.write(file.toPath(), ENHANCED_SENTENCE.getBytes(StandardCharsets.UTF_8));
    List<CoreMap> sentences = new ArrayList<>();
    for (Annotation doc : new CoNLLUReader().readCoNLLUFile(file.getPath())) {
      sentences.addAll(doc.get(CoreAnnotations.SentencesAnnotation.class));
    }
    assertEquals(1, sentences.size());
    assertNotNull(sentences.get(0).get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class));
    return sentences;
  }

  public static SemgrexStats runScript(String script, List<CoreMap> sentences) {
    SemgrexStats stats = SemgrexStats.parse(script);
    for (int stage = 0; stage < stats.numStages(); ++stage) {
      stats.accumulate(stage, sentences);
    }
    return stats;
  }

  /**
   * A pattern can count the relations of the enhanced graph, including
   * the ones to and from the empty word
   */
  @Test
  public void testEnhancedEdges() throws IOException {
    SemgrexStats stats = runScript(lines("pattern {}=gov >/.*/@enhanced=edge {}",
                                         "count edge"),
                                   readEnhancedSentence());
    CountStat count = countStat(stats, 0, 0);
    // nsubj and obj twice, once each from the empty likes
    assertEquals(6, count.getTotalMatches());
    assertEquals(4, count.getDistinctKeys());
    String report = stats.report();
    assertEquals("2", chartRow(report, "nsubj").get(1));
    assertEquals("2", chartRow(report, "obj").get(1));
    assertEquals("1", chartRow(report, "cc").get(1));
    assertEquals("1", chartRow(report, "conj:and").get(1));
    assertNull(chartRow(report, "orphan"));
  }

  /**
   * The same sentences counted without naming a graph see only the basic graph
   */
  @Test
  public void testBasicEdgesOfEnhancedSentence() throws IOException {
    SemgrexStats stats = runScript(lines("pattern {}=gov >/.*/=edge {}",
                                         "count edge"),
                                   readEnhancedSentence());
    CountStat count = countStat(stats, 0, 0);
    assertEquals(5, count.getTotalMatches());
    assertEquals(5, count.getDistinctKeys());
    assertNotNull(chartRow(stats.report(), "orphan"));
    assertNull(chartRow(stats.report(), "conj:and"));
  }

  /**
   * A match can start at the empty word, which is only in the enhanced graph.
   *<br>
   * {#} is not the way to ask for it: that is IndexedWord.NO_WORD, the
   * unaligned word of an alignment.  The empty word is the one with an
   * enhanced head and no basic head
   */
  @Test
  public void testEmptyWordMatch() throws IOException {
    SemgrexStats stats = runScript(lines("pattern {}=elided !< {} <@enhanced {}=gov",
                                         "count elided gov"),
                                   readEnhancedSentence());
    CountStat count = countStat(stats, 0, 0);
    assertEquals(1, count.getTotalMatches());
    assertEquals(1, count.getDistinctKeys());
  }

  /**
   * A first stage can collect words through the enhanced graph for a
   * second stage which counts their basic relations: the words whose
   * enhanced head is the empty "likes" are the ones left in the basic
   * graph without a verb of their own
   */
  @Test
  public void testCollectThroughEnhanced() throws IOException {
    SemgrexStats stats = runScript(lines("pattern {} !< {} <@enhanced {} >@enhanced {}=word",
                                         "collect word as elided",
                                         "pattern {}=word <=relation {}",
                                         "count -restrict word=elided relation"),
                                   readEnhancedSentence());
    CountStat count = countStat(stats, 1, 0);
    assertEquals(3, count.getTotalMatches());
    String report = stats.report();
    assertEquals("1", chartRow(report, "cc").get(1));
    assertEquals("1", chartRow(report, "conj").get(1));
    assertEquals("1", chartRow(report, "orphan").get(1));
  }
}
