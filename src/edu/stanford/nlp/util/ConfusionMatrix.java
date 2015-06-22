package edu.stanford.nlp.util;

import java.io.StringWriter;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This implements a confusion table over arbitrary types of class labels. Main
 * routines of interest: 
 * 	    add(guess, gold), increments the guess/gold entry in this cell by 1 
 *      get(guess, gold), returns the number of entries in this cell
 *      toString(), returns printed form of the table, with marginals and
 *                     contingencies for each class label
 * 
 * Example usage: 
 * Confusion<String> myConf = new Confusion<String>();
 * myConf.add("l1", "l1"); 
 * myConf.add("l1", "l2"); 
 * myConf.add("l2", "l2");
 * System.out.println(myConf.toString());
 * 
 * NOTES: - This sorts by the toString() of the guess and gold labels. Thus the
 * label.toString() values should be distinct!
 * 
 * @author yeh1@cs.stanford.edu
 * 
 * @param <U> the class label type 
 */
public class ConfusionMatrix<U> {
  // classification placeholder prefix when drawing in table
  private static final String CLASS_PREFIX = "C"; 
  
  private static final String FORMAT = "#.#####";
  protected DecimalFormat format;
  private int leftPadSize = 16;
  private int delimPadSize = 8;
  private boolean useRealLabels = false;

  public ConfusionMatrix() {
    format = new DecimalFormat(FORMAT);
  }

  public ConfusionMatrix(Locale locale) {
    format = new DecimalFormat(FORMAT, new DecimalFormatSymbols(locale));
  }

  @Override
  public String toString() {
    return printTable();
  }
  
  /**
   * This sets the lefthand side pad width for displaying the text table.
   * @param newPadSize
   */
  public void setLeftPadSize(int newPadSize) {
    this.leftPadSize = newPadSize;
  }
	
  /**
   * Sets the width used to separate cells in the table.
   */
  public void setDelimPadSize(int newPadSize) {
    this.delimPadSize = newPadSize;
  }

  public void setUseRealLabels(boolean useRealLabels) {
    this.useRealLabels = useRealLabels;
  }

  /**
   * Contingency table, listing precision ,recall, specificity, and f1 given
   * the number of true and false positives, true and false negatives.
   * 
   * @author yeh1@cs.stanford.edu
   * 
   */
  public class Contingency {
    private double tp = 0;
    private double fp = 0;
    private double tn = 0;
    private double fn = 0;
    
    private double prec = 0.0;
    private double recall = 0.0;
    private double spec = 0.0;
    private double f1 = 0.0;
    
    public Contingency(int tp_, int fp_, int tn_, int fn_) {
      tp = tp_;
      fp = fp_;
      tn = tn_;
      fn = fn_;
      
      prec = tp / (tp + fp);
      recall = tp / (tp + fn);
      spec = tn / (fp + tn);
      f1 = (2 * prec * recall) / (prec + recall);
    }
    
    public String toString() {
      return StringUtils.join(Arrays.asList("prec=" + (((tp + fp) > 0) ? format.format(prec) : "n/a"),
                                            "recall=" + (((tp + fn) > 0) ? format.format(recall) : "n/a"),
                                            "spec=" + (((fp + tn) > 0) ? format.format(spec) : "n/a"), "f1="
                                            + (((prec + recall) > 0) ? format.format(f1) : "n/a")),
                              ", ");
    }
    
  }
  
  private ConcurrentHashMap<Pair<U, U>, Integer> confTable = new ConcurrentHashMap<Pair<U, U>, Integer>();
  
  /**
   * Increments the entry for this guess and gold by 1.
   */
  public void add(U guess, U gold) {
    add(guess, gold, 1);
  }
  
  /**
   * Increments the entry for this guess and gold by the given increment amount.
   */
  public synchronized void add(U guess, U gold, int increment) {
      Pair<U, U> pair = new Pair<U, U>(guess, gold);
      if (confTable.containsKey(pair)) {
        confTable.put(pair, confTable.get(pair) + increment);
      } else {
        confTable.put(pair, increment);
      }
    }
  
  /**
   * Retrieves the number of entries with this guess and gold.
   */
  public Integer get(U guess, U gold) {
    Pair<U, U> pair = new Pair<U, U>(guess, gold);
    if (confTable.containsKey(pair)) {
      return confTable.get(pair);
    } else {
      return 0;
    }
  }
  
  /**
   * Returns the set of distinct class labels
   * entered into this confusion table.
   */
  public Set<U> uniqueLabels() {
    HashSet<U> ret = new HashSet<U>();
    for (Pair<U, U> pair : confTable.keySet()) {
      ret.add(pair.first());
      ret.add(pair.second());
    }
    return ret;
  }
  
