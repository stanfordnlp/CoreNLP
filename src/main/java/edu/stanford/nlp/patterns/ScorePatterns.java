package edu.stanford.nlp.patterns;

import java.io.IOException;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.patterns.ConstantsAndVariables;
import edu.stanford.nlp.patterns.GetPatternsFromDataMultiClass.PatternScoring;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.TwoDimensionalCounter;

public abstract class ScorePatterns<E> {

  ConstantsAndVariables constVars;
  protected PatternScoring patternScoring;
  protected Properties props;

  public abstract Counter<E> score() throws IOException, ClassNotFoundException;
  protected TwoDimensionalCounter<E, CandidatePhrase> patternsandWords4Label = new TwoDimensionalCounter<>();
  protected TwoDimensionalCounter<E, CandidatePhrase> negPatternsandWords4Label = new TwoDimensionalCounter<>();
  // protected TwoDimensionalCounter<SurfacePattern, String>
  // posnegPatternsandWords4Label = new TwoDimensionalCounter<SurfacePattern,
  // String>();
  protected TwoDimensionalCounter<E, CandidatePhrase> unLabeledPatternsandWords4Label = new TwoDimensionalCounter<>();
  //protected TwoDimensionalCounter<E, String> negandUnLabeledPatternsandWords4Label = new TwoDimensionalCounter<E, String>();
  //protected TwoDimensionalCounter<E, String> allPatternsandWords4Label = new TwoDimensionalCounter<E, String>();
  protected String label;
  protected Set<CandidatePhrase> allCandidatePhrases;

  public ScorePatterns(
      ConstantsAndVariables constVars,
      PatternScoring patternScoring,
      String label,
      Set<CandidatePhrase> allCandidatePhrases,
      TwoDimensionalCounter<E, CandidatePhrase> patternsandWords4Label,
      TwoDimensionalCounter<E, CandidatePhrase> negPatternsandWords4Label,
      TwoDimensionalCounter<E, CandidatePhrase> unLabeledPatternsandWords4Label,
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
