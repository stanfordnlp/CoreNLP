package edu.stanford.nlp.loglinear.benchmarks;

import edu.stanford.nlp.loglinear.model.ConcatVector;
import edu.stanford.nlp.loglinear.model.ConcatVectorNamespace;
import edu.stanford.nlp.loglinear.model.GraphicalModel;

import java.util.List;
import java.util.Map;

/**
 * Created on 10/23/15.
 * @author keenon
 * <p>
 * This is a useful class for turning lists of tokens into a massively annotated PGM :)
 */
public class CoNLLFeaturizer {
  private static String getWordShape(String string) {
    if (string.toUpperCase().equals(string) && string.toLowerCase().equals(string)) return "no-case";
    if (string.toUpperCase().equals(string)) return "upper-case";
    if (string.toLowerCase().equals(string)) return "lower-case";
    if (string.length() > 1 && Character.isUpperCase(string.charAt(0)) && string.substring(1).toLowerCase().equals(string.substring(1)))
      return "capitalized";
    return "mixed-case";
  }

  public static void annotate(GraphicalModel model, List<String> tags, ConcatVectorNamespace namespace, Map<String, double[]> embeddings) {
    for (int i = 0; i < model.variableMetaData.size(); i++) {
      Map<String, String> metadata = model.getVariableMetaDataByReference(i);

      String token = metadata.get("TOKEN");
      String pos = metadata.get("POS");
      String chunk = metadata.get("CHUNK");

      Map<String, String> leftMetadata = null;
      if (i > 0) leftMetadata = model.getVariableMetaDataByReference(i - 1);
      String leftToken = (leftMetadata == null) ? "^" : leftMetadata.get("TOKEN");
      String leftPos = (leftMetadata == null) ? "^" : leftMetadata.get("POS");
      String leftChunk = (leftMetadata == null) ? "^" : leftMetadata.get("CHUNK");

      Map<String, String> rightMetadata = null;
      if (i < model.variableMetaData.size() - 1) rightMetadata = model.getVariableMetaDataByReference(i + 1);
      String rightToken = (rightMetadata == null) ? "$" : rightMetadata.get("TOKEN");
      String rightPos = (rightMetadata == null) ? "$" : rightMetadata.get("POS");
      String rightChunk = (rightMetadata == null) ? "$" : rightMetadata.get("CHUNK");

      // Add the unary factor

      GraphicalModel.Factor f = model.addFactor(new int[]{i}, new int[]{tags.size()}, (assignment) -> {

        // This is the anonymous function that generates a feature vector for each assignment to the unary
        // factor

        String tag = tags.get(assignment[0]);

        ConcatVector features = namespace.newVector();

        namespace.setDenseFeature(features, "BIAS" + tag, new double[]{1.0});
        namespace.setSparseFeature(features, "word" + tag, token, 1.0);
        if (embeddings != null && embeddings.containsKey(token)) {
          namespace.setDenseFeature(features, "embedding" + tag, embeddings.get(token));
        }
        if (token.length() > 1) {
          namespace.setSparseFeature(features, "prefix1" + tag, token.substring(0, 1), 1.0);
        }
        if (token.length() > 2) {
          namespace.setSparseFeature(features, "prefix2" + tag, token.substring(0, 2), 1.0);
        }
        if (token.length() > 3) {
          namespace.setSparseFeature(features, "prefix3" + tag, token.substring(0, 3), 1.0);
        }
        if (token.length() > 1) {
          namespace.setSparseFeature(features, "suffix1" + tag, token.substring(token.length() - 1), 1.0);
        }
        if (token.length() > 2) {
          namespace.setSparseFeature(features, "suffix2" + tag, token.substring(token.length() - 2), 1.0);
        }
        if (token.length() > 3) {
          namespace.setSparseFeature(features, "suffix3" + tag, token.substring(token.length() - 3), 1.0);
        }
        namespace.setSparseFeature(features, "shape" + tag, getWordShape(token), 1.0);

        namespace.setSparseFeature(features, "pos" + tag, pos, 1.0);
        namespace.setSparseFeature(features, "chunk" + tag, chunk, 1.0);

        return features;
      });

      assert (f.neigborIndices.length == 1);
      assert (f.neigborIndices[0] == i);

      // If this is not the last variable, add a binary factor

      if (i < model.variableMetaData.size() - 1) {
        GraphicalModel.Factor jf = model.addFactor(new int[]{i, i + 1}, new int[]{tags.size(), tags.size()}, (assignment) -> {

          // This is the anonymous function that generates a feature vector for every joint assignment to the
          // binary factor

          String thisTag = tags.get(assignment[0]);
          String nextTag = tags.get(assignment[1]);

          ConcatVector features = namespace.newVector();

          namespace.setDenseFeature(features, "BIAS" + thisTag + nextTag, new double[]{1.0});

          return features;
        });

        assert (jf.neigborIndices.length == 2);
        assert (jf.neigborIndices[0] == i);
        assert (jf.neigborIndices[1] == i + 1);
      }
    }
  }
}
