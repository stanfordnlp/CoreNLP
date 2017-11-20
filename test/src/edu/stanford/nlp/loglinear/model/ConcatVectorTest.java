package edu.stanford.nlp.loglinear.model;

import com.pholser.junit.quickcheck.ForAll;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.InRange;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.junit.contrib.theories.Theories;
import org.junit.contrib.theories.Theory;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;
import java.util.function.DoubleUnaryOperator;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

@RunWith(Theories.class)
public class ConcatVectorTest {
  @Theory
  public void testNewEmptyClone(@ForAll(sampleSize = 50) @From(DenseTestVectorGenerator.class) DenseTestVector d1) {
    ConcatVector empty = new ConcatVector(d1.vector.getNumberOfComponents());
    ConcatVector emptyClone = d1.vector.newEmptyClone();

    assertTrue(empty.valueEquals(emptyClone, 1.0e-5));
  }

  @Theory
  public void testResizeOnSetComponent(@ForAll(sampleSize = 50) @From(DenseTestVectorGenerator.class) DenseTestVector d1) {
    d1.vector.setSparseComponent(d1.values.length, 1, 0.0);
    d1.vector.setDenseComponent(d1.values.length + 1, new double[]{0.0});
  }

  @Theory
  public void testCopyOnWrite(@ForAll(sampleSize = 50) @From(DenseTestVectorGenerator.class) DenseTestVector d1) {
    ConcatVector v2 = d1.vector.deepClone();
    v2.addVectorInPlace(v2, 1.0);

    for (int i = 0; i < d1.values.length; i++) {
      for (int j = 0; j < d1.values[i].length; j++) {
        assertEquals(d1.values[i][j], d1.vector.getValueAt(i, j), 5.0e-4);
        assertEquals(d1.values[i][j] * 2, v2.getValueAt(i, j), 5.0e-4);
      }
    }
  }

  @Theory
  public void testAppendDenseComponent(@ForAll(sampleSize = 10) double[] vector1,
                                       @ForAll(sampleSize = 10) double[] vector2) throws Exception {
    ConcatVector v1 = new ConcatVector(1);
    ConcatVector v2 = new ConcatVector(1);

    v1.setDenseComponent(0, vector1);
    v2.setDenseComponent(0, vector2);

    double sum = 0.0f;
    for (int i = 0; i < Math.min(vector1.length, vector2.length); i++) {
      sum += vector1[i] * vector2[i];
    }

    assertEquals(sum, v1.dotProduct(v2), 5.0e-4);
  }

  @Theory
  public void testAppendSparseComponent(@ForAll(sampleSize = 10) @InRange(minInt = 0, maxInt = 10000) int sparse1,
                                        @ForAll(sampleSize = 10) double sparse1Val,
                                        @ForAll(sampleSize = 10) @InRange(minInt = 0, maxInt = 10000) int sparse2,
                                        @ForAll(sampleSize = 10) double sparse2Val) throws Exception {
    ConcatVector v1 = new ConcatVector(1);
    ConcatVector v2 = new ConcatVector(1);

    v1.setSparseComponent(0, sparse1, sparse1Val);
    v2.setSparseComponent(0, sparse2, sparse2Val);

    if (sparse1 == sparse2) {
      assertEquals(sparse1Val * sparse2Val, v1.dotProduct(v2), 5.0e-4);
    } else {
      assertEquals(0.0, v1.dotProduct(v2), 5.0e-4);
    }
  }

  @Theory
  public void testGetSparseIndex(@ForAll(sampleSize = 10) @InRange(minInt = 0, maxInt = 10000) int sparse1,
                                 @ForAll(sampleSize = 10) double sparse1Val,
                                 @ForAll(sampleSize = 10) @InRange(minInt = 0, maxInt = 10000) int sparse2,
                                 @ForAll(sampleSize = 10) double sparse2Val) throws Exception {
    ConcatVector v1 = new ConcatVector(2);
    ConcatVector v2 = new ConcatVector(2);

    v1.setSparseComponent(0, sparse1, sparse1Val);
    v1.setSparseComponent(1, sparse2, sparse1Val);
    v2.setSparseComponent(0, sparse2, sparse2Val);
    v2.setSparseComponent(1, sparse1, sparse2Val);

    assertEquals(sparse1, v1.getSparseIndex(0));
    assertEquals(sparse2, v1.getSparseIndex(1));
    assertEquals(sparse2, v2.getSparseIndex(0));
    assertEquals(sparse1, v2.getSparseIndex(1));
  }

