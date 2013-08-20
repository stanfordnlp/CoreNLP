package edu.stanford.nlp.ie.ui;

import edu.stanford.nlp.ie.ExtractorMediator;
import edu.stanford.nlp.ie.FieldExtractor;
import edu.stanford.nlp.ie.OntologyMediator;
import edu.stanford.nlp.io.ExtensionFileFilter;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.swing.ButtonRolloverBorderAdapter;
import edu.stanford.nlp.swing.SwingUtils;
import edu.stanford.nlp.util.StringUtils;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * GUIPanel to manage an ExtractorMediator. Supports creating or loading a
 * mediator, adding/viewing/removing extractors and assignments, and storing
 * the mediator. Use {@link #getExtractorMediator} to get the current mediator
 * (e.g.  to perform extraction). If constructed with an OntologyMediator,
 * restricts the possible assignments of extractor fields to legal class/slot
 * combinations.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class EMPanel extends javax.swing.JPanel {
  /**
   * 
   */
  private static final long serialVersionUID = 376408363528943420L;
  private ExtractorMediator mediator;
  private boolean changed; // has the mediator been edited since being loaded
  private OntologyMediator ontology; // ontology to restrict assignments
  private String selectedClass; // most recently (externally) selected class in ontology

  private final DefaultListModel listModel = new DefaultListModel();
  private final DefaultTreeModel treeModel = new DefaultTreeModel(null);
  private DefaultMutableTreeNode rootNode; // root of assignment tree
  private final JFileChooser jfc;

  private String[] fieldExtractorCreators = new String[]{"edu.stanford.nlp.ie.regexp.RegexpExtractorCreator", "edu.stanford.nlp.ie.hmm.HMMFieldExtractorCreator", "edu.stanford.nlp.ie.ClassifierFieldExtractorCreator"};

  /**
   * Constructs a new GUI panel for an ExtractorMediator.<p/>
   * (Note: Previously also took: fieldExtractorCreators list of class names for FieldExtractorCreator
   *                               classes to offer for creating new extractors. This can easily be obtained
   *                               from a file listing class names one per line by calling
   *                               {@link #getLines}.)
   *
   * @param ontology               interface to the ontology to get the legal set of class
   *                               and slot names for assignments. If null, any class or slot will be
   *                               assumed to be legal.
   *
   */
  public EMPanel(OntologyMediator ontology) //,String[] fieldExtractorCreators)
  {
    setOntologyMediator(ontology);
    //this.fieldExtractorCreators=fieldExtractorCreators;
    jfc = new JFileChooser();
    jfc.setFileFilter(new ExtensionFileFilter("xml"));

    initComponents();
    setChanged(false);
    selectedClass = null;

    // hitting delete in extractor list removes selected extractors
    extractorList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
    extractorList.getActionMap().put("delete", new AbstractAction() {
      /**
       * 
       */
      private static final long serialVersionUID = -616957231624053196L;

      public void actionPerformed(ActionEvent ae) {
        removeSelectedExtractors();
      }
    });

    // hitting delete in assignment tree removes selected assignments
    assignmentTree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
    assignmentTree.getActionMap().put("delete", new AbstractAction() {
      /**
       * 
       */
      private static final long serialVersionUID = 8454099548471057823L;

      public void actionPerformed(ActionEvent ae) {
        removeSelectedAssignments();
      }
    });

    // tries to use standard java icons for toolbar buttons
    SwingUtils.loadButtonIcon(newButton, "toolbarButtonGraphics/general/New16.gif", null);
    SwingUtils.loadButtonIcon(openButton, "toolbarButtonGraphics/general/Open16.gif", null);
    SwingUtils.loadButtonIcon(saveButton, "toolbarButtonGraphics/general/Save16.gif", null);
    SwingUtils.loadButtonIcon(saveAsButton, "toolbarButtonGraphics/general/SaveAs16.gif", null);
    SwingUtils.loadButtonIcon(fecButton, "toolbarButtonGraphics/general/Properties16.gif", null);
    ButtonRolloverBorderAdapter.manageToolBar(toolBar);

    setExtractorMediator(new ExtractorMediator()); // start with blank mediator
  }

  /**
   * Constructs a new EMPanel with no ontology.
   */
  public EMPanel() {
    this(null);
  }

  /**
   * Returns the ExtractorMediator this GUI manages.
   */
  public ExtractorMediator getExtractorMediator() {
    return (mediator);
  }

  /**
   * Sets the interface to the ontology to get the legal set of class
   * and slot names for assignments. If null, any class or slot will be
   * assumed to be legal.
   */
  public void setOntologyMediator(OntologyMediator ontology) {
    this.ontology = ontology;
  }

  /**
   * Returns the ontology mediator currently being used.
   */
  public OntologyMediator getOntologyMediator() {
    return (ontology);
  }

  /**
   * Records the given class name so it can be pre-selected in the assignment
   * dialog. Ontology GUIs using EMPanel can call this method when a class
   * is selected so that if/when an assignment between class.slot and
   * extractor.field is made, the default selected class will be the one most
   * recently selected in the GUI. This is not required for normal functioning.
   */
  public void setSelectedClass(String className) {
    selectedClass = className;
  }

  /**
   * Sets the Extractor for this GUI to manage.
   *
   * @param mediator the ExtractorMediator for this GUI to manage
   */
  @SuppressWarnings("unchecked") // Collections.sort
  public void setExtractorMediator(ExtractorMediator mediator) {
    this.mediator = mediator;

    // updates the list of extractors
    List names = new ArrayList(mediator.getExtractorNames());
    Collections.sort(names);

    listModel.removeAllElements();
    for (int i = 0; i < names.size(); i++) {
      String name = (String) names.get(i);
      String[] fields = mediator.getExtractableFields(name);
      listModel.addElement(createExtractorElement(name, fields));
    }
    extractorList.clearSelection();

    // updates the tree of assignments
    updateAssignmentTree();

    // enables the editing buttons
    addExtractorButton.setEnabled(true);
    assignExtractorButton.setEnabled(names.size() > 0);
    saveButton.setEnabled(true);

    setChanged(false); // haven't yet edited this mediator
  }

  /**
   * Recreates the assignment tree to reflect the current mediator's assignments.
   */
  private void updateAssignmentTree() {
    rootNode = new DefaultMutableTreeNode();
    String[] classNames = mediator.getExtractableClassNames();
    for (int i = 0; i < classNames.length; i++) {
      DefaultMutableTreeNode classNode = new DefaultMutableTreeNode(classNames[i]);
      rootNode.add(classNode);
      String[] slotNames = mediator.getExtractableSlots(classNames[i]);
      for (int j = 0; j < slotNames.length; j++) {
        // note: currently assumes only one extractor field per slot
        ExtractorMediator.ExtractorField ef = mediator.getAssignedExtractorField(classNames[i], slotNames[j]);
        DefaultMutableTreeNode slotNode = createSlotNode(slotNames[j], ef.getExtractorName(), ef.getFieldName());
        classNode.add(slotNode);
      }
    }
    treeModel.setRoot(rootNode);
  }

  /**
   * Creates a list element for the given extractor showing its fields.
   */
  private String createExtractorElement(String name, String[] extractableFields) {
    return (name + " (" + StringUtils.join(extractableFields, ", ") + ")");
  }

  /**
   * Creates a tree node representing an assignment for the given slot of the given extractor field.
   */
  private DefaultMutableTreeNode createSlotNode(String slotName, String extractorName, String fieldName) {
    return (new DefaultMutableTreeNode(slotName + " [" + extractorName + "." + fieldName + "]"));
  }

  /**
   * Pops up UI to create or load an extractor and add it to the mediator.
   */
  private void addExtractor() {
    AddExtractorDialog aed = new AddExtractorDialog((Frame) SwingUtilities.getRoot(this), fieldExtractorCreators);
    if (aed.showDialog(this) == JOptionPane.OK_OPTION) {
      FieldExtractor extractor = aed.getFieldExtractor();
      if (mediator.addExtractor(extractor)) {
        listModel.addElement(createExtractorElement(extractor.getName(), extractor.getExtractableFields()));
        assignExtractorButton.setEnabled(true);
        setStatus("Added Extractor: " + extractor.getName());
        setChanged(true);
      } else {
        JOptionPane.showMessageDialog(this, "An error occurred while trying to add extractor: " + extractor.getName(), "Error adding extractor", JOptionPane.ERROR_MESSAGE);
        setStatus("Error adding extractor");
      }
    }
  }

  /**
   * Removes extractors selected in the extractor list UI.
   */
  private void removeSelectedExtractors() {
    // pulls "name" from "name (field1, field2, ...)"
    Object[] elems = extractorList.getSelectedValues();
    for (int i = 0; i < elems.length; i++) {
      String elem = (String) elems[i]; // list element
      String name = elem.substring(0, elem.indexOf(' ')); // extractor name
      if (!mediator.removeExtractor(name)) {
        JOptionPane.showMessageDialog(this, "An error occurred while trying to remove extractor: " + name, "Error removing extractor", JOptionPane.ERROR_MESSAGE);
      }
      listModel.removeElement(elem);
    }
    if (elems.length > 0) {
      extractorList.clearSelection();
      updateAssignmentTree();
      setStatus("Removed " + elems.length + " extractor" + (elems.length == 1 ? "" : "s"));
      setChanged(true);
    }

    // turn off assign button if there are no extractors to assign anymore
    if (listModel.isEmpty()) {
      assignExtractorButton.setEnabled(false);
    }
  }

  /**
   * Returns tool tip text for the extractor at the given index (description etc).
   *
   * @param index the index in the extractor list of the extractor to display a tool tip for
   * @param x     the x-coordinate in pixels where the tool tip is (so the width won't
   *              run over the side of the panel)
   * @return Tool tip text (name/description/etc) for the appropriate extractor
   */
  private String getExtractorToolTipText(int index, int x) {
    if (index != -1) {
      int width = getSize().width - x;
      String elem = (String) listModel.get(index);
      String name = elem.substring(0, elem.indexOf(' ')); // extractor name
      return ("<html><table width=" + width + "><tr><td>" + "<b>Name: </b>" + name + "<br>" + "<b>Class Name: </b>" + mediator.getClassName(name) + "<br>" + "<b>Description: </b>" + mediator.getDescription(name) + "<br>" + "<b>Extractable Fields: </b>" + StringUtils.join(mediator.getExtractableFields(name), ", "));
    }
    return (null);
  }

  /**
   * Pops up UI to assign an extractor field to a class slot.
   * If an ontology mediator has been provided, it will constrain the possible
   * values for class and slot to legal values.
   */
  private void addAssignment() {
    AssignExtractorDialog aed = new AssignExtractorDialog((Frame) SwingUtilities.getRoot(this), mediator, ontology);
    if (selectedClass != null) {
      aed.setClassName(selectedClass); // preselects most recently selected class
    }
    if (aed.showDialog(this) == JOptionPane.OK_OPTION) {
      setSelectedClass(aed.getClassName());
      if (mediator.assignExtractorField(aed.getClassName(), aed.getSlotName(), aed.getExtractorName(), aed.getFieldName())) {
        DefaultMutableTreeNode classNode = null;
        for (int i = 0; i < rootNode.getChildCount(); i++) {
          // try to find existing class node to attach to
          DefaultMutableTreeNode curClassNode = (DefaultMutableTreeNode) rootNode.getChildAt(i);
          if (aed.getClassName().equals(curClassNode.getUserObject())) {
            classNode = curClassNode;
            break;
          }
        }
        if (classNode == null) {
          // have to create class node first time it's referenced
          classNode = new DefaultMutableTreeNode(aed.getClassName());
          treeModel.insertNodeInto(classNode, rootNode, rootNode.getChildCount());
        }
        // TODO: either allow multiple extractor fields per slot or block assigning to an assigned slot
        DefaultMutableTreeNode slotNode = createSlotNode(aed.getSlotName(), aed.getExtractorName(), aed.getFieldName());
        treeModel.insertNodeInto(slotNode, classNode, classNode.getChildCount());
        assignmentTree.setSelectionPath(new TreePath(slotNode.getPath()));

        setStatus("Assigned extractor field " + aed.getExtractorName() + "." + aed.getFieldName() + " to " + aed.getClassName() + "." + aed.getSlotName());
        setChanged(true);
      } else {
        JOptionPane.showMessageDialog(this, "An error occurred while trying to assign extractor", "Error assigning extractor", JOptionPane.ERROR_MESSAGE);
        setStatus("Error assigning extractor");
      }
    }
  }

  /**
   * Removes extractor-slot assignments selected in the assignment tree UI.
   */
  private void removeSelectedAssignments() {
    TreePath[] paths = assignmentTree.getSelectionPaths();
    if (paths != null) {
      for (int i = 0; i < paths.length; i++) {
        TreePath path = paths[i];
        if (path.getPathCount() == 3) {
          String className = (String) ((DefaultMutableTreeNode) path.getPathComponent(1)).getUserObject();
          String slotName = (String) ((DefaultMutableTreeNode) path.getPathComponent(2)).getUserObject();
          slotName = slotName.substring(0, slotName.indexOf(" ["));
          mediator.removeAllAssignments(className, slotName);
        } else if (path.getPathCount() == 2) {
          String className = (String) ((DefaultMutableTreeNode) path.getPathComponent(1)).getUserObject();
          mediator.removeAllAssignments(className);
        }
      }
      if (paths.length > 0) {
        updateAssignmentTree();
        setStatus("Removed " + paths.length + " assignment" + (paths.length == 1 ? "" : "s"));
        setChanged(true);
      }
    }
  }

  /**
   * Creates a new (empty) extractor mediator.
   */
  private void newMediator() {
    // bail out early if user hits cancel when asked to save mediator
    if (!confirmSavedState()) {
      return;
    }

    setExtractorMediator(new ExtractorMediator());
    setStatus("Created new ExtractorMediator");
  }

  /**
   * Opens an extractor mediator from a user-specified location.
   */
  private void openMediator() {
    // bail out early if user hits cancel when asked to save mediator
    if (!confirmSavedState()) {
      return;
    }

    jfc.setDialogTitle("Open Mediator");
    if (jfc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      ExtractorMediator mediator = new ExtractorMediator();
      File mediatorFile = jfc.getSelectedFile();
      if (mediator.load(mediatorFile)) {
        setExtractorMediator(mediator);
        setStatus("Opened ExtractorMediator from " + mediatorFile);
      } else {
        JOptionPane.showMessageDialog(this, "An error occured while trying to load mediator from:\n" + mediatorFile, "Error loading mediator", JOptionPane.ERROR_MESSAGE);
        setStatus("Error loading mediator");
      }
    }
  }

  /**
   * Prompts the user to save the extractor mediator if it's not already saved.
   * Returns true if the user answers yes or no (confirms the saved state)
   * and false if the user hits cancel or closes the dialog (indicating that
   * the pending action requiring this mediator to be saved should not continue).
   */
  public boolean confirmSavedState() {
    if (changed) {
      int response = JOptionPane.showConfirmDialog(this, "Save changes to current mediator before closing?");
      if (response == JOptionPane.YES_OPTION) {
        return (saveMediator());
      }
      return (response == JOptionPane.YES_OPTION || response == JOptionPane.NO_OPTION); // did use give an answer
    }
    return (true); // mediator is up to date already
  }

  /**
   * Saves the extractor mediator, prompting the user for a location if needed.
   * Returns whether saving was successful. Saving can be unsucessful if (a)
   * actual saving causing an error, or (b) user hasn't saved this file before
   * and cancels save as dialog.
   */
  public boolean saveMediator() {
    if (mediator.getMediatorFile() != null) {
      if (mediator.store()) {
        setChanged(false);
        setStatus("Sucessfully saved ExtractorMediator to " + mediator.getMediatorFile());
        return (true);
      } else {
        JOptionPane.showMessageDialog(this, "An error occured while trying to save mediator to:\n" + mediator.getMediatorFile(), "Error saving mediator", JOptionPane.ERROR_MESSAGE);
        setStatus("Error saving mediator");
        return (false);
      }
    } else {
      return (saveMediatorAs()); // prompts user for location to save (which then calls save again)
    }
  }

  /**
   * Saves the extractor mediator to a user-specified location.
   * Returns whether saving was successful. Saving can be unsuccessul if (a)
   * user cancels save as dialog, or (b) subsequent saving causes an error.
   */
  private boolean saveMediatorAs() {
    jfc.setDialogTitle("Save Mediator As");
    if (mediator.getMediatorFile() != null) {
      jfc.setSelectedFile(mediator.getMediatorFile());
    }
    if (jfc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
      File mediatorFile = jfc.getSelectedFile();
      if (!mediatorFile.getName().endsWith(".xml")) {
        mediatorFile = new File(mediatorFile.getParent(), mediatorFile.getName() + ".xml");
        jfc.setSelectedFile(mediatorFile); // so it's right next time
      }
      if (mediator.setMediatorFile(mediatorFile)) {
        return (saveMediator());
      } else {
        JOptionPane.showMessageDialog(this, "An error occured while trying to save mediator to:\n" + mediatorFile, "Error saving mediator", JOptionPane.ERROR_MESSAGE);
        setStatus("Error saving mediator");
        return (false);
      }
    }
    return (false); // no file chosen
  }

  /**
   * Prompts the user to enter a space-delimited list of
   * FieldExtractorCreators to use for creating new extractors.
   * NOTE: I realize this is a lame UI, but it's just a stopgap for now.
   */
  private void manageFieldExtractorCreators() {
    String fecs = (String) JOptionPane.showInputDialog(this, "Enter class names of FieldExtractorCreators separated by spaces", "Manage FieldExtractorCreators", JOptionPane.QUESTION_MESSAGE, null, null, StringUtils.join(fieldExtractorCreators, " "));
    if (fecs != null) {
      fieldExtractorCreators = fecs.split(" +");
    }
  }

  /**
   * Marks whether the mediator has been changed since its last open/save.
   */
  private void setChanged(boolean changed) {
    this.changed = changed;
    saveButton.setEnabled(changed);
  }

  /**
   * Returns whether the mediator has been changed sine its last open/save.
   */
  public boolean isChanged() {
    return (changed);
  }

  /**
   * Sets the status on the status label.
   */
  public void setStatus(String status) {
    statusLabel.setText(status);
  }

  /**
   * For internal debugging purposes only.
   */
  public static void main(String[] args) {
    JFrame frame = new JFrame("EMPanel");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.getContentPane().add("Center", new EMPanel(new TestOntologyMediator()));
    frame.pack();
    frame.setVisible(true);
  }

  /**
   * Returns the lines of the given file (broken on newlines)
   * or <tt>null</tt> if the file cannot be read.
   */
  public static String[] getLines(File f) {
    try {
      return (IOUtils.slurpFile(f).split("\r?\n"));
    } catch (IOException e) {
      return (null);
    }
  }

  /**
   * For internal debugging purposes only.
   */
  private static class TestOntologyMediator implements OntologyMediator {
    public Set<String> getClassNames() {
      return (new HashSet<String>(Arrays.asList(new String[]{"trip", "Seminar", "Company", "Person", "Atom", "Student"})));
    }

    public Set<String> getSlotNames(String className) {
      if ("trip".equals(className)) {
        return (new HashSet<String>(Arrays.asList(new String[]{"origin", "destination"})));
      } else if ("Seminar".equals(className)) {
        return (new HashSet<String>(Arrays.asList(new String[]{"title", "location", "start-time", "end-time"})));
      } else if ("Company".equals(className)) {
        return (new HashSet<String>(Arrays.asList(new String[]{"name", "location", "web-page"})));
      } else if ("Person".equals(className)) {
        return (new HashSet<String>(Arrays.asList(new String[]{"name"})));
      } else if ("Atom".equals(className)) {
        return (new HashSet<String>());
      } else if ("Student".equals(className)) {
        return (new HashSet<String>(Arrays.asList(new String[]{"name", "student-id"})));
      } else {
        return (null);
      }
    }

    public Set<String> getSuperclasses(String className) {
      if ("Student".equals(className)) {
        return (Collections.singleton("Person"));
      } else {
        return (new HashSet<String>());
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
    toolBar = new javax.swing.JToolBar();
    newButton = new javax.swing.JButton();
    openButton = new javax.swing.JButton();
    saveButton = new javax.swing.JButton();
    saveAsButton = new javax.swing.JButton();
    fecButton = new javax.swing.JButton();
    mediatorSplitPane = new javax.swing.JSplitPane();
    jPanel1 = new javax.swing.JPanel();
    jPanel2 = new javax.swing.JPanel();
    addExtractorButton = new javax.swing.JButton();
    removeExtractorButton = new javax.swing.JButton();
    jScrollPane1 = new javax.swing.JScrollPane();
    extractorList = new javax.swing.JList() {
      /**
       * 
       */
      private static final long serialVersionUID = -4508707834306125912L;

      public String getToolTipText(MouseEvent e) {
        int index = locationToIndex(e.getPoint());
        return (getExtractorToolTipText(index, e.getX()));
      }
    };
    jPanel3 = new javax.swing.JPanel();
    jPanel4 = new javax.swing.JPanel();
    assignExtractorButton = new javax.swing.JButton();
    removeAssignmentButton = new javax.swing.JButton();
    jScrollPane2 = new javax.swing.JScrollPane();
    assignmentTree = new javax.swing.JTree();
    jPanel6 = new javax.swing.JPanel();
    statusLabel = new javax.swing.JLabel();
    jLabel1 = new javax.swing.JLabel();

    setLayout(new java.awt.BorderLayout());

    toolBar.setName("Extractor Mediator");
    newButton.setText("New");
    newButton.setToolTipText("New ExtractorMediator");
    newButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        newButtonActionPerformed(evt);
      }
    });

    toolBar.add(newButton);

    openButton.setText("Open...");
    openButton.setToolTipText("Open ExtractorMediator");
    openButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        openButtonActionPerformed(evt);
      }
    });

    toolBar.add(openButton);

    saveButton.setText("Save");
    saveButton.setToolTipText("Save ExtractorMediator");
    saveButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        saveButtonActionPerformed(evt);
      }
    });

    toolBar.add(saveButton);

    saveAsButton.setText("Save As...");
    saveAsButton.setToolTipText("Save ExtractorMediator As");
    saveAsButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        saveAsButtonActionPerformed(evt);
      }
    });

    toolBar.add(saveAsButton);

    toolBar.addSeparator();
    fecButton.setText("Manage FECs...");
    fecButton.setToolTipText("Manage FieldExtractorCreators");
    fecButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        fecButtonActionPerformed(evt);
      }
    });

    toolBar.add(fecButton);

    add(toolBar, java.awt.BorderLayout.NORTH);

    mediatorSplitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
    mediatorSplitPane.setContinuousLayout(true);
    jPanel1.setLayout(new java.awt.BorderLayout());

    jPanel1.setBorder(new javax.swing.border.TitledBorder("Extractors"));
    addExtractorButton.setText("Add Extractor...");
    addExtractorButton.setEnabled(false);
    addExtractorButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        addExtractorButtonActionPerformed(evt);
      }
    });

    jPanel2.add(addExtractorButton);

    removeExtractorButton.setText("Remove Selected Extractor(s)");
    removeExtractorButton.setEnabled(false);
    removeExtractorButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        removeExtractorButtonActionPerformed(evt);
      }
    });

    jPanel2.add(removeExtractorButton);

    jPanel1.add(jPanel2, java.awt.BorderLayout.SOUTH);

    extractorList.setModel(listModel);
    extractorList.setVisibleRowCount(4);
    extractorList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
      public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
        extractorListValueChanged(evt);
      }
    });

    jScrollPane1.setViewportView(extractorList);

    jPanel1.add(jScrollPane1, java.awt.BorderLayout.CENTER);

    mediatorSplitPane.setLeftComponent(jPanel1);

    jPanel3.setLayout(new java.awt.BorderLayout());

    jPanel3.setBorder(new javax.swing.border.TitledBorder("Assignments"));
    assignExtractorButton.setText("Assign Extractor...");
    assignExtractorButton.setEnabled(false);
    assignExtractorButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        assignExtractorButtonActionPerformed(evt);
      }
    });

    jPanel4.add(assignExtractorButton);

    removeAssignmentButton.setText("Remove Selected Assignment(s)");
    removeAssignmentButton.setEnabled(false);
    removeAssignmentButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        removeAssignmentButtonActionPerformed(evt);
      }
    });

    jPanel4.add(removeAssignmentButton);

    jPanel3.add(jPanel4, java.awt.BorderLayout.SOUTH);

    assignmentTree.setModel(treeModel);
    assignmentTree.setRootVisible(false);
    assignmentTree.setShowsRootHandles(true);
    assignmentTree.setVisibleRowCount(8);
    assignmentTree.addTreeSelectionListener(new javax.swing.event.TreeSelectionListener() {
      public void valueChanged(javax.swing.event.TreeSelectionEvent evt) {
        assignmentTreeValueChanged(evt);
      }
    });

    jScrollPane2.setViewportView(assignmentTree);

    jPanel3.add(jScrollPane2, java.awt.BorderLayout.CENTER);

    mediatorSplitPane.setRightComponent(jPanel3);

    add(mediatorSplitPane, java.awt.BorderLayout.CENTER);

    jPanel6.setLayout(new java.awt.BorderLayout());

    statusLabel.setToolTipText("Status");
    jPanel6.add(statusLabel, java.awt.BorderLayout.CENTER);

    jLabel1.setText(" ");
    jPanel6.add(jLabel1, java.awt.BorderLayout.WEST);

    add(jPanel6, java.awt.BorderLayout.SOUTH);

  }//GEN-END:initComponents

  private void fecButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_fecButtonActionPerformed
  {//GEN-HEADEREND:event_fecButtonActionPerformed
    manageFieldExtractorCreators();
  }//GEN-LAST:event_fecButtonActionPerformed

  private void saveAsButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_saveAsButtonActionPerformed
  {//GEN-HEADEREND:event_saveAsButtonActionPerformed
    saveMediatorAs();
  }//GEN-LAST:event_saveAsButtonActionPerformed

  private void saveButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_saveButtonActionPerformed
  {//GEN-HEADEREND:event_saveButtonActionPerformed
    saveMediator();
  }//GEN-LAST:event_saveButtonActionPerformed

  private void openButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_openButtonActionPerformed
  {//GEN-HEADEREND:event_openButtonActionPerformed
    openMediator();
  }//GEN-LAST:event_openButtonActionPerformed

  private void newButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_newButtonActionPerformed
  {//GEN-HEADEREND:event_newButtonActionPerformed
    newMediator();
  }//GEN-LAST:event_newButtonActionPerformed

  private void removeAssignmentButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_removeAssignmentButtonActionPerformed
  {//GEN-HEADEREND:event_removeAssignmentButtonActionPerformed
    removeSelectedAssignments();
  }//GEN-LAST:event_removeAssignmentButtonActionPerformed

  private void assignExtractorButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_assignExtractorButtonActionPerformed
  {//GEN-HEADEREND:event_assignExtractorButtonActionPerformed
    addAssignment();
  }//GEN-LAST:event_assignExtractorButtonActionPerformed

  private void assignmentTreeValueChanged(javax.swing.event.TreeSelectionEvent evt)//GEN-FIRST:event_assignmentTreeValueChanged
  {//GEN-HEADEREND:event_assignmentTreeValueChanged
    removeAssignmentButton.setEnabled(assignmentTree.getSelectionCount() != 0);
    TreePath path = assignmentTree.getSelectionPath();
    if (path.getPathCount() == 2) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
      setSelectedClass((String) node.getUserObject());
    }
  }//GEN-LAST:event_assignmentTreeValueChanged

  private void extractorListValueChanged(javax.swing.event.ListSelectionEvent evt)//GEN-FIRST:event_extractorListValueChanged
  {//GEN-HEADEREND:event_extractorListValueChanged
    removeExtractorButton.setEnabled(extractorList.getSelectedValue() != null);
  }//GEN-LAST:event_extractorListValueChanged

  private void removeExtractorButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_removeExtractorButtonActionPerformed
  {//GEN-HEADEREND:event_removeExtractorButtonActionPerformed
    removeSelectedExtractors();
  }//GEN-LAST:event_removeExtractorButtonActionPerformed

  private void addExtractorButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_addExtractorButtonActionPerformed
  {//GEN-HEADEREND:event_addExtractorButtonActionPerformed
    addExtractor();
  }//GEN-LAST:event_addExtractorButtonActionPerformed


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JButton removeAssignmentButton;
  private javax.swing.JPanel jPanel4;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JButton fecButton;
  private javax.swing.JButton newButton;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JButton openButton;
  private javax.swing.JLabel statusLabel;
  private javax.swing.JPanel jPanel3;
  private javax.swing.JList extractorList;
  private javax.swing.JButton assignExtractorButton;
  private javax.swing.JSplitPane mediatorSplitPane;
  private javax.swing.JTree assignmentTree;
  private javax.swing.JPanel jPanel2;
  private javax.swing.JToolBar toolBar;
  private javax.swing.JScrollPane jScrollPane2;
  private javax.swing.JButton saveButton;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JButton saveAsButton;
  private javax.swing.JPanel jPanel6;
  private javax.swing.JButton addExtractorButton;
  private javax.swing.JButton removeExtractorButton;
  // End of variables declaration//GEN-END:variables

}
