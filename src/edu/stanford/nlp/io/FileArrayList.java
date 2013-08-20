package edu.stanford.nlp.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.util.*;

/**
 * A <code>FileArrayList</code> maintains a collection of <code>Files</code>.
 * It's really just an <code>ArrayList</code> except that it has some
 * fancier constructors that traverse paths, so as to build up the
 * <code>FileArrayList</code>.
 * It is built from a Collection of paths, or just from a single path.
 * Optionally one can also provide a <code>FileFilter</code> which is
 * applied over the files in a recursive traversal, or else
 * an extension and whether to do recursive traversal, which are used to
 * construct a filter.
 * Note that the Collection argument constructor will behave 'normally'
 * iff none of the Collection elements are directories.  If they are
 * directories they will be recursed and files in them added.  To get the
 * behavior of putting just directories in the collection one needs to either
 * call <code>addAll(Collection)</code> or else use the constructor
 * <code>FileArrayList(c, failFilt, true)</code>, where <code>failFilt</code>
 * is a user-supplied <code>FileFilter</code> that accepts no files.
 * The <code>FileArrayList</code> builds from these
 * constructor arguments a collection of <code>Files</code>, which can be
 * iterated over, etc.  Note that a <code>FileArrayList</code> stores the
 * full expanded list of files in memory.  This has the advantage
 * that the list can be easily modified, as per a standard collection,
 * but the disadvantage that it may take up a lot of space.
 * <p/>
 * The class provides some additional constructors beyond the two recommended
 * by the Collections package, to allow specifying a <code>FileFilter</code>
 * and similar options.  Nevertheless, so as to avoid overburdening the
 * the API, not every possibly useful constructor has been provided where
 * these can be easily synthesized using standard Collections package
 * facilities.  Useful idioms to know are:
 * <ul>
 * <li>To make a <code>FileArrayList</code> from an array of
 * <code>Files</code> or <code>Strings</code> <code>arr</code>:<br>
 * <code>FileArrayList fcollect = new FileArrayList(Arrays.asList(arr));
 * </code></li>
 * <li>To make a <code>FileArrayList</code> from a single
 * <code>File</code> or <code>String</code> <code>fi</code>:<br>
 * <code>FileArrayList fcollect =
 * new FileArrayList(Collections.singletonList(fi));</code></li>
 * </ul>
 * This class will throw an <code>IllegalArgumentException</code> if there
 * are things that are not existing Files or String paths to existing files
 * in the input collection (from the Constructor).
 *
 * @author Christopher Manning
 * @version 1.0, August 2002
 * @see FileSequentialCollection
 */
public class FileArrayList extends ArrayList<File> {

  /**
   * 
   */
  private static final long serialVersionUID = 5424659657299318194L;


  /**
   * Creates an empty <code>FileArrayList</code>, with no Files in it.
   */
  public FileArrayList() {
    this(null);
  }


  /**
   * Creates a <code>FileArrayList</code> from the passed in
   * <code>Collection</code>.  The constructor iterates through the
   * collection.  For each element, if it is a <code>File</code> or
   * <code>String</code>, then this file path is traversed for addition
   * to the collection.  If the argument is of some other type, an
   * <code>IllegalArgumentException</code> is thrown.
   * For each <code>File</code> or <code>String</code>, if they
   * do not correspond to directories, then they are added to the
   * collection; if they do, they are recursively explored and all
   * non-directories within them are added to the collection.
   *
   * @param c The collection to build the <code>FileArrayList</code> from
   */
  public FileArrayList(Collection<?> c) {
    this(c, (FileFilter) null);
  }


