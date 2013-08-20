package edu.stanford.nlp.ie.regexp;

import edu.stanford.nlp.ie.AbstractFieldExtractor;
import edu.stanford.nlp.ie.FieldExtractor;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


/**
 * An implentation of the general FieldExtractor interface which
 * fills in multiple fields by matching various regular expressions.
 * Provides basic functionality for write serialization.
 * <p>
 * <i>Implementation notes:</i> Now serializes the String form of regex
 * patterns rather than trying to serialize Patterns.  Now uses
 * Java library regex.  You can test this file using the example.txt in
 * the package directory.
 *
 * @author Christopher Manning
 * @author Joseph Smarr (jsmarr@stanford.edu) - 02/12/03 - post KAON
 */
public class RegexpsFieldExtractor extends AbstractFieldExtractor {

  private static final long serialVersionUID = 2L;

  /**
   * names of concepts extracted from
   *
   * @serial
   */
  @SuppressWarnings({"unused", "UnusedDeclaration", "FieldCanBeLocal"})
  private final String[] conceptNames;

  /**
   * names of relations (fields) extracted
   *
   * @serial
   */
  private final String[] fieldNames;

  /**
   * group of regular expression to extract text from
   *
   * @serial
   */
  private final int[] groups;

  /**
   * String form of regular expressions to extract text
   *
   * @serial
   */
  private final String[] regexStrings;

  /**
   * regular expressions to extract text
   *
   */
  private transient Pattern[] regexps;


  /**
   * Create a new RegexpsFieldExtractor.
   *
   * @param name         the unique name required for this extractor
   * @param conceptNames an array of concepts that can be extracted
   * @param fieldNames   an array of fields to extract
   * @param groups       an array of specifications of the re group to keep
   * @param reStrs       an array of text regular expressions (of the same
   *                     size as fieldNames) which will match each one
   * @throws IllegalArgumentException We through this more general
   *                                  exception, so that we can changed regexp packages without having
   *                                  to change the API.
   */
  public RegexpsFieldExtractor(String name, String[] conceptNames, String[] fieldNames, int[] groups, String[] reStrs) throws IllegalArgumentException {
    if (fieldNames.length != reStrs.length) {
      throw new IllegalArgumentException("Array lengths differ");
    }
    this.conceptNames = conceptNames;
    this.fieldNames = fieldNames;
    this.groups = groups;
    regexps = new Pattern[reStrs.length];
    // copy String array for safety
    regexStrings = new String[reStrs.length];
    setName(name);
    try {
      for (int i = 0; i < regexps.length; i++) {
        regexStrings[i] = reStrs[i];
        regexps[i] = Pattern.compile(reStrs[i]);
      }
    } catch (PatternSyntaxException mpe) {
      throw new IllegalArgumentException("Invalid RE");
    }
    StringBuilder temp = new StringBuilder();
    temp.append("RegexpsExtractor[").append(regexps.length).append("] = {");
    if (regexps.length > 0) {
      temp.append("(").append(fieldNames[0]).append(",").append(reStrs[0]).append(")");
      if (regexps.length > 1) {
        temp.append(", ...");
      }
    }
    temp.append("}");
    setDescription(temp.toString());
  }


  /**
   * Returns the set of fields that this FieldExtractor knows how to
   * extract. Returned array may be empty but will never be null.
   */
  public String[] getExtractableFields() {
    return (fieldNames);
  }


  /**
   * Returns a map of extracted fields for the given text.
   */
  public Map<String,String> extractFields(String text) {
    Map<String,String> extractedFields = new HashMap<String,String>();
    for (int i = 0; i < fieldNames.length; i++) {
      String fieldName = fieldNames[i];
      String extractedField;
      Matcher m = regexps[i].matcher(text);
      if (m.find()) {
        extractedField = m.group(groups[i]);
      } else {
        extractedField = "";
      }
      extractedFields.put(fieldName, extractedField);
    }
    return extractedFields;
  }

