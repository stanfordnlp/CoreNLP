package edu.stanford.nlp.semgraph.semgrex.stats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.semgraph.semgrex.SemgrexMatch;
import edu.stanford.nlp.semgraph.semgrex.SemgrexParseException;
import edu.stanford.nlp.semgraph.semgrex.SemgrexUtils;
import edu.stanford.nlp.util.CoreMap;

/**
 * Counts the values taken by the named nodes, edges, and regex groups of a semgrex match.
 *<br>
 * Script syntax:
 *<pre>
 * count [-flat] [-maxColumns N] [-noTotal] [-lower KEY ...] [-sort KEY ...] KEY [KEY ...]
 *</pre>
 * With one key, this produces a ranked list of the values that key took
 * and how often.  With two keys, it produces a contingency table of the
 * first key against the second, with marginals.  With three or more, it
 * falls back to a ranked flat listing of tuples, since n-dimensional
 * ASCII art is not a solved problem.
 *<br>
 * A pair of keys which each take many values would produce a chart
 * thousands of columns wide, so past {@code -maxColumns} distinct values
 * of the second key the flat listing is used instead.
 *<br>
 * {@code -lower KEY} counts the values of that key lowercased, so that
 * "Lo", "lo", and "LO" are one row.  It can be given for more than one
 * key, and applies to a {@code -restrict} on the same key as well.
 *<br>
 * {@code -noTotal} leaves out the TOTAL row and column, and the subtotal
 * rows of a chart with blocks, for a chart going into a paper.  The pct
 * column of a one key chart stays, as it is each row's share of the
 * total.  The run can leave the totals out of every chart instead, with
 * {@code -noTotal} on the command line.
 *<br>
 * How the chart is written, as text or in LaTeX, is up to the run, as
 * {@link SemgrexStats} describes.
 *<br>
 * The values of each key are ordered by how often they occur, most
 * first.  {@code -sort KEY} orders that key's values alphabetically
 * instead, so that the same query on two treebanks lays its rows or
 * columns out the same way.  It can be given for more than one key.  In
 * a listing of whole combinations, the sorted keys order it first, in
 * the order of the keys, and then how often each combination occurs.
 *
 * @author John Bauer
 */
public class CountStat implements SemgrexStat {
  /** Displayed for a key which was not bound in a particular match, eg under an optional relation */
  public static final String UNBOUND = "--";

  /** Beyond this many distinct values for the second key, a 2D chart is unreadable */
  public static final int DEFAULT_MAX_COLUMNS = 24;

  private static final String FLAT = "-flat";
  private static final String MAX_COLUMNS = "-maxColumns";

  /** width of the percentage column, wide enough for "100.00%" */
  private static final int PCT_WIDTH = 6;

  /** label of a block's summary row */
  private static final String SUBTOTAL = "subtotal";
  private static final String RESTRICT = "-restrict";
  private static final String SORT = "-sort";
  private static final String NO_TOTAL = "-noTotal";
  private static final String NO_BIDI = "-noBidi";

  private final List<String> keys;
  /** the keys whose values are lowercased */
  private final Set<String> lowered;
  /** the keys whose values are ordered alphabetically rather than by count */
  private final Set<String> sorted;
  private final Map<List<String>, Integer> counts = new HashMap<>();

  private final int maxColumns;
  private final boolean flat;
  /** whether the chart has its TOTAL row and column, and its subtotals */
  private final boolean totals;
  private final boolean bidiAllowed;

  /** a key, and the set of values from an earlier stage that key is allowed to take */
  private static class Restriction {
    final String key;
    final String setName;
    final Set<String> values;

    Restriction(String key, String setName, Set<String> values) {
      this.key = key;
      this.setName = setName;
      this.values = values;
    }
  }

  private final List<Restriction> restrictions;

  private long totalMatches = 0;

  /**
   * Whether the chart needs Unicode bidi controls to lay out correctly.
   *<br>
   * Set once, at report time, when any value or key contains a strong
   * right to left character.  An ascii only chart is emitted exactly as
   * it always was, so this costs nothing when it isn't needed.
   */
  private boolean bidi = false;

