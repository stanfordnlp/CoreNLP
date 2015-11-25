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
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created on 8/9/15.
 * @author keenon
 * <p>
 * This will attempt to quickcheck the ConcatVectorTable class. There isn't much functionality in there, so this is a short test
 * suite.
 */
@RunWith(Theories.class)
public class ConcatVectorTableTest {

  public static ConcatVectorTable convertArrayToVectorTable(ConcatVector[][][] factor3) {
    int[] neighborSizes = new int[]{
        factor3.length,
        factor3[0].length,
        factor3[0][0].length
    };
    ConcatVectorTable concatVectorTable = new ConcatVectorTable(neighborSizes);
    for (int i = 0; i < factor3.length; i++) {
      for (int j = 0; j < factor3[0].length; j++) {
        for (int k = 0; k < factor3[0][0].length; k++) {
          final int iF = i;
          final int jF = j;
          final int kF = k;
          concatVectorTable.setAssignmentValue(new int[]{i, j, k}, () -> factor3[iF][jF][kF]);
        }
      }
    }
    return concatVectorTable;
  }

  @Theory
  public void testCache(@ForAll(sampleSize = 50) @From(FeatureFactorGenerator.class) ConcatVector[][][] factor3,
                        @ForAll(sampleSize = 10) @InRange(minInt = 1, maxInt = 30) int numUses) throws IOException {
    int[] dimensions = new int[]{factor3.length, factor3[0].length, factor3[0][0].length};

    final int[][][] thunkHits = new int[dimensions[0]][dimensions[1]][dimensions[2]];

    ConcatVectorTable table = new ConcatVectorTable(dimensions);
    for (int i = 0; i < factor3.length; i++) {
      for (int j = 0; j < factor3[0].length; j++) {
        for (int k = 0; k < factor3[0][0].length; k++) {
          int[] assignment = new int[]{i, j, k};
          table.setAssignmentValue(assignment, () -> {
            thunkHits[assignment[0]][assignment[1]][assignment[2]] += 1;
            return factor3[assignment[0]][assignment[1]][assignment[2]];
          });
        }
      }
    }

    // Pre-cacheing
    for (int n = 0; n < numUses; n++) {
      for (int i = 0; i < factor3.length; i++) {
        for (int j = 0; j < factor3[0].length; j++) {
          for (int k = 0; k < factor3[0][0].length; k++) {
            int[] assignment = new int[]{i, j, k};
            table.getAssignmentValue(assignment).get();
          }
        }
      }
    }

    for (int i = 0; i < factor3.length; i++) {
      for (int j = 0; j < factor3[0].length; j++) {
        for (int k = 0; k < factor3[0][0].length; k++) {
          assertEquals(numUses, thunkHits[i][j][k]);
        }
      }
    }

    table.cacheVectors();

    // Cached
    for (int n = 0; n < numUses; n++) {
      for (int i = 0; i < factor3.length; i++) {
        for (int j = 0; j < factor3[0].length; j++) {
          for (int k = 0; k < factor3[0][0].length; k++) {
            int[] assignment = new int[]{i, j, k};
            table.getAssignmentValue(assignment).get();
          }
        }
      }
    }

    for (int i = 0; i < factor3.length; i++) {
      for (int j = 0; j < factor3[0].length; j++) {
        for (int k = 0; k < factor3[0][0].length; k++) {
          assertEquals(numUses + 1, thunkHits[i][j][k]);
        }
      }
    }

    table.releaseCache();

    // Post-cacheing
    for (int n = 0; n < numUses; n++) {
      for (int i = 0; i < factor3.length; i++) {
        for (int j = 0; j < factor3[0].length; j++) {
          for (int k = 0; k < factor3[0][0].length; k++) {
            int[] assignment = new int[]{i, j, k};
            table.getAssignmentValue(assignment).get();
          }
        }
      }
    }

    for (int i = 0; i < factor3.length; i++) {
      for (int j = 0; j < factor3[0].length; j++) {
        for (int k = 0; k < factor3[0][0].length; k++) {
          assertEquals((2 * numUses) + 1, thunkHits[i][j][k]);
        }
      }
    }
  }

  @Theory
  public void testProtoTable(@ForAll(sampleSize = 50) @From(FeatureFactorGenerator.class) ConcatVector[][][] factor3) throws IOException {
    ConcatVectorTable concatVectorTable = convertArrayToVectorTable(factor3);

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    concatVectorTable.writeToStream(byteArrayOutputStream);
    byteArrayOutputStream.close();

    byte[] bytes = byteArrayOutputStream.toByteArray();

    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);

    ConcatVectorTable recovered = ConcatVectorTable.readFromStream(byteArrayInputStream);
    for (int i = 0; i < factor3.length; i++) {
      for (int j = 0; j < factor3[0].length; j++) {
        for (int k = 0; k < factor3[0][0].length; k++) {
          assertTrue(factor3[i][j][k].valueEquals(recovered.getAssignmentValue(new int[]{i, j, k}).get(), 1.0e-5));
        }
      }
    }
    assertTrue(concatVectorTable.valueEquals(recovered, 1.0e-5));
  }

  @Theory
  public void testCloneTable(@ForAll(sampleSize = 50) @From(FeatureFactorGenerator.class) ConcatVector[][][] factor3) throws IOException {
    ConcatVectorTable concatVectorTable = convertArrayToVectorTable(factor3);

    ConcatVectorTable cloned = concatVectorTable.cloneTable();
    for (int i = 0; i < factor3.length; i++) {
      for (int j = 0; j < factor3[0].length; j++) {
        for (int k = 0; k < factor3[0][0].length; k++) {
          assertTrue(factor3[i][j][k].valueEquals(cloned.getAssignmentValue(new int[]{i, j, k}).get(), 1.0e-5));
        }
      }
    }
    assertTrue(concatVectorTable.valueEquals(cloned, 1.0e-5));
  }

  public static class FeatureFactorGenerator extends Generator<ConcatVector[][][]> {
    public FeatureFactorGenerator(Class<ConcatVector[][][]> type) {
      super(type);
    }

    @Override
    public ConcatVector[][][] generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus) {
      int l = sourceOfRandomness.nextInt(10) + 1;
      int m = sourceOfRandomness.nextInt(10) + 1;
      int n = sourceOfRandomness.nextInt(10) + 1;
      ConcatVector[][][] factor3 = new ConcatVector[l][m][n];
      for (int i = 0; i < factor3.length; i++) {
        for (int j = 0; j < factor3[0].length; j++) {
          for (int k = 0; k < factor3[0][0].length; k++) {
            int numComponents = sourceOfRandomness.nextInt(7);
            ConcatVector v = new ConcatVector(numComponents);
            for (int x = 0; x < numComponents; x++) {
              if (sourceOfRandomness.nextBoolean()) {
                v.setSparseComponent(x, sourceOfRandomness.nextInt(32), sourceOfRandomness.nextDouble());
              } else {
                double[] val = new double[sourceOfRandomness.nextInt(12)];
                for (int y = 0; y < val.length; y++) {
                  val[y] = sourceOfRandomness.nextDouble();
                }
                v.setDenseComponent(x, val);
              }
            }
            factor3[i][j][k] = v;
          }
        }
      }
      return factor3;
    }
  }
}