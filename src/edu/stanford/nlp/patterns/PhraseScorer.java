package edu.stanford.nlp.patterns;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;

import edu.stanford.nlp.process.WordShapeClassifier;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.Execution.Option;
import edu.stanford.nlp.util.logging.Redwood;

public abstract class PhraseScorer<E extends Pattern> {
  ConstantsAndVariables<E> constVars;

  double OOVExternalFeatWt = 0.5;
  double OOVdictOdds = 1e-10;
  double OOVDomainNgramScore = 1e-10;
  double OOVGoogleNgramScore = 1e-10;

  @Option(name = "usePatternWeights")
  public boolean usePatternWeights = true;

  @Option(name = "wordFreqNorm")
  Normalization wordFreqNorm = Normalization.valueOf("LOG");
  
  /**
   * For phrases, some phrases are evaluated as a combination of their
   * individual words. Default is taking minimum of all the words. This flag
   * takes average instead of the min.
   */
  @Option(name = "useAvgInsteadofMinPhraseScoring")
  boolean useAvgInsteadofMinPhraseScoring = false;

  public enum Normalization {
    NONE, SQRT, LOG
  };

  boolean forLearningPatterns;

  public PhraseScorer(ConstantsAndVariables constvar) {
    this.constVars = constvar;
  }

  Counter<CandidatePhrase> learnedScores = new ClassicCounter<CandidatePhrase>();

  abstract Counter<CandidatePhrase> scorePhrases(String label, TwoDimensionalCounter<CandidatePhrase, E> terms,
      TwoDimensionalCounter<CandidatePhrase, E> wordsPatExtracted,
      Counter<E> allSelectedPatterns,
      Set<CandidatePhrase> alreadyIdentifiedWords, boolean forLearningPatterns)
      throws IOException, ClassNotFoundException;

  Counter<CandidatePhrase> getLearnedScores() {
    return learnedScores;
  }

  double getPatTFIDFScore(CandidatePhrase word,
      Counter<E> patsThatExtractedThis,
      Counter<E> allSelectedPatterns) {
    double total = 0;

    Set<E> rem = new HashSet<E>();
    for (Entry<E, Double> en2 : patsThatExtractedThis.entrySet()) {
      double weight = 1.0;
      if (usePatternWeights) {
        weight = allSelectedPatterns.getCount(en2.getKey());
        if (weight == 0){
          Redwood.log(Redwood.FORCE, "Warning: Weight zero for " + en2.getKey() + ". May be pattern was removed when choosing other patterns (if subsumed by another pattern).");
          rem.add(en2.getKey());  
        }
      }
      total += weight;
    }
    
    Counters.removeKeys(patsThatExtractedThis, rem);
    
    assert Data.processedDataFreq.containsKey(word) : "How come the processed corpus freq doesnt have "
        + word + " .Size of processedDataFreq is " + Data.processedDataFreq.size()  + " and size of raw freq is " + Data.rawFreq.size();
    return total / Data.processedDataFreq.getCount(word);
  }

  public double getGoogleNgramScore(CandidatePhrase g) {
    if (Data.googleNGram.containsKey(g.getPhrase())) {
      assert (Data.rawFreq.containsKey(g));
      return (1 + Data.rawFreq.getCount(g)
          * Math.sqrt(Data.ratioGoogleNgramFreqWithDataFreq))
          / Data.googleNGram.getCount(g);
    }
    return 0;
  }

  public double getDomainNgramScore(String g) {
    assert Data.domainNGramRawFreq.containsKey(g) : " How come dowmin ngram raw freq does not contain "
        + g;
    if (Data.domainNGramRawFreq.getCount(g) == 0) {
      System.err.println("domain count 0 for " + g);
      return 0;
    }
    return ((1 + Data.rawFreq.getCount(g)
        * Math.sqrt(Data.ratioDomainNgramFreqWithDataFreq)) / Data.domainNGramRawFreq
          .getCount(g));
  }

  public double getDistSimWtScore(String ph, String label) {
    Integer num = constVars.getWordClassClusters().get(ph);
    if (num != null && constVars.distSimWeights.get(label).containsKey(num)) {
      return constVars.distSimWeights.get(label).getCount(num);
    } else {
      String[] t = ph.split("\\s+");
      if (t.length < 2) {
        return OOVExternalFeatWt;
      }

      double totalscore = 0;
      double minScore = Double.MAX_VALUE;
      for (String w : t) {
        double score = OOVExternalFeatWt;
        Integer numw = constVars.getWordClassClusters().get(w);
        if (numw != null
            && constVars.distSimWeights.get(label).containsKey(numw))
          score = constVars.distSimWeights.get(label).getCount(numw);
        if (score < minScore)
          minScore = score;
        totalscore += score;
      }
      if (useAvgInsteadofMinPhraseScoring)
        return totalscore / ph.length();
      else
        return minScore;
    }
  }

  public double getWordShapeScore(String word, String label){
    String wordShape = constVars.getWordShapeCache().get(word);
    if(wordShape == null){
      wordShape = WordShapeClassifier.wordShape(word, constVars.wordShaper);
      constVars.getWordShapeCache().put(word, wordShape);
    }
    double thislabel = 0, alllabels =0;
    for(Entry<String, Counter<String>> en: constVars.getWordShapesForLabels().entrySet()){
      if(en.getKey().equals(label))
        thislabel = en.getValue().getCount(wordShape);
      alllabels += en.getValue().getCount(wordShape);
    }
    double score = thislabel/ (alllabels + 1);
    return score;
  }
  
  public double getDictOddsScore(CandidatePhrase word, String label) {
    double dscore;
    Counter<CandidatePhrase> dictOddsWordWeights = constVars.dictOddsWeights.get(label);
    assert dictOddsWordWeights != null : "dictOddsWordWeights is null for label " + label;
    if (dictOddsWordWeights.containsKey(word)) {
      dscore = dictOddsWordWeights.getCount(word);
    } else
      dscore = getPhraseWeightFromWords(dictOddsWordWeights, word, OOVdictOdds);
    return dscore;
  }

  public double getPhraseWeightFromWords(Counter<CandidatePhrase> weights, CandidatePhrase ph,
      double defaultWt) {
    String[] t = ph.getPhrase().split("\\s+");
    if (t.length < 2) {
      if (weights.containsKey(ph))
        return weights.getCount(ph);
      else
        return defaultWt;
    }
    double totalscore = 0;
    double minScore = Double.MAX_VALUE;
    for (String w : t) {
      double score = defaultWt;
      if (weights.containsKey(new CandidatePhrase(w)))
        score = weights.getCount(w);
      if (score < minScore)
        minScore = score;
      totalscore += score;
    }
    if (useAvgInsteadofMinPhraseScoring)
      return totalscore / ph.getPhrase().length();
    else
      return minScore;
  }

  abstract public Counter<CandidatePhrase> scorePhrases(String label, Set<CandidatePhrase> terms, boolean forLearningPatterns) throws IOException, ClassNotFoundException;
  

}
