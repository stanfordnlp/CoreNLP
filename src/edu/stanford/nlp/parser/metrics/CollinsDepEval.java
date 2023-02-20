package edu.stanford.nlp.parser.metrics; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.File;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.parser.lexparser.AbstractCollinizer;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.trees.CollinsDependency;
import edu.stanford.nlp.trees.CollinsRelation;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Compute P/R/F1 for the dependency representation of Collins (1999; 2003).
 * 
 * @author Spence Green
 *
 */
public class CollinsDepEval extends AbstractEval  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(CollinsDepEval.class);

  private static final boolean DEBUG = false;

  private final HeadFinder hf;
  private final String startSymbol;

  private final Counter<CollinsRelation> precisions;
  private final Counter<CollinsRelation> recalls;
  private final Counter<CollinsRelation> f1s;

  private final Counter<CollinsRelation> precisions2;
  private final Counter<CollinsRelation> recalls2;
  private final Counter<CollinsRelation> pnums2;
  private final Counter<CollinsRelation> rnums2;

  public CollinsDepEval(String str, boolean runningAverages, HeadFinder hf, String startSymbol) {
    super(str,runningAverages);

    this.hf = hf;
    this.startSymbol = startSymbol;

    precisions = new ClassicCounter<>();
    recalls = new ClassicCounter<>();
    f1s = new ClassicCounter<>();

    precisions2 = new ClassicCounter<>();
    recalls2 = new ClassicCounter<>();
    pnums2 = new ClassicCounter<>();
    rnums2 = new ClassicCounter<>();
  }

  @Override
  protected Set<?> makeObjects(Tree tree) {
    log.info(this.getClass().getName() + ": Function makeObjects() not implemented");
    return null;
  }

  private Map<CollinsRelation,Set<CollinsDependency>> makeCollinsObjects(Tree t) {
    final Map<CollinsRelation,Set<CollinsDependency>> relMap = Generics.newHashMap();
    final Set<CollinsDependency> deps = CollinsDependency.extractNormalizedFromTree(t, startSymbol, hf);

    for (CollinsDependency dep : deps) {
      if (DEBUG) System.out.println(dep.toString());
      if (relMap.get(dep.getRelation()) == null)
        relMap.put(dep.getRelation(), Generics.<CollinsDependency>newHashSet());
      relMap.get(dep.getRelation()).add(dep);
    }
    if(DEBUG) System.out.println();

    return relMap;
  }

  @Override
  public void evaluate(Tree guess, Tree gold, PrintWriter pw) {
    if(gold == null || guess == null) {
      System.err.printf("%s: Cannot compare against a null gold or guess tree!\n",this.getClass().getName());
      return;
    }

    if(DEBUG) System.out.println("guess:");
    Map<CollinsRelation,Set<CollinsDependency>> guessDeps = makeCollinsObjects(guess);

    if(DEBUG) System.out.println("gold:");
    Map<CollinsRelation,Set<CollinsDependency>> goldDeps = makeCollinsObjects(gold);

    Set<CollinsRelation> relations = Generics.newHashSet();
    relations.addAll(guessDeps.keySet());
    relations.addAll(goldDeps.keySet());

    num += 1.0;

    for (CollinsRelation rel : relations) {
      Set<CollinsDependency> thisGuessDeps = guessDeps.get(rel);
      Set<CollinsDependency> thisGoldDeps = goldDeps.get(rel);

      if (thisGuessDeps == null)
        thisGuessDeps = Generics.newHashSet();
      if (thisGoldDeps == null)
        thisGoldDeps = Generics.newHashSet();

      double currentPrecision = precision(thisGuessDeps, thisGoldDeps);
      double currentRecall = precision(thisGoldDeps, thisGuessDeps);
      double currentF1 = (currentPrecision > 0.0 && currentRecall > 0.0 ? 2.0 / (1.0 / currentPrecision + 1.0 / currentRecall) : 0.0);

      precisions.incrementCount(rel, currentPrecision);
      recalls.incrementCount(rel, currentRecall);
      f1s.incrementCount(rel, currentF1);

      precisions2.incrementCount(rel, thisGuessDeps.size() * currentPrecision);
      pnums2.incrementCount(rel, thisGuessDeps.size());

      recalls2.incrementCount(rel, thisGoldDeps.size() * currentRecall);
      rnums2.incrementCount(rel, thisGoldDeps.size());

      if (pw != null && runningAverages) {
        pw.println(rel + "\tP: " + ((int) (currentPrecision * 10000)) / 100.0 + " (sent ave " + ((int) (precisions.getCount(rel) * 10000 / num)) / 100.0 + ") (evalb " + ((int) (precisions2.getCount(rel) * 10000 / pnums2.getCount(rel))) / 100.0 + ")");
        pw.println("\tR: " + ((int) (currentRecall * 10000)) / 100.0 + " (sent ave " + ((int) (recalls.getCount(rel) * 10000 / num)) / 100.0 + ") (evalb " + ((int) (recalls2.getCount(rel) * 10000 / rnums2.getCount(rel))) / 100.0 + ")");
        double cF1 = 2.0 / (rnums2.getCount(rel) / recalls2.getCount(rel) + pnums2.getCount(rel) / precisions2.getCount(rel));
        String emit = str + " F1: " + ((int) (currentF1 * 10000)) / 100.0 + " (sent ave " + ((int) (10000 * f1s.getCount(rel) / num)) / 100.0 + ", evalb " + ((int) (10000 * cF1)) / 100.0 + ")";
        pw.println(emit);
      }
    }
    if (pw != null && runningAverages) {
      pw.println("================================================================================");
    }
  }

  @Override
  public void display(boolean verbose, PrintWriter pw) {
    final NumberFormat nf = new DecimalFormat("0.00");
    final Set<CollinsRelation> cats = Generics.newHashSet();
    final Random rand = new Random();
    cats.addAll(precisions.keySet());
    cats.addAll(recalls.keySet());

    Map<Double,CollinsRelation> f1Map = new TreeMap<>();
    for (CollinsRelation cat : cats) {
      double pnum2 = pnums2.getCount(cat);
      double rnum2 = rnums2.getCount(cat);
      double prec = precisions2.getCount(cat) / pnum2;//(num > 0.0 ? precision/num : 0.0);
      double rec = recalls2.getCount(cat) / rnum2;//(num > 0.0 ? recall/num : 0.0);
      double f1 = 2.0 / (1.0 / prec + 1.0 / rec);//(num > 0.0 ? f1/num : 0.0);

      if (Double.valueOf(f1).equals(Double.NaN)) f1 = -1.0;
      if (f1Map.containsKey(f1))
        f1Map.put(f1 + (rand.nextDouble()/1000.0), cat);
      else
        f1Map.put(f1, cat);
    }

    pw.println(" Abstract Collins Dependencies -- final statistics");
    pw.println("================================================================================");

    for (CollinsRelation cat : f1Map.values()) {
      double pnum2 = pnums2.getCount(cat);
      double rnum2 = rnums2.getCount(cat);
      double prec = precisions2.getCount(cat) / pnum2;//(num > 0.0 ? precision/num : 0.0);
      double rec = recalls2.getCount(cat) / rnum2;//(num > 0.0 ? recall/num : 0.0);
      double f1 = 2.0 / (1.0 / prec + 1.0 / rec);//(num > 0.0 ? f1/num : 0.0);

      pw.println(cat + "\tLP: " + ((pnum2 == 0.0) ? " N/A": nf.format(prec)) + "\tguessed: " + (int) pnum2 +
          "\tLR: " + ((rnum2 == 0.0) ? " N/A": nf.format(rec)) + "\tgold:  " + (int) rnum2 +
          "\tF1: " + ((pnum2 == 0.0 || rnum2 == 0.0) ? " N/A": nf.format(f1)));
    }

    pw.println("================================================================================");
  }

  private final static int MIN_ARGS = 2;
  private static String usage() {
    StringBuilder usage = new StringBuilder();
    String nl = System.getProperty("line.separator");
    usage.append(String.format("Usage: java %s [OPTS] goldFile guessFile%n%n",CollinsDepEval.class.getName()));
    usage.append("Options:").append(nl);
    usage.append("  -v        : Verbose output").append(nl);
    usage.append("  -l lang   : Language name " + Language.langList).append(nl);
    usage.append("  -y num    : Max yield of gold trees").append(nl);
    usage.append("  -g num    : Max yield of guess trees").append(nl);
    return usage.toString();
  }
  private static Map<String,Integer> optionArgDefs() {
    Map<String,Integer> optionArgDefs = Generics.newHashMap();
    optionArgDefs.put("v", 0);
    optionArgDefs.put("l", 1);
    optionArgDefs.put("g", 1);
    optionArgDefs.put("y", 1);
    return optionArgDefs;
  }

  /**
   * 
   * @param args
   */
  public static void main(String[] args) {
    if(args.length < MIN_ARGS) {
      log.info(usage());
      System.exit(-1);
    }
    Properties options = StringUtils.argsToProperties(args, optionArgDefs());
    
    boolean VERBOSE = PropertiesUtils.getBool(options, "v", false);
    Language LANGUAGE = PropertiesUtils.get(options, "l", Language.English, Language.class);
    int MAX_GOLD_YIELD = PropertiesUtils.getInt(options, "g", Integer.MAX_VALUE);
    int MAX_GUESS_YIELD = PropertiesUtils.getInt(options, "y", Integer.MAX_VALUE);
    
    String[] parsedArgs = options.getProperty("","").split("\\s+");
    if (parsedArgs.length != MIN_ARGS) {
      log.info(usage());
      System.exit(-1);
    }
    File goldFile = new File(parsedArgs[0]);
    File guessFile = new File(parsedArgs[1]);

    final TreebankLangParserParams tlpp = LANGUAGE.params;
    final PrintWriter pwOut = tlpp.pw();

    final Treebank guessTreebank = tlpp.diskTreebank();
    guessTreebank.loadPath(guessFile);
    pwOut.println("GUESS TREEBANK:");
    pwOut.println(guessTreebank.textualSummary());

    final Treebank goldTreebank = tlpp.diskTreebank();
    goldTreebank.loadPath(goldFile);
    pwOut.println("GOLD TREEBANK:");
    pwOut.println(goldTreebank.textualSummary());

    final CollinsDepEval depEval = new CollinsDepEval("CollinsDep", true, tlpp.headFinder(), tlpp.treebankLanguagePack().startSymbol());

    final AbstractCollinizer tc = tlpp.collinizer();

    //PennTreeReader skips over null/malformed parses. So when the yields of the gold/guess trees
    //don't match, we need to keep looking for the next gold tree that matches.
    //The evalb ref implementation differs slightly as it expects one tree per line. It assigns
    //status as follows:
    //
    //   0 - Ok (yields match)
    //   1 - length mismatch
    //   2 - null parse e.g. (()).
    //
    //In the cases of 1,2, evalb does not include the tree pair in the LP/LR computation.

    final Iterator<Tree> goldItr = goldTreebank.iterator();
    int goldLineId = 0;
    int skippedGuessTrees = 0;

    for(final Tree guess : guessTreebank) {
      if(guess.yield().size() > MAX_GUESS_YIELD) {
        skippedGuessTrees++;
        continue;
      }

      boolean doneEval = false;
      while(goldItr.hasNext() && !doneEval) {
        final Tree gold = goldItr.next();
        final Tree evalGold = tc.transformTree(gold, gold);
        goldLineId++;

        if(gold.yield().size() > MAX_GOLD_YIELD)
          continue;

        final Tree evalGuess = tc.transformTree(guess, gold);
        if (evalGuess == null || evalGold.yield().size() != evalGuess.yield().size()) {
          pwOut.println("Yield mismatch at gold line " + goldLineId);
          skippedGuessTrees++;
          break; //Default evalb behavior -- skip this guess tree
        } 

        depEval.evaluate(evalGuess, evalGold, ((VERBOSE) ? pwOut : null));

        doneEval = true; //Move to the next guess parse
      }
    }

    pwOut.println("================================================================================");
    if(skippedGuessTrees != 0) pwOut.printf("%s %d guess trees\n", ((MAX_GUESS_YIELD < Integer.MAX_VALUE) ? "Skipped" : "Unable to evaluate"), skippedGuessTrees);
    depEval.display(true, pwOut);
    pwOut.close();
  }
}
