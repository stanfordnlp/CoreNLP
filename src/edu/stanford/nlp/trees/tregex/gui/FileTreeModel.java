package edu.stanford.nlp.trees.tregex.gui;

import java.io.File;
import java.util.*;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import edu.stanford.nlp.io.NumberRangesFileFilter;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.trees.TransformingTreebank;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.gui.FileTreeNode.FileTreeNodeListener;
import edu.stanford.nlp.trees.tregex.gui.TregexGUI.FilterType;
import edu.stanford.nlp.util.Generics;

/**
 * Component for managing the data for files containing trees.
 *
 * @author Anna Rafferty
 */
@SuppressWarnings("serial")
public class FileTreeModel extends DefaultTreeModel implements FileTreeNodeListener {

  private final List<TreeModelListener> listeners;
  private final FileTreeNode root;
  private final Map<FileTreeNode, List<FileTreeNode>> treeStructure;

  public static final String DEFAULT_ENCODING = "UTF-8";
  public static final String DEFAULT_CHINESE_ENCODING = "GB18030";
  public static final String DEFAULT_NEGRA_ENCODING = " ISO-8859-1";

  private static String curEncoding = DEFAULT_ENCODING; // todo: shouldn't be static, but changing this requires fixing PreferencesPanel
  private static TreeReaderFactory trf; // todo: shouldn't be static, needs fixing in TreeFromFile


  public FileTreeModel(FileTreeNode root) {
   super(root);
   this.root = root;
   root.addListener(this);
   listeners = new ArrayList<>();
   treeStructure = Generics.newHashMap();
   treeStructure.put(root, new ArrayList<>());

   //other data
   trf = new TregexPattern.TRegexTreeReaderFactory();
  }

  @Override
  public void addTreeModelListener(TreeModelListener l) {
    listeners.add(l);
  }

  protected void fireTreeStructureChanged(TreePath parentPath) {
    TreeModelEvent e = null;
    for (TreeModelListener l : listeners) {
      if (e == null)
        e = new TreeModelEvent(this, parentPath, null, null);
      l.treeStructureChanged(e);
    }
  }

  @Override
  public FileTreeNode getChild(Object parent, int childNum) {
    List<FileTreeNode> children = treeStructure.get(parent);
    if (children == null || childNum < 0 || children.size() <= childNum) {
      return null;
    } else {
      return children.get(childNum);
    }
  }

  @Override
  public int getChildCount(Object parent) {
    List<FileTreeNode> children = treeStructure.get(parent);
    if (children == null) {
      return 0;
    } else {
      return children.size();
    }
  }

  @Override
  public int getIndexOfChild(Object parent, Object child) {
    if(parent == null || child == null) {
      return -1;
    }
    List<FileTreeNode> children = treeStructure.get(parent);
    if (children == null) {
      return -1;
    } else {
      return children.indexOf(child);
    }
  }

  @Override
  public boolean isLeaf(Object node) {
    List<FileTreeNode> children = treeStructure.get(node);
    return children == null;
  }

  @Override
  public void removeTreeModelListener(TreeModelListener l) {
    listeners.remove(l);
  }

  public void treeNodeChanged(FileTreeNode n) {
    TreePath t = new TreePath(makeTreePathArray(n));
    //System.out.println("Tree path is: " + t);
    this.fireTreeStructureChanged(t);

  }

  /**
   * Returns true if the root has no children; false otherwise
   */
  public boolean isEmpty() {
    return this.getChildCount(root) == 0;
  }

  private Object[] makeTreePathArray(FileTreeNode node) {
    List<TreeNode> path = new ArrayList<>();
    path.add(node);
    TreeNode child = node;
    while(child != this.getRoot()) {
      child = child.getParent();
      path.add(0, child);
    }
    return path.toArray();
  }


  @Override
  public FileTreeNode getRoot() {
    return root;
  }