  @Theory
  public void testInnerProduct(@ForAll(sampleSize = 50) @From(DenseTestVectorGenerator.class) DenseTestVector d1, @ForAll(sampleSize = 50) @From(DenseTestVectorGenerator.class) DenseTestVector d2) throws Exception {
    assertEquals(d1.trueInnerProduct(d2) + d2.trueInnerProduct(d2), d1.vector.dotProduct(d2.vector) + d2.vector.dotProduct(d2.vector), 5.0e-4);
  }

  @Theory
  public void testDeepClone(@ForAll(sampleSize = 50) @From(DenseTestVectorGenerator.class) DenseTestVector d1, @ForAll(sampleSize = 50) @From(DenseTestVectorGenerator.class) DenseTestVector d2) throws Exception {
    assertEquals(d1.vector.dotProduct(d2.vector), d1.vector.deepClone().dotProduct(d2.vector), 5.0e-4);
  }

  @Theory
  public void testDeepCloneGetValueAt(@ForAll(sampleSize = 250) @From(DenseTestVectorGenerator.class) DenseTestVector d1) throws Exception {
    ConcatVector mv = d1.vector;
    ConcatVector clone = d1.vector.deepClone();
    for (int i = 0; i < d1.values.length; i++) {
      for (int j = 0; j < d1.values[i].length; j++) {
        assertEquals(mv.getValueAt(i, j), clone.getValueAt(i, j), 1.0e-10);
      }
    }
  }

  @Theory
  public void testAddDenseToDense(@ForAll double[] dense1, @ForAll double[] dense2) {
    ConcatVector v1 = new ConcatVector(1);
    v1.setDenseComponent(0, dense1);
    ConcatVector v2 = new ConcatVector(1);
    v2.setDenseComponent(0, dense2);

    double expected = v1.dotProduct(v2) + 0.7f * (v2.dotProduct(v2));
    v1.addVectorInPlace(v2, 0.7f);
    assertEquals(expected, v1.dotProduct(v2), 5.0e-3);
  }

  @Theory
  public void testAddSparseToDense(@ForAll(sampleSize = 50) double[] dense1, @ForAll(sampleSize = 20) @InRange(minInt = 0, maxInt = 100) int sparseIndex, @ForAll(sampleSize = 10) double v) {
    ConcatVector v1 = new ConcatVector(1);
    v1.setDenseComponent(0, dense1);
    ConcatVector v2 = new ConcatVector(1);
    v2.setSparseComponent(0, sparseIndex, v);

    double expected = v1.dotProduct(v2) + 0.7f * (v2.dotProduct(v2));
    v1.addVectorInPlace(v2, 0.7f);
    assertEquals(expected, v1.dotProduct(v2), 5.0e-4);
  }

  @Theory
  public void testAddDenseToSparse(@ForAll(sampleSize = 50) double[] dense1, @ForAll(sampleSize = 20) @InRange(minInt = 0, maxInt = 100) int sparseIndex, @ForAll(sampleSize = 10) double v) {
    assumeTrue(sparseIndex >= 0);
    assumeTrue(sparseIndex <= 100);
    ConcatVector v1 = new ConcatVector(1);
    v1.setDenseComponent(0, dense1);
    ConcatVector v2 = new ConcatVector(1);
    v2.setSparseComponent(0, sparseIndex, v);

    double expected = v1.dotProduct(v2) + 0.7f * (v1.dotProduct(v1));
    v2.addVectorInPlace(v1, 0.7f);
    assertEquals(expected, v2.dotProduct(v1), 5.0e-4);
  }

  @Theory
  public void testAddSparseToSparse(@ForAll(sampleSize = 20) @InRange(minInt = 0, maxInt = 10) int sparseIndex1,
                                    @ForAll(sampleSize = 10) double val1,
                                    @ForAll(sampleSize = 20) @InRange(minInt = 0, maxInt = 10) int sparseIndex2,
                                    @ForAll(sampleSize = 10) double val2) {
    ConcatVector v1 = new ConcatVector(1);
    v1.setSparseComponent(0, sparseIndex1, val1);
    ConcatVector v2 = new ConcatVector(1);
    v2.setSparseComponent(0, sparseIndex2, val2);

    double expected = v1.dotProduct(v2) + 0.7f * (v2.dotProduct(v2));
    v1.addVectorInPlace(v2, 0.7f);
    assertEquals(expected, v1.dotProduct(v2), 5.0e-3);
  }

