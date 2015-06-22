package edu.stanford.nlp.semgraph.semgrex;

import edu.stanford.nlp.ling.AnnotationLookup;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by sonalg on 11/3/14.
 */
public class Env implements Serializable {
  /**
   * Mapping of variable names to their values
   */
  Map<String, Object> variables = new HashMap<String, Object>();

  public Env() {}

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
    AnnotationLookup.KeyLookup lookup = AnnotationLookup.getCoreKey(name);
    if (lookup != null) {
      return lookup.coreKey;
    } else {
      try {
        Class clazz = Class.forName(name);
        return clazz;
      } catch (ClassNotFoundException ex) {}
      return null;
    }
  }



}
