package edu.stanford.nlp.parser.metrics; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.*;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.text.DecimalFormat;

import edu.stanford.nlp.parser.KBestViterbiParser;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;

/**
 * A framework for Set-based precision/recall/F1 evaluation.
 *
 * @author Dan Klein
 */
public abstract class AbstractEval implements Eval  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(AbstractEval.class);

  private static final boolean DEBUG = false;

  protected final String str;
  protected final boolean runningAverages;

  private double precision = 0.0;
  private double recall = 0.0;
  private double f1 = 0.0;
  protected double num = 0.0;
  private double exact = 0.0;

  private double precision2 = 0.0;
  private double recall2 = 0.0;
  private double pnum2 = 0.0;
  private double rnum2 = 0.0;

  protected double curF1 = 0.0;

  public AbstractEval() {
    this(true);
  }

  public AbstractEval(boolean runningAverages) {
    this("", runningAverages);
  }

  public AbstractEval(String str) {
    this(str, true);
  }

  public AbstractEval(String str, boolean runningAverages) {
    this.str = str;
    this.runningAverages = runningAverages;
  }

  public double getSentAveF1() {
    return f1 / num;
  }

  public double getEvalbF1() {
    return 2.0 / (rnum2 / recall2 + pnum2 / precision2);
  }

  /**
   * Return the evalb F1% from the last call to {@link #evaluate}.
   *
   * @return The F1 percentage
   */
  public double getLastF1() {
    return curF1 * 100.0;
  }

  /** @return The evalb (micro-averaged) F1 times 100 to make it
   *  a number between 0 and 100.
   */
  public double getEvalbF1Percent() {
    return getEvalbF1() * 100.0;
  }

  public double getExact() {
    return exact / num;
  }

  public double getExactPercent() {
    return getExact() * 100.0;
  }

  public int getNum() {
    return (int) num;
  }

  // should be able to pass in a comparator!
  protected static double precision(Set<?> s1, Set<?> s2) {
    double n = 0.0;
    double p = 0.0;
    for (Object o1 : s1) {
      if (s2.contains(o1)) {
        p += 1.0;
      }
      if (DEBUG) {
        if (s2.contains(o1)) {
          log.info("Eval Found: "+o1);
        } else {
          log.info("Eval Failed to find: "+o1);
        }
      }
      n += 1.0;
    }
    if (DEBUG) log.info("Matched " + p + " of " + n);
    return (n > 0.0 ? p / n : 0.0);
  }

  protected abstract Set<?> makeObjects(Tree tree);

  public void evaluate(Tree guess, Tree gold) {
    evaluate(guess, gold, new PrintWriter(System.out, true));
  }

  /* Evaluates precision and recall by calling makeObjects() to make a
   * set of structures for guess Tree and gold Tree, and compares them
   * with each other.
   */
  public void evaluate(Tree guess, Tree gold, PrintWriter pw) {
    evaluate(guess, gold, pw, 1.0);
  }

  public void evaluate(Tree guess, Tree gold, PrintWriter pw, double weight) {
    if (DEBUG) {
      log.info("Evaluating gold tree:");
      gold.pennPrint(System.err);
      log.info("and guess tree");
      guess.pennPrint(System.err);
    }
    Set<?> dep1 = makeObjects(guess);
    Set<?> dep2 = makeObjects(gold);
    final double curPrecision = precision(dep1, dep2);
    final double curRecall = precision(dep2, dep1);
    curF1 = (curPrecision > 0.0 && curRecall > 0.0 ? 2.0 / (1.0 / curPrecision + 1.0 / curRecall) : 0.0);
    precision += curPrecision * weight;
    recall += curRecall * weight;
    f1 += curF1 * weight;
    num += weight;

    precision2 += dep1.size() * curPrecision * weight;
    pnum2 += dep1.size() * weight;

    recall2 += dep2.size() * curRecall * weight;
    rnum2 += dep2.size() * weight;

    if (curF1 > 0.9999) {
      exact += 1.0;
    }
    if (pw != null) {
      pw.print(" P: " + ((int) (curPrecision * 10000)) / 100.0);
      if (runningAverages) {
        pw.println(" (sent ave " + ((int) (precision * 10000 / num)) / 100.0 + ") (evalb " + ((int) (precision2 * 10000 / pnum2)) / 100.0 + ")");
      }
      pw.print(" R: " + ((int) (curRecall * 10000)) / 100.0);
      if (runningAverages) {
        pw.print(" (sent ave " + ((int) (recall * 10000 / num)) / 100.0 + ") (evalb " + ((int) (recall2 * 10000 / rnum2)) / 100.0 + ")");
      }
      pw.println();
      double cF1 = 2.0 / (rnum2 / recall2 + pnum2 / precision2);
      pw.print(str + " F1: " + ((int) (curF1 * 10000)) / 100.0);
      if (runningAverages) {
        pw.print(" (sent ave " + ((int) (10000 * f1 / num)) / 100.0 + ", evalb " + ((int) (10000 * cF1)) / 100.0 + ")   Exact: " + ((int) (10000 * exact / num)) / 100.0);
      }
//      pw.println(" N: " + getNum());
      pw.println(" N: " + num);
    }
    /*
      Sentence s = guess.yield();
      for (Object obj : s) {
        if (curF1 < 0.7) {
          badwords.incrementCount(obj);
        } else {
          goodwords.incrementCount(obj);
        }
      }
    */
  }

  /*
  private Counter goodwords = new Counter();
  private Counter badwords = new Counter();

  public void printGoodBad() {
    System.out.println("Printing bad categories");
    for (Object key : Counters.keysAbove(badwords, 5.0)) {
      System.out.println("In badwords 5 times: " + key);
      double numb = badwords.getCount(key);
      double numg = goodwords.getCount(key);
      if (numb / (numb + numg) > 0.1) {
        System.out.println("Bad word!  " + key + " (" +
                           (numb / (numb + numg)) + " bad)");
        // EncodingPrintWriter.out.println("Bad word!  " + key + " (" +
        //                 (numb / (numb + numg)) + " bad)",
        //                              "GB18030");
      }
    }
  }
  */

  public void display(boolean verbose) {
    display(verbose, new PrintWriter(System.out, true));
  }

  public void display(boolean verbose, PrintWriter pw) {
    double prec = precision2 / pnum2;//(num > 0.0 ? precision/num : 0.0);
    double rec = recall2 / rnum2;//(num > 0.0 ? recall/num : 0.0);
    double f = 2.0 / (1.0 / prec + 1.0 / rec);//(num > 0.0 ? f1/num : 0.0);
    //System.out.println(" Precision: "+((int)(10000.0*prec))/100.0);
    //System.out.println(" Recall:    "+((int)(10000.0*rec))/100.0);
    //System.out.println(" F1:        "+((int)(10000.0*f))/100.0);
    pw.println(str + " summary evalb: LP: " + ((int) (10000.0 * prec)) / 100.0 + " LR: " + ((int) (10000.0 * rec)) / 100.0 + " F1: " + ((int) (10000.0 * f)) / 100.0 + " Exact: " + ((int) (10000.0 * exact / num)) / 100.0 + " N: " + getNum());
    /*
    double prec = (num > 0.0 ? precision/num : 0.0);
    double rec = (num > 0.0 ? recall/num : 0.0);
    double f = (num > 0.0 ? f1/num : 0.0);
    System.out.println(" Precision: "+prec);
    System.out.println(" Recall:    "+rec);
    System.out.println(" F1:        "+f);
    */
  }


  public static class RuleErrorEval extends AbstractEval {

    //private boolean verbose = false;

    private ClassicCounter<String> over = new ClassicCounter<>();
    private ClassicCounter<String> under = new ClassicCounter<>();

    protected static String localize(Tree tree) {
      if (tree.isLeaf()) {
        return "";
      }
      StringBuilder sb = new StringBuilder();
      sb.append(tree.label());
      sb.append(" ->");
      for (int i = 0; i < tree.children().length; i++) {
        sb.append(' ');
        sb.append(tree.children()[i].label());
      }
      return sb.toString();
    }

    @Override
    protected Set<String> makeObjects(Tree tree) {
      Set<String> localTrees = Generics.newHashSet();
      for (Tree st : tree.subTreeList()) {
        localTrees.add(localize(st));
      }
      return localTrees;
    }

    @Override
    public void evaluate(Tree t1, Tree t2, PrintWriter pw) {
      Set<String> s1 = makeObjects(t1);
      Set<String> s2 = makeObjects(t2);
      for (String o1 : s1) {
        if (!s2.contains(o1)) {
          over.incrementCount(o1);
        }
      }
      for (String o2 : s2) {
        if (!s1.contains(o2)) {
          under.incrementCount(o2);
        }
      }
    }

    private static <T> void display(ClassicCounter<T> c, int num, PrintWriter pw) {
      List<T> rules = new ArrayList<>(c.keySet());
      Collections.sort(rules, Counters.toComparatorDescending(c));
      int rSize = rules.size();
      if (num > rSize) {
        num = rSize;
      }
      for (int i = 0; i < num; i++) {
        pw.println(rules.get(i) + " " + c.getCount(rules.get(i)));
      }
    }

    @Override
    public void display(boolean verbose, PrintWriter pw) {
      //this.verbose = verbose;
      pw.println("Most frequently underproposed rules:");
      display(under, (verbose ? 100 : 10), pw);
      pw.println("Most frequently overproposed rules:");
      display(over, (verbose ? 100 : 10), pw);
    }

    public RuleErrorEval(String str) {
      super(str);
    }

  } // end class RuleErrorEval


  /** This class counts which categories are over and underproposed in trees.
   */
  public static class CatErrorEval extends AbstractEval {

    private ClassicCounter<String> over = new ClassicCounter<>();
    private ClassicCounter<String> under = new ClassicCounter<>();

    /** Unused. Fake satisfying the abstract class. */
    @Override
    protected Set<?> makeObjects(Tree tree) {
      return null;
    }

    private static List<String> myMakeObjects(Tree tree) {
      List<String> cats = new LinkedList<>();
      for (Tree st : tree.subTreeList()) {
        cats.add(st.value());
      }
      return cats;
    }

    @Override
    public void evaluate(Tree t1, Tree t2, PrintWriter pw) {
      List<String> s1 = myMakeObjects(t1);
      List<String> s2 = myMakeObjects(t2);
      List<String> del2 = new LinkedList<>(s2);
      // we delete out as we find them so we can score correctly a cat with
      // a certain cardinality in a tree.
      for (String o1 : s1) {
        if ( ! del2.remove(o1)) {
          over.incrementCount(o1);
        }
      }
      for (String o2 : s2) {
        if (! s1.remove(o2)) {
          under.incrementCount(o2);
        }
      }
    }

    private static <T> void display(ClassicCounter<T> c, PrintWriter pw) {
      List<T> cats = new ArrayList<>(c.keySet());
      Collections.sort(cats, Counters.toComparatorDescending(c));
      for (T ob : cats) {
        pw.println(ob + " " + c.getCount(ob));
      }
    }

    @Override
    public void display(boolean verbose, PrintWriter pw) {
      pw.println("Most frequently underproposed categories:");
      display(under, pw);
      pw.println("Most frequently overproposed categories:");
      display(over, pw);
    }

    public CatErrorEval(String str) {
      super(str);
    }

  } // end class CatErrorEval


  /** This isn't really a kind of AbstractEval: we're sort of cheating here. */
  public static class ScoreEval extends AbstractEval {

    double totScore = 0.0;
    double n = 0.0;
    NumberFormat nf = new DecimalFormat("0.000");

    @Override
    protected Set<?> makeObjects(Tree tree) {
      return null;
    }

    public void recordScore(KBestViterbiParser parser, PrintWriter pw) {
      double score = parser.getBestScore();
      totScore += score;
      n++;
      if (pw != null) {
        pw.print(str + " score: " + nf.format(score));
        if (runningAverages) {
          pw.print(" average score: " + nf.format(totScore / n));
        }
        pw.println();
      }
    }

    @Override
    public void display(boolean verbose, PrintWriter pw) {
      if (pw != null) {
        pw.println(str + " total score: " + nf.format(totScore) +
                " average score: " + ((n == 0.0) ? "N/A": nf.format(totScore / n)));
      }
    }

    public ScoreEval(String str, boolean runningAverages) {
      super(str, runningAverages);
    }

  } // end class DependencyEval

} // end class AbstractEval
