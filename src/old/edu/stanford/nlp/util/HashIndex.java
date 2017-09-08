package old.edu.stanford.nlp.util;

import java.io.*;
import java.util.*;

/**
 * An Index is a collection that maps between an Object vocabulary and a
 * contiguous non-negative integer index series beginning (inclusively) at 0.
 * It supports constant-time lookup in
 * both directions (via <code>get(int)</code> and <code>indexOf(E)</code>.
 * The <code>indexOf(E)</code> method compares objects by
 * <code>equals</code>, as other Collections.
 * <p/>
 * The typical usage would be:
 * <p><code>Index index = new Index(collection);</code>
 * <p> followed by
 * <p><code>int i = index.indexOf(object);</code>
 * <p> or
 * <p><code>Object o = index.get(i);</code>
 * <p>The source contains a concrete example of use as the main method.
 * <p>An Index can be locked or unlocked: a locked index cannot have new
 * items added to it.
 *
 * @author <a href="mailto:klein@cs.stanford.edu">Dan Klein</a>
 * @version 1.0
 * @see AbstractCollection
 * @since 1.0
 * @author <a href="mailto:yeh1@stanford.edu">Eric Yeh</a> (added write to/load from buffer)
 */
public class HashIndex<E> extends AbstractCollection<E> implements Index<E>, RandomAccess {

  // these variables are also used in IntArrayIndex
  ArrayList<E> objects = new ArrayList<E>();
  HashMap<E,Integer> indexes = new HashMap<E,Integer>();
  boolean locked; // = false;

  /**
   * Clears this Index.
   */
  @Override
  public void clear() {
    objects.clear();
    indexes.clear();
  }

  /**
   * Returns the index of each elem in a List.
   * @param elems The list of items
   * @return An array of indices
   */
  public int[] indices(Collection<E> elems) {
    int[] indices = new int[elems.size()];
    int i = 0;
    for (E elem : elems) {
      indices[i++] = indexOf(elem);
    }
    return indices;
  }

  /**
   * Looks up the objects corresponding to an array of indices, and returns them in a {@link Collection}.
   * This collection is not a copy, but accesses the data structures of the Index.
   * @param indices An array of indices
   * @return a {@link Collection} of the objects corresponding to the indices argument.
   */
  public Collection<E> objects(final int[] indices) {
    return new AbstractList<E>() {
      @Override
      public E get(int index) {
        return objects.get(indices[index]);
      }

      @Override
      public int size() {
        return indices.length;
      }
    };
  }

  /**
   * Returns the number of indexed objects.
   * @return the number of indexed objects.
   */
  @Override
  public int size() {
    return objects.size();
  }

  /**
   * Gets the object whose index is the integer argument.
   * @param i the integer index to be queried for the corresponding argument
   * @return the object whose index is the integer argument.
   */
  public E get(int i) {
    return objects.get(i);
  }

  /**
   * Returns a complete {@link List} of indexed objects, in the order of their indices.  <b>DANGER!</b>
   * The current implementation returns the actual index list, not a defensive copy.  Messing with this List
   * can seriously screw up the state of the Index.  (perhaps this method needs to be eliminated? I don't think it's
   * ever used in ways that we couldn't use the Index itself for directly.  --Roger, 12/29/04)
   * @return a complete {@link List} of indexed objects
   */
  public List<E> objectsList() {
    return objects;
  }

  /**
   * Queries the Index for whether it's locked or not.
   * @return whether or not the Index is locked
   */
  public boolean isLocked() {
    return locked;
  }

  /** Locks the Index.  A locked index cannot have new elements added to it (calls to {@link #add} will
   * leave the Index unchanged and return <code>false</code>).*/
  public void lock() {
    locked = true;
  }

  /** Unlocks the Index.  A locked index cannot have new elements added to it (calls to {@link #add} will
   * leave the Index unchanged and return <code>false</code>).*/
  public void unlock() {
    locked = false;
  }

  /**
   * Returns the integer index of the Object in the Index or -1 if the Object is not already in the Index.
   * @param o the Object whose index is desired.
   * @return the index of the Object argument.  Returns -1 if the object is not in the index.
   */
  public int indexOf(E o) {
    return indexOf(o, false);
  }

