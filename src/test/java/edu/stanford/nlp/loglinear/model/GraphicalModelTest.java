package edu.stanford.nlp.loglinear.model;

import com.pholser.junit.quickcheck.ForAll;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.junit.contrib.theories.Theories;
import org.junit.contrib.theories.Theory;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created on 8/11/15.
 * @author keenon
 * <p>
 * Quickchecks a couple of pieces of functionality, but mostly the serialization and deserialization (basically the only
 * non-trivial section).
 */
@RunWith(Theories.class)
public class GraphicalModelTest {
  @Theory
  public void testProtoModel(@ForAll(sampleSize = 50) @From(GraphicalModelGenerator.class) GraphicalModel graphicalModel) throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

    graphicalModel.writeToStream(byteArrayOutputStream);
    byteArrayOutputStream.close();

    byte[] bytes = byteArrayOutputStream.toByteArray();

    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);

    GraphicalModel recovered = GraphicalModel.readFromStream(byteArrayInputStream);

    assertTrue(graphicalModel.valueEquals(recovered, 1.0e-5));
  }

  @Theory
  public void testClone(@ForAll(sampleSize = 50) @From(GraphicalModelGenerator.class) GraphicalModel graphicalModel) throws IOException {
    GraphicalModel clone = graphicalModel.cloneModel();

    assertTrue(graphicalModel.valueEquals(clone, 1.0e-5));
  }

  @Theory
  public void testGetVariableSizes(@ForAll(sampleSize = 50) @From(GraphicalModelGenerator.class) GraphicalModel graphicalModel) throws IOException {
    int[] sizes = graphicalModel.getVariableSizes();

    for (GraphicalModel.Factor f : graphicalModel.factors) {
      for (int i = 0; i < f.neigborIndices.length; i++) {
        assertEquals(f.featuresTable.getDimensions()[i], sizes[f.neigborIndices[i]]);
      }
    }
  }

  public static class GraphicalModelGenerator extends Generator<GraphicalModel> {
    public GraphicalModelGenerator(Class<GraphicalModel> type) {
      super(type);
    }

    private Map<String, String> generateMetaData(SourceOfRandomness sourceOfRandomness, Map<String, String> metaData) {
      int numPairs = sourceOfRandomness.nextInt(9);
      for (int i = 0; i < numPairs; i++) {
        int key = sourceOfRandomness.nextInt();
        int value = sourceOfRandomness.nextInt();
        metaData.put("key:" + key, "value:" + value);
      }
      return metaData;
    }

    @Override
    public GraphicalModel generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus) {
      GraphicalModel model = new GraphicalModel();

      // Create the variables and factors

      int[] variableSizes = new int[20];
      for (int i = 0; i < 20; i++) {
        variableSizes[i] = sourceOfRandomness.nextInt(1, 5);
      }
      int numFactors = sourceOfRandomness.nextInt(12);
      for (int i = 0; i < numFactors; i++) {
        int[] neighbors = new int[sourceOfRandomness.nextInt(1, 3)];
        int[] neighborSizes = new int[neighbors.length];
        for (int j = 0; j < neighbors.length; j++) {
          neighbors[j] = sourceOfRandomness.nextInt(20);
          neighborSizes[j] = variableSizes[neighbors[j]];
        }

        ConcatVectorTable table = new ConcatVectorTable(neighborSizes);
        for (int[] assignment : table) {
          int numComponents = sourceOfRandomness.nextInt(7);
          // Generate a vector
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
          // set vec in table
          table.setAssignmentValue(assignment, () -> v);
        }

        model.addFactor(table, neighbors);
      }

      // Add metadata to the variables, factors, and model

      generateMetaData(sourceOfRandomness, model.getModelMetaDataByReference());
      for (int i = 0; i < 20; i++) {
        generateMetaData(sourceOfRandomness, model.getVariableMetaDataByReference(i));
      }
      for (GraphicalModel.Factor factor : model.factors) {
        generateMetaData(sourceOfRandomness, factor.getMetaDataByReference());
      }

      return model;
    }
  }
}