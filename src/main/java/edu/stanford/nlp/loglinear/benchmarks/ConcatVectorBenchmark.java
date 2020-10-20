package edu.stanford.nlp.loglinear.benchmarks;

import edu.stanford.nlp.loglinear.model.ConcatVector;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

public class ConcatVectorBenchmark {
  public static void main(String[] args) throws IOException, ClassNotFoundException {
    long randomSeed = 10101L;

    // Create the templates we'll use for our truly random dense vectors benchmarks
    ConcatVectorConstructionRecord[] randomSizedRecords = new ConcatVectorConstructionRecord[100000];
    Random r = new Random(randomSeed);
    for (int i = 0; i < randomSizedRecords.length; i++) {
      randomSizedRecords[i] = new ConcatVectorConstructionRecord(r);
    }

    // Create the templates for the more realistic same-sized records
    int[] sizes = ConcatVectorConstructionRecord.getRandomSizes(r);
    ConcatVectorConstructionRecord[] sameSizedRecords = new ConcatVectorConstructionRecord[100000];
    for (int i = 0; i < sameSizedRecords.length; i++) {
      sameSizedRecords[i] = new ConcatVectorConstructionRecord(r, sizes);
    }

    // Create template for clone action
    ConcatVectorConstructionRecord toClone = new ConcatVectorConstructionRecord(r);

    // Warmup the JIT compiler
    System.out.println("Warming up");
    for (int i = 0; i < 10; i++) {
      System.out.println(i);
      System.out.println("Serialize");
      protoSerializationBenchmark(randomSizedRecords);
    }
    for (int i = 0; i < 10; i++) {
      System.out.println(i);
      System.out.println("Clone");
      cloneBenchmark(toClone.create());
    }
    for (int i = 0; i < 100; i++) {
      System.out.println(i);
      System.out.println("Construction");
      constructionBenchmark(randomSizedRecords);
    }
    for (int i = 0; i < 100; i++) {
      System.out.println(i);
      System.out.println("Inner Product");
      dotProductBenchmark(sameSizedRecords);
    }
    for (int i = 0; i < 100; i++) {
      System.out.println(i);
      System.out.println("Addition");
      addBenchmark(sameSizedRecords);
    }
    System.out.println("Done warmup");

    // Actual benchmarking
    long cloneRuntime = 0;
    long constructionRuntime = 0;
    long dotProductRuntime = 0;
    long addRuntime = 0;
    long protoSerializeRuntime = 0;
    long protoSerializeSize = 0;

    for (int i = 0; i < 10; i++) {
      System.out.println(i);
      System.out.println("Serialize");
      SerializationReport sr = protoSerializationBenchmark(randomSizedRecords);
      protoSerializeRuntime += sr.time;
      if (protoSerializeSize == 0) {
        protoSerializeSize = sr.size;
      }
    }
    for (int i = 0; i < 10; i++) {
      System.out.println(i);
      System.out.println("Clone");
      cloneRuntime += cloneBenchmark(toClone.create());
    }
    for (int i = 0; i < 100; i++) {
      System.out.println(i);
      System.out.println("Construction");
      constructionRuntime += constructionBenchmark(randomSizedRecords);
    }
    for (int i = 0; i < 100; i++) {
      System.out.println(i);
      System.out.println("Inner Product");
      dotProductRuntime += dotProductBenchmark(sameSizedRecords);
    }
    for (int i = 0; i < 100; i++) {
      System.out.println(i);
      System.out.println("Addition");
      addRuntime += addBenchmark(sameSizedRecords);
    }

    System.out.println("Clone Runtime: " + cloneRuntime);
    System.out.println("Construction Runtime: " + constructionRuntime);
    System.out.println("Dot Product Runtimes: " + dotProductRuntime);
    System.out.println("Add Runtimes: " + addRuntime);
    System.out.println("Proto Serialize Runtimes: " + protoSerializeRuntime);
    System.out.println("Proto Serialize Size: " + protoSerializeSize);
  }

  static long cloneBenchmark(ConcatVector vector) {
    long before = System.currentTimeMillis();
    for (int i = 0; i < 10000000; i++) {
      vector.deepClone();
    }
    return System.currentTimeMillis() - before;
  }

