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
  /** The file this node represents, or null for the root */
  private File file;
  /** The check box displayed for a file, or null for a directory or the root */
  private JCheckBox check = null;
  /** The label displayed for a directory or the root, or null for a file */
  private JLabel label =null;
  /** The trees read from the file, if they have been set */
  private Treebank t;
  /** Listeners told when the node is activated or deactivated */
  private final ArrayList<FileTreeNodeListener> listeners = new ArrayList<>();

  /**
   * Creates a root node, labeled "root", which has no file.
   */
  public FileTreeNode() {
    super();
    label = new JLabel("root");
    this.setAllowsChildren(true);
  }

  /**
   * Creates a node for a file or directory.  A file is displayed as a
   * check box, initially checked, and cannot have children; a directory
   * is displayed as a label and can have children.
   *
   * @param file the file or directory
   * @param parent the parent node; this node is not added to the parent's children
   */
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

  /**
   * Returns the component used to display this node.
   *
   * @return the check box for a file, or the label for a directory or the root
   */
  public JComponent getDisplay() {
    if(check != null)
      return check;
    else
      return label;
  }

  /**
   * Returns whether this node is a file whose check box is checked.
   *
   * @return true if the file is checked; always false for a directory or the root
   */
  public boolean isActive() {
    if(check == null)
      return false;
    else
      return check.isSelected();
  }

  /**
   * Checks or unchecks this node's check box, telling the listeners if that changes it.
   * Does nothing for a directory or the root.
   *
   * @param active whether the file should be checked
   */
  public void setActive(boolean active) {
    if(check != null && (check.isSelected() != active)) {
      check.setSelected(active);
      sendToListeners();
    }
  }

  /**
   * Adds a listener to be told when {@link #setActive} changes this node.
   *
   * @param l the listener
   */
  public void addListener(FileTreeNodeListener l) {
    listeners.add(l);
  }

  private void sendToListeners() {
    for(FileTreeNodeListener l : listeners)
      l.treeNodeChanged(this);
  }

  /**
   * Returns the trees for this node's file.
   *
   * @return the treebank, or null if none has been set
   */
  public Treebank getTreebank() {
    return t;
  }

  /**
   * Sets the trees for this node's file.
   *
   * @param t the treebank
   */
  public void setTreebank(Treebank t) {
    this.t = t;
  }

  /**
   * Returns the path of this node's file.
   *
   * @return the file's path, or "root" for the root node
   */
  public String getFilename() {
    if(file == null)
      return "root";
    else
      return file.getPath();
  }

  /**
   * Returns this node's file.
   *
   * @return the file, or null for the root node
   */
  public File getFile() {
    return file;
  }

  /** A listener for changes to whether a FileTreeNode is active. */
  public static interface FileTreeNodeListener {
    /**
     * Called when a node is checked or unchecked by {@link FileTreeNode#setActive}.
     *
     * @param n the node which changed
     */
    public void treeNodeChanged(FileTreeNode n);
  }


}

