package edu.stanford.nlp.parser.tools;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Counts the rule branching factor (and other rule statistics) in a treebank.
 *
 * @author Spence Green
 *
 */
public class RuleBranchingFactor {

  private static String treeToRuleString(Tree tree) {
    StringBuilder sb = new StringBuilder();
    sb.append(tree.value()).append(":").append(tree.firstChild().value());
    for (int i = 1; i < tree.numChildren(); ++i) {
      Tree kid = tree.children()[i];
      sb.append("-").append(kid.value());
    }
    return sb.toString();
  }


  private static final int minArgs = 1;
  private static final String usage;
  static {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    sb.append(String.format("Usage: java %s [OPTS] tree_file%s%s",CountTrees.class.getName(),nl,nl));
    sb.append("Options:\n");
    sb.append("  -l lang    : Select language settings from " + Language.langList).append(nl);
    sb.append("  -e enc     : Encoding.").append(nl);
    usage = sb.toString();
  }

  public static final Map<String,Integer> optionArgDefinitions = Generics.newHashMap();
  static {
    optionArgDefinitions.put("l", 1);
    optionArgDefinitions.put("e", 1);
  }

  public static void main(String[] args) {
    if(args.length < minArgs) {
      System.out.println(usage);
      System.exit(-1);
    }

    // Process command-line options
    Properties options = StringUtils.argsToProperties(args, optionArgDefinitions);
    String fileName = options.getProperty("");
    if (fileName == null || fileName.equals("")) {
      System.out.println(usage);
      System.exit(-1);
    }
    Language language = PropertiesUtils.get(options, "l", Language.English, Language.class);
    TreebankLangParserParams tlpp = language.params;
    String encoding = options.getProperty("e", "UTF-8");
    tlpp.setInputEncoding(encoding);
    tlpp.setOutputEncoding(encoding);

    DiskTreebank tb = tlpp.diskTreebank();
    tb.loadPath(fileName);

    // Statistics
    Counter<String> binaryRuleTypes = new ClassicCounter<>(20000);
    List<Integer> branchingFactors = new ArrayList<>(20000);
    int nTrees = 0;
    int nUnaryRules = 0;
    int nBinaryRules = 0;
    int binaryBranchingFactors = 0;

    // Read the treebank
    PrintWriter pw = tlpp.pw();
    for (Tree tree : tb) {
      if (tree.value().equals("ROOT")) {
        tree = tree.firstChild();
      }
      ++nTrees;
      for (Tree subTree : tree) {
        if (subTree.isPhrasal()) {
          if (subTree.numChildren() > 1) {
            ++nBinaryRules;
            branchingFactors.add(subTree.numChildren());
            binaryBranchingFactors += subTree.numChildren();
            binaryRuleTypes.incrementCount(treeToRuleString(subTree));
          } else {
            ++nUnaryRules;
          }
        }
      }
    }
    double mean = (double) binaryBranchingFactors / (double) nBinaryRules;
    System.out.printf("#trees:\t%d%n", nTrees);
    System.out.printf("#binary:\t%d%n", nBinaryRules);
    System.out.printf("#binary types:\t%d%n", binaryRuleTypes.keySet().size());
    System.out.printf("mean branching:\t%.4f%n", mean);
    System.out.printf("stddev branching:\t%.4f%n", standardDeviation(branchingFactors, mean));
    System.out.printf("rule entropy:\t%.5f%n", Counters.entropy(binaryRuleTypes));
    System.out.printf("#unaries:\t%d%n", nUnaryRules);
  }

  private static double standardDeviation(List<Integer> branchingFactors, double mean) {
    double variance = 0.0;
    for (int i : branchingFactors) {
      variance += (i-mean)*(i-mean);
    }
    return Math.sqrt(variance / (branchingFactors.size()-1));
  }

}
