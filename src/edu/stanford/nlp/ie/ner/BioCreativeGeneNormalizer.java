package edu.stanford.nlp.ie.ner;

import java.io.*;
import java.util.*;

/**
 * Maps genes recognized during the NER phase to a list of a canonical geneID IDs.
 * The geneID IDs are provided in the synonym list.
 * <p/>
 * Usage: BioCreativeGeneNormalizer &lt;synonymFile&gt; &lt;testFile&gt; [-train &lt;trainFile&gt;] [-prop &lt;properties file&gt;]
 * <p>Description of properties:</p>
 * <table>
 * <tr><td>synonymFile</td><td>File containing the synonym lists (geneID database ID followed by tab-delimited synonyms for that ID)</td></tr>
 * <tr><td>matchers</td><td>List of semi-colon delimited {@link GeneMatcher} classes.</td></tr>
 * <tr><td>disambiguate</td><td>("true", "false" or "accept") Whether or not to resolve ambiguities</td></tr>
 * <tr><td>trainFile</td><td>Training file used to gather cooccurrence stats for disambiguating (each line starts with a doc ID, followed by a tab, followed by a geneID ID.</td></tr>
 * </table>
 *
 * @author Huy Nguyen (<a href="mailto:htnguyen@cs.stanford.edu">htnguyen@cs.stanford.edu</a>)
 */
public class BioCreativeGeneNormalizer {
  // disambiguation constants
  public static final int DA_REJECT = 0;
  public static final int DA_DISAMBIGUATE = 1;
  public static final int DA_ACCEPT = 2;

  // properties
  public static final String disambiguateProp = "disambiguate";
  public static final String trainFileProp = "trainFile";
  public static final String matchersProp = "matchers";

  public static CooccurrenceModel cm = null;

