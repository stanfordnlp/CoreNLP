package edu.stanford.nlp.ie.ner.ui;

import edu.stanford.nlp.util.StringUtils;

import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * GUI frame for viewing a document with the extracted named entities
 * highlighted.  (Rather than this, the class you want to invoke from the
 * command line is NERResultVisualizer.)
 *
 * @author Huy Nguyen (htnguyen@cs.stanford.edu)
 */
public class NERResultViewer extends javax.swing.JPanel {
  /**
   * 
   */
  private static final long serialVersionUID = -3184807995135087025L;
  private static final int NUM_TYPES = 4;
  NERResultVisualizer parent;
  private int index;
  private int total;
  private SimpleAttributeSet[] highlightStyles;
  private Color FPFN_COLOR = new Color(225, 150, 225);

  /**
   * Creates new form NERResultViewer
   */
  public NERResultViewer(NERResultVisualizer parent) {
    this.parent = parent;
    initComponents();
    initHighlighters();
  }

  /**
   * Initializes the highlighters.
   */
  private void initHighlighters() {
    highlightStyles = new SimpleAttributeSet[NUM_TYPES];
    for (int i = 0; i < NUM_TYPES; i++) {
      highlightStyles[i] = new SimpleAttributeSet();
    }

    StyleConstants.setBackground(highlightStyles[NERMatch.TP], NERResultVisualizer.TP_COLOR);
    StyleConstants.setBackground(highlightStyles[NERMatch.FP], NERResultVisualizer.FP_COLOR);
    StyleConstants.setBackground(highlightStyles[NERMatch.FN], NERResultVisualizer.FN_COLOR);
    // highlighter for overlapping FP's and FN's
    StyleConstants.setBackground(highlightStyles[3], FPFN_COLOR);
  }

  /**
   * Sets the total number of documents
   */
  public void setTotal(int total) {
    totalLabel.setText(String.valueOf(total));
    this.total = total;
  }

  /**
   * Sets the result displayed in this frame.
   *
   * @param index the index of this result in the list of results
   */
  public void setResult(int index, NERResult result) {
    numLabel.setText(String.valueOf(index + 1));
    this.index = index;
    idLabel.setText(result.getID());
    textPane.setText(result.getText());
    tpLabel.setText(String.valueOf(result.getTP()));
    fpLabel.setText(String.valueOf(result.getFP()));
    fnLabel.setText(String.valueOf(result.getFN()));
    highlightMatches(result);
    result.markAsViewed();
  }

  /**
   * Highlights all of the matches for the given result.
   */
  private void highlightMatches(NERResult result) {
    String text = textPane.getText();
    ArrayList matchedRanges = new ArrayList();
    for (Iterator iter = result.getMatches().iterator(); iter.hasNext();) {
      NERMatch match = (NERMatch) iter.next();
      // the nth word corresponds to the nth space
      int startIndex = StringUtils.nthIndex(text, ' ', match.getStart());
      if (startIndex == -1) {
        System.err.println("ERROR: highlightMatches: illegal start index: " + match.getStart());
        continue;
      }
      if (startIndex > 0) {
        startIndex++; // move past the space
      }
      int endIndex = StringUtils.nthIndex(text, ' ', match.getEnd() + 1);
      if (endIndex == -1) {
        endIndex = text.length();
      }
      highlightText(startIndex, endIndex, highlightStyles[match.getType()]);
      IndexPair newRange = new IndexPair(match.getStart(), match.getEnd());
      for (Iterator iter2 = matchedRanges.iterator(); iter2.hasNext();) {
        IndexPair range = (IndexPair) iter2.next();
        IndexPair overlap = getOverlap(range, newRange);
        if (overlap != null) {
          startIndex = StringUtils.nthIndex(text, ' ', overlap.start);
          if (startIndex > 0) {
            startIndex++; // move past the space
          }
          endIndex = StringUtils.nthIndex(text, ' ', overlap.end + 1);
          if (endIndex == -1) {
            endIndex = text.length();
          }
          highlightText(startIndex, endIndex, highlightStyles[3]);
        }
      }
      matchedRanges.add(newRange);
    }
  }

  /**
   * class for wrapping a start and end index
   */
  private static class IndexPair {
    public int start;
    public int end;

    public IndexPair(int start, int end) {
      this.start = start;
      this.end = end;
    }

    @Override
    public String toString() {
      return start + " " + end;
    }
  }

