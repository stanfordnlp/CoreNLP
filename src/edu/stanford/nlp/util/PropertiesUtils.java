package edu.stanford.nlp.util;

import java.io.PrintStream;
import java.lang.reflect.Type;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;

public class PropertiesUtils {

  private PropertiesUtils() {}

  /**
   * Returns true iff the given Properties contains a property with the given
   * key (name), and its value is not "false" or "no" or "off".
   *
   * @param props Properties object
   * @param key The key to test
   * @return true iff the given Properties contains a property with the given
   * key (name), and its value is not "false" or "no" or "off".
   */
  public static boolean hasProperty(Properties props, String key) {
    String value = props.getProperty(key);
    if (value == null) {
      return false;
    }
    value = value.toLowerCase();
    return ! (value.equals("false") || value.equals("no") || value.equals("off"));
  }


  // printing -------------------------------------------------------------------

  public static void printProperties(String message, Properties properties,
                                     PrintStream stream) {
    if (message != null) {
      stream.println(message);
    }
    if (properties.isEmpty()) {
      stream.println("  [empty]");
    } else {
      List<Map.Entry<String, String>> entries = getSortedEntries(properties);
      for (Map.Entry<String, String> entry : entries) {
        if ( ! "".equals(entry.getKey())) {
          stream.format("  %-30s = %s%n", entry.getKey(), entry.getValue());
        }
      }
    }
    stream.println();
  }

  public static void printProperties(String message, Properties properties) {
    printProperties(message, properties, System.out);
  }
  
  /**
   * Tired of Properties not behaving like Map<String,String>s?  This method will solve that problem for you.
   */
  public static Map<String, String> asMap(Properties properties) {
    Map<String, String> map = Generics.newHashMap();
    for (Entry<Object, Object> entry : properties.entrySet()) {
      map.put((String)entry.getKey(), (String)entry.getValue());
    }
    return map;
  }
  
  public static List<Map.Entry<String, String>> getSortedEntries(Properties properties) {
    return Maps.sortedEntries(asMap(properties));
  }

  /**
   * Checks to make sure that all properties specified in <code>properties</code>
   * are known to the program by checking that each simply overrides
   * a default value
   * @param properties Current properties
   * @param defaults Default properties which lists all known keys
   */
  @SuppressWarnings("unchecked")
  public static void checkProperties(Properties properties, Properties defaults) {
    Set<String> names = Generics.newHashSet();
    for (Enumeration<String> e = (Enumeration<String>) properties.propertyNames();
         e.hasMoreElements(); ) {
      names.add(e.nextElement());
    }
    for (Enumeration<String> e = (Enumeration<String>) defaults.propertyNames();
         e.hasMoreElements(); ) {
      names.remove(e.nextElement());
    }
    if (!names.isEmpty()) {
      if (names.size() == 1) {
        throw new IllegalArgumentException("Unknown property: " + names.iterator().next());
      } else {
        throw new IllegalArgumentException("Unknown properties: " + names);
      }
    }
  }


  /**
   * Get the value of a property and automatically cast it to a specific type.
   * This differs from the original Properties.getProperty() method in that you
   * need to specify the desired type (e.g. Double.class) and the default value
   * is an object of that type, i.e. a double 0.0 instead of the String "0.0".
   */
  @SuppressWarnings("unchecked")
  public static <E> E get(Properties props, String key, E defaultValue, Type type) {
    String value = props.getProperty(key);
    if (value == null) {
      return defaultValue;
    } else {
      return (E) MetaClass.cast(value, type);
    }
  }
  
  /**
   * Load an integer property.  If the key is not present, returns 0.
   */
  public static int getInt(Properties props, String key) {
    return getInt(props, key, 0);
  }
  
  /**
   * Load an integer property.  If the key is not present, returns defaultValue.
   */
  public static int getInt(Properties props, String key, int defaultValue) {
    String value = props.getProperty(key);
    if (value != null) {
      return Integer.parseInt(value);
    } else {
      return defaultValue;
    }
  }
    
  /**
   * Load an integer property as a long.  
   * If the key is not present, returns defaultValue.
   */
  public static long getLong(Properties props, String key, long defaultValue) {
    String value = props.getProperty(key);
    if (value != null) {
      return Long.parseLong(value);
    } else {
      return defaultValue;
    }
  }

  /**
   * Load a double property.  If the key is not present, returns 0.0.
   */
  public static double getDouble(Properties props, String key) {
    return getDouble(props, key, 0.0);
  }
  
  /**
   * Load a double property.  If the key is not present, returns defaultValue.
   */
  public static double getDouble(Properties props, String key, double defaultValue) {
    String value = props.getProperty(key);
    if (value != null) {
      return Double.parseDouble(value);
    } else {
      return defaultValue;
    }
  }
 
  /**
   * Load a boolean property.  If the key is not present, returns false.
   */
  public static boolean getBool(Properties props, String key) {
    return getBool(props, key, false);
  }  
  
  /**
   * Load a boolean property.  If the key is not present, returns defaultValue.
   */
  public static boolean getBool(Properties props, String key, 
                                boolean defaultValue) {
    String value = props.getProperty(key);
    if (value != null) {
      return Boolean.parseBoolean(value);
    } else {
      return defaultValue;
    }
  }  
  
  /**
   * Loads a comma-separated list of integers from Properties.  The list cannot include any whitespace.
   */
  public static int[] getIntArray(Properties props, String key) {
    Integer[] result = MetaClass.cast(props.getProperty(key), Integer [].class);
    return ArrayUtils.toPrimitive(result);
  }

  /**
   * Loads a comma-separated list of doubles from Properties.  The list cannot include any whitespace.
   */
  public static double[] getDoubleArray(Properties props, String key) {
    Double[] result = MetaClass.cast(props.getProperty(key), Double [].class);
    return ArrayUtils.toPrimitive(result);
  }
  
  /**
   * Loads a comma-separated list of strings from Properties.  Commas may be quoted if needed, e.g.:
   *    property1 = value1,value2,"a quoted value",'another quoted value'
   *    
   * getStringArray(props, "property1") should return the same thing as
   *    new String[] { "value1", "value2", "a quoted value", "another quoted value" };
   */
  public static String[] getStringArray(Properties props, String key) {
    String[] results = MetaClass.cast(props.getProperty(key), String [].class);
    if (results == null) {
      results =new String[] {};
    }
    
    return results;
  }
}
