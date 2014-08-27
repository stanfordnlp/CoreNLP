package edu.stanford.nlp.trees;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

import edu.stanford.nlp.ling.HasIndex;

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
 * @author Spence Green
 */
public final class DiskTreebank extends Treebank {

  private static boolean PRINT_FILENAMES = false;

  private final List<File> filePaths = new ArrayList<File>();
  private final List<FileFilter> fileFilters = new ArrayList<FileFilter>();

  /*
   * Absolute path of the file currently being read.
   */
  private String currentFilename; // = null;


  /**
   * Create a new DiskTreebank. The trees are made with a <code>LabeledScoredTreeReaderFactory</code>.
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
   * Create a new Treebank. The trees are made with a <code>LabeledScoredTreeReaderFactory</code>.
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
    if(path.exists()) {
      filePaths.add(path);
      fileFilters.add(filt);
    } else {
      System.err.printf("%s: File/path %s does not exist. Skipping.%n" , this.getClass().getName(), path.getPath());
    }
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
   * Returns the absolute path of the file currently being read.
   *
   */
  public String getCurrentFilename() {
    return currentFilename;
  }

  public List<File> getCurrentPaths() {
    return Collections.unmodifiableList(filePaths);
  }

  public void printFileNames() {
    PRINT_FILENAMES = true;
  }

  private class DiskTreebankIterator implements Iterator<Tree> {

    private TreeReader tr = null;
    private Tree storedTree = null;  // null means iterator is exhausted (or not yet constructed)

    //Create local copies so that calls to loadPath() in the parent class
    //don't cause exceptions i.e., this iterator is valid over the state of DiskTreebank
    //when the iterator is created.
    private final List<File> localPathList;
    private final List<FileFilter> localFilterList;
    private int fileListPtr = 0;

    private File currentFile;
    private int curLineId = 1;

    private List<File> curFileList;
    private Iterator<File> curPathIter;

    private DiskTreebankIterator() {
      localPathList = new ArrayList<File>(filePaths);
      localFilterList = new ArrayList<FileFilter>(fileFilters);

      if(primeNextPath() && primeNextFile())
        storedTree = primeNextTree();
    }

    //In the case of a recursive file filter, performs a BFS through the directory structure.
    private boolean primeNextPath() {
      while(fileListPtr < localPathList.size() && fileListPtr < localFilterList.size()) {
        final File nextPath = localPathList.get(fileListPtr);
        final FileFilter nextFilter = localFilterList.get(fileListPtr);
        fileListPtr++;

        final List<File> pathListing = ((nextPath.isDirectory()) ?
                                        Arrays.asList(nextPath.listFiles(nextFilter)) : Collections.singletonList(nextPath));

        if(pathListing != null) {
          if(pathListing.size() > 1) Collections.sort(pathListing);

          curFileList = new ArrayList<File>();
          for(File path : pathListing) {
            if(path.isDirectory()) {
              localPathList.add(path);
              localFilterList.add(nextFilter);
            } else {
              curFileList.add(path);
            }
          }

          if(curFileList.size() != 0) {
            curPathIter = curFileList.iterator();
            return true;
          }
        }
      }

      return false;
    }

    private boolean primeNextFile() {
      try {
        if(curPathIter.hasNext() || (primeNextPath() && curPathIter.hasNext())) {
          currentFile = curPathIter.next();
          currentFilename = currentFile.getAbsolutePath();
          if(PRINT_FILENAMES) System.err.println(currentFile);

          if(tr != null) tr.close();
          if(currentFile.getPath().endsWith(".gz")){
            tr = treeReaderFactory().newTreeReader(new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(currentFile)), encoding())));
          } else {
            tr = treeReaderFactory().newTreeReader(new BufferedReader(new InputStreamReader(new FileInputStream(currentFile), encoding())));
          }
          curLineId = 1;

          return true;
        }

      } catch (UnsupportedEncodingException e) {
        System.err.printf("%s: Filesystem does not support encoding:%n%s%n", this.getClass().getName(), e.toString());
        throw new RuntimeException(e);
      } catch (FileNotFoundException e) {
        System.err.printf("%s: File does not exist:%n%s%n", this.getClass().getName(),e.toString());
        throw new RuntimeException(e);
      } catch (IOException e) {
        System.err.printf("%s: Unable to close open tree reader:%n%s%n", this.getClass().getName(),currentFile.getPath());
        throw new RuntimeException(e);
      }
      return false;
    }

    private Tree primeNextTree() {
      Tree t = null;

      try {
        t = tr.readTree();
        if(t == null && primeNextFile()) //Current file is exhausted
          t = tr.readTree();

        //Associate this tree with a file and line number
        if(t != null && t.label() != null && t.label() instanceof HasIndex) {
          HasIndex lab = (HasIndex) t.label();
          lab.setSentIndex(curLineId++);
          lab.setDocID(currentFile.getName());
        }

      } catch (IOException e) {
        System.err.printf("%s: Error reading from file %s:%n%s%n", this.getClass().getName(), currentFile.getPath(), e.toString());
        throw new RuntimeException(e);
      }

      return t;
    }

    /**
     * Returns true if the iteration has more elements.
     */
    @Override
    public boolean hasNext() { return storedTree != null; }

    /**
     * Returns the next element in the iteration.
     */
    @Override
    public Tree next() {
      if(storedTree == null)
        throw new NoSuchElementException();

      Tree ret = storedTree;
      storedTree = primeNextTree();
      return ret;
    }

    /**
     * Not supported
     */
    @Override
    public void remove() { throw new UnsupportedOperationException(); }
  }


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
