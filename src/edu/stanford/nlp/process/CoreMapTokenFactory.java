package edu.stanford.nlp.process;

import java.util.HashMap;

import edu.stanford.nlp.ling.AnnotationLookup;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.AnnotationLookup.KeyLookup;
import edu.stanford.nlp.ling.CoreLabel.GenericAnnotation;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;


public class CoreMapTokenFactory implements CoreTokenFactory<CoreMap>{

  private boolean VERBOSE = true;
  public CoreMap makeToken(){
    CoreMap m = new ArrayCoreMap();
    return m;
  }
  
  @SuppressWarnings("unchecked")
  public static HashMap<String, Class<? extends GenericAnnotation>> genericKeys = new HashMap<String, Class<? extends GenericAnnotation>>();
  @SuppressWarnings("unchecked")
  public static HashMap<Class<? extends GenericAnnotation>, String> genericValues = new HashMap<Class<? extends GenericAnnotation>, String>();

  /**
   * makes a token from the keys and values provided; code copied from CoreLabel.initFromString
   */
  @SuppressWarnings("unchecked")
  public CoreMap makeToken(String[] keys, String[] values){
    
    CoreMap m = new ArrayCoreMap(keys.length);
    for (int i = 0; i < Math.min(keys.length, values.length); i++) {
      String key = keys[i];
      String value = values[i];
      KeyLookup lookup = AnnotationLookup.getCoreKey(key);

      //now work with the key we got above
      if (lookup == null) {
        if(genericKeys.containsKey(key)) {
          m.set(genericKeys.get(key), value);
        } else {
          GenericAnnotation<String> newKey = new GenericAnnotation<String>() {
            public Class<String> getType() { return String.class;} };
            m.set(newKey.getClass(), values[i]);
            genericKeys.put(keys[i], newKey.getClass());
            genericValues.put(newKey.getClass(), keys[i]);
        }
        // unknown key; ignore
        if (VERBOSE) {
          System.err.println("CORE: CoreLabel.fromAbstractMapLabel: " +
              "Unknown key "+key);
        }
      } else {
        try {
          Class<?> valueClass = AnnotationLookup.getValueType(lookup.coreKey);
          if(valueClass.equals(String.class)) {
            m.set((Class<? extends CoreAnnotation>)lookup.coreKey, values[i]);
          } else if(valueClass == Integer.class) {
            m.set((Class<? extends CoreAnnotation>)lookup.coreKey, Integer.parseInt(values[i]));
          } else if(valueClass == Double.class) {
            m.set((Class<? extends CoreAnnotation>)lookup.coreKey, Double.parseDouble(values[i]));
          } else if(valueClass == Long.class) {
            m.set((Class<? extends CoreAnnotation>)lookup.coreKey, Long.parseLong(values[i]));
          }
        } catch(Exception e) {
          e.printStackTrace();
          // unexpected value type
          System.err.println("CORE: copying from a CoreMap:"
              + "Bad type for " + key
              + ". Value was: " + value
              + "; expected "+AnnotationLookup.getValueType(lookup.coreKey));
        }
      }
    }
    return m;
  }

  public CoreMap makeToken(CoreMap tokenToBeCopied){
    CoreMap m = new ArrayCoreMap(tokenToBeCopied);
    return m;
  }
}
