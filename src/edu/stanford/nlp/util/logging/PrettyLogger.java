
package edu.stanford.nlp.util.logging;

import java.util.Collections;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.util.logging.Redwood.RedwoodChannels;
import edu.stanford.nlp.util.Generics;

/**
 * Primarily for debugging, PrettyLogger helps you dump various collection
 * objects in a reasonably structured way via Redwood logging. It has support
 * for many built in collection types (Mapping, Iterable, arrays, Properties) as
 * well as anything that implements PrettyLoggable.
 *  
 * @see PrettyLoggable
 * @author David McClosky
 * @author Gabor Angeli (+ primitive arrays; dictionaries)
 */

// TODO Should have an optional maximum depth, perhaps

public class PrettyLogger {
  
  private static final RedwoodChannels DEFAULT_CHANNELS = new RedwoodChannels(Redwood.FORCE);

  /**
   * Static class.
   */
  private PrettyLogger() {}

  /*
   * Main entry methods and utilities
   */

  /**
   * Pretty log an object. It will be logged to the default channel. Its class
   * name will be used as a description.
   * 
   * @param obj
   *          the object to be pretty logged
   */
  public static void log(Object obj) {
    log(obj.getClass().getSimpleName(), obj);
  }

  /**
   * Pretty log an object along with its description. It will be logged to the
   * default channel.
   *
   * @param description
   *          denote the object in the logs (via a track name, etc.).
   * @param obj
   *          the object to be pretty logged
   */
  public static void log(String description, Object obj) {
    log(DEFAULT_CHANNELS, description, obj);
  }

  /**
   * Pretty log an object. Its class name will be used as a description.
   *
   * @param channels
   *          the channels to pretty log to
   * @param obj
   *          the object to be pretty logged
   */
  public static void log(RedwoodChannels channels, Object obj) {
    log(channels, obj.getClass().getSimpleName(), obj);
  }

  /**
   * Pretty log an object.
   *
   * @param channels
   *          the channels to pretty log to
   * @param description
   *          denote the object in the logs (via a track name, etc.).
   * @param obj
   *          the object to be pretty logged
   */
  // TODO perhaps some reflection magic can simplify this process?
  @SuppressWarnings("unchecked")
  public static <T> void log(RedwoodChannels channels, String description, Object obj) {
    if (obj instanceof Map) {
      log(channels, description, (Map)obj);
    } else if (obj instanceof PrettyLoggable) {
      ((PrettyLoggable) obj).prettyLog(channels, description);
    } else if (obj instanceof Dictionary) {
      log(channels, description, (Dictionary)obj);
    } else if (obj instanceof Iterable) {
      log(channels, description, (Iterable)obj);
    } else if (obj.getClass().isArray()) {
      Object[] arrayCopy; // the array to log
      if(obj.getClass().getComponentType().isPrimitive()){
        //(case: a primitive array)
        Class componentClass = obj.getClass().getComponentType();
        if(boolean.class.isAssignableFrom(componentClass)){
          arrayCopy = new Object[((boolean[]) obj).length];
          for(int i=0; i<arrayCopy.length; i++){ arrayCopy[i] = ((boolean[]) obj)[i]; }
        } else if(byte.class.isAssignableFrom(componentClass)){
          arrayCopy = new Object[((byte[]) obj).length];
          for(int i=0; i<arrayCopy.length; i++){ arrayCopy[i] = ((byte[]) obj)[i]; }
        } else if(char.class.isAssignableFrom(componentClass)){
          arrayCopy = new Object[((char[]) obj).length];
          for(int i=0; i<arrayCopy.length; i++){ arrayCopy[i] = ((char[]) obj)[i]; }
        } else if(short.class.isAssignableFrom(componentClass)){
          arrayCopy = new Object[((short[]) obj).length];
          for(int i=0; i<arrayCopy.length; i++){ arrayCopy[i] = ((short[]) obj)[i]; }
        } else if(int.class.isAssignableFrom(componentClass)){
          arrayCopy = new Object[((int[]) obj).length];
          for(int i=0; i<arrayCopy.length; i++){ arrayCopy[i] = ((int[]) obj)[i]; }
        } else if(long.class.isAssignableFrom(componentClass)){
          arrayCopy = new Object[((long[]) obj).length];
          for(int i=0; i<arrayCopy.length; i++){ arrayCopy[i] = ((long[]) obj)[i]; }
        } else if(float.class.isAssignableFrom(componentClass)){
          arrayCopy = new Object[((float[]) obj).length];
          for(int i=0; i<arrayCopy.length; i++){ arrayCopy[i] = ((float[]) obj)[i]; }
        } else if(double.class.isAssignableFrom(componentClass)){
          arrayCopy = new Object[((double[]) obj).length];
          for(int i=0; i<arrayCopy.length; i++){ arrayCopy[i] = ((double[]) obj)[i]; }
        } else {
          throw new IllegalStateException("I forgot about the primitive class: " + componentClass);
        }
      } else {
        //(case: a regular array)
        arrayCopy = (T[]) obj;
      }
      log(channels, description, arrayCopy);
    } else {
      if (!description.equals("")) {
        description += ": ";
      }
      channels.log(description + obj);
    }
  }

