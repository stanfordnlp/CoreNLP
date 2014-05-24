package edu.stanford.nlp.patterns.surface;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import edu.stanford.nlp.patterns.surface.GetPatternsFromDataMultiClass.PatternScoring;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.TwoDimensionalCounter;

public abstract class ScorePatterns {
  
  ConstantsAndVariables constVars;
  protected PatternScoring patternScoring;
  protected Properties props;
  
  abstract Counter<SurfacePattern> score() throws IOException, InterruptedException, ExecutionException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException;
  protected TwoDimensionalCounter<SurfacePattern, String> patternsandWords4Label = new TwoDimensionalCounter<SurfacePattern, String>();
  protected TwoDimensionalCounter<SurfacePattern, String> negPatternsandWords4Label = new TwoDimensionalCounter<SurfacePattern, String>();
  // protected TwoDimensionalCounter<SurfacePattern, String>
  // posnegPatternsandWords4Label = new TwoDimensionalCounter<SurfacePattern,
  // String>();
  protected TwoDimensionalCounter<SurfacePattern, String> unLabeledPatternsandWords4Label = new TwoDimensionalCounter<SurfacePattern, String>();
  protected TwoDimensionalCounter<SurfacePattern, String> negandUnLabeledPatternsandWords4Label = new TwoDimensionalCounter<SurfacePattern, String>();
  protected TwoDimensionalCounter<SurfacePattern, String> allPatternsandWords4Label = new TwoDimensionalCounter<SurfacePattern, String>();
  protected String label;

  public ScorePatterns(
      ConstantsAndVariables constVars,
      PatternScoring patternScoring,
      String label,
      TwoDimensionalCounter<SurfacePattern, String> patternsandWords4Label,
      TwoDimensionalCounter<SurfacePattern, String> negPatternsandWords4Label,
      TwoDimensionalCounter<SurfacePattern, String> unLabeledPatternsandWords4Label,
      TwoDimensionalCounter<SurfacePattern, String> negandUnLabeledPatternsandWords4Label,
      TwoDimensionalCounter<SurfacePattern, String> allPatternsandWords4Label, Properties props) {
    this.constVars = constVars;
    this.patternScoring = patternScoring;
    this.label = label;
    this.patternsandWords4Label = patternsandWords4Label;
    this.negPatternsandWords4Label = negPatternsandWords4Label;
    this.unLabeledPatternsandWords4Label = unLabeledPatternsandWords4Label;
    this.negandUnLabeledPatternsandWords4Label = negandUnLabeledPatternsandWords4Label;
    this.allPatternsandWords4Label = allPatternsandWords4Label;
    this.props = props;
  }
  abstract public void setUp(Properties props);
}
