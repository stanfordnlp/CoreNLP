package edu.stanford.nlp.patterns.surface;

import java.text.DecimalFormat;

public class WeightedSurfacePattern extends SurfacePattern {

  private static final long serialVersionUID = 1L;

  double[] prevContextWts;
  double[] nextContextWts;
  String weightedPrevContextStr = "";
  String weightedNextContextStr = "";
  String weightedOriginalPrevStr = "";
  String weightedOriginalNextStr = "";

  DecimalFormat df = new DecimalFormat("#.##");

  public WeightedSurfacePattern(String[] prevContext, double[] prevContextWts,
      PatternToken token, String[] nextContext, double[] nextContextWts,
      String[] originalPrevStr, String[] originalNextStr) {
    super(prevContext, token, nextContext, originalPrevStr, originalNextStr);
    this.prevContextWts = prevContextWts;
    this.nextContextWts = nextContextWts;
    if (prevContextWts != null) {
      for (int i = 0; i < prevContext.length; i++) {
        weightedPrevContextStr += " " + prevContext[i] + ":"
            + df.format(prevContextWts[i]);
        weightedOriginalPrevStr += " " + originalPrevStr[i] + ":"
            + df.format(prevContextWts[i]);

      }
    } else
      weightedPrevContextStr = prevContextStr;

    if (nextContextWts != null) {
      for (int i = 0; i < nextContext.length; i++) {
        weightedNextContextStr += " " + nextContext[i] + ":"
            + df.format(nextContextWts[i]);
        weightedOriginalNextStr += " " + originalNextStr[i] + ":"
            + df.format(nextContextWts[i]);
      }
    } else
      weightedNextContextStr = nextContextStr;
  }

  @Override
  public String toString() {
    return (weightedPrevContextStr + " " + getToken().getTokenStr(null) + " " + weightedNextContextStr)
        .trim();
  }

  @Override
  public String toStringSimple() {
  
      return weightedOriginalPrevStr + " " + getToken().toStringToWrite()
          + " " + weightedOriginalNextStr;
  }
}
