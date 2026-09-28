package edu.stanford.nlp.util;

/**
 * The goal of this class is to make it easier to load stuff by
 * reflection.  You can hide all of the ugly exception catching, etc
 * by using the static methods in this class.
 *
 * @author John Bauer
 * @author Gabor Angeli (changed)
 */

public class ReflectionLoading {

  // static methods only
  private ReflectionLoading() {}

  /**
   * Create an object of type T by calling the class constructor with the given arguments.
   * You can use this as follows:
   * <br>
   *  {@code String s = ReflectionLoading.loadByReflection("java.lang.String", "foo"); }
   * <br>
   *  {@code String s = ReflectionLoading.loadByReflection("java.lang.String"); }
   * <br>
   * Note that this uses generics for convenience, but this does
   * nothing for compile-time error checking.  You can do:
   * <br>
   *  {@code Integer i = ReflectionLoading.loadByReflection("java.lang.String"); }
   * <br>
   * and it will compile just fine, but will result in a ClassCastException.
   *
   * @param <T> The type to return the new object as
   * @param className The fully qualified name of the class to instantiate
   * @param arguments The arguments to pass to the constructor
   * @return The newly created object
   * @throws ReflectionLoadingException if the object cannot be created for any reason
   */
  @SuppressWarnings("unchecked")
  public static <T> T loadByReflection(String className,
                                       Object ... arguments) {
    try{
      return (T) new MetaClass(className).createInstance(arguments);
    } catch (Exception e) {
      throw new ReflectionLoadingException("Error creating " + className, e);
    }
  }

  /**
   * This class encapsulates all of the exceptions that can be thrown
   * when loading something by reflection.
   */
  public static class ReflectionLoadingException extends RuntimeException {

    private static final long serialVersionUID = -3324911744277952585L;


    /**
     * Creates an exception wrapping the reason reflection loading failed.
     *
     * @param message The error message
     * @param reason The underlying exception
     */
    public ReflectionLoadingException(String message, Throwable reason) {
      super(message, reason);
    }

  }

}
