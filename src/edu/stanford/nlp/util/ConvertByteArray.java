package edu.stanford.nlp.util;

import java.io.*;
import java.util.Vector;

/**
 * This is used to convert an array of double into byte array which makes it possible to keep it more efficiently.
 *
 * @author Kristina Toutanova
 */
public final class ConvertByteArray {

  private ConvertByteArray() {} // static methods

  private static short SHORTFLAG = 0x00ff;
  private static int INTFLAG = 0x000000ff;
  private static long LONGFLAG = 0x00000000000000ff;

  /**
   * Write an int into 4 bytes of an array, least significant byte first.
   *
   * @param b The array to write into
   * @param off The position in {@code b} at which to write
   * @param i The value to write
   */
  public static void writeIntToByteArr(byte[] b, int off, int i) {
    b[off] = (byte) i;
    b[off + 1] = (byte) (i >> 8);
    b[off + 2] = (byte) (i >> 16);
    b[off + 3] = (byte) (i >> 24);
  }

  /**
   * Write a long into 8 bytes of an array, least significant byte first.
   *
   * @param b The array to write into
   * @param off The position in {@code b} at which to write
   * @param l The value to write
   */
  public static void writeLongToByteArr(byte[] b, int off, long l) {
    for (int i = 0; i < 8; i++) {
      b[off + i] = (byte) (l >> (8 * i));
    }
  }

  /**
   * Write the bits of a float into 4 bytes of an array, least significant byte first.
   *
   * @param b The array to write into
   * @param off The position in {@code b} at which to write
   * @param f The value to write
   */
  public static void writeFloatToByteArr(byte[] b, int off, float f) {
    int i = Float.floatToIntBits(f);
    writeIntToByteArr(b, off, i);
  }

  /**
   * Write the bits of a double into 8 bytes of an array, least significant byte first.
   *
   * @param b The array to write into
   * @param off The position in {@code b} at which to write
   * @param d The value to write
   */
  public static void writeDoubleToByteArr(byte[] b, int off, double d) {
    long l = Double.doubleToLongBits(d);
    writeLongToByteArr(b, off, l);
  }

  /**
   * Write a boolean into one byte of an array. Note that true is written as 0 and false as 1.
   *
   * @param b The array to write into
   * @param off The position in {@code b} at which to write
   * @param bool The value to write
   */
  public static void writeBooleanToByteArr(byte[] b, int off, boolean bool) {
    if (bool) {
      b[off] = 0;
    } else {
      b[off] = 1;
    }
  }

  /**
   * Write a char into 2 bytes of an array, most significant byte first.
   *
   * @param b The array to write into
   * @param off The position in {@code b} at which to write
   * @param c The value to write
   */
  public static void writeCharToByteArr(byte[] b, int off, char c) {
    b[off + 1] = (byte) c;
    b[off] = (byte) (c >> 8);
  }

  /**
   * Write a short into 2 bytes of an array, least significant byte first.
   *
   * @param b The array to write into
   * @param off The position in {@code b} at which to write
   * @param s The value to write
   */
  public static void writeShortToByteArr(byte[] b, int off, short s) {
    b[off] = (byte) s;
    b[off + 1] = (byte) (s >> 8);
  }

