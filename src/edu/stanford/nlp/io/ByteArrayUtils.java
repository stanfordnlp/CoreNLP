package edu.stanford.nlp.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Static methods for putting shorts, ints, and longs into a ByteArrayOutputStream using bit fiddling
 *
 * @author John Bauer
 */
public class ByteArrayUtils {
  static public short readShort(ByteArrayInputStream bin) {
    int high = ((bin.read() & 0x000000FF) << 8);
    int low = (bin.read() & 0x000000FF);
    return (short) ((high | low) & 0x0000FFFF);
  }

  static public void writeShort(ByteArrayOutputStream bout, short val) {
    bout.write((byte)((val >> 8) & 0xff));
    bout.write((byte)(val & 0xff));
  }

  static public int readInt(ByteArrayInputStream bin) {
    int b24 = ((bin.read() & 0x000000FF) << 24);
    int b16 = ((bin.read() & 0x000000FF) << 16);
    int b8 = ((bin.read() & 0x000000FF) << 8);
    int b0 = (bin.read() & 0x000000FF);
    return b24 | b16 | b8 | b0;
  }

  static public void writeInt(ByteArrayOutputStream bout, int val) {
    bout.write((byte)((val >> 24) & 0xff));
    bout.write((byte)((val >> 16) & 0xff));
    bout.write((byte)((val >> 8) & 0xff));
    bout.write((byte)(val & 0xff));
  }

  static public long readLong(ByteArrayInputStream bin) {
    long b56 = ((long) (bin.read() & 0x000000FF)) << 56;
    long b48 = ((long) (bin.read() & 0x000000FF)) << 48;
    long b40 = ((long) (bin.read() & 0x000000FF)) << 40;
    long b32 = ((long) (bin.read() & 0x000000FF)) << 32;
    long b24 = ((long) (bin.read() & 0x000000FF)) << 24;
    long b16 = ((long) (bin.read() & 0x000000FF)) << 16;
    long b8 = ((long) (bin.read() & 0x000000FF)) << 8;
    long b0 = ((long) (bin.read() & 0x000000FF));
    return b56 | b48 | b40 | b32 | b24 | b16 | b8 | b0;
  }

  static public void writeLong(ByteArrayOutputStream bout, long val) {
    bout.write((byte)((val >> 56) & 0xff));
    bout.write((byte)((val >> 48) & 0xff));
    bout.write((byte)((val >> 40) & 0xff));
    bout.write((byte)((val >> 32) & 0xff));
    bout.write((byte)((val >> 24) & 0xff));
    bout.write((byte)((val >> 16) & 0xff));
    bout.write((byte)((val >> 8) & 0xff));
    bout.write((byte)(val & 0xff));
  }
}
