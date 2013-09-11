package edu.stanford.nlp.ie;

import java.awt.*;
import java.util.Set;

/**
 * Interface to programmatically create FieldExtractors.
 * A FieldExtractorCreator manages a set of properties from which it can construct
 * a FieldExtractor as specified. This is intended for use within a GUI to
 * create and manage extractors, so support is stubbed out for providing a custom
 * UI component to specify how the extractor is created. A default implementation
 * would simply provide a graphical interface to the unique extractor name
 * and properties of the creator.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public interface FieldExtractorCreator {
  /**
   * Returns the name of extractor that can be created. The name should
   * be suitable for display in a list of possible extractors to create.
   *
   * @return name of the extractor that can be created
   */
  public String getName();

  /**
   * Returns the set of possible property names (String) that can be
   * set for this creator. This should ideally let users know the complete
   * set of properties for this creator, but {@link #setProperty} does not
   * reject property names that are not in this set.
   *
   * @return enumeration of possible property names for this creator
   */
  public Set propertyNames();

  /**
   * Returns the current value of the given property, or <tt>null</tt> if
   * that property is not set.
   *
   * @return value of the given property or <tt>null</tt> if it's not set.
   */
  public String getProperty(String key);

  /**
   * Returns the default value of the given property, or <tt>null</tt> if
   * there is no default. This will be used to instantiate default values
   * for properties and allow for "reverting" edited properties.
   *
   * @return default value of the given property of <tt>null</tt> if none.
   */
  public String getPropertyDefault(String key);

  /**
   * Returns a brief textual description of the given property, or
   * <tt>null</tt> if no description is provided. This may serve for things
   * like tooltips (context-sensitive help) for properties.
   */
  public String getPropertyDescription(String key);

  /**
   * Returns an instance of the type of Object the given property actually
   * represents. Some properties actually represent numbers, files, enumerated
   * types, and so on. Editing controls may wish to know this information in
   * order to provide custom editors (e.g. a file dialog for files). Return
   * an Integer for integers, a Double for real numbers, a File for files,
   * and a java.util.List for enumerated types. In most cases, the actual
   * value is ignored (only the class is considered) but in the case of a
   * List, the toString() values of the list elements should be used for
   * the possible values of an enumerated type. This is useful when the
   * property represents one of a set of constants. Returning <tt>null</tt>
   * is equivalent to returning a String.
   */
  public Object getPropertyClass(String key);

  /**
   * Sets the current value of the given property.
   *
   * @param key   property name
   * @param value property value
   */
  public void setProperty(String key, String value);


  /**
   * Returns whether the given property is required to be set before creating
   * a FieldExtractor. UIs should use this information to force users to fill
   * out all required fields.
   */
  public boolean isRequired(String key);

  /**
   * Returns a UI component for customizing how the extractor is created.
   * Implementations may return null if they do not wish to provide a custom
   * UI component. Implementations should either use the component to set
   * properties or hold variables that can be referenced inside
   * {@link #createFieldExtractor}.
   *
   * @return custom component for creating an extractor or <tt>null</tt>.
   */
  public Component customCreatorComponent();

  /**
   * Creates a new FieldExtractor using the properties for this creator and
   * the given unique name. Implementations should throw an IllegalStateException
   * (hopefully with a suitable error message) if the properties provided are
   * unsuitable for creating the FieldExtractor.
   *
   * @param name unique name for the field extractor to create
   * @return newly created FieldExtractor based on the properties
   * @throws IllegalPropertyException if the properties make it impossible to
   *                                  correctly create the extractor
   */
  public FieldExtractor createFieldExtractor(String name) throws IllegalPropertyException;
}
