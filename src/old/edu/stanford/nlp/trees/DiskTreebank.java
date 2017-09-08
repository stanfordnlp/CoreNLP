package old.edu.stanford.nlp.trees;

import java.io.*;
import java.util.*;

import old.edu.stanford.nlp.io.FileSequentialCollection;
import old.edu.stanford.nlp.io.RuntimeIOException;


/**
 * A <code>DiskTreebank</code> is a <code>Collection</code> of
 * <code>Tree</code>s.
 * A <code>DiskTreebank</code> object stores merely the information to
 * get at a corpus of trees that is stored on disk.  Access is usually
 * via apply()'ing a TreeVisitor to each Tree in the Treebank or by using
 * an iterator() to get an iteration over the Trees.
 * <p/>
 * If the root Label of the Tree objects built by the TreeReader
 * implements HasIndex, then the filename and index of the tree in
 * a corpus will be inserted as they are read in.
 *
 * @author Christopher Manning
 */
public final class DiskTreebank extends Treebank {

  private static final boolean PRINT_FILENAMES = false;

  private final ArrayList<File> filePaths = new ArrayList<File>();
  private final ArrayList<FileFilter> fileFilters = new ArrayList<FileFilter>();

  /**
   * Maintains as a class variable the <code>File</code> from which
   * trees are currently being read.
   */
  private File currentFile; // = null;


  /**
   * Create a new DiskTreebank.
   * The trees are made with a <code>LabeledScoredTreeReaderFactory</code>.
   * <p/>
   * <i>Compatibility note: Until Sep 2004, this used to create a Treebank
   * with a SimpleTreeReaderFactory, but this was changed as the old
   * default wasn't very useful, especially to naive users.</i>
   */
  public DiskTreebank() {
    this(new LabeledScoredTreeReaderFactory());
  }

  /**
   * Create a new treebank, set the encoding for file access.
   *
   * @param encoding The charset encoding to use for treebank file decoding
   */
  public DiskTreebank(String encoding) {
    this(new LabeledScoredTreeReaderFactory(), encoding);
  }

  /**
   * Create a new DiskTreebank.
   *
   * @param trf the factory class to be called to create a new
   *            <code>TreeReader</code>
   */
  public DiskTreebank(TreeReaderFactory trf) {
    super(trf);
  }

  /**
   * Create a new DiskTreebank.
   *
   * @param trf      the factory class to be called to create a new
   *                 <code>TreeReader</code>
   * @param encoding The charset encoding to use for treebank file decoding
   */
  public DiskTreebank(TreeReaderFactory trf, String encoding) {
    super(trf, encoding);
  }

  /**
   * Create a new Treebank.
   * The trees are made with a <code>LabeledScoredTreeReaderFactory</code>.
   * <p/>
   * <i>Compatibility note: Until Sep 2004, this used to create a Treebank
   * with a SimpleTreeReaderFactory, but this was changed as the old
   * default wasn't very useful, especially to naive users.</i>
   *
   * @param initialCapacity The initial size of the underlying Collection.
   *                        For a <code>DiskTreebank</code>, this parameter is ignored.
   */
  public DiskTreebank(int initialCapacity) {
    this(initialCapacity, new LabeledScoredTreeReaderFactory());
  }

  /**
   * Create a new Treebank.
   *
   * @param initialCapacity The initial size of the underlying Collection,
   *                        For a <code>DiskTreebank</code>, this parameter is ignored.
   * @param trf             the factory class to be called to create a new
   *                        <code>TreeReader</code>
   */
  @SuppressWarnings({"UnusedDeclaration"})
  public DiskTreebank(int initialCapacity, TreeReaderFactory trf) {
    this(trf);
  }


  /**
   * Empty a <code>Treebank</code>.
   */
  @Override
  public void clear() {
    filePaths.clear();
    fileFilters.clear();
  }

