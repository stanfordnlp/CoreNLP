package edu.stanford.nlp.parser.metrics;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.international.Languages;
import edu.stanford.nlp.international.Languages.Language;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.trees.Dependency;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.Filter;
import edu.stanford.nlp.util.Filters;
import edu.stanford.nlp.util.StringUtils;

/** 
 *  Dependency unlabeled attachment score.
 *  <p>
 *  If Collinization has not been performed prior to evaluation, then
 *  it is customary (for reporting results) to pass in a filter that rejects 
 *  dependencies with punctuation dependents.
 *  
 *  @author Spence Green
 *  
 */
public class UnlabeledAttachmentEval extends AbstractEval {

  private final HeadFinder headFinder;
  
  private final Filter<String> punctRejectWordFilter;
  private final Filter<Dependency<Label, Label, Object>> punctRejectFilter;
  
  /** 
   * @param headFinder If a headFinder is provided, then head percolation will be done
   * for trees. Otherwise, it must be called separately.
   */
  public UnlabeledAttachmentEval(String str, boolean runningAverages, HeadFinder headFinder) {
    this(str, runningAverages, headFinder, Filters.<String>acceptFilter());
  }
  
  public UnlabeledAttachmentEval(String str, boolean runningAverages, HeadFinder headFinder, Filter<String> punctRejectFilter) {
    super(str, runningAverages);
    this.headFinder = headFinder;
    this.punctRejectWordFilter = punctRejectFilter;
 
    this.punctRejectFilter = new Filter<Dependency<Label,Label,Object>>() {
      private static final long serialVersionUID = 649358302237611081L;
      // Semantics of this method are weird. If accept() returns true, then the dependent is
      // *not* a punctuation item. This filter thus accepts everything except punctuation
      // dependencies.
      public boolean accept(Dependency<Label, Label, Object> dep) {
        String depString = dep.dependent().value();
        return punctRejectWordFilter.accept(depString);
      }
    };
  }

  @Override
  public void evaluate(Tree guess, Tree gold, PrintWriter pw) {
    if(gold == null || guess == null) {
      System.err.printf("%s: Cannot compare against a null gold or guess tree!\n", this.getClass().getName());
      return;

    } else if (guess.yield().size() != gold.yield().size()) {
      System.err.println("Warning: yield differs:");
      System.err.println("Guess: " + Sentence.listToString(guess.yield()));
      System.err.println("Gold:  " + Sentence.listToString(gold.yield()));
    }

    super.evaluate(guess, gold, pw);
  }
  
  /**
   * Build the set of dependencies for evaluation.  This set excludes
   * all dependencies for which the argument is a punctuation tag.
   */
  @Override
  protected Set<?> makeObjects(Tree tree) {
    if (tree == null) {
      System.err.println("Warning: null tree");
      return new HashSet<Dependency<Label, Label, Object>>();
    }
    if (headFinder != null) {
      tree.percolateHeads(headFinder);
    }
    
    Set<Dependency<Label, Label, Object>> deps = tree.dependencies(punctRejectFilter);
    return deps;
  }
  
  private static final int minArgs = 2;
  private static final StringBuilder usage = new StringBuilder();
  static {
    usage.append(String.format("Usage: java %s [OPTS] gold guess\n\n", UnlabeledAttachmentEval.class.getName()));
    usage.append("Options:\n");
    usage.append("  -v         : Verbose mode.\n");
    usage.append("  -l lang    : Select language settings from ").append(Languages.listOfLanguages()).append('\n');
    usage.append("  -y num     : Skip gold trees with yields longer than num.\n");
    usage.append("  -e         : Input encoding.\n");
  }

  public static final Map<String,Integer> optionArgDefs = new HashMap<String,Integer>();
  static {
    optionArgDefs.put("-v", 0);
    optionArgDefs.put("-l", 1);
    optionArgDefs.put("-y", 1);
    optionArgDefs.put("-e", 0);
  }

  /**
   * Run the Evalb scoring metric on guess/gold input. The default language is English.
   *
   * @param args
   */
  public static void main(String[] args) {
    TreebankLangParserParams tlpp = new EnglishTreebankParserParams();
    int maxGoldYield = Integer.MAX_VALUE;
    boolean VERBOSE = false;
    String encoding = "UTF-8";

    String guessFile = null;
    String goldFile = null;

    Map<String, String[]> argsMap = StringUtils.argsToMap(args, optionArgDefs);

    for(Map.Entry<String, String[]> opt : argsMap.entrySet()) {
      if(opt.getKey() == null) continue;
      if(opt.getKey().equals("-l")) {
        Language lang = Language.valueOf(opt.getValue()[0].trim());
        tlpp = Languages.getLanguageParams(lang);

      } else if(opt.getKey().equals("-y")) {
        maxGoldYield = Integer.parseInt(opt.getValue()[0].trim());

      } else if(opt.getKey().equals("-v")) {
        VERBOSE = true;

      } else if(opt.getKey().equals("-e")) {
        encoding = opt.getValue()[0];

      } else {
        System.err.println(usage.toString());
        System.exit(-1);
      }

      //Non-option arguments located at key null
      String[] rest = argsMap.get(null);
      if(rest == null || rest.length < minArgs) {
        System.err.println(usage.toString());
        System.exit(-1);
      }
      goldFile = rest[0];
      guessFile = rest[1];
    }

    tlpp.setInputEncoding(encoding);
    final PrintWriter pwOut = tlpp.pw();

    final Treebank guessTreebank = tlpp.diskTreebank();
    guessTreebank.loadPath(guessFile);
    pwOut.println("GUESS TREEBANK:");
    pwOut.println(guessTreebank.textualSummary());

    final Treebank goldTreebank = tlpp.diskTreebank();
    goldTreebank.loadPath(goldFile);
    pwOut.println("GOLD TREEBANK:");
    pwOut.println(goldTreebank.textualSummary());

    final UnlabeledAttachmentEval metric = new UnlabeledAttachmentEval("UAS LP/LR", true, tlpp.headFinder());

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
      if(goldYield.size() > maxGoldYield) {
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
      evalGuess.indexLeaves(true);
      final Tree evalGold = tc.transformTree(goldTree);
      evalGold.indexLeaves(true);

      metric.evaluate(evalGuess, evalGold, ((VERBOSE) ? pwOut : null));
    }
    
    if(guessItr.hasNext() || goldItr.hasNext()) {
      System.err.printf("Guess/gold files do not have equal lengths (guess: %d gold: %d)%n.", guessLineId, goldLineId);
    }

    pwOut.println("================================================================================");
    if(skippedGuessTrees != 0) pwOut.printf("%s %d guess trees\n", "Unable to evaluate", skippedGuessTrees);
    metric.display(true, pwOut);
    
    pwOut.println();
    pwOut.close();
  }
}