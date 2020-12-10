package edu.stanford.nlp.util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * This implements a confusion table over arbitrary types of class labels. Main
 * routines of interest:
 * <ul>
 * <li> add(guess, gold), increments the guess/gold entry in this cell by 1
 * <li> get(guess, gold), returns the number of entries in this cell
 * <li> toString(), returns printed form of the table, with marginals and
 *                     contingencies for each class label
 * </ul>
 *
 * Example usage:
 * <pre>{@code
 * Confusion<String> myConf = new Confusion<String>();
 * myConf.add("l1", "l1");
 * myConf.add("l1", "l2");
 * myConf.add("l2", "l2");
 * System.out.println(myConf.toString());
 * }</pre>
 *
 * NOTES: - This sorts by the toString() of the guess and gold labels. Thus the
 * label.toString() values should be distinct!
 *
 * @author yeh1@cs.stanford.edu
 *
 * @param <U> the class label type
 */
public class ConfusionMatrix<U> {
  // classification placeholder prefix when drawing in table
  private static final String CLASS_PREFIX = "C";

  private static final String FORMAT = "#.#####";
  protected DecimalFormat format;
  private int leftPadSize = 16;
  private int delimPadSize = 8;
  private boolean useRealLabels = false;

  public ConfusionMatrix() {
    format = new DecimalFormat(FORMAT);
  }

  public ConfusionMatrix(Locale locale) {
    format = new DecimalFormat(FORMAT, new DecimalFormatSymbols(locale));
  }

  @Override
  public String toString() {
    return printTable();
  }

  /**
   * This sets the lefthand side pad width for displaying the text table.
   * @param newPadSize
   */
  public void setLeftPadSize(int newPadSize) {
    this.leftPadSize = newPadSize;
  }

  /**
   * Sets the width used to separate cells in the table.
   */
  public void setDelimPadSize(int newPadSize) {
    this.delimPadSize = newPadSize;
  }

  public void setUseRealLabels(boolean useRealLabels) {
    this.useRealLabels = useRealLabels;
  }

  /**
   * Contingency table, listing precision ,recall, specificity, and f1 given
   * the number of true and false positives, true and false negatives.
   *
   * @author yeh1@cs.stanford.edu
   *
   */
  public class Contingency {
    private double tp = 0;
    private double fp = 0;
    private double tn = 0;
    private double fn = 0;

    private double prec = 0.0;
    private double recall = 0.0;
    private double spec = 0.0;
    private double f1 = 0.0;

    public Contingency(int tp_, int fp_, int tn_, int fn_) {
      tp = tp_;
      fp = fp_;
      tn = tn_;
      fn = fn_;

      prec = tp / (tp + fp);
      recall = tp / (tp + fn);
      spec = tn / (fp + tn);
      f1 = (2 * prec * recall) / (prec + recall);
    }

    public String toString() {
      return StringUtils.join(Arrays.asList("prec=" + (((tp + fp) > 0) ? format.format(prec) : "n/a"),
                                            "recall=" + (((tp + fn) > 0) ? format.format(recall) : "n/a"),
                                            "spec=" + (((fp + tn) > 0) ? format.format(spec) : "n/a"), "f1="
                                            + (((prec + recall) > 0) ? format.format(f1) : "n/a")),
                              ", ");
    }

    public double f1(){
      return f1;
    }

    public double precision(){
      return prec;
    }

    public double recall(){
      return recall;
    }

    public double spec(){
      return spec;
    }
  }


  private ConcurrentHashMap<Pair<U, U>, Integer> confTable = new ConcurrentHashMap<>();

  /**
   * Increments the entry for this guess and gold by 1.
   */
  public void add(U guess, U gold) {
    add(guess, gold, 1);
  }

  /**
   * Increments the entry for this guess and gold by the given increment amount.
   */
  public synchronized void add(U guess, U gold, int increment) {
      Pair<U, U> pair = new Pair<>(guess, gold);
      if (confTable.containsKey(pair)) {
        confTable.put(pair, confTable.get(pair) + increment);
      } else {
        confTable.put(pair, increment);
      }
    }

