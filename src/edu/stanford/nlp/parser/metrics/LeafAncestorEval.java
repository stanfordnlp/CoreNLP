package edu.stanford.nlp.parser.metrics;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Stack;
import java.util.TreeMap;

import edu.stanford.nlp.international.Languages;
import edu.stanford.nlp.international.Languages.Language;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasIndex;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.StringUtils;

/**
 * Implementation of the Leaf Ancestor metric first described by Sampson and Babarczy (2003) and
 * later analyzed more completely by Clegg and Shepherd (2005).
 * <p>
 * This implementation assumes that the guess/gold input files are of equal length, and have one tree per
 * line.
 * <p> 
 * TODO (spenceg): This implementation doesn't insert the "boundary symbols" as described by both
 * Sampson and Clegg. Need to add those.
 *
 * @author Spence Green
 *
 */
public class LeafAncestorEval {

  private final String name;

  private static final boolean DEBUG = false;

  //Corpus level (macro-averaged)
  private double sentAvg = 0.0;
  private double sentNum = 0.0;
  private int sentExact = 0;

  //Sentence level (micro-averaged)
  private double corpusAvg = 0.0;
  private double corpusNum = 0.0;

  //Category level
  private final Map<List<CoreLabel>,Double> catAvg;
  private final Map<List<CoreLabel>,Double> catNum;

  public LeafAncestorEval(String str) {
    this.name = str;

    catAvg = new HashMap<List<CoreLabel>,Double>();
    catNum = new HashMap<List<CoreLabel>,Double>();
  }

  /**
   * Depth-first (post-order) search through the tree, recording the stack state as the
   * lineage every time a terminal is reached.
   * 
   * This implementation uses the Index annotation to store depth. If CoreLabels are
   * not present in the trees (or at least something that implements HasIndex), an exception will result.
   * 
   * @param t The tree
   * @return A list of lineages
   */
  private List<List<CoreLabel>> makeLineages(final Tree t) {
    if(t == null) return null;

    ((HasIndex) t.label()).setIndex(0);

    final Stack<Tree> treeStack = new Stack<Tree>();
    treeStack.push(t);

    final Stack<CoreLabel> labelStack = new Stack<CoreLabel>();
    CoreLabel rootLabel = new CoreLabel(t.label());
    rootLabel.setIndex(0);
    labelStack.push(rootLabel);

    final List<List<CoreLabel>> lineages = new ArrayList<List<CoreLabel>>();

    while(!treeStack.isEmpty()) {
      Tree node = treeStack.pop();
      int nodeDepth = ((HasIndex) node.label()).index();
      while(!labelStack.isEmpty() && labelStack.peek().index() != nodeDepth - 1) 
        labelStack.pop();

      if(node.isPreTerminal()) {
        List<CoreLabel> lin = new ArrayList<CoreLabel>(labelStack);
        lineages.add(lin);

      } else {
        for(Tree kid : node.children()) {
          ((HasIndex) kid.label()).setIndex(nodeDepth + 1);
          treeStack.push(kid);
        }
        CoreLabel nodeLabel = new CoreLabel(node.label());
        nodeLabel.setIndex(nodeDepth);
        labelStack.add(nodeLabel);
      }
    }

    if(DEBUG) {
      System.out.println("Lineages:");
      for(List<CoreLabel> lin : lineages) {
        for(CoreLabel cl : lin)
          System.out.print(cl.value() + " <- ");
        System.out.println();
      }
    }

    return lineages;
  }

  private void updateCatAverages(final List<CoreLabel> lineage, double score) {
    if(catAvg.get(lineage) == null) {
      catAvg.put(lineage, score);
      catNum.put(lineage, 1.0);

    } else {
      double newAvg = catAvg.get(lineage) + score;
      catAvg.put(lineage, newAvg);
      double newNum = catNum.get(lineage) + 1.0;
      catNum.put(lineage, newNum);
    }
  }

