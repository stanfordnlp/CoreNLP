package edu.stanford.nlp.stats;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Sets;

/**
 * A class for calculating precision and recall statistics based on
 * comparisons between two {@link Collection}s.
 * Allows flexible specification of:
 * <p/>
 * <ul>
 * <li>The criterion by which to evaluate whether two Objects are equivalent
 * for purposes of precision and recall
 * calculation (specified by an {@link EqualityChecker} instance)
 * <li>The criterion by which Objects are grouped into equivalence classes
 * for purposes of calculating subclass precision
 * and recall (specified by an {@link EquivalenceClasser} instance)
 * <li>Evaluation is set-based or bag-based (by default, it is set-based). For example, if a gold collection
 * has {a,a,b} and a guess collection has {a,b}, then recall is 100% in set-based
 * evaluation, but is 66.67% in bag-based evaluation.
 * </ul>
 *
 * Note that for set-based evaluation, sets are always constructed using object equality, NOT
 * equality on the basis of an {@link EqualityChecker} if one is given.  If set-based evaluation
 * were conducted on the basis of an EqualityChecker, then there would be indeterminacy when it did not subsume the {@link EquivalenceClasser},
 * if one was given. For example, if objects of the form
 * X:y were equivalence-classed by the left criterion and evaluated for equality on the right, then set-based
 * evaluation based on the equality checker would be indeterminate for a collection of {A:a,B:a}
 * because it would be unclear whether to use the first or second element of the collection.
 *
 * @author Roger Levy
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) Attempt at templatization... this may be a failure.
 */
public class EquivalenceClassEval<IN, OUT> {

 /** If bagEval is set to <code>true</code>, then multiple instances of the same item will not be merged.  For example,
   * gold (a,a,b) against guess (a,b) will be scored as 100% precision and 66.67% recall. It is <code>false</code>
  * by default.*/
  public void setBagEval(boolean bagEval) {
    this.bagEval = bagEval;
  }

  protected boolean bagEval = false;


  /**
   * Maps all objects to the equivalence class <code>null</code>
   */
  @SuppressWarnings("unchecked")
  public static final EquivalenceClasser NULL_EQUIVALENCE_CLASSER = new EquivalenceClasser() {
    public Object equivalenceClass(Object o) {
      return null;
    }
  };
  
  public static final  <T,U> EquivalenceClasser<T,U> nullEquivalenceClasser() {
    return ErasureUtils.<EquivalenceClasser<T,U>>uncheckedCast(NULL_EQUIVALENCE_CLASSER);
  }

  private boolean verbose = false;

  EquivalenceClasser<IN, OUT> eq;
  Eval.CollectionContainsChecker<IN> checker;

  String summaryName;

  /**
   * Specifies a default EquivalenceClassEval, using {@link Object#equals(java.lang.Object)} as equality criterion
   * and grouping all items into the "null" equivalence class for reporting purposes
   */
  public EquivalenceClassEval() {
    this(EquivalenceClassEval.<IN,OUT>nullEquivalenceClasser());
  }


  /**
   * Specifies an EquivalenceClassEval using {@link Object#equals(java.lang.Object)} as equality criterion
   * and grouping all items according to the EquivalenceClasser argument.
   */
  public EquivalenceClassEval(EquivalenceClasser<IN, OUT> eq) {
    this(eq, "");
  }

  /**
   * Specifies an EquivalenceClassEval using the Eval.EqualityChecker argument as equality criterion
   * and grouping all items into a single equivalence class for reporting statistics.
   */
  public EquivalenceClassEval(EqualityChecker<IN> e) {
    this(EquivalenceClassEval.<IN,OUT>nullEquivalenceClasser(), e);
  }

  /**
   * Specifies an EquivalenceClassEval using {@link Object#equals(java.lang.Object)} as equality criterion
   * and grouping all items according to the EquivalenceClasser argument.
   */
  public EquivalenceClassEval(EquivalenceClasser<IN, OUT> eq, String name) {
    this(eq, EquivalenceClassEval.<IN>defaultChecker(), name);
  }

