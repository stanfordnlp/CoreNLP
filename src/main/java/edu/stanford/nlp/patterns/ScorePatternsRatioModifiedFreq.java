package edu.stanford.nlp.patterns;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.ToDoubleFunction;

import edu.stanford.nlp.patterns.ConstantsAndVariables.ScorePhraseMeasures;
import edu.stanford.nlp.patterns.GetPatternsFromDataMultiClass.PatternScoring;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.ArgumentParser;
import edu.stanford.nlp.util.Pair;

public class ScorePatternsRatioModifiedFreq<E> extends ScorePatterns<E> {

  public ScorePatternsRatioModifiedFreq(
      ConstantsAndVariables constVars,
      PatternScoring patternScoring,
      String label, Set<CandidatePhrase> allCandidatePhrases,
      TwoDimensionalCounter<E, CandidatePhrase> patternsandWords4Label,
      TwoDimensionalCounter<E, CandidatePhrase> negPatternsandWords4Label,
      TwoDimensionalCounter<E, CandidatePhrase> unLabeledPatternsandWords4Label,
      TwoDimensionalCounter<CandidatePhrase, ScorePhraseMeasures> phInPatScores,
      ScorePhrases scorePhrases, Properties props) {
    super(constVars, patternScoring, label, allCandidatePhrases,  patternsandWords4Label,
        negPatternsandWords4Label, unLabeledPatternsandWords4Label,
        props);
    this.phInPatScores = phInPatScores;
    this.scorePhrases = scorePhrases;
  }

  // cached values
  private TwoDimensionalCounter<CandidatePhrase, ScorePhraseMeasures> phInPatScores;

  private ScorePhrases scorePhrases;

  @Override
  public void setUp(Properties props) {
  }

  @Override
  public Counter<E> score() throws IOException, ClassNotFoundException {

    Counter<CandidatePhrase> externalWordWeightsNormalized = null;
    if (constVars.dictOddsWeights.containsKey(label))
      externalWordWeightsNormalized = GetPatternsFromDataMultiClass
          .normalizeSoftMaxMinMaxScores(constVars.dictOddsWeights.get(label),
            true, true, false);

    Counter<E> currentPatternWeights4Label = new ClassicCounter<>();

    boolean useFreqPhraseExtractedByPat = false;
    if (patternScoring.equals(PatternScoring.SqrtAllRatio))
      useFreqPhraseExtractedByPat = true;
    ToDoubleFunction<Pair<E, CandidatePhrase>> numeratorScore = x -> patternsandWords4Label.getCount(x.first(), x.second());

    Counter<E> numeratorPatWt = this.convert2OneDim(label,
        numeratorScore, allCandidatePhrases, patternsandWords4Label, constVars.sqrtPatScore, false, null,
        useFreqPhraseExtractedByPat);

    Counter<E> denominatorPatWt = null;

    ToDoubleFunction<Pair<E, CandidatePhrase>> denoScore;
    if (patternScoring.equals(PatternScoring.PosNegUnlabOdds)) {
      denoScore = x -> negPatternsandWords4Label.getCount(x.first(), x.second()) + unLabeledPatternsandWords4Label.getCount(x.first(), x.second());

      denominatorPatWt = this.convert2OneDim(label,
          denoScore, allCandidatePhrases, patternsandWords4Label, constVars.sqrtPatScore, false,
          externalWordWeightsNormalized, useFreqPhraseExtractedByPat);
    } else if (patternScoring.equals(PatternScoring.RatioAll)) {
      denoScore = x -> negPatternsandWords4Label.getCount(x.first(), x.second()) + unLabeledPatternsandWords4Label.getCount(x.first(), x.second()) +
        patternsandWords4Label.getCount(x.first(), x.second());
      denominatorPatWt = this.convert2OneDim(label, denoScore,allCandidatePhrases, patternsandWords4Label,
          constVars.sqrtPatScore, false, externalWordWeightsNormalized,
          useFreqPhraseExtractedByPat);
    } else if (patternScoring.equals(PatternScoring.PosNegOdds)) {
      denoScore = x -> negPatternsandWords4Label.getCount(x.first(), x.second());
      denominatorPatWt = this.convert2OneDim(label, denoScore, allCandidatePhrases, patternsandWords4Label,
          constVars.sqrtPatScore, false, externalWordWeightsNormalized,
          useFreqPhraseExtractedByPat);
    } else if (patternScoring.equals(PatternScoring.PhEvalInPat)
        || patternScoring.equals(PatternScoring.PhEvalInPatLogP)
        || patternScoring.equals(PatternScoring.LOGREG)
        || patternScoring.equals(PatternScoring.LOGREGlogP)) {
      denoScore = x -> negPatternsandWords4Label.getCount(x.first(), x.second()) + unLabeledPatternsandWords4Label.getCount(x.first(), x.second());
      denominatorPatWt = this.convert2OneDim(label,
        denoScore, allCandidatePhrases, patternsandWords4Label, constVars.sqrtPatScore, true,
          externalWordWeightsNormalized, useFreqPhraseExtractedByPat);
    } else if (patternScoring.equals(PatternScoring.SqrtAllRatio)) {
      denoScore = x -> negPatternsandWords4Label.getCount(x.first(), x.second()) + unLabeledPatternsandWords4Label.getCount(x.first(), x.second());

      denominatorPatWt = this.convert2OneDim(label,
        denoScore, allCandidatePhrases, patternsandWords4Label, true, false,
          externalWordWeightsNormalized, useFreqPhraseExtractedByPat);
    } else
      throw new RuntimeException("Cannot understand patterns scoring");

    currentPatternWeights4Label = Counters.divisionNonNaN(numeratorPatWt,
        denominatorPatWt);

    //Multiplying by logP
    if (patternScoring.equals(PatternScoring.PhEvalInPatLogP) || patternScoring.equals(PatternScoring.LOGREGlogP)) {
      Counter<E> logpos_i = new ClassicCounter<>();
      for (Entry<E, ClassicCounter<CandidatePhrase>> en : patternsandWords4Label
          .entrySet()) {
        logpos_i.setCount(en.getKey(), Math.log(en.getValue().size()));
      }
      Counters.multiplyInPlace(currentPatternWeights4Label, logpos_i);
    }
    Counters.retainNonZeros(currentPatternWeights4Label);
    return currentPatternWeights4Label;
  }

