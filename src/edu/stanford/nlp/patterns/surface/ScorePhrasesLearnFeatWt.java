package edu.stanford.nlp.patterns.surface;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;

import edu.stanford.nlp.classify.LogPrior;
import edu.stanford.nlp.classify.LogisticClassifier;
import edu.stanford.nlp.classify.LogisticClassifierFactory;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.patterns.surface.ConstantsAndVariables.ScorePhraseMeasures;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Execution.Option;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * Learn a logistic regression classifier to combine weights to score a phrase
 * @author Sonal Gupta (sonalg@stanford.edu)
 *
 */
public class ScorePhrasesLearnFeatWt extends PhraseScorer {
  public ScorePhrasesLearnFeatWt(ConstantsAndVariables constvar) {
    super(constvar);
  }

  @Option(name = "scoreClassifierType")
  ClassifierType scoreClassifierType = ClassifierType.LR;

  public enum ClassifierType {
    DT, LR, RF
  }

  public TwoDimensionalCounter<String, ScorePhraseMeasures> phraseScoresRaw = new TwoDimensionalCounter<String, ScorePhraseMeasures>();


  public edu.stanford.nlp.classify.Classifier learnClassifier(String label, boolean forLearningPatterns,
      TwoDimensionalCounter<String, SurfacePattern> wordsPatExtracted, Counter<SurfacePattern> allSelectedPatterns) throws IOException, ClassNotFoundException {
    phraseScoresRaw.clear();
    learnedScores.clear();
    
    if(Data.domainNGramsFile != null)
      Data.loadDomainNGrams();
    
    RVFDataset<String, ScorePhraseMeasures> dataset = new RVFDataset<String, ScorePhraseMeasures>();
    
    boolean computeRawFreq = false;
    if (Data.rawFreq == null) {
      Data.rawFreq = new ClassicCounter<String>();
      computeRawFreq = true;
    }
    
    if(constVars.batchProcessSents){
      
      for(File f: Data.sentsFiles){
        Redwood.log(Redwood.DBG,"Sampling sentences from " + f);
        Map<String, List<CoreLabel>> sents = IOUtils.readObjectFromFile(f);
        if(computeRawFreq)
          Data.computeRawFreqIfNull(sents, constVars.numWordsCompound);
        dataset.addAll(choosedatums(label, forLearningPatterns, sents, constVars.answerClass.get(label), label,
            constVars.getOtherSemanticClasses(), constVars.ignoreWordswithClassesDuringSelection.get(label), constVars.perSelectRand, constVars.perSelectNeg, wordsPatExtracted,
            allSelectedPatterns));
      }
    } else{
      if(computeRawFreq)
        Data.computeRawFreqIfNull(Data.sents, constVars.numWordsCompound);
      dataset.addAll(choosedatums(label, forLearningPatterns, Data.sents, constVars.answerClass.get(label), label,
        constVars.getOtherSemanticClasses(), constVars.ignoreWordswithClassesDuringSelection.get(label), constVars.perSelectRand, constVars.perSelectNeg, wordsPatExtracted,
        allSelectedPatterns));
    }
    edu.stanford.nlp.classify.Classifier classifier;
//    if (scoreClassifierType.equals(ClassifierType.DT)) {
//      ClassifierFactory wekaFactory = new WekaDatumClassifierFactory<String, ScorePhraseMeasures>("weka.classifiers.trees.J48", constVars.wekaOptions);
//      classifier = wekaFactory.trainClassifier(dataset);
//      Classifier cls = ((WekaDatumClassifier) classifier).getClassifier();
//      J48 j48decisiontree = (J48) cls;
//      System.out.println(j48decisiontree.toSummaryString());
//      System.out.println(j48decisiontree.toString());
//
//    } else
    if (scoreClassifierType.equals(ClassifierType.LR)) {
      LogisticClassifierFactory<String, ScorePhraseMeasures> logfactory = new LogisticClassifierFactory<String, ScorePhraseMeasures>();
      LogPrior lprior = new LogPrior();
      lprior.setSigma(constVars.LRSigma);
      classifier = logfactory.trainClassifier(dataset, lprior, false);
      LogisticClassifier logcl = ((LogisticClassifier) classifier);

      String l = (String) logcl.getLabelForInternalPositiveClass();
      Counter<String> weights = logcl.weightsAsGenericCounter();
      if (l.equals(Boolean.FALSE.toString())) {
        Counters.multiplyInPlace(weights, -1);
      }
      List<Pair<String, Double>> wtd = Counters.toDescendingMagnitudeSortedListWithCounts(weights);
      Redwood.log(ConstantsAndVariables.minimaldebug, "The weights are " + StringUtils.join(wtd.subList(0, Math.min(wtd.size(), 200)), "\n"));
    }
//    else if (scoreClassifierType.equals(ClassifierType.RF)) {
//      ClassifierFactory wekaFactory = new WekaDatumClassifierFactory<String, ScorePhraseMeasures>("weka.classifiers.trees.RandomForest", constVars.wekaOptions);
//      classifier = wekaFactory.trainClassifier(dataset);
//      Classifier cls = ((WekaDatumClassifier) classifier).getClassifier();
//      RandomForest rf = (RandomForest) cls;
//    }
    else
      throw new RuntimeException("cannot identify classifier " + scoreClassifierType);
    BufferedWriter w = new BufferedWriter(new FileWriter("tempscorestrainer.txt"));
    System.out.println("size of learned scores is " + phraseScoresRaw.size());
    for (String s : phraseScoresRaw.firstKeySet()) {
      w.write(s + "\t" + phraseScoresRaw.getCounter(s) + "\n");
    }
    w.close();

    return classifier;

  }