  /** Reconstruct the Patterns when deserialize. */
  private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ois.defaultReadObject();
    regexps = new Pattern[regexStrings.length];
    for (int i = 0; i < regexStrings.length; i++) {
      regexps[i] = Pattern.compile(regexStrings[i]);
    }
  }

  /**
   * Creates and serializes a RegexpsFieldExtractor based on field names
   * and regexps passed in from a file. <p>
   * Usage: <code>java RegexpsFieldExtractorCreator filename
   * outfile.obj</code> <br>
   * (example: <code>java RegexpsFieldExtractorCreator phonefile
   * phoneRegexpExtractor.obj</code>)<br>
   * The format of the file should be a series of lines consisting of a
   * concept name, field name, group to extract (0 for whole match), regular
   * expression.  Each of these should be separated by whitespace.  The
   * final regexp can contain embedded whitespace (but may not begin with
   * it), but other fields may not. The unique name for this RegexpsExtractor
   * will be based on the output filename used.
   *
   * @param args See above
   */
  public static void main(String[] args) {
    if (args.length < 2) {
      System.err.println("Usage: java RegexpsFieldExtractorCreator filename outfile.obj");
      System.exit(-1);
    }

    try {
      java.util.regex.Pattern lineMatch = java.util.regex.Pattern.compile("^[ \t]*([^\t ]+)[ \t]+([^\t ]+)[ \t]+([^\t ]+)[ \t]+(.*)$");
      java.util.regex.Pattern comment = java.util.regex.Pattern.compile("^#");
      System.err.println("Creating new RegexpsFieldExtractor(" + args[0] + "," + args[1] + ")");
      BufferedReader br = new BufferedReader(new FileReader(args[0]));
      ArrayList<String> conceptNames = new ArrayList<String>();
      ArrayList<String> fieldNames = new ArrayList<String>();
      ArrayList<Integer> groupNums = new ArrayList<Integer>();
      ArrayList<String> res = new ArrayList<String>();
      String line;

      while ((line = br.readLine()) != null) {
        Matcher commMatcher = comment.matcher(line);
        if (!commMatcher.find()) {
          System.err.println("Processing |" + line + "|");
          Matcher matcher = lineMatch.matcher(line);
          if (matcher.find()) {
            // System.err.println("Matched |" + matcher.group(1) + "|");
            conceptNames.add(matcher.group(1));
            fieldNames.add(matcher.group(2));
            groupNums.add(Integer.valueOf(matcher.group(3)));
            res.add(matcher.group(4));
          }
        }
      }
      String[] concepts = conceptNames.toArray(new String[conceptNames.size()]);
      String[] fields = fieldNames.toArray(new String[fieldNames.size()]);
      int[] groups = new int[groupNums.size()];
      for (int i = 0; i < groupNums.size(); i++) {
        groups[i] = groupNums.get(i).intValue();
      }
      String[] regex = res.toArray(new String[res.size()]);

      // unique name for this RegexpsExtractor
      StringBuilder name = new StringBuilder("RegexpExtractor-");
      String outfilename = new File(args[1]).getName();
      int index = outfilename.lastIndexOf('.');
      if (index == -1) {
        name.append(outfilename);
      } else {
        name.append(outfilename.substring(0, index));
      }

      RegexpsFieldExtractor rfe = new RegexpsFieldExtractor(name.toString(), concepts, fields, groups, regex);
      System.err.println("Serializing extractor to " + args[1]);
      rfe.storeExtractor(new File(args[1]));
      System.err.println("Done");
      System.err.println("Loading and testing extractor");
      FieldExtractor fe = AbstractFieldExtractor.loadExtractor(new File(args[1]));
      String sampleText = "Jacob Richardson, 221 Bryant St, Palo Alto CA 94301, 650-123-4567";
      System.err.println("Testing on: " + sampleText);
      System.err.println("Extracted: " +  fe.extractFields(sampleText));
    } catch (Exception e) {
      System.err.println("An error occured while trying to create the RegexpsFieldExtractor:");
      e.printStackTrace();
    }
  }
}