  /**
   * Specifies an EquivalenceClassEval using the Eval.EqualityChecker argument as equality criterion
   * and grouping all items according to the EquivalenceClasser argument.
   */
  public EquivalenceClassEval(EquivalenceClasser<IN, OUT> eq, EqualityChecker<IN> e) {
    this(eq, e, "");
  }

  /**
   * Specifies an EquivalenceClassEval using the Eval.EqualityChecker argument as equality criterion
   * and grouping all items according to the EquivalenceClasser argument.
   */
  public EquivalenceClassEval(EquivalenceClasser<IN, OUT> eq, EqualityChecker<IN> e, String summaryName) {
    this(eq, new Eval.CollectionContainsChecker<IN>(e), summaryName);
  }

  EquivalenceClassEval(EquivalenceClasser<IN, OUT> eq, Eval.CollectionContainsChecker<IN> checker, String summaryName) {
    this.eq = eq;
    this.checker = checker;
    this.summaryName = summaryName;
  }

  ClassicCounter<OUT> guessed = new ClassicCounter<OUT>();
  ClassicCounter<OUT> guessedCorrect = new ClassicCounter<OUT>();
  ClassicCounter<OUT> gold = new ClassicCounter<OUT>();
  ClassicCounter<OUT> goldCorrect = new ClassicCounter<OUT>();

  private ClassicCounter<OUT> lastPrecision = new ClassicCounter<OUT>();
  private ClassicCounter<OUT> lastRecall = new ClassicCounter<OUT>();
  private ClassicCounter<OUT> lastF1 = new ClassicCounter<OUT>();

  private ClassicCounter<OUT> previousGuessed;
  private ClassicCounter<OUT> previousGuessedCorrect;
  private ClassicCounter<OUT> previousGold;
  private ClassicCounter<OUT> previousGoldCorrect;


  //Eval eval = new Eval();

  /**
   * Adds a round of evaluation between guesses and golds {@link Collection}s to the tabulated statistics of
   * the evaluation.
   */
  public void eval(Collection<IN> guesses, Collection<IN> golds) {
    eval(guesses, golds, new PrintWriter(System.out, true));
  }

  // this one is all side effects
  /**
   * @param guesses Collection of guessed objects
   * @param golds   Collection of gold-standard objects
   * @param pw      {@link PrintWriter} to print eval stats
   */
  public void eval(Collection<IN> guesses, Collection<IN> golds, PrintWriter pw) {
    if (verbose) {
      System.out.println("evaluating precision...");
    }
    Pair<ClassicCounter<OUT>, ClassicCounter<OUT>> precision = evalPrecision(guesses, golds);
    previousGuessed = precision.first();
    Counters.addInPlace(guessed, previousGuessed);
    previousGuessedCorrect = precision.second();
    Counters.addInPlace(guessedCorrect, previousGuessedCorrect);

    if (verbose) {
      System.out.println("evaluating recall...");
    }
    Pair<ClassicCounter<OUT>, ClassicCounter<OUT>> recall = evalPrecision(golds, guesses);
    previousGold = recall.first();
    Counters.addInPlace(gold, previousGold);
    previousGoldCorrect = recall.second();
    Counters.addInPlace(goldCorrect, previousGoldCorrect);
  }

