package edu.stanford.nlp.util;

import java.io.Serializable;
import java.io.Writer;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * A collection that maps between a vocabulary of type E and a
 * continuous non-negative integer index series beginning (inclusively) at 0.
 * <p>
 * Often one uses a List to associate a unique index with each Object
 * (e.g. controlled vocabulary, feature map, etc.). Index offers constant-time
 * performance for both {@code index -> Object} ({@link #get(int)}) and {@code
 * Object -> index} ({@link #indexOf(Object)}) as well as for {@link #contains(Object)}.
 * Otherwise it behaves like a normal list. Index also
 * supports {@link #lock()} and {@link #unlock()} to ensure that it's
 * only modified when desired.
 *
 * @author Daniel Cer
 *
 * @param <E> The type of objects in the Index
 */
public interface Index<E> extends Iterable<E>, Serializable {

  /**
   * Returns the number of indexed objects.
   * @return the number of indexed objects.
   */
  int size();

  /**
   * Gets the object whose index is the integer argument.
   * @param i the integer index to be queried for the corresponding argument
   * @return the object whose index is the integer argument.
   */
  E get(int i);

  /**
   * Returns the integer index of the Object in the Index or -1 if the Object
   * is not already in the Index. This operation never changes the Index.
   *
   * @param o The Object whose index is desired.
   * @return The index of the Object argument. Returns -1 if the object is not in the index.
   */
  int indexOf(E o);

  /**
   * Takes an Object and returns the integer index of the Object.
   * If the object was already in the index, it returns its existing
   * index, otherwise it adds it to the index first.
   * Except if the index is locked, and then it returns -1 if the
   * object is not already in the index.
   *
   * @param o the Object whose index is desired.
   * @return the index of the Object argument. Normally a non-negative integer.
   *     Returns -1 if the object is not in the index and the Index is locked.
   */
  int addToIndex(E o);

  /**
   * Takes an Object and returns the integer index of the Object,
   * perhaps adding it to the index first.
   * Returns -1 if the Object is not in the Index.
   * (Note: indexOf(x, true) is the direct replacement for the number(x)
   * method in the old Numberer class.)
   *
   * @param o the Object whose index is desired.
   * @param add Whether it is okay to add new items to the index
   * @return the index of the Object argument.  Returns -1 if the object is not in the index
   *     or if the Index is locked.
   * @deprecated You should use either the addToIndex(E) or indexOf(E) methods instead
   */
  @Deprecated
  int indexOf(E o, boolean add);


  // mg2009. Methods below were temporarily added when IndexInterface was renamed
  // to Index. These methods are currently (2009-03-09) needed in order to have core classes
  // of JavaNLP (Dataset, LinearClassifier, etc.) use Index instead of HashIndex.
  // Possible JavaNLP task: delete some of these methods.

  /**
   * Returns a complete {@link List} of indexed objects, in the order of their indices.
   *
   * @return a complete {@link List} of indexed objects
   */
  List<E> objectsList();

  /**
   * Looks up the objects corresponding to an array of indices, and returns them in a {@link Collection}.
   *
   * @param indices An array of indices
   * @return a {@link Collection} of the objects corresponding to the indices argument.
   */
  Collection<E> objects(int[] indices);

  /**
   * Queries the Index for whether it's locked or not.
   * @return whether or not the Index is locked
   */
  boolean isLocked();

  /**
   * Locks the Index.  A locked index cannot have new elements added to it (calls to {@link #add} will
   * leave the Index unchanged and return {@code false}).
   */
  void lock();

  /**
   * Unlocks the Index.  A locked index cannot have new elements added to it (calls to {@link #add} will
   * leave the Index unchanged and return {@code false}).
   */
  void unlock();

  /**
   * Save the contents of this index into string form, as part of a larger
   * text-serialization.
   *
   * @param out Writer to save to.
   * @throws IOException Exception thrown if cannot save.
   */
  void saveToWriter(Writer out) throws IOException;

  /**
   * Save the contents of this index into a file.
   *
   * @param s File name.
   */
  void saveToFilename(String s);


  // Subset of the Collection interface.  These come from old uses of HashIndex. Avoid using these.

  boolean contains(Object o);   // cdm: keep this, it seems reasonable

  boolean add(E e);  // cdm: Many, many uses; could be replaced with indexOf, but why bother?

  boolean addAll(Collection<? extends E> c);  // okay to have.

  void clear();  // cdm: barely used.

}