  CountStat(List<String> keys, Set<String> lowered, Set<String> sorted, boolean flat, boolean totals, int maxColumns,
            List<Restriction> restrictions, boolean bidiAllowed) {
    if (keys.isEmpty()) {
      throw new SemgrexParseException("count needs at least one key to count");
    }
    this.keys = Collections.unmodifiableList(new ArrayList<>(keys));
    this.lowered = Collections.unmodifiableSet(new LinkedHashSet<>(lowered));
    this.sorted = Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    this.flat = flat;
    this.totals = totals;
    this.maxColumns = maxColumns;
    this.restrictions = Collections.unmodifiableList(new ArrayList<>(restrictions));
    this.bidiAllowed = bidiAllowed;
  }

  /**
   * Parses "count [-flat] [-maxColumns N] [-noBidi] [-noTotal] [-restrict KEY=SET ...] [-lower KEY ...] [-sort KEY ...] KEY [KEY ...]"
   *
   * @param context the stage this command is in, used to validate keys and look up sets
   * @param args the arguments after "count"
   * @return a new CountStat
   * @throws SemgrexParseException if an option is unknown or malformed, there are no keys,
   *   a key isn't in the pattern, a restriction's set can't be used in this stage, a
   *   lowercased key is not one this command counts or restricts, or a sorted key is not
   *   one this command counts
   */
  public static SemgrexStat create(SemgrexStats.Context context, List<String> args) {
    List<String> keys = new ArrayList<>();
    List<Restriction> restrictions = new ArrayList<>();
    Set<String> lowered = new LinkedHashSet<>();
    Set<String> sorted = new LinkedHashSet<>();
    boolean flat = false;
    boolean totals = true;
    int maxColumns = DEFAULT_MAX_COLUMNS;
    // the run decides by default, since it is a property of whatever is
    // displaying the chart, but a script may still turn it off itself
    boolean bidiAllowed = context.bidiAllowed();

    for (int idx = 0; idx < args.size(); ++idx) {
      String arg = args.get(idx);
      if (FLAT.equals(arg)) {
        flat = true;
      } else if (MAX_COLUMNS.equals(arg)) {
        if (idx + 1 >= args.size()) {
          throw new SemgrexParseException(MAX_COLUMNS + " needs a number after it");
        }
        ++idx;
        try {
          maxColumns = Integer.parseInt(args.get(idx));
        } catch (NumberFormatException e) {
          throw new SemgrexParseException(MAX_COLUMNS + " needs a number after it, not '" + args.get(idx) + "'");
        }
        if (maxColumns < 1) {
          throw new SemgrexParseException(MAX_COLUMNS + " must be at least 1");
        }
      } else if (NO_BIDI.equals(arg)) {
        bidiAllowed = false;
      } else if (NO_TOTAL.equals(arg)) {
        totals = false;
      } else if (RESTRICT.equals(arg)) {
        if (idx + 1 >= args.size()) {
          throw new SemgrexParseException(RESTRICT + " needs KEY=SET after it");
        }
        ++idx;
        String[] pieces = args.get(idx).split("=", 2);
        if (pieces.length != 2 || pieces[0].isEmpty() || pieces[1].isEmpty()) {
          throw new SemgrexParseException(RESTRICT + " needs KEY=SET after it, not '" + args.get(idx) + "'");
        }
        context.validateKeys("count " + RESTRICT, Collections.singletonList(pieces[0]));
        restrictions.add(new Restriction(pieces[0], pieces[1], context.useSet("count " + RESTRICT, pieces[1])));
      } else if (SemgrexStats.LOWER.equals(arg)) {
        if (idx + 1 >= args.size() || args.get(idx + 1).startsWith("-")) {
          throw new SemgrexParseException(SemgrexStats.LOWER + " needs a key after it");
        }
        ++idx;
        lowered.add(args.get(idx));
      } else if (SORT.equals(arg)) {
        if (idx + 1 >= args.size() || args.get(idx + 1).startsWith("-")) {
          throw new SemgrexParseException(SORT + " needs a key after it");
        }
        ++idx;
        sorted.add(args.get(idx));
      } else if (arg.startsWith("-")) {
        throw new SemgrexParseException("Unknown option '" + arg + "' for count.  Known options: " +
                                        FLAT + ", " + MAX_COLUMNS + ", " + NO_BIDI + ", " + NO_TOTAL + ", " + RESTRICT + ", " + SemgrexStats.LOWER + ", " + SORT);
      } else {
        keys.add(arg);
      }
    }

    if (keys.isEmpty()) {
      throw new SemgrexParseException("count needs at least one key to count");
    }
    context.validateKeys("count", keys);
    Set<String> used = new LinkedHashSet<>(keys);
    for (Restriction restriction : restrictions) {
      used.add(restriction.key);
    }
    SemgrexStats.checkLowered("count", lowered, used);
    SemgrexStats.checkOptionKeys("count", SORT, sorted, keys);

    return new CountStat(keys, lowered, sorted, flat, totals, maxColumns, restrictions, bidiAllowed);
  }

