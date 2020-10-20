package edu.stanford.nlp.trees.tregex.gui; 
import edu.stanford.nlp.util.logging.Redwood;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.List;

import javax.swing.SwingConstants;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.parser.ui.TreeJPanel;
import edu.stanford.nlp.trees.Constituent;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.IntPair;

/**
 * Component for displaying a tree in a JPanel that works correctly with
 * scrolling.
 * <br>
 * This panel also incorporates the text of the tree below the tree itself. 
 *
 * @author Anna Rafferty
 */
@SuppressWarnings("serial")
public class ScrollableTreeJPanel extends TreeJPanel    {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ScrollableTreeJPanel.class);

  private int fontSize = 12;
  private Color defaultColor = Color.BLACK;
  private Color matchedColor = Color.RED;
  private Color tdiffColor = Color.BLUE;
  private String fontName = "";
  private int style = Font.PLAIN;
  private Dimension preferredSize = null;

  private List<Tree> matchedParts = new ArrayList<>();
  private List<Point2D.Double> matchedPartCoordinates = new ArrayList<>();

  public ScrollableTreeJPanel() {
    super();
  }

  public ScrollableTreeJPanel(int i, int j) {
   super(i,j);
  }

  @Override
  public void paintComponent(Graphics g) {
    superPaint(g);
    Graphics2D g2 = (Graphics2D) g;
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    Font font;
    if ("".equals(fontName)) {
      font = g2.getFont();
      fontName = font.getName();
      style = font.getStyle();
    }

    if(tree != null)
      yieldOffsets = new float[tree.yield().size()];

    font = new Font(fontName, style, this.fontSize);
    g2.setFont(font);
    FontMetrics fM = g2.getFontMetrics();
    Dimension space = getSize();
    double width = width(tree, fM);
    double height = height(tree, fM);
    yieldHeight = height;
    double startX = 0.0;
    double startY = 0.0;
    if (HORIZONTAL_ALIGN == SwingConstants.CENTER) {
      startX = (space.getWidth() - width) / 2.0;
    }
    if (HORIZONTAL_ALIGN == SwingConstants.RIGHT) {
      startX = space.getWidth() - width;
    }
    if (VERTICAL_ALIGN == SwingConstants.CENTER) {
      startY = (space.getHeight() - height) / 2.0;
    }
    if (VERTICAL_ALIGN == SwingConstants.BOTTOM) {
      startY = space.getHeight() - height;
    }
    leafCtr = 0;
    if (matchedParts != null && matchedParts.contains(tree)) {
      paintTree(tree, new Point2D.Double(startX, startY), g2, fM, matchedColor);
    } else {
      paintTree(tree, new Point2D.Double(startX, startY), g2, fM, defaultColor);
      renderRows(g2,fM, defaultColor);
    }
  }

  private void renderRows(Graphics2D g2, FontMetrics fM, Color defaultColor2) {
    double nodeHeight = fM.getHeight();
    double layerMultiplier = (1.0 + belowLineSkip + aboveLineSkip + parentSkip);
    double layerHeight = nodeHeight * layerMultiplier;

    //Draw the yield
    List<HasWord> sentence = tree.yieldHasWord();
    for(int i = 0; i < sentence.size(); i++) {
      g2.drawString(sentence.get(i).word(), yieldOffsets[i], (float) (yieldHeight + layerHeight));
    }

    //Greedily draw the constituents
    final float rowOrigin = (float) (yieldHeight + 2.0*layerHeight);
    List<List<IntPair>> rows = new ArrayList<>();
    for(Constituent c : diffConstituents) {
      for(int rowIdx = 0; rowIdx < diffConstituents.size(); rowIdx++) {
        float rowHeight = rowOrigin + (float) (rowIdx*layerHeight);
        int ext = (c.end() == (yieldOffsets.length - 1)) ? 0 : 1;
        if(rowIdx >= rows.size()) {
          rows.add(new ArrayList<>());
          rows.get(rowIdx).add(new IntPair(c.start(),c.end()));
          double nodeWidth = fM.stringWidth(c.value());
          g2.drawString(c.value(), yieldOffsets[c.start()], rowHeight);
          try {
            g2.drawLine((int) (yieldOffsets[c.start()] + nodeWidth) + 10, (int) rowHeight, (int) (yieldOffsets[c.end() + ext]) - 15, (int) rowHeight);
          } catch (ArrayIndexOutOfBoundsException e) {
            // This happens if yield of two compared trees do not match.  Just ignore it for now
            // System.err.printf("yieldOffsets.length is %d, c.start() is %d, c.end() is %d, ext is %d%n", yieldOffsets.length, c.start(), c.end(), ext);
          }
          break;

        } else {
          boolean foundOverlap = false;
          for(IntPair span : rows.get(rowIdx)) {
            if(doesOverlap(c,span)) {
              foundOverlap = true;
              break;
            }
          }
          if(!foundOverlap) {
            rows.get(rowIdx).add(new IntPair(c.start(),c.end()));
            double nodeWidth = fM.stringWidth(c.value());
            g2.drawString(c.value(), yieldOffsets[c.start()], rowHeight);
            g2.drawLine((int) (yieldOffsets[c.start()] + nodeWidth) + 10, (int) rowHeight, (int) (yieldOffsets[c.end() + ext]) - 15, (int) rowHeight);
            break;
          }
        }
      }
    }
  }

  private static boolean doesOverlap(Constituent c, IntPair p) {
    if (p.getSource() <= c.start() && p.getTarget() >= c.start())
      return true;
    else if (p.getSource() >= c.start() && p.getTarget() <= c.end())
      return true;
    else if (p.getSource() <= c.end() && p.getTarget() >= c.end())
      return true;
    return false;
  }

  //Tdiff data structures
  private int leafCtr = 0;
  private double yieldHeight;
  private float[] yieldOffsets;


  protected double paintTree(Tree t, Point2D start, Graphics2D g2, FontMetrics fM, Color paintColor) {
    if (t == null) {
      return 0.0;
    }
    String nodeStr = nodeToString(t);
    double nodeWidth = fM.stringWidth(nodeStr);
    double nodeHeight = fM.getHeight();
    double nodeAscent = fM.getAscent();
    WidthResult wr = widthResult(t, fM);
    double treeWidth = wr.width;
    double nodeTab = wr.nodeTab;
    double childTab = wr.childTab;
    double nodeCenter = wr.nodeCenter;
    //double treeHeight = height(t, fM);
    // draw root
    Color curColor = g2.getColor();
    g2.setColor(paintColor);
    g2.drawString(nodeStr, (float) (nodeTab + start.getX()), (float) (start.getY() + nodeAscent));
    g2.setColor(curColor);
    if (t.isLeaf()) {
      yieldOffsets[leafCtr++] = (float) (nodeTab + start.getX());
      return nodeWidth;
    }
    double layerMultiplier = (1.0 + belowLineSkip + aboveLineSkip + parentSkip);
    double layerHeight = nodeHeight * layerMultiplier;
    double childStartX = start.getX() + childTab;
    double childStartY = start.getY() + layerHeight;
    double lineStartX = start.getX() + nodeCenter;
    double lineStartY = start.getY() + nodeHeight * (1.0 + belowLineSkip);
    double lineEndY = lineStartY + nodeHeight * parentSkip;
    // recursively draw children
    for (int i = 0; i < t.children().length; i++) {
      Tree child = t.children()[i];
      double cWidth;
      if(matchedParts != null && matchedParts.contains(child)) {
        // Track where we've painted this matched child
        Point2D.Double coord = new Point2D.Double(childStartX, childStartY);
        matchedPartCoordinates.add(coord);
        cWidth = paintTree(child, coord, g2, fM, matchedColor);
      } else {
        Color col = defaultColor;
        if(((CoreLabel) child.label()).containsKey(CoreAnnotations.DoAnnotation.class))
          col = (((CoreLabel) child.label()).get(CoreAnnotations.DoAnnotation.class)) ? tdiffColor : defaultColor;
        cWidth = paintTree(child, new Point2D.Double(childStartX, childStartY), g2, fM, col);
      }
      // draw connectors
      wr = widthResult(child, fM);
      double lineEndX = childStartX + wr.nodeCenter;
      g2.draw(new Line2D.Double(lineStartX, lineStartY, lineEndX, lineEndY));
      childStartX += cWidth;
      if (i < t.children().length - 1) {
        childStartX += sisterSkip * fM.stringWidth(" ");
      }
    }
    return treeWidth;
  }

  @Override
  public Dimension getPreferredSize() {
    if (preferredSize != null) {
      return preferredSize;
    }
    if (tree == null) {
      return super.getSize();
    }

    FontMetrics fM = getFontMetrics(new Font(fontName, style, fontSize));

    double nodeHeight = fM.getHeight();
    double layerMultiplier = (1.0 + belowLineSkip + aboveLineSkip + parentSkip);
    double layerHeight = nodeHeight * layerMultiplier;
    double layerBuffer = (diffConstituents.size() + 1)*layerHeight;
    layerBuffer += 20.0;

    preferredSize = new Dimension((int)width(tree,fM), (int)(height(tree,fM) + layerBuffer));
    return preferredSize;
  }

  public List<Tree> getMatchedParts() {
    return matchedParts;
  }

  public void setMatchedParts(List<Tree> matchedParts) {
    this.matchedParts = matchedParts;
  }

  public List<Point2D.Double> getMatchedPartCoordinates() {
    return matchedPartCoordinates;
  }

  public int getFontSize() {
    return fontSize;
  }

  public void setFontSize(int fontSize) {
    this.fontSize = fontSize;
    preferredSize = null; // stored info invalidated by font change
  }

  public Color getDefaultColor() {
    return defaultColor;
  }

  public void setDefaultColor(Color defaultColor) {
    this.defaultColor = defaultColor;
  }

  public Color getMatchedColor() {
    return matchedColor;
  }

  public void setMatchedColor(Color matchedColor) {
    this.matchedColor = matchedColor;
  }

  public String getFontName() {
    return fontName;
  }

  public void setFontName(String fontName) {
    this.fontName = fontName;
  }


  private Set<Constituent> diffConstituents = Generics.newHashSet();
  public void setDiffConstituents(Set<Constituent> diffConstituents) {
    this.diffConstituents = diffConstituents;
  }

}
