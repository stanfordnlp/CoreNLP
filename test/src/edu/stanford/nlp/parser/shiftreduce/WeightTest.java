package edu.stanford.nlp.parser.shiftreduce;

import static org.junit.Assert.*;
import org.junit.Test;

import java.util.Random;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class WeightTest {
  @Test
  public void testSize() {
    Weight w = new Weight();
    assertEquals(0, w.size());

    w.updateWeight(37, 0.5f);
    assertEquals(1, w.size());
    
    w.updateWeight(40, 0.25f);
    assertEquals(2, w.size());

    w.updateWeight(40, 0.25f);
    assertEquals(2, w.size());
  }

  @Test
  public void testMaxABS() {
    Weight w = new Weight();
    assertEquals(0, w.size());

    w.updateWeight(37, 0.5f);
    assertEquals(0.5f, w.maxAbs(), 0.0001f);
    
    w.updateWeight(40, -0.65f);
    assertEquals(0.65f, w.maxAbs(), 0.0001f);

    w.updateWeight(40, 0.4f);
    assertEquals(0.5f, w.maxAbs(), 0.0001f);
  }

  @Test
  public void testScore() {
    Weight w = new Weight();
    w.updateWeight(3, 0.5f);
    w.updateWeight(1, -0.25f);

    float[] scores = new float[5];
    w.score(scores);
    float[] expected = {0, -0.25f, 0, 0.5f, 0};
    assertArrayEquals(expected, scores, 0.0001f);

    w.score(scores);
    float[] expected2 = {0, -0.5f, 0, 1.0f, 0};
    assertArrayEquals(expected2, scores, 0.0001f);
  }

  @Test
  public void testCondense() {
    Weight w = new Weight();
    w.updateWeight(1, 0.5f);
    w.updateWeight(1, -0.5f);
    w.updateWeight(2, 0.25f);

    float[] scores = new float[5];
    w.score(scores);
    float[] expected = {0, 0, 0.25f, 0, 0};
    assertArrayEquals(expected, scores, 0.0001f);
    assertEquals(2, w.size());

    w.condense();
    assertEquals(1, w.size());
    scores[2] = 0.0f;
    w.score(scores);
    assertArrayEquals(expected, scores, 0.0001f);
  }

  @Test
  public void testAddScaled() {
    Weight w = new Weight();
    w.updateWeight(1, 0.5f);
    w.updateWeight(3, 0.25f);

    Weight w2 = new Weight();
    w2.updateWeight(2, 0.5f);
    w2.updateWeight(3, -0.125f);
    w2.updateWeight(4, 0.125f);

    w.addScaled(w2, 2.0f);
    float[] scores = new float[5];
    w.score(scores);
    float[] expected = {0, 0.5f, 1.0f, 0, 0.25f};
    assertArrayEquals(expected, scores, 0.0001f);
    assertEquals(4, w.size());

    w.condense();
    assertEquals(3, w.size());
    float[] scores2 = new float[5];
    w.score(scores2);
    assertArrayEquals(expected, scores2, 0.0001f);
  }

  @Test
  public void testRandomAdd() {
    Random random = new Random(1234);
    Weight w = new Weight();
    float sum = 0.0f;
    for (int i = 0; i < 1000000; ++i) {
      float next = random.nextFloat();
      sum = sum + next;
      w.updateWeight(0, next);
      assertEquals(sum, w.getScore(0), 0.0001f);
    }
    for (int i = 0; i < 2000000; ++i) {
      float next = random.nextFloat();
      sum = sum - next;
      w.updateWeight(0, -next);
      assertEquals(sum, w.getScore(0), 0.0001f);
    }
  }

  @Test
  public void testNaN() {
    // The proposed 48 bit version of the weights failed on this particular math problem
    Weight w = new Weight();
    w.updateWeight(232, -431.0f);

    Weight w2 = new Weight();
    w2.updateWeight(232, -521.0f);

    float[] scores = new float[233];

    w.score(scores);
    w2.score(scores);
    assertEquals(-952.0, scores[232], 0.0001f);
  }

  @Test
  public void testReadWrite() {
    Weight w = new Weight();
    w.updateWeight(232, -431.0f);
    w.updateWeight(200, -521.0f);
    w.updateWeight(3, 50.0f);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    w.writeBytes(bout);

    ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
    Weight w2 = Weight.readBytes(bin);
    assertEquals(3, w2.size());
    assertEquals(-431.0f, w2.getScore(232), 0.0001f);
    assertEquals(-521.0f, w2.getScore(200), 0.0001f);
    assertEquals(50.0f, w2.getScore(3), 0.0001f);
  }
}