  @Theory
  public void testInnerProduct2(@ForAll(sampleSize = 10) @From(DenseTestVectorGenerator.class) DenseTestVector d1, @ForAll(sampleSize = 10) @From(DenseTestVectorGenerator.class) DenseTestVector d2, @ForAll(sampleSize = 10) @From(DenseTestVectorGenerator.class) DenseTestVector d3) throws Exception {
    // Test the invariant x^Tz + 0.7*y^Tz == (x+0.7*y)^Tz
    double d1d3 = d1.vector.dotProduct(d3.vector);
    assertEquals(d1.trueInnerProduct(d3), d1d3, 5.0e-4);
    double d2d3 = d2.vector.dotProduct(d3.vector);
    assertEquals(d2.trueInnerProduct(d3), d2d3, 5.0e-4);
    double expected = d1d3 + (0.7f * d2d3);
    assertEquals(d1.trueInnerProduct(d3) + (0.7 * d2.trueInnerProduct(d3)), expected, 5.0e-4);
  }

  @Theory
  public void testAddVector(@ForAll(sampleSize = 10) @From(DenseTestVectorGenerator.class) DenseTestVector d1, @ForAll(sampleSize = 10) @From(DenseTestVectorGenerator.class) DenseTestVector d2, @ForAll(sampleSize = 10) @From(DenseTestVectorGenerator.class) DenseTestVector d3) throws Exception {
    // Test the invariant x^Tz + 0.7*y^Tz == (x+0.7*y)^Tz
    double expected = d1.vector.dotProduct(d3.vector) + (0.7f * d2.vector.dotProduct(d3.vector));
    ConcatVector clone = d1.vector.deepClone();
    clone.addVectorInPlace(d2.vector, 0.7f);

    assertEquals(expected, clone.dotProduct(d3.vector), 5.0e-4);
  }

  @Theory
  public void testProtoVector(@ForAll(sampleSize = 50) @From(DenseTestVectorGenerator.class) DenseTestVector d1, @ForAll(sampleSize = 50) @From(DenseTestVectorGenerator.class) DenseTestVector d2) throws Exception {
    double expected = d1.vector.dotProduct(d2.vector);
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

    assert (d1.vector.getClass() == ConcatVector.class);

    d1.vector.writeToStream(byteArrayOutputStream);
    byteArrayOutputStream.close();

    byte[] bytes = byteArrayOutputStream.toByteArray();

    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);

    ConcatVector recovered = ConcatVector.readFromStream(byteArrayInputStream);

