package edu.stanford.nlp.patterns.surface;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;

import edu.stanford.nlp.patterns.surface.ConstantsAndVariables.ScorePhraseMeasures;
import edu.stanford.nlp.patterns.surface.GetPatternsFromDataMultiClass.PatternScoring;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;

public class ScorePatternsRatioModifiedFreq extends ScorePatterns {

  public ScorePatternsRatioModifiedFreq(
      ConstantsAndVariables constVars,
      PatternScoring patternScoring,
      String label,
      TwoDimensionalCounter<SurfacePattern, String> patternsandWords4Label,
      TwoDimensionalCounter<SurfacePattern, String> negPatternsandWords4Label,
      TwoDimensionalCounter<SurfacePattern, String> unLabeledPatternsandWords4Label,
      TwoDimensionalCounter<SurfacePattern, String> negandUnLabeledPatternsandWords4Label,
      TwoDimensionalCounter<SurfacePattern, String> allPatternsandWords4Label,
      TwoDimensionalCounter<String, ScorePhraseMeasures> phInPatScores,
      ScorePhrases scorePhrases, Properties props) {
    super(constVars, patternScoring, label, patternsandWords4Label,
        negPatternsandWords4Label, unLabeledPatternsandWords4Label,
        negandUnLabeledPatternsandWords4Label, allPatternsandWords4Label, props);
    this.phInPatScores = phInPatScores;
    this.scorePhrases = scorePhrases;
  }

  // cached values
  private TwoDimensionalCounter<String, ScorePhraseMeasures> phInPatScores;

  private ScorePhrases scorePhrases;

  @Override
  public void setUp(Properties props) {
  }

  @Override
  Counter<SurfacePattern> score() throws IOException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
    // TODO: changed
    Counter<String> externalWordWeightsNormalized = null;
    if (constVars.dictOddsWeights.containsKey(label))
      externalWordWeightsNormalized = GetPatternsFromDataMultiClass
          .normalizeSoftMaxMinMaxScores(constVars.dictOddsWeights.get(label),
              true, true, false);

    Counter<SurfacePattern> currentPatternWeights4Label = new ClassicCounter<SurfacePattern>();

    boolean useFreqPhraseExtractedByPat = false;
    if (patternScoring.equals(PatternScoring.SqrtAllRatio))
      useFreqPhraseExtractedByPat = true;

    Counter<SurfacePattern> numeratorPatWt = this.convert2OneDim(label,
        patternsandWords4Label, constVars.sqrtPatScore, false, null,
        useFreqPhraseExtractedByPat);
    Counter<SurfacePattern> denominatorPatWt = null;

    if (patternScoring.equals(PatternScoring.PosNegUnlabOdds)) {
      // deno = negandUnLabeledPatternsandWords4Label;
      denominatorPatWt = this.convert2OneDim(label,
          negandUnLabeledPatternsandWords4Label, constVars.sqrtPatScore, false,
          externalWordWeightsNormalized, useFreqPhraseExtractedByPat);
    } else if (patternScoring.equals(PatternScoring.RatioAll)) {
      // deno = allPatternsandWords4Label;
      denominatorPatWt = this.convert2OneDim(label, allPatternsandWords4Label,
          constVars.sqrtPatScore, false, externalWordWeightsNormalized,
          useFreqPhraseExtractedByPat);
    } else if (patternScoring.equals(PatternScoring.PosNegOdds)) {
      // deno = negPatternsandWords4Label;
      denominatorPatWt = this.convert2OneDim(label, negPatternsandWords4Label,
          constVars.sqrtPatScore, false, externalWordWeightsNormalized,

          useFreqPhraseExtractedByPat);
    } else if (patternScoring.equals(PatternScoring.PhEvalInPat)
        || patternScoring.equals(PatternScoring.PhEvalInPatLogP)
        || patternScoring.equals(PatternScoring.LOGREG)) {
      // deno = negandUnLabeledPatternsandWords4Label;
      denominatorPatWt = this.convert2OneDim(label,
          negandUnLabeledPatternsandWords4Label, constVars.sqrtPatScore, true,
          externalWordWeightsNormalized, useFreqPhraseExtractedByPat);
    } else if (patternScoring.equals(PatternScoring.SqrtAllRatio)) {
      // deno = negandUnLabeledPatternsandWords4Label;
      denominatorPatWt = this.convert2OneDim(label,
          negandUnLabeledPatternsandWords4Label, true, false,
          externalWordWeightsNormalized, useFreqPhraseExtractedByPat);
    } else
      throw new RuntimeException("Cannot understand patterns scoring");