  @Override
  public String name() {
    return "count";
  }

  @Override
  public List<String> getKeys() {
    return keys;
  }

  @Override
  public void accumulate(CoreMap sentence, SemgrexMatch match) {
    for (Restriction restriction : restrictions) {
      // an unbound key is not in any collected set, so it cannot pass a restriction
      String value = SemgrexStats.keyValue(sentence, match, restriction.key, lowered);
      if (value == null || !restriction.values.contains(value)) {
        return;
      }
    }

    List<String> cleaned = new ArrayList<>(keys.size());
    for (String key : keys) {
      cleaned.add(clean(SemgrexStats.keyValue(sentence, match, key, lowered)));
    }
    counts.merge(Collections.unmodifiableList(cleaned), 1, Integer::sum);
    totalMatches++;
  }

  /**
   * buildKey returns null for a name which was not bound in this
   * particular match; tabs and newlines in a token would wreck the
   * chart, and MISC fields have been known to contain both.
   */
  static String clean(String value) {
    if (value == null) {
      return UNBOUND;
    }
    return value.replaceAll("[\t\r\n]", " ");
  }

  /**
   * Number of matches counted, not including those which failed a restriction.
   *
   * @return the number of matches counted
   */
  public long getTotalMatches() {
    return totalMatches;
  }

  /**
   * Number of distinct key tuples seen
   *
   * @return the number of distinct key tuples
   */
  public int getDistinctKeys() {
    return counts.size();
  }

  // ------------------------------------------------------------------
  // rendering
  // ------------------------------------------------------------------

  /**
   * Whether any strong right to left character appears in this string.
   *<br>
   * Iterates by code point rather than by char so that the RTL scripts
   * outside the BMP, such as Adlam, are caught too.
   */
  private static boolean hasRightToLeft(String value) {
    for (int i = 0; i < value.length(); ) {
      int codePoint = value.codePointAt(i);
      byte directionality = Character.getDirectionality(codePoint);
      if (directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
          directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
        return true;
      }
      i += Character.charCount(codePoint);
    }
    return false;
  }

  /**
   * Decides whether this chart needs the bidi controls.
   *<br>
   * Without them, a row label in a right to left script makes that line's
   * base direction RTL, which reverses the whole column order.  The
   * padding spaces are neutrals and join whichever run is next to them,
   * so cells bleed into one another as well.
   */
  private void detectBidi() {
    if (!bidiAllowed) {
      return;
    }
    for (String key : keys) {
      if (hasRightToLeft(key)) {
        bidi = true;
        return;
      }
    }
    for (List<String> key : counts.keySet()) {
      for (String value : key) {
        if (hasRightToLeft(value)) {
          bidi = true;
          return;
        }
      }
    }
  }

  @Override
  public String report() {
    return report(SemgrexStats.Presentation.DEFAULT, Collections.emptyList());
  }

