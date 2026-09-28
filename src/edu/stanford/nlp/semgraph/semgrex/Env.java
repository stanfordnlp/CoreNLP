package edu.stanford.nlp.semgraph.semgrex;

import edu.stanford.nlp.ling.AnnotationLookup;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Variable bindings available to a SemgrexPattern.  When a node
 * attribute is matched, the attribute name is first looked up here to
 * find the annotation class it refers to.
 *
 * @author sonalg
 * @version 11/3/14.
 */
public class Env implements Serializable {

  private static final long serialVersionUID = -4168610545399833956L;

  /**
   * Mapping of variable names to their values.
   */
  private final Map<String, Object> variables;

  /** Creates an Env with no variables bound. */
  public Env() {
    variables = new HashMap<>();
  }

  /**
   * Creates an Env which uses the given map (not a copy) for its bindings.
   *
   * @param variables The map of variable names to values
   */
  public Env(Map<String, Object> variables) {
    this.variables = variables;
  }

  /**
   * Binds a variable to a value.
   *
   * @param name The variable name
   * @param obj The value; if null, the variable is unbound instead
   */
  public void bind(String name, Object obj) {
    if (obj != null) {
      variables.put(name, obj);
    } else {
      variables.remove(name);
    }
  }

  /**
   * Removes the binding for a variable, if there is one.
   *
   * @param name The variable name
   */
  public void unbind(String name) {
    bind(name, null);
  }

  /**
   * Returns the value bound to a variable.
   *
   * @param name The variable name
   * @return The value, or null if the variable is not bound
   */
  public Object get(String name){
    return variables.get(name);
  }

  /**
   * Finds the annotation class for an attribute name.  In order, this
   * tries a {@code Class} bound to {@code name} in {@code env}, then
   * {@link AnnotationLookup#toCoreKey}, then {@code name} as a fully
   * qualified class name.
   *
   * @param env The Env to look in first; may be null
   * @param name The attribute name
   * @return The annotation class, or null if none is found
   */
  public static Class lookupAnnotationKey(Env env, String name){
    if (env != null) {
      Object obj = env.get(name);
      if (obj != null) {
        if (obj instanceof Class) {
          return (Class) obj;
        }
//        else if (obj instanceof Value) {
//          obj = ((Value) obj).get();
//          if (obj instanceof Class) {
//            return (Class) obj;
//          }
//        }
      }
    }
    Class coreKeyClass = AnnotationLookup.toCoreKey(name);
    if (coreKeyClass != null) {
      return coreKeyClass;
    } else {
      try {
        Class clazz = Class.forName(name);
        return clazz;
      } catch (ClassNotFoundException ex) {
        return null;
      }
    }
  }

}
