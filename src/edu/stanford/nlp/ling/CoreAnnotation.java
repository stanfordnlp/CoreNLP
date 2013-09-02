package edu.stanford.nlp.ling;

import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.TypesafeMap;

/**
 * The base class for any annotation that can be marked on a {@link CoreMap},
 * parameterized by the type of the value associated with the annotation.
 * Subclasses of this class are the keys in the {@link CoreMap}, so they are
 * instantiated only by utility methods in {@link CoreAnnotations}.
 * 
 * @author dramage
 * @author rafferty
 */
public interface CoreAnnotation<V>
  extends TypesafeMap.Key<CoreMap, V> {

  /**
   * Returns the type associated with this annotation.  This method must
   * return the same class type as its value type parameter.  It feels like
   * one should be able to get away without this method, but because Java
   * erases the generic type signature, that info disappears at runtime.
   */
  public Class<V> getType();
}