  /**
   * Creates a <code>FileArrayList</code> from the passed in
   * <code>File</code> path.  If the <code>File</code>
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
  public FileArrayList(File path, String suffix, boolean recursively) {
    this(Collections.singletonList(path), suffix, recursively);
  }


  /**
   * Creates a <code>FileArrayList</code> from the passed in
   * <code>Collection</code>.  The constructor iterates through the
   * collection.  For each element, if it is a <code>File</code>, then the
   * <code>File</code> is added to the collection, if it is a
   * <code>String</code>, then a <code>File</code> corresponding to this
   * <code>String</code> as a file path is added to the collection, and
   * if the argument is of some other type, an
   * <code>IllegalArgumentException</code> is thrown.  For the files
   * thus specified, they are included in the collection only if they
   * match an extension filter as specified by the other arguments.
   *
   * @param c           Collection of files or directories as Files or Strings
   * @param suffix      suffix (normally "File extension") of files to load
   * @param recursively true means descend into subdirectories as well.
   *                    Note that if a collection member is a directory, this code
   *                    will always look at the members of this directory.  This
   *                    variable controls whether subdirectories fo the directory are
   *                    examined.
   */
  public FileArrayList(Collection<?> c, String suffix, boolean recursively) {
    this(c, new ExtensionFileFilter(suffix, recursively), false);
  }


  /**
   * Creates a <code>FileArrayList</code> from the passed in
   * <code>Collection</code>.  The constructor iterates through the
   * collection.  For each element, if it is a <code>File</code> or
   * <code>String</code> then these file paths are processed as
   * explained below.
   * If the argument is of some other type, an
   * <code>IllegalArgumentException</code> is thrown.  For the files
   * specified, if they are not directories, they are included in the
   * collection.  If they are directories, files inside them are
   * included iff they match the <code>FileFilter</code>.  This will
   * include recursive directory descent iff the <code>FileFilter</code>
   * accepts directories.
   * If the path is a directory then only
   * files within the directory (perhaps recursively) that satisfy the
   * filter are processed.  If the <code>path</code>is a file, then
   * that file is processed regardless of whether it satisfies the
   * filter.  (This semantics was adopted, since otherwise there was no
   * easy way to go through all the files in a directory without
   * descending recursively via the specification of a
   * <code>FileFilter</code>.)
   *
   * @param c    The collection of file or directory to load from
   * @param filt A FileFilter of files to load.  This may be
   *             <code>null</code>, in which case all files are accepted.
   */
  public FileArrayList(Collection<?> c, FileFilter filt) {
    this(c, filt, false);
  }


  /**
   * Creates a <code>FileArrayList</code> from the passed in
   * <code>Collection</code>.  The constructor iterates through the
   * collection.  For each element, if it is a <code>File</code> or
   * <code>String</code> then these file paths are processed as
   * explained below.
   * If the argument is of some other type, an
   * <code>IllegalArgumentException</code> is thrown.  For the files
   * specified, if they are not directories, they are included in the
   * collection.  If they are directories, files inside them are
   * included iff they match the <code>FileFilter</code>.  This will
   * include recursive directory descent iff the <code>FileFilter</code>
   * accepts directories.
   * If the path is a directory then only
   * files within the directory (perhaps recursively) that satisfy the
   * filter are processed.  If the <code>path</code>is a file, then
   * that file is processed regardless of whether it satisfies the
   * filter.  (This semantics was adopted, since otherwise there was no
   * easy way to go through all the files in a directory without
   * descending recursively via the specification of a
   * <code>FileFilter</code>.)
   *
   * @param c           The collection of file or directory to load from.  An
   *                    argument of <code>null</code> is interpreted like an
   *                    empty collection.
   * @param filt        A FileFilter of files to load.  This may be
   *                    <code>null</code>, in which case all files are accepted.
   * @param includeDirs Whether to include director names in the file list
   */
  public FileArrayList(Collection<?> c, FileFilter filt, boolean includeDirs) {
    super(); // make it empty to start -- we can't really guess the size

    if (c != null) {
      for (Iterator<?> i = c.iterator(); i.hasNext();) {
        Object obj = i.next();
        if (obj instanceof String) {
          obj = new File((String) obj);
        }
        if (!(obj instanceof File)) {
          throw new IllegalArgumentException("Collection elements must be Files or Strings");
        }
        processPath((File) obj, filt, includeDirs);
      }
    }
  }


