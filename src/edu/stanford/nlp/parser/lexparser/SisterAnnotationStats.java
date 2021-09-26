package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.ling.StringLabelFactory;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Pair;

import java.io.Reader;
import java.text.NumberFormat;
import java.util.*;

/**
 * See what sister annotation helps in treebank, based on support and
 * KL divergence.  Some code borrowing from ParentAnnotationStats.
 *
 * @author Roger Levy
 * @version 2003/02
 */
public class SisterAnnotationStats implements TreeVisitor {


  public static final boolean DO_TAGS = true;


  /**
   * nodeRules is a HashMap -&gt; Counter: label-&gt;rewrite-&gt;count
   */
  private final Map nodeRules = new HashMap();

  /**
   * leftRules and rightRules are HashMap -&gt; HashMap -&gt; Counter:
   * label-&gt;sister_label-&gt;rewrite-&gt;count
   */
  private final Map leftRules = new HashMap();
  private final Map rightRules = new HashMap();

  /**
   * Minimum support * KL to be included in output and as feature
   */
  public static final double[] CUTOFFS = {250.0, 500.0, 1000.0, 1500.0};

  /**
   * Minimum support of parent annotated node for grandparent to be
   * studied.  Just there to reduce runtime and printout size.
   */
  public static final double SUPPCUTOFF = 100.0;

  /**
   * Does whatever one needs to do to a particular parse tree
   */
  public void visitTree(Tree t) {
    recurse(t, null);
  }

  /**
   * p is parent
   */
  public void recurse(Tree t, Tree p) {
    if (t.isLeaf() || (t.isPreTerminal() && (!DO_TAGS))) {
      return;
    }
    if (!(p == null || t.label().value().equals("ROOT"))) {
      sisterCounters(t, p);
    }
    Tree[] kids = t.children();
    for (Tree kid : kids) {
      recurse(kid, t);
    }
  }

  /**
   * string-value labels of left sisters; from inside to outside (right-left)
   */
  public static List<String> leftSisterLabels(Tree t, Tree p) {
    List<String> l = new ArrayList<>();
    if (p == null) {
      return l;
    }
    Tree[] kids = p.children();
    for (Tree kid : kids) {
      if (kid.equals(t)) {
        break;
      } else {
        l.add(0, kid.label().value());
      }
    }
    return l;
  }

  /**
   * string-value labels of right sisters; from inside to outside (left-right)
   */
  public static List<String> rightSisterLabels(Tree t, Tree p) {
    List<String> l = new ArrayList<>();
    if (p == null) {
      return l;
    }
    Tree[] kids = p.children();
    for (int i = kids.length - 1; i >= 0; i--) {
      if (kids[i].equals(t)) {
        break;
      } else {
        l.add(kids[i].label().value());
      }
    }
    return l;
  }


  public static List<String> kidLabels(Tree t) {
    Tree[] kids = t.children();
    List<String> l = new ArrayList<>(kids.length);
    for (Tree kid : kids) {
      l.add(kid.label().value());
    }
    return l;
  }

  protected void sisterCounters(Tree t, Tree p) {
    List rewrite = kidLabels(t);
    List left = leftSisterLabels(t, p);
    List right = rightSisterLabels(t, p);

    String label = t.label().value();

    if (!nodeRules.containsKey(label)) {
      nodeRules.put(label, new ClassicCounter());
    }

    if (!rightRules.containsKey(label)) {
      rightRules.put(label, new HashMap());
    }

    if (!leftRules.containsKey(label)) {
      leftRules.put(label, new HashMap());
    }


    ((ClassicCounter) nodeRules.get(label)).incrementCount(rewrite);


    sideCounters(label, rewrite, left, leftRules);
    sideCounters(label, rewrite, right, rightRules);

  }

  protected void sideCounters(String label, List rewrite, List sideSisters, Map sideRules) {
    for (Object sideSister : sideSisters) {
      String sis = (String) sideSister;

      if (!((Map) sideRules.get(label)).containsKey(sis)) {
        ((Map) sideRules.get(label)).put(sis, new ClassicCounter());
      }

      ((ClassicCounter) ((HashMap) sideRules.get(label)).get(sis)).incrementCount(rewrite);
    }
  }


