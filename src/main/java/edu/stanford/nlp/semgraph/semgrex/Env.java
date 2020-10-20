package edu.stanford.nlp.semgraph.semgrex;

import edu.stanford.nlp.ling.AnnotationLookup;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author sonalg
 * @version 11/3/14.
 */
public class Env implements Serializable {

  private static final long serialVersionUID = -4168610545399833956L;

  /**
   * Mapping of variable names to their values.
   */
  private final Map<String, Object> variables;

  public Env() {
    variables = new HashMap<>();
  }

  public Env(Map<String, Object> variables) {
    this.variables = variables;
  }

  public void bind(String name, Object obj) {
    if (obj != null) {
      variables.put(name, obj);
    } else {
      variables.remove(name);
    }
  }

  public void unbind(String name) {
    bind(name, null);
  }

  public Object get(String name){
    return variables.get(name);
  }

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