  public void evaluate(Tree guess, Tree gold, PrintWriter pw) {
    if(gold == null || guess == null) {
      System.err.printf("%s: Cannot compare against a null gold or guess tree!\n",this.getClass().getName());
      return;
    }

    final List<List<CoreLabel>> guessLineages = makeLineages(guess);
    final List<List<CoreLabel>> goldLineages = makeLineages(gold);

    if(guessLineages.size() == goldLineages.size()) {

      double localScores = 0.0;
      for(int i = 0; i < guessLineages.size(); i++) {
        List<CoreLabel> guessLin = guessLineages.get(i);
        List<CoreLabel> goldLin = goldLineages.get(i);

        double levDist = editDistance(guessLin, goldLin);
        double la = 1.0 - (levDist / (double) (guessLin.size() + goldLin.size()));

        localScores += la;

        updateCatAverages(goldLin, la);
      }

      corpusAvg += localScores;
      corpusNum += goldLineages.size();

      double localSentAvg = localScores / goldLineages.size();
      if(localSentAvg == 1.0) sentExact++;
      sentAvg += localSentAvg;
      sentNum++;

    } else {
      System.err.printf("%s: Number of guess (%d) gold (%d) don't match!\n",this.getClass().getName(),guessLineages.size(),goldLineages.size());
      System.err.println("Cannot evaluate!");
      System.err.printf("GUESS tree:\n%s\n", guess.toString());
      System.err.printf("GOLD tree:\n%s\n", gold.toString());
    }
  }

  /**
   * Computes Levenshtein edit distance between two lists of labels;
   * 
   * @param l1
   * @param l2
   */
  private int editDistance(final List<CoreLabel> l1, final List<CoreLabel> l2) {
    int[][] m = new int[l1.size()+1][l2.size()+1];
    for(int i = 1; i <= l1.size(); i++)
      m[i][0] = i;
    for(int j = 1; j <= l2.size(); j++)
      m[0][j] = j;

    for(int i = 1; i <= l1.size(); i++) {
      for(int j = 1; j <= l2.size(); j++) {
        m[i][j] = Math.min(m[i-1][j-1] + ((l1.get(i-1).equals(l2.get(j-1))) ? 0 : 1), m[i-1][j] + 1);
        m[i][j] = Math.min(m[i][j], m[i][j-1] + 1);
      }
    }

    return m[l1.size()][l2.size()];
  }

  private String toString(final List<CoreLabel> lineage) {
    StringBuilder sb = new StringBuilder();
    for(CoreLabel cl : lineage) {
      sb.append(cl.value());
      sb.append(" <-- ");
    }

    return sb.toString();
  }

  public void display(boolean verbose, PrintWriter pw) {
    final Random rand = new Random();

    double corpusLevel = corpusAvg / corpusNum;
    double sentLevel = sentAvg / sentNum;
    double sentEx = 100.0 * sentExact / sentNum;
    
    if(verbose) {
      Map<Double,List<CoreLabel>> avgMap = new TreeMap<Double,List<CoreLabel>>();
      for (List<CoreLabel> lineage : catAvg.keySet()) {
        double avg = catAvg.get(lineage) / catNum.get(lineage);
        if(new Double(avg).equals(Double.NaN)) avg = -1.0;
        if(avgMap.containsKey(avg))
          avgMap.put(avg + (rand.nextDouble()/10000.0), lineage);
        else
          avgMap.put(avg, lineage);
      }

      pw.println("============================================================");
      pw.println("Leaf Ancestor Metric" + "(" + name + ") -- final statistics");
      pw.println("============================================================");
      pw.println("#Sentences: " + (int) sentNum);
      pw.println();
      pw.println("Sentence-level (macro-averaged)");
      pw.printf(" Avg: %.3f%n", sentLevel);
      pw.printf(" Exact: %.2f%%%n", sentEx);
      pw.println();
      pw.println("Corpus-level (micro-averaged)");
      pw.printf(" Avg: %.3f%n", corpusLevel);
      pw.println("============================================================");

      for (List<CoreLabel> lineage : avgMap.values()) {
        if(catNum.get(lineage) < 30.0) continue;
        double avg = catAvg.get(lineage) / catNum.get(lineage);
        pw.printf(" %.3f\t%d\t%s\n",avg, (int) ((double)catNum.get(lineage)),toString(lineage));
      }

      pw.println("============================================================");
    
    } else {
      pw.printf("%s summary: corpus: %.3f sent: %.3f sent-ex: %.2f%n", name,corpusLevel,sentLevel,sentEx);
    }
  }


  private static StringBuilder usage = new StringBuilder();
  static {
    usage.append(String.format("Usage: java %s [OPTS] goldFile guessFile\n\n",LeafAncestorEval.class.getName()));
    usage.append("Options:\n");
    usage.append("  -l lang   : Language name " + Languages.listOfLanguages() + "\n");
    usage.append("  -y num    : Skip gold trees with yields longer than num.\n");
    usage.append("  -v        : Verbose output\n");
  }