  /**
   * Returns true if an object has special logic for pretty logging (e.g.
   * implements PrettyLoggable). If so, we ask it to pretty log itself. If not,
   * we can safely use its toString() in logs.
   * @param obj The object to test
   * @return true if the object is dispatchable
   */
  @SuppressWarnings("unchecked")
  public static boolean dispatchable(Object obj) {
    if (obj == null) {
      return false;
    }
    return obj instanceof PrettyLoggable ||
      obj instanceof Map ||
      obj instanceof Dictionary ||
      obj instanceof Iterable ||
      obj.getClass().isArray();
  }

  /*
   * Mappings
   */

  private static <K, V> void log(RedwoodChannels channels, String description, Map<K, V> mapping) {
    Redwood.startTrack(description);
    if (mapping == null) {
      channels.log("(mapping is null)");
    } else if (mapping.size() == 0) {
      channels.log("(empty)");
    } else {
      // convert keys to sorted list, if possible
      List<K> keys = new LinkedList<K>();
      for (K key : mapping.keySet()) {
        keys.add(key);
      }
      Collections.sort(keys, new Comparator<K>() {
        @SuppressWarnings("unchecked")
        public int compare(K a, K b) {
          if (a != null && Comparable.class.isAssignableFrom(a.getClass())) {
            return ((Comparable) a).compareTo(b);
          } else {
            return 0;
          }
        }
      });
      // log key/value pairs
      int entryCounter = 0;
      for (K key : keys) {
        V value = mapping.get(key);
        if (!dispatchable(key) && dispatchable(value)) {
          log(channels, key.toString(), value);
        } else if (dispatchable(key) || dispatchable(value)) {
          Redwood.startTrack("Entry " + entryCounter);
          if (dispatchable(key)) {
            log(channels, "Key", key);
          } else {
            channels.logf("Key %s", key);
          }

          if (dispatchable(value)) {
            log(channels, "Value", value);
          } else {
            channels.logf("Value %s", value);
          }
          Redwood.endTrack("Entry " + entryCounter);
        } else {
          channels.logf("%s = %s", key, value);
        }
        entryCounter++;
      }
    }
    Redwood.endTrack(description);
  }

  /*
   * Dictionaries (notably, Properties) -- convert them to Maps and dispatch
   */

  private static <K,V> void log(RedwoodChannels channels, String description, Dictionary<K,V> dict) {
    //(a real data structure)
    Map<K, V> map = Generics.newHashMap();
    //(copy to map)
    Enumeration<K> keys = dict.keys();
    while(keys.hasMoreElements()){
      K key = keys.nextElement();
      V value = dict.get(key);
      map.put(key,value);
    }
    //(log like normal)
    log(channels, description, map);
  }

  /*
   * Iterables (includes Collection, List, Set, etc.)
   */

  private static <T> void log(RedwoodChannels channels, String description, Iterable<T> iterable) {
    Redwood.startTrack(description);
    if (iterable == null) {
      channels.log("(iterable is null)");
    } else {
      int index = 0;
      for (T item : iterable) {
        if (dispatchable(item) && item != iterable) {
          log(channels, "Index " + index, item);
        } else {
          channels.logf("Index %d: %s", index, item == iterable ? "...<infinite loop>" : item);
        }
        index++;
      }

      if (index == 0) {
        channels.log("(empty)");
      }
    }
    Redwood.endTrack(description);
  }

  /*
   * Arrays
   */

  private static <T> void log(RedwoodChannels channels, String description, T[] array) {
    Redwood.startTrack(description);
    if (array == null) {
      channels.log("(array is null)");
    } else if (array.length == 0) {
      channels.log("(empty)");
    } else {
      int index = 0;
      for (T item : array) {
        if (dispatchable(item)) {
          log(channels, "Index " + index, item);
        } else {
          channels.logf("Index %d: %s", index, item);
        }
        index++;
      }
    }
    Redwood.endTrack(description);
  }
}
