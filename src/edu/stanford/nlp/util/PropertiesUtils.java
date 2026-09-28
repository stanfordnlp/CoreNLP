package edu.stanford.nlp.util;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

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

  /**
   * Returns true iff any String key in the given Properties starts with the given prefix.
   *
   * @param props Properties object
   * @param prefix The prefix to look for
   * @return whether some key starts with {@code prefix}
   */
  public static boolean hasPropertyPrefix(Properties props, String prefix) {
    for (Object o : props.keySet()) {
      if (o instanceof String && ((String) o).startsWith(prefix)) return true;
    }
    return false;
  }

  /** Create a Properties object from the passed in String arguments.
   *  The odd numbered arguments are the names of keys, and the even
   *  numbered arguments are the value of the preceding key
   *
   *  @param args An even-length list of alternately key and value
   *  @return A new Properties object with the given keys and values
   *  @throws IllegalArgumentException if there are an odd number of arguments
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

  /**
   * Convert from Properties to String, in the format written by {@link Properties#store}.
   *
   * @param props the properties to convert
   * @return the properties in {@code .properties} file format, including a date comment
   */
  public static String asString(Properties props) {
    try {
      StringWriter sw = new StringWriter();
      props.store(sw, null);
      return sw.toString();
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * Convert from String to Properties, parsing it as by {@link Properties#load}.
   *
   * @param str the properties in {@code .properties} file format
   * @return a new Properties object
   */
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

  /**
   * Prints the properties to a stream, one per line in key order, preceded
   * by the message (if not null) and followed by a blank line.  A property
   * whose key is the empty string is not printed.
   *
   * @param message a header line to print first, or null for none
   * @param properties the properties to print
   * @param stream the stream to print to
   */
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

  /**
   * Prints the properties to {@code System.out}; see
   * {@link #printProperties(String, Properties, PrintStream)}.
   *
   * @param message a header line to print first, or null for none
   * @param properties the properties to print
   */
  public static void printProperties(String message, Properties properties) {
    printProperties(message, properties, System.out);
  }

  /**
   * Tired of Properties not behaving like {@code Map<String,String>}s?  This method will solve that problem for you.
   * Only the properties set directly in {@code properties} are copied, not its defaults.
   *
   * @param properties the properties to copy; all keys and values must be Strings
   * @return a new HashMap with the same keys and values
   * @throws ClassCastException if a key or value is not a String
   */
  public static Map<String, String> asMap(Properties properties) {
    Map<String, String> map = Generics.newHashMap();
    for (Entry<Object, Object> entry : properties.entrySet()) {
      map.put((String)entry.getKey(), (String)entry.getValue());
    }
    return map;
  }

  /**
   * Returns the entries of the properties, sorted by key.
   *
   * @param properties the properties to list; all keys and values must be Strings
   * @return a new list of the entries, sorted by key
   */
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
   *
   * @param <E> the type of the value
   * @param props the properties to read from
   * @param key the property to look up
   * @param defaultValue the value to return if the key is not present
   * @param type the type to convert the value to, using {@link MetaClass#cast}
   * @return the converted value, or {@code defaultValue} if the key is not present
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
   * Get the value of a property as a path to a directory.  If the key is not present, returns defaultValue.
   * If the path doesn't terminate with '/', append '/' to the string.
   *
   * @param props the properties to read from
   * @param key the property to look up
   * @param defaultValue the value to use if the key is not present; may be null
   * @return the path ending with '/', or null if the key is not present and {@code defaultValue} is null
   */
  public static String getDirPath(Properties props, String key, String defaultValue) {
    String returnPath = props.getProperty(key, defaultValue);
    if (returnPath != null && ! returnPath.endsWith("/")) {
      returnPath += "/";
    }
    return returnPath;
  }

  /**
   * Get the value of a property.  If the key is not present, returns defaultValue.
   * This is just equivalent to props.getProperty(key, defaultValue).
   *
   * @param props the properties to read from
   * @param key the property to look up
   * @param defaultValue the value to return if the key is not present
   * @return the value of the property, or {@code defaultValue}
   */
  public static String getString(Properties props, String key, String defaultValue) {
    return props.getProperty(key, defaultValue);
  }

  /**
   * Load an integer property.  If the key is not present, returns 0.
   *
   * @param props the properties to read from
   * @param key the property to look up
   * @return the value of the property as an int
   * @throws NumberFormatException if the value is not an integer
   */
  public static int getInt(Properties props, String key) {
    return getInt(props, key, 0);
  }

  /**
   * Load an integer property.  If the key is not present, returns defaultValue.
   *
   * @param props the properties to read from
   * @param key the property to look up
   * @param defaultValue the value to return if the key is not present
   * @return the value of the property as an int
   * @throws NumberFormatException if the value is not an integer
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
   *
   * @param props the properties to read from
   * @param key the property to look up
   * @param defaultValue the value to return if the key is not present
   * @return the value of the property as a long
   * @throws NumberFormatException if the value is not an integer
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
   *
   * @param props the properties to read from
   * @param key the property to look up
   * @return the value of the property as a double
   * @throws NumberFormatException if the value is not a number
   */
  public static double getDouble(Properties props, String key) {
    return getDouble(props, key, 0.0);
  }

  /**
   * Load a double property.  If the key is not present, returns defaultValue.
   *
   * @param props the properties to read from
   * @param key the property to look up
   * @param defaultValue the value to return if the key is not present
   * @return the value of the property as a double
   * @throws NumberFormatException if the value is not a number
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
   * Any value other than "true" (ignoring case) is false.
   *
   * @param props the properties to read from
   * @param key the property to look up
   * @return the value of the property as a boolean
   */
  public static boolean getBool(Properties props, String key) {
    return getBool(props, key, false);
  }

  /**
   * Load a boolean property.  If the key is not present, returns defaultValue.
   * Any value other than "true" (ignoring case) is false.
   *
   * @param props the properties to read from
   * @param key the property to look up
   * @param defaultValue the value to return if the key is not present
   * @return the value of the property as a boolean
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
   *
   * @param props the properties to read from
   * @param key the property to look up
   * @return the values as an int array, or null if the key is not present
   */
  public static int[] getIntArray(Properties props, String key) {
    Integer[] result = MetaClass.cast(props.getProperty(key), Integer [].class);
    return ArrayUtils.toPrimitive(result);
  }

  /**
   * Loads a comma-separated list of doubles from Properties.  The list cannot include any whitespace.
   *
   * @param props the properties to read from
   * @param key the property to look up
   * @return the values as a double array, or null if the key is not present
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
   * @param props the properties to read from
   * @param key the property to look up
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

  /**
   * Loads a comma-separated list of strings from Properties, as
   * {@link #getStringArray(Properties, String)} does.
   *
   * @param props the properties to read from
   * @param key the property to look up
   * @param defaults the array to return if the key is not present
   * @return the values as a String array, or {@code defaults} if the key is not present
   */
  public static String[] getStringArray(Properties props, String key, String[] defaults) {
    String[] results = MetaClass.cast(props.getProperty(key), String [].class);
    if (results == null) {
      results = defaults;
    }
    return results;
  }

  // add ovp's key values to bp, overwrite if necessary , this is a helper
  /**
   * Copies all of the properties in {@code ovp} (including its defaults)
   * into {@code bp}, overwriting any existing values.
   *
   * @param bp the properties to modify
   * @param ovp the properties to copy from
   * @return {@code bp}, after modification
   */
  public static Properties overWriteProperties(Properties bp, Properties ovp) {
    for (String propertyName : ovp.stringPropertyNames()) {
      bp.setProperty(propertyName,ovp.getProperty(propertyName));
    }
    return bp;
  }

  //  add ovp's key values to bp, don't overwrite if there is already a value
  /**
   * Copies the properties in {@code ovp} (including its defaults) into
   * {@code bp}, except those whose keys are already set directly in {@code bp}.
   *
   * @param bp the properties to modify
   * @param ovp the properties to copy from
   * @return {@code bp}, after modification
   */
  public static Properties noClobberWriteProperties(Properties bp, Properties ovp) {
    for (String propertyName : ovp.stringPropertyNames()) {
      if (bp.containsKey(propertyName))
        continue;
      bp.setProperty(propertyName,ovp.getProperty(propertyName));
    }
    return bp;
  }


  /** A property name, with its default value and a description. */
  public static class Property {

    private final String name;
    private final String defaultValue;
    private final String description;

    /**
     * Creates a property description.
     *
     * @param name the name of the property
     * @param defaultValue the default value of the property
     * @param description a description of the property
     */
    public Property(String name, String defaultValue, String description) {
      this.name = name;
      this.defaultValue = defaultValue;
      this.description = description;
    }

    /**
     * Returns the name of the property.
     *
     * @return the name of the property
     */
    public String name() { return name; }

    /**
     * Returns the default value of the property.
     *
     * @return the default value of the property
     */
    public String defaultValue() { return defaultValue; }

  }


  // This is CoreNLP-specific-ish and now unused. Delete?
  /**
   * Returns a signature of the values of the given supported properties,
   * as {@code name:value;} for each, with the property names prefixed by
   * {@code name} and a period (if {@code name} is non-empty).  Properties
   * not set in {@code properties} use their default values.
   *
   * @param name the prefix for the property names, or null or empty for none
   * @param properties the properties to read from
   * @param supportedProperties the properties to include in the signature
   * @return the signature string
   */
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

  /**
   * Returns a signature of all the properties relevant to the annotator
   * {@code name}, as {@code name:value;} for each property whose name starts
   * with {@code name} and a period.  Some annotators use several prefixes
   * without the period (e.g. tokenize, ssplit and segment share their
   * properties), and parse gets {@code parse.binaryTrees=true} if sentiment
   * is among the annotators and that property is not set.
   *
   * @param name the name of the annotator, or null or empty to include every property
   * @param properties the properties to read from
   * @return the signature string
   */
  public static String getSignature(String name, Properties properties) {
    String[] prefixes = new String[]{(name != null && !name.isEmpty())? name + '.' : ""};
    // TODO(gabor) This is a hack, as tokenize and ssplit depend on each other so heavily
    // the tokenize annotator also uses segment properties to determine which model to use, etc
    if ("tokenize".equals(name) || "ssplit".equals(name) || "segment".equals(name)) {
      prefixes = new String[]{"tokenize", "ssplit", "segment"};
    }
    // TODO [chris 2017]: Another hack. Traditionally, we have called the cleanxml properties clean!
    if ("clean".equals(name) || "cleanxml".equals(name)) {
      prefixes = new String[]{"clean", "cleanxml"};
    }

    if ("mention".equals(name)) {
      prefixes = new String[]{"mention", "coref"};
    }
    if ("ner".equals(name)) {
      prefixes = new String[]{"ner", "sutime"};
    }
    Properties propertiesCopy = new Properties();
    propertiesCopy.putAll(properties);
    // handle special case of implied properties (e.g. sentiment implies parse should set parse.binaryTrees = true
    // TODO(jb) This is a hack: handle implied need for binary trees if sentiment annotator is present
    Set<String> annoNames =
        Generics.newHashSet(Arrays.asList(properties.getProperty("annotators", "").split("[, \t]+")));
    if ("parse".equals(name) && annoNames.contains("sentiment") && !properties.containsKey("parse.binaryTrees")) {
      propertiesCopy.setProperty("parse.binaryTrees", "true");
    }
    // keep track of all relevant properties for this annotator here!
    StringBuilder sb = new StringBuilder();
    for (String pname : propertiesCopy.stringPropertyNames()) {
      for (String prefix : prefixes) {
        if (pname.startsWith(prefix)) {
          String pvalue = propertiesCopy.getProperty(pname);
          sb.append(pname).append(':').append(pvalue).append(';');
        }
      }
    }
    return sb.toString();
  }

  /**
   * Convert the given properties to a json string
   *
   * @param props the properties to convert
   * @return a single-line JSON object mapping each property name to its value
   */
  public static String propsAsJsonString(Properties props) {
    List<String> jsonProperties = props.stringPropertyNames().stream().map(key -> '"' + StringUtils.escapeJsonString(key) +
      "\": \"" + StringUtils.escapeJsonString(props.getProperty(key)) + '"')
      .collect(Collectors.toList());
    return "{ " + StringUtils.join(jsonProperties, ", ") + " }";
  }
}
