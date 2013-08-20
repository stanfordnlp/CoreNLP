package edu.stanford.nlp.ie.ui;

import edu.stanford.nlp.ie.ExtractorMediator;
import edu.stanford.nlp.ie.OntologyMediator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Modal dialog for assigning a class slot to an extractor field.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class AssignExtractorDialog extends javax.swing.JDialog {
  /**
   * 
   */
  private static final long serialVersionUID = -2216555802061630098L;
  private final ExtractorMediator em;
  private final OntologyMediator om;
  private int response;

  /**
   * Constructs a new dialog for assigning a class slot to an extractor field.
   *
   * @param parent window this dialog should be modal with respect to
   * @param e      mediator to restrict possible extractor and field values (if not null)
   * @param o      mediator to restrict possible class and slot values (if not null)
   */
  public AssignExtractorDialog(java.awt.Frame parent, ExtractorMediator e, OntologyMediator o) {
    super(parent, true);

    em = e;
    om = o;

    initComponents();
    getRootPane().setDefaultButton(okButton);

    // hitting esc is like hitting cancel
    JComponent contentPane = (JComponent) getContentPane();
    contentPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
    contentPane.getActionMap().put("cancel", new AbstractAction() {
      /**
       * 
       */
      private static final long serialVersionUID = -4136145430797391042L;

      public void actionPerformed(ActionEvent ae) {
        setResponse(JOptionPane.CANCEL_OPTION);
      }
    });

    if (om != null) {
      classChooser.setEditable(false);
      slotChooser.setEditable(false);
      classChooser.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            // loads the slots for the newly selected class
            String className = (String) e.getItem();
            List<String> slotNames = new ArrayList<String>(om.getSlotNames(className));
            Collections.sort(slotNames);
            slotChooser.setModel(new DefaultComboBoxModel(slotNames.toArray(new String[0])));
          }
        }
      });
      List<String> classNames = new ArrayList<String>(om.getClassNames());
      Collections.sort(classNames);
      classChooser.setModel(new DefaultComboBoxModel(classNames.toArray(new String[0])));
      // hack to trigger the slot chooser to change
      classChooser.setSelectedIndex(-1);
      classChooser.setSelectedIndex(0);
    }

    if (em != null) {
      extractorChooser.setEditable(false);
      fieldChooser.setEditable(false);
      extractorChooser.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            // loads the fields for the newly selected extractor
            String extractorName = (String) e.getItem();
            String[] fieldNames = em.getExtractableFields(extractorName);
            Arrays.sort(fieldNames);
            fieldChooser.setModel(new DefaultComboBoxModel(fieldNames));
          }
        }
      });
      List<String> extractorNames = new ArrayList<String>(em.getExtractorNames());
      Collections.sort(extractorNames);
      extractorChooser.setModel(new DefaultComboBoxModel(extractorNames.toArray(new String[0])));
      // hack to trigger the field chooser to change
      extractorChooser.setSelectedIndex(-1);
      extractorChooser.setSelectedIndex(0);
    }
  }

  /**
   * Displays the dialog in modal form centered on the given component.
   * Once the user has made an assignment or cancelled or closed the dialog,
   * returns the dialog response (ala JOptionPane). If the response is OK_OPTION,
   * use the various getXXX methods to get the class/slot/extractor/field values.
   *
   * @param parent component to center this dialog on (if not null)
   * @return response type (JOptionPane constant), one of: OK_OPTION, CANCEL_OPTION, CLOSED_OPTION
   */
  public int showDialog(Component parent) {
    if (parent != null) {
      setLocation(parent.getLocationOnScreen().x + (parent.getWidth() - getWidth()) / 2, parent.getLocationOnScreen().y + (parent.getHeight() - getHeight()) / 2);
    }
    super.setVisible(true);
    return (response);
  }

  /**
   * Returns the selected (or entered) class name in the assignment.
   */
  public String getClassName() {
    return ((String) classChooser.getSelectedItem());
  }

  /**
   * Returns the selected (or entered) slot name in the assignment.
   */
  public String getSlotName() {
    return ((String) slotChooser.getSelectedItem());
  }

  /**
   * Returns the selected (or entered) extractor name in the assignment.
   */
  public String getExtractorName() {
    return ((String) extractorChooser.getSelectedItem());
  }

  /**
   * Returns the selected (or entered) field name in the assignment.
   */
  public String getFieldName() {
    return ((String) fieldChooser.getSelectedItem());
  }

  /**
   * Pre-selects (or enters) the given class name in the assignment.
   */
  public void setClassName(String className) {
    classChooser.setSelectedItem(className);
  }

  /**
   * Sets the user response to the dialog and closes it.
   * Response should be one of JOptionPane.OK_OPTION, CANCEL_OPTION, or CLOSED_OPTION.
   * Checks to ensure all fields have a value before accpeting.
   */
  private void setResponse(int response) {
    if (response != JOptionPane.OK_OPTION || isValidated()) {
      this.response = response;
      setVisible(false);
    }
  }

  /**
   * Returns whether all 4 choosers are validated (have real values).
   */
  private boolean isValidated() {
    return (isValidated(classChooser, "You must enter a class name") && isValidated(slotChooser, "You must enter a slot name") && isValidated(extractorChooser, "You must enter an extractor name") && isValidated(fieldChooser, "You must enter a field name"));
  }

  /**
   * Returns whether the given chooser has a non-empty value.
   * If it's empty, displays the given message and selects the chooser.
   */
  private boolean isValidated(JComboBox chooser, String invalidMessage) {
    if (chooser.getSelectedItem() == null || chooser.getSelectedItem().toString().length() == 0) {
      JOptionPane.showMessageDialog(this, invalidMessage);
      if (chooser.isEditable()) {
        chooser.getEditor().getEditorComponent().requestFocus();
      } else {
        chooser.requestFocus();
      }
      return (false);
    }
    return (true);
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
    jLabel2 = new javax.swing.JLabel();
    classChooser = new javax.swing.JComboBox();
    jLabel3 = new javax.swing.JLabel();
    slotChooser = new javax.swing.JComboBox();
    jLabel4 = new javax.swing.JLabel();
    extractorChooser = new javax.swing.JComboBox();
    jLabel5 = new javax.swing.JLabel();
    fieldChooser = new javax.swing.JComboBox();
    jPanel2 = new javax.swing.JPanel();
    okButton = new javax.swing.JButton();
    cancelButton = new javax.swing.JButton();

    setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
    setTitle("Assign Extractor");
    addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosing(java.awt.event.WindowEvent evt) {
        closeDialog(evt);
      }
    });

    jPanel1.setLayout(new java.awt.GridLayout(4, 2));

    jPanel1.setBorder(new javax.swing.border.TitledBorder("Assign an extractor field to fill a class slot:"));
    jLabel2.setText("Class:");
    jPanel1.add(jLabel2);

    classChooser.setEditable(true);
    jPanel1.add(classChooser);

    jLabel3.setText("Slot:");
    jPanel1.add(jLabel3);

    slotChooser.setEditable(true);
    jPanel1.add(slotChooser);

    jLabel4.setText("Extractor:");
    jPanel1.add(jLabel4);

    extractorChooser.setEditable(true);
    jPanel1.add(extractorChooser);

    jLabel5.setText("Field:");
    jPanel1.add(jLabel5);

    fieldChooser.setEditable(true);
    jPanel1.add(fieldChooser);

    getContentPane().add(jPanel1, java.awt.BorderLayout.NORTH);

    jPanel2.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));

    okButton.setText("OK");
    okButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        okButtonActionPerformed(evt);
      }
    });

    jPanel2.add(okButton);

    cancelButton.setText("Cancel");
    okButton.setPreferredSize(cancelButton.getPreferredSize());
    cancelButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cancelButtonActionPerformed(evt);
      }
    });

    jPanel2.add(cancelButton);

    getContentPane().add(jPanel2, java.awt.BorderLayout.SOUTH);

    pack();
  }//GEN-END:initComponents

  private void okButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_okButtonActionPerformed
  {//GEN-HEADEREND:event_okButtonActionPerformed
    setResponse(JOptionPane.OK_OPTION);
  }//GEN-LAST:event_okButtonActionPerformed

  private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cancelButtonActionPerformed
  {//GEN-HEADEREND:event_cancelButtonActionPerformed
    setResponse(JOptionPane.CANCEL_OPTION);
  }//GEN-LAST:event_cancelButtonActionPerformed

  /**
   * Closes the dialog
   */
  private void closeDialog(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_closeDialog
    setResponse(JOptionPane.CLOSED_OPTION);
  }//GEN-LAST:event_closeDialog

  /**
   * @param args the command line arguments
   */
  public static void main(String args[]) {
    new AssignExtractorDialog(new javax.swing.JFrame(), null, null).setVisible(true);
  }


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JLabel jLabel4;
  private javax.swing.JLabel jLabel3;
  private javax.swing.JLabel jLabel2;
  private javax.swing.JPanel jPanel2;
  private javax.swing.JButton okButton;
  private javax.swing.JComboBox classChooser;
  private javax.swing.JComboBox slotChooser;
  private javax.swing.JButton cancelButton;
  private javax.swing.JComboBox fieldChooser;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JComboBox extractorChooser;
  private javax.swing.JLabel jLabel5;
  // End of variables declaration//GEN-END:variables

}
