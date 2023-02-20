package edu.stanford.nlp.parser.metrics; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.parser.lexparser.AbstractCollinizer;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.trees.Constituent;
import edu.stanford.nlp.trees.ConstituentFactory;
import edu.stanford.nlp.trees.LabeledScoredConstituentFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Triple;

/**
 * A Java re-implementation of the evalb bracket scoring metric (Collins, 1997) that accepts Unicode input.
 * "Collinization" should be performed on input trees prior to invoking the package programmatically.
 * "Collinization" refers to normalization of trees for things not counted in evaluation,
 * such as equivalencing PRT and ADVP, which has standardly been done in English evaluation.
 * A main method is provided that performs Collinization according to language specific settings.
 * <p>
 * This implementation assumes that the guess/gold input files are of equal length, and have one tree per
 * line.
 * <p>
 * This implementation was last validated against EVALB20080701 (http://nlp.cs.nyu.edu/evalb/)
 * by Spence Green on 22 Jan. 2010.  Notwithstanding this, Sekine and collins' EVALB script has been
 * the common standard for constituency evaluation of parsers for the last decade.  We always validate
 * any numbers we report with it, and we suggest that you do the same.
 *
 * @author Dan Klein
 * @author Spence Green
 */
public class Evalb extends AbstractEval  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(Evalb.class);

  private final ConstituentFactory cf;

  public Evalb(String str, boolean runningAverages) {
    super(str, runningAverages);
    cf = new LabeledScoredConstituentFactory();
  }

  /**
   * evalb only evaluates phrasal categories, thus constituents() does not
   * return objects for terminals and pre-terminals.
   */
  @Override
  protected Set<Constituent> makeObjects(Tree tree) {
    Set<Constituent> set = Generics.newHashSet();
    if(tree != null) set.addAll(tree.constituents(cf));
    return set;
  }

  @Override
  public void evaluate(Tree guess, Tree gold, PrintWriter pw) {
    if(gold == null || guess == null) {
      System.err.printf("%s: Cannot compare against a null gold or guess tree!\n",this.getClass().getName());
      return;

    } else if (guess.yield().size() != gold.yield().size()) {
      log.info("Warning: yield differs:");
      log.info("Guess: " + SentenceUtils.listToString(guess.yield()));
      log.info("Gold:  " + SentenceUtils.listToString(gold.yield()));
    }

    super.evaluate(guess, gold, pw);
  }


  public static class CBEval extends Evalb {

    private double cb = 0.0;
    private double num = 0.0;
    private double zeroCB = 0.0;

    protected void checkCrossing(Set<Constituent> s1, Set<Constituent> s2) {
      double c = 0.0;
      for (Constituent constit : s1) {
        if (constit.crosses(s2)) {
          c += 1.0;
        }
      }
      if (c == 0.0) {
        zeroCB += 1.0;
      }
      cb += c;
      num += 1.0;
    }

    @Override
    public void evaluate(Tree t1, Tree t2, PrintWriter pw) {
      Set<Constituent> b1 = makeObjects(t1);
      Set<Constituent> b2 = makeObjects(t2);
      checkCrossing(b1, b2);
      if (pw != null && runningAverages) {
        pw.println("AvgCB: " + ((int) (10000.0 * cb / num)) / 100.0 +
            " ZeroCB: " + ((int) (10000.0 * zeroCB / num)) / 100.0 + " N: " + getNum());
      }
    }

    @Override
    public void display(boolean verbose, PrintWriter pw) {
      pw.println(str + " AvgCB: " + ((int) (10000.0 * cb / num)) / 100.0 +
          " ZeroCB: " + ((int) (10000.0 * zeroCB / num)) / 100.0);
    }

    public CBEval(String str, boolean runningAverages) {
      super(str, runningAverages);
    }
  }


  private static final int minArgs = 2;
  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    sb.append(String.format("Usage: java %s [OPTS] gold guess%n%n",Evalb.class.getName()));
    sb.append("Options:").append(nl);
    sb.append("  -v         : Verbose mode.").append(nl);
    sb.append("  -l lang    : Select language settings from ").append(Language.langList).append(nl);
    sb.append("  -y num     : Skip gold trees with yields longer than num.").append(nl);
    sb.append("  -s num     : Sort the trees by F1 and output the num lowest F1 trees.").append(nl);
    sb.append("  -c         : Compute LP/LR/F1 by category.").append(nl);
    sb.append("  -f regex   : Compute category level evaluation for categories that match this regex.").append(nl);
    sb.append("  -e         : Input encoding.").append(nl);
    return sb.toString();
  }
  private static Map<String,Integer> optionArgDefs() {
    Map<String,Integer> optionArgDefs = Generics.newHashMap();
    optionArgDefs.put("v", 0);
    optionArgDefs.put("l", 1);
    optionArgDefs.put("y", 1);
    optionArgDefs.put("s", 1);
    optionArgDefs.put("c", 0);
    optionArgDefs.put("e", 0);
    optionArgDefs.put("f", 1);
    return optionArgDefs;
  }

  /**
   * Run the Evalb scoring metric on guess/gold input. The default language is English.
   *
   * @param args
   */
  public static void main(String[] args) {
    if (args.length < minArgs) {
      log.info(usage());
      System.exit(-1);
    }
    Properties options = StringUtils.argsToProperties(args, optionArgDefs());
    Language language = PropertiesUtils.get(options, "l", Language.English, Language.class);
    final TreebankLangParserParams tlpp = language.params;
    final int maxGoldYield = PropertiesUtils.getInt(options, "y", Integer.MAX_VALUE);
    final boolean VERBOSE = PropertiesUtils.getBool(options, "v", false);
    final boolean sortByF1 = PropertiesUtils.hasProperty(options, "s");
    int worstKTreesToEmit = PropertiesUtils.getInt(options, "s", 0);
    PriorityQueue<Triple<Double,Tree,Tree>> queue = sortByF1 ? new PriorityQueue<>(2000, new F1Comparator()) : null;
    boolean doCatLevel = PropertiesUtils.getBool(options, "c", false);
    String labelRegex = options.getProperty("f", null);
    String encoding = options.getProperty("e", "UTF-8");

    String[] parsedArgs = options.getProperty("","").split("\\s+");
    if (parsedArgs.length != minArgs) {
      log.info(usage());
      System.exit(-1);
    }
    String goldFile = parsedArgs[0];
    String guessFile = parsedArgs[1];

    // Command-line has been parsed. Configure the metric for evaluation.
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

    final Evalb metric = new Evalb("Evalb LP/LR", true);
    final EvalbByCat evalbCat = (doCatLevel) ? new EvalbByCat("EvalbByCat LP/LR", true, labelRegex) : null;
    final AbstractCollinizer tc = tlpp.collinizer();

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

      final Tree evalGuess = tc.transformTree(guessTree, goldTree);
      final Tree evalGold = tc.transformTree(goldTree, goldTree);

      metric.evaluate(evalGuess, evalGold, ((VERBOSE) ? pwOut : null));

      if(doCatLevel) evalbCat.evaluate(evalGuess, evalGold, ((VERBOSE) ? pwOut : null));
      if(sortByF1) storeTrees(queue,guessTree,goldTree,metric.getLastF1());
    }

    if(guessItr.hasNext() || goldItr.hasNext()) {
      System.err.printf("Guess/gold files do not have equal lengths (guess: %d gold: %d)%n.", guessLineId, goldLineId);
    }

    pwOut.println("================================================================================");
    if(skippedGuessTrees != 0) pwOut.printf("%s %d guess trees\n", "Unable to evaluate", skippedGuessTrees);
    metric.display(true, pwOut);
    pwOut.println();
    if(doCatLevel) {
      evalbCat.display(true, pwOut);
      pwOut.println();
    }
    if(sortByF1) emitSortedTrees(queue,worstKTreesToEmit,guessFile);
    pwOut.close();
  }


  private static void emitSortedTrees(PriorityQueue<Triple<Double, Tree, Tree>> queue, int worstKTreesToEmit,
      String filePrefix) {

    if(queue == null) log.info("Queue was not initialized properly");

    try {
      final PrintWriter guessPw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePrefix + ".kworst.guess"),"UTF-8")));
      final PrintWriter goldPw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePrefix + ".kworst.gold"),"UTF-8")));

      final ConstituentFactory cFact = new LabeledScoredConstituentFactory();
      final PrintWriter guessDepPw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePrefix + ".kworst.guess.deps"),"UTF-8")));
      final PrintWriter goldDepPw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePrefix + ".kworst.gold.deps"),"UTF-8")));

      System.out.printf("F1s of %d worst trees:\n",worstKTreesToEmit);

      for(int i = 0; queue.peek() != null && i < worstKTreesToEmit; i++) {
        final Triple<Double, Tree, Tree> trees = queue.poll();

        System.out.println(trees.first());

        //Output the trees
        goldPw.println(trees.second().toString());
        guessPw.println(trees.third().toString());

        //Output the set differences
        Set<Constituent> goldDeps = Generics.newHashSet();
        goldDeps.addAll(trees.second().constituents(cFact));
        goldDeps.removeAll(trees.third().constituents(cFact));
        for(Constituent c : goldDeps)
          goldDepPw.print(c.toString() + "  ");
        goldDepPw.println();

        Set<Constituent> guessDeps = Generics.newHashSet();
        guessDeps.addAll(trees.third().constituents(cFact));
        guessDeps.removeAll(trees.second().constituents(cFact));
        for(Constituent c : guessDeps)
          guessDepPw.print(c.toString() + "  ");
        guessDepPw.println();
      }

      guessPw.close();
      goldPw.close();
      goldDepPw.close();
      guessDepPw.close();

    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();

    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }

  private static void storeTrees(PriorityQueue<Triple<Double, Tree, Tree>> queue, Tree guess, Tree gold, double curF1) {
    if(queue == null) return;

    queue.add(new Triple<>(curF1, gold, guess));
  }

  private static class F1Comparator implements Comparator<Triple<Double, Tree, Tree>> {

    public int compare(Triple<Double, Tree, Tree> o1, Triple<Double, Tree, Tree> o2) {
      final double firstF1 = o1.first();
      final double secondF1 = o2.first();

      if(firstF1 < secondF1)
        return -1;
      else if(firstF1 == secondF1)
        return 0;

      return 1;
    }
  }
}