  /**
   * Retrieves the number of entries with this guess and gold.
   */
  public Integer get(U guess, U gold) {
    Pair<U, U> pair = new Pair<>(guess, gold);
    if (confTable.containsKey(pair)) {
      return confTable.get(pair);
    } else {
      return 0;
    }
  }

  /**
   * Returns the set of distinct class labels
   * entered into this confusion table.
   */
  public Set<U> uniqueLabels() {
    HashSet<U> ret = new HashSet<>();
    for (Pair<U, U> pair : confTable.keySet()) {
      ret.add(pair.first());
      ret.add(pair.second());
    }
    return ret;
  }

  /**
   * Returns the contingency table for the given class label, where all other
   * class labels are treated as negative.
   */
  public Contingency getContingency(U positiveLabel) {
    int tp = 0;
    int fp = 0;
    int tn = 0;
    int fn = 0;
    for (Pair<U, U> pair : confTable.keySet()) {
      int count = confTable.get(pair);
      U guess = pair.first();
      U gold = pair.second();
      boolean guessP = guess.equals(positiveLabel);
      boolean goldP = gold.equals(positiveLabel);
      if (guessP && goldP) {
        tp += count;
      } else if (!guessP && goldP) {
        fn += count;
      } else if (guessP && !goldP) {
        fp += count;
      } else {
        tn += count;
      }
    }
    return new Contingency(tp, fp, tn, fn);
  }

  /**
   * Returns the current set of unique labels, sorted by their string order.
   */
  private List<U> sortKeys() {
    Set<U> labels = uniqueLabels();
    if (labels.size() == 0) {
      return Collections.emptyList();
    }

    boolean comparable = true;
    for (U label : labels) {
      if (!(label instanceof Comparable)) {
        comparable = false;
        break;
      }
    }
    if (comparable) {
      List<Comparable<Object>> sorted = Generics.newArrayList();
      for (U label : labels) {
        sorted.add(ErasureUtils.<Comparable<Object>>uncheckedCast(label));
      }
      Collections.sort(sorted);
      List<U> ret = Generics.newArrayList();
      for (Object o : sorted) {
        ret.add(ErasureUtils.<U>uncheckedCast(o));
      }
      return ret;
    } else {
      ArrayList<String> names = new ArrayList<>();
      HashMap<String, U> lookup = new HashMap<>();
      for (U label : labels) {
        names.add(label.toString());
        lookup.put(label.toString(), label);
      }
      Collections.sort(names);

      ArrayList<U> ret = new ArrayList<>();
      for (String name : names) {
        ret.add(lookup.get(name));
      }
      return ret;
    }
  }

  /**
   * Marginal over the given gold, or column sum
   */
  private Integer goldMarginal(U gold) {
    Integer sum = 0;
    Set<U> labels = uniqueLabels();
    for (U guess : labels) {
      sum += get(guess, gold);
    }
    return sum;
  }

  /**
   * Marginal over given guess, or row sum
   */
  private Integer guessMarginal(U guess) {
    Integer sum = 0;
    Set<U> labels = uniqueLabels();
    for (U gold : labels) {
      sum += get(guess, gold);
    }
    return sum;
  }

  private String getPlaceHolder(int index, U label) {
    if (useRealLabels) {
      return label.toString();
    } else {
      return CLASS_PREFIX + (index + 1); // class name
    }
  }

