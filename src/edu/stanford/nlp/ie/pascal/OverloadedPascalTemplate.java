package edu.stanford.nlp.ie.pascal;

import edu.stanford.nlp.stats.Counter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;


/**
 * Holds multiple filler values for each field in a document, and provides
 * methods for generating all possible {@link PascalTemplate} that comprise it.
 *
 * @author Chris Cox
 */

public class OverloadedPascalTemplate {

  private String[][] values = null;

  private int[] numValues = null; //number of values for each field.

  public static final int MAX_VALUES = 40;

  public static final double SCORE_CUTOFF = .00005;

  public OverloadedPascalTemplate() {
    values = new String[PascalTemplate.fields.length][MAX_VALUES];
    numValues = new int[PascalTemplate.fields.length];
    for (int i = 0; i < numValues.length; i++) {
      numValues[i] = 0;
    }
  }

  public int getNumValues(int index) {
    return numValues[index];
  }

  /**
   * Creates a new "values" entry for the passed tag, and fills
   * it with the passed word.
   */
  public String[] getValues(int index) {
    return values[index];
  }

  public void createNewValue(String tag, String word) {
    //System.out.println("new value created: " + tag + " : "+word);
    int tagIndex = PascalTemplate.getFieldIndex(tag);
    if (tagIndex == -1) {
      System.err.println("Illegal tag: " + tag);
    } else {
      values[tagIndex][numValues[tagIndex]] = word;
      numValues[tagIndex]++;
    }
  }

  /**
   * Concatenates the passed word to the current value field for the tag.
   */

  public void addToCurrentValue(String tag, String word) {

    int tagIndex = PascalTemplate.getFieldIndex(tag);
    if (word.equals("")) {
      return;
    }
    if (tagIndex == -1) {
      System.err.println("Illegal tag: " + tag);
    } else if (numValues[tagIndex] < 1) {
      createNewValue(tag, word);
    } else {
      String str = values[tagIndex][numValues[tagIndex] - 1];
      str = str.concat(" " + word);
      values[tagIndex][numValues[tagIndex] - 1] = str;
    }

  }

  private void removeDuplicateValues() {
    for (int i = 0; i < PascalTemplate.fields.length; i++) {
      HashSet hm = new HashSet();
      for (int j = 0; j < numValues[i]; j++) {
        if (hm.contains(values[i][j].trim())) {
          for (int k = j; k < numValues[i] - 1; k++) {
            values[i][k] = values[i][k + 1];
          }
          numValues[i]--;
          j--;
        } else {
          hm.add(values[i][j].trim());
        }
      }
    }

  }

  /**
   * Generates all possible single templates and adds
   * them to the passed {@link HashMap}.
   * In the Map, {@link PascalTemplate} keys map to double "score" values.
   * If a generated {@link PascalTemplate} already exists in the Map,
   * its score is increased.
   * Each child PascalTemplate of this parent receives a score of
   * 1.0 / numPossibleChildren.
   */


  public Counter<PascalTemplate> unpackToSingleTemplates(Counter<PascalTemplate> templateCounter, Counter[] fieldValueCounter) {
    removeDuplicateValues();
    double scoreForEach = 1.0 / getNumPossibilities();
    //if(scoreForEach < SCORE_CUTOFF) return templateCounter;
    //	System.out.println("Unpacking to singles.  :" + scoreForEach);
    //If the overloaded template has no value for a particular field,
    //we give it one: "NULL"
    for (int i = 0; i < PascalTemplate.fields.length - 1; i++) {
      if (numValues[i] == 0) {
        numValues[i] = 1;
        values[i][0] = null;
      }
    }

    /* First call to recursive allPossibilities which generates possible
     * PascalTemplates from the overloaded parent.
     */
    ArrayList<PascalTemplate> templateCollector = allPossibilities(0);

    for (PascalTemplate pt : templateCollector) {
      double d;
      if (templateCounter.containsKey(pt)) {
        d = templateCounter.getCount(pt);
      } else {
        d = 0.0;
      }
      templateCounter.setCount(pt, d + scoreForEach);
      pt.writeToFieldValueCounter(fieldValueCounter, scoreForEach);

    }
    return templateCounter;
  }

  //Returns an ArrayList of PascalTemplates.
  private ArrayList<PascalTemplate> allPossibilities(int startidx) {

    ArrayList<PascalTemplate> templateCollector = new ArrayList<PascalTemplate>();

    if (startidx == numValues.length - 1) {
      templateCollector.add(new PascalTemplate());
      return templateCollector;
    }

    ArrayList<PascalTemplate> nextPossibilities = allPossibilities(startidx + 1);

    for (int i = 0; i < numValues[startidx]; i++) {
      String val = values[startidx][i];
      for (PascalTemplate nextTemplate : nextPossibilities) {
        PascalTemplate newTemplate = new PascalTemplate(nextTemplate);
        newTemplate.setValue(startidx, val);
        templateCollector.add(newTemplate);
      }
    }
    // nextPossibilities = null;
    return templateCollector;
  }

  /*
   * Returns the number of single templates generatable from this object.
   * Templates don't include the last member,
   * which is reserved for the "non-filler" slot.
   */
  private int getNumPossibilities() {
    int tally = 1;

    for (int i = 0; i < numValues.length - 1; i++) {
      int multiplier = numValues[i];
      if (multiplier == 0) {
        multiplier = 1;
      }
      tally *= multiplier;
    }
    //System.err.println("Found" + tally + "possibilities");
    return tally;
  }

  @Override
  public String toString() {
    removeDuplicateValues();
    String str = "";
    for (int i = 0; i < PascalTemplate.fields.length; i++) {
      for (int j = 0; j < numValues[i]; j++) {
        str = str.concat(PascalTemplate.fields[i] + ": " + values[i][j] + "\n");
      }
    }
    return str;
  }

}
