package edu.stanford.nlp.maxent;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.ErasureUtils;

import java.util.ArrayList;
import java.util.Set;
import java.util.Arrays;

/**
 * A Type2Datum consists of the following elements:
 * <ul>
 * <p/>
 * <li>A map of hidden classes to sets of real-valued features.  This map is represented as a TwoDimensionalCounter.
 * <p/>
 * <li>A set of possible hidden classes, which must be a superset of the class-to-feature map's class keyset.
 * <p/>
 * <li>A true class, which must be a member of the set of possible hidden classes.
 * <p/>
 * </ul>
 * <p/>
 * If you want to do a type 2 classification problem, you should construct type2datum objects one by one, put them inside a {@link Type2Dataset},
 * then at the end of all this call {@link Type2Dataset#toProblem()} and Kristina will tell you what to do with the result!!!
 *
 * @author Roger Levy
 */
public class Type2Datum<L,F> {

  /**
   * Creates a Type2Datum.
   *
   * @param classFeatureCounts the class-to-feature map
   * @param classes            the set of possible classes
   * @param trueClass          the true class of the example.
   */
  public Type2Datum(TwoDimensionalCounter<L,F> classFeatureCounts, Set<L> classes, L trueClass) {
    this(classes, trueClass);
    this.classFeatureCounts = classFeatureCounts;
  }

  /**
   * Creates a Type2Datum, using the class keyset of the class-to-feature map as the set of possible classes.
   *
   * @param classFeatureCounts the class-to-feature map; must be a {@link TwoDimensionalCounter} of depth 2!
   * @param trueClass          the true class of the example.
   */
  public Type2Datum(TwoDimensionalCounter<L,F> classFeatureCounts, L trueClass) {
    this(classFeatureCounts, classFeatureCounts.firstKeySet(), trueClass);
  }


  @Override
  public String toString() {
    return "class " + trueClass + "\n" + toSortedString(classFeatureCounts) + "\n" + toShortString();
  }

  public String toSortedString(TwoDimensionalCounter<L,F> c) {

    ArrayList<L> topLevel = new ArrayList<L>(c.firstKeySet());
    topLevel = new ArrayList<L>(ErasureUtils.sortedIfPossible(topLevel));
    StringBuffer b = new StringBuffer();
    for (int i = 0; i < topLevel.size(); i++) {
      L key = topLevel.get(i);
      b.append(key + "\t");
      ClassicCounter<F> result = c.getCounter(key);
      ArrayList<F> innerKeys = new ArrayList<F>(result.keySet());
      String[] resultArray = new String[innerKeys.size()];
      //Arrays.sort(innerKeys);
      for (int j = 0; j < innerKeys.size(); j++) {
        resultArray[j] = innerKeys.get(j) + ":" + result.getCount(innerKeys.get(j));
      }
      Arrays.sort(resultArray);
      for (int i1 = 0; i1 < resultArray.length; i1++) {
        b.append(resultArray[i1] + "\t");
      }
      b.append("\n");
    }

    return b.toString();
  }

  public String toShortString() {
    return "class " + trueClass + " feature size" + classFeatureCounts.totalCount();
  }

  private Type2Datum(Set<L> classes, L trueClass) {
    this.classes = classes;
    setTrueClass(trueClass);
  }

  TwoDimensionalCounter<L,F> classFeatureCounts;
  private Set<L> classes;

  /**
   * Queries the true hidden class of the datum.
   *
   * @return the true hidden class of the datum.
   */
  public L trueClass() {
    return trueClass;
  }

  /**
   * Specifies the true class of the example.
   *
   * @param trueClass The true class of the example.
   */
  public void setTrueClass(L trueClass) {
    this.trueClass = trueClass;
  }

  private L trueClass;

  /**
   * Gets the set of possible hidden classes for the datum.
   *
   * @return the set of possible hidden classes.
   */
  public Set<L> classes() {
    return classes;
  }

  /**
   * Queries the value of a feature in a possible hidden class for the datum.
   *
   * @param theClass the possible hidden class to query
   * @param feature  the feature of interest within that class
   * @return the value of the feature for the class
   */
  public double featureValue(L theClass, F feature) {
    return classFeatureCounts.getCount(theClass, feature);
  }

  /**
   * Adds a class-specific feature.
   *
   * @param theClass The class in which the feature occurs
   * @param feature  The specific feature
   * @param value    The value of the feature for this class
   */
  public void addFeature(L theClass, F feature, double value) {
    addClass(theClass);
    classFeatureCounts.incrementCount(theClass, feature, value);
  }

  /**
   * Adds a possible hidden class for the item.
   *
   * @param theClass The possible class
   */
  public void addClass(L theClass) {
    classes.add(theClass);
  }


}
