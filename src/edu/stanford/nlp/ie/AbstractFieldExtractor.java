package edu.stanford.nlp.ie;

import java.io.*;


/**
 * Abstract superclass for implementations of the FieldExtractor interface.
 * Provides basic functionality for read/write serialization and maintains name
 * and description properties.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public abstract class AbstractFieldExtractor implements FieldExtractor {
  /**
   * Used for serialization compatibility across minor edits
   */
  private static final long serialVersionUID = -78232374030794537L;

  /**
   * Unique name of this instantiated field extractor. @serial
   */
  protected String name = "unnamed";

  /**
   * Brief textual description of this field extractor. @serial
   */
  protected String description = "[no description provided]";

  /**
   * Empty constructor.
   */
  public AbstractFieldExtractor() {
  } // does nothing

  /**
   * Returns whether the given field name is among the list of extractable
   * fields for this field extractor. Default implementation that simply
   * looks through the list of extractable fields and returns a boolean
   * for convinience.
   */
  public boolean isFieldExtractable(String fieldName) {
    String[] extractableFields = getExtractableFields();
    for (int i = 0; i < extractableFields.length; i++) {
      if (fieldName.equals(extractableFields[i])) {
        return (true);
      }
    }

    return (false); // no match found
  }

  /**
   * Returns the unique name of this instantiated field extractor.
   * Subclasses can use the name property provided by AbstractFieldExtractor
   * or override this method to provide their own naming system.
   *
   * @see #setName
   */
  public String getName() {
    return (name);
  }

  /**
   * Sets the unique name of this instantiated field extractor.
   * Note that if subclasses override {@link #getName} then calling this
   * method may have no effect.
   *
   * @see #getName
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Returns a brief textual description of this instantiated field extractor.
   * Subclasses can use the description property provided by AbstractFieldExtractor
   * or override this method to provide their own description system.
   *
   * @see #setDescription
   */
  public String getDescription() {
    return (description);
  }

  /**
   * Sets the unique description of this instantiated field extractor.
   * Note that if subclasses override {@link #getDescription} then calling this
   * method may have no effect.
   *
   * @see #getDescription
   */
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * Loads a serialized extractor from the given input stream and returns it.
   *
   * @return the de-serialized extractor
   * @throws ClassNotFoundException if for some reason the implementing class
   *                                isn't in your classpath (shouldn't ever happen)
   * @throws IOException            if there's a problem reading in the serialized extractor
   */
  public static FieldExtractor loadExtractor(InputStream in) throws ClassNotFoundException, IOException {
    ObjectInputStream ois = new ObjectInputStream(in);
    FieldExtractor fe = (FieldExtractor) ois.readObject();
    ois.close();

    return (fe);
  }

  /**
   * Loads a serialized extractor from the given file and returns it.
   *
   * @see #loadExtractor(InputStream)
   */
  public static FieldExtractor loadExtractor(File in) throws FileNotFoundException, ClassNotFoundException, IOException {
    return (loadExtractor(new FileInputStream(in)));
  }

  /**
   * Serializes this FieldExtractor to the given output stream.
   * Throws an IOException if there is a problem with serialization
   * (e.g. if it can't get write permission or the disk is full).
   */
  public void storeExtractor(OutputStream out) throws IOException {
    ObjectOutputStream oos = new ObjectOutputStream(out);
    oos.writeObject(this);
    oos.flush();
    oos.close();
  }

  /**
   * Serializes this FieldExtractor to the given output stream.
   * Throws an IOException if there is a problem with serialization
   * (e.g. if it can't get write permission or the disk is full).
   */
  public void storeExtractor(File out) throws FileNotFoundException, IOException {
    storeExtractor(new FileOutputStream(out));
  }

  /**
   * Returns the value of getName() as the String representation of this Object.
   */
  @Override
  public String toString() {
    return (getName());
  }
}

