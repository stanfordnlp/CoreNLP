package edu.stanford.nlp.ie.ui;

import edu.stanford.nlp.ie.FieldExtractorCreator;
import edu.stanford.nlp.ie.hmm.HMMFieldExtractorCreator;
import edu.stanford.nlp.io.ExtensionFileFilter;
import edu.stanford.nlp.swing.PropertyPanel;

import javax.swing.*;

import java.awt.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * UI for naming a new FieldExtractor and customizing its properties.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class NewExtractorPanel extends javax.swing.JPanel {
  /**
   * 
   */
  private static final long serialVersionUID = 2538253040481115106L;

  private static final int defaultNumVisibleProperties = 10;

  private final FieldExtractorCreator creator;
  private List<PropertyPanel> ppanels;

  private final JFileChooser jfc;

  /**
   * Creates new form NewExtractorPanel
   */
  public NewExtractorPanel(FieldExtractorCreator creator) {
    this.creator = creator;
    initComponents();

    jfc = new JFileChooser();
    jfc.setDialogTitle("Load Properties");
    jfc.addChoosableFileFilter(new ExtensionFileFilter("properties"));

    // use the customCreatorComponent if not null, otherwise create a
    // property panel for each field name
    java.awt.Component custom = creator.customCreatorComponent();
    if (custom != null) {
      propertiesPanel.add(custom);
      ppanels = new ArrayList<PropertyPanel>();
    } else {
      createPropertyPanels();
    }
  }

  /**
   * Creates a PropertyPanel for each property in the property names of the
   * field extractor creator. Properties are shown in alphabetical order
   * of property name.
   */
  @SuppressWarnings("unchecked") // sort
  protected void createPropertyPanels() {
    // creates and adds a property panel for each property in propertyNames
    ppanels = new ArrayList<PropertyPanel>(creator.propertyNames().size());
    List pNames = new ArrayList(creator.propertyNames());
    Collections.sort(pNames);
    for (Iterator iter = pNames.iterator(); iter.hasNext();) {
      String key = (String) iter.next();
      String value = creator.getProperty(key);
      String defaultValue = creator.getPropertyDefault(key);
      String description = creator.getPropertyDescription(key);
      boolean required = creator.isRequired(key);
      Object propertyClass = creator.getPropertyClass(key);
      ppanels.add(new PropertyPanel(key, value, defaultValue, description, required, propertyClass));
    }
    PropertyPanel.synchronizeLabelWidths(ppanels.toArray(new PropertyPanel[0]));
    propertiesPanel.add(Box.createVerticalGlue());
    for (int i = 0; i < ppanels.size(); i++) {
      PropertyPanel ppanel = ppanels.get(i);
      propertiesPanel.add(ppanel);
    }

    // displays only at most the first N property panels
    if (ppanels.size() > defaultNumVisibleProperties) {
      Dimension psize = ppanels.get(0).getPreferredSize();
      propertiesScrollPane.setPreferredSize(new Dimension(psize.width, defaultNumVisibleProperties * psize.height));
    }

  }

  /**
   * Prompts user to load properties from a file. Updates property panels for
   * properties found in the file and creates custom property panels for all
   * properties in file that don't currently have property panels.
   */
  private void loadProperties() {
    if (jfc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      Properties props = new Properties();
      try {
        props.load(new FileInputStream(jfc.getSelectedFile()));
      } catch (IOException e) {
        JOptionPane.showMessageDialog(this, "Error loading properties: " + e.getMessage(), "Error loading properties", JOptionPane.ERROR_MESSAGE);
        return; // bail out early
      }
      for (int i = 0; i < ppanels.size(); i++) {
        PropertyPanel ppanel = ppanels.get(i);
        String newValue = props.getProperty(ppanel.getKey());
        if (newValue != null) {
          ppanel.setValue(newValue);
          props.remove(ppanel.getKey()); // so only un-used props remain at the end
        }
      }
      Enumeration<?> customPropertyNames = props.propertyNames();
      while (customPropertyNames.hasMoreElements()) {
        String key = (String) customPropertyNames.nextElement();
        addCustomPropertyPanel(key, props.getProperty(key));
      }
    }
  }

  /**
   * Prompts the user for a property name and then adds a new property panel
   * with that name and focuses input on the corresponding value. This is how
   * you can add properties that aren't in propertyNames.
   */
  private void addCustomProperty() {
    String key = JOptionPane.showInputDialog(this, "Enter the name of the custom property to add:", "Custom Property", JOptionPane.QUESTION_MESSAGE);
    if (key != null) {
      if (key.length() == 0 || creator.getProperty(key) != null) {
        JOptionPane.showMessageDialog(this, "Bad property name");
      } else {
        addCustomPropertyPanel(key, "");
      }
    }
  }

  /**
   * Adds a new property panel with the given name and initial value.
   */
  private void addCustomPropertyPanel(String key, String value) {
    PropertyPanel ppanel = new PropertyPanel(key, value, null, "Custom property", false, null);
    ppanels.add(ppanel);
    PropertyPanel.synchronizeLabelWidths(ppanels.toArray(new PropertyPanel[0]));
    propertiesPanel.add(ppanel, propertiesPanel.getComponentCount() - 1);
    revalidate(); // updates the layout to ensure the new ppanel is added
    ppanel.requestFocus(); // scrolls to the new ppanel
  }

  /**
   * Updates the properties of the field extractor creator to reflect the UI.
   * All checked properties are set and all unchecked properties are removed
   * (set to null).
   */
  public void updateProperties() {
    for (int i = 0; i < ppanels.size(); i++) {
      PropertyPanel ppanel = ppanels.get(i);
      creator.setProperty(ppanel.getKey(), ppanel.isOn() ? ppanel.getValue() : null);
    }
  }

  /**
   * Returns the unique name entered for the new FieldExtractor.
   */
  @Override
  public String getName() {
    return (nameField.getText());
  }

  /**
   * Transfers focus to the extractor name text field and selects the text.
   * This may be appropriate to streamline correction when a bad name is entered.
   */
  public void selectNameField() {
    nameField.requestFocus();
    nameField.selectAll();
  }

  /**
   * Transfers focus to the property field for the given property and selects
   * the text. This may be appropriate to streamline correction when a bad
   * property causes the creation of the extractor to fail. Nothing is done
   * if a property field for the given property name cannot be found.
   */
  public void selectPropertyField(String key) {
    for (int i = 0; i < ppanels.size(); i++) {
      PropertyPanel ppanel = ppanels.get(i);
      if (ppanel.getKey().equals(key)) {
        ppanel.selectValueField();
      }
    }
  }

  /**
   * Returns the FieldExtractorCreator being customized.
   */
  public FieldExtractorCreator getFieldExtractorCreator() {
    return (creator);
  }

  /**
   * For internal debugging purposes only.
   */
  public static void main(String[] args) {
    JFrame frame = new JFrame("NewExtractorPanel");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.getContentPane().add("Center", new NewExtractorPanel(new HMMFieldExtractorCreator()));
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
    jPanel1 = new javax.swing.JPanel();
    jLabel1 = new javax.swing.JLabel();
    nameField = new javax.swing.JTextField();
    jPanel2 = new javax.swing.JPanel();
    propertiesScrollPane = new javax.swing.JScrollPane();
    propertiesPanel = new javax.swing.JPanel();
    jPanel4 = new javax.swing.JPanel();
    jButton2 = new javax.swing.JButton();
    jButton1 = new javax.swing.JButton();

    setLayout(new java.awt.BorderLayout());

    jPanel1.setLayout(new java.awt.BorderLayout());

    jPanel1.setBorder(new javax.swing.border.TitledBorder("Enter a unique name for this FieldExtractor"));
    jLabel1.setText("Name: ");
    jLabel1.setToolTipText("Used when serializing the extractor and assigning it to a class slot");
    jPanel1.add(jLabel1, java.awt.BorderLayout.WEST);

    jPanel1.add(nameField, java.awt.BorderLayout.CENTER);

    add(jPanel1, java.awt.BorderLayout.NORTH);

    jPanel2.setLayout(new java.awt.BorderLayout());

    jPanel2.setBorder(new javax.swing.border.TitledBorder("Customize the properties of this FieldExtractor"));
    propertiesScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    propertiesPanel.setLayout(new javax.swing.BoxLayout(propertiesPanel, javax.swing.BoxLayout.Y_AXIS));

    propertiesScrollPane.setViewportView(propertiesPanel);

    jPanel2.add(propertiesScrollPane, java.awt.BorderLayout.CENTER);

    jButton2.setText("Load Properties...");
    jButton2.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        jButton2ActionPerformed(evt);
      }
    });

    jPanel4.add(jButton2);

    jButton1.setText("Add Custom Property...");
    jButton1.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        jButton1ActionPerformed(evt);
      }
    });

    jPanel4.add(jButton1);

    jPanel2.add(jPanel4, java.awt.BorderLayout.SOUTH);

    add(jPanel2, java.awt.BorderLayout.CENTER);

  }//GEN-END:initComponents

  private void jButton2ActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_jButton2ActionPerformed
  {//GEN-HEADEREND:event_jButton2ActionPerformed
    loadProperties();
  }//GEN-LAST:event_jButton2ActionPerformed

  private void jButton1ActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_jButton1ActionPerformed
  {//GEN-HEADEREND:event_jButton1ActionPerformed
    addCustomProperty();
  }//GEN-LAST:event_jButton1ActionPerformed


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JButton jButton2;
  private javax.swing.JPanel jPanel4;
  private javax.swing.JTextField nameField;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JScrollPane propertiesScrollPane;
  private javax.swing.JButton jButton1;
  private javax.swing.JPanel jPanel2;
  private javax.swing.JPanel propertiesPanel;
  private javax.swing.JPanel jPanel1;
  // End of variables declaration//GEN-END:variables

}