  /**
   * The chart, as text or in LaTeX, with its totals unless this command
   * or the run leaves them out.  In LaTeX, the label is the caption,
   * joined into one line.  A float with no label is captioned with this
   * command, and an inline tabular with no label has no caption.
   */
  @Override
  public String report(SemgrexStats.Presentation presentation, List<String> labels) {
    SemgrexStats.Format format = presentation.format();
    if (counts.isEmpty()) {
      // a comment in LaTeX, so it can sit in a document without breaking it
      return (format == SemgrexStats.Format.TEXT ? "" : "% ") + "No matches, nothing to count." + System.lineSeparator();
    }
    Chart chart = chart(totals && presentation.totals());
    String caption = String.join(" ", labels).trim();
    switch (format) {
      case LATEX:
        return chart.toLatex(caption.isEmpty() ? null : caption, false);
      case LATEX_FLOAT:
        return chart.toLatex(caption.isEmpty() ? toString() : caption, true);
      default:
        detectBidi();
        return chart.toText(bidi);
    }
  }

  /**
   * The chart this command reports, in the form its keys call for.
   *
   * @param totals whether the chart has its TOTAL row and column, and its subtotals
   */
  Chart chart(boolean totals) {
    if (flat || keys.size() > 3) {
      return flatChart(null);
    }
    if (keys.size() == 1) {
      return oneDimensionalChart(totals);
    }

    // the last key is always the columns; the keys before it nest
    // leftward, so two keys are rows against columns and three are
    // blocks of rows against columns
    int columnKey = keys.size() - 1;
    List<String> columnValues = distinctValues(columnKey);
    if (columnValues.size() > maxColumns) {
      return flatChart(keys.get(columnKey) + " took " + columnValues.size() + " distinct values, which is more than " +
                       MAX_COLUMNS + " (" + maxColumns + "), so printing the flat form.");
    }
    if (keys.size() == 2) {
      return twoDimensionalChart(columnValues, totals);
    }
    return blockChart(columnValues, totals);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("count");
    for (Restriction restriction : restrictions) {
      sb.append(" ").append(RESTRICT).append(" ").append(restriction.key).append("=").append(restriction.setName);
      sb.append(" (").append(restriction.values.size()).append(" values)");
    }
    if (flat) {
      sb.append(" ").append(FLAT);
    }
    if (!totals) {
      sb.append(" ").append(NO_TOTAL);
    }
    if (maxColumns != DEFAULT_MAX_COLUMNS) {
      sb.append(" ").append(MAX_COLUMNS).append(" ").append(maxColumns);
    }
    if (!bidiAllowed) {
      sb.append(" ").append(NO_BIDI);
    }
    for (String key : lowered) {
      sb.append(" ").append(SemgrexStats.LOWER).append(" ").append(key);
    }
    for (String key : sorted) {
      sb.append(" ").append(SORT).append(" ").append(key);
    }
    for (String key : keys) {
      sb.append(" ").append(key);
    }
    return sb.toString();
  }

  /** Alignments for a chart: the first {@code labels} columns left, the next {@code numbers} right */
  private static List<Boolean> alignments(int labels, int numbers) {
    List<Boolean> leftAligned = new ArrayList<>();
    for (int i = 0; i < labels; ++i) {
      leftAligned.add(true);
    }
    for (int i = 0; i < numbers; ++i) {
      leftAligned.add(false);
    }
    return leftAligned;
  }

  /** Minimum widths of 0 for every column, with the last at least wide enough for a percentage if {@code pct} */
  private static List<Integer> minimumWidths(int columns, boolean pct) {
    List<Integer> widths = new ArrayList<>(Collections.nCopies(columns, 0));
    if (pct) {
      widths.set(columns - 1, PCT_WIDTH);
    }
    return widths;
  }

  /**
   * count X
   */
  private Chart oneDimensionalChart(boolean totals) {
    Chart chart = new Chart(alignments(1, 2), minimumWidths(3, true));
    chart.row(keys.get(0), "count", "pct");
    chart.rule();
    for (Map.Entry<List<String>, Integer> entry : sortedEntries()) {
      chart.row(entry.getKey().get(0), Integer.toString(entry.getValue()), percent(entry.getValue(), totalMatches));
    }
    if (totals) {
      chart.rule();
      chart.row("TOTAL", Long.toString(totalMatches));
    }
    return chart;
  }

