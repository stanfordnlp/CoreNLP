package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.*;

import java.util.*;
import java.util.function.Function;

/**
* Trigger for CoreMap Node Patterns.  Allows for fast identification of which patterns
*  may match for one node.
*
* @author Angel Chang
*/
public class CoreMapNodePatternTrigger implements MultiPatternMatcher.NodePatternTrigger<CoreMap> {
  final Collection<SequencePattern<CoreMap>> alwaysTriggered;
  TwoDimensionalCollectionValuedMap<Class, Object, SequencePattern<CoreMap>> annotationTriggers =
          new TwoDimensionalCollectionValuedMap<>(true);
  TwoDimensionalCollectionValuedMap<Class, String, SequencePattern<CoreMap>> lowercaseStringTriggers =
          new TwoDimensionalCollectionValuedMap<>(true);

  public CoreMapNodePatternTrigger(SequencePattern<CoreMap>... patterns) {
    this(Arrays.asList(patterns));
  }
  public CoreMapNodePatternTrigger(Collection<? extends SequencePattern<CoreMap>> patterns) {
    Function<NodePattern<CoreMap>, StringTriggerCandidate> stringTriggerFilter =
        in -> {
          if (in instanceof CoreMapNodePattern) {
            CoreMapNodePattern p = (CoreMapNodePattern) in;
            for (Pair<Class,NodePattern> v:p.getAnnotationPatterns()) {
              if (v.second instanceof CoreMapNodePattern.StringAnnotationPattern) {
                return new StringTriggerCandidate(v.first, ((CoreMapNodePattern.StringAnnotationPattern) v.second).target,
                        ((CoreMapNodePattern.StringAnnotationPattern) v.second).ignoreCase());
              }
            }
          }
          return null;
        };

    LinkedHashSet<SequencePattern<CoreMap>> alwaysTriggeredTemp = new LinkedHashSet<>();
    for (SequencePattern<CoreMap> pattern:patterns) {
      // Look for first string...
      Collection<StringTriggerCandidate> triggerCandidates = pattern.findNodePatterns(stringTriggerFilter, false, true);
      // TODO: Select most unlikely to trigger trigger from the triggerCandidates
      //  (if we had some statistics on most frequent annotation values...., then pick least frequent)
      // For now, just pick the longest: going from (text or lemma) to rest
      StringTriggerCandidate trigger = triggerCandidates.stream().max(STRING_TRIGGER_CANDIDATE_COMPARATOR).orElse(null);
      if (!triggerCandidates.isEmpty()) {
        if (trigger.ignoreCase) {
          lowercaseStringTriggers.add(trigger.key, trigger.value.toLowerCase(), pattern);
        } else {
          annotationTriggers.add(trigger.key, trigger.value, pattern);
        }
      } else {
        alwaysTriggeredTemp.add(pattern);
      }
    }
    if (alwaysTriggeredTemp.size() == 0) {
      alwaysTriggered = Collections.emptySet();
    } else if (alwaysTriggeredTemp.size() == 1) {
      alwaysTriggered = Collections.singleton(alwaysTriggeredTemp.iterator().next());
    } else {
      // the set filtering has already been done, so now we can keep
      // them in a more efficient list
      alwaysTriggered = Collections.unmodifiableList(new ArrayList<>(alwaysTriggeredTemp));
    }
  }

  private static class StringTriggerCandidate {
    Class key;
    String value;
    boolean ignoreCase;
    int keyLevel;
    int effectiveValueLength;

    public StringTriggerCandidate(Class key, String value, boolean ignoreCase) {
      this.key = key;
      this.value = value;
      this.ignoreCase = ignoreCase;
      // Favor text and lemma (more likely to be unique)
      this.keyLevel = (CoreAnnotations.TextAnnotation.class.equals(key) || CoreAnnotations.LemmaAnnotation.class.equals(key))? 1:0;
      // Special case for -LRB- ( and -RRB- )
      this.effectiveValueLength = ("-LRB-".equals(value) || "-RRB-".equals(value))? 1: value.length();
    }
  }
  private static final Comparator<StringTriggerCandidate> STRING_TRIGGER_CANDIDATE_COMPARATOR =
    new Comparator<StringTriggerCandidate>() {
      @Override
      public int compare(StringTriggerCandidate o1, StringTriggerCandidate o2) {
        if (o1.keyLevel != o2.keyLevel) {
          return (o1.keyLevel < o2.keyLevel)? -1:1;
        } else {
          int v1 = o1.effectiveValueLength;
          int v2 = o2.effectiveValueLength;
          if (v1 != v2) return (v1 < v2)? -1:1;
          else return 0;
        }
      }
    };

  @Override
  public Collection<SequencePattern<CoreMap>> apply(CoreMap in) {
    Set<SequencePattern<CoreMap>> triggeredPatterns = new LinkedHashSet<>();
    triggeredPatterns.addAll(alwaysTriggered);
    for (Class key:annotationTriggers.firstKeySet()) {
      Object value = in.get(key);
      if (value != null) {
        Collection<SequencePattern<CoreMap>> triggered = annotationTriggers.get(key, value);
        if (triggered != null) {
          triggeredPatterns.addAll(triggered);
        }
      }
    }
    for (Class key:lowercaseStringTriggers.firstKeySet()) {
      Object value = in.get(key);
      if (value != null && value instanceof String) {
        Collection<SequencePattern<CoreMap>> triggered = lowercaseStringTriggers.get(key, ((String) value).toLowerCase());
        if (triggered != null) {
          triggeredPatterns.addAll(triggered);
        }
      }
    }
    // TODO: triggers for normalized patterns...
    return triggeredPatterns;
  }
}
