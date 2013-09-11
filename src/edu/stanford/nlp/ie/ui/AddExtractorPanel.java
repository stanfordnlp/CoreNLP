package edu.stanford.nlp.ie.ui;

import edu.stanford.nlp.ie.FieldExtractorCreator;
import edu.stanford.nlp.io.ExtensionFileFilter;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;

/**
 * UI for selecting whether to create a new extractor or load a serialized
 * extractor. Lets the user pick the type of extractor to create (from a list
 * of FieldExtractorCreators) or type/browse the filename of a serialized
 * extractor to load. Check {@link #createExtractor} to see whether the user
 * wanted to create or load an extractor.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class AddExtractorPanel extends javax.swing.JPanel {
  /**
   * 
   */
  private static final long serialVersionUID = 5028128813076997752L;
  /**
   * File browser for loading a serialized extractor.
   */
  private final JFileChooser jfc;

  /**
   * Constructs a new AddExtractorPanel with the given list of FieldExtractorCreator
   * class names. A new instance of each creator class is created to do the actual
   * creation (if creation is selected). FieldExtractorCreator class names that
   * can't be instantiated are skipped.
   */
  public AddExtractorPanel(String[] creators) {
    initComponents();
    add(Box.createHorizontalGlue());

    // selects the load button when you edit the filename text
    filenameField.getDocument().addDocumentListener(new DocumentListener() {
      public void insertUpdate(DocumentEvent e) {
        loadButton.setSelected(true);
      }

      public void removeUpdate(DocumentEvent e) {
        loadButton.setSelected(true);
      }

      public void changedUpdate(DocumentEvent e) {
        loadButton.setSelected(true);
      }
    });

    // selects the new button when you choose a field extractor creator
    extractorTypeChooser.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        newButton.setSelected(true);
      }
    });

    // sets up file browser for serialized extractors
    jfc = new JFileChooser();
    jfc.setDialogTitle("Load Serialized FieldExtractor");
    jfc.addChoosableFileFilter(new ExtensionFileFilter("obj"));

    // sets up field extractor creators
    if (creators == null || creators.length == 0) {
      createPanel.setEnabled(false);
    } else {
      for (int i = 0; i < creators.length; i++) {
        try {
          FieldExtractorCreator creator = (FieldExtractorCreator) Class.forName(creators[i]).newInstance();
          extractorTypeChooser.addItem(creator);
        } catch (Exception e) {
          e.printStackTrace();
        } // skip this one
      }
    }
  }

  /**
   * Returns whether the user desires to create a new extractor (true) or
   * load a serialized extractor (false).
   *
   * @see #getSelectedFieldExtractorCreator
   * @see #getSerializedExtractorFilename
   */
  public boolean createExtractor() {
    return (newButton.isSelected());
  }

  /**
   * Returns the selected FieldExtractorCreator that will be used to create
   * a new FieldExtractor. First check {@link #createExtractor} to ensure
   * it's true before using this method.
   */
  public FieldExtractorCreator getSelectedFieldExtractorCreator() {
    return ((FieldExtractorCreator) extractorTypeChooser.getSelectedItem());
  }

  /**
   * Returns the entered text of the file name for the serialized extractor to load.
   * First check {@link #createExtractor} to ensure it's false before using
   * this method.
   */
  public String getSerializedExtractorFilename() {
    return (filenameField.getText());
  }

  /**
   * Transfers focus to the serialized extractor filename text field and
   * selects the text. This may be appropriate to streamline correction
   * when a bad file name is entered.
   */
  public void selectFilenameField() {
    filenameField.requestFocus();
    filenameField.selectAll();
  }

  /**
   * Opens a file chooser to select a serialized extractor to load.
   * Starts at the current value of the file name field and sets the
   * value of the file name field to the selected file (unless the user
   * cancels).
   */
  private void browse() {
    jfc.setSelectedFile(new File(filenameField.getText()));
    if (jfc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      filenameField.setText(jfc.getSelectedFile().getAbsolutePath());
    }
    filenameField.requestFocus();
  }

  /**
   * For internal debugging purposes only.
   */
  public static void main(String[] args) {
    JFrame frame = new JFrame("Testing AddExtractorPanel");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    String[] creators = new String[]{"edu.stanford.nlp.ie.regexp.RegexpExtractorCreator", "edu.stanford.nlp.ie.hmm.HMMFieldExtractorCreator"};
    frame.getContentPane().add("Center", new AddExtractorPanel(creators));
    frame.pack();
    frame.setVisible(true);
  }

  /**
   * This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  private void initComponents()//GEN-BEGIN:initComponents
  {
    buttonGroup1 = new javax.swing.ButtonGroup();
    createPanel = new javax.swing.JPanel();
    newButton = new javax.swing.JRadioButton();
    extractorTypeChooser = new javax.swing.JComboBox();
    jPanel2 = new javax.swing.JPanel();
    loadButton = new javax.swing.JRadioButton();
    filenameField = new javax.swing.JTextField();
    browseButton = new javax.swing.JButton();

    setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));

    setBorder(new javax.swing.border.TitledBorder("Select the type of FieldExtractor to add:"));
    setToolTipText("null");
    createPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));

    newButton.setMnemonic('C');
    newButton.setSelected(true);
    newButton.setText("Create new FieldExtactor of type:");
    buttonGroup1.add(newButton);
    createPanel.add(newButton);

    createPanel.add(extractorTypeChooser);

    add(createPanel);

    jPanel2.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));

    loadButton.setMnemonic('L');
    loadButton.setText("Load serialized FieldExtractor from:");
    buttonGroup1.add(loadButton);
    jPanel2.add(loadButton);

    filenameField.setColumns(20);
    jPanel2.add(filenameField);

    browseButton.setText("...");
    browseButton.setToolTipText("Browse for serialized extractor to load");
    browseButton.setMargin(new java.awt.Insets(0, 4, 0, 4));
    browseButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        browseButtonActionPerformed(evt);
      }
    });

    jPanel2.add(browseButton);

    add(jPanel2);

  }//GEN-END:initComponents

  private void browseButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_browseButtonActionPerformed
  {//GEN-HEADEREND:event_browseButtonActionPerformed
    browse();
  }//GEN-LAST:event_browseButtonActionPerformed


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JRadioButton loadButton;
  private javax.swing.JPanel jPanel2;
  private javax.swing.JTextField filenameField;
  private javax.swing.JRadioButton newButton;
  private javax.swing.JPanel createPanel;
  private javax.swing.ButtonGroup buttonGroup1;
  private javax.swing.JComboBox extractorTypeChooser;
  private javax.swing.JButton browseButton;
  // End of variables declaration//GEN-END:variables

}