  /* returns a Pair of each */
  Pair<ClassicCounter<OUT>, ClassicCounter<OUT>> evalPrecision(Collection<IN> guesses, Collection<IN> golds) {
    Collection<IN> internalGuesses = null;
    Collection<IN> internalGolds = null;
    if(bagEval) {
      internalGuesses = new ArrayList<IN>(guesses.size());
      internalGolds = new ArrayList<IN>(golds.size());
    }
    else {
      internalGuesses = new HashSet<IN>(guesses.size());
      internalGolds = new HashSet<IN>(golds.size());
    }
    internalGuesses.addAll(guesses);
    internalGolds.addAll(golds);
    ClassicCounter<OUT> thisGuessed = new ClassicCounter<OUT>();
    ClassicCounter<OUT> thisCorrect = new ClassicCounter<OUT>();
    for (IN o : internalGuesses) {
      OUT equivalenceClass = eq.equivalenceClass(o);
      thisGuessed.incrementCount(equivalenceClass);
      if (checker.contained(o, internalGolds)) {
        thisCorrect.incrementCount(equivalenceClass);
        removeItem(o,internalGolds,checker);
      } else {
        if (verbose) {
          System.out.println("Eval missed " + o);
        }
      }
    }
    return Generics.newPair(thisGuessed, thisCorrect);
  }

  /* there is some discomfort here, we should really be using an EqualityChecker for checker, but
   * I screwed up the API. */
  protected static <T> void removeItem(T o, Collection<T> c, Eval.CollectionContainsChecker<T> checker) {
    for(T o1 : c) {
      if(checker.contained(o,Collections.singleton(o1))) {
        c.remove(o1);
        return;
      }
    }
  }


  /**
   * Displays the cumulative results of the evaluation to {@link System#out}.
   */
  public void display() {
    display(new PrintWriter(System.out, true));
  }

  /**
   * Displays the cumulative results of the evaluation.
   */
  public void display(PrintWriter pw) {
    pw.println("*********Final " + summaryName + " eval stats by antecedent category***********");
    Set<OUT> keys = new HashSet<OUT>();
    keys.addAll(guessed.keySet());
    keys.addAll(gold.keySet());
    displayHelper(keys, pw, guessed, guessedCorrect, gold, goldCorrect);
    pw.println("Finished final " + summaryName + " eval stats.");
  }

  /**
   * Displays the results of the previous Collection pair evaluation to {@link System#out}.
   */
  public void displayLast() {
    displayLast(new PrintWriter(System.out, true));
  }

  /**
   * Displays the results of the previous Collection pair evaluation.
   */
  public void displayLast(PrintWriter pw) {
    Set<OUT> keys = new HashSet<OUT>();
    keys.addAll(previousGuessed.keySet());
    keys.addAll(previousGold.keySet());
    displayHelper(keys, pw, previousGuessed, previousGuessedCorrect, previousGold, previousGoldCorrect);
  }

  public double precision(OUT key) {
    return percentage(key, guessed, guessedCorrect);
  }

  public double recall(OUT key) {
    return percentage(key, gold, goldCorrect);
  }

  public double lastPrecision(OUT key) {
    return percentage(key, previousGuessed, previousGuessedCorrect);
  }

  public ClassicCounter<OUT> lastPrecision() {
    ClassicCounter<OUT> result = new ClassicCounter<OUT>();
    Counters.addInPlace(result, previousGuessedCorrect);
    Counters.divideInPlace(result, previousGuessed);
    return result;
  }

  public double lastRecall(OUT key) {
    return percentage(key, previousGold, previousGoldCorrect);
  }

  public ClassicCounter<OUT> lastRecall() {
    ClassicCounter<OUT> result = new ClassicCounter<OUT>();
    Counters.addInPlace(result, previousGoldCorrect);
    Counters.divideInPlace(result, previousGold);
    return result;
  }

  public double lastNumGuessed(OUT key) {
    return previousGuessed.getCount(key);
  }

  public ClassicCounter<OUT> lastNumGuessed() {
    return previousGuessed;
  }

  public ClassicCounter<OUT> lastNumGuessedCorrect() {
    return previousGuessedCorrect;
  }

  public double lastNumGolds(OUT key) {
    return previousGold.getCount(key);
  }

  public ClassicCounter<OUT> lastNumGolds() {
    return previousGold;
  }

