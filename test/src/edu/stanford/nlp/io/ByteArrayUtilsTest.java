package edu.stanford.nlp.io;

import static org.junit.Assert.*;
import org.junit.Test;

import java.util.Random;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class ByteArrayUtilsTest {
  static final int TEST_LENGTH = 1000;

  @Test
  public void testShort() {
    Random random = new Random(1234);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();

    short[] values = new short[TEST_LENGTH];
    values[0] = 0;
    for (int i = 1; i < values.length; ++i) {
      values[i] = (short) random.nextInt(1 << 16);
    }

    for (int i = 0; i < values.length; ++i) {
      ByteArrayUtils.writeShort(bout, values[i]);
    }

    ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
    short[] output = new short[values.length];
    for (int i = 0; i < values.length; ++i) {
      output[i] = ByteArrayUtils.readShort(bin);
    }

    assertArrayEquals(values, output);
  }

  @Test
  public void testInt() {
    Random random = new Random(1234);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();

    int[] values = new int[TEST_LENGTH];
    values[0] = 0;
    for (int i = 1; i < values.length; ++i) {
      values[i] = random.nextInt();
    }

    for (int i = 0; i < values.length; ++i) {
      ByteArrayUtils.writeInt(bout, values[i]);
    }

    ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
    int[] output = new int[values.length];
    for (int i = 0; i < values.length; ++i) {
      output[i] = ByteArrayUtils.readInt(bin);
    }

    assertArrayEquals(values, output);
  }  

  @Test
  public void testLong() {
    Random random = new Random(1234);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();

    long[] values = new long[TEST_LENGTH];
    values[0] = 0;
    for (int i = 1; i < values.length; ++i) {
      values[i] = random.nextLong();
    }

    for (int i = 0; i < values.length; ++i) {
      ByteArrayUtils.writeLong(bout, values[i]);
    }

    ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
    long[] output = new long[values.length];
    for (int i = 0; i < values.length; ++i) {
      output[i] = ByteArrayUtils.readLong(bin);
    }

    assertArrayEquals(values, output);
  }
}
