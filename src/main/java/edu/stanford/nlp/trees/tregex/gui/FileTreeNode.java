package edu.stanford.nlp.trees.tregex.gui;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.tree.DefaultMutableTreeNode;

import edu.stanford.nlp.trees.Treebank;

/**
 * Represents a node in a JTree that holds a file and displays
 * the short name of the file in the JTree.
 *
 * @author Anna Rafferty
 */
@SuppressWarnings("serial")
public class FileTreeNode extends DefaultMutableTreeNode {
  private File file;
  private JCheckBox check = null;
  private JLabel label =null;
  private Treebank t;
  private final ArrayList<FileTreeNodeListener> listeners = new ArrayList<>();

  //this is only for a root node
  public FileTreeNode() {
    super();
    label = new JLabel("root");
    this.setAllowsChildren(true);
  }

  public FileTreeNode(File file, FileTreeNode parent) {
    super(file);
    this.setParent(parent);
    this.file = file;
    boolean isLeaf = file.isFile();
    if(isLeaf) {
      check = new JCheckBox(this.toString(),isLeaf);
      check.setOpaque(true);
      check.setBackground(Color.WHITE);
    }
    else
      label = new JLabel(this.toString());
    this.setAllowsChildren(!isLeaf);
  }

  @Override
  public String toString() {
    if(file == null)
      return "root";
    else
      return file.getName();
  }

  public JComponent getDisplay() {
    if(check != null)
      return check;
    else
      return label;
  }

  public boolean isActive() {
    if(check == null)
      return false;
    else
      return check.isSelected();
  }

  public void setActive(boolean active) {
    if(check != null && (check.isSelected() != active)) {
      check.setSelected(active);
      sendToListeners();
    }
  }

  public void addListener(FileTreeNodeListener l) {
    listeners.add(l);
  }

  private void sendToListeners() {
    for(FileTreeNodeListener l : listeners)
      l.treeNodeChanged(this);
  }

  public Treebank getTreebank() {
    return t;
  }

  public void setTreebank(Treebank t) {
    this.t = t;
  }

  public String getFilename() {
    if(file == null)
      return "root";
    else
      return file.getPath();
  }

  public File getFile() {
    return file;
  }

  public static interface FileTreeNodeListener {
    public void treeNodeChanged(FileTreeNode n);
  }


}

