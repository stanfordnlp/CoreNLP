package edu.stanford.nlp.ie.regexp;

import edu.stanford.nlp.ie.SingleFieldExtractor;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SingleFieldExtractor that matches text using a regular expression.
 * The Regexp is assumed to be Concept-neutral, i.e. it returns the same
 * Relation using the same RE for all Concepts. This class is implemented
 * using the java.util.regex.* classes from J2SE 1.4.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class RegexpExtractor extends SingleFieldExtractor {
  /**
   * name of relation (field) extracted
   *
   * @serial
   */
  private final String fieldName;

  /**
   * regular expression to extract text
   *
   * @serial
   */
  private Pattern re;

  /**
   * description of this RegexpExtractor
   *
   * @serial
   */
  private final String regexp;

  /**
   * Used for serialization compatibility across minor edits
   */
  private static final long serialVersionUID = -1639338961685832L;


  /**
   * Constructs a new RegexpExtractor to extract the given relation name
   * using the given regular expression string.
   *
   * @param name      Unique name for this extractor
   * @param fieldName Name of field to extract
   * @param regexp    Regexp to identify that field
   */
  public RegexpExtractor(String name, String fieldName, String regexp) {
    this.fieldName = fieldName;
    this.regexp = regexp;
    re = Pattern.compile(regexp);
    setName(name);
    setDescription("RegexpExtractor[name=" + name + ",fieldName=" + fieldName + ",regexp=" + regexp + "]");
  }

  /**
   * Returns the name of the field extracted by this RegexpExtractor.
   */
  @Override
  public String getExtractableField() {
    return (fieldName);
  }


  /**
   * Returns the first matching stretch of text in the given text, or null,
   * if none is found.
   */
  @Override
  public String extractField(String text) {
    // System.err.println("\tExtracting using " + re + " from |" +
    //                 text + "|");
    // System.err.println("Using regexp |" + regexp + "|");
    // Chris: Here's where I seem to need to rebuild the regexp!
    re = Pattern.compile(regexp);
    Matcher m = re.matcher(text);
    if (m.find()) {
      String matched;
      if (m.groupCount() > 0) {
        matched = m.group(1);
      } else {
        matched = m.group(0);
      }
      // System.err.println("\tMatched " + matched);
      return matched;
    } else {
      // System.err.println("\tDidn't match.");
      return "";
    }
  }

  /**
   * Returns the description of this RegexpExtractor, which includes its
   * name, field name, and regexp.
   */
  @Override
  public String toString() {
    return (description);
  }


  /**
   * Creates and serializes a RegexpExtractor using a field name and regexp
   * passed in on the command line.<p>
   * Usage: <code>java RegexpExtractor name fieldName regexp outfile.obj</code>
   * Name is the unique name that each extractor is required to have. The
   * description
   * is automatically generated based on the name, field name, and regexp.
   * (example: <code>java RegexpExtractor phone-regexp-extractor phone "([0-9][0-9][0-9]) [0-9][0-9][0-9]-[0-9][0-9][0-9][0-9]" phoneRegexpExtractor.obj</code>)
   *
   * @param args Command line arguments, as above
   */
  public static void main(String[] args) {
    if (args.length < 4) {
      System.err.println("Usage: java RegexpExtractorCreator name fieldName regexp outfile.obj");
      System.exit(1);
    }

    try {
      System.err.println("Creating new RegexpExtractor(" + args[0] + "," + args[1] + "," + args[2] + ")");
      RegexpExtractor re = new RegexpExtractor(args[0], args[1], args[2]);
      System.err.println("Serializing extractor to " + args[3]);
      re.storeExtractor(new File(args[3]));
      System.err.println("Done");
    } catch (Exception e) {
      System.err.println("An error occured while trying to create the RegexpExtractor:");
      e.printStackTrace();
    }
  }

}
