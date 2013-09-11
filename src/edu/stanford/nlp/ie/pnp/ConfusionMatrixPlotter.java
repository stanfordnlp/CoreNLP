package edu.stanford.nlp.ie.pnp;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.StringTokenizer;

/**
 * Visualization utility for confusion matrices. Pops up a frame with a 2x2 table
 * visualizing each count in the confusion matrix as a circle with area proportional to count.
 * This way (a) it's easy to see where the big and small cells are, and (b) since the radius of
 * the circle is proportional to the square-root of the count, you get a bit of a scaling where
 * large values aren't WAY bigger than small values (sort of like with a log-transform).
 * <p/>
 * To use: <code>java ConfusionMatrixPlotter data-filename</code> where the first line in <code>data-filename</code>
 * looks like the following: <code># categories</code> (where # is the appropriate number), and the remaining
 * lines of <code>data-filename</code> each have a row of the confusion matrix, with the values separated by spaces or tabs.
 * <p/>
 * Here is an example data file:
 * <pre>
 * 4 categories
 * 619     1       24      16
 * 2       332     2       7
 * 7       0       429     56
 * 5       2       22      501
 * </pre>
 * <p/>
 * Free parameters:
 * <UL>
 * <LI>Fraction of cell size that should be taken up by largest circle (i.e. scale factor)
 * <LI>Colors:
 * <UL>
 * <LI>background color
 * <LI>circle fill color
 * <LI>circle border color
 * <LI>label font color
 * </UL>
 * <LI> circle border width
 * </UL>
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class ConfusionMatrixPlotter extends JPanel implements Printable {
  /**
   * 
   */
  private static final long serialVersionUID = 7022376815151486948L;
  public static final Color strokeColor = Color.black; // circle outline color
  public static final Color fillColor = Color.white; // circle fill color
  public static final Color bgColor = Color.white; // background (i.e. outside circle) color
  public static final Color labelColor = Color.black; // text (number) label

  final int[][] values; // n*n confusion matrix (rows=correct, cols=guess, value=count)
  final double maxSize = 0.95; // biggest value in values will have circle with this fraction of cellSize in width
  final JFrame parent; // enclosing JFrame

  /**
   * Constructs a new ConfusionMatrixPlotter for the given frame with the given
   * confusion matrix of data.
   */
  public ConfusionMatrixPlotter(JFrame parent, int[][] values) {
    this.parent = parent;
    this.values = values;

    initComponents();

    // adds the NxN CellPlotters, one for each cell
    setBackground(Color.white);
    setLayout(new GridLayout(values.length, values.length, 0, 0));
    for (int i = 0; i < values.length; i++) {
      for (int j = 0; j < values.length; j++) {
        add(new CellPlotter(values[i][j]));
      }
    }

    // Turns the labels on.
    for (int i = 0; i < getComponentCount(); i++) {
      if (getComponent(i) instanceof CellPlotter) {
        ((CellPlotter) getComponent(i)).setLabelShowing(true);
      }
    }

    // ensures the layour stays square
    parent.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        updateSize();
      }
    });

    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_MASK), "print");
    getActionMap().put("print", new AbstractAction() {
      /**
       * 
       */
      private static final long serialVersionUID = 6807319864478455840L;

      public void actionPerformed(ActionEvent e) {
        doPrint();
      }
    });
  }

  /**
   * Prints the confusion matrix (to a physical printer).
   */
  private void doPrint() {
    PrinterJob printJob = PrinterJob.getPrinterJob();
    printJob.setPrintable(this);
    if (printJob.printDialog()) {
      try {
        printJob.print();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

  }

  /**
   * Paints the confusion matrix for printout.
   */
  public int print(Graphics g, PageFormat pf, int pi) throws PrinterException {
    if (pi >= 1) {
      return (Printable.NO_SUCH_PAGE);
    }
    g.translate(100, 100); // pushes the component into the printable area
    print(g);
    return (Printable.PAGE_EXISTS);
  }

  /**
   * Resizes to be square (keeping the smaller dimension), then resizes the frame.
   */
  private void updateSize() {
    int maxSize = Math.min(getSize().width, getSize().height);
    setPreferredSize(new Dimension(maxSize, maxSize));
    parent.pack();
  }

  /**
   * Component for drawing a single cell with a circle.
   */
  public class CellPlotter extends JPanel {
    /**
     * 
     */
    private static final long serialVersionUID = -1518647886664025286L;
    private BufferedImage bimg; // image to draw circles on
    private int value; // value of cell -> area of circle
    private boolean labelShowing; // is the numerical value printed

    /**
     * Constructs a new CellPlotter representing the given value (count), and with the
     * label showing or not as given.
     */
    public CellPlotter(int value, boolean labelShowing) {
      this.value = value;
      this.labelShowing = labelShowing;
    }

    /**
     * Constructs a new CellPlotter with the given value and labelShowing=false.
     */
    public CellPlotter(int value) {
      this(value, false);
    }

    /**
     * Constructs a new CellPlotter with value=0 and the label showing or not as given.
     */
    public CellPlotter(boolean labelShowing) {
      this(0, labelShowing);
    }

    /**
     * Constructs a new CellPlotter with value=0 and labelShowing=false.
     */
    public CellPlotter() {
      this(0, false);
    }

    /**
     * Creates the image to draw the circle on.
     */
    @SuppressWarnings("unused")
    private Graphics2D createGraphics2D(int w, int h) {
      Graphics2D g2 = null;
      if (bimg == null || bimg.getWidth() != w || bimg.getHeight() != h) {
        bimg = (BufferedImage) createImage(w, h);
      }
      g2 = bimg.createGraphics();
      g2.setBackground(bgColor);
      g2.clearRect(0, 0, getSize().width, getSize().height);
      //g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

      return (g2);
    }

    /**
     * Calls drawCell to paint this cell.
     */
    @Override
    public void paintComponent(Graphics g) {
      super.paintComponent(g);
      drawCell((Graphics2D) g);
    }

    /**
     * Fills in the background, draws the circle, then optionally draws the label.
     */
    protected void drawCell(Graphics2D g2) {
      g2.setBackground(bgColor);
      g2.clearRect(0, 0, getCellSize(), getCellSize());
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      drawCircle(g2);
      if (labelShowing) {
        drawLabel(g2);
      }

    }

    /**
     * Draws the circle centered in the cell. The width of the outline border
     * grows linearly with the size of the cell.
     */
    protected void drawCircle(Graphics2D g2) {
      // creates the centered circle
      double r = getScaledRadius(); // circle radius
      int s = getCellSize();
      double cx = s / 2.0f;
      double cy = s / 2.0f;
      Ellipse2D.Double circle = new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);

      // fills in the circle
      g2.setPaint(fillColor);
      g2.setStroke(new BasicStroke(s * 0.02f));
      g2.fill(circle);

      // draws the circle outline
      g2.setColor(strokeColor);
      g2.draw(circle);

    }

    /**
     * Draws the label (with the number of the value) on the cell. Depending on
     * the size of the circle, the label is either painted inside the circle,
     * or at the lower-right corner. The font size of the label grows linearly
     * with the size of the cell.
     */
    protected void drawLabel(Graphics2D g2) {
      String v = Integer.toString(value); // value as string
      int s = getCellSize(); // # pixels for width/height of cell
      Font f = new Font("Dialog", Font.PLAIN, s / 6); // label font

      g2.setFont(f);
      g2.setColor(labelColor);
      FontMetrics fm = g2.getFontMetrics();

      int sw = fm.stringWidth(v); // width of label
      int sh = fm.getAscent() - fm.getDescent(); // height of label

      float r = (float) getScaledRadius(); // circle radius
      if (r > (3 + sw / 2) && r > (3 + sh / 2)) {
        g2.drawString(v, (s - sw) / 2, (s + sh) / 2);
      } else {
        g2.drawString(v, s / 2 + r, s / 2 + r + fm.getAscent());
      }
    }

    /**
     * Returns the conversion factor from units of radius to pixels for this cell.
     */
    private double getScaleFactor() {
      return (Math.sqrt(Math.PI / getMaxValue(values)) * maxSize * getCellSize());
    }

    /**
     * Returns the corresponding radius for the value of this CellPlotter, scaled using the scale factor.
     */
    private double getScaledRadius() {
      return (Math.sqrt(value / Math.PI) * getScaleFactor() / 2);
    }

    /**
     * Returns the number of pixels on each side of this cell. The cell should be
     * a square at all times, but just to be on the safe side, this returns the minimum
     * of the width and height of the cell.
     */
    private int getCellSize() {
      return (Math.min(getSize().width, getSize().height));
    }

    /**
     * Returns whether to paint the number of the value on the cell.
     */
    public boolean isLabelShowing() {
      return (labelShowing);
    }

    /**
     * Sets whether to paint the number of the value on the cell. Calls repaint() when set.
     */
    public void setLabelShowing(boolean labelShowing) {
      this.labelShowing = labelShowing;
      repaint();
    }

    /**
     * Returns the value (count) represented by this CellPlotter.
     */
    public int getValue() {
      return (value);
    }

    /**
     * Sets the value (count) represented by this CellPlotter. Calls repaint() when set.
     */
    public void setValue(int value) {
      this.value = value;
      repaint();
    }
  }

  /**
   * Reads in a File for a confusion matrix and returns an int[][] with the values.
   * The first line of the file should be "# categories" where # is the right number.
   * The next lines should be one row at a time, with values separated by spaces or tabs.
   */
  public static int[][] readValuesFromFile(String filename) throws FileNotFoundException, IOException {
    int[][] values;

    BufferedReader br = new BufferedReader(new FileReader(filename));
    String line = br.readLine();
    int numCategories = Integer.parseInt(line.substring(0, line.indexOf(' ')));
    values = new int[numCategories][numCategories];
    for (int i = 0; (line = br.readLine()) != null; i++) {
      StringTokenizer st = new StringTokenizer(line);
      int j = 0;
      while (st.hasMoreTokens()) {
        int value = Integer.parseInt(st.nextToken());
        values[i][j++] = value;
      }
    }

    /*
    for(int i=0;i<numCategories;i++)
    {
        for(int j=0;j<numCategories;j++)
            System.err.print((j>0?"\t":"")+values[i][j]);
        System.err.println();
    }
     */
    return (values);

  }

  /**
   * Returns the largest value in the given matrix.
   */
  public static int getMaxValue(int[][] values) {
    int max = -Integer.MAX_VALUE;

    for (int i = 0; i < values.length; i++) {
      for (int j = 0; j < values[i].length; j++) {
        if (values[i][j] > max) {
          max = values[i][j];
        }
      }
    }

    return (max);
  }

  /**
   * Opens a new JFrame for a ConfusionMatrixPlotter to display the contents of the given file.
   * <p><tt>Usage: java ConfusionMatrixPlotter data-filename</tt></p>
   */
  public static void main(String[] args) {
    if (args.length < 1) {
      System.err.println("Usage: java ConfusionMatrixPlotter data-filename");
      System.exit(-1);
    }

    try {
      int[][] values = readValuesFromFile(args[0]);

      JFrame jf = new JFrame("Confusion Matrix Plotter");
      ConfusionMatrixPlotter cmp = new ConfusionMatrixPlotter(jf, values);
      jf.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent we) {
          System.exit(0);
        }
      });
      jf.setContentPane(cmp);
      jf.setVisible(true);
      jf.setBounds(200, 100, values.length * 100, values.length * 100);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  private void initComponents() {//GEN-BEGIN:initComponents
    setLayout(new java.awt.BorderLayout());

  }//GEN-END:initComponents
  // Variables declaration - do not modify//GEN-BEGIN:variables
  // End of variables declaration//GEN-END:variables

}