  /**
   * count X Y
   */
  private Chart twoDimensionalChart(List<String> columnValues, boolean totals) {
    List<String> rowValues = distinctValues(0);
    Map<String, Integer> rowTotals = marginal(0);
    Map<String, Integer> columnTotals = marginal(1);

    int totalColumns = totals ? 1 : 0;
    Chart chart = new Chart(alignments(1, columnValues.size() + totalColumns), minimumWidths(columnValues.size() + 1 + totalColumns, false));
    chart.note("rows: " + keys.get(0) + ", columns: " + keys.get(1));

    List<String> header = new ArrayList<>();
    header.add(keys.get(0));
    header.addAll(columnValues);
    if (totals) {
      header.add("TOTAL");
    }
    chart.row(header);
    chart.rule();

    for (String row : rowValues) {
      List<String> cells = new ArrayList<>();
      cells.add(row);
      for (String column : columnValues) {
        cells.add(Integer.toString(get(row, column)));
      }
      if (totals) {
        cells.add(Integer.toString(rowTotals.getOrDefault(row, 0)));
      }
      chart.row(cells);
    }

    if (!totals) {
      return chart;
    }
    chart.rule();
    List<String> footer = new ArrayList<>();
    footer.add("TOTAL");
    for (String column : columnValues) {
      footer.add(Integer.toString(columnTotals.getOrDefault(column, 0)));
    }
    footer.add(Long.toString(totalMatches));
    chart.row(footer);
    return chart;
  }

  /**
   * count X Y Z
   *<br>
   * The first key groups the rows into blocks, the second labels the
   * rows within a block, and the third is the columns.  The columns are
   * shared by every block, so blocks can be compared to one another and
   * to the totals at the foot; the rows are not, so each block shows
   * only what occurs in it.
   */
  private Chart blockChart(List<String> columnValues, boolean totals) {
    List<String> blockValues = distinctValues(0);
    Map<String, Integer> columnTotals = marginal(2);

    int totalColumns = totals ? 1 : 0;
    Chart chart = new Chart(alignments(1, columnValues.size() + totalColumns), minimumWidths(columnValues.size() + 1 + totalColumns, false));
    chart.note("blocks: " + keys.get(0) + ", rows: " + keys.get(1) + ", columns: " + keys.get(2));

    List<String> header = new ArrayList<>();
    header.add(keys.get(0));
    header.addAll(columnValues);
    if (totals) {
      header.add("TOTAL");
    }
    chart.row(header);
    chart.rule();

    for (int b = 0; b < blockValues.size(); ++b) {
      String block = blockValues.get(b);
      if (b > 0) {
        chart.blank();
      }
      chart.block(block);

      int[] subtotals = new int[columnValues.size()];
      int blockTotal = 0;
      for (String row : distinctValuesInBlock(block, 1)) {
        List<String> cells = new ArrayList<>();
        cells.add(row);
        int rowTotal = 0;
        for (int i = 0; i < columnValues.size(); ++i) {
          int value = get(block, row, columnValues.get(i));
          subtotals[i] += value;
          rowTotal += value;
          cells.add(Integer.toString(value));
        }
        blockTotal += rowTotal;
        if (totals) {
          cells.add(Integer.toString(rowTotal));
        }
        chart.indentedRow(cells);
      }

      if (!totals) {
        continue;
      }
      List<String> subtotal = new ArrayList<>();
      subtotal.add(SUBTOTAL);
      for (int value : subtotals) {
        subtotal.add(Integer.toString(value));
      }
      subtotal.add(Integer.toString(blockTotal));
      chart.indentedRow(subtotal);
    }

    if (!totals) {
      return chart;
    }
    chart.rule();
    List<String> footer = new ArrayList<>();
    footer.add("TOTAL");
    for (String column : columnValues) {
      footer.add(Integer.toString(columnTotals.getOrDefault(column, 0)));
    }
    footer.add(Long.toString(totalMatches));
    chart.row(footer);
    return chart;
  }

