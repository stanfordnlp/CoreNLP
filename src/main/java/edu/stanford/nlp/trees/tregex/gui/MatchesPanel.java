package edu.stanford.nlp.trees.tregex.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.MouseInputAdapter;
import javax.swing.text.Highlighter;
import javax.swing.text.Highlighter.Highlight;

import edu.stanford.nlp.swing.TooltipJList;
import edu.stanford.nlp.trees.Constituent;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;


/**
 * Component for displaying the list of trees that match
 * the query.
 *
 * @author Anna Rafferty
 */
@SuppressWarnings("serial")
public class MatchesPanel extends JPanel implements ListSelectionListener {
  private static MatchesPanel instance = null;
  private JList<TreeFromFile> list;
  // todo: Change the below to just be a List<List<Tree>> paralleling list above
  private Map<TreeFromFile,List<Tree>> matchedParts;
  private List<MatchesPanelListener> listeners;
  private Color highlightColor = Color.CYAN;
  private boolean showOnlyMatchedPortion = false;
  private JTextField lastSelected = null;
  private MouseEvent firstMouseEvent = null;
  private int maxMatches = 1000;


  /**
   * Returns the singleton instance of the MatchesPanel
   * @return The singleton instance of the MatchesPanel
   */
  public static synchronized MatchesPanel getInstance() {
    if (instance == null) {
      instance = new MatchesPanel();
    }
    return instance;
  }