  /**
   * Forks off a new thread to load your files based on the filters you set in the interface
   */
  public void addFileFolder(final EnumMap<FilterType, String> filters, final File[] files) {
    List<FileTreeNode> newFiles = new ArrayList<>();
    findLoadableFiles(filters, files, newFiles, FileTreeModel.this.getRoot());//findLoadableFiles updates newFiles
    for(FileTreeNode fileNode : newFiles) {
      Treebank treebank = new DiskTreebank(trf, curEncoding);
      treebank.loadPath(fileNode.getFile(), null, true);
      TreeTransformer transformer = TregexGUI.getInstance().transformer;
      if (transformer != null) {
        treebank = new TransformingTreebank(treebank, transformer);
      }
      fileNode.setTreebank(treebank);
    }
    // System.out.println("Loadable files are: " + newFiles);
    FileTreeModel.this.fireTreeStructureChanged(new TreePath(getRoot()));
  }


  private void findLoadableFiles(EnumMap<FilterType, String> filters, File[] files,
       List<FileTreeNode> newFiles, FileTreeNode parent) {
    for(File f : files) {
      if(f.isDirectory()) {
        if(isLikelyInvisible(f.getName()))
          continue;
        FileTreeNode newParent = createNode(f, parent);
        treeStructure.put(newParent, new ArrayList<>());
        //recursively call on all the files inside
        findLoadableFiles(filters, f.listFiles(), newFiles, newParent);
        if(!treeStructure.get(newParent).isEmpty()) {//only add non-empty directories
          List<FileTreeNode> value = treeStructure.get(parent);
          value.add(newParent);
        }
      } else {
        boolean loadFile = checkFile(filters,f);
        if(loadFile) {
          FileTreeNode newFile = addToMap(f, parent);
          if(TregexGUI.getInstance().isTdiffEnabled() && FilePanel.getInstance().getActiveTreebanks().size() > TregexGUI.MAX_TDIFF_TREEBANKS)
            newFile.setActive(false);
          newFiles.add(newFile);
          //System.out.println("Loading: " + loadFile);
        }
      }
    }
  }

  private FileTreeNode createNode(File f, FileTreeNode parent) {
    FileTreeNode newNode = new FileTreeNode(f,parent);
    newNode.addListener(this);
    return newNode;
  }

  private FileTreeNode addToMap(File f, FileTreeNode parent) {
    List<FileTreeNode> value = treeStructure.get(parent);
    if(value == null) {
      throw new RuntimeException("Something very very bad has happened; a parent was not in the tree for the given child; parent: " + parent);
    }
    FileTreeNode newNode = createNode(f, parent);
    value.add(newNode);
    return newNode;
  }

  private static boolean checkFile(EnumMap<FilterType, String> filters, File file) {
    String fileName = file.getName();
    if(isLikelyInvisible(fileName))
      return false;
    if(filters.containsKey(FilterType.hasExtension)) {
      String ext = filters.get(FilterType.hasExtension);
      if(!fileName.endsWith(ext)) {
        return false;
      }
    }
    if(filters.containsKey(FilterType.hasPrefix)) {
      String pre = filters.get(FilterType.hasPrefix);
      if(!fileName.startsWith(pre))
        return false;
    }
    if(filters.containsKey(FilterType.isInRange)) {
      NumberRangesFileFilter f = new NumberRangesFileFilter(filters.get(FilterType.isInRange), false);
      if(!f.accept(fileName))
        return false;
    }
    return true;
  }

  //filter files and directories that start with .
  private static boolean isLikelyInvisible(String filename) {
    return filename.startsWith(".");
  }

  public static TreeReaderFactory getTRF() {
    return trf;
  }

  public static void setTRF(TreeReaderFactory trf) {
    FileTreeModel.trf = trf;
  }

  public static String getCurEncoding() {
    return curEncoding;
  }

  public static void setCurEncoding(String curEncoding) {
    FileTreeModel.curEncoding = curEncoding;
  }


}
