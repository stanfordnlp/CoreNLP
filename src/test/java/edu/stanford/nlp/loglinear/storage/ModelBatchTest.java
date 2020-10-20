package edu.stanford.nlp.loglinear.storage;

import com.pholser.junit.quickcheck.ForAll;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import edu.stanford.nlp.loglinear.model.GraphicalModel;
import edu.stanford.nlp.loglinear.model.GraphicalModelTest;
import org.junit.contrib.theories.Theories;
import org.junit.contrib.theories.Theory;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created on 10/17/15.
 * @author keenon
 * <p>
 * This just double checks that we can write and read these model batches without loss.
 */
@RunWith(Theories.class)
public class ModelBatchTest {
  @Theory
  public void testProtoBatch(@ForAll(sampleSize = 50) @From(BatchGenerator.class) ModelBatch batch) throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

    batch.writeToStream(byteArrayOutputStream);
    byteArrayOutputStream.close();

    byte[] bytes = byteArrayOutputStream.toByteArray();

    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);

    ModelBatch recovered = new ModelBatch(byteArrayInputStream);
    byteArrayInputStream.close();

    assertEquals(batch.size(), recovered.size());

    for (int i = 0; i < batch.size(); i++) {
      assertTrue(batch.get(i).valueEquals(recovered.get(i), 1.0e-5));
    }
  }

  @Theory
  public void testProtoBatchModifier(@ForAll(sampleSize = 50) @From(BatchGenerator.class) ModelBatch batch) throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

    batch.writeToStream(byteArrayOutputStream);
    byteArrayOutputStream.close();

    byte[] bytes = byteArrayOutputStream.toByteArray();

    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);

    ModelBatch recovered = new ModelBatch(byteArrayInputStream, (model) -> {
      model.getModelMetaDataByReference().put("testing", "true");
    });
    byteArrayInputStream.close();

    assertEquals(batch.size(), recovered.size());

    for (int i = 0; i < batch.size(); i++) {
      assertEquals("true", recovered.get(i).getModelMetaDataByReference().get("testing"));
    }
  }

  @Theory
  public void testProtoBatchWithoutFactors(@ForAll(sampleSize = 50) @From(BatchGenerator.class) ModelBatch batch) throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

    batch.writeToStreamWithoutFactors(byteArrayOutputStream);
    byteArrayOutputStream.close();

    byte[] bytes = byteArrayOutputStream.toByteArray();

    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);

    ModelBatch recovered = new ModelBatch(byteArrayInputStream);
    byteArrayInputStream.close();

    assertEquals(batch.size(), recovered.size());

    for (int i = 0; i < batch.size(); i++) {
      assertEquals(0, recovered.get(i).factors.size());
      assertTrue(batch.get(i).getModelMetaDataByReference().equals(recovered.get(i).getModelMetaDataByReference()));
      for (int j = 0; j < batch.get(i).getVariableSizes().length; j++) {
        assertTrue(batch.get(i).getVariableMetaDataByReference(j).equals(recovered.get(i).getVariableMetaDataByReference(j)));
      }
    }
  }

  public static class BatchGenerator extends Generator<ModelBatch> {
    GraphicalModelTest.GraphicalModelGenerator modelGenerator = new GraphicalModelTest.GraphicalModelGenerator(GraphicalModel.class);

    public BatchGenerator(Class<ModelBatch> type) {
      super(type);
    }

    @Override
    public ModelBatch generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus) {
      int length = sourceOfRandomness.nextInt(0, 50);
      ModelBatch batch = new ModelBatch();
      for (int i = 0; i < length; i++) {
        batch.add(modelGenerator.generate(sourceOfRandomness, generationStatus));
      }
      return batch;
    }
  }
}