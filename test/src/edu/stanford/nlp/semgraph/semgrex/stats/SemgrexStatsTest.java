package edu.stanford.nlp.semgraph.semgrex.stats;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.semgrex.SemgrexParseException;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;

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
    assertNull(chartRow(report, "TOTAL"));
    // all 8 edges are distinct triples
    assertEquals(8, countStat(stats, 0, 0).getDistinctKeys());
    assertEquals(8, chartRows(report, "count gov relation dep").size());
    assertChartRow(report, "ate", "nsubj", "Bill", "1", "12.50%");
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

  // ------------------------------------------------------------------
  // expanding the -conlluFile arguments
  // ------------------------------------------------------------------

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
}
