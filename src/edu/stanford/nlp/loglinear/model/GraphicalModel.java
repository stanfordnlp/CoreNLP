package edu.stanford.nlp.loglinear.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import edu.stanford.nlp.loglinear.model.proto.GraphicalModelProto;

/**
 * Created on 8/7/15.
 * @author keenon
 * <p>
 * A basic graphical model representation: Factors and Variables. This should be a fairly familiar interface to anybody
 * who's taken a basic PGM course (eg https://www.coursera.org/course/pgm). The key points:
 * - Stitching together feature factors
 * - Attaching metadata to everything, so that different sections of the program can communicate in lots of unplanned
 * ways. For now, the planned meta-data is a lot of routing and status information to do with LENSE.
 * <p>
 * This is really just the data structure, and inference lives elsewhere and must use public interfaces to access these
 * models. We just provide basic utility functions here, and barely do that, because we pass through directly to maps
 * wherever appropriate.
 */
public class GraphicalModel {
  public Map<String, String> modelMetaData = new HashMap<>();
  public List<Map<String, String>> variableMetaData = new ArrayList<>();
  public Set<Factor> factors = new HashSet<>();

  /**
   * A single factor in this graphical model. ConcatVectorTable can be reused multiple times if the same graph (or different
   * ones) and this is the glue object that tells a model where the factor lives, and what it is connected to.
   */
  public static class Factor {
    public ConcatVectorTable featuresTable;
    public int[] neigborIndices;
    public Map<String, String> metaData = new HashMap<>();

    /**
     * DO NOT USE. FOR SERIALIZATION ONLY.
     */
    private Factor() {
    }

    public Factor(ConcatVectorTable featuresTable, int[] neighborIndices) {
      this.featuresTable = featuresTable;
      this.neigborIndices = neighborIndices;
    }

    /**
     * @return the factor meta-data, by reference
     */
    public Map<String, String> getMetaDataByReference() {
      return metaData;
    }

    /**
     * Does a deep comparison, using equality with tolerance checks against the vector table of values.
     *
     * @param other     the factor to compare to
     * @param tolerance the tolerance to accept in differences
     * @return whether the two factors are within tolerance of one another
     */
    public boolean valueEquals(Factor other, double tolerance) {
      return Arrays.equals(neigborIndices, other.neigborIndices) &&
          metaData.equals(other.metaData) &&
          featuresTable.valueEquals(other.featuresTable, tolerance);
    }

    public GraphicalModelProto.Factor.Builder getProtoBuilder() {
      GraphicalModelProto.Factor.Builder builder = GraphicalModelProto.Factor.newBuilder();
      for (int neighbor : neigborIndices) {
        builder.addNeighbor(neighbor);
      }
      builder.setFeaturesTable(featuresTable.getProtoBuilder());
      builder.setMetaData(GraphicalModel.getProtoMetaDataBuilder(metaData));
      return builder;
    }

    public static Factor readFromProto(GraphicalModelProto.Factor proto) {
      Factor factor = new Factor();
      factor.featuresTable = ConcatVectorTable.readFromProto(proto.getFeaturesTable());
      factor.metaData = GraphicalModel.readMetaDataFromProto(proto.getMetaData());
      factor.neigborIndices = new int[proto.getNeighborCount()];
      for (int i = 0; i < factor.neigborIndices.length; i++) {
        factor.neigborIndices[i] = proto.getNeighbor(i);
      }
      return factor;
    }

    /**
     * Duplicates this factor.
     *
     * @return a copy of the factor
     */
    public Factor cloneFactor() {
      Factor clone = new Factor();
      clone.neigborIndices = neigborIndices.clone();
      clone.featuresTable = featuresTable.cloneTable();
      clone.metaData.putAll(metaData);
      return clone;
    }
  }

  /**
   * @return a reference to the model meta-data
   */
  public Map<String, String> getModelMetaDataByReference() {
    return modelMetaData;
  }

  /**
   * Gets the metadata for a variable. Creates blank metadata if does not exists, then returns that. Pass by reference.
   *
   * @param variableIndex the variable number, 0 indexed, to retrieve
   * @return the metadata map corresponding to that variable number
   */
  public synchronized Map<String, String> getVariableMetaDataByReference(int variableIndex) {
    while (variableIndex >= variableMetaData.size()) {
      variableMetaData.add(new HashMap<>());
    }
    return variableMetaData.get(variableIndex);
  }

