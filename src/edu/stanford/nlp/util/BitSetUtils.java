package edu.stanford.nlp.util;

import java.util.BitSet;

/**
 * Code borrowed from here: http://www.exampledepot.com/egs/java.util/Bits2Array.html
 * @author Michel Galley
 */
public class BitSetUtils {

	private BitSetUtils() {}

	// Returns a bitset containing the values in bytes.
	// The byte-ordering of bytes must be big-endian which means the most significant bit is in element 0.
	public static BitSet fromByteArray(byte[] bytes) {
    BitSet bits = new BitSet();
    for (int i=0; i<bytes.length*8; i++) {
      if ((bytes[bytes.length-i/8-1]&(1<<(i%8))) > 0) {
        bits.set(i);
      }
    }
    return bits;
	}

	// Returns a byte array of at least length 1.
	// The most significant bit in the result is guaranteed not to be a 1
	// (since BitSet does not support sign extension).
	// The byte-ordering of the result is big-endian which means the most significant bit is in element 0.
	// The bit at index 0 of the bit set is assumed to be the least significant bit.
	public static byte[] toByteArray(BitSet bits) {
    byte[] bytes = new byte[bits.length()/8+1];
    for (int i=0; i<bits.length(); i++) {
      if (bits.get(i)) {
        bytes[bytes.length-i/8-1] |= 1<<(i%8);
      }
    }
    return bytes;
	}

  public static BitSet toBitSet(int v) {
    return toBitSet(v, 0);
  }

  public static BitSet toBitSet(int v, int shift) {
    int bitIdx = shift;
    BitSet bitset = new BitSet();
    while(v > 0) {
      if((v & 1) == 1)
        bitset.set(bitIdx);
      v = v >> 1;
      ++bitIdx;
    }
    return bitset;
  }
}
