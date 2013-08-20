package edu.stanford.nlp.ie.ner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

/**
 * Map from gene synonym to Set of corresponding IDs in the gene database.
 *
 * @author Huy Nguyen (<a href="mailto:htnguyen@cs.stanford.edu">htnguyen@cs.stanford.edu</a>)
 */
public class BioCreativeSynonymMap {
  private HashSet<String> ids;
  private HashMap<String, Set<String>> synonyms;
  //public static final double MAX_EDIT_DISTANCE = 3.0;

  public BioCreativeSynonymMap() {
    ids = new HashSet<String>();
    synonyms = new HashMap<String, Set<String>>();
  }

  /**
   * Returns the Set of all known IDs in the gene database.
   */
  public Set<String> getIDs() {
    return ids;
  }

  /**
   * Returns the Set of all known IDs for the given gene.
   */
  public Set<String> getIDs(String gene) {
    return synonyms.get(gene);
  }

  public Set<String> getSynonyms() {
    return synonyms.keySet();
  }

  /** Returns true if the given gene matches a known synonym exactly. */
  /*public boolean contains(String gene) {
      if (synonyms.keySet().contains(gene.toLowerCase())) return true;
      return false;
  }*/

  /**
   * Returns the Set of IDs corresponding to the closest matching synonym(s).
   * The closest match is determined by {@link EditDistance#score} or null if no synonym can be found within
   * {@link #MAX_EDIT_DISTANCE}. If the gene matches a known synonym exactly,
   * then we can use the underlying map directly without computing edit distance.
   * Returns null if closest match has edit distance greater than {@link #MAX_EDIT_DISTANCE}.
   * Check for this by seeing whether {@link #contains} returns true.
   */
  /*public Set getIDs(String gene) {
      String lc = gene.toLowerCase();
      if (contains(lc)) return (HashSet) synonyms.get(lc);
      EditDistance ed = new EditDistance();
      HashSet bestMatches = new HashSet();
      double minDist = Integer.MAX_VALUE;
      for (Iterator iter = synonyms.keySet().iterator(); iter.hasNext();) {
          String synonym = (String) iter.next();
          // skip strings starting with a different character
          if (lc.charAt(0) != synonym.charAt(0)) continue;
          double dist = ed.score(Characters.asCharacterArray(lc), Characters.asCharacterArray(synonym));
          if (dist <= minDist) {
              if (dist < minDist) {
                  bestMatches = new HashSet();
                  minDist = dist;
              }
              bestMatches.addAll((HashSet) synonyms.get(synonym));
          }
      }
      if (minDist <= MAX_EDIT_DISTANCE) return bestMatches;
      return null;
  }*/

  /**
   * Loads a synonym list from a file.  The file should be in
   * the following format:
   * <pre>GENE_DB_ID\tsynonym1\tsynonm2\t....</pre>
   */
  public void load(File file) {
    try {
      BufferedReader br = new BufferedReader(new FileReader(file));
      String line;
      while ((line = br.readLine()) != null) {
        String[] columns = line.split("\t");
        if (columns.length == 0) {
          break;
        }
        String id = columns[0];
        ids.add(id);
        for (int i = 1; i < columns.length; i++) {
          String synonym = columns[i].trim();//.toLowerCase();
          if (synonym.length() == 0) {
            continue;
          }
          Set<String> idSet;
          if (synonyms.containsKey(synonym)) {
            idSet = synonyms.get(synonym);
          } else {
            idSet = new HashSet<String>();
          }
          idSet.add(id);
          synonyms.put(synonym, idSet);
        }
      }
    } catch (Exception e) {
      //To change body of catch statement use Options | File Templates.
      throw new RuntimeException(e);
    }
  }

  /**
   * Prints the ambiguous gene IDs.
   */
  private void printAmbiguous() {
    int count = 0;
    /*for (Iterator iter = synonyms.keySet().iterator(); iter.hasNext();) {
        String id = (String) iter.next();
        Set synSet = (Set) synonyms.get(id);
        if (synSet.size() > 1) {
            System.out.print(id + ": ");
            printCollection(synSet);
            count++;
        }
    }*/
    RegexpGeneMatcher matcher = new RegexpGeneMatcher();
    matcher.setSynonymMap(this);
    Properties props = new Properties();
    props.setProperty("substringMatch", "false");
    props.setProperty("caseSensitive", "false");
    matcher.setProperties(props);
    for (Iterator iter = synonyms.keySet().iterator(); iter.hasNext();) {
      String id = (String) iter.next();
      Set synSet = matcher.getIDs(id);
      if (synSet.size() > 1) {
        System.out.print(id + ": ");
        printCollection(synSet);
        count++;
      }
    }
    System.out.println("Total number of ambiguous ids: " + count);
    System.out.println("Total number of synonyms: " + synonyms.keySet().size());
  }

  /**
   * For internal debugging purposes only
   */
  public static void main(String[] args) {
    BioCreativeSynonymMap map = new BioCreativeSynonymMap();
    File file = new File(args[0]);
    map.load(file);
    map.printAmbiguous();
    //printCollection(map.getIDs("45SRNA"));
  }

  /**
   * Utility method for printing a Collection by delmiting its members with spaces
   */
  public static void printCollection(Collection c) {
    if (c == null) {
      return;
    }
    for (Iterator iter = c.iterator(); iter.hasNext();) {
      System.out.print(iter.next());
      System.out.print(" ");
    }
    System.out.println();
  }
}