  /**
   * Returns the contingency table for the given class label, where all other
   * class labels are treated as negative.
   */
  public Contingency getContingency(U positiveLabel) {
    int tp = 0;
    int fp = 0;
    int tn = 0;
    int fn = 0;
    for (Pair<U, U> pair : confTable.keySet()) {
      int count = confTable.get(pair);
      U guess = pair.first();
      U gold = pair.second();
      boolean guessP = guess.equals(positiveLabel);
      boolean goldP = gold.equals(positiveLabel);
      if (guessP && goldP) {
        tp += count;
      } else if (!guessP && goldP) {
        fn += count;
      } else if (guessP && !goldP) {
        fp += count;
      } else {
        tn += count;
      }
    }
    return new Contingency(tp, fp, tn, fn);
  }
  
  /**
   * Returns the current set of unique labels, sorted by their string order.
   */
  private List<U> sortKeys() {
    Set<U> labels = uniqueLabels();
    if (labels.size() == 0) {
      return Collections.emptyList();
    }

    boolean comparable = true;
    for (U label : labels) {
      if (!(label instanceof Comparable)) {
        comparable = false;
        break;
      }
    }
    if (comparable) {
      List<Comparable<Object>> sorted = Generics.newArrayList();
      for (U label : labels) {
        sorted.add(ErasureUtils.<Comparable<Object>>uncheckedCast(label));
      }
      Collections.sort(sorted);
      List<U> ret = Generics.newArrayList();
      for (Object o : sorted) {
        ret.add(ErasureUtils.<U>uncheckedCast(o));
      }
      return ret;
    } else {
      ArrayList<String> names = new ArrayList<String>();
      HashMap<String, U> lookup = new HashMap<String, U>();
      for (U label : labels) {
        names.add(label.toString());
        lookup.put(label.toString(), label);
      }
      Collections.sort(names);
    
      ArrayList<U> ret = new ArrayList<U>();
      for (String name : names) {
        ret.add(lookup.get(name));
      }
      return ret;
    }
  }
  
  /**
   * Marginal over the given gold, or column sum
   */
  private Integer goldMarginal(U gold) {
    Integer sum = 0;
    Set<U> labels = uniqueLabels();
    for (U guess : labels) {
      sum += get(guess, gold);
    }
    return sum;
  }
  
  /**
   * Marginal over given guess, or row sum
   */
  private Integer guessMarginal(U guess) {
    Integer sum = 0;
    Set<U> labels = uniqueLabels();
    for (U gold : labels) {
      sum += get(guess, gold);
    }
    return sum;
  }
  
  public String getPlaceHolder(int index, U label) {
    if (useRealLabels) {
      return label.toString();
    } else {
      return CLASS_PREFIX + (index + 1); // class name
    }
  }

  /**
   * Prints the current confusion in table form to a string, with contingency
   */
  public String printTable() {
    List<U> sortedLabels = sortKeys();
    if (confTable.size() == 0) {
      return "Empty table!";
    }
    StringWriter ret = new StringWriter();
    
    // header row (top)
    ret.write(StringUtils.padLeft("Guess/Gold", leftPadSize));
    for (int i = 0; i < sortedLabels.size(); i++) {
      String placeHolder = getPlaceHolder(i, sortedLabels.get(i));
      // placeholder
      ret.write(StringUtils.padLeft(placeHolder, delimPadSize));
    }
    ret.write("    Marg. (Guess)");
    ret.write("\n");
    
    // Write out contents
    for (int guessI = 0; guessI < sortedLabels.size(); guessI++) {
      String placeHolder = getPlaceHolder(guessI, sortedLabels.get(guessI));
      ret.write(StringUtils.padLeft(placeHolder, leftPadSize));
      U guess = sortedLabels.get(guessI);
      for (int goldI = 0; goldI < sortedLabels.size(); goldI++) {
        U gold = sortedLabels.get(goldI);
        Integer value = get(guess, gold);
        ret.write(StringUtils.padLeft(value.toString(), delimPadSize));
      }
      ret.write(StringUtils.padLeft(guessMarginal(guess).toString(), delimPadSize));
      ret.write("\n");
    }
    
    // Bottom row, write out marginals over golds
    ret.write(StringUtils.padLeft("Marg. (Gold)", leftPadSize));
    for (int goldI = 0; goldI < sortedLabels.size(); goldI++) {
      U gold = sortedLabels.get(goldI);
      ret.write(StringUtils.padLeft(goldMarginal(gold).toString(), delimPadSize));
    }
    
    // Print out key, along with contingencies
    ret.write("\n\n");
    for (int labelI = 0; labelI < sortedLabels.size(); labelI++) {
      U classLabel = sortedLabels.get(labelI);
      String placeHolder = getPlaceHolder(labelI, classLabel);
      ret.write(StringUtils.padLeft(placeHolder, leftPadSize));
      if (!useRealLabels) {
        ret.write(" = ");
        ret.write(classLabel.toString());
      }
      ret.write(StringUtils.padLeft("", delimPadSize));
      Contingency contingency = getContingency(classLabel);
      ret.write(contingency.toString());
      ret.write("\n");
    }
    
    return ret.toString();
  }
}
