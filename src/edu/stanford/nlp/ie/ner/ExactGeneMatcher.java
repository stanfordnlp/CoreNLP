package edu.stanford.nlp.ie.ner;

import java.io.File;
import java.util.*;

/**
 * Determines ID of gene by doing exact match on synonyms.
 * <p/>
 * <p>Relevant properties:
 * <table>
 * <tr><td>Property</td><td>Default</td></tr>
 * <tr><td>caseSensitive</td><td>false</td></tr>
 * <tr><td>substringMatch</td><td>false</td></tr>
 * </table>
 *
 * @author Huy Nguyen (<a href="mailto:htnguyen@cs.stanford.edu">htnguyen@cs.stanford.edu</a>)
 */
public class ExactGeneMatcher extends AbstractGeneMatcher {
  private boolean caseSensitive = false;
  private boolean substringMatch = false;
  private int minSubLength = 3;
  private HashMap<String, Set<String>> lcMap;

  /**
   * Builds an all-lower case version of the map as well.
   */
  @Override
  public void setSynonymMap(BioCreativeSynonymMap map) {
    lcMap = new HashMap<String, Set<String>>();
    super.setSynonymMap(map);
    for (Iterator<String> iter = map.getSynonyms().iterator(); iter.hasNext();) {
      String synonym = iter.next();
      lcMap.put(synonym.toLowerCase(), map.getIDs(synonym));
    }
  }

  public Set<String> getIDs(String gene) {
    Set<String> ids = null;
    if (substringMatch) {
      // HN: TODO: use extent of match to disambiguate
      ids = new HashSet<String>();
      Set<String> synonyms;
      if (caseSensitive) {
        synonyms = map.getSynonyms();
      } else {
        synonyms = lcMap.keySet();
        gene = gene.toLowerCase();
      }

      for (Iterator<String> iter = synonyms.iterator(); iter.hasNext();) {
        String synonym = iter.next();
        if (gene.indexOf(synonym) != -1) {
          if (caseSensitive) {
            ids.addAll(map.getIDs(synonym));
          } else {
            ids.addAll(lcMap.get(synonym));
          }
        } else if (synonym.indexOf(gene) != -1 && isValidMatch(synonym, gene)) {
          if (caseSensitive) {
            ids.addAll(map.getIDs(synonym));
          } else {
            ids.addAll(lcMap.get(synonym));
          }
        }
      }

    } else {
      if (caseSensitive) {
        ids = map.getIDs(gene);
      } else {
        ids = lcMap.get(gene.toLowerCase());
      }
    }
    if (ids == null) {
      return new HashSet<String>();
    } else {
      return ids;
    }
  }

  /**
   * If either string is shorter than minSubLength, they have to match exactly.
   */
  private boolean isValidMatch(String matchTarget, String string) {
    if (matchTarget.length() < minSubLength || string.length() < minSubLength) {
      return matchTarget.equals(string);
    }
    return true;
  }

  /**
   * Accepts "caseSensitive=boolean" property.
   */
  @Override
  public void setProperties(Properties props) {
    super.setProperties(props);
    // use case insensitive unless explicitly specified
    caseSensitive = "true".equals(props.getProperty("caseSensitive"));
    substringMatch = "true".equals(props.getProperty("substringMatch"));
    if (props.containsKey("minSubLength")) {
      minSubLength = Integer.parseInt(props.getProperty("minSubLength"));
    }
  }

  /**
   * For internal debugging purposes only.
   */
  public static void main(String[] args) {
    BioCreativeSynonymMap map = new BioCreativeSynonymMap();
    File file = new File(args[0]);
    map.load(file);
    ExactGeneMatcher matcher = new ExactGeneMatcher();
    matcher.setSynonymMap(map);
    Properties props = new Properties();
    props.setProperty("caseSensitive", "false");
    matcher.setProperties(props);
    BioCreativeSynonymMap.printCollection(matcher.getIDs("dpse\\His2B"));
  }
}