  /**
   * This is the preferred way to add factors to a graphical model. Specify the neighbors, their dimensions, and a
   * function that maps from variable assignments to ConcatVector's of features, and this function will handle the
   * data flow of constructing and populating a factor matching those specifications.
   * <p>
   * IMPORTANT: assignmentFeaturizer must be REPEATABLE and NOT HAVE SIDE EFFECTS
   * This is because it is actually stored as a lazy closure until the full featurized vector is needed, and then it
   * is created, used, and discarded. It CAN BE CALLED MULTIPLE TIMES, and must always return the same value in order
   * for behavior of downstream systems to be defined.
   *
   * @param neighborIndices      the names of the variables, as indices
   * @param neighborDimensions   the sizes of the neighbor variables, corresponding to the order in neighborIndices
   * @param assignmentFeaturizer a function that maps from an assignment to the variables, represented as an array of
   *                             assignments in the same order as presented in neighborIndices, to a ConcatVector of
   *                             features for that assignment.
   * @return a reference to the created factor. This can be safely ignored, as the factor is already saved in the model
   */
  public Factor addFactor(int[] neighborIndices, int[] neighborDimensions, Function<int[], ConcatVector> assignmentFeaturizer) {
    ConcatVectorTable features = new ConcatVectorTable(neighborDimensions);
    for (int[] assignment : features) {
      features.setAssignmentValue(assignment, () -> assignmentFeaturizer.apply(assignment));
    }

    return addFactor(features, neighborIndices);
  }

  /**
   * Creates an instantiated factor in this graph, with neighborIndices representing the neighbor variables by integer
   * index.
   *
   * @param featureTable    the feature table to use to drive the value of the factor
   * @param neighborIndices the indices of the neighboring variables, in order
   * @return a reference to the created factor. This can be safely ignored, as the factor is already saved in the model
   */
  public Factor addFactor(ConcatVectorTable featureTable, int[] neighborIndices) {
    assert (featureTable.getDimensions().length == neighborIndices.length);
    Factor factor = new Factor(featureTable, neighborIndices);
    factors.add(factor);
    return factor;
  }

  /**
   * @return an array of integers, indicating variable sizes given by each of the factors in the model
   */
  public int[] getVariableSizes() {
    if (factors.size() == 0) {
      return new int[0];
    }

    int maxVar = 0;
    for (Factor f : factors) {
      for (int n : f.neigborIndices) {
        if (n > maxVar) maxVar = n;
      }
    }

    int[] sizes = new int[maxVar + 1];
    for (int i = 0; i < sizes.length; i++) {
      sizes[i] = -1;
    }

    for (Factor f : factors) {
      for (int i = 0; i < f.neigborIndices.length; i++) {
        sizes[f.neigborIndices[i]] = f.featuresTable.getDimensions()[i];
      }
    }

    return sizes;
  }

  /**
   * Writes the protobuf version of this graphical model to a stream. reversible with readFromStream().
   *
   * @param stream the output stream to write to
   * @throws IOException passed through from the stream
   */
  public void writeToStream(OutputStream stream) throws IOException {
    getProtoBuilder().build().writeDelimitedTo(stream);
  }

  /**
   * Static function to deserialize a graphical model from an input stream.
   *
   * @param stream the stream to read from, assuming protobuf encoding
   * @return a new graphical model
   * @throws IOException passed through from the stream
   */
  public static GraphicalModel readFromStream(InputStream stream) throws IOException {
    return readFromProto(GraphicalModelProto.GraphicalModel.parseDelimitedFrom(stream));
  }

  /**
   * @return the proto builder corresponding to this GraphicalModel
   */
  public GraphicalModelProto.GraphicalModel.Builder getProtoBuilder() {
    GraphicalModelProto.GraphicalModel.Builder builder = GraphicalModelProto.GraphicalModel.newBuilder();
    builder.setMetaData(getProtoMetaDataBuilder(modelMetaData));
    for (Map<String, String> metaData : variableMetaData) {
      builder.addVariableMetaData(getProtoMetaDataBuilder(metaData));
    }
    for (Factor factor : factors) {
      builder.addFactor(factor.getProtoBuilder());
    }
    return builder;
  }