  /**
   * Returns the overlapping range or null if no overlap
   */
  private IndexPair getOverlap(IndexPair pair1, IndexPair pair2) {
    if (pair1.start >= pair2.start && pair1.start <= pair2.end) {
      if (pair1.end < pair2.end) {
        return new IndexPair(pair1.start, pair1.end);
      } else {
        return new IndexPair(pair1.start, pair2.end);
      }
    }
    if (pair2.start >= pair1.start && pair2.start <= pair1.end) {
      if (pair1.end < pair2.end) {
        return new IndexPair(pair2.start, pair1.end);
      } else {
        return new IndexPair(pair2.start, pair2.end);
      }
    }
    return null;
  }

  /**
   * Highlights specified text region by changing the character attributes
   */
  private void highlightText(int start, int end, SimpleAttributeSet style) {
    if (end > start) {
      textPane.getStyledDocument().setCharacterAttributes(start, end - start, style, false);
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
    jPanel1 = new javax.swing.JPanel();
    jPanel2 = new javax.swing.JPanel();
    Document = new javax.swing.JLabel();
    numLabel = new javax.swing.JLabel();
    jLabel3 = new javax.swing.JLabel();
    totalLabel = new javax.swing.JLabel();
    jLabel5 = new javax.swing.JLabel();
    idLabel = new javax.swing.JLabel();
    jPanel3 = new javax.swing.JPanel();
    prevButton = new javax.swing.JButton();
    nextButton = new javax.swing.JButton();
    jScrollPane1 = new javax.swing.JScrollPane();
    textPane = new javax.swing.JTextPane();
    jPanel4 = new javax.swing.JPanel();
    jLabel1 = new javax.swing.JLabel();
    tpLabel = new javax.swing.JLabel();
    jLabel2 = new javax.swing.JLabel();
    fpLabel = new javax.swing.JLabel();
    jLabel6 = new javax.swing.JLabel();
    fnLabel = new javax.swing.JLabel();

    setLayout(new java.awt.BorderLayout());

    jPanel1.setLayout(new java.awt.BorderLayout());

    jPanel2.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

    Document.setText("Document");
    jPanel2.add(Document);

    numLabel.setToolTipText("");
    jPanel2.add(numLabel);

    jLabel3.setText("of");
    jPanel2.add(jLabel3);

    jPanel2.add(totalLabel);

    jLabel5.setText("- ID =");
    jPanel2.add(jLabel5);

    jPanel2.add(idLabel);

    jPanel1.add(jPanel2, java.awt.BorderLayout.CENTER);

    prevButton.setText("<");
    prevButton.setToolTipText("Previous document");
    prevButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        prevButtonActionPerformed(evt);
      }
    });

    jPanel3.add(prevButton);

    nextButton.setText(">");
    nextButton.setToolTipText("Next document");
    nextButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        nextButtonActionPerformed(evt);
      }
    });

    jPanel3.add(nextButton);

    jPanel1.add(jPanel3, java.awt.BorderLayout.EAST);

    add(jPanel1, java.awt.BorderLayout.NORTH);

    textPane.setPreferredSize(new java.awt.Dimension(400, 100));
    jScrollPane1.setViewportView(textPane);

    add(jScrollPane1, java.awt.BorderLayout.CENTER);

    jPanel4.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

    jLabel1.setText("TP: ");
    jPanel4.add(jLabel1);

    jPanel4.add(tpLabel);

    jLabel2.setText("FP:");
    jPanel4.add(jLabel2);

    jPanel4.add(fpLabel);

    jLabel6.setText("FN:");
    jPanel4.add(jLabel6);

    jPanel4.add(fnLabel);

    add(jPanel4, java.awt.BorderLayout.SOUTH);

  }//GEN-END:initComponents

  private void nextButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_nextButtonActionPerformed
  {//GEN-HEADEREND:event_nextButtonActionPerformed
    parent.setResult(index + 1);
    nextButton.setEnabled(index != total);
  }//GEN-LAST:event_nextButtonActionPerformed

  private void prevButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_prevButtonActionPerformed
  {//GEN-HEADEREND:event_prevButtonActionPerformed
    parent.setResult(index - 1);
    prevButton.setEnabled(index > 0);
  }//GEN-LAST:event_prevButtonActionPerformed


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JLabel tpLabel;
  private javax.swing.JButton prevButton;
  private javax.swing.JPanel jPanel4;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel3;
  private javax.swing.JLabel numLabel;
  private javax.swing.JLabel fpLabel;
  private javax.swing.JPanel jPanel3;
  private javax.swing.JLabel jLabel2;
  private javax.swing.JPanel jPanel2;
  private javax.swing.JLabel Document;
  private javax.swing.JLabel fnLabel;
  private javax.swing.JLabel totalLabel;
  private javax.swing.JLabel idLabel;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JTextPane textPane;
  private javax.swing.JLabel jLabel6;
  private javax.swing.JButton nextButton;
  private javax.swing.JLabel jLabel5;
  // End of variables declaration//GEN-END:variables

}
