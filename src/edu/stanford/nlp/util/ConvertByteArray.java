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

  public static void writeIntToByteArr(byte[] b, int off, int i) {
    b[off] = (byte) i;
    b[off + 1] = (byte) (i >> 8);
    b[off + 2] = (byte) (i >> 16);
    b[off + 3] = (byte) (i >> 24);
  }

  public static void writeLongToByteArr(byte[] b, int off, long l) {
    for (int i = 0; i < 8; i++) {
      b[off + i] = (byte) (l >> (8 * i));
    }
  }

  public static void writeFloatToByteArr(byte[] b, int off, float f) {
    int i = Float.floatToIntBits(f);
    writeIntToByteArr(b, off, i);
  }

  public static void writeDoubleToByteArr(byte[] b, int off, double d) {
    long l = Double.doubleToLongBits(d);
    writeLongToByteArr(b, off, l);
  }

  public static void writeBooleanToByteArr(byte[] b, int off, boolean bool) {
    if (bool) {
      b[off] = 0;
    } else {
      b[off] = 1;
    }
  }

  public static void writeCharToByteArr(byte[] b, int off, char c) {
    b[off + 1] = (byte) c;
    b[off] = (byte) (c >> 8);
  }

  public static void writeShortToByteArr(byte[] b, int off, short s) {
    b[off] = (byte) s;
    b[off + 1] = (byte) (s >> 8);
  }

  public static void writeUStringToByteArr(byte[] b, int off, String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      b[2 * i + 1 + off] = (byte) c;
      b[2 * i + off] = (byte) (c >> 8);
    }
  }

  public static void writeUStringToByteArr(byte[] b, int off, String s, int pos, int length) {
    for (int i = pos; i < (pos + length); i++) {
      char c = s.charAt(i);
      b[2 * i + 1 + off] = (byte) c;
      b[2 * i + off] = (byte) (c >> 8);
    }
  }

  public static void writeAStringToByteArr(byte[] b, int off, String s) {
    System.arraycopy(s.getBytes(), 0, b, off, s.length());
  }

  public static void writeAStringToByteArr(byte[] b, int off, String s, int pos, int length) {
    String sub = s.substring(pos, pos + length);
    writeAStringToByteArr(b, off, sub);
  }

  //-------------------------------------------------------------------

  public static int byteArrToInt(byte[] b, int off) {
    int z = 0;
    for (int i = 3; i > 0; i--) {
      z = (z | (b[off + i] & INTFLAG)) << 8;
    }
    z = z | (b[off] & INTFLAG);
    return z;
  }

  public static short byteArrToShort(byte[] b, int off) {
    short s = (short) (((0 | (b[off + 1] & SHORTFLAG)) << 8) | (b[off] & SHORTFLAG));
    return s;
  }

  public static float byteArrToFloat(byte[] b, int off) {
    int i = byteArrToInt(b, off);
    return Float.intBitsToFloat(i);
  }

  public static double byteArrToDouble(byte[] b, int off) {
    long l = byteArrToLong(b, off);
    return Double.longBitsToDouble(l);
  }

  public static long byteArrToLong(byte[] b, int off) {
    long z = 0;
    for (int i = 7; i > 0; i--) {
      z = (z | (b[off + i] & LONGFLAG)) << 8;
    }
    z = z | (b[off] & LONGFLAG);
    return z;
  }

  public static boolean byteArrToBoolean(byte[] b, int off) {
    return b[off] == 0;
  }

  public static char byteArrToChar(byte[] b, int off) {
    char c = (char) ((b[off] << 8) | b[off + 1]);
    return c;
  }

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

  public static String byteArrToAString(byte[] b) {
    return new String(b);
  }

  public static String byteArrToAString(byte[] b, int off, int length) {
    if (length == 0) {
      return "";
    } else {
      return new String(b, off, length);
    }
  }

  //-----------------------------------------------------------------

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

  public static byte[] stringAToByteArr(String s) {
    return s.getBytes();
  }

  //-----------------------------------------------------------------

  public static byte[] intArrToByteArr(int[] i) {
    return intArrToByteArr(i, 0, i.length);
  }

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

  public static void intArrToByteArr(byte[] b, int pos, int[] i, int off, int len) {
    for (int j = off; j < (off + len); j++) {
      b[4 * (j - off) + pos] = (byte) i[j];
      b[4 * (j - off) + 1 + pos] = (byte) (i[j] >> 8);
      b[4 * (j - off) + 2 + pos] = (byte) (i[j] >> 16);
      b[4 * (j - off) + 3 + pos] = (byte) (i[j] >> 24);
    }
  }

  public static byte[] longArrToByteArr(long[] l) {
    return longArrToByteArr(l, 0, l.length);
  }

  public static byte[] longArrToByteArr(long[] l, int off, int length) {
    byte[] b = new byte[8 * length];
    for (int j = off; j < (off + length); j++) {
      for (int i = 0; i < 8; i++) {
        b[8 * (j - off) + i] = (byte) (l[j] >> (8 * i));
      }
    }
    return b;
  }

  public static void longArrToByteArr(byte[] b, int pos, long[] l, int off, int length) {
    for (int j = off; j < (off + length); j++) {
      for (int i = 0; i < 8; i++) {
        b[8 * (j - off) + i + pos] = (byte) (l[j] >> (8 * i));
      }
    }
  }

  public static byte[] booleanArrToByteArr(boolean[] b) {
    return booleanArrToByteArr(b, 0, b.length);
  }

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

  public static void booleanArrToByteArr(byte[] bytes, int pos, boolean[] b, int off, int length) {
    for (int i = 0; i < length; i++) {
      if (b[i]) {
        bytes[i + pos] = 0;
      } else {
        bytes[i + pos] = 1;
      }
    }
  }

  public static byte[] charArrToByteArr(char[] c) {
    return charArrToByteArr(c, 0, c.length);
  }

  public static byte[] charArrToByteArr(char[] c, int off, int len) {
    byte[] b = new byte[len * 2];
    for (int i = 0; i < len; i++) {
      b[2 * i + 1] = (byte) c[off + i];
      b[2 * i] = (byte) (c[off + i] >> 8);
    }
    return b;
  }

  public static void charArrToByteArr(byte[] b, int pos, char[] c, int off, int len) {
    for (int i = 0; i < len; i++) {
      b[2 * i + 1 + pos] = (byte) c[off + i];
      b[2 * i + pos] = (byte) (c[off + i] >> 8);
    }
  }

  public static byte[] floatArrToByteArr(float[] f) {
    return floatArrToByteArr(f, 0, f.length);
  }

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

  public static void floatArrToByteArr(byte[] b, int pos, float[] f, int off, int len) {
    for (int j = off; j < (off + len); j++) {
      int i = Float.floatToIntBits(f[j]);
      b[4 * (j - off) + pos] = (byte) i;
      b[4 * (j - off) + 1 + pos] = (byte) (i >> 8);
      b[4 * (j - off) + 2 + pos] = (byte) (i >> 16);
      b[4 * (j - off) + 3 + pos] = (byte) (i >> 24);
    }
  }

  public static byte[] doubleArrToByteArr(double[] d) {
    return doubleArrToByteArr(d, 0, d.length);
  }

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

  public static void doubleArrToByteArr(byte[] b, int pos, double[] d, int off, int length) {
    for (int j = off; j < (off + length); j++) {
      long l = Double.doubleToLongBits(d[j]);
      for (int i = 0; i < 8; i++) {
        b[8 * (j - off) + i + pos] = (byte) (l >> (8 * i));
      }
    }
  }

  public static byte[] shortArrToByteArr(short[] s) {
    return shortArrToByteArr(s, 0, s.length);
  }

  public static byte[] shortArrToByteArr(short[] s, int off, int length) {
    byte[] y = new byte[2 * length];
    for (int j = off; j < (off + length); j++) {
      y[4 * (j - off)] = (byte) s[j];
      y[4 * (j - off) + 1] = (byte) (s[j] >> 8);
    }
    return y;
  }

  public static void shortArrToByteArr(byte[] b, int pos, short[] s, int off, int len) {
    for (int j = off; j < (off + len); j++) {
      b[4 * (j - off) + pos] = (byte) s[j];
      b[4 * (j - off) + 1 + pos] = (byte) (s[j] >> 8);
    }
  }

  public static byte[] uStringArrToByteArr(String[] s) {
    return uStringArrToByteArr(s, 0, s.length);
  }

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

  public static byte[] aStringArrToByteArr(String[] s) {
    return aStringArrToByteArr(s, 0, s.length);
  }

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

  public static int[] byteArrToIntArr(byte[] b) {
    return byteArrToIntArr(b, 0, b.length / 4);
  }

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

  public static void byteArrToIntArr(byte[] b, int off, int[] i, int pos, int length) {
    for (int j = 0; j < length; j++) {
      i[j + pos] = 0;
      for (int k = 3; k > 0; k--) {
        i[j + pos] = (i[j + pos] | (b[off + k + 4 * j] & INTFLAG)) << 8;
      }
      i[j + pos] = i[j + pos] | (b[off + 4 * j] & INTFLAG);
    }
  }

  public static long[] byteArrToLongArr(byte[] b) {
    return byteArrToLongArr(b, 0, b.length / 8);
  }

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

  public static void byteArrToLongArr(byte[] b, int off, long[] l, int pos, int length) {
    for (int i = 0; i < length; i++) {
      l[i + pos] = 0;
      for (int j = 0; j < 8; j++) {
        l[i + pos] = l[i + pos] | ((b[8 * i + j + off] & 0x000000ff) << (8 * j));
      }
    }
  }

  public static boolean[] byteArrToBooleanArr(byte[] b) {
    return byteArrToBooleanArr(b, 0, b.length);
  }

  public static boolean[] byteArrToBooleanArr(byte[] b, int off, int length) {
    boolean[] bool = new boolean[length];
    for (int i = 0; i < length; i++) {
      bool[i] = b[i + off] == 0;
    }
    return bool;
  }

  public static void byteArrToBooleanArr(byte[] b, int off, boolean[] bool, int pos, int length) {
    for (int i = 0; i < length; i++) {
      bool[i + pos] = b[i + off] == 0;
    }
  }

  public static char[] byteArrToCharArr(byte[] b) {
    return byteArrToCharArr(b, 0, b.length / 2);
  }

  public static char[] byteArrToCharArr(byte[] b, int off, int length) {
    char[] c = new char[length];
    for (int i = 0; i < (length); i++) {
      c[i] = (char) ((b[2 * i + off] << 8) | b[2 * i + 1 + off]);
    }
    return c;
  }

  public static void byteArrToCharArr(byte[] b, int off, char[] c, int pos, int length) {
    for (int i = 0; i < (length); i++) {
      c[i + pos] = (char) ((b[2 * i + off] << 8) | b[2 * i + 1 + off]);
    }
  }

  public static short[] byteArrToShortArr(byte[] b) {
    return byteArrToShortArr(b, 0, b.length / 2);
  }

  public static short[] byteArrToShortArr(byte[] b, int off, int length) {
    short[] z = new short[length];
    for (int i = 0; i < length; i++) {
      z[i] = (short) (((0 | (b[off + 1 + 2 * i] & SHORTFLAG)) << 8) | (b[off + 2 * i] & SHORTFLAG));
    }
    return z;
  }

  public static void byteArrToShortArr(byte[] b, int off, short[] s, int pos, int length) {
    for (int i = 0; i < length; i++) {
      s[i + pos] = (short) (((0 | (b[off + 1 + 2 * i] & SHORTFLAG)) << 8) | (b[off + 2 * i] & SHORTFLAG));
    }
  }

  public static float[] byteArrToFloatArr(byte[] b) {
    return byteArrToFloatArr(b, 0, b.length / 4);
  }

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

  public static void byteArrToDoubleArr(byte[] b, int off, double[] d, int pos, int length) {
    for (int i = 0; i < length; i++) {
      long l = 0;
      for (int j = 0; j < 8; j++) {
        l = l | ((long) (b[8 * i + j + off] & 0x00000000000000ff) << (8 * j));
      }
      d[pos + i] = Double.longBitsToDouble(l);
    }
  }

  public static String[] byteArrToUStringArr(byte[] b) {
    int off = 0;
    Vector<String> v = new Vector<String>();
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

  public static String[] byteArrToAStringArr(byte[] b) {
    int off = 0;
    Vector<String> v = new Vector<String>();
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

  public static void saveDoubleArr(DataOutputStream rf, double[] arr) throws IOException {
    rf.writeInt(arr.length);
    byte[] lArr = doubleArrToByteArr(arr);
    rf.write(lArr);
    rf.close();
  }

  public static void saveFloatArr(DataOutputStream rf, float[] arr) throws IOException {
    rf.writeInt(arr.length);
    byte[] lArr = floatArrToByteArr(arr);
    rf.write(lArr);
    rf.close();
  }

  public static double[] readDoubleArr(DataInputStream rf) throws IOException {
    int size = rf.readInt();
    byte[] b = new byte[8 * size];
    rf.read(b);
    return byteArrToDoubleArr(b);
  }

  public static float[] readFloatArr(DataInputStream rf) throws IOException {
    int size = rf.readInt();
    byte[] b = new byte[4 * size];
    rf.read(b);
    return byteArrToFloatArr(b);
  }

}