  @Override
  public Counter<String> scorePhrases(String label, TwoDimensionalCounter<String, SurfacePattern> terms,
      TwoDimensionalCounter<String, SurfacePattern> wordsPatExtracted, Counter<SurfacePattern> allSelectedPatterns,
      Set<String> alreadyIdentifiedWords, boolean forLearningPatterns) throws IOException, ClassNotFoundException {

    Counter<String> scores = new ClassicCounter<String>();
    edu.stanford.nlp.classify.Classifier classifier = learnClassifier(label, forLearningPatterns, wordsPatExtracted, allSelectedPatterns);
    for (Entry<String, ClassicCounter<SurfacePattern>> en : terms.entrySet()) {
      double score = this.scoreUsingClassifer(classifier, en.getKey(), label, forLearningPatterns, en.getValue(), allSelectedPatterns);
      scores.setCount(en.getKey(), score);
    }
    return scores;
  }
  
  @Override
  public Counter<String> scorePhrases(String label, Set<String> terms, boolean forLearningPatterns) throws IOException, ClassNotFoundException {
    Counter<String> scores = new ClassicCounter<String>();
    edu.stanford.nlp.classify.Classifier classifier = learnClassifier(label, forLearningPatterns, null, null);
    for (String en : terms) {
      double score = this.scoreUsingClassifer(classifier, en, label, forLearningPatterns,null, null);
      scores.setCount(en, score);
    }
    return scores;
  }

  public static boolean getRandomBoolean(Random random, double p) {
    return random.nextFloat() < p;
  }

  static double logistic(double d) {
    return 1 / (1 + Math.exp(-1 * d));
  }

  public RVFDataset<String, ScorePhraseMeasures> choosedatums(String label, boolean forLearningPattern, Map<String, List<CoreLabel>> sents, Class answerClass, String answerLabel,
      Set<String> negativeWords, Map<Class, Object> otherIgnoreClasses, double perSelectRand, double perSelectNeg, TwoDimensionalCounter<String, SurfacePattern> wordsPatExtracted,
      Counter<SurfacePattern> allSelectedPatterns) {
    // TODO: check whats happening with candidate terms for this iteration. do
    // not count them as negative!!!
    Random r = new Random(10);
    Random rneg = new Random(10);
    RVFDataset<String, ScorePhraseMeasures> dataset = new RVFDataset<String, ScorePhraseMeasures>();
    int numpos = 0, numneg = 0;
    List<Pair<String, Integer>> chosen = new ArrayList<Pair<String, Integer>>();
    for (Entry<String, List<CoreLabel>> en : sents.entrySet()) {
      List<CoreLabel> value = en.getValue();
      CoreLabel[] sent = value.toArray(new CoreLabel[value.size()]);

      for (int i = 0; i < sent.length; i++) {
        CoreLabel l = sent[i];

        boolean chooseThis = false;
        boolean ignoreclass = false;
        Boolean datumlabel = false;
        for (Class cl : otherIgnoreClasses.keySet()) {
          if ((Boolean) l.get(cl)) {    // cast is needed for jdk 1.6
            ignoreclass = true;
          }
        }
        if (l.get(answerClass).equals(answerLabel)) {
          datumlabel = true;
          chooseThis = true;
          numpos++;
        }
        if (chooseThis) {
          chosen.add(new Pair<String, Integer>(en.getKey(), i));

          Counter<ScorePhraseMeasures> feat = null;
          if (forLearningPattern) {
            feat = getPhraseFeaturesForPattern(label, l.word());
          } else {
            feat = getFeatures(label, l.word(), wordsPatExtracted.getCounter(l.word()), allSelectedPatterns);
          }
          RVFDatum<String, ScorePhraseMeasures> datum = new RVFDatum<String, ScorePhraseMeasures>(feat, datumlabel.toString());
          dataset.add(datum);
        }
      }

      for (int i = 0; i < sent.length; i++) {
        CoreLabel l = sent[i];
        if (numneg >= numpos)
          break;
        boolean chooseThis = false;
        boolean ignoreclass = false;
        Boolean datumlabel = false;
        if (l.get(answerClass).equals(answerLabel)) {
          continue;
        } else if ((ignoreclass || negativeWords.contains(l.word().toLowerCase())) && getRandomBoolean(rneg, perSelectNeg)) {
          chooseThis = true;
          datumlabel = false;
          numneg++;
        } else if (getRandomBoolean(r, perSelectRand)) {
          chooseThis = true;
          datumlabel = false;
          numneg++;
        } else
          chooseThis = false;
        if (chooseThis) {
          chosen.add(new Pair<String, Integer>(en.getKey(), i));
          Counter<ScorePhraseMeasures> feat = null;
          if (forLearningPattern) {
            feat = getPhraseFeaturesForPattern(label, l.word());
          } else {
            feat = getFeatures(label, l.word(), wordsPatExtracted.getCounter(l.word()), allSelectedPatterns);
          }
          RVFDatum<String, ScorePhraseMeasures> datum = new RVFDatum<String, ScorePhraseMeasures>(feat, datumlabel.toString());
          dataset.add(datum);
        }
      }
    }
    System.out.println("size of the dataset is ");
    dataset.summaryStatistics();
    System.out.println("number of positive datums:  " + numpos + " and number of negative datums: " + numneg);
    return dataset;
  }

