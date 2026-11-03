package edu.stanford.nlp.semgraph.semgrex.stats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A chart of counts, as the lines it is made of: rows of cells, rules,
 * the labels of blocks of rows, and notes about the chart.  A command
 * builds one of these, and it is then laid out, so how a chart is drawn
 * is decided in one place, apart from what goes in it.
 */
final class Chart {
  /** The kinds of line in a chart */
  enum LineType {
    /** a row of cells */
    ROW,
    /** a rule across every column */
    RULE,
    /** the label of a block of rows, on a line of its own */
    BLOCK,
    /** an empty line, between blocks */
    BLANK,
    /** a note about the chart, written before it as a comment */
    NOTE
  }

  /** One line of a chart */
  static final class Line {
    final LineType type;
    /** the cells of a row, which may stop short of the last columns; the text of a block or note */
    final List<String> cells;
    /** whether the first cell of a row is indented under its block */
    final boolean indented;

    Line(LineType type, List<String> cells, boolean indented) {
      this.type = type;
      this.cells = Collections.unmodifiableList(new ArrayList<>(cells));
      this.indented = indented;
    }
  }

  /** How far the rows of a block are indented under its label */
  static final String INDENT = "  ";

  /** first strong isolate: makes a cell pick its own direction without affecting its neighbours */
  private static final String FSI = "⁨";
  /** pop directional isolate */
  private static final String PDI = "⁩";
  /** left to right mark: pins a line's base direction so the columns don't reverse */
  private static final String LRM = "‎";

  private final List<Boolean> leftAligned;
  private final List<Integer> minimumWidths;
  private final List<Line> lines = new ArrayList<>();

  /**
   * Creates an empty chart.
   *
   * @param leftAligned for each column, whether it is aligned left, as labels are, or right, as numbers are
   * @param minimumWidths for each column, the narrowest it is laid out as text
   */
  Chart(List<Boolean> leftAligned, List<Integer> minimumWidths) {
    if (leftAligned.size() != minimumWidths.size()) {
      throw new IllegalArgumentException("Every column needs an alignment and a minimum width");
    }
    this.leftAligned = Collections.unmodifiableList(new ArrayList<>(leftAligned));
    this.minimumWidths = Collections.unmodifiableList(new ArrayList<>(minimumWidths));
  }

  /** The number of columns */
  int columns() {
    return leftAligned.size();
  }

  /** Whether a column is aligned left */
  boolean isLeftAligned(int column) {
    return leftAligned.get(column);
  }

  /** The lines of the chart, in order */
  List<Line> lines() {
    return Collections.unmodifiableList(lines);
  }

  void note(String text) {
    lines.add(new Line(LineType.NOTE, Collections.singletonList(text), false));
  }

  void row(String... cells) {
    row(Arrays.asList(cells));
  }

  void row(List<String> cells) {
    lines.add(new Line(LineType.ROW, cells, false));
  }

  void indentedRow(List<String> cells) {
    lines.add(new Line(LineType.ROW, cells, true));
  }

  void rule() {
    lines.add(new Line(LineType.RULE, Collections.emptyList(), false));
  }

  void block(String label) {
    lines.add(new Line(LineType.BLOCK, Collections.singletonList(label), false));
  }

  void blank() {
    lines.add(new Line(LineType.BLANK, Collections.emptyList(), false));
  }

  /**
   * The width of each column as text: the widest of its cells, an
   * indented cell counting its indent, and of the block labels for the
   * first column, and at least its minimum width.
   */
  private int[] widths() {
    int[] widths = new int[columns()];
    for (int c = 0; c < widths.length; ++c) {
      widths[c] = minimumWidths.get(c);
    }
    for (Line line : lines) {
      if (line.type == LineType.ROW) {
        for (int c = 0; c < line.cells.size(); ++c) {
          int length = line.cells.get(c).length() + (c == 0 && line.indented ? INDENT.length() : 0);
          widths[c] = Math.max(widths[c], length);
        }
      } else if (line.type == LineType.BLOCK) {
        widths[0] = Math.max(widths[0], line.cells.get(0).length());
      }
    }
    return widths;
  }

  /**
   * Lays the chart out as text, with columns of spaces, a rule as a row
   * of dashes, and each note as a line starting with {@code #}.
   *<br>
   * With {@code bidi}, each line starts with a left to right mark and
   * each cell is isolated, so that a right to left value can neither
   * reverse the order of the columns nor run into its neighbours.  The
   * padding goes outside the isolates, as spaces inside one would join
   * the cell's direction and land on the wrong side of the word.
   *
   * @param bidi whether to add the Unicode bidi controls
   * @return the chart, each line ending with a line separator
   */
  String toText(boolean bidi) {
    int[] widths = widths();
    String start = bidi ? LRM : "";
    StringBuilder sb = new StringBuilder();
    for (Line line : lines) {
      switch (line.type) {
        case NOTE:
          sb.append(start).append("# ").append(line.cells.get(0));
          break;
        case BLOCK:
          // the block's line carries only its label, so there is no
          // trailing whitespace to strip out of a paste
          sb.append(start).append(line.cells.get(0));
          break;
        case BLANK:
          break;
        case RULE:
          sb.append(start);
          for (int c = 0; c < widths.length; ++c) {
            if (c > 0) {
              sb.append("  ");
            }
            sb.append(cell(dashes(widths[c]), widths[c], true, bidi));
          }
          break;
        case ROW:
          sb.append(start);
          for (int c = 0; c < line.cells.size(); ++c) {
            if (c > 0) {
              sb.append("  ");
            }
            if (c == 0 && line.indented) {
              sb.append(INDENT).append(cell(line.cells.get(c), widths[c] - INDENT.length(), true, bidi));
            } else {
              sb.append(cell(line.cells.get(c), widths[c], leftAligned.get(c), bidi));
            }
          }
          break;
        default:
          throw new AssertionError("Unknown line type " + line.type);
      }
      sb.append(System.lineSeparator());
    }
    return sb.toString();
  }

  /** One padded cell, isolated if the chart needs the bidi controls */
  private static String cell(String value, int width, boolean leftAlign, boolean bidi) {
    StringBuilder padding = new StringBuilder();
    for (int i = value.length(); i < width; ++i) {
      padding.append(' ');
    }
    String content = bidi ? FSI + value + PDI : value;
    return leftAlign ? content + padding : padding + content;
  }

  private static String dashes(int width) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < width; ++i) {
      sb.append('-');
    }
    return sb.toString();
  }
}
