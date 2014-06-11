package edu.stanford.nlp.patterns.surface;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.patterns.surface.ConstantsAndVariables;
import edu.stanford.nlp.patterns.surface.Data;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.Execution.Option;

public abstract class PhraseScorer {
  ConstantsAndVariables constVars;


  double OOVExternalFeatWt = 0.5;
  double OOVdictOdds = 1e-10;
  double OOVDomainNgramScore = 1e-10;
  double OOVGoogleNgramScore = 1e-10;
  
  @Option(name = "usePatternWeights")
  public boolean usePatternWeights = true;
 
  @Option(name = "wordNorm")
  Normalization wordNorm = Normalization.valueOf("LOG");
  
  public enum Normalization {
    NONE, SQRT, LOG
  };

  boolean forLearningPatterns;
  
  public PhraseScorer(ConstantsAndVariables constvar){
    this.constVars = constvar;
  }
  
  Counter<String> learnedScores = new ClassicCounter<String>();

  abstract Counter<String> scorePhrases(Map<String, List<CoreLabel>> sents, String label, TwoDimensionalCounter<String, SurfacePattern> terms,
      TwoDimensionalCounter<String, SurfacePattern> wordsPatExtracted, Counter<String> dictOddsWordWeights, Counter<SurfacePattern> allSelectedPatterns,
      Set<String> alreadyIdentifiedWords, boolean forLearningPatterns) throws IOException;
  
  Counter<String> getLearnedScores(){
    return learnedScores;
  }
  
  double getPatTFIDFScore(String word, Counter<SurfacePattern> patsThatExtractedThis, Counter<SurfacePattern> allSelectedPatterns) {
    double total = 0;

    for (Entry<SurfacePattern, Double> en2 : patsThatExtractedThis.entrySet()) {
      double weight = 1.0;
      if (usePatternWeights) {
        weight = allSelectedPatterns.getCount(en2.getKey());
        if (weight == 0)
          throw new RuntimeException("How is weight zero for " + en2.getKey());
      }
      total += weight;
    }
    assert Data.processedDataFreq.containsKey(word) : "How come the processed corpus freq doesnt have " + word;
    return total / Data.processedDataFreq.getCount(word);
  }

  public double getGoogleNgramScore(String g) {
    if (Data.googleNGram.containsKey(g)) {
      assert (Data.rawFreq.containsKey(g));
      return (1 + Data.rawFreq.getCount(g) * Math.sqrt(Data.ratioGoogleNgramFreqWithDataFreq)) / Data.googleNGram.getCount(g);
    }
    return 0;
  }

  public double getDomainNgramScore(String g) {
    assert Data.domainNGramRawFreq.containsKey(g) : " How come dowmin ngram raw freq does not contain " + g;
    if (Data.domainNGramRawFreq.getCount(g) == 0) {
      System.err.println("domain count 0 for " + g);
      return 0;
    }
    return ((1 + Data.rawFreq.getCount(g) * Math.sqrt(Data.ratioDomainNgramFreqWithDataFreq)) / Data.domainNGramRawFreq.getCount(g));
  }

  public double getDistSimWtScore(String ph) {
    Integer num = constVars.distSimClusters.get(ph);
    if (num != null && constVars.distSimWeights.containsKey(num)) {
      return constVars.distSimWeights.getCount(num);
    } else {
      String[] t = ph.split("\\s+");
      if (t.length < 2) {
        return OOVExternalFeatWt;
      }
      //double totalscore = 0;
      double minScore = Double.MAX_VALUE;
      for (String w : t) {
        double score = OOVExternalFeatWt;
        Integer numw = constVars.distSimClusters.get(w);
        if (numw != null && constVars.distSimWeights.containsKey(numw))
          score = constVars.distSimWeights.getCount(numw);
        if (score < minScore)
          minScore = score;
       // totalscore += score;
      }
      // return totalscore / ph.length();
      return minScore;
    }
  }

  public double getDictOddsScore(String word, Counter<String> dictOddsWordWeights) {
    double dscore;
    if (dictOddsWordWeights.containsKey(word)) {
      dscore = dictOddsWordWeights.getCount(word);
    } else
      dscore = getPhraseWeightFromWords(dictOddsWordWeights, word, OOVdictOdds);
    return dscore;
  }
  
  public double getPhraseWeightFromWords(Counter<String> weights, String ph,
      double defaultWt) {
    String[] t = ph.split("\\s+");
    if (t.length < 2) {
      if (weights.containsKey(ph))
        return weights.getCount(ph);
      else
        return defaultWt;
    }
    //double totalscore = 0;
    double minScore = Double.MAX_VALUE;
    for (String w : t) {
      double score = defaultWt;
      if (weights.containsKey(w))
        score = weights.getCount(w);
      if (score < minScore)
        minScore = score;
      //totalscore += score;
    }
    // return totalscore / ph.length();
    return minScore;
  }

}
