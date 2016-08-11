package edu.stanford.nlp.coref.statistical;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.coref.data.Dictionaries.MentionType;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

/**
 * Class for filtering out input features and producing feature conjunctions.
 * @author Kevin Clark
 */
public class MetaFeatureExtractor {

  public enum PairConjunction {FIRST, LAST, BOTH}
  public enum SingleConjunction {INDEX, INDEX_CURRENT, INDEX_OTHER, INDEX_BOTH, INDEX_LAST}

  private final boolean neTypeConjuntion;
  private final boolean anaphoricityClassifier;
  private final Set<PairConjunction> pairConjunctions;
  private final Set<SingleConjunction> singleConjunctions;
  private final List<String> disallowedPrefixes;
  private final String str;

  public static class Builder {
    private boolean anaphoricityClassifier = false;
    private List<PairConjunction> pairConjunctions = Arrays.asList(
        new PairConjunction[] {PairConjunction.LAST,
            PairConjunction.FIRST,
            PairConjunction.BOTH});
    private List<SingleConjunction> singleConjunctions = Arrays.asList(
        new SingleConjunction[] {SingleConjunction.INDEX,
            SingleConjunction.INDEX_CURRENT,
            SingleConjunction.INDEX_BOTH});
    private List<String> disallowedPrefixes = new ArrayList<>();
    private boolean useNEType = true;

    public Builder anaphoricityClassifier(boolean anaphoricityClassifier)
      { this.anaphoricityClassifier = anaphoricityClassifier; return this; }
    public Builder pairConjunctions(PairConjunction[] pairConjunctions)
      { this.pairConjunctions = Arrays.asList(pairConjunctions); return this; }
    public Builder singleConjunctions(SingleConjunction[] singleConjunctions)
      { this.singleConjunctions = Arrays.asList(singleConjunctions); return this; }
    public Builder disallowedPrefixes(String[] disallowedPrefixes)
      { this.disallowedPrefixes = Arrays.asList(disallowedPrefixes); return this; }
    public Builder useNEType(boolean useNEType)
      { this.useNEType = useNEType; return this; }

