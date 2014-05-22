package edu.stanford.nlp.parser.shiftreduce;

import java.io.Serializable;

import edu.stanford.nlp.util.ArrayUtils;

public class Weight implements Serializable {
  public Weight() {
    indices = null;
    values = null;
  }

  public Weight(Weight other) {
    if (other.size() == 0) {
      indices = null;
      values = null;
      return;
    }
    indices = ArrayUtils.copy(other.indices);
    values = ArrayUtils.copy(other.values);
    condense();
  }

  public int size() {
    if (indices == null) {
      return 0;
    }
    return indices.length;
  }

  public void score(float[] scores) {
    for (int i = 0; i < size(); ++i) {
      scores[indices[i]] += values[i];
    }
  }

  public void addScaled(Weight other, float scale) {
    for (int i = 0; i < other.size(); ++i) {
      updateWeight(other.indices[i], other.values[i] * scale);
    }
  }

  public void condense() {
    if (values == null) {
      return;
    }

    int nonzero = 0;
    for (int i = 0; i < values.length; ++i) {
      if (values[i] != 0.0f) {
        ++nonzero;
      }
    }

    if (nonzero == 0) {
      indices = null;
      values = null;
      return;
    }

    if (nonzero == indices.length) {
      return;
    }

    int[] newIndices = new int[nonzero];
    float[] newValues = new float[nonzero];
    int j = 0;
    for (int i = 0; i < values.length; ++i) {
      if (values[i] == 0.0f) {
        continue;
      }
      newIndices[j] = indices[i];
      newValues[j] = values[i];
      ++j;
    }
    indices = newIndices;
    values = newValues;
  }

  public void updateWeight(int index, float increment) {
    if (index < 0) {
      return;
    }

    if (indices == null) {
      indices = new int[1];
      indices[0] = index;
      values = new float[1];
      values[0] = increment;
      return;
    }

    for (int i = 0; i < indices.length; ++i) {
      if (indices[i] == index) {
        values[i] += increment;
        return;
      }
    }

    int[] newIndices = new int[indices.length + 1];
    float[] newValues = new float[values.length + 1];
    for (int i = 0; i < indices.length; ++i) {
      newIndices[i] = indices[i];
      newValues[i] = values[i];
    }
    newIndices[indices.length] = index;
    newValues[values.length] = increment;
    indices = newIndices;
    values = newValues;
  }

  int[] indices;
  float[] values;

  private static final long serialVersionUID = 1;
}
