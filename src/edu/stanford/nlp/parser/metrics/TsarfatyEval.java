package edu.stanford.nlp.parser.metrics;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.parser.lexparser.AbstractCollinizer;
import edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.trees.Constituent;
import edu.stanford.nlp.trees.ConstituentFactory;
import edu.stanford.nlp.trees.LabeledScoredConstituentFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.Generics;

/**
 * Character level segmentation and tagging metric from (Tsarfaty, 2006). For evaluating parse
 * trees at the character level, use {@link edu.stanford.nlp.parser.metrics.Evalb}
 * with the charLevel flag set to true.
 * 
 * NOTE: If segmentation markers (e.g. "+") appear in the input, then they should be stripped
 * prior to running this metric.
 * 
 * @author Spence Green
 *
 */
public class TsarfatyEval extends AbstractEval {

  private final boolean useTag;
  private final ConstituentFactory cf = new LabeledScoredConstituentFactory();

  public TsarfatyEval(String str, boolean tags) {
    super(str, false);
    useTag = tags;
  }

  @Override
  protected Set<?> makeObjects(Tree tree) {
    Set<Constituent> deps = Generics.newHashSet();
    if(tree != null) extractDeps(tree, 0, deps);
    return deps;
  }

  private int extractDeps(Tree t, int left, Set<Constituent> deps) {
    int position = left;

    // Segmentation constituents
    if(!useTag && t.isLeaf()) { 
      position += t.label().value().length();
      deps.add(cf.newConstituent(left, position - 1, t.label(), 0.0));

      // POS tag constituents
    } else if(useTag && t.isPreTerminal()) {
      position += t.firstChild().label().value().length();
      deps.add(cf.newConstituent(left, position - 1, t.label(), 0.0));

    } else {
      Tree[] kids = t.children();
      for (Tree kid : kids) position = extractDeps(kid, position, deps);
    }

    return position;
  }

  private static final int minArgs = 2;
  private static final StringBuilder usage = new StringBuilder();
  static {
    usage.append(String.format("Usage: java %s [OPTS] gold guess\n\n",TsarfatyEval.class.getName()));
    usage.append("Options:\n");
    usage.append("  -v         : Verbose mode.\n");
    usage.append("  -l lang    : Select language settings from " + Language.class.getName() + "\n");
    usage.append("  -y num     : Skip gold trees with yields longer than num.\n");
    usage.append("  -g num     : Skip guess trees with yields longer than num.\n");
    usage.append("  -t         : Tagging mode (default: segmentation).\n");
  }

  /**
   * Run the scoring metric on guess/gold input. This method performs "Collinization." 
   * The default language is English.
   * 
   * @param args
   */
  public static void main(String[] args) {

    if(args.length < minArgs) {
      System.out.println(usage.toString());
      System.exit(-1);
    }

    TreebankLangParserParams tlpp = new EnglishTreebankParserParams();
    int maxGoldYield = Integer.MAX_VALUE;
    int maxGuessYield = Integer.MAX_VALUE;
    boolean VERBOSE = false;
    boolean skipGuess = false;
    boolean tagMode = false;
    String guessFile = null;
    String goldFile = null;

    for(int i = 0; i < args.length; i++) {

      if(args[i].startsWith("-")) {

        switch (args[i]) {
          case "-l":
            Language lang = Language.valueOf(args[++i].trim());
            tlpp = lang.params;

            break;
          case "-y":
            maxGoldYield = Integer.parseInt(args[++i].trim());

            break;
          case "-t":
            tagMode = true;

            break;
          case "-v":
            VERBOSE = true;

            break;
          case "-g":
            maxGuessYield = Integer.parseInt(args[++i].trim());
            skipGuess = true;

            break;
          default:
            System.out.println(usage.toString());
            System.exit(-1);
        }

      } else {
        //Required parameters
        goldFile = args[i++];
        guessFile = args[i];
        break;
      }
    }

    final PrintWriter pwOut = tlpp.pw();

    final Treebank guessTreebank = tlpp.diskTreebank();
    guessTreebank.loadPath(guessFile);
    pwOut.println("GUESS TREEBANK:");
    pwOut.println(guessTreebank.textualSummary());

    final Treebank goldTreebank = tlpp.diskTreebank();
    goldTreebank.loadPath(goldFile);
    pwOut.println("GOLD TREEBANK:");
    pwOut.println(goldTreebank.textualSummary());

    final String evalName = (tagMode) ? "TsarfatyTAG" : "TsarfatySEG";
    final TsarfatyEval eval = new TsarfatyEval(evalName, tagMode);

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
      final ArrayList<Label> guessSent = guess.yield();
      final String guessChars = SentenceUtils.listToString(guessSent).replaceAll("\\s+","");
      if(guessSent.size() > maxGuessYield) {
        skippedGuessTrees++;
        continue;
      }

      boolean doneEval = false;
      while(goldItr.hasNext() && !doneEval) {
        final Tree gold = goldItr.next();
        final Tree evalGold = tc.transformTree(gold, gold);
        goldLineId++;

        final ArrayList<Label> goldSent = gold.yield();
        final String goldChars = SentenceUtils.listToString(goldSent).replaceAll("\\s+","");

        if(goldSent.size() > maxGoldYield)
          continue;

        if(goldChars.length() != guessChars.length()) {
          pwOut.printf("Char level yield mismatch at line %d (guess: %d gold: %d)\n",goldLineId,guessChars.length(),goldChars.length());
          skippedGuessTrees++;
          break; //Default evalb behavior -- skip this guess tree
        }

        final Tree evalGuess = tc.transformTree(guess, gold);
        if (evalGuess == null) {
          pwOut.printf("Collinizer failure at line %d\n", goldLineId);
          skippedGuessTrees++;
          break; //Default evalb behavior -- skip this guess tree
        }
        eval.evaluate(evalGuess, evalGold, ((VERBOSE) ? pwOut : null));

        doneEval = true; //Move to the next guess parse
      }
    }

    pwOut.println("================================================================================");
    if(skippedGuessTrees != 0) pwOut.printf("%s %d guess trees\n", ((skipGuess) ? "Skipped" : "Unable to evaluate"), skippedGuessTrees);
    eval.display(true, pwOut);
    pwOut.println();
    pwOut.close();
  }
}
