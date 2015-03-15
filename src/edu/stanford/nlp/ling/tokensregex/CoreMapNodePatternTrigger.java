package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
* Trigger for CoreMap Node Patterns.  Allows for fast identification of which patterns
*  may match for one node.
*
* @author Angel Chang
*/
public class CoreMapNodePatternTrigger implements MultiPatternMatcher.NodePatternTrigger<CoreMap> {
  Collection<? extends SequencePattern<CoreMap>> patterns;
  Collection<SequencePattern<CoreMap>> alwaysTriggered = new LinkedHashSet<SequencePattern<CoreMap>>();
  TwoDimensionalCollectionValuedMap<Class, Object, SequencePattern<CoreMap>> annotationTriggers =
          new TwoDimensionalCollectionValuedMap<Class, Object, SequencePattern<CoreMap>>();
  TwoDimensionalCollectionValuedMap<Class, String, SequencePattern<CoreMap>> lowercaseStringTriggers =
          new TwoDimensionalCollectionValuedMap<Class, String, SequencePattern<CoreMap>>();

  public CoreMapNodePatternTrigger(SequencePattern<CoreMap>... patterns) {
    this(Arrays.asList(patterns));
  }
  public CoreMapNodePatternTrigger(Collection<? extends SequencePattern<CoreMap>> patterns) {
    this.patterns = patterns;

    Function<NodePattern<CoreMap>, Triple<Class,String,Boolean>> textTriggerFilter =
            new Function<NodePattern<CoreMap>, Triple<Class,String,Boolean>>() {
      @Override
      public Triple<Class,String,Boolean> apply(NodePattern<CoreMap> in) {
        if (in instanceof CoreMapNodePattern) {
          CoreMapNodePattern p = (CoreMapNodePattern) in;
          for (Pair<Class,NodePattern> v:p.getAnnotationPatterns()) {
            if (v.first == CoreAnnotations.TextAnnotation.class && v.second instanceof CoreMapNodePattern.StringAnnotationPattern) {
              return Triple.makeTriple(v.first, ((CoreMapNodePattern.StringAnnotationPattern) v.second).target,
                      ((CoreMapNodePattern.StringAnnotationPattern) v.second).ignoreCase());
            }
          }
        }
        return null;
      }
    };

    for (SequencePattern<CoreMap> pattern:patterns) {
      // Look for first string...
      Triple<Class,String,Boolean> firstTextTrigger = pattern.findNodePattern(textTriggerFilter);
      if (firstTextTrigger != null) {
        if (firstTextTrigger.third) {
          lowercaseStringTriggers.add(firstTextTrigger.first, firstTextTrigger.second.toLowerCase(), pattern);
        } else {
          annotationTriggers.add(firstTextTrigger.first, firstTextTrigger.second, pattern);
        }
      } else {
        alwaysTriggered.add(pattern);
      }
    }
  }

  @Override
  public Collection<SequencePattern<CoreMap>> apply(CoreMap in) {
    Set<SequencePattern<CoreMap>> triggeredPatterns = new LinkedHashSet<SequencePattern<CoreMap>>();
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