  /**
   * Takes an Object and returns the integer index of the Object,
   * perhaps adding it to the index first.
   * Returns -1 if the Object is not in the Index.
   * <p>
   * <i>Notes:</i> The method indexOf(x, true) is the direct replacement for
   * the number(x) method in the old Numberer class.  This method now uses a
   * Semaphore object to make the index safe for concurrent multithreaded
   *  usage. (CDM: Is this better than using a syncronized block?)
   *
   * @param o the Object whose index is desired.
   * @param add Whether it is okay to add new items to the index
   * @return The index of the Object argument.  Returns -1 if the object is not in the index.
   */
  public int indexOf(E o, boolean add) {
    Integer index = indexes.get(o);
    if (index == null) {
      if (add && ! locked) {
        try {
          semaphore.acquire();
          index = indexes.get(o);
          if (index == null) {
            index = objects.size();
            objects.add(o);
            indexes.put(o, index);
          }
          semaphore.release();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      } else {
        return -1;
      }
    }
    return index;
  }

  private java.util.concurrent.Semaphore semaphore = new java.util.concurrent.Semaphore(1);
  
  // TODO: delete this because we can leach off of Abstract Collection
 /**
   * Adds every member of Collection to the Index. Does nothing for members already in the Index.
   *
   * @return true if some item was added to the index and false if no
   *         item was already in the index or if the index is locked
   */
 @Override
  public boolean addAll(Collection<? extends E> c) {
    boolean changed = false;
    for (E element: c) {
      changed |= add(element);
      //changed &= add(element);
    }
    return changed;
  }

  /**
   * Adds an object to the Index. If it was already in the Index,
   * then nothing is done.  If it is not in the Index, then it is
   * added iff the Index hasn't been locked.
   *
   * @return true if the item was added to the index and false if the
   *         item was already in the index or if the index is locked
   */
  @Override
  public boolean add(E o) {
    Integer index = indexes.get(o);
    if (index == null && ! locked) {
      index = objects.size();
      objects.add(o);
      indexes.put(o, index);
      return true;
    }
    return false;
  }

  /**
   * Checks whether an Object already has an index in the Index
   * @param o the object to be queried.
   * @return true iff there is an index for the queried object.
   */
  @SuppressWarnings({"SuspiciousMethodCalls"})
  @Override
  public boolean contains(Object o) {
    return indexes.containsKey(o);
  }

  /**
   * Creates a new Index.
   */
  public HashIndex() {
    super();
  }

  /**
   * Creates a new Index.
   * @param capacity Initial capacity of Index.
   */
  public HashIndex(int capacity) {
    super();
    objects = new ArrayList<E>(capacity);
    indexes = new HashMap<E,Integer>(capacity);
  }

  /**
   * Creates a new Index and adds every member of c to it.
   * @param c A collection of objects
   */
  public HashIndex(Collection<? extends E> c) {
    this();
    addAll(c);
  }

  public void saveToFilename(String file) {
    BufferedWriter bw = null;
    try {
      bw = new BufferedWriter(new FileWriter(file));
      for (int i = 0, sz = size(); i < sz; i++) {
        bw.write(i + "=" + get(i) + "\n");
      }
      bw.close();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (bw != null) {
        try {
          bw.close();
        } catch (IOException ioe) {
          // give up
        }
      }
    }
  }

  public static Index<String> loadFromFilename(String file) {
    Index<String> index = new HashIndex<String>();
    BufferedReader br = null;
    try {
      br = new BufferedReader(new FileReader(file));
      for (String line; (line = br.readLine()) != null; ) {
        int start = line.indexOf('=');
        if (start == -1 || start == line.length() - 1) {
          continue;
        }
        index.add(line.substring(start + 1));
      }
      br.close();
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if (br != null) {
        try {
          br.close();
        } catch (IOException ioe) {
          // forget it
        }
      }
    }
    return index;
  }

  /**
   * This saves the contents of this index into string form, as part of a larger
   * text-serialization.  This is not intended to act as a standalone routine,
   * instead being called from the text-serialization routine for a component
   * that makes use of an Index, so everything can be stored in one file.  This is
   * similar to <code>saveToFileName</code>.
   * NOTE: adds an extra newline at the end of the sequence.
   * @param bw Writer to save to.
   * @throws IOException Exception thrown if cannot save.
   */
  public void saveToWriter(Writer bw) throws IOException {
    for (int i = 0, sz = size(); i < sz; i++) {
      bw.write(i + "=" + get(i) + "\n");
    }
    bw.write("\n");
  }

  /**
   * This is the analogue of <code>loadFromFilename</code>, and is intended to be included in a routine
   * that unpacks a text-serialized form of an object that incorporates an Index.
   * NOTE: presumes that the next readLine() will read in the first line of the
   * portion of the text file representing the saved Index.  Currently reads until it
   * encounters a blank line, consuming that line and returning the Index.
   * TODO: figure out how best to terminate: currently a blank line is considered to be a terminator.
   * @param br The Reader to read the index from
   * @return An Index read from a file
   */
  public static Index<String> loadFromReader(BufferedReader br) throws Exception {
    HashIndex<String> index = new HashIndex<String>();
    String line = br.readLine();
    // terminate if EOF reached, or if a blank line is encountered.
    while ((line != null) && (line.length() > 0)) {
      int start = line.indexOf('=');
      if (start == -1 || start == line.length() - 1) {
        continue;
      }
      index.add(line.substring(start + 1));
      line = br.readLine();
    }
    return index;
  }

  /** Returns a readable version of the Index contents
   *
   *  @return A String showing the full index contents
   */
  @Override
  public String toString() {
    return toString(Integer.MAX_VALUE);
  }

  
  public String toStringOneEntryPerLine() {
    return toStringOneEntryPerLine(Integer.MAX_VALUE);
  }
  

  /** Returns a readable version of at least part of the Index contents.
   *
   *  @param n Show the first <i>n</i> items in the Index
   *  @return A String rshowing some of the index contents
   */
  public String toString(int n) {
    StringBuilder buff = new StringBuilder("[");
    int sz = objects.size();
    if (n > sz) {
      n = sz;
    }
    int i;
    for (i = 0; i < n; i++) {
      E e = objects.get(i);
      buff.append(i).append("=").append(e);
      if (i < (sz-1)) buff.append(",");
    }
    if (i < sz) buff.append("...");
    buff.append("]");
    return buff.toString();
  }

  public String toStringOneEntryPerLine(int n) {
    StringBuilder buff = new StringBuilder();
    int sz = objects.size();
    if (n > sz) {
      n = sz;
    }
    int i;
    for (i = 0; i < n; i++) {
      E e = objects.get(i);
      buff.append(e);
      if (i < (sz-1)) buff.append("\n");
    }
    if (i < sz) buff.append("...");
    return buff.toString();
  }
  
  
  public static void main(String[] args) {
    List<String> list = new ArrayList<String>();
    list.add("A");
    list.add("B");
    list.add("A");
    list.add("C");
    HashIndex<String> index = new HashIndex<String>(list);
    System.out.println("Index size: " + index.size());
    System.out.println("Index has A? : " + index.contains("A"));
    System.out.println("Index of A: " + index.indexOf("A"));
    System.out.println("Index of B: " + index.indexOf("B"));
    System.out.println("Index of C: " + index.indexOf("C"));
    System.out.println("Object 0: " + index.get(0));
    index = index.unmodifiableView();
    System.out.println("Index size: " + index.size());
    System.out.println("Index has A? : " + index.contains("A"));
    System.out.println("Index of A: " + index.indexOf("A"));
    System.out.println("Index of B: " + index.indexOf("B"));
    System.out.println("Index of C: " + index.indexOf("C"));
    System.out.println("Object 0: " + index.get(0));

  }

  private static final long serialVersionUID = 5398562825928375260L;

  /**
   * Returns an iterator over the elements of the collection.
   * @return An iterator over the objects indexed
   */
  @Override
  public Iterator<E> iterator() {
    return objects.iterator();
  }

  /**
   * Removes an object from the index, if it exists (otherwise nothing
   * happens).  Note, the indices of other
   * elements will not be changed, so indices will no longer necessarily
   * be contiguous
   * @param o the object to remove
   * @return whether anything was removed
   */
  @Override
  public boolean remove(Object o) {
    Integer oldIndex = indexes.remove(o);
    if (oldIndex == null) {
      return false;
    }
    objects.set(oldIndex, null);
    return true;
  }

  /**
   * Returns an unmodifiable view of the Index.  It is just
   * a locked index that cannot be unlocked, so if you
   * try to add something, nothing will happen (it won't throw
   * an exception).  Trying to unlock it will throw an
   * UnsupportedOperationException.  If the
   * underlying Index is modified, the change will
   * "write-through" to the view.
   *
   * @return An unmodifiable view of the Index
   */
  public HashIndex<E> unmodifiableView() {
    HashIndex<E> newIndex = new HashIndex<E>() {
      @Override
      public void unlock() { throw new UnsupportedOperationException("This is an unmodifiable view!"); }
      private static final long serialVersionUID = 3415903369787491736L;
    };
    newIndex.objects = objects;
    newIndex.indexes = indexes;
    newIndex.lock();
    return newIndex;
  }
}
