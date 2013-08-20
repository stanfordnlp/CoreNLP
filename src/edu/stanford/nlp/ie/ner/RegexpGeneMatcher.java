package edu.stanford.nlp.ie.ner;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches genes by building regular expressions from the synonyms that allow
 * optional parentheses, British spelling variations, optional dashes, etc.
 * <p/>
 * <p>Relevant properties:
 * <table>
 * <tr><td>Property</td><td>Default</td></tr>
 * <tr><td>caseSensitive</td><td>true</td></tr>
 * <tr><td>substringMatch</td><td>true</td></tr>
 * <tr><td>minSubLength</td><td>3</td></tr>
 * </table>
 *
 * @author Huy Nguyen (<a href="mailto:htnguyen@cs.stanford.edu">htnguyen@cs.stanford.edu</a>)
 */
public class RegexpGeneMatcher extends AbstractGeneMatcher {
  private HashMap<String, Pattern> regexpMap;
  private boolean caseSensitive = true;
  private boolean substringMatch = true;
  private int minSubLength = 3;

  @Override
  public void setProperties(Properties props) {
    super.setProperties(props);
    // case sensitive unless explicitly stated otherwise
    caseSensitive = !"false".equals(props.getProperty("caseSensitive"));
    substringMatch = !"false".equals(props.getProperty("substringMatch"));
    if (props.containsKey("minSubLength")) {
      minSubLength = Integer.parseInt(props.getProperty("minSubLength"));
    }

  }

  /**
   * Initializes the regular expressions for the gene synonyms.
   */
  @Override
  public void setSynonymMap(BioCreativeSynonymMap map) {
    regexpMap = new HashMap<String, Pattern>();
    super.setSynonymMap(map);
    for (Iterator<String> iter = map.getSynonyms().iterator(); iter.hasNext();) {
      String synonym = iter.next();
      regexpMap.put(synonym, makePattern(synonym));
    }
  }

  /**
   * Converts a gene String into a Pattern that can match several variations of that String.
   */
  private Pattern makePattern(String synonym) {
    //System.err.println("synonym: " + synonym);
    String pattern = synonym;
    // replace question marks with escaped question marks
    if (pattern.indexOf('?') != -1) {
      pattern = pattern.replaceAll("\\?", "\\\\?");
    }
    // make all spaces optional
    pattern = pattern.replaceAll(" ", " ?");
    // replace brackets with optional escaped brackets
    if (pattern.indexOf('[') != -1) {
      pattern = pattern.replaceAll("\\[", "[\\\\[ ]?");
    }
    if (pattern.indexOf(']') != -1) {
      pattern = pattern.replaceAll("\\](?=\\b|[^?])", "[\\\\] ]?");
    }
    // make slashes optional
    if (pattern.indexOf('\\') != -1) {
      pattern = pattern.replaceAll("\\\\(?=\\b|[^\\?\\[\\]])", "[\\\\ ]?");
    }
    // replace + with escaped +
    if (pattern.indexOf('+') != -1) {
      pattern = pattern.replaceAll("\\+", "\\\\+");
    }
    // replace * with escaped *
    if (pattern.indexOf('*') != -1) {
      pattern = pattern.replaceAll("\\*", "\\\\*");
    }
    if (pattern.indexOf('/') != -1) {
      pattern = pattern.replaceAll("/", "[/ ]?");
    }
    // make colons optional
    if (pattern.indexOf(':') != -1) {
      pattern = pattern.replaceAll(":", "[: ]?");
    }
    // replace periods with optional escaped periods
    if (pattern.indexOf('.') != -1) {
      pattern = pattern.replaceAll("\\.", "\\\\.?");
    }
    // make parentheses optional
    if (pattern.indexOf('(') != -1) {
      pattern = pattern.replaceAll("\\(", "[( ]?");
    }
    if (pattern.indexOf(')') != -1) {
      pattern = pattern.replaceAll("\\)", "[) ]?");
    }
    // make -'s optional
    if (pattern.indexOf('-') != -1) {
      pattern = pattern.replaceAll("-", "[- ]?");
    }
    // allow British spellings
    if (pattern.indexOf("aemia") != -1) {
      pattern = pattern.replaceAll("aemia", "a?emia");
    }
    if (pattern.indexOf("our") != -1) {
      pattern = pattern.replaceAll("our", "ou?r");
    }
    // wrap pattern in boundary tags
    pattern = "\\b" + pattern + "\\b";
    //System.err.println("pattern: " + pattern);
    // allow case insensitive matching
    if (caseSensitive) {
      return Pattern.compile(pattern);
    } else {
      return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
    }
  }

  /**
   * Returns all IDs where the regexp based on the gene is found within
   * a synonym or vice versa.
   */
  public Set<String> getIDs(String gene) {
    HashSet<String> ids = new HashSet<String>();
    if (gene.length() == 0) {
      return ids;
    }

    Pattern genePattern = makePattern(gene);
    double maxRatio = 0.0; // return the closest match

    for (Iterator<String> iter = regexpMap.keySet().iterator(); iter.hasNext();) {
      String synonym = iter.next();
      Pattern pattern = regexpMap.get(synonym);
      Matcher m = pattern.matcher(gene);
      boolean matches = false;
      // try to find synonym pattern within gene
      if (substringMatch) {
        matches = m.find() && isValidMatch(gene, m);
      } else {
        matches = m.matches();
      }
      if (matches) {
        //System.err.println(gene + " matched forward " + synonym);
        double ratio = (m.end() - m.start()) / (double) gene.length();
        if (ratio > maxRatio) {
          maxRatio = ratio;
          ids = new HashSet<String>();
          ids.addAll(map.getIDs(synonym));
        } else if (ratio == maxRatio && ratio > 0.0) {
          ids.addAll(map.getIDs(synonym));
        }
      }
      // try to find gene pattern within synonym
      /*else {
          m = genePattern.matcher(synonym);
          if (substringMatch)
              matches = m.find();
          else
              matches = m.matches();
          if (matches) {
              System.err.println(synonym + " matched backward " + gene);
              double ratio = (m.end() - m.start()) / (double) synonym.length();
              if (ratio > maxRatio) {
                  maxRatio = ratio;
                  ids = new HashSet();
                  ids.addAll(map.getIDs(synonym));
              } else if (ratio == maxRatio && ratio > 0.0)
                  ids.addAll(map.getIDs(synonym));
          }
}*/
    }
    return ids;
  }

  /**
   * If the expression to match is shorter than minSubLength,
   * it must match the regular expression exactly.
   */
  private boolean isValidMatch(String gene, Matcher m) {
    if (gene.length() < minSubLength) {
      return (m.end() - m.start() == gene.length());
    }
    return true;
  }

  /**
   * For internal debugging purposes only.
   */
  public static void main(String[] args) {
    BioCreativeSynonymMap map = new BioCreativeSynonymMap();
    File file = new File(args[0]);
    map.load(file);
    RegexpGeneMatcher matcher = new RegexpGeneMatcher();
    System.err.println(matcher.makePattern("+: ").pattern());
    //matcher.setSynonymMap(map);
    //map.printCollection(matcher.getIDs("Polycomb"));
  }
}
