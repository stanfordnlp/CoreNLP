package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.util.Pair;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Pattern for matching a Map from keys K to objects
 *
 * @author Angel Chang
 */
public class MapNodePattern<M extends Map<K,Object>, K> extends ComplexNodePattern<M,K> {

  private static <M extends Map<K, Object>, K> BiFunction<M,K,Object> createGetter() {
    return new BiFunction<M, K, Object>() {
      @Override
      public Object apply(M m, K k) {
        return m.get(k);
      }
    };
  }

  public MapNodePattern(List<Pair<K, NodePattern>> annotationPatterns) {
    super(createGetter(), annotationPatterns);
  }

  public MapNodePattern(Pair<K, NodePattern>... annotationPatterns) {
    super(createGetter(), annotationPatterns);
  }

  public MapNodePattern(K key, NodePattern pattern) {
    super(createGetter(), key, pattern);
  }

}
