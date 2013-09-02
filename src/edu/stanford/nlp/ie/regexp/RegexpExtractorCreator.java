package edu.stanford.nlp.ie.regexp;

import edu.stanford.nlp.ie.AbstractFieldExtractorCreator;
import edu.stanford.nlp.ie.FieldExtractor;
import edu.stanford.nlp.ie.IllegalPropertyException;

import java.awt.*;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

/**
 * FieldExtractorCreator for regular expression extractors.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class RegexpExtractorCreator extends AbstractFieldExtractorCreator {
  private static final String regexpProperty = "regexp";
  private final RegexpExtractorCreatorPanel recPanel;

  /**
   * Constructs a new RegexpExtractorCreator with name "Regular Expression"
   * and the required property  "regexp".
   */
  public RegexpExtractorCreator() {
    setName("Regular Expression");
    //setPropertyRequired(regexpProperty);
    //setPropertyDescription(regexpProperty,"Regular expression to match extracted text - if regexp contains capture groups, only $1 is is extracted");
    recPanel = new RegexpExtractorCreatorPanel();
  }

  @Override
  public String getProperty(String key) {
    if (regexpProperty.equals(key)) {
      return (recPanel.getRegexp());
    } else {
      return (super.getProperty(key));
    }
  }

  @Override
  public Component customCreatorComponent() {
    return (recPanel);
  }

  /**
   * Returns the propertiey "regexp".
   * <ul>
   * <li><tt>regexp</tt> is the regular expression pattern to match target text
   * </ul>
   */
  @Override
  public Set<String> propertyNames() {
    return (asSet(new String[]{regexpProperty}));
  }

  /**
   * Creates a new RegexpExtractor with the given name, and regexp.
   * The extracted field name will be the same as the extractor name.
   *
   * @throws IllegalPropertyException if targetField is empty or regexp has illegal syntax.
   */
  public FieldExtractor createFieldExtractor(String name) throws IllegalPropertyException {
    try {
      return (new RegexpExtractor(name, name, getProperty(regexpProperty)));
    } catch (PatternSyntaxException pse) {
      throw(new IllegalPropertyException(this, "Error in " + regexpProperty + " syntax:\n" + pse.getMessage(), regexpProperty, getProperty(regexpProperty)));
    }
  }
}
