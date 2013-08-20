package edu.stanford.nlp.parser.eval;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.international.Languages;
import edu.stanford.nlp.international.Languages.Language;
import edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.Triple;

/**
 * Loosely-based on the variation nuclei method of Dickinson (2005).
 * 
 * @author Spence Green
 *
 */
public class DominanceEntropy extends DickinsonNucleusFinder {

  protected static int minArgs = 1;
  protected static StringBuilder usage = new StringBuilder();
  static {
    usage.append(String.format("Usage: java %s [OPTS] tree_file\n\n",DominanceEntropy.class.getName()));
    usage.append("Options:\n");
    usage.append("  -v         : Verbose mode.\n");
    usage.append("  -l lang    : Select language settings from " + Languages.listOfLanguages() + "\n");
    usage.append("  -u         : Collapse unaries\n");
  }

  /**
   * Evaluate entropy of similar ngrams with multiple constituent labels.
   * 
   * @param args
   */
  public static void main(String[] args) {

    if(args.length < minArgs) {
      System.out.println(usage.toString());
      System.exit(-1);
    }

    TreebankLangParserParams tlpp = new EnglishTreebankParserParams();
    boolean VERBOSE = false;
    boolean collapseUnaries = false;

    File treeFile = null;

    for(int i = 0; i < args.length; i++) {

      if(args[i].startsWith("-")) {

        if(args[i].equals("-l")) {
          Language lang = Language.valueOf(args[++i].trim());
          tlpp = Languages.getLanguageParams(lang);

        } else if(args[i].equals("-v")) {
          VERBOSE = true;

        } else if(args[i].equals("-u")) {
          collapseUnaries = true;

        } else {
          System.out.println(usage.toString());
          System.exit(-1);
        }

      } else {
        //Required parameters
        treeFile = new File(args[i]);
        break;
      }
    }

    final PrintWriter pwOut = tlpp.pw();

    final Treebank tb = tlpp.diskTreebank();
    tb.loadPath(treeFile);
    if(VERBOSE) pwOut.println(tb.textualSummary());

    pwOut.println("Reading trees:");

    final Map<String,Counter<String>> precMap = new HashMap<String,Counter<String>>();
    int numTrees = 0;
    for(final Tree tree : tb) {
      if(collapseUnaries) collapseUnaries(tree);
      for(Tree subTree : tree) { //Includes t itself
        if(!subTree.isPhrasal()) continue;
        String precRelation = getPrecRelation(subTree.getChildrenAsList());
        if(!precMap.containsKey(precRelation))
          precMap.put(precRelation, new ClassicCounter<String>());
        precMap.get(precRelation).incrementCount(subTree.value());
      }

      numTrees++;
      if((numTrees % 200) == 0) {
        System.out.print(".");
      } if((numTrees % 4000) == 0) {
        System.out.println();
      }
    }

    //Compute the "variation nuclei" with multiple labels
    final Set<Triple<Double,String,Set<String>>> nucleiSet = new HashSet<Triple<Double,String,Set<String>>>();
    double entropies = 0.0;
    for(String pRelation : precMap.keySet()) {
      Counter<String> cnt = precMap.get(pRelation);
      if(cnt.keySet().size() > 1) { //Definition of a nucleus from Dickinson (2005)
        double entropy = Counters.entropy(cnt);
        entropies += entropy;
        nucleiSet.add(new Triple<Double,String,Set<String>>(entropy,pRelation,cnt.keySet()));
      }
    }

    //Display final statistics
    pwOut.println("\n\n=======================================================================");
    pwOut.println("Variation nuclei:     " + nucleiSet.size());
    pwOut.println("Macro Avg. entropy:   " + entropies / (double) nucleiSet.size());
    pwOut.println("\nEntropy\t\tRelation\tDominating Nodes");
    for(Triple<Double,String,Set<String>> nucleus : nucleiSet)
      pwOut.printf("%f\t%s\t\t%s\n", nucleus.first(), nucleus.second(), nucleus.third().toString());
    pwOut.println();
  }

  private static String getPrecRelation(final List<Tree> childrenAsList) {
    final StringBuilder sb = new StringBuilder();
    for(final Tree kid : childrenAsList) {
      sb.append(kid.value());
      sb.append(" ");
    }
    return sb.toString().trim();
  }

}