  public ClassicCounter<OUT> lastNumGoldsCorrect() {
    return previousGoldCorrect;
  }


  public double f1(OUT key) {
    return f1(precision(key), recall(key));
  }

  public double lastF1(OUT key) {
    return f1(lastPrecision(key), lastRecall(key));
  }

  public ClassicCounter<OUT> lastF1() {
    ClassicCounter<OUT> result = new ClassicCounter<OUT>();
    Set<OUT> keys = Sets.union(previousGuessed.keySet(),previousGold.keySet());
    for(OUT key : keys) {
      result.setCount(key,lastF1(key));
    }
    return result;
  }

  public static double f1(double precision, double recall) {
    return (precision == 0.0 || recall == 0.0) ? 0.0 : (2 * precision * recall) / (precision + recall);
  }

  public static <E> Counter<E> f1(Counter<E> precision, Counter<E> recall) {
    Counter<E> result = precision.getFactory().create();
    for(E key : Sets.intersection(precision.keySet(),recall.keySet())) {
      result.setCount(key,f1(precision.getCount(key),recall.getCount(key)));
    }
    return result;
  }

  private double percentage(OUT key, ClassicCounter<OUT> guessed, ClassicCounter<OUT> guessedCorrect) {
    double thisGuessed = guessed.getCount(key);
    double thisGuessedCorrect = guessedCorrect.getCount(key);
    return (thisGuessed == 0.0) ? 0.0 : thisGuessedCorrect / thisGuessed;
  }

  private void displayHelper(Set<OUT> keys, PrintWriter pw, ClassicCounter<OUT> guessed, ClassicCounter<OUT> guessedCorrect, ClassicCounter<OUT> gold, ClassicCounter<OUT> goldCorrect) {
    Map<OUT, String> pads = getPads(keys);
    for (OUT key : keys) {
      double thisGuessed = guessed.getCount(key);
      double thisGuessedCorrect = guessedCorrect.getCount(key);
      double precision = (thisGuessed == 0.0) ? 0.0 : thisGuessedCorrect / thisGuessed;
      lastPrecision.setCount(key, precision);
      double thisGold = gold.getCount(key);
      double thisGoldCorrect = goldCorrect.getCount(key);
      double recall = (thisGold == 0.0) ? 0.0 : thisGoldCorrect / thisGold;
      lastRecall.setCount(key, recall);
      double f1 = f1(precision, recall);
      lastF1.setCount(key, f1);
      String pad = pads.get(key);
      pw.println(key + pad + "\t" + "P: " + formatNumber(precision) + "\ton " + formatCount(thisGuessed) + " objects\tR: " + formatNumber(recall) + "\ton " + formatCount(thisGold) + " objects\tF1: " + formatNumber(f1));
    }
  }

  //   public static String formatNumber(double d) {
  //     double frac = d % 1.0;
  //     int whole = (int) Math.round(d - frac);
  //     int frac1 = (int) Math.round(frac * 1000);
  //     String prePad = "";
  //     if(whole < 1000)
  //       prePad += " ";
  //     if(whole > 100)
  //       prePad += " ";
  //     if(whole > 10)
  //       prePad += " ";

  //     return pad + whole + "." + frac1;
  //   }

  private static java.text.NumberFormat numberFormat = java.text.NumberFormat.getNumberInstance();

  {
    numberFormat.setMaximumFractionDigits(4);
    numberFormat.setMinimumFractionDigits(4);
    numberFormat.setMinimumIntegerDigits(1);
    numberFormat.setMaximumIntegerDigits(1);
  }


  private static String formatNumber(double d) {
    return numberFormat.format(d);
  }

  private static int formatCount(double d) {
    return (int) Math.round(d);
  }

