package edu.stanford.nlp.parser.lexparser;

import java.util.List;

import edu.stanford.nlp.ling.HasWord;


/** @author Dan Klein */
class TwinScorer implements Scorer {

  private Scorer scorer1;
  private Scorer scorer2;

  public double oScore(Edge edge) {
    return scorer1.oScore(edge) + scorer2.oScore(edge);
  }

  public double iScore(Edge edge) {
    return scorer1.iScore(edge) + scorer2.iScore(edge);
  }

  public boolean oPossible(Hook hook) {
    return scorer1.oPossible(hook) && scorer2.oPossible(hook);
  }

  public boolean iPossible(Hook hook) {
    return scorer1.iPossible(hook) && scorer2.iPossible(hook);
  }

  public boolean parse(List<? extends HasWord> words) {
    boolean b1 = scorer1.parse(words);
    boolean b2 = scorer2.parse(words);
    return (b1 && b2);
  }

  public TwinScorer(Scorer scorer1, Scorer scorer2) {
    this.scorer1 = scorer1;
    this.scorer2 = scorer2;
  }

}