  /**
   * Prints the current confusion in table form to a string, with contingency
   */
  public String printTable() {
    List<U> sortedLabels = sortKeys();
    if (confTable.size() == 0) {
      return "Empty table!";
    }
    StringWriter ret = new StringWriter();

    // header row (top)
    ret.write(StringUtils.padLeft("Guess/Gold", leftPadSize));
    for (int i = 0; i < sortedLabels.size(); i++) {
      String placeHolder = getPlaceHolder(i, sortedLabels.get(i));
      // placeholder
      ret.write(StringUtils.padLeft(placeHolder, delimPadSize));
    }
    ret.write("    Marg. (Guess)");
    ret.write("\n");

    // Write out contents
    for (int guessI = 0; guessI < sortedLabels.size(); guessI++) {
      String placeHolder = getPlaceHolder(guessI, sortedLabels.get(guessI));
      ret.write(StringUtils.padLeft(placeHolder, leftPadSize));
      U guess = sortedLabels.get(guessI);
      for (U gold : sortedLabels) {
        Integer value = get(guess, gold);
        ret.write(StringUtils.padLeft(value.toString(), delimPadSize));
      }
      ret.write(StringUtils.padLeft(guessMarginal(guess).toString(), delimPadSize));
      ret.write("\n");
    }

    // Bottom row, write out marginals over golds
    ret.write(StringUtils.padLeft("Marg. (Gold)", leftPadSize));
    for (U gold : sortedLabels) {
      ret.write(StringUtils.padLeft(goldMarginal(gold).toString(), delimPadSize));
    }

    // Print out key, along with contingencies
    ret.write("\n\n");
    for (int labelI = 0; labelI < sortedLabels.size(); labelI++) {
      U classLabel = sortedLabels.get(labelI);
      String placeHolder = getPlaceHolder(labelI, classLabel);
      ret.write(StringUtils.padLeft(placeHolder, leftPadSize));
      if (!useRealLabels) {
        ret.write(" = ");
        ret.write(classLabel.toString());
      }
      ret.write(StringUtils.padLeft("", delimPadSize));
      Contingency contingency = getContingency(classLabel);
      ret.write(contingency.toString());
      ret.write("\n");
    }

    return ret.toString();
  }


  private class ConfusionGrid extends Canvas {

    public class Grid extends JPanel {
      private int columnCount = uniqueLabels().size() + 1;
      private int rowCount = uniqueLabels().size() + 1;
      private List<Rectangle> cells;
      private Point selectedCell;

      public Grid() {
        cells = new ArrayList<>(columnCount * rowCount);
        MouseAdapter mouseHandler;
        mouseHandler = new MouseAdapter() {
          @Override
          public void mouseMoved(MouseEvent e) {
            int width = getWidth();
            int height = getHeight();
            int cellWidth = width / columnCount;
            int cellHeight = height / rowCount;
            int column = e.getX() / cellWidth;
            int row = e.getY() / cellHeight;
            selectedCell = new Point(column, row);
            repaint();
          }
        };
        addMouseMotionListener(mouseHandler);
      }

      public void onMouseOver(Graphics2D g2d, Rectangle cell, U guess, U gold) {
        // Compute values
        int x = (int) (cell.getLocation().x + cell.getWidth() / 5.0);
        int y = (int) ( cell.getLocation().y + cell.getHeight() / 5.0);
        // Compute the text
        Integer value = confTable.get(Pair.makePair(guess, gold));
        if (value == null) { value = 0; }
        String text = "Guess: " + guess.toString() + "\n" +
            "Gold: " + gold.toString() + "\n" +
            "Value: " + value;
        // Set the font
        Font bak = g2d.getFont();
        g2d.setFont(bak.deriveFont(bak.getSize() * 2.0f));
        // Render
        g2d.setColor(Color.WHITE);
        g2d.fill(cell);
        g2d.setColor(Color.BLACK);
        for (String line : text.split("\n")) {
          g2d.drawString(line, x, y += g2d.getFontMetrics().getHeight());
        }

        // Reset
        g2d.setFont(bak);
      }

      @Override
      public Dimension getPreferredSize() {
        return new Dimension(800, 800);
      }

      @Override
      public void invalidate() {
        cells.clear();
        super.invalidate();
      }

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Dimensions
        Graphics2D g2d = (Graphics2D) g.create();
        g.setFont(new Font("Arial", Font.PLAIN, 10));
        int width = getWidth();
        int height = getHeight();
        int cellWidth = width / columnCount;
        int cellHeight = height / rowCount;
        int xOffset = (width - (columnCount * cellWidth)) / 2;
        int yOffset = (height - (rowCount * cellHeight)) / 2;

