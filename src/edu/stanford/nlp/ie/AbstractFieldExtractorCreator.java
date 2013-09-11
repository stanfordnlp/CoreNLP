package edu.stanford.nlp.ie;

import java.awt.*;
import java.io.File;
import java.util.*;

/**
 * Abstract superclass for implementers of the FieldExtractorCreator interface.
 * Maintains a name property, an internal list of properties for customizing
 * the field extractor, and a set of required properties.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public abstract class AbstractFieldExtractorCreator implements FieldExtractorCreator {

  /** Template property class for Integer properties. */
  public static final Integer PC_INTEGER = Integer.valueOf(0);
  /**
   * Template property class for Double properties.
   */
  public static final Double PC_DOUBLE = new Double(0);
  /**
   * Template property class for Long properties.
   */
  public static final Long PC_LONG = new Long(0);
  /**
   * Template property class for Float properties.
   */
  public static final Float PC_FLOAT = new Float(0);
  /** Template property class for Boolean properties. */
  public static final Boolean PC_BOOLEAN = Boolean.valueOf(true);
  /**
   * Template property class for File properties.
   */
  public static final File PC_FILE = new File("");
  /**
   * Template property class for String properties. You shouldn't really
   * need this since returning null for the property class is equivalent to
   * saying it's a String, but it's included for completeness.
   */
  public static final String PC_STRING = "";


  /**
   * Name this creator presents for the extractor it can create.
   */
  protected String name;

  /**
   * Properties for this creator.
   */
  protected final Properties properties = new Properties();
  /**
   * Default values for each property.
   */
  protected final Properties propertyDefaults = new Properties();
  /**
   * Descriptions for each property key.
   */
  protected final Properties propertyDescriptions = new Properties();
  /**
   * Example instances of the class represented by each property key.
   */
  protected final Map<String, Object> propertyClasses = new HashMap<String, Object>();

  /**
   * Property names that must have values before the extractor can be created.
   */
  protected final Set<String> requiredProperties = new HashSet<String>();

  /**
   * Default implementation returns null.
   */
  public Component customCreatorComponent() {
    return (null);
  }

  /**
   * Returns the name of extractor that can be created. The name should
   * be suitable for display in a list of possible extractors to create.
   * Default implementation maintains a read/write name property.
   *
   * @return name of the extractor that can be created
   */
  public String getName() {
    return (name);
  }

  /**
   * Sets the name this creator presents for the extractor it can create.
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Returns the current value of the given property, or <tt>null</tt> if
   * that property is not set. Default implementation uses internal properties.
   * If the property is not found in the internal properties, the internal
   * default properties are checked.
   *
   * @return value of the given property or <tt>null</tt> if it's not set.
   */
  public String getProperty(String key) {
    String value = properties.getProperty(key);
    if (value == null) {
      value = propertyDefaults.getProperty(key);
    }
    return (value);
  }

  /**
   * Sets the current value of the given property.
   * Default implementation uses internal properties.
   * Setting value to null removes the property.
   */
  public void setProperty(String key, String value) {
    if (value == null) {
      properties.remove(key);
    } else {
      properties.setProperty(key, value);
    }
  }

  /**
   * Replaces the properties for this creator with the given properties.
   * The properties are copied over to an internal properties object.
   */
  public void setProperties(Properties properties) {
    this.properties.clear();
    this.properties.putAll(properties);
  }

  /**
   * Marks the given property as required. Does not change whether there is
   * a current value set for this property. This can be undone by a call
   * to {@link #setPropertyOptional}
   *
   * @see #setPropertyOptional
   * @see #isRequired
   */
  public void setPropertyRequired(String key) {
    requiredProperties.add(key);
  }

  /**
   * Marks the given property as not required. All properties start out
   * optional by default, until {@link #setPropertyRequired} is called. This
   * undoes the effect of a previous such call. Does not remove the current
   * value for this property, if any.
   *
   * @see #setPropertyRequired
   * @see #isRequired
   */
  public void setPropertyOptional(String key) {
    requiredProperties.remove(key);
  }

  /**
   * Default implementation uses internal set of required properties.
   *
   * @see #setPropertyRequired
   */
  public boolean isRequired(String key) {
    return (requiredProperties.contains(key));
  }

  /**
   * Returns the set of possible property names (String) that can be
   * set for this creator. This should ideally let users know the complete
   * set of properties for this creator, but {@link #setProperty} does not
   * reject property names that are not in this set.
   * Default implementation returns the keySet of the internal properties.
   * Subclasses may want to override this method to provide a more complete
   * set of possible property names instead of just relying on the currently
   * set properties.
   *
   * @return enumeration of possible property names for this creator
   */
  public Set<?> propertyNames() {
    return (properties.keySet());
  }

  /**
   * Default implementation returns the name of this FieldExtractorCreator.
   */
  @Override
  public String toString() {
    return (getName());
  }

  /**
   * Convinience method to make a Set from a list of Strings.
   */
  public static Set<String> asSet(String[] elements) {
    return (new HashSet<String>(Arrays.asList(elements)));
  }

  /**
   * Returns the class instance for the given property or <tt>null</tt> if
   * it's not been specially defined. Default implementation uses an internal
   * map from keys to class instances.
   *
   * @see #setPropertyClass
   */
  public Object getPropertyClass(String key) {
    return (propertyClasses.get(key));
  }

  /**
   * Sets the example instance of the given property's represented class.
   * Default implementation uses internal map from keys to class instances.
   * Setting value to null removes the class instance for this property.
   * Subclasses may wish to use one of the <tt>PC</tt> constants provided
   * by this class to designate common class types.
   */
  public void setPropertyClass(String key, Object classInstance) {
    if (classInstance == null) {
      propertyClasses.remove(key);
    } else {
      propertyClasses.put(key, classInstance);
    }
  }

  /**
   * Returns the default value for the given property or <tt>null</tt> if
   * there's no default set. Default implementation uses in internal default
   * properties.
   *
   * @see #setPropertyDefault
   * @see #setPropertyDefaults
   */
  public String getPropertyDefault(String key) {
    return (propertyDefaults.getProperty(key));
  }

  /**
   * Sets the current value of the given property's default value.
   * Default implementation uses internal class name properties.
   * Setting value to null removes the class name for this property.
   */
  public void setPropertyDefault(String key, String defaultValue) {
    if (defaultValue == null) {
      propertyClasses.remove(key);
    } else {
      propertyDefaults.setProperty(key, defaultValue);
    }
  }

  /**
   * Replaces the default properties for this creator with the given
   * default properties. The properties are copied over to an
   * internal properties object. This is not to be confused with the ability
   * of Properties objects to also store defaults internally (don't do that).
   */
  public void setPropertyDefaults(Properties defaultProperties) {
    propertyDefaults.clear();
    propertyDefaults.putAll(defaultProperties);
  }

  /**
   * Returns a description of the given property or <tt>null</tt> if none was
   * supplied. Default implementation uses an internal properties with
   * descriptions.
   *
   * @see #setPropertyDescription
   * @see #setPropertyDescriptions
   */
  public String getPropertyDescription(String key) {
    return (propertyDescriptions.getProperty(key));
  }

  /**
   * Sets the current description of the given property.
   * Default implementation uses internal class name properties.
   * Setting value to null removes the description for this property.
   */
  public void setPropertyDescription(String key, String description) {
    if (description == null) {
      propertyDescriptions.remove(key);
    } else {
      propertyDescriptions.setProperty(key, description);
    }
  }

  /**
   * Replaces the property descriptions for this creator with the given
   * property descriptions. The properties are copied over to an
   * internal properties object.
   */
  public void setPropertyDescriptions(Properties descriptions) {
    propertyDescriptions.clear();
    propertyDescriptions.putAll(descriptions);
  }
}