  Counter<ScorePhraseMeasures> getPhraseFeaturesForPattern(String label, String word) {

    if (phraseScoresRaw.containsFirstKey(word))
      return phraseScoresRaw.getCounter(word);

    Counter<ScorePhraseMeasures> scoreslist = new ClassicCounter<ScorePhraseMeasures>();

    if (constVars.usePatternEvalSemanticOdds) {
      assert constVars.dictOddsWeights != null : "usePatternEvalSemanticOdds is true but dictOddsWeights is null for the label " + label;
      double dscore = this.getDictOddsScore(word, label);
      dscore = logistic(dscore);
      scoreslist.setCount(ScorePhraseMeasures.SEMANTICODDS, dscore);
    }

    if (constVars.usePatternEvalGoogleNgram) {
      Double gscore = getGoogleNgramScore(word);
      if (gscore.isInfinite() || gscore.isNaN()) {
        throw new RuntimeException("how is the google ngrams score " + gscore + " for " + word);
      }
      gscore = logistic(gscore);
      scoreslist.setCount(ScorePhraseMeasures.GOOGLENGRAM, gscore);
    }

    if (constVars.usePatternEvalDomainNgram) {
      Double gscore = getDomainNgramScore(word);
      if (gscore.isInfinite() || gscore.isNaN()) {
        throw new RuntimeException("how is the domain ngrams score " + gscore + " for " + word + " when domain raw freq is " + Data.domainNGramRawFreq.getCount(word)
            + " and raw freq is " + Data.rawFreq.getCount(word));

      }
      gscore = logistic(gscore);
      scoreslist.setCount(ScorePhraseMeasures.DOMAINNGRAM, gscore);
    }

    if (constVars.usePatternEvalWordClass) {
      double distSimWt = getDistSimWtScore(word, label);
      distSimWt = logistic(distSimWt);
      scoreslist.setCount(ScorePhraseMeasures.DISTSIM, distSimWt);
    }

    if (constVars.usePatternEvalEditDistOther) {
      scoreslist.setCount(ScorePhraseMeasures.EDITDISTSAME, constVars.getEditDistanceScoresThisClass(label, word));
    }
    if (constVars.usePatternEvalEditDistSame)
      scoreslist.setCount(ScorePhraseMeasures.EDITDISTOTHER, constVars.getEditDistanceScoresOtherClass(word));
    
    if(constVars.usePatternEvalWordShape){
      scoreslist.setCount(ScorePhraseMeasures.WORDSHAPE, this.getWordShapeScore(word, label));
    }
    
    phraseScoresRaw.setCounter(word, scoreslist);
    return scoreslist;
  }