    currentPatternWeights4Label = Counters.divisionNonNaN(numeratorPatWt,
        denominatorPatWt);

    if (patternScoring.equals(PatternScoring.PhEvalInPatLogP)) {
      Counter<SurfacePattern> logpos_i = new ClassicCounter<SurfacePattern>();
      for (Entry<SurfacePattern, ClassicCounter<String>> en : patternsandWords4Label
          .entrySet()) {
        logpos_i.setCount(en.getKey(), Math.log(en.getValue().size()));
      }
      Counters.multiplyInPlace(currentPatternWeights4Label, logpos_i);
    }
    Counters.retainNonZeros(currentPatternWeights4Label);
    return currentPatternWeights4Label;
  }

  Counter<SurfacePattern> convert2OneDim(String label,
      TwoDimensionalCounter<SurfacePattern, String> patternsandWords,
      boolean sqrtPatScore, boolean scorePhrasesInPatSelection,
      Counter<String> dictOddsWordWeights, boolean useFreqPhraseExtractedByPat)
      throws IOException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {

    if (Data.googleNGram.size() == 0 && Data.googleNGramsFile != null) {
      Data.loadGoogleNGrams();
    }
    Data.computeRawFreqIfNull(constVars.numWordsCompound);

    Counter<SurfacePattern> patterns = new ClassicCounter<SurfacePattern>();

    Counter<String> googleNgramNormScores = new ClassicCounter<String>();
    Counter<String> domainNgramNormScores = new ClassicCounter<String>();

    Counter<String> externalFeatWtsNormalized = new ClassicCounter<String>();
    Counter<String> editDistanceFromOtherSemanticBinaryScores = new ClassicCounter<String>();
    Counter<String> editDistanceFromAlreadyExtractedBinaryScores = new ClassicCounter<String>();
    double externalWtsDefault = 0.5;
    Counter<String> classifierScores = null;

    if ((patternScoring.equals(PatternScoring.PhEvalInPat) || patternScoring
        .equals(PatternScoring.PhEvalInPatLogP)) && scorePhrasesInPatSelection) {
      Set<String> allPhrasesInQuestion = new HashSet<String>();
      for (Entry<SurfacePattern, ClassicCounter<String>> d : patternsandWords
          .entrySet()) {
        allPhrasesInQuestion.addAll(d.getValue().keySet());
      }
      for (String g : allPhrasesInQuestion) {
        if (constVars.usePatternEvalEditDistOther) {

          editDistanceFromOtherSemanticBinaryScores.setCount(g,
              constVars.getEditDistanceScoresOtherClassThreshold(g));
        }
        if (constVars.usePatternEvalEditDistSame) {
          editDistanceFromAlreadyExtractedBinaryScores.setCount(g,
              1 - constVars.getEditDistanceScoresThisClassThreshold(label, g));
        }

        if (constVars.usePatternEvalGoogleNgram) {
          if (Data.googleNGram.containsKey(g)) {
            assert (Data.rawFreq.containsKey(g));
            googleNgramNormScores
                .setCount(
                    g,
                    ((1 + Data.rawFreq.getCount(g)
                        * Math.sqrt(Data.ratioGoogleNgramFreqWithDataFreq)) / Data.googleNGram
                        .getCount(g)));
          }
        }
        if (constVars.usePatternEvalDomainNgram) {
          // calculate domain-ngram wts
          if (Data.domainNGramRawFreq.containsKey(g)) {
            assert (Data.rawFreq.containsKey(g));
            domainNgramNormScores.setCount(g,
                scorePhrases.phraseScorer.getDomainNgramScore(g));
          }
        }

        if (constVars.usePatternEvalWordClass) {
          Integer num = constVars.getWordClassClusters().get(g);
          if (num != null
              && constVars.distSimWeights.get(label).containsKey(num)) {
            externalFeatWtsNormalized.setCount(g,
                constVars.distSimWeights.get(label).getCount(num));
          } else
            externalFeatWtsNormalized.setCount(g, externalWtsDefault);
        }
      }
      // TODO : changed
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

    else if (patternScoring.equals(PatternScoring.LOGREG)
        && scorePhrasesInPatSelection) {
      Properties props2 = new Properties();
      props2.putAll(props);
      props2.setProperty("phraseScorerClass", "edu.stanford.nlp.patterns.surface.ScorePhrasesLearnFeatWt");
      ScorePhrases scoreclassifier = new ScorePhrases(props2, constVars);
      
      classifierScores = scoreclassifier.phraseScorer.scorePhrases(Data.sents, label, null,
          null, null, null, true);
      // scorePhrases(Data.sents, label, true,
      // constVars.perSelectRand, constVars.perSelectNeg, null, null,
      // dictOddsWordWeights);
      // throw new RuntimeException("Not implemented currently");
    }

    Counter<String> cachedScoresForThisIter = new ClassicCounter<String>();

    for (Entry<SurfacePattern, ClassicCounter<String>> d : patternsandWords
        .entrySet()) {

      for (Entry<String, Double> e : d.getValue().entrySet()) {
        String word = e.getKey();
        Counter<ScorePhraseMeasures> scoreslist = new ClassicCounter<ScorePhraseMeasures>();
        double score = 1;
        if ((patternScoring.equals(PatternScoring.PhEvalInPat) || patternScoring
            .equals(PatternScoring.PhEvalInPatLogP))
            && scorePhrasesInPatSelection) {
          if (cachedScoresForThisIter.containsKey(word)) {
            score = cachedScoresForThisIter.getCount(word);
          } else {
            if (constVars.getOtherSemanticClasses().contains(word)
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
                if (externalFeatWtsNormalized.containsKey(e.getKey()))
                  externalFeatureWt = 1 - externalFeatWtsNormalized.getCount(e
                      .getKey());
                scoreslist.setCount(ScorePhraseMeasures.DISTSIM,
                    externalFeatureWt);
              }

              if (constVars.usePatternEvalEditDistOther) {
                assert editDistanceFromOtherSemanticBinaryScores.containsKey(e
                    .getKey()) : "How come no edit distance info?";
                scoreslist.setCount(ScorePhraseMeasures.EDITDISTOTHER,
                    editDistanceFromOtherSemanticBinaryScores.getCount(e
                        .getKey()));
              }
              if (constVars.usePatternEvalEditDistSame) {
                scoreslist.setCount(ScorePhraseMeasures.EDITDISTSAME,
                    editDistanceFromAlreadyExtractedBinaryScores.getCount(e
                        .getKey()));
              }

              // taking average
              score = Counters.mean(scoreslist);

              phInPatScores.setCounter(e.getKey(), scoreslist);
            }

            cachedScoresForThisIter.setCount(e.getKey(), score);
          }
        } else if (patternScoring.equals(PatternScoring.LOGREG)
            && scorePhrasesInPatSelection) {
          score = 1 - classifierScores.getCount(e.getKey());
          // score = 1 - scorePhrases.scoreUsingClassifer(classifier,
          // e.getKey(), label, true, null, null, dictOddsWordWeights);
          // throw new RuntimeException("not implemented yet");
        }
        if (useFreqPhraseExtractedByPat)
          score = score * e.getValue();
        if (constVars.sqrtPatScore)
          patterns.incrementCount(d.getKey(), Math.sqrt(score));
        else
          patterns.incrementCount(d.getKey(), score);

      }
    }

    return patterns;
  }

}
