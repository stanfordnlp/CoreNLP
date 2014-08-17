package edu.stanford.nlp.sequences;

import edu.stanford.nlp.math.ArrayMath;


/**
 * @author grenager
 *         Date: Dec 14, 2004
 * @author nmramesh
 *         Date: May 12, 2010
 */
public class FactoredSequenceModel implements SequenceModel {

  // todo: The current version has variables for a 2 model version and arrays for an n-model version.  Unify.
  private SequenceModel model1;
  private SequenceModel model2;
  private double model1Wt = 1.0;
  private double model2Wt = 1.0;

  private SequenceModel[] models = null;
  private double[] wts = null;

  /** {@inheritDoc} */
  @Override
  public double[] scoresOf(int[] sequence, int pos) {
    if(models != null){
      double[] dist = ArrayMath.multiply(models[0].scoresOf(sequence, pos),wts[0]);
      for(int i = 1; i < models.length; i++){
        double[] dist_i = models[i].scoresOf(sequence, pos);
        ArrayMath.addMultInPlace(dist,dist_i,wts[i]);
      }
      return dist;
    }

    double[] dist1 = model1.scoresOf(sequence, pos);
    double[] dist2 = model2.scoresOf(sequence, pos);

    double[] dist = new double[dist1.length];
    for(int i = 0; i < dist1.length; i++)
      dist[i] = model1Wt*dist1[i] + model2Wt*dist2[i];

    return dist;
  }

  /** {@inheritDoc} */
  @Override
  public double scoreOf(int[] sequence, int pos) {
    return scoresOf(sequence, pos)[sequence[pos]];
  }

  /** {@inheritDoc} */
  @Override
  public double scoreOf(int[] sequence) {
    if(models != null){
      double score = 0;
      for(int i = 0; i < models.length; i++)
        score+= wts[i]*models[i].scoreOf(sequence);
      return score;
    }
    //return model1.scoreOf(sequence);
    return model1Wt*model1.scoreOf(sequence) + model2Wt*model2.scoreOf(sequence);
  }

  /** {@inheritDoc} */
  @Override
  public int length() {
    if(models != null)
      return models[0].length();
    return model1.length();
  }

  /** {@inheritDoc} */
  @Override
  public int leftWindow() {
    if(models != null)
      return models[0].leftWindow();
    return model1.leftWindow();
  }

  /** {@inheritDoc} */
  @Override
  public int rightWindow() {
    if(models != null)
      return models[0].rightWindow();
    return model1.rightWindow();
  }

  /** {@inheritDoc} */
  @Override
  public int[] getPossibleValues(int position) {
    if(models != null)
      return models[0].getPossibleValues(position);
    return model1.getPossibleValues(position);
  }

  /**
   * using this constructor results in a weighted addition of the two models' scores.
   * @param model1
   * @param model2
   * @param wt1 weight of model1
   * @param wt2 weight of model2
   */
  public FactoredSequenceModel(SequenceModel model1, SequenceModel model2, double wt1, double wt2){
    this(model1,model2);
    this.model1Wt = wt1;
    this.model2Wt = wt2;
  }

  public FactoredSequenceModel(SequenceModel model1, SequenceModel model2) {
    //if (model1.leftWindow() != model2.leftWindow()) throw new RuntimeException("Two models must have same window size");
    if (model1.getPossibleValues(0).length != model2.getPossibleValues(0).length) throw new RuntimeException("Two models must have the same number of classes");
    if (model1.length() != model2.length()) throw new RuntimeException("Two models must have the same sequence length");
    this.model1 = model1;
    this.model2 = model2;
  }

  public FactoredSequenceModel(SequenceModel[] models, double[] weights){
    this.models = models;
    this.wts = weights;
    /*
  for(int i = 1; i < models.length; i++){
    if (models[0].getPossibleValues(0).length != models[i].getPossibleValues(0).length) throw new RuntimeException("All models must have the same number of classes");
    if(models[0].length() != models[i].length())
      throw new RuntimeException("All models must have the same sequence length");

    }
    */
  }

}
