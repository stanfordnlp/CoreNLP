package edu.stanford.nlp.ie.ner.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;

/**
 * GUI tool for viewing and analyzing the results of a Named Entity
 * Recognition (NER) task.
 * <p/>
 * To execute from the command-line, type:
 * <pre>java edu.stanford.nlp.ie.ner.ui.NERResultVisualizer tokenizedTestFile evaluationOutputFile]</pre>
 * The <code>tokenizedTestFile</code> is a one-sentence-per-line
 * <code>.tok</code> file not a multicolumn file.
 * </p>
 * <p/>
 * HN: TODO: Currently specific to the BioCreative task, but will hopefully be generalized
 * </p>
 *
 * @author Huy Nguyen
 */
public class NERResultVisualizer extends javax.swing.JFrame implements SwingConstants {
  /**
   * 
   */
  private static final long serialVersionUID = 5672332551703339626L;
  private JFileChooser jfc;
  private NERResultTableModel resultsTableModel;
  private NERResultViewer resultViewer;

  private final Color unviewedBGColor = new Color(255, 240, 240); // table row bg color for messages that haven't been opened
  public static final Color TP_COLOR = new Color(191, 255, 191); // bg color for true positives
  public static final Color FP_COLOR = new Color(225, 100, 100); // bg color for false positives
  public static final Color FN_COLOR = new Color(175, 200, 255); // bg color for false negatives

  public final TPFilter tpFilter = new TPFilter();
  public final FPFilter fpFilter = new FPFilter();
  public final FNFilter fnFilter = new FNFilter();
  public final ErrorFilter errorFilter = new ErrorFilter();
  public final NonZeroFilter nzFilter = new NonZeroFilter();