  /**
   * Write the chars of a String into an array, 2 bytes per char, most significant byte first.
   * The length is not written.
   *
   * @param b The array to write into
   * @param off The position in {@code b} at which to write
   * @param s The String to write
   */
  public static void writeUStringToByteArr(byte[] b, int off, String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      b[2 * i + 1 + off] = (byte) c;
      b[2 * i + off] = (byte) (c >> 8);
    }
  }

  /**
   * Write {@code length} chars of a String, starting at char {@code pos}, into an array,
   * 2 bytes per char, most significant byte first. Char {@code i} of the String is written
   * at {@code off + 2 * i}, so the output starts at {@code off + 2 * pos}, not at {@code off}.
   *
   * @param b The array to write into
   * @param off The position in {@code b} corresponding to char 0 of the String
   * @param s The String to write
   * @param pos The first char of {@code s} to write
   * @param length The number of chars to write
   */
  public static void writeUStringToByteArr(byte[] b, int off, String s, int pos, int length) {
    for (int i = pos; i < (pos + length); i++) {
      char c = s.charAt(i);
      b[2 * i + 1 + off] = (byte) c;
      b[2 * i + off] = (byte) (c >> 8);
    }
  }

  /**
   * Write a String into an array, encoded in the platform default charset.
   * Only {@code s.length()} bytes are copied, so this is only correct when each char encodes to one byte.
   * The length is not written.
   *
   * @param b The array to write into
   * @param off The position in {@code b} at which to write
   * @param s The String to write
   */
  public static void writeAStringToByteArr(byte[] b, int off, String s) {
    System.arraycopy(s.getBytes(), 0, b, off, s.length());
  }

  /**
   * Write the substring of {@code s} from {@code pos} to {@code pos + length} into an array
   * at {@code off}, as in {@link #writeAStringToByteArr(byte[], int, String)}.
   *
   * @param b The array to write into
   * @param off The position in {@code b} at which to write
   * @param s The String to write from
   * @param pos The first char of {@code s} to write
   * @param length The number of chars to write
   */
  public static void writeAStringToByteArr(byte[] b, int off, String s, int pos, int length) {
    String sub = s.substring(pos, pos + length);
    writeAStringToByteArr(b, off, sub);
  }

  //-------------------------------------------------------------------

  /**
   * Read an int from 4 bytes of an array, least significant byte first.
   *
   * @param b The array to read from
   * @param off The position in {@code b} at which to read
   * @return The int read
   */
  public static int byteArrToInt(byte[] b, int off) {
    int z = 0;
    for (int i = 3; i > 0; i--) {
      z = (z | (b[off + i] & INTFLAG)) << 8;
    }
    z = z | (b[off] & INTFLAG);
    return z;
  }

  /**
   * Read a short from 2 bytes of an array, least significant byte first.
   *
   * @param b The array to read from
   * @param off The position in {@code b} at which to read
   * @return The short read
   */
  public static short byteArrToShort(byte[] b, int off) {
    short s = (short) (((0 | (b[off + 1] & SHORTFLAG)) << 8) | (b[off] & SHORTFLAG));
    return s;
  }

  /**
   * Read a float from 4 bytes of an array, least significant byte first.
   *
   * @param b The array to read from
   * @param off The position in {@code b} at which to read
   * @return The float read
   */
  public static float byteArrToFloat(byte[] b, int off) {
    int i = byteArrToInt(b, off);
    return Float.intBitsToFloat(i);
  }

  /**
   * Read a double from 8 bytes of an array, least significant byte first.
   *
   * @param b The array to read from
   * @param off The position in {@code b} at which to read
   * @return The double read
   */
  public static double byteArrToDouble(byte[] b, int off) {
    long l = byteArrToLong(b, off);
    return Double.longBitsToDouble(l);
  }

  /**
   * Read a long from 8 bytes of an array, least significant byte first.
   *
   * @param b The array to read from
   * @param off The position in {@code b} at which to read
   * @return The long read
   */
  public static long byteArrToLong(byte[] b, int off) {
    long z = 0;
    for (int i = 7; i > 0; i--) {
      z = (z | (b[off + i] & LONGFLAG)) << 8;
    }
    z = z | (b[off] & LONGFLAG);
    return z;
  }

  /**
   * Read a boolean from one byte of an array: 0 is true and anything else is false.
   *
   * @param b The array to read from
   * @param off The position in {@code b} at which to read
   * @return The boolean read
   */
  public static boolean byteArrToBoolean(byte[] b, int off) {
    return b[off] == 0;
  }

  /**
   * Read a char from 2 bytes of an array, most significant byte first.
   *
   * @param b The array to read from
   * @param off The position in {@code b} at which to read
   * @return The char read
   */
  public static char byteArrToChar(byte[] b, int off) {
    char c = (char) ((b[off] << 8) | b[off + 1]);
    return c;
  }

  /**
   * Read a String from an array holding 2 bytes per char, most significant byte first.
   * If the array has an odd length, the last byte is ignored.
   *
   * @param b The array to read from
   * @return The String read
   */
  public static String byteArrToUString(byte[] b) {
    String s;
    if (b.length == 0) {
      s = "";
    } else {
      char[] c = new char[b.length / 2];
      for (int i = 0; i < (b.length / 2); i++) {
        int j = (b[2 * i] << 8) | b[2 * i + 1];
        c[i] = (char) j;
      }
      s = new String(c);
    }
    return s;
  }

  /**
   * Read a String of {@code strLen} chars from an array holding 2 bytes per char,
   * most significant byte first.
   *
   * @param b The array to read from
   * @param off The position in {@code b} at which to read
   * @param strLen The number of chars to read
   * @return The String read
   */
  public static String byteArrToUString(byte[] b, int off, int strLen) {
    String s;
    if (strLen == 0) {
      s = "";
    } else {
      char[] c = new char[strLen];
      for (int i = 0; i < strLen; i++) {
        int j = (b[2 * i + off] << 8) | b[2 * i + 1 + off];
        c[i] = (char) j;
      }
      s = new String(c);
    }
    return s;
  }

  /**
   * Decode an array as a String using the platform default charset.
   *
   * @param b The array to read from
   * @return The String read
   */
  public static String byteArrToAString(byte[] b) {
    return new String(b);
  }

  /**
   * Decode part of an array as a String using the platform default charset.
   *
   * @param b The array to read from
   * @param off The position in {@code b} at which to read
   * @param length The number of bytes to read
   * @return The String read
   */
  public static String byteArrToAString(byte[] b, int off, int length) {
    if (length == 0) {
      return "";
    } else {
      return new String(b, off, length);
    }
  }

  //-----------------------------------------------------------------

  /**
   * Convert a String to a new array holding 2 bytes per char, most significant byte first.
   *
   * @param s The String to convert
   * @return The encoded String
   */
  public static byte[] stringUToByteArr(String s) {
    char c;
    byte[] b = new byte[2 * s.length()];
    for (int i = 0; i < s.length(); i++) {
      c = s.charAt(i);
      b[2 * i + 1] = (byte) c;
      b[2 * i] = (byte) (c >> 8);
    }
    return b;
  }

  /**
   * Convert a String to bytes using the platform default charset.
   *
   * @param s The String to convert
   * @return The encoded String
   */
  public static byte[] stringAToByteArr(String s) {
    return s.getBytes();
  }

  //-----------------------------------------------------------------

  /**
   * Convert a whole int array to a new byte array, 4 bytes each, least significant byte first.
   *
   * @param i The array to convert
   * @return The encoded values
   */
  public static byte[] intArrToByteArr(int[] i) {
    return intArrToByteArr(i, 0, i.length);
  }

  /**
   * Convert {@code length} values of a int array, starting at {@code off}, to a new byte array, 4 bytes each, least significant byte first.
   *
   * @param i The array to convert
   * @param off The first element of {@code i} to convert
   * @param length The number of elements to convert
   * @return The encoded values
   */
  public static byte[] intArrToByteArr(int[] i, int off, int length) {
    byte[] y = new byte[4 * length];
    for (int j = off; j < (off + length); j++) {
      y[4 * (j - off)] = (byte) i[j];
      y[4 * (j - off) + 1] = (byte) (i[j] >> 8);
      y[4 * (j - off) + 2] = (byte) (i[j] >> 16);
      y[4 * (j - off) + 3] = (byte) (i[j] >> 24);
    }
    return y;
  }

  /**
   * Write {@code len} values of a int array, starting at {@code off}, into a byte array at {@code pos}, 4 bytes each, least significant byte first.
   *
   * @param b The array to write into
   * @param pos The position in {@code b} at which to write
   * @param i The array to convert
   * @param off The first element of {@code i} to convert
   * @param len The number of elements to convert
   */
  public static void intArrToByteArr(byte[] b, int pos, int[] i, int off, int len) {
    for (int j = off; j < (off + len); j++) {
      b[4 * (j - off) + pos] = (byte) i[j];
      b[4 * (j - off) + 1 + pos] = (byte) (i[j] >> 8);
      b[4 * (j - off) + 2 + pos] = (byte) (i[j] >> 16);
      b[4 * (j - off) + 3 + pos] = (byte) (i[j] >> 24);
    }
  }

  /**
   * Convert a whole long array to a new byte array, 8 bytes each, least significant byte first.
   *
   * @param l The array to convert
   * @return The encoded values
   */
  public static byte[] longArrToByteArr(long[] l) {
    return longArrToByteArr(l, 0, l.length);
  }

  /**
   * Convert {@code length} values of a long array, starting at {@code off}, to a new byte array, 8 bytes each, least significant byte first.
   *
   * @param l The array to convert
   * @param off The first element of {@code l} to convert
   * @param length The number of elements to convert
   * @return The encoded values
   */
  public static byte[] longArrToByteArr(long[] l, int off, int length) {
    byte[] b = new byte[8 * length];
    for (int j = off; j < (off + length); j++) {
      for (int i = 0; i < 8; i++) {
        b[8 * (j - off) + i] = (byte) (l[j] >> (8 * i));
      }
    }
    return b;
  }

  /**
   * Write {@code length} values of a long array, starting at {@code off}, into a byte array at {@code pos}, 8 bytes each, least significant byte first.
   *
   * @param b The array to write into
   * @param pos The position in {@code b} at which to write
   * @param l The array to convert
   * @param off The first element of {@code l} to convert
   * @param length The number of elements to convert
   */
  public static void longArrToByteArr(byte[] b, int pos, long[] l, int off, int length) {
    for (int j = off; j < (off + length); j++) {
      for (int i = 0; i < 8; i++) {
        b[8 * (j - off) + i + pos] = (byte) (l[j] >> (8 * i));
      }
    }
  }

  /**
   * Convert a whole boolean array to a new byte array, one byte each, with true as 0 and false as 1.
   *
   * @param b The array to convert
   * @return The encoded values
   */
  public static byte[] booleanArrToByteArr(boolean[] b) {
    return booleanArrToByteArr(b, 0, b.length);
  }

  /**
   * Convert {@code len} values of a boolean array, starting at {@code off}, to a new byte array, one byte each, with true as 0 and false as 1. Note that {@code off} is ignored: elements are always read from the start of {@code b}.
   *
   * @param b The array to convert
   * @param off The first element of {@code b} to convert
   * @param len The number of elements to convert
   * @return The encoded values
   */
  public static byte[] booleanArrToByteArr(boolean[] b, int off, int len) {
    byte[] bytes = new byte[len];
    for (int i = 0; i < len; i++) {
      if (b[i]) {
        bytes[i] = 0;
      } else {
        bytes[i] = 1;
      }
    }
    return bytes;
  }

  /**
   * Write {@code length} values of a boolean array, starting at {@code off}, into a byte array at {@code pos}, one byte each, with true as 0 and false as 1. Note that {@code off} is ignored: elements are always read from the start of {@code b}.
   *
   * @param bytes The array to write into
   * @param pos The position in {@code bytes} at which to write
   * @param b The array to convert
   * @param off The first element of {@code b} to convert
   * @param length The number of elements to convert
   */
  public static void booleanArrToByteArr(byte[] bytes, int pos, boolean[] b, int off, int length) {
    for (int i = 0; i < length; i++) {
      if (b[i]) {
        bytes[i + pos] = 0;
      } else {
        bytes[i + pos] = 1;
      }
    }
  }

  /**
   * Convert a whole char array to a new byte array, 2 bytes each, most significant byte first.
   *
   * @param c The array to convert
   * @return The encoded values
   */
  public static byte[] charArrToByteArr(char[] c) {
    return charArrToByteArr(c, 0, c.length);
  }

  /**
   * Convert {@code len} values of a char array, starting at {@code off}, to a new byte array, 2 bytes each, most significant byte first.
   *
   * @param c The array to convert
   * @param off The first element of {@code c} to convert
   * @param len The number of elements to convert
   * @return The encoded values
   */
  public static byte[] charArrToByteArr(char[] c, int off, int len) {
    byte[] b = new byte[len * 2];
    for (int i = 0; i < len; i++) {
      b[2 * i + 1] = (byte) c[off + i];
      b[2 * i] = (byte) (c[off + i] >> 8);
    }
    return b;
  }

  /**
   * Write {@code len} values of a char array, starting at {@code off}, into a byte array at {@code pos}, 2 bytes each, most significant byte first.
   *
   * @param b The array to write into
   * @param pos The position in {@code b} at which to write
   * @param c The array to convert
   * @param off The first element of {@code c} to convert
   * @param len The number of elements to convert
   */
  public static void charArrToByteArr(byte[] b, int pos, char[] c, int off, int len) {
    for (int i = 0; i < len; i++) {
      b[2 * i + 1 + pos] = (byte) c[off + i];
      b[2 * i + pos] = (byte) (c[off + i] >> 8);
    }
  }

  /**
   * Convert a whole float array to a new byte array, 4 bytes each, least significant byte first.
   *
   * @param f The array to convert
   * @return The encoded values
   */
  public static byte[] floatArrToByteArr(float[] f) {
    return floatArrToByteArr(f, 0, f.length);
  }

  /**
   * Convert {@code length} values of a float array, starting at {@code off}, to a new byte array, 4 bytes each, least significant byte first.
   *
   * @param f The array to convert
   * @param off The first element of {@code f} to convert
   * @param length The number of elements to convert
   * @return The encoded values
   */
  public static byte[] floatArrToByteArr(float[] f, int off, int length) {
    byte[] y = new byte[4 * length];
    for (int j = off; j < (off + length); j++) {
      int i = Float.floatToIntBits(f[j]);
      y[4 * (j - off)] = (byte) i;
      y[4 * (j - off) + 1] = (byte) (i >> 8);
      y[4 * (j - off) + 2] = (byte) (i >> 16);
      y[4 * (j - off) + 3] = (byte) (i >> 24);
    }
    return y;
  }

  /**
   * Write {@code len} values of a float array, starting at {@code off}, into a byte array at {@code pos}, 4 bytes each, least significant byte first.
   *
   * @param b The array to write into
   * @param pos The position in {@code b} at which to write
   * @param f The array to convert
   * @param off The first element of {@code f} to convert
   * @param len The number of elements to convert
   */
  public static void floatArrToByteArr(byte[] b, int pos, float[] f, int off, int len) {
    for (int j = off; j < (off + len); j++) {
      int i = Float.floatToIntBits(f[j]);
      b[4 * (j - off) + pos] = (byte) i;
      b[4 * (j - off) + 1 + pos] = (byte) (i >> 8);
      b[4 * (j - off) + 2 + pos] = (byte) (i >> 16);
      b[4 * (j - off) + 3 + pos] = (byte) (i >> 24);
    }
  }

  /**
   * Convert a whole double array to a new byte array, 8 bytes each, least significant byte first.
   *
   * @param d The array to convert
   * @return The encoded values
   */
  public static byte[] doubleArrToByteArr(double[] d) {
    return doubleArrToByteArr(d, 0, d.length);
  }

  /**
   * Convert {@code length} values of a double array, starting at {@code off}, to a new byte array, 8 bytes each, least significant byte first.
   *
   * @param d The array to convert
   * @param off The first element of {@code d} to convert
   * @param length The number of elements to convert
   * @return The encoded values
   */
  public static byte[] doubleArrToByteArr(double[] d, int off, int length) {
    byte[] b = new byte[8 * length];
    for (int j = off; j < (off + length); j++) {
      long l = Double.doubleToLongBits(d[j]);
      for (int i = 0; i < 8; i++) {
        b[8 * (j - off) + i] = (byte) (l >> (8 * i));
      }
    }
    return b;
  }

  /**
   * Write {@code length} values of a double array, starting at {@code off}, into a byte array at {@code pos}, 8 bytes each, least significant byte first.
   *
   * @param b The array to write into
   * @param pos The position in {@code b} at which to write
   * @param d The array to convert
   * @param off The first element of {@code d} to convert
   * @param length The number of elements to convert
   */
  public static void doubleArrToByteArr(byte[] b, int pos, double[] d, int off, int length) {
    for (int j = off; j < (off + length); j++) {
      long l = Double.doubleToLongBits(d[j]);
      for (int i = 0; i < 8; i++) {
        b[8 * (j - off) + i + pos] = (byte) (l >> (8 * i));
      }
    }
  }

  /**
   * Convert a whole short array to a new byte array, least significant byte first, with each value placed at a stride of 4 bytes in an array of only 2 bytes per value (so this fails for more than one value).
   *
   * @param s The array to convert
   * @return The encoded values
   */
  public static byte[] shortArrToByteArr(short[] s) {
    return shortArrToByteArr(s, 0, s.length);
  }

  /**
   * Convert {@code length} values of a short array, starting at {@code off}, to a new byte array, least significant byte first, with each value placed at a stride of 4 bytes in an array of only 2 bytes per value (so this fails for more than one value).
   *
   * @param s The array to convert
   * @param off The first element of {@code s} to convert
   * @param length The number of elements to convert
   * @return The encoded values
   */
  public static byte[] shortArrToByteArr(short[] s, int off, int length) {
    byte[] y = new byte[2 * length];
    for (int j = off; j < (off + length); j++) {
      y[4 * (j - off)] = (byte) s[j];
      y[4 * (j - off) + 1] = (byte) (s[j] >> 8);
    }
    return y;
  }

  /**
   * Write {@code len} values of a short array, starting at {@code off}, into a byte array at {@code pos}, least significant byte first, with each value placed at a stride of 4 bytes.
   *
   * @param b The array to write into
   * @param pos The position in {@code b} at which to write
   * @param s The array to convert
   * @param off The first element of {@code s} to convert
   * @param len The number of elements to convert
   */
  public static void shortArrToByteArr(byte[] b, int pos, short[] s, int off, int len) {
    for (int j = off; j < (off + len); j++) {
      b[4 * (j - off) + pos] = (byte) s[j];
      b[4 * (j - off) + 1 + pos] = (byte) (s[j] >> 8);
    }
  }

  /**
   * Convert a whole String array to a new byte array: each String is written as its length in chars (4 bytes, least significant byte first)
   * followed by its chars (2 bytes each, most significant byte first). A null String is written as length 0.
   *
   * @param s The array to convert
   * @return The encoded values
   */
  public static byte[] uStringArrToByteArr(String[] s) {
    return uStringArrToByteArr(s, 0, s.length);
  }

  /**
   * Convert {@code length} values of a String array, starting at {@code off}, to a new byte array: each String is written as its length in chars (4 bytes, least significant byte first)
   * followed by its chars (2 bytes each, most significant byte first). A null String is written as length 0.
   * The returned array has room for 4 bytes per element of the whole of {@code s}, so it has trailing zero bytes when {@code length < s.length}.
   *
   * @param s The array to convert
   * @param off The first element of {@code s} to convert
   * @param length The number of elements to convert
   * @return The encoded values
   */
  public static byte[] uStringArrToByteArr(String[] s, int off, int length) {
    int byteOff = 0;
    int byteCount = 0;
    for (int i = off; i < (off + length); i++) {
      if (s[i] != null) {
        byteCount += 2 * s[i].length();
      }
    }
    byte[] b = new byte[byteCount + 4 * s.length];
    for (int i = off; i < (off + length); i++) {
      if (s[i] != null) {
        writeIntToByteArr(b, byteOff, s[i].length());
        byteOff += 4;
        writeUStringToByteArr(b, byteOff, s[i]);
        byteOff += 2 * s[i].length();
      } else {
        writeIntToByteArr(b, byteOff, 0);
        byteOff += 4;
      }
    }
    return b;
  }

  /**
   * Write {@code length} values of a String array, starting at {@code off}, into a byte array at {@code pos}: each String is written as its length in chars (4 bytes, least significant byte first)
   * followed by its chars (2 bytes each, most significant byte first). A null String is written as length 0.
   *
   * @param b The array to write into
   * @param pos The position in {@code b} at which to write
   * @param s The array to convert
   * @param off The first element of {@code s} to convert
   * @param length The number of elements to convert
   */
  public static void uStringArrToByteArr(byte[] b, int pos, String[] s, int off, int length) {
    for (int i = off; i < (off + length); i++) {
      if (s[i] != null) {
        writeIntToByteArr(b, pos, s[i].length());
        pos += 4;
        writeUStringToByteArr(b, pos, s[i]);
        pos += 2 * s[i].length();
      } else {
        writeIntToByteArr(b, pos, 0);
        pos += 4;
      }
    }
  }

  /**
   * Convert a whole String array to a new byte array: each String is written as its length (4 bytes, least significant byte first)
   * followed by its chars encoded in the platform default charset. A null String is written as length 0.
   *
   * @param s The array to convert
   * @return The encoded values
   */
  public static byte[] aStringArrToByteArr(String[] s) {
    return aStringArrToByteArr(s, 0, s.length);
  }

  /**
   * Convert {@code length} values of a String array, starting at {@code off}, to a new byte array: each String is written as its length (4 bytes, least significant byte first)
   * followed by its chars encoded in the platform default charset. A null String is written as length 0.
   * The returned array has room for 4 bytes per element of the whole of {@code s}, so it has trailing zero bytes when {@code length < s.length}.
   *
   * @param s The array to convert
   * @param off The first element of {@code s} to convert
   * @param length The number of elements to convert
   * @return The encoded values
   */
  public static byte[] aStringArrToByteArr(String[] s, int off, int length) {
    int byteOff = 0;
    int byteCount = 0;
    for (int i = off; i < (off + length); i++) {
      if (s[i] != null) {
        byteCount += s[i].length();
      }
    }
    byte[] b = new byte[byteCount + 4 * s.length];
    for (int i = off; i < (off + length); i++) {
      if (s[i] != null) {
        writeIntToByteArr(b, byteOff, s[i].length());
        byteOff += 4;
        writeAStringToByteArr(b, byteOff, s[i]);
        byteOff += s[i].length();
      } else {
        writeIntToByteArr(b, byteOff, 0);
        byteOff += 4;
      }
    }
    return b;
  }

  /**
   * Write {@code length} values of a String array, starting at {@code off}, into a byte array at {@code pos}: each String is written as its length (4 bytes, least significant byte first)
   * followed by its chars encoded in the platform default charset. A null String is written as length 0.
   *
   * @param b The array to write into
   * @param pos The position in {@code b} at which to write
   * @param s The array to convert
   * @param off The first element of {@code s} to convert
   * @param length The number of elements to convert
   */
  public static void aStringArrToByteArr(byte[] b, int pos, String[] s, int off, int length) {
    for (int i = off; i < (off + length); i++) {
      if (s[i] != null) {
        writeIntToByteArr(b, pos, s[i].length());
        pos += 4;
        writeAStringToByteArr(b, pos, s[i]);
        pos += s[i].length();
      } else {
        writeIntToByteArr(b, pos, 0);
        pos += 4;
      }
    }
  }

  //-----------------------------------------------------------------

  /**
   * Decode a whole byte array to a new int array, 4 bytes each, least significant byte first. Trailing bytes which do not make up a whole value are ignored.
   *
   * @param b The array to decode
   * @return The decoded values
   */
  public static int[] byteArrToIntArr(byte[] b) {
    return byteArrToIntArr(b, 0, b.length / 4);
  }

  /**
   * Decode {@code length} values from a byte array, starting at {@code off}, into a new int array, 4 bytes each, least significant byte first.
   *
   * @param b The array to decode
   * @param off The position in {@code b} at which to read
   * @param length The number of values to decode
   * @return The decoded values
   */
  public static int[] byteArrToIntArr(byte[] b, int off, int length) {
    int[] z = new int[length];
    for (int i = 0; i < length; i++) {
      z[i] = 0;
      for (int j = 3; j > 0; j--) {
        z[i] = (z[i] | (b[off + j + 4 * i] & INTFLAG)) << 8;
      }
      z[i] = z[i] | (b[off + 4 * i] & INTFLAG);
    }
    return z;
  }

  /**
   * Decode {@code length} values from a byte array, starting at {@code off}, into a int array at {@code pos}, 4 bytes each, least significant byte first.
   *
   * @param b The array to decode
   * @param off The position in {@code b} at which to read
   * @param i The array to write the values into
   * @param pos The position in {@code i} at which to write
   * @param length The number of values to decode
   */
  public static void byteArrToIntArr(byte[] b, int off, int[] i, int pos, int length) {
    for (int j = 0; j < length; j++) {
      i[j + pos] = 0;
      for (int k = 3; k > 0; k--) {
        i[j + pos] = (i[j + pos] | (b[off + k + 4 * j] & INTFLAG)) << 8;
      }
      i[j + pos] = i[j + pos] | (b[off + 4 * j] & INTFLAG);
    }
  }

  /**
   * Decode a whole byte array to a new long array, 8 bytes each, least significant byte first. Trailing bytes which do not make up a whole value are ignored.
   *
   * @param b The array to decode
   * @return The decoded values
   */
  public static long[] byteArrToLongArr(byte[] b) {
    return byteArrToLongArr(b, 0, b.length / 8);
  }

  /**
   * Decode {@code length} values from a byte array, starting at {@code off}, into a new long array, 8 bytes each, least significant byte first.
   *
   * @param b The array to decode
   * @param off The position in {@code b} at which to read
   * @param length The number of values to decode
   * @return The decoded values
   */
  public static long[] byteArrToLongArr(byte[] b, int off, int length) {
    long[] l = new long[length];
    for (int i = 0; i < length; i++) {
      l[i] = 0;
      for (int j = 0; j < 8; j++) {
        l[i] = l[i] | ((b[8 * i + j + off] & 0x000000ff) << (8 * j));
      }
    }
    return l;
  }

  /**
   * Decode {@code length} values from a byte array, starting at {@code off}, into a long array at {@code pos}, 8 bytes each, least significant byte first.
   *
   * @param b The array to decode
   * @param off The position in {@code b} at which to read
   * @param l The array to write the values into
   * @param pos The position in {@code l} at which to write
   * @param length The number of values to decode
   */
  public static void byteArrToLongArr(byte[] b, int off, long[] l, int pos, int length) {
    for (int i = 0; i < length; i++) {
      l[i + pos] = 0;
      for (int j = 0; j < 8; j++) {
        l[i + pos] = l[i + pos] | ((b[8 * i + j + off] & 0x000000ff) << (8 * j));
      }
    }
  }

  /**
   * Decode a whole byte array to a new boolean array, one byte each, where 0 is true and anything else is false.
   *
   * @param b The array to decode
   * @return The decoded values
   */
  public static boolean[] byteArrToBooleanArr(byte[] b) {
    return byteArrToBooleanArr(b, 0, b.length);
  }

  /**
   * Decode {@code length} values from a byte array, starting at {@code off}, into a new boolean array, one byte each, where 0 is true and anything else is false.
   *
   * @param b The array to decode
   * @param off The position in {@code b} at which to read
   * @param length The number of values to decode
   * @return The decoded values
   */
  public static boolean[] byteArrToBooleanArr(byte[] b, int off, int length) {
    boolean[] bool = new boolean[length];
    for (int i = 0; i < length; i++) {
      bool[i] = b[i + off] == 0;
    }
    return bool;
  }

  /**
   * Decode {@code length} values from a byte array, starting at {@code off}, into a boolean array at {@code pos}, one byte each, where 0 is true and anything else is false.
   *
   * @param b The array to decode
   * @param off The position in {@code b} at which to read
   * @param bool The array to write the values into
   * @param pos The position in {@code bool} at which to write
   * @param length The number of values to decode
   */
  public static void byteArrToBooleanArr(byte[] b, int off, boolean[] bool, int pos, int length) {
    for (int i = 0; i < length; i++) {
      bool[i + pos] = b[i + off] == 0;
    }
  }

  /**
   * Decode a whole byte array to a new char array, 2 bytes each, most significant byte first. Trailing bytes which do not make up a whole value are ignored.
   *
   * @param b The array to decode
   * @return The decoded values
   */
  public static char[] byteArrToCharArr(byte[] b) {
    return byteArrToCharArr(b, 0, b.length / 2);
  }

  /**
   * Decode {@code length} values from a byte array, starting at {@code off}, into a new char array, 2 bytes each, most significant byte first.
   *
   * @param b The array to decode
   * @param off The position in {@code b} at which to read
   * @param length The number of values to decode
   * @return The decoded values
   */
  public static char[] byteArrToCharArr(byte[] b, int off, int length) {
    char[] c = new char[length];
    for (int i = 0; i < (length); i++) {
      c[i] = (char) ((b[2 * i + off] << 8) | b[2 * i + 1 + off]);
    }
    return c;
  }

  /**
   * Decode {@code length} values from a byte array, starting at {@code off}, into a char array at {@code pos}, 2 bytes each, most significant byte first.
   *
   * @param b The array to decode
   * @param off The position in {@code b} at which to read
   * @param c The array to write the values into
   * @param pos The position in {@code c} at which to write
   * @param length The number of values to decode
   */
  public static void byteArrToCharArr(byte[] b, int off, char[] c, int pos, int length) {
    for (int i = 0; i < (length); i++) {
      c[i + pos] = (char) ((b[2 * i + off] << 8) | b[2 * i + 1 + off]);
    }
  }

  /**
   * Decode a whole byte array to a new short array, 2 bytes each, least significant byte first. Trailing bytes which do not make up a whole value are ignored.
   *
   * @param b The array to decode
   * @return The decoded values
   */
  public static short[] byteArrToShortArr(byte[] b) {
    return byteArrToShortArr(b, 0, b.length / 2);
  }

  /**
   * Decode {@code length} values from a byte array, starting at {@code off}, into a new short array, 2 bytes each, least significant byte first.
   *
   * @param b The array to decode
   * @param off The position in {@code b} at which to read
   * @param length The number of values to decode
   * @return The decoded values
   */
  public static short[] byteArrToShortArr(byte[] b, int off, int length) {
    short[] z = new short[length];
    for (int i = 0; i < length; i++) {
      z[i] = (short) (((0 | (b[off + 1 + 2 * i] & SHORTFLAG)) << 8) | (b[off + 2 * i] & SHORTFLAG));
    }
    return z;
  }

  /**
   * Decode {@code length} values from a byte array, starting at {@code off}, into a short array at {@code pos}, 2 bytes each, least significant byte first.
   *
   * @param b The array to decode
   * @param off The position in {@code b} at which to read
   * @param s The array to write the values into
   * @param pos The position in {@code s} at which to write
   * @param length The number of values to decode
   */
  public static void byteArrToShortArr(byte[] b, int off, short[] s, int pos, int length) {
    for (int i = 0; i < length; i++) {
      s[i + pos] = (short) (((0 | (b[off + 1 + 2 * i] & SHORTFLAG)) << 8) | (b[off + 2 * i] & SHORTFLAG));
    }
  }

  /**
   * Decode a whole byte array to a new float array, 4 bytes each, least significant byte first. Trailing bytes which do not make up a whole value are ignored.
   *
   * @param b The array to decode
   * @return The decoded values
   */
  public static float[] byteArrToFloatArr(byte[] b) {
    return byteArrToFloatArr(b, 0, b.length / 4);
  }

  /**
   * Decode {@code length} values from a byte array, starting at {@code off}, into a new float array, 4 bytes each, least significant byte first.
   *
   * @param b The array to decode
   * @param off The position in {@code b} at which to read
   * @param length The number of values to decode
   * @return The decoded values
   */
  public static float[] byteArrToFloatArr(byte[] b, int off, int length) {
    float[] z = new float[length];
    for (int i = 0; i < length; i++) {
      int k = 0;
      for (int j = 3; j > 0; j--) {
        k = (k | (b[off + j + 4 * i] & INTFLAG)) << 8;
      }
      k = k | (b[off + 4 * i] & INTFLAG);
      z[i] = Float.intBitsToFloat(k);
    }
    return z;
  }

  /**
   * Decode {@code length} values from a byte array, starting at {@code off}, into a float array at {@code pos}, 4 bytes each, least significant byte first.
   *
   * @param b The array to decode
   * @param off The position in {@code b} at which to read
   * @param f The array to write the values into
   * @param pos The position in {@code f} at which to write
   * @param length The number of values to decode
   */
  public static void byteArrToFloatArr(byte[] b, int off, float[] f, int pos, int length) {
    for (int i = 0; i < length; i++) {
      int k = 0;
      for (int j = 3; j > 0; j--) {
        k = (k | (b[off + j + 4 * i] & INTFLAG)) << 8;
      }
      k = k | (b[off + 4 * i] & INTFLAG);
      f[pos + i] = Float.intBitsToFloat(k);
    }
  }

  /** This method allocates a new double[] to return, based on the size of
   *  the array b (namely b.length / 8 in size)
   * @param b Array to decode to doubles
   * @return Array of doubles.
   */
  public static double[] byteArrToDoubleArr(byte[] b) {
    return byteArrToDoubleArr(b, 0, b.length / 8);
  }

  /**
   * Decode {@code length} values from a byte array, starting at {@code off}, into a new double array, 8 bytes each, least significant byte first.
   *
   * @param b The array to decode
   * @param off The position in {@code b} at which to read
   * @param length The number of values to decode
   * @return The decoded values
   */
  public static double[] byteArrToDoubleArr(byte[] b, int off, int length) {
    double[] d = new double[length];
    for (int i = 0; i < length; i++) {
      long l = 0;
      for (int j = 0; j < 8; j++) {
        l = l | ((long) (b[8 * i + j + off] & 0x00000000000000ff) << (8 * j));
      }
      d[i] = Double.longBitsToDouble(l);
    }
    return d;
  }

  /**
   * Decode {@code length} values from a byte array, starting at {@code off}, into a double array at {@code pos}, 8 bytes each, least significant byte first.
   *
   * @param b The array to decode
   * @param off The position in {@code b} at which to read
   * @param d The array to write the values into
   * @param pos The position in {@code d} at which to write
   * @param length The number of values to decode
   */
  public static void byteArrToDoubleArr(byte[] b, int off, double[] d, int pos, int length) {
    for (int i = 0; i < length; i++) {
      long l = 0;
      for (int j = 0; j < 8; j++) {
        l = l | ((long) (b[8 * i + j + off] & 0x00000000000000ff) << (8 * j));
      }
      d[pos + i] = Double.longBitsToDouble(l);
    }
  }

  /**
   * Decode a whole byte array, in the format written by {@link #uStringArrToByteArr(String[])},
   * to a new String array. Strings written as null are decoded as empty Strings.
   *
   * @param b The array to decode
   * @return The decoded Strings
   */
  public static String[] byteArrToUStringArr(byte[] b) {
    int off = 0;
    Vector<String> v = new Vector<>();
    while (off < b.length) {
      int length = byteArrToInt(b, off);
      if (length != 0) {
        v.addElement(byteArrToUString(b, off + 4, length));
      } else {
        v.addElement("");
      }
      off = off + 2 * length + 4;
    }
    String[] s = new String[v.size()];
    for (int i = 0; i < s.length; i++) {
      s[i] = v.elementAt(i);
    }
    return s;
  }

  /**
   * Decode {@code length} Strings, in the format written by {@link #uStringArrToByteArr(String[])},
   * from a byte array starting at {@code off}. Strings written as null are decoded as empty Strings.
   *
   * @param b The array to decode
   * @param off The position in {@code b} at which to read
   * @param length The number of Strings to decode
   * @return The decoded Strings
   */
  public static String[] byteArrToUStringArr(byte[] b, int off, int length) {
    String[] s = new String[length];
    for (int i = 0; i < length; i++) {
      int stringLen = byteArrToInt(b, off);
      off += 4;
      if (stringLen != 0) {
        s[i] = byteArrToUString(b, off, stringLen);
        off += 2 * s[i].length();
      } else {
        s[i] = "";
      }
    }
    return s;
  }

  /**
   * Decode {@code length} Strings, in the format written by {@link #uStringArrToByteArr(String[])},
   * from a byte array starting at {@code off} into a String array at {@code pos}.
   * Note that the lengths used to advance through {@code b}, and the empty Strings stored for zero lengths,
   * use {@code s[i]} rather than {@code s[pos + i]}, so this only works correctly when {@code pos} is 0.
   *
   * @param b The array to decode
   * @param off The position in {@code b} at which to read
   * @param s The array to write the Strings into
   * @param pos The position in {@code s} at which to write
   * @param length The number of Strings to decode
   */
  public static void byteArrToUStringArr(byte[] b, int off, String[] s, int pos, int length) {
    for (int i = 0; i < length; i++) {
      int stringLen = byteArrToInt(b, off);
      off += 4;
      if (stringLen != 0) {
        s[i + pos] = byteArrToUString(b, off, stringLen);
        off += 2 * s[i].length();
      } else {
        s[i] = "";
      }
    }
  }

  /**
   * Decode a whole byte array, in the format written by {@link #aStringArrToByteArr(String[])},
   * to a new String array. Strings written as null are decoded as empty Strings.
   *
   * @param b The array to decode
   * @return The decoded Strings
   */
  public static String[] byteArrToAStringArr(byte[] b) {
    int off = 0;
    Vector<String> v = new Vector<>();
    while (off < b.length) {
      int length = byteArrToInt(b, off);
      if (length != 0) {
        v.addElement(byteArrToAString(b, off + 4, length));
      } else {
        v.addElement("");
      }
      off = off + length + 4;
    }
    String[] s = new String[v.size()];
    for (int i = 0; i < s.length; i++) {
      s[i] = v.elementAt(i);
    }
    return s;
  }

  /**
   * Decode {@code length} Strings, in the format written by {@link #aStringArrToByteArr(String[])},
   * from a byte array starting at {@code off}. Strings written as null are decoded as empty Strings.
   *
   * @param b The array to decode
   * @param off The position in {@code b} at which to read
   * @param length The number of Strings to decode
   * @return The decoded Strings
   */
  public static String[] byteArrToAStringArr(byte[] b, int off, int length) {
    String[] s = new String[length];
    for (int i = 0; i < length; i++) {
      int stringLen = byteArrToInt(b, off);
      off += 4;
      if (stringLen != 0) {
        s[i] = byteArrToAString(b, off, stringLen);
        off += s[i].length();
      } else {
        s[i] = "";
      }
    }
    return s;
  }

  /**
   * Decode {@code length} Strings, in the format written by {@link #aStringArrToByteArr(String[])},
   * from a byte array starting at {@code off} into a String array at {@code pos}.
   * Note that the lengths used to advance through {@code b}, and the empty Strings stored for zero lengths,
   * use {@code s[i]} rather than {@code s[pos + i]}, so this only works correctly when {@code pos} is 0.
   *
   * @param b The array to decode
   * @param off The position in {@code b} at which to read
   * @param s The array to write the Strings into
   * @param pos The position in {@code s} at which to write
   * @param length The number of Strings to decode
   */
  public static void byteArrToAStringArr(byte[] b, int off, String[] s, int pos, int length) {
    for (int i = 0; i < length; i++) {
      int stringLen = byteArrToInt(b, off);
      off += 4;
      if (stringLen != 0) {
        s[i + pos] = byteArrToAString(b, off, stringLen);
        off += s[i].length();
      } else {
        s[i] = "";
      }
    }
  }

  /**
   * Write a double array to a stream as its length (via {@link DataOutputStream#writeInt})
   * followed by its values, 8 bytes each, least significant byte first. The stream is closed afterwards.
   *
   * @param rf The stream to write to; it is closed by this method
   * @param arr The array to write
   * @throws IOException If writing to or closing the stream fails
   */
  public static void saveDoubleArr(DataOutputStream rf, double[] arr) throws IOException {
    rf.writeInt(arr.length);
    byte[] lArr = doubleArrToByteArr(arr);
    rf.write(lArr);
    rf.close();
  }

  /**
   * Write a float array to a stream as its length (via {@link DataOutputStream#writeInt})
   * followed by its values, 4 bytes each, least significant byte first. The stream is closed afterwards.
   *
   * @param rf The stream to write to; it is closed by this method
   * @param arr The array to write
   * @throws IOException If writing to or closing the stream fails
   */
  public static void saveFloatArr(DataOutputStream rf, float[] arr) throws IOException {
    rf.writeInt(arr.length);
    byte[] lArr = floatArrToByteArr(arr);
    rf.write(lArr);
    rf.close();
  }

  /**
   * Read a double array in the format written by {@link #saveDoubleArr}. The stream is not closed.
   * This uses a single {@code read} call, so it does not check that all the bytes were read.
   *
   * @param rf The stream to read from
   * @return The array read
   * @throws IOException If reading from the stream fails
   */
  public static double[] readDoubleArr(DataInputStream rf) throws IOException {
    int size = rf.readInt();
    byte[] b = new byte[8 * size];
    rf.read(b);
    return byteArrToDoubleArr(b);
  }

  /**
   * Read a float array in the format written by {@link #saveFloatArr}. The stream is not closed.
   * This uses a single {@code read} call, so it does not check that all the bytes were read.
   *
   * @param rf The stream to read from
   * @return The array read
   * @throws IOException If reading from the stream fails
   */
  public static float[] readFloatArr(DataInputStream rf) throws IOException {
    int size = rf.readInt();
    byte[] b = new byte[4 * size];
    rf.read(b);
    return byteArrToFloatArr(b);
  }

}
