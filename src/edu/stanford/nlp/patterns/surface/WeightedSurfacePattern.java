package edu.stanford.nlp.patterns.surface;

public class WeightedSurfacePattern extends SurfacePattern {


  private static final long serialVersionUID = 1L;

  double[] prevContextWts;
  double[] nextContextWts;
  
  public WeightedSurfacePattern(String[] prevContext, double[] prevContextWts, PatternToken token,
      String[] nextContext, double[] nextContextWts, String originalPrevStr, String originalNextStr) {
    super(prevContext, token, nextContext, originalPrevStr, originalNextStr);
    this.prevContextWts = prevContextWts;
    this.nextContextWts = nextContextWts;
  }

}
