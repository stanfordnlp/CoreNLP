package edu.stanford.nlp.sequences;


/**
 * This abstract class is meant to hold the code shared by both
 * the CMM and the CRF.
 *
 * @author Jenny Finkel
 */
public abstract class AbstractQueriableSequenceModel implements QueriableSequenceModel {

  protected CliqueDataset dataset;

  public CliqueDataset dataset() { return dataset; } // should make defensive copy

  public AbstractQueriableSequenceModel (CliqueDataset dataset) {
    this.dataset = dataset;
  }
  
  public DatasetMetaInfo metaInfo() { return dataset.metaInfo(); }
  
  public int leftWindow() { return dataset.metaInfo().leftWindow(); }
  public int rightWindow() { return dataset.metaInfo().rightWindow(); }
  public int length() { return dataset.numDatums(); } // ?? include windows ??

  public int[] getPossibleValues(int position) {
    if (position < leftWindow() || position > leftWindow() + dataset.features.length) return new int[] {dataset.metaInfo().backgroundIndex()};

    return dataset.possibleLabels[position-leftWindow()];
  }  
  
  public double conditionalProbOf(int[] sequenceLabels, int[] positions, int[] conditionOnPositions) {
    return Math.exp(logConditionalProbOf(sequenceLabels, positions, conditionOnPositions));
  }
  
  public double probOf(int[] sequenceLabels, int[] positions) {
    return Math.exp(logProbOf(sequenceLabels, positions));
  }

  /**
   * positions should be sorted
   */
  public double probOf(int position, LabeledClique labeledClique) {
    return Math.exp(logProbOf(position, labeledClique));
  }

  /**
   * prob of position, conditioned on rest of clique
   */
  public double conditionalProbOf(int position, LabeledClique labeledClique) {
    return Math.exp(logConditionalProbOf(position, labeledClique));
  }

  public double logConditionalProbOf(int[] sequenceLabels, int[] positions, int[] conditionOnPositions) {
    double d = logProbOf(sequenceLabels, positions);

    int[] joint = new int[positions.length+conditionOnPositions.length];

    int j = 0, k = 0;
    int numRepeats = 0;
    for (int i = 0; i < joint.length - numRepeats; i++) {
      if (positions[j] == conditionOnPositions[k]) {
        joint[i] = positions[j];
        j++;
        k++;
        numRepeats++;
      } else if (positions[j] < conditionOnPositions[k]) {
        joint[i] = positions[j];
        j++;        
      } else {
        joint[i] = conditionOnPositions[k];
        k++;
      }
    }

    if (numRepeats > 0) {
      int[] tmp = new int[joint.length-numRepeats];
      System.arraycopy(joint, 0, tmp, 0, tmp.length);
      joint = tmp;
    }

    double n = logProbOf(sequenceLabels, joint);

    return n - d;
  }

  
  public double[] scoresOf(int[] sequence, int position) {
    LabeledClique lc = LabeledClique.valueOf(dataset.metaInfo().getMaxClique(), sequence, position);
    int[] labels = getPossibleValues(position);
    int origLabel = sequence[position];

    double[] scores = new double[labels.length];
    
    for (int i = 0; i < labels.length; i++) {
      sequence[position] = labels[i];
      scores[i] = scoreOf(sequence, position);
    }

    sequence[position] = origLabel;
    return scores;
  }

  public double scoreOf(int[] sequence, int position) {
    LabeledClique lc = LabeledClique.valueOf(dataset.metaInfo().getMaxClique(), sequence, position);
    return logConditionalProbOf(position-leftWindow(), lc);
  }

  public double scoreOf(int[] sequence) {
    double score = 0.0;
    for (int i = 0; i < dataset.numDatums(); i++) {
      score += scoreOf(sequence, i);
    }
    return score;
  }
  
}
