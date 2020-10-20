package edu.stanford.nlp.io;

import java.io.File;
import java.io.FileFilter;
import java.util.*;

/**
 * A {@code FileSequentialCollection} maintains a read-only
 * collection of {@code Files}.  (It's a list, but we don't
 * make it a List or else one needs an iterator that can go backwards.)
 * It is built from a Collection of paths, or just from a single path.
 * Optionally one can also provide a {@code FileFilter} which is
 * applied over the files in a recursive traversal, or else
 * an extension and whether to do recursive traversal, which are used to
 * construct a filter.
 * Note that the Collection argument constructor will behave 'normally'
 * iff none of the Collection elements are directories.  If they are
 * directories they will be recursed and files in them added.  To get the
 * behavior of putting just directories in the collection one needs to
 * use the constructor
 * {@code FileSequentialCollection(c, failFilt, true)},
 * where {@code failFilt}
 * is a user-supplied {@code FileFilter} that accepts no files.
 * The {@code FileSequentialCollection} builds from these
 * constructor arguments a collection of {@code Files}, which can be
 * iterated over, etc.  This class does runtime expansion of paths.
 * That is, it is optimized for iteration and not for random access.
 * It is also an unmodifiable Collection.
 *
 * The class provides some additional constructors beyond the two recommended
 * by the Collections package, to allow specifying a {@code FileFilter}
 * and similar options.  Nevertheless, so as to avoid overburdening the
 * the API, not every possibly useful constructor has been provided where
 * these can be easily synthesized using standard Collections package
 * facilities.  Useful idioms to know are:
 * <ul>
 * <li>To make a {@code FileSequentialCollection} from an array of
 * {@code Files} or {@code Strings} {@code arr}:<br>
 * {@code FileSequentialCollection fcollect = new FileSequentialCollection(Arrays.asList(arr)); }
 * </li>
 * <li>To make a {@code FileSequentialCollection} from a single
 * {@code File} or {@code String} fi:<br>
 * {@code FileSequentialCollection fcollect =
 * new FileSequentialCollection(Collections.singletonList(fi)); }</li>
 * </ul>
 * This class will throw an {@code IllegalArgumentException} if there
 * are things that are not existing Files or String paths to existing files
 * in the input collection (from the Iterator).
 *
 * @author Christopher Manning
 * @version 1.0, August 2002
 */
public class FileSequentialCollection extends AbstractCollection<File>  {

  /**
   * Stores the input collection over which we work.  This is
   * commonly a brief summary of a full set of files.
   */
  private final Collection<?> coll;

  /**
   * A filter for files to match.
   */
  private final FileFilter filt;

  private final boolean includeDirs;


  /**
   * Creates an empty {@code FileSequentialCollection}, with no Files
   * in it.  Since a {@code FileSequentialCollection} is not
   * modifiable, this is
   * largely useless (except if you want an empty one).
   */
  public FileSequentialCollection() { this((Collection<?>) null); }


  /**
   * Creates a {@code FileSequentialCollection} from the passed in
   * {@code Collection}.  The constructor iterates through the
   * collection.  For each element, if it is a {@code File} or
   * {@code String}, then this file path is traversed for addition
   * to the collection.  If the argument is of some other type, an
   * {@code IllegalArgumentException} is thrown.
   * For each {@code File} or {@code String}, if they
   * do not correspond to directories, then they are added to the
   * collection; if they do, they are recursively explored and all
   * non-directories within them are added to the collection.
   *
   * @param c The collection to build the
   *          {@code FileSequentialCollection} from
   */
  public FileSequentialCollection(Collection<?> c) {
    this(c, null);
  }


  /**
   * Creates a {@code FileSequentialCollection} from the passed in
   * {@code File} path.  If the {@code File}
   * does not correspond to a directory, then it is added to the
   * collection; if it does, it is explored.  Files
   * that match the extension, and files in subfolders that match, if
   * appropriate, are added to the collection.
   * This is an additional convenience constructor.
   *
   * @param path        file or directory to load from
   * @param suffix      suffix (normally "File extension") of files to load
   * @param recursively true means descend into subdirectories as well
   */
  public FileSequentialCollection(File path, String suffix, boolean recursively) {
    this(Collections.singletonList(path), suffix, recursively);
  }


  /**
   * Creates a {@code FileSequentialCollection} from the passed in
   * {@code Collection}.  The constructor iterates through the
   * collection.  For each element, if it is a {@code File}, then the
   * {@code File} is added to the collection, if it is a
   * {@code String}, then a {@code File} corresponding to this
   * {@code String} as a file path is added to the collection, and
   * if the argument is of some other type, an
   * {@code IllegalArgumentException} is thrown.  For the files
   * thus specified, they are included in the collection only if they
   * match an extension filter as specified by the other arguments.
   *
   * @param c           Collection of files or directories as Files or Strings
   * @param suffix      suffix (normally "File extension") of files to load
   * @param recursively true means descend into subdirectories as well
   */
  public FileSequentialCollection(Collection<?> c, String suffix, boolean recursively) {
    this(c, new ExtensionFileFilter(suffix, recursively), false);
  }


