package edu.stanford.nlp.util;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


/**
 * Class to gather unsafe operations into one place.
 * @author dlwh
 *
 */
public class ErasureUtils {
  private ErasureUtils(){}

  /**
   *  Casts an Object to a T
   * @param <T> The type to cast to
   * @param o The object to cast
   * @return {@code o}, cast to {@code T} without any runtime check
   */
  @SuppressWarnings("unchecked")
  public static <T> T uncheckedCast(Object o) {
    return (T)o;
  }

  /**
   * Does nothing, occasionally used to make Java happy that a value is used
   *
   * @param o The value, which is ignored
   */
  public static void noop(Object o){}


  /**
   * Makes an array based on klass, but casts it to be of type T[]. This is a very
   * unsafe operation and should be used carefully. Namely, you should ensure that
   * klass is a subtype of T, or that klass is a supertype of T *and* that the array
   * will not escape the generic constant *and* that klass is the same as the erasure
   * of T.
   * @param <T> The element type of the returned array
   * @param klass The component type of the array actually created
   * @param size The length of the array
   * @return A new array of {@code klass} of length {@code size}, cast to {@code T[]}
   */
  @SuppressWarnings("unchecked")
  public static <T> T[] mkTArray(Class<?> klass, int size) {
    return (T[])(Array.newInstance(klass, size));

  }
  
  /**
   * Makes a two-dimensional array based on klass, but casts it to be of type T[][].
   * The same cautions as for {@link #mkTArray} apply.
   *
   * @param <T> The element type of the returned array
   * @param klass The component type of the array actually created
   * @param dim The two dimensions of the array
   * @return A new array of {@code klass} with dimensions {@code dim}, cast to {@code T[][]}
   * @throws RuntimeException If {@code dim} does not have exactly two entries
   */
  @SuppressWarnings("unchecked")
  public static <T> T[][] mkT2DArray(Class<?> klass, int[] dim ) {
	  if(dim.length != 2)
		  throw new RuntimeException("dim should be an array of size 2.");
	  return (T[][])(Array.newInstance(klass, dim));
  }

  /**
   * Returns a new list containing the elements of the collection, sorted by their
   * natural ordering if possible.  If the elements are not mutually comparable or
   * include null, the copy is returned in the collection's iteration order.
   *
   * @param <T> The type of the elements
   * @param collection The elements to copy and sort
   * @return A new list of the elements, sorted if possible
   */
  @SuppressWarnings("unchecked")
  public static <T> List<T> sortedIfPossible(Collection<T> collection) {
    List<T> result = new ArrayList<>(collection);
    try {
      Collections.sort((List)result);
    } catch (ClassCastException e) {
      // unable to sort, just return the copy
    } catch (NullPointerException npe) {
      // this happens if there are null elements in the collection; just return the copy
    }
    return result;
  }
}
