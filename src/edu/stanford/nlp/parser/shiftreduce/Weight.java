package edu.stanford.nlp.parser.shiftreduce;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;

import edu.stanford.nlp.io.ByteArrayUtils;
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
  static final short[] EMPTY = {};

  public Weight() {
    packed = EMPTY;
  }

  public Weight(Weight other) {
    if (other.size() == 0) {
      packed = EMPTY;
      return;
    }
    packed = ArrayUtils.copy(other.packed);
    condense();
  }

  public int size() {
    // TODO: find a fast way of doing this... we know it's a multiple of 3 after all
    return packed.length / 3;
  }

  private short unpackIndex(int i) {
    return packed[i * 3];
  }

  private float unpackScore(int i) {
    i = i * 3 + 1;
    final int high = ((int) packed[i++]) << 16;
    final int low = packed[i] & 0x0000FFFF;
    return Float.intBitsToFloat(high | low);
  }

  private static void pack(short[] packed, int i, int index, float score) {
    if (i > Short.MAX_VALUE) {
      throw new ArithmeticException("How did you make an index with 30,000 weights??");
    }
    int pos = i * 3;
    packed[pos++] = (short) index;
    final int bits = Float.floatToIntBits(score);
    packed[pos++] = (short) ((bits & 0xFFFF0000) >> 16);
    packed[pos] = (short) (bits & 0x0000FFFF);
  }

  private void pack(int i, int index, float score) {
    if (i > Short.MAX_VALUE) {
      throw new ArithmeticException("How did you make an index with 30,000 weights??");
    }
    int pos = i * 3;
    packed[pos++] = (short) index;
    final int bits = Float.floatToIntBits(score);
    packed[pos++] = (short) ((bits & 0xFFFF0000) >> 16);
    packed[pos] = (short) (bits & 0x0000FFFF);
  }

  public void score(float[] scores) {
    if (packed.length > scores.length * 3) {
      throw new AssertionError("Called with an array of scores too small to fit");
    }
    for (int i = 0; i < packed.length; ) {
      // Since this is the critical method, we optimize it even further.
      // We could do this:
      // int index = unpackIndex; float score = unpackScore;
      // That results in extra operations
      final short index = packed[i++];
      final int high = ((int) packed[i++]) << 16;
      final int low = packed[i++] & 0x0000FFFF;
      final int bits = high | low;
      // final int bits = (((int) packed[i++]) << 16) | (packed[i++] & 0x0000FFFF);
      final float score = Float.intBitsToFloat(bits);
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
    if (packed == null || packed.length == 0) {
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
      packed = EMPTY;
      return;
    }

    if (nonzero == length) {
      return;
    }

    short[] newPacked = new short[nonzero * 3];
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

    if (packed == null || packed.length == 0) {
      packed = new short[3];
      pack(0, index, increment);
      return;
    }

    final int length = size();
    for (int i = 0; i < length; ++i) {
      if (unpackIndex(i) == index) {
        final float score = unpackScore(i);
        pack(i, index, score + increment);
        return;
      }
    }

    short[] newPacked = new short[packed.length + 3];
    for (int i = 0; i < packed.length; ++i) {
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
    builder.append("Weight(");
    for (int i = 0; i < length; ++i) {
      if (i > 0) builder.append("  ");
      builder.append(unpackIndex(i) + "=" + unpackScore(i));
    }
    builder.append(")");
    return builder.toString();
  }

  private short[] packed;

  void writeBytes(ByteArrayOutputStream bout) {
    ByteArrayUtils.writeInt(bout, packed.length);
    for (int i = 0; i < packed.length; ++i) {
      ByteArrayUtils.writeShort(bout, packed[i]);
    }
  }

  static Weight readBytes(ByteArrayInputStream bin) {
    int len = ByteArrayUtils.readInt(bin);
    Weight weight = new Weight();
    weight.packed = new short[len];
    for (int i = 0; i < len; ++i) {
      weight.packed[i] = ByteArrayUtils.readShort(bin);
    }
    return weight;
  }

  private static final long serialVersionUID = 3;
}