  /**
   * Creates a {@code FileSequentialCollection} from the passed in
   * {@code Collection}.  The constructor iterates through the
   * collection.  For each element, if it is a {@code File} or
   * {@code String} then these file paths are processed as
   * explained below.
   * If the argument is of some other type, an
   * {@code IllegalArgumentException} is thrown.  For the files
   * specified, if they are not directories, they are included in the
   * collection.  If they are directories, files inside them are
   * included iff they match the {@code FileFilter}.  This will
   * include recursive directory descent iff the {@code FileFilter}
   * accepts directories.
   * If the path is a directory then only
   * files within the directory (perhaps recursively) that satisfy the
   * filter are processed.  If the {@code path}is a file, then
   * that file is processed regardless of whether it satisfies the
   * filter.  (This semantics was adopted, since otherwise there was no
   * easy way to go through all the files in a directory without
   * descending recursively via the specification of a
   * {@code FileFilter}.)
   *
   * @param c    The collection of file or directory to load from
   * @param filt A FileFilter of files to load.  This may be
   *             {@code null}, in which case all files are accepted.
   */
  public FileSequentialCollection(Collection<?> c, FileFilter filt) {
    this(c, filt, false);
  }

  public FileSequentialCollection(String filename, FileFilter filt) {
    this(Collections.singletonList(filename), filt);
  }

  public FileSequentialCollection(String filename) { this(filename,  null); }

  /**
   * Creates a {@code FileSequentialCollection} from the passed in
   * {@code Collection}.  The constructor iterates through the
   * collection.  For each element, if it is a {@code File} or
   * {@code String} then these file paths are processed as
   * explained below.
   * If the argument is of some other type, an
   * {@code IllegalArgumentException} is thrown.  For the files
   * specified, if they are not directories, they are included in the
   * collection.  If they are directories, files inside them are
   * included iff they match the {@code FileFilter}.  This will
   * include recursive directory descent iff the {@code FileFilter}
   * accepts directories.
   * If the path is a directory then only
   * files within the directory (perhaps recursively) that satisfy the
   * filter are processed.  If the {@code path}is a file, then
   * that file is processed regardless of whether it satisfies the
   * filter.  (This semantics was adopted, since otherwise there was no
   * easy way to go through all the files in a directory without
   * descending recursively via the specification of a
   * {@code FileFilter}.)
   *
   * @param c           The collection of file or directory to load from.  An
   *                    argument of {@code null} is interpreted like an
   *                    empty collection.
   * @param filt        A FileFilter of files to load.  This may be
   *                    {@code null}, in which case all files are accepted
   * @param includeDirs Whether to include directory names in the file list
   */
  public FileSequentialCollection(Collection<?> c, FileFilter filt, boolean includeDirs) {
    super();
    // store the arguments.  They are expanded by the iterator
    if (c == null) {
      coll = new ArrayList<>();
    } else {
      coll = c;
    }
    this.filt = filt;
    this.includeDirs = includeDirs;
  }


  /**
   * Returns the size of the FileSequentialCollection.
   *
   * @return size How many files are in the collection
   */
  @SuppressWarnings({"UnusedDeclaration","unused"})
  @Override
  public int size() {
    int counter = 0;
    for (File f : this) {
      counter++;
    }
    return counter;
  }


  /**
   * Return an Iterator over files in the collection.
   * This version lazily works its way down directories.
   */
  @Override
  public Iterator<File> iterator() {
    return new FileSequentialCollectionIterator();
  }


  /**
   * This is the iterator that gets returned
   */
  private final class FileSequentialCollectionIterator implements Iterator<File> {

    // current state is a rootsIterator, a position in a recursion
    // under a directory listing, and a pointer in the current
    // directory.

    private Object[] roots;  // these may be of type File or String
    private int rootsIndex;
    // these next two simulate a list of pairs, but I was too lazy to
    // make an extra class
    private Stack<Object> fileArrayStack;
    private Stack<Integer> fileArrayStackIndices;
    private File next;

    public FileSequentialCollectionIterator() {
      // log.info("Coll is " + coll);
      roots = coll.toArray();
      rootsIndex = 0;
      fileArrayStack = new Stack<>();
      fileArrayStackIndices = new Stack<>();
      if (roots.length > 0) {
        fileArrayStack.add(roots[rootsIndex]);
        fileArrayStackIndices.push(Integer.valueOf(0));
      }
      next = primeNextFile();
    }