  /**
   * Load trees from given directory.  This version just records
   * the paths to be processed, and actually processes them at apply time.
   *
   * @param path file or directory to load from
   * @param filt a FilenameFilter of files to load
   */
  @Override
  public void loadPath(File path, FileFilter filt) {
    filePaths.add(path);
    fileFilters.add(filt);
  }

  /**
   * Applies the TreeVisitor to to all trees in the Treebank.
   *
   * @param tp A class that can process trees.
   */
  @Override
  public void apply(final TreeVisitor tp) {
    for (Tree t : this) {
      tp.visitTree(t);
    }
  }

  /**
   * Return the <code>File</code> from which trees are currently being
   * read by an Iterator or <code>apply()</code> and passed to a
   * <code>TreePprocessor</code>.
   * <p/>
   * This is useful if one wants to map the original file and
   * directory structure over to a set of modified trees.  New code
   * might prefer to build trees with labels that implement
   * HasIndex.
   *
   * @return the file that trees are currently being read from, or
   *         <code>null</code> if no file is currently open
   */
  public File getCurrentFile() {
    return currentFile;
  }


  private class DiskTreebankIterator implements Iterator<Tree> {

    private int fileUpto; // = 0 (will start on index array 0)
    Iterator<File> fileIterator;
    private TreeReader tr;
    private Tree storedTree;  // null means iterator is exhausted (or not yet constructed)

    private DiskTreebankIterator() {
      storedTree = primeNextTree();
    }

    private Tree primeNextTree() {
      Tree nextTree = null;
      int fpsize = filePaths.size();
      while (nextTree == null && fileUpto <= fpsize) {
        if (tr == null && (fileIterator == null || ! fileIterator.hasNext())) {
          if (fileUpto < fpsize) {
            FileSequentialCollection fsc = new FileSequentialCollection(Collections.singletonList(filePaths.get(fileUpto)), fileFilters.get(fileUpto));
            fileIterator = fsc.iterator();
          }
          // else we're finished, but increment anyway so we leave outermost loop
          fileUpto++;
        }
        while (nextTree == null && (tr != null || (fileIterator != null && fileIterator.hasNext()))) {
          try {
            while (nextTree == null && (tr != null || (fileIterator != null && fileIterator.hasNext()))) {
              if (tr != null) {
                nextTree = tr.readTree();
                if (nextTree == null) {
                  tr.close();
                  tr = null;
                }
              }
              if (nextTree == null && (fileIterator != null && fileIterator.hasNext())) {
                currentFile = fileIterator.next();
                // maybe print file name to stdout to get some feedback
                if (PRINT_FILENAMES) {
                  System.err.println(currentFile);
                }
                tr = treeReaderFactory().newTreeReader(new BufferedReader(new InputStreamReader(new FileInputStream(currentFile), encoding())));
              }
            }
          } catch (IOException e) {
            throw new RuntimeIOException("primeNextTree IO Exception in file " + currentFile, e);
          }
        }
      }
      if (nextTree == null) {
        currentFile = null;
      }
      return nextTree;
    }


    /**
     * Returns true if the iteration has more elements.
     */
    public boolean hasNext() {
      return storedTree != null;
    }

    /**
     * Returns the next element in the iteration.
     */
    public Tree next() {
      if (storedTree == null) {
        throw new NoSuchElementException();
      }
      Tree ret = storedTree;
      storedTree = primeNextTree();
      return ret;
    }

    /**
     * Not supported
     */
    public void remove() {
      throw new UnsupportedOperationException();
    }

  } // end class DiskTreebankIterator


  /**
   * Return an Iterator over Trees in the Treebank.  This is implemented
   * by building per-file MemoryTreebanks for the files in the
   * DiskTreebank.  As such, it isn't as efficient as using
   * <code>apply()</code>.
   */
  @Override
  public Iterator<Tree> iterator() {
    return new DiskTreebankIterator();
  }

}