  /**
   * Usage: BioCreativeGeneNormalizer &lt;synonymFile&gt; &lt;testFile&gt; [-train &lt;trainFile&gt;] [-prop &lt;properties file&gt;]
   */
  public static void main(String[] args) {
    if (args.length < 2 || args.length > 6) {
      System.err.println("Usage: BioCreativeGeneNormalizer <synonymFile> <testFile> [-train <trainFile>] [-prop <properties file>]");
      System.exit(1);
    }
    int disambiguate = DA_REJECT;
    String synonymFile = args[0];
    String testFile = args[1];
    String trainFile = null;
    Properties props = defaultProperties();

    for (int index = 2; index < args.length; index++) {
      // allow testFile to be specified as command-line arg
      if (args[index].equalsIgnoreCase("-prop") && args.length > index + 1) {
        index++;
        try {
          Properties newProps = new Properties();
          newProps.load(new BufferedInputStream(new FileInputStream(args[index])));
          props.putAll(newProps);
        } catch (Exception e) {
          e.printStackTrace();
        }
        continue;
      } else if (args[index].equalsIgnoreCase("-train") && args.length > index + 1) {
        index++;
        trainFile = args[index];
        continue;
      } else {
        System.err.println("Unrecognized command-line argument: " + args[index]);
        System.exit(1);
      }
    }

    BioCreativeSynonymMap synMap = new BioCreativeSynonymMap();
    synMap.load(new File(synonymFile));

    if ("true".equals(props.getProperty(disambiguateProp))) {
      disambiguate = DA_DISAMBIGUATE;
    } else if ("accept".equals(props.getProperty(disambiguateProp))) {
      disambiguate = DA_ACCEPT;
    }

    if (disambiguate == DA_DISAMBIGUATE) {
      if (props.containsKey(trainFileProp)) {
        trainFile = props.getProperty(trainFileProp);
      }
      if (trainFile != null) {
        cm = new CooccurrenceModel();
        System.err.println("Initializing co-occurrence matrix...");
        cm.initialize(trainFile);
        System.err.println("Done.");
      } else {
        System.err.println("trainFile must be specified if disambiguous is specified");
        System.exit(1);
      }
    }

    // create and initialize all the matchers
    GeneMatcher[] matchers;
    // defaults to the regular expression matcher
    if (!props.containsKey(matchersProp)) {
      matchers = new GeneMatcher[]{new RegexpGeneMatcher()};
    } else {
      String[] matcherClasses = props.getProperty(matchersProp).split(";");
      matchers = new GeneMatcher[matcherClasses.length];
      for (int i = 0; i < matcherClasses.length; i++) {
        try {
          matchers[i] = (GeneMatcher) Class.forName(matcherClasses[i]).newInstance();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
    for (int i = 0; i < matchers.length; i++) {
      matchers[i].setProperties(props);
      matchers[i].setSynonymMap(synMap);
    }

    String line;
    String curFile = null;
    Set<GeneEvidencePair> genes = new HashSet<GeneEvidencePair>();
    //List ambiguous = new ArrayList();
    Set<GeneIDPair> ambiguous = new HashSet<GeneIDPair>();
    Set<String> ids = null;
    try {
      BufferedReader br = new BufferedReader(new FileReader(testFile));
      while ((line = br.readLine()) != null) {
        String[] fields = line.split("\\|");
        if (fields.length == 3) {
          if (curFile != null && !curFile.equals(fields[0])) {
            if (disambiguate == DA_DISAMBIGUATE && ambiguous.size() > 0) {
              disambiguate(genes, ambiguous);
            }
            printGenes(genes, curFile);
            genes = new HashSet<GeneEvidencePair>();
            //ambiguous = new ArrayList();
            ambiguous = new HashSet<GeneIDPair>();
          }
          curFile = fields[0];

          for (int i = 0; i < matchers.length; i++) {
            ids = matchers[i].getIDs(fields[2]);
            // break after first matcher finds something
            if (ids.size() > 0) {
              break;
            }
          }
          if (ids.size() == 0) {
            System.err.println("NOT FOUND:" + fields[0] + ":" + fields[2]);
          } else {
            if (ids.size() == 1) {
              String gene = ids.iterator().next();
              genes.add(new GeneEvidencePair(gene, fields[2]));
            } else if (disambiguate == DA_ACCEPT) {
              // add all the gene ids possible for the given match
              for (Iterator<String> iter = ids.iterator(); iter.hasNext();) {
                genes.add(new GeneEvidencePair(iter.next(), fields[2]));
              }
            } else {
              if (disambiguate == DA_DISAMBIGUATE) {
                ambiguous.add(new GeneIDPair(fields[2], ids));
              } else {
                System.err.println("AMBIGUOUS:" + fields[0] + ":" + fields[2] + ":" + ids.size());
              }
            }
          }
        }
      }
      if (genes.size() > 0) {
        printGenes(genes, curFile);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Returns the default properties.
   */
  private static Properties defaultProperties() {
    Properties props = new Properties();
    props.setProperty(disambiguateProp, "true");
    props.setProperty("caseSensitive", "false");
    return props;
  }

  /**
   * Enumerates all possible assignments for the ambiguities, and returns
   * the one with the highest summed pairwise cooccurrence score.  Also uses
   * the uniquely identified genes in <tt>genes</tt> to disambiguate.
   */
  static void disambiguate(Set<GeneEvidencePair> genes, Set<GeneIDPair> ambiguities) {
    Set<String> fixed = new HashSet<String>();
    //for(Iterator iter = ambiguities.iterator(); iter.hasNext(); )
    //    System.err.println(iter.next());
    for (Iterator<GeneEvidencePair> iter = genes.iterator(); iter.hasNext();) {
      fixed.add(iter.next().geneID);
    }

    // disambiguate based on co-occurrence with unambiguous genes
    for (Iterator<GeneIDPair> iter = ambiguities.iterator(); iter.hasNext();) {
      GeneIDPair gene = iter.next();
      double bestScore = 0.0;
      double bestSelfScore = 0.0;
      String bestId = null;

      for (Iterator iter2 = gene.ids.iterator(); iter2.hasNext();) {
        String ambiguous = (String) iter2.next();

        double score = 0.0;
        for (Iterator<String> iter3 = fixed.iterator(); iter3.hasNext();) {
          String unambiguous = iter3.next();
          // if an unambiguous gene id already accounts for this gene
          // then ignore this gene
          if (fixed.contains(ambiguous)) {
            bestId = null;
            break;
          }
          score += cm.get(ambiguous, unambiguous);
        }
        if (score > bestScore) {
          bestScore = score;
          bestId = ambiguous;
          bestSelfScore = cm.get(bestId, bestId);
        } else if (score > 0.0 && score == bestScore) {
          // if co-occurrence scores are equal, backoff to maximum occurrence in data
          double selfScore = cm.get(ambiguous, ambiguous);
          if (selfScore > bestSelfScore) {
            bestSelfScore = selfScore;
            bestId = ambiguous;
          } else if (selfScore == bestSelfScore) {
            // if the tie still can't be broken, just ignore this gene
            bestId = null;
          }
        }
      }
      if (bestId != null) {
        genes.add(new GeneEvidencePair(bestId, gene.gene));
        //fixed.add(bestId);
      }
    }

    /*IDSelectionPath path = new IDSelectionPath();
    path.setFixed(fixed);
    disambiguate(ambiguities, path, 0);
    List best = path.best();
    if (best == null) return;
    //System.err.print("disambiguated: ");
    //BioCreativeSynonymMap.printCollection(best);
    for (Iterator iter1 = best.iterator(), iter2 = ambiguities.iterator(); iter1.hasNext();)
        genes.add(new GeneEvidencePair((String) iter1.next(), ((GeneIDPair) iter2.next()).gene));*/
  }

  static void disambiguate(List<GeneIDPair> ambiguities, IDSelectionPath path, int depth) {
    if (depth == ambiguities.size()) {
      path.terminate();
      return;
    }
    GeneIDPair curGene = ambiguities.get(depth);
    for (Iterator<String> iter = curGene.ids.iterator(); iter.hasNext();) {
      String id = iter.next();
      path.push(id);
      disambiguate(ambiguities, path, depth + 1);
      path.pop();
    }
  }

  /**
   * Prints the genes for the current file in sorted order
   */
  @SuppressWarnings("unchecked")
  private static void printGenes(Set<GeneEvidencePair> genes, String curFile) {
    if (curFile.startsWith("@@")) {
      curFile = curFile.substring(2);
    }
    List<GeneEvidencePair> sorted = new ArrayList<GeneEvidencePair>(genes);
    Collections.<GeneEvidencePair>sort(sorted);
    for (Iterator<GeneEvidencePair> iter = sorted.iterator(); iter.hasNext();) {
      System.out.println(curFile + iter.next());
    }
  }

  /**
   * Used to keep track of the IDs we've currently selected, and the score of the selected set.
   */
  private static class IDSelectionPath {
    double score;
    double maxScore;
    List<Double> scores;
    List<String> ids;
    List<ArrayList<String>> best;
    List mergedBest;
    Set fixed;

    public IDSelectionPath() {
      scores = new ArrayList<Double>();
      maxScore = Double.NEGATIVE_INFINITY;
      ids = new ArrayList<String>();
    }

    public void push(String id) {
      double newScore = 0.0;
      // compare to other disambiguation decisions
      for (Iterator<String> iter = ids.iterator(); iter.hasNext();) {
        String geneID = iter.next();
        newScore += cm.get(id, geneID);
      }
      // compare to fixed values
      for (Iterator iter = fixed.iterator(); iter.hasNext();) {
        String geneID = (String) iter.next();
        newScore += cm.get(id, geneID);
      }
      score += newScore;
      scores.add(new Double(newScore));
      ids.add(id);
    }

    public void pop() {
      Double lastScore = scores.remove(scores.size() - 1);
      score -= lastScore.doubleValue();
      ids.remove(ids.size() - 1);
    }

    /**
     * Terminates the path.
     */
    public void terminate() {
      if (score > maxScore) {
        maxScore = score;
        best = new ArrayList<ArrayList<String>>();
        best.add(new ArrayList<String>(ids));
      } else if (score == maxScore) {
        best.add(new ArrayList<String>(ids));
      }
    }

    public List best() {
      if (best.size() == 1) {
        return best.get(0);
      } else {
        System.err.println("STILL AMBIGUOUS");
        return null;
        /*if (mergedBest != null) return mergedBest;
        int numElems = ((List) best.get(0)).size();
        mergedBest = new ArrayList(numElems);
        for (int i=0; i<numElems; i++) {
            String curBest;
            double max = 0.0;
            for (int j=0; j<best.size(); j++) {
                List assignment = (List) best.get(j);
                if ()
            }
        }
        return mergedBest;*/
      }
    }

    public void setFixed(Set fixed) {
      this.fixed = fixed;
    }
  }

  /**
   * Groups a gene string with set of candidate ids.
   */
  protected static class GeneIDPair {
    String gene;
    Set<String> ids;

    @Override
    public int hashCode() {
      return gene.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return gene.equals(((GeneIDPair) obj).gene);
    }

    public GeneIDPair(String gene, Set<String> ids) {
      this.gene = gene;
      this.ids = ids;
    }

    @Override
    public String toString() {
      StringBuffer sb = new StringBuffer();
      sb.append(gene);
      sb.append(':');
      for (Iterator<String> iter = ids.iterator(); iter.hasNext();) {
        sb.append(iter.next());
        sb.append(' ');
      }
      return sb.toString();
    }
  }

  /**
   * Groups a gene id with its supporting evidence.
   */
  protected static class GeneEvidencePair implements Comparable {
    String geneID;
    String evidence;

    public GeneEvidencePair(String geneID, String evidence) {
      this.geneID = geneID;
      this.evidence = evidence;
    }

    // only need one instance of each ID in each result set
    @Override
    public boolean equals(Object o) {
      return geneID.equals(((GeneEvidencePair) o).geneID);
    }

    @Override
    public int hashCode() {
      return geneID.hashCode();
    }

    @Override
    public String toString() {
      StringBuffer sb = new StringBuffer();
      sb.append('\t');
      sb.append(geneID);
      sb.append('\t');
      sb.append(evidence);
      return sb.toString();
    }

    public int compareTo(Object o) {
      GeneEvidencePair other = (GeneEvidencePair) o;
      return geneID.compareTo(other.geneID);
    }
  }

}
