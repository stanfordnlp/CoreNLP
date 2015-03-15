package edu.stanford.nlp.patterns.surface;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import edu.stanford.nlp.patterns.surface.GetPatternsFromDataMultiClass.PatternScoring;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.TwoDimensionalCounter;

public abstract class ScorePatterns<E> {
  
  ConstantsAndVariables<E> constVars;
  protected PatternScoring patternScoring;
  protected Properties props;
  
  abstract Counter<E> score() throws IOException, ClassNotFoundException;
  protected TwoDimensionalCounter<E, String> patternsandWords4Label = new TwoDimensionalCounter<E, String>();
  protected TwoDimensionalCounter<E, String> negPatternsandWords4Label = new TwoDimensionalCounter<E, String>();
  // protected TwoDimensionalCounter<SurfacePattern, String>
  // posnegPatternsandWords4Label = new TwoDimensionalCounter<SurfacePattern,
  // String>();
  protected TwoDimensionalCounter<E, String> unLabeledPatternsandWords4Label = new TwoDimensionalCounter<E, String>();
  //protected TwoDimensionalCounter<E, String> negandUnLabeledPatternsandWords4Label = new TwoDimensionalCounter<E, String>();
  //protected TwoDimensionalCounter<E, String> allPatternsandWords4Label = new TwoDimensionalCounter<E, String>();
  protected String label;
  protected Set<String> allCandidatePhrases;

  public ScorePatterns(
      ConstantsAndVariables constVars,
      PatternScoring patternScoring,
      String label,
      Set<String> allCandidatePhrases,
      TwoDimensionalCounter<E, String> patternsandWords4Label,
      TwoDimensionalCounter<E, String> negPatternsandWords4Label,
      TwoDimensionalCounter<E, String> unLabeledPatternsandWords4Label,
      Properties props) {
    this.constVars = constVars;
    this.patternScoring = patternScoring;
    this.label = label;
    this.allCandidatePhrases = allCandidatePhrases;
    this.patternsandWords4Label = patternsandWords4Label;
    this.negPatternsandWords4Label = negPatternsandWords4Label;
    this.unLabeledPatternsandWords4Label = unLabeledPatternsandWords4Label;

    this.props = props;
  }
  
  abstract public void setUp(Properties props);
}
