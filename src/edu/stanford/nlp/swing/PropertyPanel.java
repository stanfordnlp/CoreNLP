package edu.stanford.nlp.swing;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;

/**
 * UI for a key/value property with an on/off checkbox.
 * The key is a label, the value is a text box, and the checkbox
 * turns the property on and off. If the property is required,
 * the checkbox is always on. When the checkbox is off, the text field is
 * disabled.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class PropertyPanel extends JComponent {
  /**
   * 
   */
  private static final long serialVersionUID = -2164679410449618223L;
  private boolean required;
  private String defaultValue;
  private Object propertyClass;

  private final JCheckBox checkbox;
  private final JLabel keyLabel;
  private final JFormattedTextField valueField;
  private final JPanel valuePanel; // contains only valueField
  private final JButton defaultButton;

  /**
   * Constructs a new PropertyPanel with nothing initially set.
   */
  public PropertyPanel() {
    // implementation note:
    // you can give a label to a check box, but then when you disable it,
    // the label text gets greyed out. so i use a separate label and when
    // you click it, it toggles the checkbox, but it always stays on

    checkbox = new JCheckBox();
    keyLabel = new JLabel();
    keyLabel.setVerticalTextPosition(SwingConstants.TOP);
    valueField = new JFormattedTextField();

    // enables/disables the text field in sync with the checkbox
    checkbox.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        for (int i = 0; i < valuePanel.getComponentCount(); i++) {
          valuePanel.getComponent(i).setEnabled(checkbox.isSelected());
        }
      }
    });

    // clicking label toggles check box
    keyLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent me) {
        if (checkbox.isEnabled()) {
          checkbox.setSelected(!checkbox.isSelected());
        }
      }
    });

    valueField.setColumns(20); // default width

    // first time the text is edited, turns the checkbox on
    // also means when you revert to default it turns the property on
    valueField.getDocument().addDocumentListener(new DocumentListener() {
      public void insertUpdate(DocumentEvent e) {
        if (!isOn()) {
          setOn(true);
        }
      }

      public void removeUpdate(DocumentEvent e) {
      }

      public void changedUpdate(DocumentEvent e) {
      }

    });
    valueField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent fe) {
        scrollRectToVisible(getBounds());
      }
    });

    JPanel boxAndLabelPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
    boxAndLabelPanel.add(checkbox);
    boxAndLabelPanel.add(keyLabel);

    // revert to default button
    defaultButton = new JButton("D");
    defaultButton.setMargin(new Insets(0, 0, 0, 0));
    defaultButton.setToolTipText("Revert to default value");
    defaultButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent ae) {
        setValue(defaultValue);
      }
    });

    // keeps the value field in its own panel so it can be swapped out
    valuePanel = new JPanel(new BorderLayout());
    valuePanel.add("Center", valueField);

    // keeps the text field from stretching vertically
    JPanel valueAndDefaultPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    valueAndDefaultPanel.add(valuePanel);
    valueAndDefaultPanel.add(defaultButton);

    setLayout(new BorderLayout());
    add("West", boxAndLabelPanel);
    add("Center", valueAndDefaultPanel);

    // gives focus to the text field when the panel gets focus
    addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent fe) {
        valueField.requestFocus();
      }
    });

    setDefaultValue(null);
    setPropertyClass(null);
  }

  /**
   * Constructs a new PropertyPanel with the given initial values.
   * The property will be initially on if the value is not null or
   * if required is true. If the value is null, the checkbox will initially
   * be off, but the text field will not be disabled (as it normally would
   * when the checkbox is off). The first time the user types in the text
   * field, the checkbox is turned on, and from there on it behaves normally.
   * This is to save the user from having to explicitly turn on every
   * property before using it the first time.
   */
  public PropertyPanel(String key, String value, String defaultValue, String description, boolean required, Object propertyClass) {
    this();

    if (value == null) {
      value = defaultValue;
    }

    setKey(key);
    setDefaultValue(defaultValue);
    setPropertyClass(propertyClass);
    setValue(value);
    setDescription(description);
    setOn(value != null);
    setRequired(required);
  }

  /**
   * Gets the preferred width of the label displaying the property key.
   */
  public int getPreferredLabelWidth() {
    return (keyLabel.getPreferredSize().width);
  }

  /**
   * Sets the preferred width of the label displaying the property key.
   */
  public void setPreferredLabelWidth(int width) {
    keyLabel.setPreferredSize(new Dimension(width, keyLabel.getPreferredSize().height));
  }

  /**
   * Sets the preferred label width of each of the given panels to the max
   * preferred width of any of the panels. This is useful for laying out a
   * column of property panels and ensuring their text fields all line up.
   */
  public static void synchronizeLabelWidths(PropertyPanel[] panels) {
    int maxWidth = 0;
    for (int i = 0; i < panels.length; i++) {
      maxWidth = Math.max(maxWidth, panels[i].getPreferredLabelWidth());
    }
    for (int i = 0; i < panels.length; i++) {
      panels[i].setPreferredLabelWidth(maxWidth);
    }
  }

  public String getKey() {
    return (keyLabel.getText());
  }

  public String getValue() {
    return (valueField.getText());
  }

  public String getDefaultValue() {
    return (defaultValue);
  }

  public String getDescription() {
    return (keyLabel.getToolTipText());
  }

  public boolean isOn() {
    return (checkbox.isSelected());
  }

  public boolean isRequired() {
    return (required);
  }

  public Object getPropertyClass() {
    return (propertyClass);
  }

  public void setKey(String key) {
    keyLabel.setText(key);
  }

  public void setValue(String value) {
    valueField.setText(value);
    // tries to commit this value so future edits will revert back to it
    // but doesn't care if this doesn't work (user will have to deal with it)
    try {
      valueField.commitEdit();
    } catch (ParseException e) {
    }
    // if this value is being displayed as a combo box, selects the corresponding item
    if (valueField.getParent() == null && valuePanel.getComponent(0) instanceof JComboBox) {
      ((JComboBox) valuePanel.getComponent(0)).setSelectedItem(value);
    }
  }

  public void setDefaultValue(String defaultValue) {
    this.defaultValue = defaultValue;
    defaultButton.setEnabled(defaultValue != null);
  }

  public void setDescription(String description) {
    keyLabel.setToolTipText(description);
  }

  public void setOn(boolean on) {
    checkbox.setSelected(on);
  }

  /**
   * Sets whether this property is required to be on.
   * If so, forces the checkbox to be selected and disables de-selecting it.
   */
  public void setRequired(boolean required) {
    this.required = required;
    checkbox.setEnabled(!required);
    if (required) {
      checkbox.setSelected(true);
    }
  }

  /**
   * Changes the property value editor to support editing objects of the given
   * class type. If classInstance is null, it is assumed the property is a generic
   * string. In addition, the following other object types are supported, with
   * the following behaviors:
   * <ul>
   * <li><tt>Integer/Double/Long/Float</tt> - text field will validate input
   * <li><tt>File</tt> - text field will add "browse" button to open file chooser
   * <li><tt>List</tt> - text field will change to pull-down menu with list elements
   * <li><tt>Boolean</tt> - text field will change to pull-down menu with true/false
   * </ul>
   */
  @SuppressWarnings("unchecked")
  public void setPropertyClass(Object classInstance) {
    if (classInstance == null || classInstance instanceof String || classInstance instanceof Integer || classInstance instanceof Double || classInstance instanceof Long || classInstance instanceof Float) {
      valuePanel.removeAll();
      valuePanel.add("Center", valueField);
      valueField.setValue(classInstance);
    } else if (classInstance instanceof File) {
      // adds file browse button
      JButton browseButton = new JButton();
      browseButton.setText("...");
      browseButton.setToolTipText("Browse for file");
      browseButton.setMargin(new java.awt.Insets(0, 4, 0, 4));
      browseButton.addActionListener(new java.awt.event.ActionListener() {
        final JFileChooser jfc = new JFileChooser();

        public void actionPerformed(java.awt.event.ActionEvent evt) {
          jfc.setSelectedFile(new File(valueField.getText()));
          jfc.setDialogTitle("Select File");
          if (jfc.showOpenDialog(PropertyPanel.this) == JFileChooser.APPROVE_OPTION) {
            valueField.setText(jfc.getSelectedFile().getAbsolutePath());
          }
        }
      });

      valuePanel.removeAll();
      valuePanel.add("Center", valueField);
      valuePanel.add("East", browseButton);
    } else if (classInstance instanceof List) {
      // replaces text field with chooser box
      JComboBox chooser = new JComboBox(((List) classInstance).toArray());
      chooser.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          valueField.setText(e.getItem().toString());
        }
      });
      valuePanel.removeAll();
      valuePanel.add("Center", chooser);
    } else if (classInstance instanceof Boolean) {
      setPropertyClass(Arrays.asList(new String[]{"True", "False"}));
    }
  }

  /**
   * Transfers focus to this PropertyPanel's value text field and selects the text.
   * This may be appropriate to streamline correction when a bad property
   * value is entered.
   */
  public void selectValueField() {
    valuePanel.getComponent(0).requestFocus();
    valueField.selectAll();
  }

  /**
   * For internal debugging purposes only.
   */
  public static void main(String[] args) {
    JFrame frame = new JFrame("test");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    PropertyPanel ppanel = new PropertyPanel("key", "1", "0", "description", false, Integer.valueOf(0));
    frame.getContentPane().add("North", ppanel);
    frame.getContentPane().add("South", new JTextField("bogus other field"));
    frame.pack();
    frame.setVisible(true);
  }
}