  /* find pads for each key based on length of longest key */
  private static <OUT> Map<OUT, String> getPads(Set<OUT> keys) {
    Map<OUT, String> pads = new HashMap<OUT, String>();
    int max = 0;
    for (OUT key : keys) {
      String keyString = key==null ? "null" : key.toString();
      if (keyString.length() > max) {
        max = keyString.length();
      }
    }
    for (OUT key : keys) {
      String keyString = key==null ? "null" : key.toString();
      int diff = max - keyString.length();
      String pad = "";
      for (int j = 0; j < diff; j++) {
        pad += " ";
      }
      pads.put(key, pad);
    }
    return pads;
  }

  public static void main(String[] args) {
    final Pattern p = Pattern.compile("^([^:]*):(.*)$");
    Collection<String> guesses = Arrays.asList(new String[]{"S:a", "S:b", "VP:c", "VP:d", "S:a"});
    Collection<String> golds = Arrays.asList(new String[]{"S:a", "S:b", "S:b", "VP:d", "VP:a"});
    EqualityChecker<String> e = new EqualityChecker<String>() {
      public boolean areEqual(String o1, String o2) {
        Matcher m1 = p.matcher(o1);
        m1.find();
        String s1 = m1.group(2);
        System.out.println(s1);
        Matcher m2 = p.matcher(o2);
        m2.find();
        String s2 = m2.group(2);
        System.out.println(s2);
        return s1.equals(s2);
      }
    };
    EquivalenceClasser<String, String> eq = new EquivalenceClasser<String, String>() {
      public String equivalenceClass(String o) {
        Matcher m = p.matcher(o);
        m.find();
        return m.group(1);
      }
    };
    EquivalenceClassEval<String, String> eval = new EquivalenceClassEval<String, String>(eq, e, "testing");
    eval.setBagEval(false);
    eval.eval(guesses, golds);
    eval.displayLast();
    eval.display();

  }


  /**
   * A strategy-type interface for specifying an equality criterion for pairs of {@link Object}s.
   *
   * @author Roger Levy
   */
  public interface EqualityChecker<T> {

    /**
     * Returns <code>true</code> iff <code>o1</code> and <code>o2</code> are equal by the desired
     * evaluation criterion.
     */
    public boolean areEqual(T o1, T o2);

  }

  /**
   * A default equality checker that uses {@link Object#equals} to determine equality.
   */
  @SuppressWarnings("unchecked")
  public static final EqualityChecker DEFAULT_CHECKER = new EqualityChecker() {
    public boolean areEqual(Object o1, Object o2) {
      return o1.equals(o2);
    }
  };
  
  @SuppressWarnings("unchecked")
  public static final <T> EqualityChecker<T> defaultChecker() {
    return DEFAULT_CHECKER;
  }

  static class Eval<T> {

    private boolean bagEval = false;

    public Eval(EqualityChecker<T> e) {
      this(false,e);
    }

    public Eval() {
      this(false);
    }

    public Eval(boolean bagEval) {
      this(bagEval,EquivalenceClassEval.<T>defaultChecker());
    }

    public Eval(boolean bagEval, EqualityChecker<T> e) {
      checker = new CollectionContainsChecker<T>(e);
      this.bagEval = bagEval;
    }

    CollectionContainsChecker<T> checker;

    /* a filter that returns true iff the object is a collection that contains currentItem */
    static class CollectionContainsChecker<T> {
      EqualityChecker<T> e;

      public CollectionContainsChecker(EqualityChecker<T> e) {
        this.e = e;
      }

      public boolean contained(T obj, Collection<T> coll) {
        for (T o : coll) {
          if (e.areEqual(obj, o)) {
            return true;
          }
        }
        return false;
      }
    } // end class CollectionContainsChecker

    double guessed = 0.0;
    double guessedCorrect = 0.0;
    double gold = 0.0;
    double goldCorrect = 0.0;

    double lastPrecision;
    double lastRecall;
    double lastF1;

