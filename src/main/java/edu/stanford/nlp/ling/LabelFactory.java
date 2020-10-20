package edu.stanford.nlp.ling;


/**
 * A <code>LabelFactory</code> object acts as a factory for creating
 * objects of class <code>Label</code>, or some descendant class.
 * It can also make Labels from Strings, optionally with options.
 *
 * @author Christopher Manning
 * @version 2000/12/25
 */
public interface LabelFactory {

  /**
   * Make a new label with this <code>String</code> as the
   * <code>value</code>.
   * Any other fields of the label would normally be <code>null</code>.
   *
   * @param labelStr The String that will be used for value
   * @return The new Label
   */
  public Label newLabel(String labelStr);


  /**
   * Make a new label with this <code>String</code> as the value, and
   * the type determined in an implementation-dependent way from the
   * options value.
   *
   * @param labelStr The String that will be used for value
   * @param options  May determine what kind of label is created
   * @return The new Label
   */
  public Label newLabel(String labelStr, int options);


  /**
   * Make a new label.  The String argument will be decomposed into
   * multiple fields in an implementing class-specific way, in
   * accordance with the class's setFromString() method.
   *
   * @param encodedLabelStr The String that will be used for labelling the
   *                        object (by decoding it into parts)
   * @return The new Label
   */
  public Label newLabelFromString(String encodedLabelStr);


  /**
   * Create a new <code>Label</code>, where the label is formed from
   * the <code>Label</code> object passed in.  The new Label is
   * guaranteed to at least copy the <code>value()</code> of the
   * source label (if non-null); it may also copy other components
   * (this is implementation-specific).  However, if oldLabel is of
   * the same type as is produced by the factory, then the whole
   * label should be cloned, so that the returnedLabel.equals(oldLabel).
   * <i>Implementation note:</i> That last sentence isn't true of all
   * current implementations (e.g., WordTag), but we should make it
   * so that it is true!
   *
   * @param oldLabel The Label that the new label is being created from
   * @return The new label of a particular type
   */
  public Label newLabel(Label oldLabel);

}
