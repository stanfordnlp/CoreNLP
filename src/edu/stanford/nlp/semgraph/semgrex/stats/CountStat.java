package edu.stanford.nlp.semgraph.semgrex.stats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.semgraph.semgrex.SemgrexMatch;
import edu.stanford.nlp.semgraph.semgrex.SemgrexParseException;
import edu.stanford.nlp.semgraph.semgrex.SemgrexUtils;

/**
 * Counts the values taken by the named nodes, edges, and regex groups of a semgrex match.
 *<br>
 * Script syntax:
 *<pre>
 * count [-flat] [-maxColumns N] KEY [KEY ...]
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

  /** how far the rows of a block chart are indented under their block */
  private static final String INDENT = "  ";

  /** label of a block's summary row */
  private static final String SUBTOTAL = "subtotal";
  private static final String RESTRICT = "-restrict";
  private static final String NO_BIDI = "-noBidi";

  private final List<String> keys;
  private final Map<List<String>, Integer> counts = new HashMap<>();

  private final int maxColumns;
  private final boolean flat;
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

  CountStat(List<String> keys, boolean flat, int maxColumns, List<Restriction> restrictions, boolean bidiAllowed) {
    if (keys.isEmpty()) {
      throw new SemgrexParseException("count needs at least one key to count");
    }
    this.keys = Collections.unmodifiableList(new ArrayList<>(keys));
    this.flat = flat;
    this.maxColumns = maxColumns;
    this.restrictions = Collections.unmodifiableList(new ArrayList<>(restrictions));
    this.bidiAllowed = bidiAllowed;
  }

  /**
   * Parses "count [-flat] [-maxColumns N] [-noBidi] [-restrict KEY=SET ...] KEY [KEY ...]"
   */
  public static SemgrexStat create(SemgrexStats.Context context, List<String> args) {
    List<String> keys = new ArrayList<>();
    List<Restriction> restrictions = new ArrayList<>();
    boolean flat = false;
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
      } else if (arg.startsWith("-")) {
        throw new SemgrexParseException("Unknown option '" + arg + "' for count.  Known options: " +
                                        FLAT + ", " + MAX_COLUMNS + ", " + NO_BIDI + ", " + RESTRICT);
      } else {
        keys.add(arg);
      }
    }

    if (keys.isEmpty()) {
      throw new SemgrexParseException("count needs at least one key to count");
    }
    context.validateKeys("count", keys);

    return new CountStat(keys, flat, maxColumns, restrictions, bidiAllowed);
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
  public void accumulate(SemgrexMatch match) {
    for (Restriction restriction : restrictions) {
      // an unbound key is not in any collected set, so it cannot pass a restriction
      String value = SemgrexUtils.getKey(match, restriction.key);
      if (value == null || !restriction.values.contains(value)) {
        return;
      }
    }

    List<String> key = SemgrexUtils.buildKey(match, keys);
    List<String> cleaned = new ArrayList<>(key.size());
    for (String value : key) {
      cleaned.add(clean(value));
    }
    counts.merge(Collections.unmodifiableList(cleaned), 1, Integer::sum);
    totalMatches++;
  }

  /**
   * buildKey returns null for a name which was not bound in this
   * particular match; tabs and newlines in a token would wreck the
   * chart, and MISC fields have been known to contain both.
   */
  private static String clean(String value) {
    if (value == null) {
      return UNBOUND;
    }
    return value.replaceAll("[\t\r\n]", " ");
  }

  public long getTotalMatches() {
    return totalMatches;
  }

  /** Number of distinct key tuples seen */
  public int getDistinctKeys() {
    return counts.size();
  }

  // ------------------------------------------------------------------
  // rendering
  // ------------------------------------------------------------------

  /** first strong isolate: makes a cell pick its own direction without affecting its neighbours */
  private static final String FSI = "\u2068";
  /** pop directional isolate */
  private static final String PDI = "\u2069";
  /** left to right mark: pins a line's base direction so the columns don't reverse */
  private static final String LRM = "\u200E";

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

  /** Starts a line, pinning its base direction when the chart needs it */
  private String start() {
    return bidi ? LRM : "";
  }

  /**
   * One padded cell.
   *<br>
   * The width is measured on the raw value and the padding goes outside
   * the isolate: spaces inside it would join the cell's own direction and
   * end up on the wrong side of the word.
   */
  private String cell(String value, int width, boolean leftAlign) {
    StringBuilder padding = new StringBuilder();
    for (int i = value.length(); i < width; ++i) {
      padding.append(' ');
    }
    String content = bidi ? FSI + value + PDI : value;
    return leftAlign ? content + padding : padding + content;
  }

  private String dashes(int width) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < width; ++i) {
      sb.append('-');
    }
    return cell(sb.toString(), width, true);
  }

  @Override
  public String report() {
    detectBidi();
    if (counts.isEmpty()) {
      return "No matches, nothing to count." + System.lineSeparator();
    }
    if (flat || keys.size() > 3) {
      return flatChart();
    }
    if (keys.size() == 1) {
      return oneDimensionalChart();
    }

    // the last key is always the columns; the keys before it nest
    // leftward, so two keys are rows against columns and three are
    // blocks of rows against columns
    int columnKey = keys.size() - 1;
    List<String> columnValues = distinctValues(columnKey);
    if (columnValues.size() > maxColumns) {
      StringBuilder sb = new StringBuilder();
      sb.append(start()).append("# ").append(keys.get(columnKey)).append(" took ").append(columnValues.size());
      sb.append(" distinct values, which is more than ").append(MAX_COLUMNS).append(" (").append(maxColumns);
      sb.append("), so printing the flat form.").append(System.lineSeparator());
      sb.append(flatChart());
      return sb.toString();
    }
    if (keys.size() == 2) {
      return twoDimensionalChart(columnValues);
    }
    return blockChart(columnValues);
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
    if (maxColumns != DEFAULT_MAX_COLUMNS) {
      sb.append(" ").append(MAX_COLUMNS).append(" ").append(maxColumns);
    }
    if (!bidiAllowed) {
      sb.append(" ").append(NO_BIDI);
    }
    for (String key : keys) {
      sb.append(" ").append(key);
    }
    return sb.toString();
  }

  /**
   * count X
   */
  private String oneDimensionalChart() {
    List<Map.Entry<List<String>, Integer>> entries = sortedEntries();

    int keyWidth = keys.get(0).length();
    int countWidth = "count".length();
    for (Map.Entry<List<String>, Integer> entry : entries) {
      keyWidth = Math.max(keyWidth, entry.getKey().get(0).length());
      countWidth = Math.max(countWidth, Integer.toString(entry.getValue()).length());
    }
    keyWidth = Math.max(keyWidth, "TOTAL".length());
    countWidth = Math.max(countWidth, Long.toString(totalMatches).length());

    StringBuilder sb = new StringBuilder();
    sb.append(row(keyWidth, countWidth, keys.get(0), "count", "pct"));
    sb.append(rule(keyWidth, countWidth));
    for (Map.Entry<List<String>, Integer> entry : entries) {
      sb.append(row(keyWidth, countWidth, entry.getKey().get(0), Integer.toString(entry.getValue()),
                    percent(entry.getValue(), totalMatches)));
    }
    sb.append(rule(keyWidth, countWidth));
    sb.append(start());
    sb.append(cell("TOTAL", keyWidth, true)).append("  ");
    sb.append(cell(Long.toString(totalMatches), countWidth, false));
    sb.append(System.lineSeparator());
    return sb.toString();
  }

  private String row(int keyWidth, int countWidth, String key, String count, String pct) {
    StringBuilder sb = new StringBuilder(start());
    sb.append(cell(key, keyWidth, true)).append("  ");
    sb.append(cell(count, countWidth, false)).append("  ");
    sb.append(cell(pct, PCT_WIDTH, false));
    sb.append(System.lineSeparator());
    return sb.toString();
  }

  private String rule(int keyWidth, int countWidth) {
    StringBuilder sb = new StringBuilder(start());
    sb.append(dashes(keyWidth)).append("  ");
    sb.append(dashes(countWidth)).append("  ");
    sb.append(dashes(PCT_WIDTH));
    sb.append(System.lineSeparator());
    return sb.toString();
  }

  /**
   * count X Y
   */
  private String twoDimensionalChart(List<String> columnValues) {
    List<String> rowValues = distinctValues(0);

    Map<String, Integer> rowTotals = marginal(0);
    Map<String, Integer> columnTotals = marginal(1);

    int rowHeaderWidth = keys.get(0).length();
    for (String row : rowValues) {
      rowHeaderWidth = Math.max(rowHeaderWidth, row.length());
    }
    rowHeaderWidth = Math.max(rowHeaderWidth, "TOTAL".length());

    // each column is as wide as the widest of its header and its counts
    List<Integer> columnWidths = new ArrayList<>();
    for (String column : columnValues) {
      int width = column.length();
      for (String row : rowValues) {
        width = Math.max(width, Integer.toString(get(row, column)).length());
      }
      width = Math.max(width, Integer.toString(columnTotals.getOrDefault(column, 0)).length());
      columnWidths.add(width);
    }
    int totalWidth = Math.max("TOTAL".length(), Long.toString(totalMatches).length());

    StringBuilder sb = new StringBuilder();
    sb.append(start()).append("# rows: ").append(keys.get(0));
    sb.append(", columns: ").append(keys.get(1)).append(System.lineSeparator());

    sb.append(start()).append(cell(keys.get(0), rowHeaderWidth, true));
    for (int i = 0; i < columnValues.size(); ++i) {
      sb.append("  ").append(cell(columnValues.get(i), columnWidths.get(i), false));
    }
    sb.append("  ").append(cell("TOTAL", totalWidth, false)).append(System.lineSeparator());

    sb.append(gridRule(rowHeaderWidth, columnWidths, totalWidth));

    for (String row : rowValues) {
      sb.append(start()).append(cell(row, rowHeaderWidth, true));
      for (int i = 0; i < columnValues.size(); ++i) {
        sb.append("  ").append(cell(Integer.toString(get(row, columnValues.get(i))), columnWidths.get(i), false));
      }
      sb.append("  ").append(cell(Integer.toString(rowTotals.getOrDefault(row, 0)), totalWidth, false));
      sb.append(System.lineSeparator());
    }

    sb.append(gridRule(rowHeaderWidth, columnWidths, totalWidth));

    sb.append(start()).append(cell("TOTAL", rowHeaderWidth, true));
    for (int i = 0; i < columnValues.size(); ++i) {
      sb.append("  ").append(cell(Integer.toString(columnTotals.getOrDefault(columnValues.get(i), 0)), columnWidths.get(i), false));
    }
    sb.append("  ").append(cell(Long.toString(totalMatches), totalWidth, false));
    sb.append(System.lineSeparator());

    return sb.toString();
  }

  private String gridRule(int rowHeaderWidth, List<Integer> columnWidths, int totalWidth) {
    StringBuilder sb = new StringBuilder(start());
    sb.append(dashes(rowHeaderWidth));
    for (int width : columnWidths) {
      sb.append("  ").append(dashes(width));
    }
    sb.append("  ").append(dashes(totalWidth));
    sb.append(System.lineSeparator());
    return sb.toString();
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
  private String blockChart(List<String> columnValues) {
    List<String> blockValues = distinctValues(0);
    Map<String, Integer> columnTotals = marginal(2);

    int rowHeaderWidth = keys.get(0).length();
    for (String block : blockValues) {
      rowHeaderWidth = Math.max(rowHeaderWidth, block.length());
      for (String row : distinctValuesInBlock(block, 1)) {
        rowHeaderWidth = Math.max(rowHeaderWidth, INDENT.length() + row.length());
      }
    }
    rowHeaderWidth = Math.max(rowHeaderWidth, INDENT.length() + SUBTOTAL.length());
    rowHeaderWidth = Math.max(rowHeaderWidth, "TOTAL".length());

    List<Integer> columnWidths = new ArrayList<>();
    for (String column : columnValues) {
      int width = Math.max(column.length(), Integer.toString(columnTotals.getOrDefault(column, 0)).length());
      for (String block : blockValues) {
        for (String row : distinctValuesInBlock(block, 1)) {
          width = Math.max(width, Integer.toString(get(block, row, column)).length());
        }
      }
      columnWidths.add(width);
    }
    int totalWidth = Math.max("TOTAL".length(), Long.toString(totalMatches).length());

    StringBuilder sb = new StringBuilder();
    sb.append(start()).append("# blocks: ").append(keys.get(0));
    sb.append(", rows: ").append(keys.get(1));
    sb.append(", columns: ").append(keys.get(2)).append(System.lineSeparator());

    sb.append(start()).append(cell(keys.get(0), rowHeaderWidth, true));
    for (int i = 0; i < columnValues.size(); ++i) {
      sb.append("  ").append(cell(columnValues.get(i), columnWidths.get(i), false));
    }
    sb.append("  ").append(cell("TOTAL", totalWidth, false)).append(System.lineSeparator());

    sb.append(gridRule(rowHeaderWidth, columnWidths, totalWidth));

    for (int b = 0; b < blockValues.size(); ++b) {
      String block = blockValues.get(b);
      if (b > 0) {
        sb.append(System.lineSeparator());
      }
      // the block's own line carries only its label, so there is no
      // trailing whitespace to strip out of a paste
      sb.append(start()).append(block).append(System.lineSeparator());

      List<String> rowValues = distinctValuesInBlock(block, 1);
      int[] subtotals = new int[columnValues.size()];
      int blockTotal = 0;

      for (String row : rowValues) {
        // the indent goes outside the isolate for the same reason the
        // padding does: it is neutral, and inside it would join the
        // cell's own direction and end up on the wrong side of the word
        sb.append(start()).append(INDENT).append(cell(row, rowHeaderWidth - INDENT.length(), true));
        int rowTotal = 0;
        for (int i = 0; i < columnValues.size(); ++i) {
          int value = get(block, row, columnValues.get(i));
          subtotals[i] += value;
          rowTotal += value;
          sb.append("  ").append(cell(Integer.toString(value), columnWidths.get(i), false));
        }
        blockTotal += rowTotal;
        sb.append("  ").append(cell(Integer.toString(rowTotal), totalWidth, false));
        sb.append(System.lineSeparator());
      }

      sb.append(start()).append(INDENT).append(cell(SUBTOTAL, rowHeaderWidth - INDENT.length(), true));
      for (int i = 0; i < columnValues.size(); ++i) {
        sb.append("  ").append(cell(Integer.toString(subtotals[i]), columnWidths.get(i), false));
      }
      sb.append("  ").append(cell(Integer.toString(blockTotal), totalWidth, false));
      sb.append(System.lineSeparator());
    }

    sb.append(gridRule(rowHeaderWidth, columnWidths, totalWidth));

    sb.append(start()).append(cell("TOTAL", rowHeaderWidth, true));
    for (int i = 0; i < columnValues.size(); ++i) {
      sb.append("  ").append(cell(Integer.toString(columnTotals.getOrDefault(columnValues.get(i), 0)), columnWidths.get(i), false));
    }
    sb.append("  ").append(cell(Long.toString(totalMatches), totalWidth, false));
    sb.append(System.lineSeparator());

    return sb.toString();
  }

  /**
   * count W X Y Z ... , or a count too wide to draw
   */
  private String flatChart() {
    List<Map.Entry<List<String>, Integer>> entries = sortedEntries();

    List<Integer> widths = new ArrayList<>();
    for (int i = 0; i < keys.size(); ++i) {
      widths.add(keys.get(i).length());
    }
    int countWidth = "count".length();
    for (Map.Entry<List<String>, Integer> entry : entries) {
      for (int i = 0; i < keys.size(); ++i) {
        widths.set(i, Math.max(widths.get(i), entry.getKey().get(i).length()));
      }
      countWidth = Math.max(countWidth, Integer.toString(entry.getValue()).length());
    }

    StringBuilder sb = new StringBuilder(start());
    for (int i = 0; i < keys.size(); ++i) {
      sb.append(cell(keys.get(i), widths.get(i), true)).append("  ");
    }
    sb.append(cell("count", countWidth, false)).append("  ").append(cell("pct", PCT_WIDTH, false));
    sb.append(System.lineSeparator());

    for (Map.Entry<List<String>, Integer> entry : entries) {
      sb.append(start());
      for (int i = 0; i < keys.size(); ++i) {
        sb.append(cell(entry.getKey().get(i), widths.get(i), true)).append("  ");
      }
      sb.append(cell(Integer.toString(entry.getValue()), countWidth, false));
      sb.append("  ").append(cell(percent(entry.getValue(), totalMatches), PCT_WIDTH, false));
      sb.append(System.lineSeparator());
    }

    return sb.toString();
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
   * ordered by their totals in that block.
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
    List<String> values = new ArrayList<>(totals.keySet());
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

  /**
   * The distinct values the key at this position took, ordered by
   * total count descending, ties broken alphabetically.
   */
  private List<String> distinctValues(int position) {
    Map<String, Integer> totals = marginal(position);
    List<String> values = new ArrayList<>(totals.keySet());
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

  private List<Map.Entry<List<String>, Integer>> sortedEntries() {
    List<Map.Entry<List<String>, Integer>> entries = new ArrayList<>(counts.entrySet());
    Collections.sort(entries, new Comparator<Map.Entry<List<String>, Integer>>() {
      public int compare(Map.Entry<List<String>, Integer> first, Map.Entry<List<String>, Integer> second) {
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