    public MetaFeatureExtractor build() {
      return new MetaFeatureExtractor(this);
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public MetaFeatureExtractor(Builder builder) {
    anaphoricityClassifier = builder.anaphoricityClassifier;
    if (anaphoricityClassifier) {
      pairConjunctions = new HashSet<>();
    } else {
      pairConjunctions = new HashSet<>(builder.pairConjunctions);
    }
    singleConjunctions = new HashSet<>(builder.singleConjunctions);
    disallowedPrefixes = builder.disallowedPrefixes;
    neTypeConjuntion = builder.useNEType;

    str = StatisticalCorefTrainer.fieldValues(builder);
  }

  public static MetaFeatureExtractor anaphoricityMFE() {
    return MetaFeatureExtractor.newBuilder()
    .singleConjunctions(new SingleConjunction[] {SingleConjunction.INDEX,
            SingleConjunction.INDEX_LAST})
    .disallowedPrefixes(new String[] {"parent-word"})
    .anaphoricityClassifier(true)
    .build();
  }

  public static Counter<String> filterOut(Counter<String> c, List<String> disallowedPrefixes) {
    Counter<String> c2 = new ClassicCounter<>();
    for (Map.Entry<String, Double> e : c.entrySet()) {
      boolean allowed = true;
      for (String prefix : disallowedPrefixes) {
        allowed &= !e.getKey().startsWith(prefix);
      }
      if (allowed) {
        c2.incrementCount(e.getKey(), e.getValue());
      }
    }
    return c2;
  }

  public Counter<String> getFeatures(Example example,
      Map<Integer, CompressedFeatureVector> mentionFeatures, Compressor<String> compressor) {
    Counter<String> features = new ClassicCounter<>();
    Counter<String> pairFeatures = new ClassicCounter<>();
    Counter<String> features1 = new ClassicCounter<>();
    Counter<String> features2 = compressor.uncompress(mentionFeatures.get(example.mentionId2));

    if (!example.isNewLink()) {
      assert(!anaphoricityClassifier);
      pairFeatures = compressor.uncompress(example.pairwiseFeatures);
      features1 = compressor.uncompress(mentionFeatures.get(example.mentionId1));
    } else {
      features2.incrementCount("bias");
    }
    if (!disallowedPrefixes.isEmpty()) {
      features1 = filterOut(features1, disallowedPrefixes);
      features2 = filterOut(features2, disallowedPrefixes);
      pairFeatures = filterOut(pairFeatures, disallowedPrefixes);
    }

    List<String> ids1 = example.isNewLink() ? new ArrayList<>() :
      identifiers(features1, example.mentionType1);
    List<String> ids2 = identifiers(features2, example.mentionType2);
    features.addAll(pairFeatures);
    for (String id1 : ids1) {
      for (String id2 : ids2) {
        if (pairConjunctions.contains(PairConjunction.FIRST)) {
          features.addAll(getConjunction(pairFeatures, "_m1=" + id1));
        }
        if (pairConjunctions.contains(PairConjunction.LAST)) {
          features.addAll(getConjunction(pairFeatures, "_m2=" + id2));
        }
        if (pairConjunctions.contains(PairConjunction.BOTH)) {
          features.addAll(getConjunction(pairFeatures, "_ms=" + id1 + "_" + id2));
        }
        if (singleConjunctions.contains(SingleConjunction.INDEX)) {
          features.addAll(getConjunction(features1, "_1"));
          features.addAll(getConjunction(features2, "_2"));
        }
        if (singleConjunctions.contains(SingleConjunction.INDEX_CURRENT)) {
          features.addAll(getConjunction(features1, "_1" + "_m=" + id1));
          features.addAll(getConjunction(features2, "_2" + "_m=" + id2));
        }
        if (singleConjunctions.contains(SingleConjunction.INDEX_LAST)) {
          features.addAll(getConjunction(features1, "_1" + "_m2=" + id2));
          features.addAll(getConjunction(features2, "_2" + "_m2=" + id2));
        }
        if (singleConjunctions.contains(SingleConjunction.INDEX_OTHER)) {
          features.addAll(getConjunction(features1, "_1" + "_m=" + id2));
          features.addAll(getConjunction(features2, "_2" + "_m=" + id1));
        }
        if (singleConjunctions.contains(SingleConjunction.INDEX_BOTH)) {
          features.addAll(getConjunction(features1, "_1" + "_ms=" + id1 + "_" + id2));
          features.addAll(getConjunction(features2, "_2" + "_ms=" + id1 + "_" + id2));
        }
      }
    }

    if (example.isNewLink()) {
      features.addAll(features2);
      features.addAll(getConjunction(features2, "_m=" + ids2.get(0)));
      Counter<String> newFeatures = new ClassicCounter<>();
      for (Map.Entry<String, Double> e : features.entrySet()) {
        newFeatures.incrementCount(e.getKey() + "_NEW", e.getValue());
      }
      features = newFeatures;
    }

    return features;
  }

  private List<String> identifiers(Counter<String> features, MentionType mentionType) {
    List<String> identifiers = new ArrayList<>();
    if (mentionType == MentionType.PRONOMINAL) {
      for (String feature : features.keySet()) {
        if (feature.startsWith("head-word=")) {
          identifiers.add(feature.replace("head-word=", ""));
          return identifiers;
        }
      }
    } else if (neTypeConjuntion && mentionType == MentionType.PROPER) {
      for (String feature : features.keySet()) {
        if (feature.startsWith("head-ne-type=")) {
          identifiers.add(mentionType.toString() + "_" + feature.replace("head-ne-type=", ""));
          return identifiers;
        }
      }
    }

    identifiers.add(mentionType.toString());
    return identifiers;
  }

  private static Counter<String> getConjunction(Counter<String> original, String suffix) {
    Counter<String> conjuction = new ClassicCounter<>();
    for (Map.Entry<String, Double> e : original.entrySet()) {
      conjuction.incrementCount(e.getKey() + suffix, e.getValue());
    }
    return conjuction;
  }

  @Override
  public String toString() {
    return str;
  }
}
