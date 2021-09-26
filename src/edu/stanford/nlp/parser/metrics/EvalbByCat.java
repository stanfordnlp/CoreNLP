package edu.stanford.nlp.parser.metrics; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.PrintWriter;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.trees.Constituent;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Generics;

/**
 * Computes labeled precision and recall (evalb) at the constituent category level.
 * 
 * @author Roger Levy
 * @author Spence Green
 */
public class EvalbByCat extends AbstractEval  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(EvalbByCat.class);

  private final Evalb evalb;
  
  // Only evaluate categories that match this regular expression
  private Pattern pLabelFilter = null;

  private final Counter<Label> precisions;
  private final Counter<Label> recalls;
  private final Counter<Label> f1s;

  private final Counter<Label> precisions2;
  private final Counter<Label> recalls2;
  private final Counter<Label> pnums2;
  private final Counter<Label> rnums2;


  public EvalbByCat(String str, boolean runningAverages) {
    super(str, runningAverages);

    evalb = new Evalb(str, false);
    precisions = new ClassicCounter<>();
    recalls = new ClassicCounter<>();
    f1s = new ClassicCounter<>();

    precisions2 = new ClassicCounter<>();
    recalls2 = new ClassicCounter<>();
    pnums2 = new ClassicCounter<>();
    rnums2 = new ClassicCounter<>();
  }
  
  public EvalbByCat(String str, boolean runningAverages, String labelRegex) {
    this(str, runningAverages);
    if (labelRegex != null) {
      pLabelFilter = Pattern.compile(labelRegex.trim());
    }
  }

  @Override
  protected Set<Constituent> makeObjects(Tree tree) {
    return evalb.makeObjects(tree);
  }

  private Map<Label,Set<Constituent>> makeObjectsByCat(Tree t) {
    Map<Label,Set<Constituent>> objMap = Generics.newHashMap();
    Set<Constituent> objSet = makeObjects(t);
    for (Constituent lc : objSet) {
      Label l = lc.label();
      if (!objMap.keySet().contains(l)) {
        objMap.put(l, Generics.<Constituent>newHashSet());
      }
      objMap.get(l).add(lc);
    }
    return objMap;
  }

  @Override
  public void evaluate(Tree guess, Tree gold, PrintWriter pw) {
    if(gold == null || guess == null) {
      System.err.printf("%s: Cannot compare against a null gold or guess tree!%n",this.getClass().getName());
      return;
    }

    Map<Label,Set<Constituent>> guessDeps = makeObjectsByCat(guess);
    Map<Label,Set<Constituent>> goldDeps = makeObjectsByCat(gold);
    Set<Label> cats = Generics.newHashSet(guessDeps.keySet());
    cats.addAll(goldDeps.keySet());

    if (pw != null && runningAverages) {
      pw.println("========================================");
      pw.println("Labeled Bracketed Evaluation by Category");
      pw.println("========================================");
    }

    ++num;

    for (Label cat : cats) {
      Set<Constituent> thisGuessDeps = guessDeps.containsKey(cat) ? guessDeps.get(cat) : Generics.<Constituent>newHashSet();
      Set<Constituent> thisGoldDeps = goldDeps.containsKey(cat) ? goldDeps.get(cat) : Generics.<Constituent>newHashSet();

      double currentPrecision = precision(thisGuessDeps, thisGoldDeps);
      double currentRecall = precision(thisGoldDeps, thisGuessDeps);
      double currentF1 = (currentPrecision > 0.0 && currentRecall > 0.0 ? 2.0 / (1.0 / currentPrecision + 1.0 / currentRecall) : 0.0);

      precisions.incrementCount(cat, currentPrecision);
      recalls.incrementCount(cat, currentRecall);
      f1s.incrementCount(cat, currentF1);

      precisions2.incrementCount(cat, thisGuessDeps.size() * currentPrecision);
      pnums2.incrementCount(cat, thisGuessDeps.size());

      recalls2.incrementCount(cat, thisGoldDeps.size() * currentRecall);
      rnums2.incrementCount(cat, thisGoldDeps.size());

      if (pw != null && runningAverages) {
        pw.println(cat + "\tP: " + ((int) (currentPrecision * 10000)) / 100.0 + " (sent ave " + ((int) (precisions.getCount(cat) * 10000 / num)) / 100.0 + ") (evalb " + ((int) (precisions2.getCount(cat) * 10000 / pnums2.getCount(cat))) / 100.0 + ")");
        pw.println("\tR: " + ((int) (currentRecall * 10000)) / 100.0 + " (sent ave " + ((int) (recalls.getCount(cat) * 10000 / num)) / 100.0 + ") (evalb " + ((int) (recalls2.getCount(cat) * 10000 / rnums2.getCount(cat))) / 100.0 + ")");
        double cF1 = 2.0 / (rnums2.getCount(cat) / recalls2.getCount(cat) + pnums2.getCount(cat) / precisions2.getCount(cat));
        String emit = str + " F1: " + ((int) (currentF1 * 10000)) / 100.0 + " (sent ave " + ((int) (10000 * f1s.getCount(cat) / num)) / 100.0 + ", evalb " + ((int) (10000 * cF1)) / 100.0 + ")";
        pw.println(emit);
      }
    }
    if (pw != null && runningAverages) {
      pw.println("========================================");
    }
  }

  private Set<Label> getEvalLabelSet(Set<Label> labelSet) {
    if (pLabelFilter == null) {
      return Generics.newHashSet(precisions.keySet());
    } else {
      Set<Label> evalSet = Generics.newHashSet(precisions.keySet().size());
      for (Label label : labelSet) {
        if (pLabelFilter.matcher(label.value()).matches()) {
          evalSet.add(label);
        }
      }
      return evalSet;
    }
  }
  
  @Override
  public void display(boolean verbose, PrintWriter pw) {
    if (precisions.keySet().size() != recalls.keySet().size()) {
      log.error("Different counts for precisions and recalls!");
      return;
    }
    final Set<Label> cats = getEvalLabelSet(precisions.keySet());
    final Random rand = new Random();

    Map<Double,Label> f1Map = new TreeMap<>();
    for (Label cat : cats) {
      double pnum2 = pnums2.getCount(cat);
      double rnum2 = rnums2.getCount(cat);
      double prec = precisions2.getCount(cat) / pnum2;
      double rec = recalls2.getCount(cat) / rnum2;
      double f1 = 2.0 / (1.0 / prec + 1.0 / rec);

      if (Double.valueOf(f1).equals(Double.NaN)) f1 = -1.0;
      if (f1Map.containsKey(f1)) {
        f1Map.put(f1 + (rand.nextDouble()/1000.0), cat);
      } else {
        f1Map.put(f1, cat);
      }
    }
    pw.println("============================================================");
    pw.println("Labeled Bracketed Evaluation by Category -- final statistics");
    pw.println("============================================================");
    // Per category
    double catPrecisions = 0.0;
    double catPrecisionNums = 0.0;
    double catRecalls = 0.0;
    double catRecallNums = 0.0;
    for (Label cat : f1Map.values()) {
      double pnum2 = pnums2.getCount(cat);
      double rnum2 = rnums2.getCount(cat);
      double prec = precisions2.getCount(cat) / pnum2;
      prec *= 100.0;
      double rec = recalls2.getCount(cat) / rnum2;
      rec *= 100.0;
      double f1 = 2.0 / (1.0 / prec + 1.0 / rec);

      catPrecisions += precisions2.getCount(cat);
      catPrecisionNums += pnum2;
      catRecalls += recalls2.getCount(cat);
      catRecallNums += rnum2;
      
      String LP = pnum2 == 0.0 ? "N/A" : String.format("%.2f", prec);
      String LR = rnum2 == 0.0 ? "N/A" : String.format("%.2f", rec);
      String F1 = (pnum2 == 0.0 || rnum2 == 0.0) ? "N/A": String.format("%.2f", f1);
      
      pw.printf("%s\tLP: %s\tguessed: %d\tLR: %s\tgold: %d\t F1: %s%n", 
          cat.value(),
          LP,
          (int) pnum2,
          LR,
          (int) rnum2,
          F1);
    }
    pw.println("============================================================");
    // Totals
    double prec = catPrecisions / catPrecisionNums;
    double rec = catRecalls / catRecallNums;
    double f1 = (2 * prec * rec) / (prec + rec);
    pw.printf("Total\tLP: %.2f\tguessed: %d\tLR: %.2f\tgold: %d\t F1: %.2f%n", 
        prec*100.0,
        (int) catPrecisionNums,
        rec*100.0,
        (int) catRecallNums,
        f1*100.0);
    pw.println("============================================================");
  }
}
