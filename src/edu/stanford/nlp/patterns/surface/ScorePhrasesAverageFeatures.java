package edu.stanford.nlp.patterns.surface;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import edu.stanford.nlp.patterns.surface.Data;
import edu.stanford.nlp.patterns.surface.ConstantsAndVariables.ScorePhraseMeasures;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Score phrases by averaging scores of individual features.
 * @author Sonal Gupta (sonalg@stanford.edu)
 *
 */
public class ScorePhrasesAverageFeatures<E extends Pattern> extends PhraseScorer<E>{
  
  public ScorePhrasesAverageFeatures(ConstantsAndVariables constvar) {
    super(constvar);
  }


  private TwoDimensionalCounter<String, ScorePhraseMeasures> phraseScoresNormalized = new TwoDimensionalCounter<String, ScorePhraseMeasures>();

  
  @Override
  public Counter<String> scorePhrases(String label, TwoDimensionalCounter<String, E> terms,
      TwoDimensionalCounter<String, E> wordsPatExtracted, Counter<E> allSelectedPatterns,
      Set<String> alreadyIdentifiedWords, boolean forLearningPatterns) {
    Map<String, Counter<ScorePhraseMeasures>> scores = new HashMap<String, Counter<ScorePhraseMeasures>>();
    if (Data.domainNGramsFile != null)
      Data.loadDomainNGrams();


    Redwood.log(ConstantsAndVariables.extremedebug, "Considering terms: " + terms.firstKeySet());

    // calculate TF-IDF like scores
    Counter<String> tfidfScores = new ClassicCounter<String>();
    if (constVars.usePhraseEvalPatWtByFreq) {
      for (Entry<String, ClassicCounter<E>> en : terms.entrySet()) {
        double score = getPatTFIDFScore(en.getKey(), en.getValue(), allSelectedPatterns);
        tfidfScores.setCount(en.getKey(), score);
      }
      Redwood.log(ConstantsAndVariables.extremedebug, "BEFORE IDF " + Counters.toSortedString(tfidfScores, 100, "%1$s:%2$f", "\t"));
      Counters.divideInPlace(tfidfScores, Data.processedDataFreq);
    }

    Counter<String> externalFeatWtsNormalized = new ClassicCounter<String>();
    Counter<String> domainNgramNormScores = new ClassicCounter<String>();
    Counter<String> googleNgramNormScores = new ClassicCounter<String>();
    Counter<String> editDistanceOtherBinaryScores = new ClassicCounter<String>();
    Counter<String> editDistanceSameBinaryScores = new ClassicCounter<String>();

    for (String g : terms.firstKeySet()) {

      if (constVars.usePhraseEvalEditDistOther) {
        editDistanceOtherBinaryScores.setCount(g, 1 - constVars.getEditDistanceScoresOtherClassThreshold(g));
      }

      if (constVars.usePhraseEvalEditDistSame)
        editDistanceSameBinaryScores.setCount(g, constVars.getEditDistanceScoresThisClassThreshold(label, g));

      if (constVars.usePhraseEvalDomainNgram) {
        // calculate domain-ngram wts
        if (Data.domainNGramRawFreq.containsKey(g)) {
          assert (Data.rawFreq.containsKey(g));
          domainNgramNormScores.setCount(g, getDomainNgramScore(g));
        }else
          System.err.println("why is " + g + " not present in domainNgram");
      }

      if (constVars.usePhraseEvalGoogleNgram) {
        if (Data.googleNGram.containsKey(g)) {
          assert (Data.rawFreq.containsKey(g));
          googleNgramNormScores.setCount(g, ((1 + Data.rawFreq.getCount(g) * Math.sqrt(Data.ratioGoogleNgramFreqWithDataFreq)) / Data.googleNGram.getCount(g)));
        }
      }

      if (constVars.usePhraseEvalWordClass) {
        // calculate dist sim weights
        Integer num = constVars.getWordClassClusters().get(g);
        if (num != null && constVars.distSimWeights.get(label).containsKey(num)) {
          externalFeatWtsNormalized.setCount(g, constVars.distSimWeights.get(label).getCount(num));
        } else
          externalFeatWtsNormalized.setCount(g, OOVExternalFeatWt);
      }
    }

    Counter<String> normTFIDFScores = GetPatternsFromDataMultiClass.normalizeSoftMaxMinMaxScores(tfidfScores, true, true, false);
    Counter<String> dictOdddsScores = null;
    if (constVars.usePhraseEvalSemanticOdds){
      assert constVars.dictOddsWeights != null : "usePhraseEvalSemanticOdds is true but dictOddsWeights is null for the label " + label;
      dictOdddsScores = GetPatternsFromDataMultiClass.normalizeSoftMaxMinMaxScores(constVars.dictOddsWeights.get(label), true, true, false);
      }
    domainNgramNormScores = GetPatternsFromDataMultiClass.normalizeSoftMaxMinMaxScores(domainNgramNormScores, true, true, false);
    googleNgramNormScores = GetPatternsFromDataMultiClass.normalizeSoftMaxMinMaxScores(googleNgramNormScores, true, true, false);
    externalFeatWtsNormalized = GetPatternsFromDataMultiClass.normalizeSoftMaxMinMaxScores(externalFeatWtsNormalized, true, true, false);

    // Counters.max(googleNgramNormScores);
    // Counters.max(externalFeatWtsNormalized);

    for (String word : terms.firstKeySet()) {
      if (alreadyIdentifiedWords.contains(word))
        continue;
      Counter<ScorePhraseMeasures> scoreslist = new ClassicCounter<ScorePhraseMeasures>();
      assert normTFIDFScores.containsKey(word) : "NormTFIDF score does not contain" + word;
      double tfscore = normTFIDFScores.getCount(word);
      scoreslist.setCount(ScorePhraseMeasures.PATWTBYFREQ, tfscore);

      if (constVars.usePhraseEvalSemanticOdds) {
        double dscore;
        if (dictOdddsScores.containsKey(word)) {
          dscore = dictOdddsScores.getCount(word);
        } else
          dscore = getPhraseWeightFromWords(dictOdddsScores, word, OOVdictOdds);
        scoreslist.setCount(ScorePhraseMeasures.SEMANTICODDS, dscore);
      }

      if (constVars.usePhraseEvalDomainNgram) {
        double domainscore;
        if (domainNgramNormScores.containsKey(word)) {
          domainscore = domainNgramNormScores.getCount(word);
        } else
          domainscore = getPhraseWeightFromWords(domainNgramNormScores, word, OOVDomainNgramScore);
        scoreslist.setCount(ScorePhraseMeasures.DOMAINNGRAM, domainscore);
      }

      if (constVars.usePhraseEvalGoogleNgram) {
        double googlescore;
        if (googleNgramNormScores.containsKey(word)) {
          googlescore = googleNgramNormScores.getCount(word);
        } else
          googlescore = getPhraseWeightFromWords(googleNgramNormScores, word, OOVGoogleNgramScore);
        scoreslist.setCount(ScorePhraseMeasures.GOOGLENGRAM, googlescore);
      }

      if (constVars.usePhraseEvalWordClass) {
        double externalFeatureWt;
        if (externalFeatWtsNormalized.containsKey(word))
          externalFeatureWt = externalFeatWtsNormalized.getCount(word);
        else
          externalFeatureWt = getPhraseWeightFromWords(externalFeatWtsNormalized, word, OOVExternalFeatWt);
        scoreslist.setCount(ScorePhraseMeasures.DISTSIM, externalFeatureWt);
      }

      if (constVars.usePhraseEvalEditDistOther) {
        assert editDistanceOtherBinaryScores.containsKey(word) : "How come no edit distance info?";
        double editD = editDistanceOtherBinaryScores.getCount(word);
        scoreslist.setCount(ScorePhraseMeasures.EDITDISTOTHER, editD);
      }
      if (constVars.usePhraseEvalEditDistSame) {
        double editDSame = editDistanceSameBinaryScores.getCount(word);
        scoreslist.setCount(ScorePhraseMeasures.EDITDISTSAME, editDSame);
      }
      
      if(constVars.usePhraseEvalWordShape){
        scoreslist.setCount(ScorePhraseMeasures.WORDSHAPE, this.getWordShapeScore(word, label));
      }
      
      scores.put(word, scoreslist);
      phraseScoresNormalized.setCounter(word, scoreslist);
    }
    Counter<String> phraseScores = new ClassicCounter<String>();
    for (Entry<String, Counter<ScorePhraseMeasures>> wEn : scores
        .entrySet()) {
      double avgScore = Counters.mean(wEn.getValue());
      phraseScores.setCount(wEn.getKey(), avgScore);
    }
    return phraseScores;
  }


  @Override
  public Counter<String> scorePhrases(String label, Set<String> terms, boolean forLearningPatterns)
      throws IOException {
    throw new RuntimeException("not implemented");
  }


}
