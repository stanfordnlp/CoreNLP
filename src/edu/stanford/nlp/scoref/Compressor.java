package edu.stanford.nlp.scoref;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

public class Compressor<K> implements Serializable {
  private static final long serialVersionUID = 364548642855692442L;
  private final Map<K, Integer> index;
  private final Map<Integer, K> inverse;

  public Compressor() {
    index = new HashMap<>();
    inverse = new HashMap<>();
  }

  public CompressedFeatureVector compress(Counter<K> c) {
    List<Integer> keys = new ArrayList<>(c.size());
    List<Double> values = new ArrayList<>(c.size());

    for (Map.Entry<K, Double> e : c.entrySet()) {
      K key = e.getKey();
      Integer id = index.get(key);
      if (id == null) {
        id = index.size();
        inverse.put(id, key);
        index.put(key, id);
      }

      keys.add(id);
      values.add(e.getValue());
    }

    return new CompressedFeatureVector(keys, values);
  }

  public Counter<K> uncompress(CompressedFeatureVector cvf) {
    Counter<K> c = new ClassicCounter<>();
    for (int i = 0; i < cvf.keys.size(); i++) {
      c.incrementCount(inverse.get(cvf.keys.get(i)), cvf.values.get(i));
    }
    return c;
  }
}
