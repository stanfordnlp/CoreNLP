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
  
  abstract Counter<Integer> score() throws IOException, InterruptedException, ExecutionException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException;
  protected TwoDimensionalCounter<Integer, String> patternsandWords4Label = new TwoDimensionalCounter<Integer, String>();
  protected TwoDimensionalCounter<Integer, String> negPatternsandWords4Label = new TwoDimensionalCounter<Integer, String>();
  // protected TwoDimensionalCounter<SurfacePattern, String>
  // posnegPatternsandWords4Label = new TwoDimensionalCounter<SurfacePattern,
  // String>();
  protected TwoDimensionalCounter<Integer, String> unLabeledPatternsandWords4Label = new TwoDimensionalCounter<Integer, String>();
  protected TwoDimensionalCounter<Integer, String> negandUnLabeledPatternsandWords4Label = new TwoDimensionalCounter<Integer, String>();
  protected TwoDimensionalCounter<Integer, String> allPatternsandWords4Label = new TwoDimensionalCounter<Integer, String>();
  protected String label;

  public ScorePatterns(
      ConstantsAndVariables constVars,
      PatternScoring patternScoring,
      String label,
      TwoDimensionalCounter<Integer, String> patternsandWords4Label,
      TwoDimensionalCounter<Integer, String> negPatternsandWords4Label,
      TwoDimensionalCounter<Integer, String> unLabeledPatternsandWords4Label,
      TwoDimensionalCounter<Integer, String> negandUnLabeledPatternsandWords4Label,
      TwoDimensionalCounter<Integer, String> allPatternsandWords4Label, Properties props) {
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
