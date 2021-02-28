package edu.stanford.nlp.parser.shiftreduce;

import java.io.Serializable;

import edu.stanford.nlp.util.ArrayUtils;

/**
 * Stores one row of the sparse matrix which makes up the multiclass perceptron.
 *
 * Uses a lot of bit fiddling to get the desired results.  What we
 * want is a row of scores representing transitions where each score
 * is the score for that transition (for the feature using this Weight
 * object).  Since the average model seems to have about 3 non-zero
 * scores per feature, we condense that by keeping pairs of index and
 * score.  However, we can then further condense that by bit packing
 * the index and score into one long.  This cuts down on object
 * creation and makes it faster to read/write the models.
 *
 * Thankfully, all of the unpleasant bit fiddling can be hidden away
 * in this one class.
 *
 * @author John Bauer
 */

public class Weight implements Serializable {
  public Weight() {
    packed = null;
  }

  public Weight(Weight other) {
    if (other.size() == 0) {
      packed = null;
      return;
    }
    packed = ArrayUtils.copy(other.packed);
    condense();
  }

  public int size() {
    if (packed == null) {
      return 0;
    }
    return packed.length;
  }

  private int unpackIndex(int i) {
    long pack = packed[i];
    return (int) (pack >>> 32);
  }

  private float unpackScore(int i) {
    long pack = packed[i];
    return Float.intBitsToFloat((int) (pack & 0xFFFFFFFF));
  }

  private static long packedValue(int index, float score) {
    long pack = ((long) (Float.floatToIntBits(score))) & 0x00000000FFFFFFFFL;
    pack = pack | (((long) index) << 32);
    return pack;
  }

  private static void pack(long[] packed, int i, int index, float score) {
    packed[i] = packedValue(index, score);
  }

  private void pack(int i, int index, float score) {
    packed[i] = packedValue(index, score);
  }

  public void score(float[] scores) {
    final int length = size();
    if (length > scores.length) {
      throw new AssertionError("Called with an array of scores too small to fit");
    }
    for (int i = 0; i < length; ++i) {
      // Since this is the critical method, we optimize it even further.
      // We could do this:
      // int index = unpackIndex; float score = unpackScore;
      // That results in an extra array lookup
      final long pack = packed[i];
      final int index = (int) (pack >>> 32);
      final float score = Float.intBitsToFloat((int) (pack & 0xFFFFFFFF));
      scores[index] += score;
    }
  }

  public void addScaled(Weight other, float scale) {
    final int otherLength = other.size();
    for (int i = 0; i < otherLength; ++i) {
      final int index = other.unpackIndex(i);
      final float score = other.unpackScore(i);
      updateWeight(index, score * scale);
    }
  }

  static private final float THRESHOLD = 0.00001f;

  void condense() {
    // threshold is in case floating point math makes a feature we
    // don't care about exist
    if (packed == null) {
      return;
    }

    int nonzero = 0;
    final int length = this.size();
    for (int i = 0; i < length; ++i) {
      if (Math.abs(unpackScore(i)) > THRESHOLD) {
        ++nonzero;
      }
    }

    if (nonzero == 0) {
      packed = null;
      return;
    }

    if (nonzero == length) {
      return;
    }

    long[] newPacked = new long[nonzero];
    int j = 0;
    for (int i = 0; i < length; ++i) {
      if (Math.abs(unpackScore(i)) <= THRESHOLD) {
        continue;
      }
      int index = unpackIndex(i);
      float score = unpackScore(i);
      pack(newPacked, j, index, score);
      ++j;
    }
    packed = newPacked;
  }

  public float getScore(int index) {
    if (packed == null) {
      return 0.0f;
    }

    final int length = size();
    for (int i = 0; i < length; ++i) {
      if (unpackIndex(i) == index) {
        return unpackScore(i);
      }
    }
    return 0.0f;
  }

  public void updateWeight(int index, float increment) {
    if (index < 0) {
      return;
    }

    if (packed == null) {
      packed = new long[1];
      pack(0, index, increment);
      return;
    }

    final int length = size();
    for (int i = 0; i < length; ++i) {
      if (unpackIndex(i) == index) {
        float score = unpackScore(i);
        pack(i, index, score + increment);
        return;
      }
    }

    long[] newPacked = new long[length + 1];
    for (int i = 0; i < length; ++i) {
      newPacked[i] = packed[i];
    }
    pack(newPacked, length, index, increment);
    packed = newPacked;
  }

  float maxAbs() {
    if (packed == null) {
      return 0.0f;
    }

    float maxScore = 0.0f;
    final int length = size();
    for (int i = 0; i < length; ++i) {
      float score = Math.abs(unpackScore(i));
      maxScore = Math.max(score, maxScore);
    }

    return maxScore;
  }

  /**
   * Moves the weights closer to 0 as a form of l1 regularization
   */
  void l1Reg(float reg) {
    if (packed == null) {
      return;
    }

    final int length = size();
    for (int i = 0; i < length; ++i) {
      int index = unpackIndex(i);
      float score = unpackScore(i);
      if (score > 0.0f) {
        score = Math.max(0.0f, score - reg);
      } else {
        score = Math.min(0.0f, score + reg);
      }
      pack(i, index, score);
    }
  }

  /**
   * Moves the weights closer to 0 as a form of l2 regularization
   */
  void l2Reg(float reg) {
    if (packed == null) {
      return;
    }

    final int length = size();
    for (int i = 0; i < length; ++i) {
      int index = unpackIndex(i);
      float score = unpackScore(i);
      score = score - score * reg;
      pack(i, index, score);
    }
  }

  public String toString() {
    StringBuilder builder = new StringBuilder();
    final int length = size();
    for (int i = 0; i < length; ++i) {
      if (i > 0) builder.append("   ");
      builder.append(unpackIndex(i) + "=" + unpackScore(i));
    }
    return builder.toString();
  }

  private long[] packed;

  private static final long serialVersionUID = 1;

}
