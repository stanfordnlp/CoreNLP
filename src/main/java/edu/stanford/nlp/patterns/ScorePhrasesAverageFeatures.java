package edu.stanford.nlp.patterns;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import edu.stanford.nlp.patterns.ConstantsAndVariables.ScorePhraseMeasures;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.GoogleNGramsSQLBacked;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Score phrases by averaging scores of individual features.
 * @author Sonal Gupta (sonalg@stanford.edu)
 *
 */
public class ScorePhrasesAverageFeatures<E extends Pattern> extends PhraseScorer<E> {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ScorePhrasesAverageFeatures.class);

  public ScorePhrasesAverageFeatures(ConstantsAndVariables constvar) {
    super(constvar);
  }


  private TwoDimensionalCounter<CandidatePhrase, ScorePhraseMeasures> phraseScoresNormalized = new TwoDimensionalCounter<>();

  
  @Override
  public Counter<CandidatePhrase> scorePhrases(String label, TwoDimensionalCounter<CandidatePhrase, E> terms,
      TwoDimensionalCounter<CandidatePhrase, E> wordsPatExtracted, Counter<E> allSelectedPatterns,
      Set<CandidatePhrase> alreadyIdentifiedWords, boolean forLearningPatterns) {
    Map<CandidatePhrase, Counter<ScorePhraseMeasures>> scores = new HashMap<>();
    if (Data.domainNGramsFile != null)
      Data.loadDomainNGrams();


    Redwood.log(ConstantsAndVariables.extremedebug, "Considering terms: " + terms.firstKeySet());

    // calculate TF-IDF like scores
    Counter<CandidatePhrase> tfidfScores = new ClassicCounter<>();
    if (constVars.usePhraseEvalPatWtByFreq) {
      for (Entry<CandidatePhrase, ClassicCounter<E>> en : terms.entrySet()) {
        double score = getPatTFIDFScore(en.getKey(), en.getValue(), allSelectedPatterns);
        tfidfScores.setCount(en.getKey(), score);
      }
      Redwood.log(ConstantsAndVariables.extremedebug, "BEFORE IDF " + Counters.toSortedString(tfidfScores, 100, "%1$s:%2$f", "\t"));
      Counters.divideInPlace(tfidfScores, Data.processedDataFreq);
    }

    Counter<CandidatePhrase> externalFeatWtsNormalized = new ClassicCounter<>();
    Counter<CandidatePhrase> domainNgramNormScores = new ClassicCounter<>();
    Counter<CandidatePhrase> googleNgramNormScores = new ClassicCounter<>();
    Counter<CandidatePhrase> editDistanceOtherBinaryScores = new ClassicCounter<>();
    Counter<CandidatePhrase> editDistanceSameBinaryScores = new ClassicCounter<>();

    for (CandidatePhrase gc : terms.firstKeySet()) {
      String g = gc.getPhrase();
      if (constVars.usePhraseEvalEditDistOther) {
        editDistanceOtherBinaryScores.setCount(gc, 1 - constVars.getEditDistanceScoresOtherClassThreshold(label, g));
      }

      if (constVars.usePhraseEvalEditDistSame)
        editDistanceSameBinaryScores.setCount(gc, constVars.getEditDistanceScoresThisClassThreshold(label, g));

      if (constVars.usePhraseEvalDomainNgram) {
        // calculate domain-ngram wts
        if (Data.domainNGramRawFreq.containsKey(g)) {
          assert (Data.rawFreq.containsKey(gc));
          domainNgramNormScores.setCount(gc, getDomainNgramScore(g));
        }else
          log.info("why is " + g + " not present in domainNgram");
      }

      if (constVars.usePhraseEvalGoogleNgram)
        googleNgramNormScores.setCount(gc, getGoogleNgramScore(gc));

      if (constVars.usePhraseEvalWordClass) {
        // calculate dist sim weights
        Integer num = constVars.getWordClassClusters().get(g);
        if(num == null){
          num = constVars.getWordClassClusters().get(g.toLowerCase());
        }
        if (num != null && constVars.distSimWeights.get(label).containsKey(num)) {
          externalFeatWtsNormalized.setCount(gc, constVars.distSimWeights.get(label).getCount(num));
        } else
          externalFeatWtsNormalized.setCount(gc, OOVExternalFeatWt);
      }
    }

    Counter<CandidatePhrase> normTFIDFScores = GetPatternsFromDataMultiClass.normalizeSoftMaxMinMaxScores(tfidfScores, true, true, false);
    Counter<CandidatePhrase> dictOdddsScores = null;
    if (constVars.usePhraseEvalSemanticOdds){
      assert constVars.dictOddsWeights != null : "usePhraseEvalSemanticOdds is true but dictOddsWeights is null for the label " + label;
      dictOdddsScores = GetPatternsFromDataMultiClass.normalizeSoftMaxMinMaxScores(constVars.dictOddsWeights.get(label), true, true, false);
      }
    domainNgramNormScores = GetPatternsFromDataMultiClass.normalizeSoftMaxMinMaxScores(domainNgramNormScores, true, true, false);
    googleNgramNormScores = GetPatternsFromDataMultiClass.normalizeSoftMaxMinMaxScores(googleNgramNormScores, true, true, false);
    externalFeatWtsNormalized = GetPatternsFromDataMultiClass.normalizeSoftMaxMinMaxScores(externalFeatWtsNormalized, true, true, false);

    // Counters.max(googleNgramNormScores);
    // Counters.max(externalFeatWtsNormalized);

    for (CandidatePhrase word : terms.firstKeySet()) {
      if (alreadyIdentifiedWords.contains(word))
        continue;
      Counter<ScorePhraseMeasures> scoreslist = new ClassicCounter<>();
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
        scoreslist.setCount(ScorePhraseMeasures.WORDSHAPE, this.getWordShapeScore(word.getPhrase(), label));
      }
      
      scores.put(word, scoreslist);
      phraseScoresNormalized.setCounter(word, scoreslist);
    }
    Counter<CandidatePhrase> phraseScores = new ClassicCounter<>();
    for (Entry<CandidatePhrase, Counter<ScorePhraseMeasures>> wEn : scores
        .entrySet()) {
      Double avgScore = Counters.mean(wEn.getValue());
      if(!avgScore.isInfinite() && !avgScore.isNaN())
        phraseScores.setCount(wEn.getKey(), avgScore);
      else
        Redwood.log(Redwood.DBG, "Ignoring " + wEn.getKey() + " because score is " + avgScore);
    }
    return phraseScores;
  }


  @Override
  public Counter<CandidatePhrase> scorePhrases(String label, Set<CandidatePhrase> terms, boolean forLearningPatterns)
      throws IOException {
    throw new RuntimeException("not implemented");
  }

  @Override
  public void printReasonForChoosing(Counter<CandidatePhrase> phrases) {
    //TODO
  }
}