  Counter<E> convert2OneDim(String label,
      ToDoubleFunction<Pair<E, CandidatePhrase>> scoringFunction, Set<CandidatePhrase> allCandidatePhrases, TwoDimensionalCounter<E, CandidatePhrase> positivePatternsAndWords,
      boolean sqrtPatScore, boolean scorePhrasesInPatSelection,
      Counter<CandidatePhrase> dictOddsWordWeights, boolean useFreqPhraseExtractedByPat) throws IOException, ClassNotFoundException {

//    if (Data.googleNGram.size() == 0 && Data.googleNGramsFile != null) {
//      Data.loadGoogleNGrams();
//    }

    Counter<E> patterns = new ClassicCounter<>();

    Counter<CandidatePhrase> googleNgramNormScores = new ClassicCounter<>();
    Counter<CandidatePhrase> domainNgramNormScores = new ClassicCounter<>();

    Counter<CandidatePhrase> externalFeatWtsNormalized = new ClassicCounter<>();
    Counter<CandidatePhrase> editDistanceFromOtherSemanticBinaryScores = new ClassicCounter<>();
    Counter<CandidatePhrase> editDistanceFromAlreadyExtractedBinaryScores = new ClassicCounter<>();
    double externalWtsDefault = 0.5;
    Counter<String> classifierScores = null;

    if ((patternScoring.equals(PatternScoring.PhEvalInPat) || patternScoring
        .equals(PatternScoring.PhEvalInPatLogP)) && scorePhrasesInPatSelection) {

      for (CandidatePhrase gc : allCandidatePhrases) {
        String g = gc.getPhrase();

        if (constVars.usePatternEvalEditDistOther) {
          editDistanceFromOtherSemanticBinaryScores.setCount(gc,
              constVars.getEditDistanceScoresOtherClassThreshold(label, g));
        }

        if (constVars.usePatternEvalEditDistSame) {
          editDistanceFromAlreadyExtractedBinaryScores.setCount(gc,
              1 - constVars.getEditDistanceScoresThisClassThreshold(label, g));
        }

        if (constVars.usePatternEvalGoogleNgram)
            googleNgramNormScores
                .setCount(gc, PhraseScorer.getGoogleNgramScore(gc));

        if (constVars.usePatternEvalDomainNgram) {
          // calculate domain-ngram wts
          if (Data.domainNGramRawFreq.containsKey(g)) {
            assert (Data.rawFreq.containsKey(gc));
            domainNgramNormScores.setCount(gc,
                scorePhrases.phraseScorer.getDomainNgramScore(g));
          }
        }

        if (constVars.usePatternEvalWordClass) {
          Integer num = constVars.getWordClassClusters().get(g);
          if(num == null){
            num = constVars.getWordClassClusters().get(g.toLowerCase());
          }
          if (num != null
              && constVars.distSimWeights.get(label).containsKey(num)) {
            externalFeatWtsNormalized.setCount(gc,
                constVars.distSimWeights.get(label).getCount(num));
          } else
            externalFeatWtsNormalized.setCount(gc, externalWtsDefault);
        }
      }
      if (constVars.usePatternEvalGoogleNgram)
        googleNgramNormScores = GetPatternsFromDataMultiClass
            .normalizeSoftMaxMinMaxScores(googleNgramNormScores, true, true,
                false);
      if (constVars.usePatternEvalDomainNgram)
        domainNgramNormScores = GetPatternsFromDataMultiClass
            .normalizeSoftMaxMinMaxScores(domainNgramNormScores, true, true,
                false);
      if (constVars.usePatternEvalWordClass)
        externalFeatWtsNormalized = GetPatternsFromDataMultiClass
            .normalizeSoftMaxMinMaxScores(externalFeatWtsNormalized, true,
                true, false);
    }

    else if ((patternScoring.equals(PatternScoring.LOGREG) || patternScoring.equals(PatternScoring.LOGREGlogP))
        && scorePhrasesInPatSelection) {
      Properties props2 = new Properties();
      props2.putAll(props);
      props2.setProperty("phraseScorerClass", "edu.stanford.nlp.patterns.ScorePhrasesLearnFeatWt");
      ScorePhrases scoreclassifier = new ScorePhrases(props2, constVars);
      System.out.println("file is " + props.getProperty("domainNGramsFile"));
      ArgumentParser.fillOptions(Data.class, props2);
      classifierScores = scoreclassifier.phraseScorer.scorePhrases(label, allCandidatePhrases,  true);
    }

    Counter<CandidatePhrase> cachedScoresForThisIter = new ClassicCounter<>();

    for (Map.Entry<E, ClassicCounter<CandidatePhrase>> en: positivePatternsAndWords.entrySet()) {

        for(Entry<CandidatePhrase, Double> en2: en.getValue().entrySet()) {
          CandidatePhrase word = en2.getKey();
          Counter<ScorePhraseMeasures> scoreslist = new ClassicCounter<>();
          double score = 1;
          if ((patternScoring.equals(PatternScoring.PhEvalInPat) || patternScoring
            .equals(PatternScoring.PhEvalInPatLogP))
            && scorePhrasesInPatSelection) {
            if (cachedScoresForThisIter.containsKey(word)) {
              score = cachedScoresForThisIter.getCount(word);
            } else {
              if (constVars.getOtherSemanticClassesWords().contains(word)
                || constVars.getCommonEngWords().contains(word))
                score = 1;
              else {

                if (constVars.usePatternEvalSemanticOdds) {
                  double semanticClassOdds = 1;
                  if (dictOddsWordWeights.containsKey(word))
                    semanticClassOdds = 1 - dictOddsWordWeights.getCount(word);
                  scoreslist.setCount(ScorePhraseMeasures.SEMANTICODDS,
                    semanticClassOdds);
                }

                if (constVars.usePatternEvalGoogleNgram) {
                  double gscore = 0;
                  if (googleNgramNormScores.containsKey(word)) {
                    gscore = 1 - googleNgramNormScores.getCount(word);
                  }
                  scoreslist.setCount(ScorePhraseMeasures.GOOGLENGRAM, gscore);
                }

                if (constVars.usePatternEvalDomainNgram) {
                  double domainscore;
                  if (domainNgramNormScores.containsKey(word)) {
                    domainscore = 1 - domainNgramNormScores.getCount(word);
                  } else
                    domainscore = 1 - scorePhrases.phraseScorer
                      .getPhraseWeightFromWords(domainNgramNormScores, word,
                        scorePhrases.phraseScorer.OOVDomainNgramScore);
                  scoreslist.setCount(ScorePhraseMeasures.DOMAINNGRAM,
                    domainscore);
                }
                if (constVars.usePatternEvalWordClass) {
                  double externalFeatureWt = externalWtsDefault;
                  if (externalFeatWtsNormalized.containsKey(word))
                    externalFeatureWt = 1 - externalFeatWtsNormalized.getCount(word);
                  scoreslist.setCount(ScorePhraseMeasures.DISTSIM,
                    externalFeatureWt);
                }

                if (constVars.usePatternEvalEditDistOther) {
                  assert editDistanceFromOtherSemanticBinaryScores.containsKey(word) : "How come no edit distance info for word " + word + "";
                  scoreslist.setCount(ScorePhraseMeasures.EDITDISTOTHER,
                    editDistanceFromOtherSemanticBinaryScores.getCount(word));
                }
                if (constVars.usePatternEvalEditDistSame) {
                  scoreslist.setCount(ScorePhraseMeasures.EDITDISTSAME,
                    editDistanceFromAlreadyExtractedBinaryScores.getCount(word));
                }

                // taking average
                score = Counters.mean(scoreslist);

                phInPatScores.setCounter(word, scoreslist);
              }

              cachedScoresForThisIter.setCount(word, score);
            }
          } else if ((patternScoring.equals(PatternScoring.LOGREG) || patternScoring.equals(PatternScoring.LOGREGlogP))
            && scorePhrasesInPatSelection) {
            score = 1 - classifierScores.getCount(word);
            // score = 1 - scorePhrases.scoreUsingClassifer(classifier,
            // e.getKey(), label, true, null, null, dictOddsWordWeights);
            // throw new RuntimeException("not implemented yet");
          }

          if (useFreqPhraseExtractedByPat)
            score = score * scoringFunction.applyAsDouble(new Pair<>(en.getKey(), word));
          if (constVars.sqrtPatScore)
            patterns.incrementCount(en.getKey(), Math.sqrt(score));
          else
            patterns.incrementCount(en.getKey(), score);
        }
    }



    return patterns;
  }

}