  /**
   * count W X Y Z ... , or a count too wide to draw
   *
   * @param note why the flat form is used for a count which would otherwise be a grid, or null
   */
  private Chart flatChart(String note) {
    Chart chart = new Chart(alignments(keys.size(), 2), minimumWidths(keys.size() + 2, true));
    if (note != null) {
      chart.note(note);
    }
    List<String> header = new ArrayList<>(keys);
    header.add("count");
    header.add("pct");
    chart.row(header);
    for (Map.Entry<List<String>, Integer> entry : sortedEntries()) {
      List<String> cells = new ArrayList<>(entry.getKey());
      cells.add(Integer.toString(entry.getValue()));
      cells.add(percent(entry.getValue(), totalMatches));
      chart.row(cells);
    }
    return chart;
  }

  // ------------------------------------------------------------------
  // helpers
  // ------------------------------------------------------------------

  private int get(String row, String column) {
    return counts.getOrDefault(Arrays.asList(row, column), 0);
  }

  private int get(String block, String row, String column) {
    return counts.getOrDefault(Arrays.asList(block, row, column), 0);
  }

  /**
   * The distinct values the key at this position took within one block,
   * ordered by their totals in that block, or alphabetically for a key
   * given with {@code -sort}.
   *<br>
   * Blocks are ragged on purpose: a block shows the rows which occur in
   * it rather than every row in the chart.  Padding them to a common set
   * would make the blocks line up, but for the data this is useful on --
   * a word belongs to one feature value -- almost every padded row is
   * zero.
   */
  private List<String> distinctValuesInBlock(String block, int position) {
    Map<String, Integer> totals = new HashMap<>();
    for (Map.Entry<List<String>, Integer> entry : counts.entrySet()) {
      if (entry.getKey().get(0).equals(block)) {
        totals.merge(entry.getKey().get(position), entry.getValue(), Integer::sum);
      }
    }
    return ordered(totals, position);
  }

  /**
   * The distinct values the key at this position took, ordered by
   * total count descending, ties broken alphabetically, or alphabetically
   * for a key given with {@code -sort}.
   */
  private List<String> distinctValues(int position) {
    return ordered(marginal(position), position);
  }

  /** The values of the key at this position, in the order the chart shows them */
  private List<String> ordered(Map<String, Integer> totals, int position) {
    List<String> values = new ArrayList<>(totals.keySet());
    if (sorted.contains(keys.get(position))) {
      Collections.sort(values);
      return values;
    }
    Collections.sort(values, new Comparator<String>() {
      public int compare(String first, String second) {
        int cmp = totals.get(second).compareTo(totals.get(first));
        if (cmp != 0) {
          return cmp;
        }
        return first.compareTo(second);
      }
    });
    return values;
  }

  private Map<String, Integer> marginal(int position) {
    Map<String, Integer> totals = new HashMap<>();
    for (Map.Entry<List<String>, Integer> entry : counts.entrySet()) {
      totals.merge(entry.getKey().get(position), entry.getValue(), Integer::sum);
    }
    return totals;
  }

  /**
   * The combinations, ordered by the values of the keys given with
   * {@code -sort}, in the order of the keys, then by count descending,
   * then alphabetically.
   */
  private List<Map.Entry<List<String>, Integer>> sortedEntries() {
    List<Map.Entry<List<String>, Integer>> entries = new ArrayList<>(counts.entrySet());
    Collections.sort(entries, new Comparator<Map.Entry<List<String>, Integer>>() {
      public int compare(Map.Entry<List<String>, Integer> first, Map.Entry<List<String>, Integer> second) {
        for (int position = 0; position < keys.size(); ++position) {
          if (sorted.contains(keys.get(position))) {
            int cmp = first.getKey().get(position).compareTo(second.getKey().get(position));
            if (cmp != 0) {
              return cmp;
            }
          }
        }
        int cmp = second.getValue().compareTo(first.getValue());
        if (cmp != 0) {
          return cmp;
        }
        return SemgrexUtils.compareKeys(first.getKey(), second.getKey());
      }
    });
    return entries;
  }

  private static String percent(long count, long total) {
    if (total == 0) {
      return "";
    }
    return String.format("%.2f%%", 100.0 * count / total);
  }

}