  static ConcatVector[] makeVectors(ConcatVectorConstructionRecord[] records) {
    ConcatVector[] vectors = new ConcatVector[records.length];
    for (int i = 0; i < records.length; i++) {
      vectors[i] = records[i].create();
    }
    return vectors;
  }

  static long addBenchmark(ConcatVectorConstructionRecord[] records) {
    ConcatVector[] vectors = makeVectors(records);
    long before = System.currentTimeMillis();
    for (int i = 1; i < vectors.length; i++) {
      vectors[0].addVectorInPlace(vectors[i], 1.0f);
    }
    return System.currentTimeMillis() - before;
  }

  static long dotProductBenchmark(ConcatVectorConstructionRecord[] records) {
    ConcatVector[] vectors = makeVectors(records);
    long before = System.currentTimeMillis();
    for (int i = 0; i < vectors.length; i++) {
      vectors[0].dotProduct(vectors[i]);
    }
    return System.currentTimeMillis() - before;
  }

  static long constructionBenchmark(ConcatVectorConstructionRecord[] records) {

    // Then run the ConcatVector parts

    long before = System.currentTimeMillis();
    for (int i = 0; i < records.length; i++) {
      ConcatVector v = records[i].create();
    }

    // Report the union

    return System.currentTimeMillis() - before;
  }

  public static class ConcatVectorConstructionRecord {
    int[] componentSizes;
    double[][] densePieces;
    int[] sparseOffsets;
    double[] sparseValues;

    public static int[] getRandomSizes(Random r) {
      int length = r.nextInt(10);
      int[] sizes = new int[length];
      for (int i = 0; i < length; i++) {
        boolean sparse = r.nextBoolean();
        if (sparse) {
          sizes[i] = -1;
        } else {
          sizes[i] = r.nextInt(100);
        }
      }
      return sizes;
    }

    public ConcatVectorConstructionRecord(Random r) {
      this(r, getRandomSizes(r));
    }

    // Generates a new multivector construction record
    public ConcatVectorConstructionRecord(Random r, int[] sizes) {
      int length = sizes.length;
      componentSizes = sizes;
      densePieces = new double[length][];
      sparseOffsets = new int[length];
      sparseValues = new double[length];

      for (int i = 0; i < length; i++) {
        boolean sparse = componentSizes[i] == -1;
        if (sparse) {
          sparseOffsets[i] = r.nextInt(100);
          sparseValues[i] = r.nextFloat();
        } else {
          densePieces[i] = new double[componentSizes[i]];
          for (int j = 0; j < densePieces[i].length; j++) {
            densePieces[i][j] = r.nextFloat();
          }
        }
      }
    }

    // Creates the multivector
    public ConcatVector create() {
      ConcatVector mv = new ConcatVector(componentSizes.length);
      for (int i = 0; i < componentSizes.length; i++) {
        if (componentSizes[i] == -1) {
          mv.setSparseComponent(i, sparseOffsets[i], sparseValues[i]);
        } else {
          mv.setDenseComponent(i, densePieces[i]);
        }
      }
      return mv;
    }
  }

  static class SerializationReport {
    public long time;
    public int size;
  }

  static SerializationReport protoSerializationBenchmark(ConcatVectorConstructionRecord[] records) throws IOException, ClassNotFoundException {
    ConcatVector[] vectors = makeVectors(records);
    ByteArrayOutputStream bArr = new ByteArrayOutputStream();

    long before = System.currentTimeMillis();
    for (int i = 0; i < vectors.length; i++) {
      vectors[i].writeToStream(bArr);
    }
    bArr.close();
    byte[] bytes = bArr.toByteArray();

    ByteArrayInputStream bArrIn = new ByteArrayInputStream(bytes);
    for (int i = 0; i < vectors.length; i++) {
      ConcatVector.readFromStream(bArrIn);
    }
    SerializationReport sr = new SerializationReport();
    sr.time = System.currentTimeMillis() - before;
    sr.size = bytes.length;

    return sr;
  }
}