    assertEquals(expected, recovered.dotProduct(d2.vector), 5.0e-4);
  }

  @Theory
  public void testSizes(@ForAll(sampleSize = 50) @From(DenseTestVectorGenerator.class) DenseTestVector d1) {
    int size = d1.vector.getNumberOfComponents();
    assertEquals(d1.values.length, size);
  }

  @Theory
  public void testIsSparse(@ForAll(sampleSize = 50) @From(DenseTestVectorGenerator.class) DenseTestVector d1) {
    int size = d1.vector.getNumberOfComponents();
    assertEquals(d1.values.length, size);
    for (int i = 0; i < d1.values.length; i++) {
      if (d1.vector.isComponentSparse(i)) {
        for (int j = 0; j < d1.values[i].length; j++) {
          if (d1.vector.getSparseIndex(i) != j) assertEquals(0.0, d1.values[i][j], 1.0e-9);
        }
      }
    }
  }

  @Theory
  public void testRetrieveDense(@ForAll(sampleSize = 50) @From(DenseTestVectorGenerator.class) DenseTestVector d1) {
    int size = d1.vector.getNumberOfComponents();
    assertEquals(d1.values.length, size);
    for (int i = 0; i < d1.values.length; i++) {
      if (!d1.vector.isComponentSparse(i)) {
        assertArrayEquals(d1.values[i], d1.vector.getDenseComponent(i), 1.0e-9);
      }
    }
  }

  @Theory
  public void testGetValueAt(@ForAll(sampleSize = 50) @From(DenseTestVectorGenerator.class) DenseTestVector d1) {
    for (int i = 0; i < d1.values.length; i++) {
      for (int j = 0; j < d1.values[i].length; j++) {
        assertEquals(d1.values[i][j], d1.vector.getValueAt(i, j), 5.0e-4);
      }
    }
  }

  @Theory
  public void testElementwiseProduct(@ForAll(sampleSize = 50) @From(DenseTestVectorGenerator.class) DenseTestVector d1, @ForAll(sampleSize = 50) @From(DenseTestVectorGenerator.class) DenseTestVector d2) {
    for (int i = 0; i < d1.values.length; i++) {
      for (int j = 0; j < d1.values[i].length; j++) {
        assumeTrue(d1.values[i][j] == d1.vector.getValueAt(i, j));
      }
    }
    for (int i = 0; i < d2.values.length; i++) {
      for (int j = 0; j < d2.values[i].length; j++) {
        assumeTrue(d2.values[i][j] == d2.vector.getValueAt(i, j));
      }
    }

    ConcatVector clone = d1.vector.deepClone();
    clone.elementwiseProductInPlace(d2.vector);
    for (int i = 0; i < d1.values.length; i++) {
      for (int j = 0; j < d1.values[i].length; j++) {
        double val = 0.0f;
        if (i < d2.values.length) {
          if (j < d2.values[i].length) {
            val = d1.values[i][j] * d2.values[i][j];
          }
        }
        assertEquals(val, clone.getValueAt(i, j), 5.0e-4);
      }
    }
  }

  @Theory
  public void testElementwiseDenseToDense(@ForAll double[] dense1, @ForAll double[] dense2) {
    ConcatVector v1 = new ConcatVector(1);
    v1.setDenseComponent(0, dense1);
    ConcatVector v2 = new ConcatVector(1);
    v2.setDenseComponent(0, dense2);

    v1.elementwiseProductInPlace(v2);
    for (int i = 0; i < dense1.length; i++) {
      double expected = 0.0f;
      if (i < dense2.length) expected = dense1[i] * dense2[i];
      assertEquals(expected, v1.getValueAt(0, i), 5.0e-4);
    }
  }

  @Theory
  public void testElementwiseSparseToDense(@ForAll(sampleSize = 50) double[] dense1, @ForAll(sampleSize = 20) @InRange(minInt = 0, maxInt = 100) int sparseIndex, @ForAll(sampleSize = 10) double v) {
    ConcatVector v1 = new ConcatVector(1);
    v1.setDenseComponent(0, dense1);
    ConcatVector v2 = new ConcatVector(1);
    v2.setSparseComponent(0, sparseIndex, v);

    v1.elementwiseProductInPlace(v2);
    for (int i = 0; i < dense1.length; i++) {
      double expected = 0.0f;
      if (i == sparseIndex) expected = dense1[i] * v;
      assertEquals(expected, v1.getValueAt(0, i), 5.0e-4);
    }
  }

  @Theory
  public void testElementwiseDenseToSparse(@ForAll(sampleSize = 50) double[] dense1, @ForAll(sampleSize = 20) @InRange(minInt = 0, maxInt = 100) int sparseIndex, @ForAll(sampleSize = 10) double v) {
    assumeTrue(sparseIndex >= 0);
    assumeTrue(sparseIndex <= 100);
    ConcatVector v1 = new ConcatVector(1);
    v1.setDenseComponent(0, dense1);
    ConcatVector v2 = new ConcatVector(1);
    v2.setSparseComponent(0, sparseIndex, v);

    v2.elementwiseProductInPlace(v1);
    for (int i = 0; i < dense1.length; i++) {
      double expected = 0.0f;
      if (i == sparseIndex) expected = dense1[i] * v;
      assertEquals(expected, v2.getValueAt(0, i), 5.0e-4);
    }
  }

  @Theory
  public void testElementwiseSparseToSparse(@ForAll(sampleSize = 20) @InRange(minInt = 0, maxInt = 10) int sparseIndex1,
                                            @ForAll(sampleSize = 10) double val1,
                                            @ForAll(sampleSize = 20) @InRange(minInt = 0, maxInt = 10) int sparseIndex2,
                                            @ForAll(sampleSize = 10) double val2) {
    ConcatVector v1 = new ConcatVector(1);
    v1.setSparseComponent(0, sparseIndex1, val1);
    ConcatVector v2 = new ConcatVector(1);
    v2.setSparseComponent(0, sparseIndex2, val2);

    v1.elementwiseProductInPlace(v2);
    for (int i = 0; i < 10; i++) {
      double expected = 0.0f;
      if (i == sparseIndex1 && i == sparseIndex2) expected = val1 * val2;
      assertEquals(expected, v1.getValueAt(0, i), 5.0e-4);
    }
  }

  @Theory
  public void testMap(@ForAll(sampleSize = 50) @From(DenseTestVectorGenerator.class) DenseTestVector d1) {
    d1.vector.mapInPlace(x -> (x * Math.sqrt(x)));
    d1.map(x -> (x * Math.sqrt(x)));
    for (int i = 0; i < d1.values.length; i++) {
      for (int j = 0; j < d1.values[i].length; j++) {
        assertEquals(d1.values[i][j], d1.vector.getValueAt(i, j), 5.0e-4);
      }
    }
  }

  @Theory
  public void testValueEquals(@ForAll(sampleSize = 50) @From(DenseTestVectorGenerator.class) DenseTestVector d1) {
    ConcatVector clone = d1.vector.deepClone();
    assertTrue(clone.valueEquals(d1.vector, 1.0e-5));
    assertTrue(d1.vector.valueEquals(clone, 1.0e-5));
    assertTrue(d1.vector.valueEquals(d1.vector, 1.0e-5));
    assertTrue(clone.valueEquals(clone, 1.0e-5));

    Random r = new Random();

    int size = clone.getNumberOfComponents();
    if (size > 0) {
      clone.addVectorInPlace(d1.vector, 1.0);

      // If the clone is a 0 vector
      boolean isZero = true;
      for (double[] arr : d1.values) {
        for (double d : arr) if (d != 0) isZero = false;
      }
      if (isZero) {
        assertTrue(clone.valueEquals(d1.vector, 1.0e-5));
        assertTrue(d1.vector.valueEquals(clone, 1.0e-5));
      } else {
        assertFalse(clone.valueEquals(d1.vector, 1.0e-5));
        assertFalse(d1.vector.valueEquals(clone, 1.0e-5));
      }
      assertTrue(d1.vector.valueEquals(d1.vector, 1.0e-5));
      assertTrue(clone.valueEquals(clone, 1.0e-5));

      // refresh the clone

      clone = d1.vector.deepClone();

      int tinker = r.nextInt(size);
      d1.vector.setDenseComponent(tinker, new double[]{0, 0, 1});
      clone.setSparseComponent(tinker, 2, 1);

      assertTrue(d1.vector.valueEquals(clone, 1.0e-5));
      assertTrue(clone.valueEquals(d1.vector, 1.0e-5));
    }
  }

  /**
   * Created by keenon on 12/6/14.
   * <p>
   * DenseVector with obviously correct semantics for checking the MultiVector against.
   */
  public static class DenseTestVector {
    public double[][] values;
    public ConcatVector vector;

    public DenseTestVector(double[][] values, ConcatVector vector) {
      this.values = values;
      this.vector = vector;
    }

    public double trueInnerProduct(DenseTestVector testVector) {
      double sum = 0.0f;
      for (int i = 0; i < Math.min(values.length, testVector.values.length); i++) {
        for (int j = 0; j < Math.min(values[i].length, testVector.values[i].length); j++) {
          sum += values[i][j] * testVector.values[i][j];
        }
      }
      return sum;
    }

    public void map(DoubleUnaryOperator fn) {
      for (int i = 0; i < values.length; i++) {
        for (int j = 0; j < values[i].length; j++) {
          values[i][j] = fn.applyAsDouble(values[i][j]);
        }
      }
    }

    @Override
    public String toString() {
      return vector.toString();
    }
  }

  /**
   * Created by keenon on 12/6/14.
   * <p>
   * Handles generating the inputs for Quickcheck against the MultiVector
   */
  public static class DenseTestVectorGenerator extends Generator<DenseTestVector> {
    public DenseTestVectorGenerator(Class<DenseTestVector> type) {
      super(type);
    }

    static final int SPARSE_VECTOR_LENGTH = 5;

    public DenseTestVectorGenerator() {
      super(DenseTestVector.class);
    }

    @Override
    public DenseTestVector generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus) {
      int length = sourceOfRandomness.nextInt(10);
      double[][] trueValues = new double[length][];
      boolean[] sparse = new boolean[length];
      int[] sizes = new int[length];

      // Generate sizes in advance, so we can pass the clues on to the constructor for the multivector
      for (int i = 0; i < length; i++) {
        boolean isSparse = sourceOfRandomness.nextBoolean();
        sparse[i] = isSparse;
        if (isSparse) {
          sizes[i] = -1;
        } else {
          int componentLength = sourceOfRandomness.nextInt(SPARSE_VECTOR_LENGTH);
          sizes[i] = componentLength;
        }
      }

      ConcatVector mv = new ConcatVector(length);
      for (int i = 0; i < length; i++) {
        if (sparse[i]) {
          trueValues[i] = new double[SPARSE_VECTOR_LENGTH];
          int sparseIndex = sourceOfRandomness.nextInt(SPARSE_VECTOR_LENGTH);
          double sparseValue = sourceOfRandomness.nextDouble();
          trueValues[i][sparseIndex] = sparseValue;
          mv.setSparseComponent(i, sparseIndex, sparseValue);
        } else {
          trueValues[i] = new double[sizes[i]];
          // Ensure we have some null components in our generated vector
          if (sizes[i] > 0) {
            for (int j = 0; j < sizes[i]; j++) {
              trueValues[i][j] = sourceOfRandomness.nextDouble();
            }
            mv.setDenseComponent(i, trueValues[i]);
          }
        }
      }

      return new DenseTestVector(trueValues, mv);
    }
  }
}