  public double scoreUsingClassifer(edu.stanford.nlp.classify.Classifier classifier, String word, String label, boolean forLearningPatterns,
      Counter<SurfacePattern> patternsThatExtractedPat, Counter<SurfacePattern> allSelectedPatterns) {

    if (learnedScores.containsKey(word))
      return learnedScores.getCount(word);
    double score;
    if (scoreClassifierType.equals(ClassifierType.DT)) {
      Counter<ScorePhraseMeasures> feat = null;
      if (forLearningPatterns)
        feat = getPhraseFeaturesForPattern(label, word);
      else
        feat = this.getFeatures(label, word, patternsThatExtractedPat, allSelectedPatterns);

      RVFDatum<String, ScorePhraseMeasures> d = new RVFDatum<String, ScorePhraseMeasures>(feat, Boolean.FALSE.toString());
      Counter<String> sc = classifier.scoresOf(d);
      score = sc.getCount(Boolean.TRUE.toString());

    } else if (scoreClassifierType.equals(ClassifierType.LR)) {

      LogisticClassifier logcl = ((LogisticClassifier) classifier);

      String l = (String) logcl.getLabelForInternalPositiveClass();
      boolean flipsign = false;
      if (l.equals(Boolean.FALSE.toString())) {
        flipsign = true;
      }
      Counter<ScorePhraseMeasures> feat = null;
      if (forLearningPatterns)
        feat = getPhraseFeaturesForPattern(label, word);
      else
        feat = this.getFeatures(label, word, patternsThatExtractedPat, allSelectedPatterns);

      RVFDatum<String, ScorePhraseMeasures> d = new RVFDatum<String, ScorePhraseMeasures>(feat, Boolean.FALSE.toString());
      score = logcl.probabilityOf(d);
      if (flipsign)
        score = 1 - score;

    } else if (scoreClassifierType.equals(ClassifierType.RF)) {

      Counter<ScorePhraseMeasures> feat = null;
      if (forLearningPatterns)
        feat = getPhraseFeaturesForPattern(label, word);
      else
        feat = this.getFeatures(label, word, patternsThatExtractedPat, allSelectedPatterns);

      RVFDatum<String, ScorePhraseMeasures> d = new RVFDatum<String, ScorePhraseMeasures>(feat, Boolean.FALSE.toString());
      Counter<String> sc = classifier.scoresOf(d);
      score = sc.getCount(Boolean.TRUE.toString());

    } else
      throw new RuntimeException("cannot identify classifier " + scoreClassifierType);

    this.learnedScores.setCount(word, score);
    return score;
  }

  Counter<ScorePhraseMeasures> getFeatures(String label, String word, Counter<SurfacePattern> patThatExtractedWord, Counter<SurfacePattern> allSelectedPatterns) {

    if (phraseScoresRaw.containsFirstKey(word))
      return phraseScoresRaw.getCounter(word);

    Counter<ScorePhraseMeasures> scoreslist = new ClassicCounter<ScorePhraseMeasures>();
    if (constVars.usePhraseEvalPatWtByFreq) {
      double tfscore = getPatTFIDFScore(word, patThatExtractedWord, allSelectedPatterns);
      scoreslist.setCount(ScorePhraseMeasures.PATWTBYFREQ, tfscore);
    }

    if (constVars.usePhraseEvalSemanticOdds) {
      double dscore = this.getDictOddsScore(word, label);
      scoreslist.setCount(ScorePhraseMeasures.SEMANTICODDS, dscore);
    }

    if (constVars.usePhraseEvalGoogleNgram) {
      Double gscore = getGoogleNgramScore(word);
      if (gscore.isInfinite() || gscore.isNaN()) {
        throw new RuntimeException("how is the google ngrams score " + gscore + " for " + word);
      }
      scoreslist.setCount(ScorePhraseMeasures.GOOGLENGRAM, gscore);
    }

    if (constVars.usePhraseEvalDomainNgram) {
      Double gscore = getDomainNgramScore(word);
      if (gscore.isInfinite() || gscore.isNaN()) {
        throw new RuntimeException("how is the domain ngrams score " + gscore + " for " + word + " when domain raw freq is " + Data.domainNGramRawFreq.getCount(word)
            + " and raw freq is " + Data.rawFreq.getCount(word));

      }
      scoreslist.setCount(ScorePhraseMeasures.DOMAINNGRAM, gscore);
    }

    if (constVars.usePhraseEvalWordClass) {
      double distSimWt = getDistSimWtScore(word, label);
      scoreslist.setCount(ScorePhraseMeasures.DISTSIM, distSimWt);
    }

    if (constVars.usePhraseEvalEditDistOther) {
      scoreslist.setCount(ScorePhraseMeasures.EDITDISTSAME, constVars.getEditDistanceScoresThisClass(label, word));
    }
    if (constVars.usePhraseEvalEditDistSame)
      scoreslist.setCount(ScorePhraseMeasures.EDITDISTOTHER, constVars.getEditDistanceScoresOtherClass(word));
    
    if(constVars.usePhraseEvalWordShape){
      scoreslist.setCount(ScorePhraseMeasures.WORDSHAPE, this.getWordShapeScore(word, label));
    }
    
    phraseScoresRaw.setCounter(word, scoreslist);
    return scoreslist;
  }

}