  public NERResultVisualizer() {
    resultsTableModel = new NERResultTableModel();
    resultViewer = new NERResultViewer(this);
    jfc = new JFileChooser();
    initComponents();
    splitPane.setDividerLocation(0.75);

    JComponent contentPane = (JComponent) getContentPane();
    contentPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_MASK), "goto");
    contentPane.getActionMap().put("goto", new AbstractAction() {
      /**
       * 
       */
      private static final long serialVersionUID = -130128165779152798L;

      public void actionPerformed(ActionEvent ae) {
        gotoItemActionPerformed(null);
      }
    });

    for (int c = 0; c < 2; c++) {
      resultsTable.getColumnModel().getColumn(c).setCellRenderer(new DefaultTableCellRenderer() {
        /**
         * 
         */
        private static final long serialVersionUID = 1201519348432660466L;
        Color defaultBGColor = null;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowIndex, int vColIndex) {
          Component comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, rowIndex, vColIndex);
          if (defaultBGColor == null) {
            defaultBGColor = comp.getBackground();
          }
          if (!isSelected) {
            comp.setBackground(resultsTableModel.hasBeenViewed(rowIndex) ? defaultBGColor : unviewedBGColor);
          }
          return (comp);
        }
      });
    }

    resultsTable.getColumn("TP").setCellRenderer(new DefaultTableCellRenderer() {
      /**
       * 
       */
      private static final long serialVersionUID = -6841855932415441880L;
      Font defaultFont = null;
      Font highlightedFont = null;
      Color defaultBGColor = null;

      @Override
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowIndex, int vColIndex) {
        Component comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, rowIndex, vColIndex);
        if (defaultFont == null) {
          defaultFont = comp.getFont();
          highlightedFont = new Font(defaultFont.getName(), Font.BOLD, defaultFont.getSize());
        }
        if (defaultBGColor == null) {
          defaultBGColor = comp.getBackground();
        }
        int intVal = ((Integer) value).intValue();
        if (!isSelected) {
          if (intVal > 0) {
            comp.setBackground(TP_COLOR);
          } else {
            comp.setBackground(getBGColor(rowIndex, defaultBGColor));
          }
        }
        comp.setForeground(intVal > 0 ? Color.black : Color.lightGray);
        comp.setFont(intVal > 0 ? highlightedFont : defaultFont);
        return (comp);
      }
    });

    resultsTable.getColumn("FP").setCellRenderer(new DefaultTableCellRenderer() {
      /**
       * 
       */
      private static final long serialVersionUID = 2282056029159825343L;
      Font defaultFont = null;
      Font highlightedFont = null;
      Color defaultBGColor = null;

      @Override
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowIndex, int vColIndex) {
        Component comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, rowIndex, vColIndex);
        if (defaultFont == null) {
          defaultFont = comp.getFont();
          highlightedFont = new Font(defaultFont.getName(), Font.BOLD, defaultFont.getSize());
        }
        if (defaultBGColor == null) {
          defaultBGColor = comp.getBackground();
        }
        int intVal = ((Integer) value).intValue();
        if (!isSelected) {
          if (intVal > 0) {
            comp.setBackground(FP_COLOR);
          } else {
            comp.setBackground(getBGColor(rowIndex, defaultBGColor));
          }
        }
        comp.setForeground(intVal > 0 ? Color.black : Color.lightGray);
        comp.setFont(intVal > 0 ? highlightedFont : defaultFont);
        return (comp);
      }
    });

    resultsTable.getColumn("FN").setCellRenderer(new DefaultTableCellRenderer() {
      /**
       * 
       */
      private static final long serialVersionUID = 7896461404089329475L;
      Font defaultFont = null;
      Font highlightedFont = null;
      Color defaultBGColor = null;

      @Override
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowIndex, int vColIndex) {
        Component comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, rowIndex, vColIndex);
        if (defaultFont == null) {
          defaultFont = comp.getFont();
          highlightedFont = new Font(defaultFont.getName(), Font.BOLD, defaultFont.getSize());
        }
        if (defaultBGColor == null) {
          defaultBGColor = comp.getBackground();
        }
        int intVal = ((Integer) value).intValue();
        if (!isSelected) {
          if (intVal > 0) {
            comp.setBackground(FN_COLOR);
          } else {
            comp.setBackground(getBGColor(rowIndex, defaultBGColor));
          }
        }
        comp.setForeground(intVal > 0 ? Color.black : Color.lightGray);
        comp.setFont(intVal > 0 ? highlightedFont : defaultFont);
        return (comp);
      }
    });


  }

  /**
   * Utility method for selecting the background color based on whether the
   * result has been viewed or not.
   */
  private Color getBGColor(int rowIndex, Color defaultBGColor) {
    Color bgColor = resultsTableModel.hasBeenViewed(rowIndex) ? defaultBGColor : unviewedBGColor;
    return (bgColor);
  }

  /**
   * Makes each column wide enough to display the contents of the widest cell.
   * Why I have to do this manually is beyond me.
   * Code taken from: http://javaalmanac.com/egs/javax.swing.table/PackCol.html
   */
  public static void packColumns(JTable table, boolean[] resizable, int[] alignments) {
    int arm = table.getAutoResizeMode();
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    int margin = 2; // extra pixels on each side of largest column
    for (int c = 0; c < table.getColumnCount(); c++) {
      TableColumn col = table.getColumnModel().getColumn(c);
      int width = 0;

      // gets preferred header width
      TableCellRenderer renderer = col.getHeaderRenderer();
      if (renderer == null) {
        renderer = table.getTableHeader().getDefaultRenderer();
      }
      width = renderer.getTableCellRendererComponent(table, col.getHeaderValue(), false, false, 0, 0).getPreferredSize().width;

      // gets max cell width
      for (int r = 0; r < table.getRowCount(); r++) {
        width = Math.max(width, table.getCellRenderer(r, c).getTableCellRendererComponent(table, table.getValueAt(r, c), false, false, r, c).getPreferredSize().width);
      }
      col.setPreferredWidth(width + 2 * margin);
      if (resizable != null && !resizable[c]) {
        col.setMinWidth(width + 2 * margin);
        col.setMaxWidth(width + 2 * margin);
      }
      if (alignments != null && alignments[c] != -1) {
        renderer = col.getCellRenderer();
        if (renderer == null) {
          renderer = new DefaultTableCellRenderer();
          col.setCellRenderer(renderer);
        }
        ((DefaultTableCellRenderer) renderer).setHorizontalAlignment(alignments[c]);
      }
    }
    table.setPreferredScrollableViewportSize(table.getPreferredSize());
    table.setAutoResizeMode(arm);
  }

  private void loadTestFile(File file) {
    int numDocs = resultsTableModel.loadTestFile(file);
    setStatus(numDocs + " documents. To continue, load evaluation file.");
    packColumns(resultsTable, new boolean[]{false, true, false, false, false}, new int[]{LEFT, LEFT, CENTER, CENTER, CENTER});
    resultViewer.setTotal(numDocs);
    gotoDocument(0);
    openEvalFileItem.setEnabled(true);
  }

  private void loadEvalFile(File file) {
    int numMatches = resultsTableModel.loadEvalFile(file);
    setStatus(numMatches + " matches.");
  }

  /**
   * Sets the status text at the bottom-left corner of the frame.
   */
  private void setStatus(String status) {
    statusLabel.setText(status);
  }

  /**
   * Opens a NERResultViewer to show the results of NER for the document at the given index.
   */
  public void setResult(int index) {
    resultsTable.setRowSelectionInterval(index, index);
    resultViewer.setResult(index, resultsTableModel.getResult(index));
  }

  /**
   * Attempts to find the index of a document with the given id.  Returns -1 if a match could not be found.
   */
  private int findDocument(String id) {
    for (int i = 0; i < resultsTableModel.getRowCount(); i++) {
      if (resultsTableModel.getDocumentID(i).indexOf(id) != -1) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Scrolls to the given document index
   */
  private void gotoDocument(int index) {
    // attempts to center the new selected row in the scroll view
    int visibleHeight = resultsTable.getVisibleRect().height;
    int visibleRows = visibleHeight / (resultsTable.getRowHeight() + resultsTable.getRowMargin());
    int endRow = 0;
    // if the new row is below the currently selected row, attempts to scroll until
    // visibleRows/2 rows AFTER the new row are visible, else, attempts to scroll until
    // visibleRows/2 rows BEFORE the new row are visible
    if (index > resultsTable.getSelectedRow()) {
      endRow = Math.min(resultsTableModel.getRowCount(), index + visibleRows / 2);
    } else {
      endRow = Math.min(0, index - visibleRows / 2);
    }
    resultsTable.scrollRectToVisible(resultsTable.getCellRect(endRow, 0, true));
    setResult(index);
  }

  /**
   * Unchecks all the checkboxes in the filter menu
   */
  private void clearFilterBoxes() {
    allFilterItem.setSelected(false);
    tpFilterItem.setSelected(false);
    fpFilterItem.setSelected(false);
    fnFilterItem.setSelected(false);
    errorFilterItem.setSelected(false);
    nzFilterItem.setSelected(false);
  }

  /**
   * Sets the document filter. Tries to stay at the current document, if it is still valid.
   */
  private void setFilter(NERResultFilter filter) {
    String id = resultsTableModel.getResult(resultsTable.getSelectedRow()).getID();
    resultsTableModel.setFilter(filter);
    setStatus(resultsTableModel.getNumDocuments() + " documents.");
    int index = findDocument(id);
    if (index != -1) {
      gotoDocument(index);
    } else {
      gotoDocument(0);
    }
  }

  /**
   * Filters out results without true positives
   */
  private class TPFilter implements NERResultFilter {
    public boolean filter(NERResult result) {
      return (result.getTP() < 1);
    }
  }

  /**
   * Filters out results without false positives
   */
  private class FPFilter implements NERResultFilter {
    public boolean filter(NERResult result) {
      return (result.getFP() < 1);
    }
  }

  /**
   * Filters out results without false negatives
   */
  private class FNFilter implements NERResultFilter {
    public boolean filter(NERResult result) {
      return (result.getFN() < 1);
    }
  }

  /**
   * Filters out results without any errors
   */
  private class ErrorFilter implements NERResultFilter {
    public boolean filter(NERResult result) {
      return (result.getFN() < 1 && result.getFP() < 1);
    }
  }

  /**
   * Filters out results without a single TP, FP, or FN
   */
  private class NonZeroFilter implements NERResultFilter {
    public boolean filter(NERResult result) {
      return (result.getTP() < 1 && result.getFN() < 1 && result.getFP() < 1);
    }
  }

  /**
   * This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  private void initComponents()//GEN-BEGIN:initComponents
  {
    splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, jPanel1, resultViewer);
    jPanel1 = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    resultsTable = new javax.swing.JTable();
    jPanel2 = new javax.swing.JPanel();
    statusLabel = new javax.swing.JLabel();
    jMenuBar1 = new javax.swing.JMenuBar();
    fileMenu = new javax.swing.JMenu();
    openTestFileItem = new javax.swing.JMenuItem();
    openEvalFileItem = new javax.swing.JMenuItem();
    jSeparator1 = new javax.swing.JSeparator();
    exitItem = new javax.swing.JMenuItem();
    searchMenu = new javax.swing.JMenu();
    gotoItem = new javax.swing.JMenuItem();
    filterMenu = new javax.swing.JMenu();
    allFilterItem = new javax.swing.JCheckBoxMenuItem();
    tpFilterItem = new javax.swing.JCheckBoxMenuItem();
    fpFilterItem = new javax.swing.JCheckBoxMenuItem();
    fnFilterItem = new javax.swing.JCheckBoxMenuItem();
    errorFilterItem = new javax.swing.JCheckBoxMenuItem();
    nzFilterItem = new javax.swing.JCheckBoxMenuItem();

    setTitle("NER Result Visualization Tool");
    addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosing(java.awt.event.WindowEvent evt) {
        exitForm(evt);
      }
    });

    splitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
    jPanel1.setLayout(new java.awt.BorderLayout());

    jPanel1.setBorder(new javax.swing.border.TitledBorder("Results"));
    resultsTable.setModel(resultsTableModel);
    resultsTable.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent evt) {
        resultsTableMouseClicked(evt);
      }
    });

    jScrollPane1.setViewportView(resultsTable);

    jPanel1.add(jScrollPane1, java.awt.BorderLayout.CENTER);

    splitPane.setLeftComponent(jPanel1);

    getContentPane().add(splitPane, java.awt.BorderLayout.CENTER);

    jPanel2.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

    statusLabel.setText("To start, open test file.");
    jPanel2.add(statusLabel);

    getContentPane().add(jPanel2, java.awt.BorderLayout.SOUTH);

    fileMenu.setMnemonic('F');
    fileMenu.setText("File");
    fileMenu.setToolTipText("Open file menu");
    openTestFileItem.setMnemonic('O');
    openTestFileItem.setText("Open Test File");
    openTestFileItem.setToolTipText("Load file containing test documents");
    openTestFileItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        openTestFileItemActionPerformed(evt);
      }
    });

    fileMenu.add(openTestFileItem);
    openEvalFileItem.setMnemonic('E');
    openEvalFileItem.setText("Open Eval File");
    openEvalFileItem.setToolTipText("Load evaluation output file");
    openEvalFileItem.setEnabled(false);
    openEvalFileItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        openEvalFileItemActionPerformed(evt);
      }
    });

    fileMenu.add(openEvalFileItem);
    fileMenu.add(jSeparator1);
    exitItem.setMnemonic('X');
    exitItem.setText("Exit");
    exitItem.setToolTipText("Exit program");
    exitItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        exitItemActionPerformed(evt);
      }
    });

    fileMenu.add(exitItem);
    jMenuBar1.add(fileMenu);
    searchMenu.setMnemonic('S');
    searchMenu.setText("Search");
    searchMenu.setToolTipText("Open search menu");
    gotoItem.setMnemonic('G');
    gotoItem.setText("Go to");
    gotoItem.setToolTipText("Find a document by ID");
    gotoItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        gotoItemActionPerformed(evt);
      }
    });

    searchMenu.add(gotoItem);
    jMenuBar1.add(searchMenu);
    filterMenu.setMnemonic('F');
    filterMenu.setText("Filter");
    filterMenu.setToolTipText("Set filters for results");
    allFilterItem.setMnemonic('a');
    allFilterItem.setSelected(true);
    allFilterItem.setText("Show all");
    allFilterItem.setToolTipText("Show all documents");
    allFilterItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        allFilterItemActionPerformed(evt);
      }
    });

    filterMenu.add(allFilterItem);
    tpFilterItem.setMnemonic('t');
    tpFilterItem.setText("Show true positives");
    tpFilterItem.setToolTipText("Only show documents that contain at least one true positive");
    tpFilterItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        tpFilterItemActionPerformed(evt);
      }
    });

    filterMenu.add(tpFilterItem);
    fpFilterItem.setMnemonic('f');
    fpFilterItem.setText("Show false positives");
    fpFilterItem.setToolTipText("Only show documents that contain at least one false positive");
    fpFilterItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        fpFilterItemActionPerformed(evt);
      }
    });

    filterMenu.add(fpFilterItem);
    fnFilterItem.setMnemonic('n');
    fnFilterItem.setText("Show false negatives");
    fnFilterItem.setToolTipText("Only show documents that contain at least one false negative");
    fnFilterItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        fnFilterItemActionPerformed(evt);
      }
    });

    filterMenu.add(fnFilterItem);
    errorFilterItem.setMnemonic('e');
    errorFilterItem.setText("Show errors");
    errorFilterItem.setToolTipText("Only show documents that contain at least one false positive or one false negative");
    errorFilterItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        errorFilterItemActionPerformed(evt);
      }
    });

    filterMenu.add(errorFilterItem);
    nzFilterItem.setMnemonic('z');
    nzFilterItem.setText("Show non-zero");
    nzFilterItem.setToolTipText("Only shows documents containg at least one TP, FP, or FN.");
    nzFilterItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        nzFilterItemActionPerformed(evt);
      }
    });

    filterMenu.add(nzFilterItem);
    jMenuBar1.add(filterMenu);
    setJMenuBar(jMenuBar1);

    pack();
  }//GEN-END:initComponents

  private void nzFilterItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_nzFilterItemActionPerformed
  {//GEN-HEADEREND:event_nzFilterItemActionPerformed
    clearFilterBoxes();
    nzFilterItem.setSelected(true);
    setFilter(nzFilter);
  }//GEN-LAST:event_nzFilterItemActionPerformed

  private void gotoItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_gotoItemActionPerformed
  {//GEN-HEADEREND:event_gotoItemActionPerformed
    String id = JOptionPane.showInputDialog(this, "Document ID:", "Go to Document", JOptionPane.QUESTION_MESSAGE);
    if (id != null) {
      int index = findDocument(id);
      if (index != -1) {
        gotoDocument(index);
      } else {
        JOptionPane.showMessageDialog(this, "Could not find message " + id, "Message not found", JOptionPane.ERROR_MESSAGE);
      }
    }
  }//GEN-LAST:event_gotoItemActionPerformed

  private void errorFilterItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_errorFilterItemActionPerformed
  {//GEN-HEADEREND:event_errorFilterItemActionPerformed
    clearFilterBoxes();
    errorFilterItem.setSelected(true);
    setFilter(errorFilter);
  }//GEN-LAST:event_errorFilterItemActionPerformed

  private void allFilterItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_allFilterItemActionPerformed
  {//GEN-HEADEREND:event_allFilterItemActionPerformed
    clearFilterBoxes();
    allFilterItem.setSelected(true);
    setFilter(null);
  }//GEN-LAST:event_allFilterItemActionPerformed

  private void fnFilterItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_fnFilterItemActionPerformed
  {//GEN-HEADEREND:event_fnFilterItemActionPerformed
    clearFilterBoxes();
    fnFilterItem.setSelected(true);
    setFilter(fnFilter);
  }//GEN-LAST:event_fnFilterItemActionPerformed

  private void fpFilterItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_fpFilterItemActionPerformed
  {//GEN-HEADEREND:event_fpFilterItemActionPerformed
    clearFilterBoxes();
    fpFilterItem.setSelected(true);
    setFilter(fpFilter);
  }//GEN-LAST:event_fpFilterItemActionPerformed

  private void tpFilterItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_tpFilterItemActionPerformed
  {//GEN-HEADEREND:event_tpFilterItemActionPerformed
    clearFilterBoxes();
    tpFilterItem.setSelected(true);
    setFilter(tpFilter);
  }//GEN-LAST:event_tpFilterItemActionPerformed

  private void resultsTableMouseClicked(java.awt.event.MouseEvent evt)//GEN-FIRST:event_resultsTableMouseClicked
  {//GEN-HEADEREND:event_resultsTableMouseClicked
    if (evt.getClickCount() == 2) {
      int row = resultsTable.rowAtPoint(evt.getPoint());
      if (row != -1) {
        setResult(row);
      }
      resultViewer.setVisible(true);
    }
  }//GEN-LAST:event_resultsTableMouseClicked

  private void exitItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_exitItemActionPerformed
  {//GEN-HEADEREND:event_exitItemActionPerformed
    exitForm(null);
  }//GEN-LAST:event_exitItemActionPerformed

  private void openEvalFileItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_openEvalFileItemActionPerformed
  {//GEN-HEADEREND:event_openEvalFileItemActionPerformed
    if (jfc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      loadEvalFile(jfc.getSelectedFile());
    }
  }//GEN-LAST:event_openEvalFileItemActionPerformed


  private void openTestFileItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_openTestFileItemActionPerformed
  {//GEN-HEADEREND:event_openTestFileItemActionPerformed
    if (jfc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      loadTestFile(jfc.getSelectedFile());
    }
  }//GEN-LAST:event_openTestFileItemActionPerformed

  /**
   * Exit the Application
   */
  private void exitForm(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_exitForm
    System.exit(0);
  }//GEN-LAST:event_exitForm

  /**
   * For viewing BioCreative performance.
   * Usage: NERResultVisualizer testFile evalFile <br>
   * The first file is the input to the test, the second is the output of
   * the BioCreative evaluation program.
   */
  public static void main(String args[]) {
    edu.stanford.nlp.util.DisabledPreferencesFactory.install();

    NERResultVisualizer visualizer = new NERResultVisualizer();

    if (args.length > 0) {
      visualizer.loadTestFile(new File(args[0]));
    }

    if (args.length > 1) {
      visualizer.loadEvalFile(new File(args[1]));
    }

    visualizer.setVisible(true);
  }


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JMenuItem openEvalFileItem;
  private javax.swing.JMenuItem openTestFileItem;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JMenu fileMenu;
  private javax.swing.JLabel statusLabel;
  private javax.swing.JPanel jPanel2;
  private javax.swing.JCheckBoxMenuItem fpFilterItem;
  private javax.swing.JCheckBoxMenuItem fnFilterItem;
  private javax.swing.JCheckBoxMenuItem errorFilterItem;
  private javax.swing.JMenuItem gotoItem;
  private javax.swing.JCheckBoxMenuItem nzFilterItem;
  private javax.swing.JSeparator jSeparator1;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JSplitPane splitPane;
  private javax.swing.JMenu searchMenu;
  private javax.swing.JMenuItem exitItem;
  private javax.swing.JCheckBoxMenuItem tpFilterItem;
  private javax.swing.JTable resultsTable;
  private javax.swing.JCheckBoxMenuItem allFilterItem;
  private javax.swing.JMenu filterMenu;
  private javax.swing.JMenuBar jMenuBar1;
  // End of variables declaration//GEN-END:variables

}