  private final static int MIN_ARGS = 2;

  //Command line options
  private static boolean VERBOSE = false;
  private static Language LANGUAGE = Language.English;
  private static int MAX_GOLD_YIELD = Integer.MAX_VALUE;

  private static File guessFile = null;
  private static File goldFile = null;

  public static final Map<String,Integer> optionArgDefs = new HashMap<String,Integer>();
  static {
    optionArgDefs.put("-y", 1);
    optionArgDefs.put("-l", 1);
    optionArgDefs.put("-v", 0);
  }
  
  private static boolean validateCommandLine(String[] args) {
    Map<String, String[]> argsMap = StringUtils.argsToMap(args,optionArgDefs);
    
    for(Map.Entry<String, String[]> opt : argsMap.entrySet()) {
      String key = opt.getKey();
      if(key == null) {
        continue;
      
      } else if(key.equals("-y")) {
        MAX_GOLD_YIELD = Integer.valueOf(opt.getValue()[0]);
      
      } else if(key.equals("-l")) {
        LANGUAGE = Language.valueOf(opt.getValue()[0]);
      
      } else if(key.equals("-v")) {
        VERBOSE = true;
      
      } else {
        return false;
      }
    }
    
    //Regular arguments
    String[] rest = argsMap.get(null);
    if(rest == null || rest.length != MIN_ARGS) {
      return false;
    } else {
      goldFile = new File(rest[0]);
      guessFile = new File(rest[1]);
    }
    
    return true;
  }
  

  /**
   * Execute with no arguments for usage.
   */
  public static void main(String[] args) {

    if(!validateCommandLine(args)) {
      System.err.println(usage);
      System.exit(-1);
    }

    final TreebankLangParserParams tlpp = Languages.getLanguageParams(LANGUAGE);
    final PrintWriter pwOut = tlpp.pw();

    final Treebank guessTreebank = tlpp.diskTreebank();
    guessTreebank.loadPath(guessFile);
    pwOut.println("GUESS TREEBANK:");
    pwOut.println(guessTreebank.textualSummary());

    final Treebank goldTreebank = tlpp.diskTreebank();
    goldTreebank.loadPath(goldFile);
    pwOut.println("GOLD TREEBANK:");
    pwOut.println(goldTreebank.textualSummary());

    final LeafAncestorEval metric = new LeafAncestorEval("LeafAncestor");

    final TreeTransformer tc = tlpp.collinizer();

    //The evalb ref implementation assigns status for each tree pair as follows:
    //
    //   0 - Ok (yields match)
    //   1 - length mismatch
    //   2 - null parse e.g. (()).
    //
    //In the cases of 1,2, evalb does not include the tree pair in the LP/LR computation.
    final Iterator<Tree> goldItr = goldTreebank.iterator();
    final Iterator<Tree> guessItr = guessTreebank.iterator();
    int goldLineId = 0;
    int guessLineId = 0;
    int skippedGuessTrees = 0;
    while( guessItr.hasNext() && goldItr.hasNext() ) {
      Tree guessTree = guessItr.next();
      List<Label> guessYield = guessTree.yield();
      guessLineId++;

      Tree goldTree = goldItr.next();
      List<Label> goldYield = goldTree.yield();
      goldLineId++;

      // Check that we should evaluate this tree
      if(goldYield.size() > MAX_GOLD_YIELD) {
        skippedGuessTrees++;
        continue;
      }

      // Only trees with equal yields can be evaluated
      if(goldYield.size() != guessYield.size()) {
        pwOut.printf("Yield mismatch gold: %d tokens vs. guess: %d tokens (lines: gold %d guess %d)%n", goldYield.size(), guessYield.size(), goldLineId, guessLineId);
        skippedGuessTrees++;
        continue;
      }
      
      final Tree evalGuess = tc.transformTree(guessTree);
      final Tree evalGold = tc.transformTree(goldTree);

      metric.evaluate(evalGuess, evalGold, ((VERBOSE) ? pwOut : null));
    }
    
    if(guessItr.hasNext() || goldItr.hasNext()) {
      System.err.printf("Guess/gold files do not have equal lengths (guess: %d gold: %d)%n.", guessLineId, goldLineId);
    }
    
    pwOut.println("================================================================================");
    if(skippedGuessTrees != 0) pwOut.printf("%s %d guess trees\n", "Unable to evaluate", skippedGuessTrees);
    metric.display(true, pwOut);
    pwOut.close();
  }
}
