package edu.stanford.nlp.ie.regexp;


/**
 * Utility for creating and serializing RegexpExtractors. See main() comment
 * for more details.
 * This version works with Apache ORO regular expression package.
 * (but uses the 1.4 regular expressions for parsing input file)
 * <p/>
 * JS: 02/12/03 Just use RegexpsExtractor now, it has the same functionality.
 *
 * @author Christopher Manning
 */
public class RegexpsFieldExtractorCreator {

  /**
   * Not an instantiable class
   */
  private RegexpsFieldExtractorCreator() {
  }

  /** Creates and serializes a RegexpsFieldExtractor based on field names
   *  and regexps passed in from a file. <p>
   *  Usage: <code>java RegexpsFieldExtractorCreator filename
   *        outfile.obj</code> <br>
   * (example: <code>java RegexpsFieldExtractorCreator phonefile
   *        phoneRegexpExtractor.obj</code>)<br>
   * The format of the file should be a series of lines consisting of a
   * concept name, field name, group to extract (0 for whole match, regular
   * expression.  Each of these should be separated by whitespace.  The
   * final regexp can contain embedded whitespace (but may not begin with
   * it), but other fields may not.
   *
   * @param args See above
   */
  /*
  public static void main(String[] args) {
if (args.length < 2) {
    System.err.println("Usage: java RegexpsFieldExtractorCreator filename outfile.obj");
    System.exit(-1);
}

try {
    Pattern lineMatch = Pattern.compile("^[ \t]*([^\t ]+)[ \t]+([^\t ]+)[ \t]+([^\t ]+)[ \t]+(.*)$");
    Pattern comment = Pattern.compile("^#");
    System.err.println("Creating new RegexpsFieldExtractor("+args[0]+","+args[1]+")");
    BufferedReader br = new BufferedReader(new FileReader(args[0]));
    ArrayList conceptNames = new ArrayList();
    ArrayList fieldNames = new ArrayList();
    ArrayList groupNums = new ArrayList();
    ArrayList res = new ArrayList();
    String line;

    while ((line = br.readLine()) != null) {
  Matcher commMatcher = comment.matcher(line);
  if ( ! commMatcher.find()) {
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
    String[] concepts = (String[]) conceptNames.toArray(new String[0]);
    String[] fields = (String[]) fieldNames.toArray(new String[0]);
    int[] groups = new int[groupNums.size()];
    for (int i = 0; i < groupNums.size(); i++) {
  groups[i] = ((Integer) groupNums.get(i)).intValue();
    }
    String[] regex = (String[]) res.toArray(new String[0]);
    RegexpsFieldExtractor rfe =
  new RegexpsFieldExtractor(concepts, fields, groups, regex);
    System.err.println("Serializing extractor to "+args[1]);
    rfe.storeExtractor(new File(args[1]));
    System.err.println("Done");
} catch(Exception e) {
    System.err.println("An error occured while trying to create the RegexpsFieldExtractor:");
    e.printStackTrace();
}
  }
*/
}