  /**
   * Creates a <tt>FileArrayList</tt> of all the files listed in
   * <tt>splitFile</tt>. <tt>splitFile</tt> should be a list of file paths,
   * one per line. File paths are taken to be relative to <tt>baseDir</tt>
   * unless <tt>baseDir</tt> is null, in which case they're taken to be
   * absolute.
   *
   * @param fileList File containing a list of file paths to load
   * @param baseDir  Relative base for files in fileList (if <tt>null</tt>,
   *                 paths are considered absolute)
   */
  public FileArrayList(File fileList, File baseDir) {
    super();
    if (!fileList.canRead()) {
      throw(new IllegalArgumentException("Can't read " + fileList));
    }
    BufferedReader br = null;
    try {
      br = new BufferedReader(new FileReader(fileList));
      String line;
      while ((line = br.readLine()) != null) {
        if (baseDir == null) {
          add(new File(line));
        } else {
          add(new File(baseDir, line));
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        if (br != null) {
          br.close();
        }
      } catch (Exception e) {
      }
    }
  }


  /**
   * Add to the collection files under a given directory and
   * perhaps its subdirectories.  If the path is a directory then only
   * files within the directory (perhaps recursively) that satisfy the
   * filter are processed.  If the <code>path</code>is a file, then
   * that file is processed regardless of whether it satisfies the
   * filter.  (This semantics was adopted, since otherwise there was no
   * easy way to go through all the files in a directory without
   * descending recursively via the specification of a
   * <code>FileFilter</code>.)
   *
   * @param path        file or directory to load from
   * @param filt        a FileFilter of files to load
   * @param includeDirs Whether to include director names in the file list
   */
  private void processPath(File path, FileFilter filt, boolean includeDirs) {
    if (path.isDirectory()) {
      if (includeDirs) {
        add(path);
      }
      // if path is a directory, look into it
      File[] directoryListing = path.listFiles(filt);
      if (directoryListing == null) {
        throw new IllegalArgumentException("Directory access problem for: " + path);
      }
      for (int i = 0; i < directoryListing.length; i++) {
        processPath(directoryListing[i], filt, includeDirs);
      }
    } else {
      // it's already passed the filter or was uniquely specified
      // if (filt.accept(path))
      // but check that it is a real file...
      if (!path.exists()) {
        throw new IllegalArgumentException("File doesn't exist: " + path);
      }
      add(path);
    }
  }


  /**
   * This is simply a debugging aid that tests the functionality of
   * the class.  The supplied arguments are put in a
   * <code>Collection</code>, and passed to the
   * <code>FileArrayList</code> constructor.  An iterator is then used
   * to print the names of all the files in the collection.
   *
   * @param args A list of file paths
   */
  public static void main(String[] args) {
    FileArrayList fcollect = new FileArrayList(Arrays.asList(args));
    for (File fi: fcollect) {
      System.out.println(fi);
    }

    if (true) {
      // test the other constructors
      System.out.println("Above was Collection constructor");
      System.out.println("Empty constructor");
      FileArrayList fcollect2 = new FileArrayList();
      for (File fi: fcollect2) {
        System.out.println(fi);
      }

      System.out.println("File String(mrg) boolean(true) constructor");
      FileArrayList fcollect3 = new FileArrayList(new File(args[0]), "mrg", true);
      for (File fi: fcollect3) {
        System.out.println(fi);
      }

      System.out.println("Collection String(mrg) boolean constructor");
      FileArrayList fcollect4 = new FileArrayList(Arrays.asList(args), "mrg", true);
      for (File fi: fcollect4) {
        System.out.println(fi);
      }

      System.out.println("Testing number range file filter");
      FileArrayList fcollect5 = new FileArrayList(Arrays.asList(args), new NumberRangeFileFilter(320, 410, true));
      for (File fi: fcollect5) {
        System.out.println(fi);
      }

      System.out.println("Testing number ranges file filter");
      FileArrayList fcollect7 = new FileArrayList(Arrays.asList(args), new NumberRangesFileFilter("21,33-40,82 , 200-299", true));
      for (File fi: fcollect7) {
        System.out.println(fi);
      }

      System.out.println("Testing null filter but include dirs");
      FileArrayList fcollect6 = new FileArrayList(Arrays.asList(args), (FileFilter) null, true);
      for (File fi: fcollect6) {
        System.out.println(fi);
      }
    }
  }

}