        // Get label index
        List<U> labels = uniqueLabels().stream().collect(Collectors.toList());

        // Get color gradient
        int maxDiag = 0;
        int maxOffdiag = 0;
        for (Map.Entry<Pair<U, U>, Integer> entry : confTable.entrySet()) {
          if (entry.getKey().first == entry.getKey().second) {
            maxDiag = Math.max(maxDiag, entry.getValue());
          } else {
            maxOffdiag = Math.max(maxOffdiag, entry.getValue());
          }
        }

        // Render the grid
        float[] hsb = new float[3];
        for (int row = 0; row < rowCount; row++) {
          for (int col = 0; col < columnCount; col++) {
            // Position
            int x = xOffset + (col * cellWidth);
            int y = yOffset + (row * cellHeight);
            float xCenter = xOffset + (col * cellWidth) + cellWidth / 3.0f;
            float yCenter = yOffset + (row * cellHeight) + cellHeight / 2.0f;
            // Get text + Color
            String text;
            Color bg = Color.WHITE;
            if (row == 0 && col == 0) {
              text = "V guess | gold >";
            } else if (row == 0) {
              text = labels.get(col - 1).toString();
            } else if (col == 0) {
              text = labels.get(row - 1).toString();
            } else {
              // Set value
              Integer count = confTable.get(Pair.makePair(labels.get(row - 1), labels.get(col - 1)));
              if (count == null) {
                count = 0;
              }
              text = "" + count;
              // Get color
              if (row == col) {
                double percentGood = ((double) count) / ((double) maxDiag);
                hsb = Color.RGBtoHSB(
                    (int) (255 - (255.0 * percentGood)),
                    (int) (255 - (255.0 * percentGood / 2.0)),
                    (int) (255 - (255.0 * percentGood)),
                    hsb
                );
                bg = Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
              } else {
                double percentBad = ((double) count) / ((double) maxOffdiag);
                hsb = Color.RGBtoHSB(
                    (int) (255 - (255.0 * percentBad / 2.0)),
                    (int) (255 - (255.0 * percentBad)),
                    (int) (255 - (255.0 * percentBad)),
                    hsb
                );
                bg = Color.getHSBColor(hsb[0], hsb[1], hsb[2]);

              }
            }
            // Draw
            Rectangle cell = new Rectangle(x, y, cellWidth, cellHeight);
            g2d.setColor(bg);
            g2d.fill(cell);
            g2d.setColor(Color.BLACK);
            g2d.drawString(text, xCenter, yCenter);
            cells.add(cell);
          }
        }

        // Mouse over
        if (selectedCell != null && selectedCell.x > 0 && selectedCell.y > 0) {
          int index = selectedCell.x + (selectedCell.y * columnCount);
          Rectangle cell = cells.get(index);
          onMouseOver(g2d, cell, labels.get(selectedCell.y - 1), labels.get(selectedCell.x - 1));
        }

        // Clean up
        g2d.dispose();
      }
    }

    public ConfusionGrid() {
      EventQueue.invokeLater(() -> {
        try {
          UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ignored) {
        }

        JFrame frame = new JFrame("Confusion Matrix");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(new Grid());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
      });
    }
  }

  /**
   * Show the confusion matrix in a GUI.
   */
  public void gui() {
    ConfusionGrid gui = new ConfusionGrid();
    gui.setVisible(true);
  }

  public static void main(String[] args) {
    ConfusionMatrix<String> confusion = new ConfusionMatrix<>();
    confusion.add("a", "a");
    confusion.add("a", "b");
    confusion.add("b", "a");
    confusion.add("a", "a");
    confusion.add("b", "b");
    confusion.add("b", "b");
    confusion.add("a", "b");
    confusion.gui();
  }

}
