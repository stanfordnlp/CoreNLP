package edu.stanford.nlp.parser.shiftreduce;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WeightMap implements Serializable {
  private HashMap<String, Weight> weights = new HashMap<>();

  private void writeObject(ObjectOutputStream out)
    throws IOException
  {
    out.writeObject(weights.size());
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    for (String feature : weights.keySet()) {
      out.writeObject(feature);
      weights.get(feature).writeBytes(bout);
    }
    out.writeObject(bout.toByteArray());
  }

  private void readObject(ObjectInputStream in)
    throws IOException, ClassNotFoundException
  {
    Integer size = (Integer) in.readObject();

    List<String> keys = new ArrayList<>();
    for (int i = 0; i < size; ++i) {
      String feature = (String) in.readObject();
      keys.add(feature);
    }
    byte[] bytes = (byte[]) in.readObject();
    ByteArrayInputStream bin = new ByteArrayInputStream(bytes);

    weights = new HashMap<>(size);
    for (int i = 0; i < size; ++i) {
      weights.put(keys.get(i), Weight.readBytes(bin));
    }
  }

  public Weight get(String key) {
    return weights.get(key);
  }

  public void put(String key, Weight weight) {
    weights.put(key, weight);
  }

  public int size() {
    return weights.size();
  }

  public boolean containsKey(String key) {
    return weights.containsKey(key);
  }

  public Set<String> keySet() {
    return weights.keySet();
  }

  public Set<Map.Entry<String, Weight>> entrySet() {
    return weights.entrySet();
  }
}