  private MatchesPanel() {
    //data
    DefaultListModel<TreeFromFile> model = new DefaultListModel<>();
    list = new TooltipJList(model);
    list.setCellRenderer(new MatchCellRenderer());
    list.setTransferHandler(new TreeTransferHandler());
    matchedParts = Generics.newHashMap();
    list.addListSelectionListener(this);
    MouseInputAdapter mouseListener = new MouseInputAdapter() {
      private boolean dragNDrop = false;
      @Override
      public void mousePressed(MouseEvent e) {
        if (MatchesPanel.getInstance().isEmpty()) return;
        if(firstMouseEvent == null) {
          firstMouseEvent = e;
        }
        e.consume();
        TreeFromFile selectedValue = list.getSelectedValue();
        if(selectedValue == null) return;
        JTextField label = selectedValue.getLabel();
        if(((e.getModifiersEx()) & InputEvent.SHIFT_DOWN_MASK) == InputEvent.SHIFT_DOWN_MASK) {
          //shift is being held
          addHighlight(label, firstMouseEvent, e);
        } else if(!HighlightUtils.isInHighlight(e, label, label.getHighlighter())) {
          label.getHighlighter().removeAllHighlights();
          firstMouseEvent = e;
          dragNDrop = false;
          list.repaint();
        } else {
          //in a highlight, if we drag after this, we'll be DnDing
          dragNDrop = true;
        }
      }

      private boolean addHighlight(JTextField label, MouseEvent mouseEvent1, MouseEvent mouseEvent2) {
        //Two parts: adding the highlight on the label, and scrolling the list appropriately
        //HighlightUtils handles the first part, we handle the second part here
        boolean highlightSuccessful = HighlightUtils.addHighlight(label, mouseEvent1, mouseEvent2);
        FontMetrics fm = label.getFontMetrics(label.getFont());
        int firstXpos = mouseEvent1.getX();
        int lastXpos = mouseEvent2.getX();
        int firstOffset = getCharOffset(fm, label.getText(), firstXpos);
        int lastOffset = getCharOffset(fm, label.getText(), lastXpos);
        if(lastOffset != firstOffset) {
          if(firstOffset > lastOffset) {
            int tmp = firstOffset;
            firstOffset = lastOffset;
            lastOffset = tmp;
          }
          Rectangle curVisible = list.getVisibleRect();
          if(lastXpos > curVisible.x+curVisible.width) {
            list.scrollRectToVisible(new Rectangle(new Point(lastXpos-curVisible.width, curVisible.y), curVisible.getSize()));
          } else if(lastXpos < curVisible.x) {
            list.scrollRectToVisible(new Rectangle(new Point(lastXpos, curVisible.y), curVisible.getSize()));
          }
          list.repaint();
          return highlightSuccessful;
        } else
          return false;
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        if (MatchesPanel.getInstance().isEmpty()) return;

        if (firstMouseEvent != null) {
          e.consume();
          JTextField label = list.getSelectedValue().getLabel();
          if(dragNDrop) {
            if(label == null)
              return;
            if(Point2D.distanceSq(e.getX(), e.getY(), firstMouseEvent.getX(), firstMouseEvent.getY()) > 25) {
              //do DnD
              list.getTransferHandler().exportAsDrag((JComponent) e.getSource(), firstMouseEvent, TransferHandler.COPY);
            }
          } else {
            addHighlight(label, firstMouseEvent, e);
          }
        }
      }

      private int getCharOffset(FontMetrics fm, String characters, int xPos) {
        StringBuilder s = new StringBuilder();
        char[] sArray = characters.toCharArray();
        int i;
        for(i = 0; i < characters.length() && fm.stringWidth(s.toString()) < xPos; i++) {
          s.append(sArray[i]);
        }
        return i;

      }
    };

    list.addMouseMotionListener(mouseListener);
    list.addMouseListener(mouseListener);
    listeners = new ArrayList<>();
    //layout
    this.setLayout(new BorderLayout());
    this.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(),"Matches: "));
    JScrollPane scroller = new JScrollPane(list);
    this.add(scroller, BorderLayout.CENTER);
  }

  public void removeAllMatches() {
    setMatchedParts(Generics.newHashMap());
    ((DefaultListModel) list.getModel()).removeAllElements();
    list.setSelectedIndex(-1);
    this.sendToListeners();
  }

  private static List<Tree> getTreebankAsList(Treebank tb) {
    List<Tree> treeList = new ArrayList<>();
    if (tb != null) {
      treeList.addAll(tb);
    }
    return treeList;
  }


  /**
   * Used to set the trees to be displayed in this panel (which should match
   * the tregex expression).
   *
   * @param matches trees that match the expression
   */
  public void setMatches(List<TreeFromFile> matches, Map<TreeFromFile, List<Tree>> matchedParts) {
    // cdm Nov 2010: I rewrote this so the performance wasn't dreadful.
    // In the old days, one by one updates to active Swing components gave dreadful performance, so
    // I changed that, but that wasn't really the problem, it was that the if part didn't honor maxMatches!
    removeAllMatches();
    final DefaultListModel<TreeFromFile> newModel = new DefaultListModel<>();
    newModel.ensureCapacity(matches.size());

    //Two cases:
    // 1) Trees contain file and sentence annotations -> we can display differences
    // 2) Trees do not contain file and sentence annotations -> we display all matches with no differences
    //
    if(TregexGUI.getInstance().isTdiffEnabled()) {
      FileTreeNode refTreebank = FilePanel.getInstance().getActiveTreebanks().get(0); //First selected treebank is the reference
      String refFileName = refTreebank.getFilename();
      List<Tree> treeList = null;
      Map<TreeFromFile, List<Tree>> filteredMatchedParts = Generics.newHashMap();

      for (TreeFromFile t2 : matches) {
        if (t2.getFilename() == null || t2.getSentenceId() < 0) { //Trees were not read by PennTreeReader.
          newModel.addElement(t2);
          filteredMatchedParts.put(t2, matchedParts.get(t2));

        } else if( ! t2.getFilename().equals(refFileName)) {
          if (treeList == null) //Lazy loading to account for the if statement above
            treeList = getTreebankAsList(refTreebank.getTreebank());

          int treeId = t2.getSentenceId() - 1;
          if(treeId >= treeList.size())
            continue;

          Tree t1 = treeList.get(treeId);
          Tree treeT2 = t2.getTree();
          Set<Constituent> inT1notT2 = Tdiff.markDiff(t1, treeT2);
          t2.setDiffConstituents(inT1notT2);
          t2.setDiffDecoratedTree(treeT2);

          newModel.addElement(t2);
          if(matchedParts != null && matchedParts.containsKey(t2))
            filteredMatchedParts.put(t2, matchedParts.get(t2));
        } //else skip this tree
        if(newModel.size() >= maxMatches) break;
      }
      matchedParts = filteredMatchedParts;

    } else if (!showOnlyMatchedPortion || matchedParts == null) {
      int i = 0;
      for (TreeFromFile t : matches) {
        newModel.addElement(t);
        i++;
        if (i >= maxMatches) break;
      }
    } else {
      int i = 0;
      for (TreeFromFile t : matchedParts.keySet()) {
        List<Tree> curMatches = matchedParts.get(t);
        for (Tree match : curMatches) {
          newModel.addElement(new TreeFromFile(match, t.getFilename()));
          i++;
          if (i >= maxMatches) break;
        }
      }
    }

    if (! newModel.isEmpty()) {
      SwingUtilities.invokeLater(() -> {
        list.setModel(newModel);
        list.setSelectedIndex(0);
        sendToListeners();
      });
    }

    setMatchedParts(matchedParts);
    this.setPreferredSize(this.getSize());
  }



  /**
   * Get the selected tree and its corresponding matched parts
   * @return a tree that matches the tregex expression
   */
  public Pair<TreeFromFile, List<Tree>> getSelectedMatch() {
    if(!isEmpty()) {
      TreeFromFile selectedTree = list.getSelectedValue();
      return new Pair<>(selectedTree, matchedParts.get(selectedTree));
    }
    else
      return null;
  }

  /**
   * Returns all currently displayed matches in string buffer, penn treebank form
   * (suitable for writing out, for instance)
   *
   * @return String filled with the Penn treebank forms of all trees in the matches panel
   */
  public String getMatches() {
    StringBuilder sb = new StringBuilder();
    for(int i = 0, sz = list.getModel().getSize(); i < sz; i++) {
      Tree t = list.getModel().getElementAt(i).getTree();
      sb.append(t.pennString());
      sb.append("\n\n");
    }
    return sb.toString();
  }

  /**
   * Returns all currently displayed sentences in plain text form.
   *
   * @return String filled with the plain text form of all sentences in the matches panel
   */
  public String getMatchedSentences() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0, sz = list.getModel().getSize(); i < sz; i++) {
      String t = list.getModel().getElementAt(i).getLabel().getText();
      sb.append(t);
      sb.append("\n");
    }
    return sb.toString();
  }

  public void selectPreviousMatch() {
    int idx = Math.max(0, list.getSelectedIndex() - 1);
    list.setSelectedIndex(idx);
  }

  public void selectNextMatch() {
    int idx = Math.min(list.getModel().getSize() - 1,
                       list.getSelectedIndex() + 1);
    list.setSelectedIndex(idx);
  }

  /**
   * Determine whether any trees are in the matches panel at this time
   * @return true if trees are present
   */
  public boolean isEmpty() {
    return ((DefaultListModel) list.getModel()).isEmpty();
  }

  /**
   * Allows other panels to be updated about changes to the matches panel
   * (better abstraction)
   * @author rafferty
   *
   */
  public interface MatchesPanelListener {
    void matchesChanged();

  }

  /**
   * Become a listener to changes in the trees the matches panel is showing
   */
  public void addListener(MatchesPanelListener l) {
    listeners.add(l);
  }

  /**
   * Become a listener to changes in which tree is selected
   */
  public void addListener(ListSelectionListener l) {
    list.addListSelectionListener(l);
  }

  private void sendToListeners() {
    for (MatchesPanelListener l : listeners) {
      l.matchesChanged();
    }
  }

  private class MatchCellRenderer extends JLabel implements ListCellRenderer {

    public MatchCellRenderer() {
      setOpaque(true);
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value,
        int index, boolean isSelected, boolean cellHasFocus) {
      JTextField l = ((TreeFromFile) value).getLabel();
      l.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
      l.setOpaque(true);
      if(cellHasFocus || isSelected) {
        l.setBackground(highlightColor);
      } else {
        l.setBackground(Color.WHITE);
      }
      return l;
    }

  }


  private static class TreeTransferHandler extends TransferHandler {

    public TreeTransferHandler() {
      super();
    }

    private static String exportString(JComponent c) {
      JList<TreeFromFile>  list = (JList<TreeFromFile> ) c;
      List<TreeFromFile> values = list.getSelectedValuesList();
      StringBuilder sb = new StringBuilder();
      for (TreeFromFile val : values) {
        Highlighter h = val.getLabel().getHighlighter();
        Highlight[] highlights = h.getHighlights();
        if (highlights == null || highlights.length == 0) {
          sb.append(val.getLabel().getText());
        } else {
          // we have a highlight
          for (Highlight highlight : highlights) {
            sb.append(val.getLabel().getText(), highlight.getStartOffset(), highlight.getEndOffset());
          }
        }
      }
      return sb.toString();
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
      return new StringSelection(exportString(c));
    }

    @Override
    public int getSourceActions(JComponent c) {
      return COPY_OR_MOVE;
    }

  } // end static class TreeTransferHandler


  public Map<TreeFromFile, List<Tree>> getMatchedParts() {
    return matchedParts;
  }

  /**
   * Set the matched parts to the given hash/list - if null is passed in,
   * resets matchedParts to an empty hash.
   */
  private void setMatchedParts(Map<TreeFromFile, List<Tree>> matchedParts) {
    if(matchedParts == null)
      this.matchedParts = Generics.newHashMap();
    else
      this.matchedParts = matchedParts;
  }

  public void setHighlightColor(Color highlightColor) {
    this.highlightColor = highlightColor;
  }

  public boolean isShowOnlyMatchedPortion() {
    return showOnlyMatchedPortion;
  }

  public void setShowOnlyMatchedPortion(boolean showOnlyMatchedPortion) {
    this.showOnlyMatchedPortion = showOnlyMatchedPortion;
  }

  public void setFontName(String fontName) {
    Font curFont = this.getFont();
    Font newFont = new Font(fontName, curFont.getStyle(), curFont.getSize());
    list.setFont(newFont);
  }

  @Override
  public void valueChanged(ListSelectionEvent arg0) {
    TreeFromFile t = list.getSelectedValue();
    if (t == null) {
      lastSelected = null;
      return;
    }
    JTextField curSelected = t.getLabel();
    if(lastSelected != null) {
      if(lastSelected != curSelected) { //get rid of old highlights
        lastSelected.getHighlighter().removeAllHighlights();
        lastSelected = curSelected;
        firstMouseEvent = null;
        lastSelected.repaint();
      }

    } else
      lastSelected = curSelected;
  }

  public void setMaxMatches(int maxMatches) {
    this.maxMatches = maxMatches;
  }

  public void focusOnList() {
    list.requestFocusInWindow();
  }

}
