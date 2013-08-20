package edu.stanford.nlp.ie.ui;

import edu.stanford.nlp.ie.ExtractorUtilities;
import edu.stanford.nlp.ie.FieldExtractor;
import edu.stanford.nlp.ie.FieldExtractorCreator;
import edu.stanford.nlp.ie.IllegalPropertyException;
import edu.stanford.nlp.swing.SwingWorker;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;

/**
 * Modal dialog for creating or loading a new FieldExtractor.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class AddExtractorDialog extends javax.swing.JDialog {
  /**
   * 
   */
  private static final long serialVersionUID = 1076073144901284357L;
  private FieldExtractor extractor; // extractor to add
  int response; // dialog response (ok, cancel, closed)

  // steps in the wizard
  private static final int ADD_STEP = 0;
  private static final int NEW_STEP = 1;
  private static final int CONFIRM_STEP = 2;
  private String[] stepLabels = new String[]{"Add", "New", "Confirm"};
  private String[] stepTitles = new String[]{"Create or Load Extractor", "Create New Extractor", "Review and Confirm Extractor"};
  private int currentStep; // where are we in the wizard
  private int lastStep; // where we came from (might skip New step)

  private final AddExtractorPanel addPanel; // step 1

  private FieldExtractorCreator lastCreator; // creator last used
  private NewExtractorPanel lastNewPanel; // panel used with lastCreator

  private boolean changed; // has the user started using the wizard

  private final JDialog progressDialog; // popped up when field extractor is being created
  private Timer progressTimer; // thread that pops up progress dialog

  /**
   * Constructs a new AddExtractorDialog that's modal with respect to the given
   * parent frame and allows the user to select from the given list of
   * FieldExtractorCreators.
   *
   * @param parent   frame this dialog is modal with respect to
   * @param creators full class names of FieldExtractorCreators to use
   */
  public AddExtractorDialog(java.awt.Frame parent, String[] creators) {
    super(parent, true);
    initComponents();

    getRootPane().setDefaultButton(nextButton);

    // hitting esc is like hitting cancel
    JComponent contentPane = (JComponent) getContentPane();
    contentPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
    contentPane.getActionMap().put("cancel", new AbstractAction() {
      /**
       * 
       */
      private static final long serialVersionUID = 8744097815106491199L;

      public void actionPerformed(ActionEvent ae) {
        doCancel(JOptionPane.CANCEL_OPTION);
      }
    });

    // progress bar to pop up when creating field extractor
    JProgressBar progressBar = new JProgressBar();
    progressBar.setIndeterminate(true);
    JOptionPane progressPane = new JOptionPane(new Object[]{"Creating FieldExtractor...", progressBar});
    progressPane.setOptions(new String[]{"Cancel"});
    progressDialog = progressPane.createDialog(this, "Progress...");

    extractor = null;
    lastCreator = null;
    lastNewPanel = null;
    addPanel = new AddExtractorPanel(creators);
    mainPanel.add(addPanel, stepLabels[ADD_STEP]);
    setCurrentStep(ADD_STEP);
    changed = false;
  }

  /**
   * Displays the given step in the wizard.
   */
  private void setCurrentStep(int currentStep) {
    lastStep = this.currentStep; // remember where you came from
    this.currentStep = currentStep;
    titleLabel.setText(stepTitles[currentStep]);
    getMainCardLayout().show(mainPanel, stepLabels[currentStep]);
    backButton.setEnabled(currentStep > 0);
    changed = true;
    pack();
    setCursor(Cursor.getDefaultCursor());
  }

  /**
   * Goes back a step in the wizard.
   */
  private void doBack() {
    setCurrentStep(lastStep);
    lastStep = currentStep - 1; // so hitting back twice does the right thing
    backButton.setEnabled(currentStep > 0);
    nextButton.setText("Next >");
  }

  /**
   * Goes to the next step in the wizard, including error checking, etc.
   */
  private void doNext() {
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    if (currentStep == ADD_STEP && addPanel.createExtractor()) {
      FieldExtractorCreator creator = addPanel.getSelectedFieldExtractorCreator();
      if (creator != lastCreator) {
        // kill old "new extractor" card and add new one
        NewExtractorPanel newPanel = new NewExtractorPanel(creator);
        if (lastNewPanel != null) {
          mainPanel.remove(lastNewPanel);
        }
        mainPanel.add(newPanel, stepLabels[NEW_STEP]);
        lastCreator = creator;
        lastNewPanel = newPanel;
      }
      // show new extractor step
      setCurrentStep(NEW_STEP);
    } else if (currentStep == ADD_STEP || currentStep == NEW_STEP) {
      final SwingWorker worker; // used to load/create extractor
      if (currentStep == ADD_STEP) {
        // spanws the loading of the field extractor in a new thread
        final AddExtractorPanel addPanel = this.addPanel;
        worker = new SwingWorker() {
          @Override
          public Object construct() {
            // loads the extractor in a separate thread
            loadExtractor(addPanel);
            return (null);
          }
        };
      } else {
        // create extractor using creator
        if (lastNewPanel.getName().length() == 0) {
          JOptionPane.showMessageDialog(this, "You must enter a unique name for this FieldExtractor", "Bad FieldExtractorName", JOptionPane.ERROR_MESSAGE);
          lastNewPanel.selectNameField();
          setCursor(Cursor.getDefaultCursor());
          return; // bail out early
        }
        lastNewPanel.updateProperties();

        // spanws the creation of the field extractor in a new thread
        final NewExtractorPanel newPanel = lastNewPanel;
        worker = new SwingWorker() {
          @Override
          public Object construct() {
            // creates the extractor in a separate thread
            createExtractor(newPanel);
            return (null);
          }
        };
      }

      // pops up a progress bar if loading/creating field extractor takes over a second
      progressTimer = new Timer(1000, new ActionListener() {
        public void actionPerformed(ActionEvent ae) {
          progressDialog.setVisible(true); // show modal progress dialog
          worker.interrupt(); // make sure worker has stopped
          setCursor(Cursor.getDefaultCursor());
        }
      });
      progressTimer.setRepeats(false); // only pop up progress bar once
      progressTimer.start();
      worker.start();
    } else if (currentStep == CONFIRM_STEP) {
      setResponse(JOptionPane.OK_OPTION); // done with wizard
    }
  }

  /**
   * Displays confirmation page for given extractor.
   */
  private void confirmExtractor(FieldExtractor extractor) {
    this.extractor = extractor; // save extractor to be returned
    ConfirmExtractorPanel confirmPanel = new ConfirmExtractorPanel(extractor);
    mainPanel.add(confirmPanel, stepLabels[CONFIRM_STEP]);
    setCurrentStep(CONFIRM_STEP);
    nextButton.setText("Finish");
  }

  /**
   * Makes sure the progress bar isn't showing or about to be showing.
   */
  private void haltProgress() {
    progressTimer.stop(); // stop waiting to pop up progress bar
    if (progressDialog.isShowing()) {
      progressDialog.setVisible(true);
    }
    setCursor(Cursor.getDefaultCursor());
  }

  /**
   * Creates and stores a new extractor using the settings on the given panel.
   * Hides the given progress dialog on completion or failure. After calling
   * this method, check whether extractor is null to see if the method failed.
   */
  private void createExtractor(NewExtractorPanel newPanel) {
    extractor = null; // clear out old value
    try {
      extractor = newPanel.getFieldExtractorCreator().createFieldExtractor(newPanel.getName());
      haltProgress(); // done, so make sure progress popup is gone
      confirmExtractor(extractor);
    } catch (IllegalPropertyException e) {
      // explains the property error and selects the relevant property
      haltProgress(); // error, so make sure progress popup is gone
      String errorMessage = "<html>FieldExtractor cannot be created because an illegal property value has been specified:<br>" + "Property Name: <tt><font color=\"red\">" + e.getKey() + "</font></tt><br>" + "Illegal Value: <tt><font color=\"red\">" + e.getValue() + "</font></tt><br>" + "Error Description:<pre><font color=\"red\">" + e.getDescription().replaceAll("\r?\n", "<br>") + "</font></pre>" + "Please correct this error and try again.";
      JOptionPane.showMessageDialog(AddExtractorDialog.this, errorMessage, "Error Creating FieldExtractor", JOptionPane.ERROR_MESSAGE);
      newPanel.selectPropertyField(e.getKey());
    }
  }

  private void loadExtractor(AddExtractorPanel addPanel) {
    extractor = ExtractorUtilities.loadExtractor(new File(addPanel.getSerializedExtractorFilename()));
    haltProgress(); // make sure progress popup is gone
    if (extractor == null) {
      // error loading extractor
      JOptionPane.showMessageDialog(this, "Unable to load serialized FieldExtractor from:\n" + addPanel.getSerializedExtractorFilename(), "Error Loading FieldExtractor", JOptionPane.ERROR_MESSAGE);
      addPanel.selectFilenameField();
    } else {
      confirmExtractor(extractor);
    }
  }

  /**
   * Hides the dialog after presenting a confirmation dialog.
   *
   * @param response the source of this cancellation (JOptionPane.CLOSED_OPTION or
   *                 JOptionPane.CANCEL_OPTION)
   */
  private void doCancel(int response) {
    if (!changed || JOptionPane.showConfirmDialog(getContentPane(), "You have not finished adding an extractor yet. Cancel anyway?") == JOptionPane.YES_OPTION) {
      setResponse(response);
    }
  }

  /**
   * Sets the user response to the dialog and closes it.
   * Response should be one of JOptionPane.OK_OPTION, CANCEL_OPTION, or CLOSED_OPTION.
   * Checks to ensure all fields have a value before accpeting.
   */
  private void setResponse(int response) {
    this.response = response;
    setVisible(false);
  }

  /**
   * Displays the dialog in modal form centered on the given component.
   * Once the user has created an extractor or cancelled or closed the dialog,
   * returns the dialog response (ala JOptionPane). If the response is OK_OPTION,
   * use the getFieldExtractor method to fetch the extractor to add.
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
   * Returns the newly created FieldExtractor to add.
   * The return value of this method should only be trusted if the wizard
   * was completed (i.e. showDialog returned OK_OPTION).
   */
  public FieldExtractor getFieldExtractor() {
    return (extractor);
  }

  /**
   * Returns the layout manager for the main panel.
   */
  private CardLayout getMainCardLayout() {
    return ((CardLayout) mainPanel.getLayout());
  }

  /**
   * For internal debugging purposes only.
   */
  public static void main(String args[]) {
    String[] creators = new String[]{"edu.stanford.nlp.ie.regexp.RegexpExtractorCreator", "edu.stanford.nlp.ie.hmm.HMMFieldExtractorCreator"};
    AddExtractorDialog aed = new AddExtractorDialog(new javax.swing.JFrame(), creators);
    if (aed.showDialog(null) == JOptionPane.OK_OPTION) {
      System.out.println(aed.getFieldExtractor());
    }
    System.exit(0);
  }

  /**
   * This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  private void initComponents()//GEN-BEGIN:initComponents
  {
    jPanel3 = new javax.swing.JPanel();
    jLabel1 = new javax.swing.JLabel();
    titleLabel = new javax.swing.JLabel();
    mainPanel = new javax.swing.JPanel();
    jPanel1 = new javax.swing.JPanel();
    backButton = new javax.swing.JButton();
    nextButton = new javax.swing.JButton();
    cancelButton = new javax.swing.JButton();

    setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
    setTitle("Add Extractor");
    addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosing(java.awt.event.WindowEvent evt) {
        closeDialog(evt);
      }
    });

    jPanel3.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

    jPanel3.setBorder(new javax.swing.border.EtchedBorder());
    jLabel1.setText("Add Extractor Wizard:");
    jPanel3.add(jLabel1);

    titleLabel.setText("Create or Load Extractor");
    jPanel3.add(titleLabel);

    getContentPane().add(jPanel3, java.awt.BorderLayout.NORTH);

    mainPanel.setLayout(new java.awt.CardLayout(5, 5));

    getContentPane().add(mainPanel, java.awt.BorderLayout.CENTER);

    jPanel1.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));

    jPanel1.setBorder(new javax.swing.border.EtchedBorder());
    backButton.setMnemonic('B');
    backButton.setText("< Back");
    backButton.setEnabled(false);
    backButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        backButtonActionPerformed(evt);
      }
    });

    jPanel1.add(backButton);

    nextButton.setText("Next >");
    nextButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        nextButtonActionPerformed(evt);
      }
    });

    jPanel1.add(nextButton);

    cancelButton.setText("Cancel");
    cancelButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cancelButtonActionPerformed(evt);
      }
    });

    jPanel1.add(cancelButton);

    getContentPane().add(jPanel1, java.awt.BorderLayout.SOUTH);

    pack();
  }//GEN-END:initComponents

  private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cancelButtonActionPerformed
  {//GEN-HEADEREND:event_cancelButtonActionPerformed
    doCancel(JOptionPane.CANCEL_OPTION);
  }//GEN-LAST:event_cancelButtonActionPerformed

  private void nextButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_nextButtonActionPerformed
  {//GEN-HEADEREND:event_nextButtonActionPerformed
    doNext();
  }//GEN-LAST:event_nextButtonActionPerformed

  private void backButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_backButtonActionPerformed
  {//GEN-HEADEREND:event_backButtonActionPerformed
    doBack();
  }//GEN-LAST:event_backButtonActionPerformed

  private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
  {//GEN-HEADEREND:event_closeDialog
    doCancel(JOptionPane.CLOSED_OPTION);
  }//GEN-LAST:event_closeDialog

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JLabel titleLabel;
  private javax.swing.JButton backButton;
  private javax.swing.JPanel jPanel3;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JButton cancelButton;
  private javax.swing.JPanel mainPanel;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JButton nextButton;
  // End of variables declaration//GEN-END:variables

}
