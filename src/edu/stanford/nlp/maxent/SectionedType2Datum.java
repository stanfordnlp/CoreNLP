package edu.stanford.nlp.maxent;

import edu.stanford.nlp.util.*;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.TwoDimensionalCounter;

import java.util.*;

/**
 * @author Kristina Toutanova
 *         Nov 17, 2004
 *         For Type2Datums that have the features organized in blocks for effieincy
 *         Each datum has a number of alternatives and there is a correct alternative
 */
public class SectionedType2Datum<L, F> {
  int numOptions;
  int correctOption;
  List<FeatureBlock<L, F>> blocks;

  public SectionedType2Datum(int nOptions, int cOption, List<FeatureBlock<L, F>> blocks) {
    numOptions = nOptions;
    correctOption = cOption;
    this.blocks = blocks;
  }


  /**
   * subtract the minimum value of a feature from the values in all classes
   * if any value becomes 0, remove the feature from that class
   *
   */
  public static <L, F> Type2Datum<L, F> removeRepetitions(Type2Datum<L, F> d) {
    System.err.println("starting size of d " + d.toShortString());
    TwoDimensionalCounter<L, F> classFeatures = d.classFeatureCounts;
    HashSet<F> features = new HashSet<F>();
    for (L thisClass: classFeatures.firstKeySet()) {
      Counter<F> thisClassFeatures = classFeatures.getCounter(thisClass);
      for (F thisFeature: thisClassFeatures.keySet()) {
        features.add(thisFeature);
      }
    }


    Collection<L> classes = d.classes();
    for (F feature: features) {
      double min = Double.POSITIVE_INFINITY;
      double max = Double.NEGATIVE_INFINITY;
      for (L cls: classes) {
        double value = d.featureValue(cls, feature);
        if (value < min) {
          min = value;
        }
        if (value > max) {
          max = value;
        }
      }
      if (min > 0) {
        System.err.println("found savings for feature " + feature);
        for (L cls: classes) {
          d.classFeatureCounts.incrementCount(cls, feature, -min);
        }
      }
    }
    System.err.println("resulting size of d " + d.toShortString());
    return d;
  }

  /**
   * devise an efficient representation of the Type2Datum as a list of FeatureBlocks
   *
   */
  public SectionedType2Datum(Type2Datum<L, Pair<L, F>> datum) {
    numOptions = datum.classes().size();
    Index<L> classIndex = new HashIndex<L>();
    classIndex.add(datum.trueClass());
    correctOption = classIndex.indexOf(datum.trueClass());
    CollectionValuedMap<Pair<F, Double>, Pair<L, Integer>> cVM = new CollectionValuedMap<Pair<F, Double>, Pair<L, Integer>>(); //it is hashmap to hashset
    TwoDimensionalCounter<L, Pair<L, F>> classFeatures = datum.classFeatureCounts;
    for (L thisClass: classFeatures.firstKeySet()) {
      classIndex.add(thisClass);
      Counter<Pair<L, F>> thisClassFeatures = classFeatures.getCounter(thisClass);
      int thisIntClass = classIndex.indexOf(thisClass);
      for (Pair<L, F> thisFeature: thisClassFeatures.keySet()) {
        double thisFeatureValue = thisClassFeatures.getCount(thisFeature);
        if (thisFeatureValue == 0) {
          continue;
        }
        Pair<F, Double> fValue = new Pair<F, Double>(thisFeature.second(), new Double(thisFeatureValue));
        Pair<L, Integer> fLocation = new Pair<L, Integer>(thisFeature.first(), Integer.valueOf(thisIntClass));
        cVM.add(fValue, fLocation);
      }
    }
    //now group the features in groups
    HashMap<Collection<Pair<L, Integer>>, ClassicCounter<F>> valuesMap = new HashMap<Collection<Pair<L, Integer>>, ClassicCounter<F>>(); // the keys will be sets of <class,alternative> and the values will be counters
    for (Pair<F, Double> fVal: cVM.keySet()) {
      F feature = fVal.first();
      double value = fVal.second();
      Collection<Pair<L, Integer>> locations = cVM.get(fVal);
      ClassicCounter<F> c = valuesMap.get(locations);
      if (c != null) {
        c.incrementCount(feature, value);
      } else {
        c = new ClassicCounter<F>();
        c.incrementCount(feature, value);
        valuesMap.put(locations, c);
      }

    }
    System.err.println("number of blocks " + valuesMap.size());
    blocks = new ArrayList<FeatureBlock<L, F>>();
    for (Map.Entry<Collection<Pair<L, Integer>>, ClassicCounter<F>> e: valuesMap.entrySet()) {
      Collection<Pair<L, Integer>> locations = e.getKey();
      ClassicCounter<F> features = e.getValue();
      blocks.add(new FeatureBlock<L, F>(features, locations));
    }

  }

  public int numBlocks() {
    return blocks.size();
  }

  public FeatureBlock<L, F> getBlock(int index) {
    return blocks.get(index);
  }
}