    @Override
    public boolean hasNext() {
      return next != null;
    }

    /**
     * Returns the next element in the iteration.
     */
    @Override
    public File next() {
      if (next == null) {
        throw new NoSuchElementException("FileSequentialCollection exhausted");
      }
      File ret = next;
      next = primeNextFile();
      return ret;
    }

    /**
     * Not supported
     */
    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

    /**
     * Returns the next file to be accessed, or {@code null} if
     * there are none left.  This is all quite hairy to write as an
     * iterator....
     *
     * @return The next file
     */
    private File primeNextFile() {
      while (rootsIndex < roots.length) {
        while (!fileArrayStack.empty()) {
          // log.info("fileArrayStack: " + fileArrayStack);
          Object obj = fileArrayStack.peek();
          if (obj instanceof File[]) {
            // log.info("Got a File[]");
            File[] files = (File[]) obj;
            Integer index = fileArrayStackIndices.pop();
            int ind = index.intValue();
            if (ind < files.length) {
              index = Integer.valueOf(ind + 1);
              fileArrayStackIndices.push(index);
              fileArrayStack.push(files[ind]);
              // loop around to process this new file
            } else {
              // this directory is finished and we pop up
              fileArrayStack.pop();
            }
          } else {
            // take it off the stack: tail recursion optimization
            fileArrayStack.pop();
            if (obj instanceof String) {
              obj = new File((String) obj);
            }
            if (!(obj instanceof File)) {
              throw new IllegalArgumentException("Collection elements must be Files or Strings");
            }
            File path = (File) obj;
            if (path.isDirectory()) {
              // log.info("Got directory " + path);
              // if path is a directory, look into it
              File[] directoryListing = path.listFiles(filt);
              if (directoryListing == null) {
                throw new IllegalArgumentException("Directory access problem for: " + path);
              }
              // log.info("  with " +
              //	    directoryListing.length + " files in it.");
              if (includeDirs) {
                // log.info("Include dir as answer");
                if (directoryListing.length > 0) {
                  fileArrayStack.push(directoryListing);
                  fileArrayStackIndices.push(Integer.valueOf(0));
                }
                return path;
              } else {
                // we don't include the dir, so we'll push
                // the directory and loop around again ...
                if (directoryListing.length > 0) {
                  fileArrayStack.push(directoryListing);
                  fileArrayStackIndices.push(Integer.valueOf(0));
                }
                // otherwise there was nothing in the
                // directory; we will pop back up
              }
            } else {
              // it's just a fixed file
              // log.info("Got a plain file " + path);
              if (!path.exists()) {
                throw new IllegalArgumentException("File doesn't exist: " + path);
              }
              return path;
            }
          }
          // go through loop again. we've pushed or popped as needed
        }
        // finished this root entry; go on to the next
        rootsIndex++;
        if (rootsIndex < roots.length) {
          fileArrayStack.add(roots[rootsIndex]);
          fileArrayStackIndices.push(Integer.valueOf(0));
        }
      }
      // finished everything
      return null;
    }

  }


  /**
   * This is simply a debugging aid that tests the functionality of
   * the class.  The supplied arguments are put in a
   * {@code Collection}, and passed to the
   * {@code FileSequentialCollection} constructor.
   * An iterator is then used to print the names of all the files
   * (but not directories) in the collection.
   *
   * @param args A list of file paths
   */
  public static void main(String[] args) {
    FileSequentialCollection fcollect = new FileSequentialCollection(Arrays.asList(args));
    for (File fi: fcollect) {
      System.out.println(fi);
    }

    // test the other constructors
    System.out.println("Above was Collection constructor");
    System.out.println("Empty constructor");
    FileSequentialCollection fcollect2 = new FileSequentialCollection();
    for (File fi : fcollect2) {
      System.out.println(fi);
    }

    System.out.println("File String(mrg) boolean(true) constructor");
    FileSequentialCollection fcollect3 = new FileSequentialCollection(new File(args[0]), "mrg", true);
    for (File fi : fcollect3) {
      System.out.println(fi);
    }

    System.out.println("Collection String(mrg) boolean constructor");
    FileSequentialCollection fcollect4 = new FileSequentialCollection(Arrays.asList(args), "mrg", true);
    for (File fi: fcollect4) {
      System.out.println(fi);
    }

    System.out.println("Testing number range file filter");
    FileSequentialCollection fcollect5 = new FileSequentialCollection(Arrays.asList(args), new NumberRangeFileFilter(320, 410, true));
    for (File fi: fcollect5) {
      System.out.println(fi);
    }

    System.out.println("Testing null filter but include dirs");
    FileSequentialCollection fcollect6 = new FileSequentialCollection(Arrays.asList(args), (FileFilter) null, true);
    for (File fi : fcollect6) {
      System.out.println(fi);
    }
  }

}