    public void eval(Collection<T> guesses, Collection<T> golds) {
      eval(guesses, golds, new PrintWriter(System.out, true));
    }

    // this one is all side effects
    public void eval(Collection<T> guesses, Collection<T> golds, PrintWriter pw) {
      double precision = evalPrecision(guesses, golds);
      lastPrecision = precision;
      double recall = evalRecall(guesses, golds);
      lastRecall = recall;
      double f1 = (2 * precision * recall) / (precision + recall);
      lastF1 = f1;
      guessed += guesses.size();
      guessedCorrect += (guesses.size() == 0.0 ? 0.0 : precision * guesses.size());
      gold += golds.size();
      goldCorrect += (golds.size() == 0.0 ? 0.0 : recall * golds.size());
      pw.println("This example:\tP:\t" + precision + " R:\t" + recall + " F1:\t" + f1);
      double cumPrecision = guessedCorrect / guessed;
      double cumRecall = goldCorrect / gold;
      double cumF1 = (2 * cumPrecision * cumRecall) / (cumPrecision + cumRecall);
      pw.println("Cumulative:\tP:\t" + cumPrecision + " R:\t" + cumRecall + " F1:\t" + cumF1);
    }

    // this has no side effects!
    public double evalPrecision(Collection<T> guesses, Collection<T> golds) {
      Collection<T> internalGuesses;
      Collection<T> internalGolds;
      if(bagEval) {
        internalGuesses = new ArrayList<T>(guesses.size());
        internalGolds = new ArrayList<T>(golds.size());
      } else {
        internalGuesses = new HashSet<T>(guesses.size());
        internalGolds = new HashSet<T>(golds.size());
      }
      internalGuesses.addAll(guesses);
      internalGolds.addAll(golds);
      double thisGuessed = 0.0;
      double thisGuessedCorrect = 0.0;
      for (T o: internalGuesses) {
        thisGuessed += 1.0;
        if (checker.contained(o, internalGolds)) {
          thisGuessedCorrect += 1.0;
           removeItem(o,internalGolds,checker);
        }
        //       else
        // 	System.out.println("Precision eval missed " + o);
      }
      return thisGuessedCorrect / thisGuessed;
    }

    // no side effects here either
    public double evalRecall(Collection<T> guesses, Collection<T> golds) {
      double thisGold = 0.0;
      double thisGoldCorrect = 0.0;
      for (T o : golds) {
        thisGold += 1.0;
        if (guesses.contains(o)) {
          thisGoldCorrect += 1.0;
        }
        //       else
        // 	System.out.println("Recall eval missed " + o);
      }
      return thisGoldCorrect / thisGold;
    }

    public void display() {
      display(new PrintWriter(System.out, true));
    }

    public void display(PrintWriter pw) {
      double precision = guessedCorrect / guessed;
      double recall = goldCorrect / gold;
      double f1 = (2 * precision * recall) / (precision + recall);
      pw.println("*********Final eval stats***********");
      pw.println("P:\t" + precision + " R:\t" + recall + " F1:\t" + f1);
    }

  }

  public static interface Factory<IN, OUT> {
    public EquivalenceClassEval<IN, OUT> equivalenceClassEval();
  }

  /**
   * returns a new {@link Factory} instance that vends new EquivalenceClassEval instances with
   * settings like <code>this</code>
   */
  public Factory<IN, OUT> factory() {
    return new Factory<IN, OUT>() {
      boolean bagEval1 = bagEval;
      EquivalenceClasser<IN, OUT> eq1 = eq;
      Eval.CollectionContainsChecker<IN> checker1 = checker;
      String summaryName1 = summaryName;

      public EquivalenceClassEval<IN, OUT> equivalenceClassEval() {
        EquivalenceClassEval<IN, OUT> e = new EquivalenceClassEval<IN, OUT>(eq1, checker1, summaryName1);
        e.setBagEval(bagEval1);
        return e;
      }
    };
  }

}
