package edu.stanford.nlp.util;

import java.io.*;
import java.util.*;
import java.util.concurrent.Semaphore;

/**
 * Implements an Index that supports constant-time lookup in
 * both directions (via {@code get(int)} and {@code indexOf(E)}.
 * The {@code indexOf(E)} method compares objects by
 * {@code equals()}, as other Collections.
 * <p/>
 * The typical usage would be:
 * <p>{@code Index<String> index = new Index<String>(collection);}
 * <p> followed by
 * <p>{@code int i = index.indexOf(str);}
 * <p> or
 * <p>{@code String s = index.get(i);}
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
  private final ArrayList<E> objects;
  private final Map<E,Integer> indexes;
  private boolean locked; // = false; // Mutable

  private static final long serialVersionUID = 5398562825928375260L;

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
   * @param elements The list of items
   * @return An array of indices
   */
  public int[] indices(Collection<E> elements) {
    int[] indices = new int[elements.size()];
    int i = 0;
    for (E elem : elements) {
      indices[i++] = indexOf(elem);
    }
    return indices;
  }

  /**
   * Looks up the objects corresponding to an array of indices, and returns them in a {@link Collection}.
   * This collection is not a copy, but accesses the data structures of the Index.
   *
   * @param indices An array of indices
   * @return a {@link Collection} of the objects corresponding to the indices argument.
   */
  @Override
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
   *
   * @return the number of indexed objects.
   */
  @Override
  public int size() {
    return objects.size();
  }

  /**
   * Gets the object whose index is the integer argument.
   *
   * @param i the integer index to be queried for the corresponding argument
   * @return the object whose index is the integer argument.
   */
  @Override
  public E get(int i) {
    if (i < 0 || i >= objects.size())
      throw new ArrayIndexOutOfBoundsException("Index " + i +
                                               " outside the bounds [0," +
                                               size() + ")");
    return objects.get(i);
  }

  /**
   * Returns a complete {@link List} of indexed objects, in the order of their indices.  <b>DANGER!</b>
   * The current implementation returns the actual index list, not a defensive copy.  Messing with this List
   * can seriously screw up the state of the Index.  (perhaps this method needs to be eliminated? I don't think it's
   * ever used in ways that we couldn't use the Index itself for directly.  --Roger, 12/29/04)
   *
   * @return a complete {@link List} of indexed objects
   */
  @Override
  public List<E> objectsList() {
    return objects;
  }

  /**
   * Queries the Index for whether it's locked or not.
   * @return whether or not the Index is locked
   */
  @Override
  public boolean isLocked() {
    return locked;
  }

  /** Locks the Index.  A locked index cannot have new elements added to it (calls to {@link #add} will
   * leave the Index unchanged and return {@code false}).*/
  @Override
  public void lock() {
    locked = true;
  }

  /** Unlocks the Index.  A locked index cannot have new elements added to it (calls to {@link #add} will
   * leave the Index unchanged and return {@code false}).*/
  @Override
  public void unlock() {
    locked = false;
  }

  /**
   * Returns the integer index of the Object in the Index or -1 if the Object is not already in the Index.
   * @param o the Object whose index is desired.
   * @return the index of the Object argument.  Returns -1 if the object is not in the index.
   */
  @Override
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
   *  usage. (CDM: Is this better than using a synchronized block?)
   *
   * @param o the Object whose index is desired.
   * @param add Whether it is okay to add new items to the index
   * @return The index of the Object argument.  Returns -1 if the object is not in the index.
   */
  @Override
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

  private final Semaphore semaphore = new Semaphore(1);

  // TODO: delete this when breaking serialization because we can leach off of AbstractCollection
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
    objects = new ArrayList<E>();
    indexes = Generics.newHashMap();
  }

  /**
   * Creates a new Index.
   * @param capacity Initial capacity of Index.
   */
  public HashIndex(int capacity) {
    super();
    objects = new ArrayList<E>(capacity);
    indexes = Generics.newHashMap(capacity);
  }

  /** Private constructor for supporting the unmodifiable view. */
  private HashIndex(ArrayList<E> objects, Map<E,Integer> indexes) {
    super();
    this.objects = objects;
    this.indexes = indexes;
  }


  /**
   * Creates a new Index and adds every member of c to it.
   * @param c A collection of objects
   */
  public HashIndex(Collection<? extends E> c) {
    this();
    addAll(c);
  }

  public HashIndex(Index<? extends E> index) {
    this();
    // TODO: this assumes that no index supports deletion
    addAll(index.objectsList());
  }

  @Override
  public void saveToFilename(String file) {
    BufferedWriter bw = null;
    try {
      bw = new BufferedWriter(new FileWriter(file));
      for (int i = 0, sz = size(); i < sz; i++) {
        bw.write(i + "=" + get(i) + '\n');
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

  /**
   * This assumes each line is of the form (number=value) and it adds each value in order of the lines in the file
   * @param file Which file to load
   * @return An index built out of the lines in the file
   */
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
   * similar to {@code saveToFileName}.
   * @param bw Writer to save to.
   * @throws IOException Exception thrown if cannot save.
   */
  @Override
  public void saveToWriter(Writer bw) throws IOException {
    for (int i = 0, sz = size(); i < sz; i++) {
      bw.write(i + "=" + get(i) + '\n');
    }
  }

  /**
   * This is the analogue of {@code loadFromFilename}, and is intended to be included in a routine
   * that unpacks a text-serialized form of an object that incorporates an Index.
   * NOTE: presumes that the next readLine() will read in the first line of the
   * portion of the text file representing the saved Index.  Currently reads until it
   * encounters a blank line, consuming that line and returning the Index.
   * TODO: figure out how best to terminate: currently a blank line is considered to be a terminator.
   * @param br The Reader to read the index from
   * @return An Index read from a file
   */
  public static Index<String> loadFromReader(BufferedReader br) throws IOException {
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
   *  @return A String showing some of the index contents
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
      buff.append(i).append('=').append(e);
      if (i < (sz-1)) buff.append(',');
    }
    if (i < sz) buff.append("...");
    buff.append(']');
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
      if (i < (sz-1)) buff.append('\n');
    }
    if (i < sz) buff.append("...");
    return buff.toString();
  }
  /**
   * Returns an iterator over the elements of the collection.
   * @return An iterator over the objects indexed
   */
  @Override
  public Iterator<E> iterator() {
    return objects.iterator();
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
    HashIndex<E> newIndex = new HashIndex<E>(objects, indexes) {
      @Override
      public void unlock() { throw new UnsupportedOperationException("This is an unmodifiable view!"); }
      private static final long serialVersionUID = 3415903369787491736L;
    };
    newIndex.lock();
    return newIndex;
  }

  @Override
  public boolean remove(Object o){
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeAll(Collection<?> e){
    throw new UnsupportedOperationException();
  }

  /**
   * This assumes each line is one value and creates index by adding values in the order of the lines in the file
   * @param file Which file to load
   * @return An index built out of the lines in the file
   */
  public static Index<String> loadFromFileWithList(String file) {
    Index<String> index = new HashIndex<String>();
    BufferedReader br = null;
    try {
      br = new BufferedReader(new FileReader(file));
      for (String line; (line = br.readLine()) != null; ) {
        index.add(line.trim());
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    // TODO: why not allow equality to non-HashIndex indices?
    if (!(o instanceof HashIndex)) return false;
    HashIndex hashIndex = (HashIndex) o;
    return indexes.equals(hashIndex.indexes) && objects.equals(hashIndex.objects);

  }

  @Override
  public int hashCode() {
    int result = objects.hashCode();
    result = 31 * result + indexes.hashCode();
    return result;
  }

}
