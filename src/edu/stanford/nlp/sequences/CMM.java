package edu.stanford.nlp.sequences;

import java.util.*;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;


/**
 * @author Jenny Finkel
 */

public class CMM extends AbstractQueriableSequenceModel {

  private double[] weights;
  private double[][] factors;
  private List<Index<LabeledClique>> cliqueLabels;

  public CMM(CliqueDataset dataset) {
    super(dataset);
    initCliqueLabels();
  }

  public CMM(CliqueDataset dataset, double[] weights) {
    this(dataset);
    setParameters(weights);
  }

  public void setParameters(double[] weights) {
    this.weights = weights;
    factors = new double[dataset.numDatums()][];
  }

  private void initCliqueLabels() {
    cliqueLabels = new ArrayList<Index<LabeledClique>>(dataset.numDatums());
    for (int i = 0; i < dataset.numDatums(); i++) {
      cliqueLabels.add(new HashIndex<LabeledClique>(dataset.features[i].keySet()));
    }
  }

  public double logProbOf(int[] sequenceLabels, int[] positions) {
    throw new UnsupportedOperationException();
  }

  public double logConditionalProbOf(int position, LabeledClique labeledClique) {
    if (labeledClique.clique != dataset.metaInfo().getMaxClique()) {
      throw new RuntimeException("This method is only valid for the maximum clique!");
    }

    int index = cliqueLabels.get(position).indexOf(labeledClique);

    if (index < 0) { return Double.NEGATIVE_INFINITY; }


    if (factors[position] == null) {
      factors[position] = new double[cliqueLabels.get(position).size()];
      Arrays.fill(factors[position], 1.0);
    }

    if (factors[position][index] > 0.0) {

      List<LabeledClique> labels = dataset.getMaxCliqueConditionalLabels(position, labeledClique);

      int[] otherIndexes = new int[labels.size()];
      double[] toSum = new double[otherIndexes.length];
      int i = 0;
      for (LabeledClique lc : labels) {

        int otherIndex = cliqueLabels.get(position).indexOf(lc);
        otherIndexes[i] = otherIndex;

        Features labelInfo = dataset.features[position].get(lc);
        int[] features = labelInfo.features;

        double score = 0.0;
        for (int j = 0; j < features.length; j++) {
          score += labelInfo.value(j)*weights[features[j]];
        }

        factors[position][otherIndex] = score;
        toSum[i] = score;
        i++;
      }

      double Z = ArrayMath.logSum(toSum);

      for (int j = 0; j < otherIndexes.length; j++) {
        factors[position][otherIndexes[j]] -= Z;
      }
    }

    return factors[position][index];

  }

  public double logProbOf(int position, LabeledClique labeledClique) {
    throw new UnsupportedOperationException();
  }

  public List drawSample() {
    throw new RuntimeException();
  }
}
