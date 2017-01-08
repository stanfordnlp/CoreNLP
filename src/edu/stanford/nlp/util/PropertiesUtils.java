package edu.stanford.nlp.util;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;

/** Utilities methods for standard (but woeful) Java Properties objects.
 *
 *  @author Sarah Spikes
 *  @author David McClosky
 */
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

  /** Create a Properties object from the passed in String arguments.
   *  The odd numbered arguments are the names of keys, and the even
   *  numbered arguments are the value of the preceding key
   *
   *  @param args An even-length list of alternately key and value
   */
  public static Properties asProperties(String... args) {
    if (args.length % 2 != 0) {
      throw new IllegalArgumentException("Need an even number of arguments but there were " + args.length);
    }
    Properties properties = new Properties();
    for (int i = 0; i < args.length; i += 2) {
      properties.setProperty(args[i], args[i + 1]);
    }
    return properties;
  }

  /** Convert from Properties to String. */
  public static String asString(Properties props) {
    try {
      StringWriter sw = new StringWriter();
      props.store(sw, null);
      return sw.toString();
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  /** Convert from String to Properties. */
  public static Properties fromString(String str) {
    try {
      StringReader sr = new StringReader(str);
      Properties props = new Properties();
      props.load(sr);
      return props;
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
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
   * Tired of Properties not behaving like {@code Map<String,String>}s?  This method will solve that problem for you.
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
   * Checks to make sure that all properties specified in {@code properties}
   * are known to the program by checking that each simply overrides
   * a default value.
   *
   * @param properties Current properties
   * @param defaults Default properties which lists all known keys
   */
  @SuppressWarnings("unchecked")
  public static void checkProperties(Properties properties, Properties defaults) {
    Set<String> names = Generics.newHashSet();
    names.addAll(properties.stringPropertyNames());
    for (String defaultName : defaults.stringPropertyNames()) {
      names.remove(defaultName);
    }
    if ( ! names.isEmpty()) {
      if (names.size() == 1) {
        throw new IllegalArgumentException("Unknown property: " + names.iterator().next());
      } else {
        throw new IllegalArgumentException("Unknown properties: " + names);
      }
    }
  }

  /**
   * Build a {@code Properties} object containing key-value pairs from
   * the given data where the keys are prefixed with the given
   * {@code prefix}. The keys in the returned object will be stripped
   * of their common prefix.
   *
   * @param properties Key-value data from which to extract pairs
   * @param prefix Key-value pairs where the key has this prefix will
   *               be retained in the returned {@code Properties} object
   * @return A Properties object containing those key-value pairs from
   *         {@code properties} where the key was prefixed by
   *         {@code prefix}. This prefix is removed from all keys in
   *         the returned structure.
   */
    public static Properties extractPrefixedProperties(Properties properties, String prefix) {
      return extractPrefixedProperties(properties, prefix, false);
    }

  /**
   * Build a {@code Properties} object containing key-value pairs from
   * the given data where the keys are prefixed with the given
   * {@code prefix}. The keys in the returned object will be stripped
   * of their common prefix.
   *
   * @param properties Key-value data from which to extract pairs
   * @param prefix Key-value pairs where the key has this prefix will
   *               be retained in the returned {@code Properties} object
   * @param keepPrefix whether the prefix should be kept in the key
   * @return A Properties object containing those key-value pairs from
   *         {@code properties} where the key was prefixed by
   *         {@code prefix}. If keepPrefix is false, the prefix is removed from all keys in
   *         the returned structure.
   */
    public static Properties extractPrefixedProperties(Properties properties, String prefix, boolean keepPrefix) {
    Properties ret = new Properties();

    for (String keyStr : properties.stringPropertyNames()) {
      if (keyStr.startsWith(prefix)) {
        if (keepPrefix) {
          ret.setProperty(keyStr, properties.getProperty(keyStr));
        } else {
          String newStr = keyStr.substring(prefix.length());
          ret.setProperty(newStr, properties.getProperty(keyStr));
        }
      }
    }

    return ret;
  }

  /**
   * Build a {@code Properties} object containing key-value pairs from
   * the given properties whose keys are in a list to keep.
   *
   * @param properties Key-value data from which to extract pairs
   * @param keptProperties Key names to keep (by exact match).
   * @return A Properties object containing those key-value pairs from
   *         {@code properties} where the key was in keptProperties
   */
  public static Properties extractSelectedProperties(Properties properties, Set<String> keptProperties) {
    Properties ret = new Properties();

    for (String keyStr : properties.stringPropertyNames()) {
      if (keptProperties.contains(keyStr)) {
        ret.setProperty(keyStr, properties.getProperty(keyStr));
      }
    }

    return ret;
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
   * Get the value of a property.  If the key is not present, returns defaultValue.
   * This is just equivalent to props.getProperty(key, defaultValue).
   */
  public static String getString(Properties props, String key, String defaultValue) {
    return props.getProperty(key, defaultValue);
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
   *
   *    property1 = value1,value2,"a quoted value",'another quoted value'
   *
   * getStringArray(props, "property1") should return the same thing as
   *
   *    new String[] { "value1", "value2", "a quoted value", "another quoted value" };
   *
   * @return An array of Strings value for the given key in the Properties. May be empty. Never null.
   */
  public static String[] getStringArray(Properties props, String key) {
    String val = props.getProperty(key);
    String[] results;
    if (val == null) {
      results = StringUtils.EMPTY_STRING_ARRAY;
    } else {
      results = StringUtils.decodeArray(val);
      if (results == null) {
        results = StringUtils.EMPTY_STRING_ARRAY;
      }
    }
    // System.out.printf("Called with prop key and value %s %s, returned %s.%n", key, val, Arrays.toString(results));
    return results;
  }

  public static String[] getStringArray(Properties props, String key, String[] defaults) {
    String[] results = MetaClass.cast(props.getProperty(key), String [].class);
    if (results == null) {
      results = defaults;
    }
    return results;
  }

  // add ovp's key values to bp, overwrite if necessary , this is a helper
  public static Properties overWriteProperties(Properties bp, Properties ovp) {
    for (String propertyName : ovp.stringPropertyNames()) {
      bp.setProperty(propertyName,ovp.getProperty(propertyName));
    }
    return bp;
  }

  //  add ovp's key values to bp, don't overwrite if there is already a value
  public static Properties noClobberWriteProperties(Properties bp, Properties ovp) {
    for (String propertyName : ovp.stringPropertyNames()) {
      if (bp.containsKey(propertyName))
        continue;
      bp.setProperty(propertyName,ovp.getProperty(propertyName));
    }
    return bp;
  }


  public static class Property {

    private final String name;
    private final String defaultValue;
    private final String description;

    public Property(String name, String defaultValue, String description) {
      this.name = name;
      this.defaultValue = defaultValue;
      this.description = description;
    }

    public String name() { return name; }

    public String defaultValue() { return defaultValue; }

  }


  public static String getSignature(String name, Properties properties, Property[] supportedProperties) {
    String prefix = (name != null && !name.isEmpty())? name + '.' : "";
    // keep track of all relevant properties for this annotator here!
    StringBuilder sb = new StringBuilder();
    for (Property p : supportedProperties) {
      String pname = prefix + p.name();
      String pvalue = properties.getProperty(pname, p.defaultValue());
      sb.append(pname).append(':').append(pvalue).append(';');
    }
    return sb.toString();
  }

  public static String getSignature(String name, Properties properties) {
    String prefix = (name != null && !name.isEmpty())? name + '.' : "";
    // keep track of all relevant properties for this annotator here!
    StringBuilder sb = new StringBuilder();
    for (String pname : properties.stringPropertyNames()) {
      if (pname.startsWith(prefix)) {
        String pvalue = properties.getProperty(pname);
        sb.append(pname).append(':').append(pvalue).append(';');
      }
    }
    return sb.toString();
  }

}