  /**
   * Recreates an in-memory GraphicalModel from a proto serialization, recursively creating all the ConcatVectorTable's
   * and ConcatVector's in memory as well.
   *
   * @param proto the proto to read
   * @return an in-memory GraphicalModel
   */
  public static GraphicalModel readFromProto(GraphicalModelProto.GraphicalModel proto) {
    if (proto == null) return null;
    GraphicalModel model = new GraphicalModel();
    model.modelMetaData = readMetaDataFromProto(proto.getMetaData());
    model.variableMetaData = new ArrayList<>();
    for (int i = 0; i < proto.getVariableMetaDataCount(); i++) {
      model.variableMetaData.add(readMetaDataFromProto(proto.getVariableMetaData(i)));
    }
    for (int i = 0; i < proto.getFactorCount(); i++) {
      model.factors.add(Factor.readFromProto(proto.getFactor(i)));
    }
    return model;
  }

  /**
   * Check that two models are deeply value-equivalent, down to the concat vectors inside the factor tables, within
   * some tolerance. Mostly useful for testing.
   *
   * @param other     the graphical model to compare against.
   * @param tolerance the tolerance to accept when comparing concat vectors for value equality.
   * @return whether the two models are tolerance equivalent
   */
  public boolean valueEquals(GraphicalModel other, double tolerance) {
    if (!modelMetaData.equals(other.modelMetaData)) {
      return false;
    }
    if (!variableMetaData.equals(other.variableMetaData)) {
      return false;
    }
    // compare factor sets for equality
    Set<Factor> remaining = new HashSet<>();
    remaining.addAll(factors);
    for (Factor otherFactor : other.factors) {
      Factor match = null;
      for (Factor factor : remaining) {
        if (factor.valueEquals(otherFactor, tolerance)) {
          match = factor;
          break;
        }
      }
      if (match == null) return false;
      else remaining.remove(match);
    }
    return remaining.size() <= 0;
  }

  /**
   * Displays a list of factors, by neighbor.
   *
   * @return a formatted list of factors, by neighbor
   */
  @Override
  public String toString() {
    String s = "{";
    for (Factor f : factors) {
      s += "\n\t" + Arrays.toString(f.neigborIndices) + "@" + f;
    }
    s += "\n}";
    return s;
  }

  /**
   * The point here is to allow us to save a copy of the model with a current set of factors and metadata mappings,
   * which can come in super handy with gameplaying applications. The cloned model doesn't instantiate the feature
   * thunks inside factors, those are just taken over individually.
   *
   * @return a clone
   */
  public GraphicalModel cloneModel() {
    GraphicalModel clone = new GraphicalModel();
    clone.modelMetaData.putAll(modelMetaData);
    for (int i = 0; i < variableMetaData.size(); i++) {
      if (variableMetaData.get(i) != null) {
        clone.getVariableMetaDataByReference(i).putAll(variableMetaData.get(i));
      }
    }

    for (Factor f : factors) {
      clone.factors.add(f.cloneFactor());
    }

    return clone;
  }

  ////////////////////////////////////////////////////////////////////////////
  // PRIVATE IMPLEMENTATION
  ////////////////////////////////////////////////////////////////////////////

  private static GraphicalModelProto.MetaData.Builder getProtoMetaDataBuilder(Map<String, String> metaData) {
    GraphicalModelProto.MetaData.Builder builder = GraphicalModelProto.MetaData.newBuilder();
    for (Map.Entry<String, String> entry : metaData.entrySet()) {
      builder.addKey(entry.getKey());
      builder.addValue(entry.getValue());
    }
    return builder;
  }

  private static Map<String, String> readMetaDataFromProto(GraphicalModelProto.MetaData proto) {
    Map<String, String> metaData = new HashMap<>();
    for (int i = 0; i < proto.getKeyCount(); i++) {
      metaData.put(proto.getKey(i), proto.getValue(i));
    }
    return metaData;
  }
}
