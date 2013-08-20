package edu.stanford.nlp.ie.ui;


import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.*;

/**
 * Dialog for validating/modifying the values extracted for a set of fields.
 *
 * @author  Huy Nguyen
 */
public class ConfirmExtractedFieldsDialog extends javax.swing.JDialog {
  /**
   * 
   */
  private static final long serialVersionUID = 5472759515045563700L;
  public static final int CANCEL_OPTION = 0;
  public static final int OK_OPTION = 1;

  public static final int NUM_COLUMNS = 4;
  public static final int SLOT_COLUMN = 0;
  public static final int CURRENT_COLUMN = 1;
  public static final int EXTRACTED_COLUMN = 2;
  public static final int OVERWRITE_COLUMN = 3;

  private ExtractedFieldPanel[] fieldPanels;
  private HashMap<String, Integer> indexBySlot;
  private Set<String> slotNames;
  private int status;
  private FontMetrics fm;

  /**
   * Creates new form ConfirmExtractedFieldsDialog
   */
  public ConfirmExtractedFieldsDialog(java.awt.Frame parent, Set<String> slotNames, boolean modal) {
    super(parent, modal);
    initComponents();
    fm = slotLabel.getFontMetrics(slotLabel.getFont());
    getRootPane().setDefaultButton(confirmButton);

    this.slotNames = slotNames;
    fieldPanels = new ExtractedFieldPanel[slotNames.size()];
    indexBySlot = new HashMap<String, Integer>();
    createFieldPanels();

    // sets status to CANCEL_OPTION if dialog window is closed
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent we) {
        status = CANCEL_OPTION;
      }
    });
  }

  /* Creates an ExtractedFieldPanel for each slot */
  private void createFieldPanels() {
    int index = 0;
    ArrayList<String> slotNameList = new ArrayList<String>(slotNames);
    Collections.sort(slotNameList);
    for (Iterator<String> iter = slotNameList.iterator(); iter.hasNext(); index++) {
      String slotName = iter.next();
      ExtractedFieldPanel fieldPanel = new ExtractedFieldPanel(slotName);
      indexBySlot.put(slotName, Integer.valueOf(index));
      fieldPanels[index] = fieldPanel;
      extractedFieldsPanel.add(fieldPanel);
    }
  }

  /**
   * Gets the names of the slots that were extracted
   */
  public Set<String> getSlotNames() {
    return slotNames;
  }

  /**
   * Gets the current value for the slot, null if slot is not found
   */
  public String getCurrentValue(String slot) {
    if (getFieldPanel(slot) != null) {
      return getFieldPanel(slot).getCurrentValue();
    } else {
      return null;
    }
  }

  /**
   * Sets the current value for the slot
   */
  public void setCurrentValue(String slot, String currentValue) {
    ExtractedFieldPanel fieldPanel = getFieldPanel(slot);
    if (fieldPanel != null) {
      fieldPanel.setCurrentValue(currentValue);
    }
  }

  /**
   * Gets the extracted value for the slot, null if slot is not found
   */
  public String getExtractedValue(String slot) {
    if (getFieldPanel(slot) != null) {
      return getFieldPanel(slot).getExtractedValue();
    } else {
      return null;
    }
  }

  /**
   * Sets the extracted value for the slot
   */
  public void setExtractedValue(String slot, String extractedValue) {
    ExtractedFieldPanel fieldPanel = getFieldPanel(slot);
    if (fieldPanel != null) {
      fieldPanel.setExtractedValue(extractedValue);
    }
  }

  /**
   * Whether the overwrite box is checked or not, false if slot is not found
   */
  public boolean getOverwrite(String slot) {
    if (getFieldPanel(slot) != null) {
      return getFieldPanel(slot).getOverwrite();
    } else {
      return false;
    }
  }

  /* Helper method to retrieve a fieldPanel given the name of the slot */
  private ExtractedFieldPanel getFieldPanel(String slot) {
    Integer index = indexBySlot.get(slot);
    if (index == null) {
      return null;
    } else {
      return fieldPanels[index.intValue()];
    }
  }

  /**
   * Returns OK_OPTION or CANCEL_OPTION, indicating whether the dialog values were confirmed or not
   */
  public int getStatus() {
    return status;
  }

  /* synchronizes the column widths within the dialog */
  public void synchronizeWidths() {
    if (fieldPanels == null) {
      return;
    }
    int[] widths = new int[NUM_COLUMNS];
    for (int col = 0; col < NUM_COLUMNS; col++) {
      int maxWidth = 0;
      // initialize max width
      switch (col) {
        case SLOT_COLUMN:
          maxWidth = fm.stringWidth(slotLabel.getText());
          break;
        case CURRENT_COLUMN:
          maxWidth = fm.stringWidth(currentValueLabel.getText());
          break;
        case EXTRACTED_COLUMN:
          maxWidth = fm.stringWidth(extractedValueLabel.getText());
          break;
        case OVERWRITE_COLUMN:
          maxWidth = fm.stringWidth(overwriteLabel.getText());
          break;
      }

      for (int i = 0; i < fieldPanels.length; i++) {
        maxWidth = Math.max(maxWidth, fieldPanels[i].getPreferredWidth(col));
      }

      widths[col] = maxWidth;
    }
    // subtract widths for slot and overwrite, and distribute the rest to current and extracted
    int width = getWidth() - widths[SLOT_COLUMN] - widths[OVERWRITE_COLUMN];
    if (width > 0) {
      int curWidth = width * widths[CURRENT_COLUMN] / (widths[CURRENT_COLUMN] + widths[EXTRACTED_COLUMN]);
      if (widths[CURRENT_COLUMN] <= curWidth) {
        widths[EXTRACTED_COLUMN] = width - widths[CURRENT_COLUMN] - 25;
      } else {
        widths[EXTRACTED_COLUMN] = width - curWidth - 25;
      }
    }

    for (int col = 0; col < NUM_COLUMNS; col++) {
      // resize header columns
      switch (col) {
        case SLOT_COLUMN:
          slotLabel.setPreferredSize(new Dimension(widths[col], slotLabel.getPreferredSize().height));
          break;
        case CURRENT_COLUMN:
          currentValueLabel.setPreferredSize(new Dimension(widths[col], currentValueLabel.getPreferredSize().height));
          break;
        case EXTRACTED_COLUMN:
          extractedValueLabel.setPreferredSize(new Dimension(widths[col], extractedValueLabel.getPreferredSize().height));
          break;
        case OVERWRITE_COLUMN:
          overwriteLabel.setPreferredSize(new Dimension(widths[col], overwriteLabel.getPreferredSize().height));
          break;
      }
      // resize panel columns
      for (int i = 0; i < fieldPanels.length; i++) {
        fieldPanels[i].setPreferredWidth(col, widths[col]);
      }
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
    headerPanel = new javax.swing.JPanel();
    slotLabel = new javax.swing.JLabel();
    currentValueLabel = new javax.swing.JLabel();
    extractedValueLabel = new javax.swing.JLabel();
    overwriteLabel = new javax.swing.JLabel();
    extractedFieldsScrollPane = new javax.swing.JScrollPane();
    extractedFieldsPanel = new javax.swing.JPanel();
    jPanel2 = new javax.swing.JPanel();
    confirmButton = new javax.swing.JButton();
    cancelButton = new javax.swing.JButton();

    setTitle("Extracted Values");
    addComponentListener(new java.awt.event.ComponentAdapter() {
      @Override
      public void componentResized(java.awt.event.ComponentEvent evt) {
        formComponentResized(evt);
      }
    });

    addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosing(java.awt.event.WindowEvent evt) {
        closeDialog(evt);
      }
    });

    headerPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

    slotLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
    slotLabel.setText("Slot");
    headerPanel.add(slotLabel);

    currentValueLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
    currentValueLabel.setText("Current Value");
    headerPanel.add(currentValueLabel);

    extractedValueLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
    extractedValueLabel.setText("Extracted Value");
    headerPanel.add(extractedValueLabel);

    overwriteLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
    overwriteLabel.setText("Overwrite?");
    headerPanel.add(overwriteLabel);

    getContentPane().add(headerPanel, java.awt.BorderLayout.NORTH);

    extractedFieldsPanel.setLayout(new javax.swing.BoxLayout(extractedFieldsPanel, javax.swing.BoxLayout.Y_AXIS));

    extractedFieldsScrollPane.setViewportView(extractedFieldsPanel);

    getContentPane().add(extractedFieldsScrollPane, java.awt.BorderLayout.CENTER);

    confirmButton.setText("Ok");
    confirmButton.setSelected(true);
    confirmButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        confirmButtonActionPerformed(evt);
      }
    });

    jPanel2.add(confirmButton);

    cancelButton.setText("Cancel");
    cancelButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cancelButtonActionPerformed(evt);
      }
    });

    jPanel2.add(cancelButton);

    getContentPane().add(jPanel2, java.awt.BorderLayout.SOUTH);

    pack();
  }//GEN-END:initComponents

  private void formComponentResized(java.awt.event.ComponentEvent evt)//GEN-FIRST:event_formComponentResized
  {//GEN-HEADEREND:event_formComponentResized
    synchronizeWidths();
  }//GEN-LAST:event_formComponentResized

  private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cancelButtonActionPerformed
  {//GEN-HEADEREND:event_cancelButtonActionPerformed
    status = CANCEL_OPTION;
    closeDialog(null);
  }//GEN-LAST:event_cancelButtonActionPerformed

  private void confirmButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_confirmButtonActionPerformed
  {//GEN-HEADEREND:event_confirmButtonActionPerformed
    status = OK_OPTION;
    closeDialog(null);
  }//GEN-LAST:event_confirmButtonActionPerformed

  /**
   * Closes the dialog
   */
  private void closeDialog(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_closeDialog
    setVisible(false);
    dispose();
  }//GEN-LAST:event_closeDialog

  /**
   * @param args the command line arguments
   */
  public static void main(String args[]) {
    HashSet<String> slotNames = new HashSet<String>();
    slotNames.add("Test");
    new ConfirmExtractedFieldsDialog(new javax.swing.JFrame(), slotNames, true).setVisible(true);
  }


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JLabel extractedValueLabel;
  private javax.swing.JLabel slotLabel;
  private javax.swing.JPanel headerPanel;
  private javax.swing.JPanel jPanel2;
  private javax.swing.JLabel currentValueLabel;
  private javax.swing.JButton confirmButton;
  private javax.swing.JScrollPane extractedFieldsScrollPane;
  private javax.swing.JButton cancelButton;
  private javax.swing.JLabel overwriteLabel;
  private javax.swing.JPanel extractedFieldsPanel;
  // End of variables declaration//GEN-END:variables

}