  public void printStats() {
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(2);
    // System.out.println("Node rules");
    // System.out.println(nodeRules);
    // System.out.println("Parent rules");
    // System.out.println(pRules);
    // System.out.println("Grandparent rules");
    // System.out.println(gPRules);

    // Store java code for selSplit
    StringBuilder[] javaSB = new StringBuilder[CUTOFFS.length];
    for (int i = 0; i < CUTOFFS.length; i++) {
      javaSB[i] = new StringBuilder("  private static String[] sisterSplit" + (i + 1) + " = new String[] {");
    }

    /** topScores contains all enriched categories, to be sorted later */
    List<Pair> topScores = new ArrayList<>();

    for (Object o : nodeRules.keySet()) {
      List<Pair<String, Double>> answers = new ArrayList<>();
      String label = (String) o;
      ClassicCounter cntr = (ClassicCounter) nodeRules.get(label);
      double support = (cntr.totalCount());
      System.out.println("Node " + label + " support is " + support);


      for (Object o4 : ((HashMap) leftRules.get(label)).keySet()) {
        String sis = (String) o4;
        ClassicCounter cntr2 = (ClassicCounter) ((HashMap) leftRules.get(label)).get(sis);
        double support2 = (cntr2.totalCount());

        /* alternative 1: use full distribution to calculate score */
        double kl = Counters.klDivergence(cntr2, cntr);

        /* alternative 2: hold out test-context data to calculate score */
        /* this doesn't work because it can lead to zero-probability
         * data points hence infinite divergence */
        // 	Counter tempCounter = new Counter();
        // 	tempCounter.addCounter(cntr2);
        // 	for(Iterator i = tempCounter.seenSet().iterator(); i.hasNext();) {
        // 	  Object o = i.next();
        // 	  tempCounter.setCount(o,-1*tempCounter.countOf(o));
        // 	}
        // 	System.out.println(tempCounter); //debugging
        // 	tempCounter.addCounter(cntr);
        // 	System.out.println(tempCounter); //debugging
        // 	System.out.println(cntr);
        // 	double kl = cntr2.klDivergence(tempCounter);
        /* alternative 2 ends here */

        String annotatedLabel = label + "=l=" + sis;
        System.out.println("KL(" + annotatedLabel + "||" + label + ") = " + nf.format(kl) + "\t" + "support(" + sis + ") = " + support2);
        answers.add(new Pair<>(annotatedLabel, Double.valueOf(kl * support2)));
        topScores.add(new Pair<>(annotatedLabel, Double.valueOf(kl * support2)));
      }

      for (Object o3 : ((HashMap) rightRules.get(label)).keySet()) {
        String sis = (String) o3;
        ClassicCounter cntr2 = (ClassicCounter) ((HashMap) rightRules.get(label)).get(sis);
        double support2 = (cntr2.totalCount());
        double kl = Counters.klDivergence(cntr2, cntr);
        String annotatedLabel = label + "=r=" + sis;
        System.out.println("KL(" + annotatedLabel + "||" + label + ") = " + nf.format(kl) + "\t" + "support(" + sis + ") = " + support2);
        answers.add(new Pair(annotatedLabel, Double.valueOf(kl * support2)));
        topScores.add(new Pair(annotatedLabel, Double.valueOf(kl * support2)));
      }


      // upto

      System.out.println("----");
      System.out.println("Sorted descending support * KL");
      Collections.sort(answers, (o1, o2) -> {
        Pair p1 = o1;
        Pair p2 = o2;
        Double p12 = (Double) p1.second();
        Double p22 = (Double) p2.second();
        return p22.compareTo(p12);
      });
      for (Pair answer : answers) {
        Pair p = answer;
        double psd = ((Double) p.second()).doubleValue();
        System.out.println(p.first() + ": " + nf.format(psd));
        if (psd >= CUTOFFS[0]) {
          String annotatedLabel = (String) p.first();
          for (double CUTOFF : CUTOFFS) {
            if (psd >= CUTOFF) {
              //javaSB[j].append("\"").append(annotatedLabel);
              //javaSB[j].append("\",");
            }
          }
        }
      }
      System.out.println();
    }


    Collections.sort(topScores, (o1, o2) -> {
      Pair p1 = o1;
      Pair p2 = o2;
      Double p12 = (Double) p1.second();
      Double p22 = (Double) p2.second();
      return p22.compareTo(p12);
    });
    String outString = "All enriched categories, sorted by score\n";
    for (Pair topScore : topScores) {
      Pair p = topScore;
      double psd = ((Double) p.second()).doubleValue();
      System.out.println(p.first() + ": " + nf.format(psd));
    }


    System.out.println();
    System.out.println("  // Automatically generated by SisterAnnotationStats -- preferably don't edit");
    int k = CUTOFFS.length - 1;
    for (int j = 0; j < topScores.size(); j++) {
      Pair p = topScores.get(j);
      double psd = ((Double) p.second()).doubleValue();
      if (psd < CUTOFFS[k]) {
        if (k == 0) {
          break;
        } else {
          k--;
          j -= 1; // messy but should do it
          continue;
        }
      }
      javaSB[k].append("\"").append(p.first());
      javaSB[k].append("\",");
    }


    for (int i = 0; i < CUTOFFS.length; i++) {
      int len = javaSB[i].length();
      javaSB[i].replace(len - 2, len, "};");
      System.out.println(javaSB[i]);
    }
    System.out.print("  public static String[] sisterSplit = ");
    for (int i = CUTOFFS.length; i > 0; i--) {
      if (i == 1) {
        System.out.print("sisterSplit1");
      } else {
        System.out.print("selectiveSisterSplit" + i + " ? sisterSplit" + i + " : (");
      }
    }
    // need to print extra one to close other things open
    for (int i = CUTOFFS.length; i >= 0; i--) {
      System.out.print(")");
    }
    System.out.println(";");

  }


  /**
   * Calculate sister annotation statistics suitable for doing
   * selective sister splitting in the PCFGParser inside the
   * FactoredParser.
   *
   * @param args One argument: path to the Treebank
   */
  public static void main(String[] args) {

    ClassicCounter<String> c = new ClassicCounter<>();
    c.setCount("A", 0);
    c.setCount("B", 1);

    double d = Counters.klDivergence(c, c);
    System.out.println("KL Divergence: " + d);


    String encoding = "UTF-8";
    if (args.length > 1) {
      encoding = args[1];
    }
    if (args.length < 1) {
      System.out.println("Usage: ParentAnnotationStats treebankPath");
    } else {
      SisterAnnotationStats pas = new SisterAnnotationStats();
      Treebank treebank = new DiskTreebank(in -> new PennTreeReader(in, new LabeledScoredTreeFactory(new StringLabelFactory()), new BobChrisTreeNormalizer()), encoding);
      treebank.loadPath(args[0]);
      treebank.apply(pas);
      pas.printStats();
    }
  }

}
