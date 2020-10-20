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

  private static long pack(int index, float score) {
    long pack = ((long) (Float.floatToIntBits(score))) & 0x00000000FFFFFFFFL;
    pack = pack | (((long) index) << 32);
    return pack;
  }

  public void score(float[] scores) {
    for (int i = 0; i < size(); ++i) {
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
    for (int i = 0; i < other.size(); ++i) {
      int index = other.unpackIndex(i);
      float score = other.unpackScore(i);
      updateWeight(index, score * scale);
    }
  }

  public void condense() {
    if (packed == null) {
      return;
    }

    int nonzero = 0;
    for (int i = 0; i < packed.length; ++i) {
      if (unpackScore(i) != 0.0f) {
        ++nonzero;
      }
    }

    if (nonzero == 0) {
      packed = null;
      return;
    }

    if (nonzero == packed.length) {
      return;
    }

    long[] newPacked = new long[nonzero];
    int j = 0;
    for (int i = 0; i < packed.length; ++i) {
      if (unpackScore(i) == 0.0f) {
        continue;
      }
      int index = unpackIndex(i);
      float score = unpackScore(i);
      newPacked[j] = pack(index, score);
      ++j;
    }
    packed = newPacked;
  }

  public void updateWeight(int index, float increment) {
    if (index < 0) {
      return;
    }

    if (packed == null) {
      packed = new long[1];
      packed[0] = pack(index, increment);
      return;
    }

    for (int i = 0; i < packed.length; ++i) {
      if (unpackIndex(i) == index) {
        float score = unpackScore(i);
        packed[i] = pack(index, score + increment);
        return;
      }
    }

    long[] newPacked = new long[packed.length + 1];
    for (int i = 0; i < packed.length; ++i) {
      newPacked[i] = packed[i];
    }
    newPacked[packed.length] = pack(index, increment);
    packed = newPacked;
  }

  private long[] packed;

  private static final long serialVersionUID = 1;

}
