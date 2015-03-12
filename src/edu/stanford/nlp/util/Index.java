package edu.stanford.nlp.util;

import java.io.Serializable;
import java.io.Writer;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * A collection that maps between a vocabulary of type E and a
 * continuous non-negative integer index series beginning (inclusively) at 0.
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
    public abstract int size();

    /**
     * Gets the object whose index is the integer argument.
     * @param i the integer index to be queried for the corresponding argument
     * @return the object whose index is the integer argument.
     */
    public abstract E get(int i);

    /**
     * Returns the integer index of the Object in the Index or -1 if the Object
     * is not already in the Index. This operation never changes the Index.
     *
     * @param o The Object whose index is desired.
     * @return The index of the Object argument. Returns -1 if the object is not in the index.
     */
    public abstract int indexOf(E o);

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
     */
    public abstract int indexOf(E o, boolean add);


    // mg2009. Methods below were temporarily added when IndexInterface was renamed
    // to Index. These methods are currently (2009-03-09) needed in order to have core classes
    // of JavaNLP (Dataset, LinearClassifier, etc.) use Index instead of HashIndex.
    // Possible JavaNLP task: delete some of these methods.

    /**
     * Returns a complete {@link List} of indexed objects, in the order of their indices.
     *
     * @return a complete {@link List} of indexed objects
     */
    public List<E> objectsList();

    /**
     * Looks up the objects corresponding to an array of indices, and returns them in a {@link Collection}.
     *
     * @param indices An array of indices
     * @return a {@link Collection} of the objects corresponding to the indices argument.
     */
    public Collection<E> objects(int[] indices);

    /**
     * Queries the Index for whether it's locked or not.
     * @return whether or not the Index is locked
     */
    public boolean isLocked();

    /**
     * Locks the Index.  A locked index cannot have new elements added to it (calls to {@link #add} will
     * leave the Index unchanged and return <code>false</code>).
     */
    public void lock();

    /**
     * Unlocks the Index.  A locked index cannot have new elements added to it (calls to {@link #add} will
     * leave the Index unchanged and return <code>false</code>).
     * */
    public void unlock();

    /**
     * Save the contents of this index into string form, as part of a larger
     * text-serialization.
     *
     * @param out Writer to save to.
     * @throws IOException Exception thrown if cannot save.
     */
    public void saveToWriter(Writer out) throws IOException;

    /**
     * Save the contents of this index into a file.
     *
     * @param s File name.
     */
    public void saveToFilename(String s);


    // Subset of the Collection interface.  These come from old uses of HashIndex. Particularly avoid using these.

    public boolean contains(Object o);   // cdm: keep this, it seems reasonable

    public <T> T[] toArray(T[] a);   // cdm: delete this (you can do objectsList().toArray() if you're desperate

    public boolean add(E e);  // cdm: Many, many uses; could be replaced with indexOf, but why bother?

    // public boolean remove(Object o);

    public boolean addAll(Collection<? extends E> c);  // okay to have.

    public void clear();  // cdm: barely used.

}
