package edu.stanford.nlp.patterns;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

import edu.stanford.nlp.classify.LogPrior;
import edu.stanford.nlp.classify.LogisticClassifier;
import edu.stanford.nlp.classify.LogisticClassifierFactory;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.patterns.ConstantsAndVariables.ScorePhraseMeasures;
import edu.stanford.nlp.patterns.dep.DataInstanceDep;
import edu.stanford.nlp.patterns.dep.ExtractPhraseFromPattern;
import edu.stanford.nlp.patterns.dep.ExtractedPhrase;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Execution.Option;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * Learn a logistic regression classifier to combine weights to score a phrase
 * @author Sonal Gupta (sonalg@stanford.edu)
 *
 */
public class ScorePhrasesLearnFeatWt<E extends Pattern> extends PhraseScorer<E> {
  public ScorePhrasesLearnFeatWt(ConstantsAndVariables constvar) {
    super(constvar);
  }

  @Option(name = "scoreClassifierType")
  ClassifierType scoreClassifierType = ClassifierType.LR;

  public enum ClassifierType {
    DT, LR, RF
  }

  public TwoDimensionalCounter<CandidatePhrase, ScorePhraseMeasures> phraseScoresRaw = new TwoDimensionalCounter<CandidatePhrase, ScorePhraseMeasures>();


  public edu.stanford.nlp.classify.Classifier learnClassifier(String label, boolean forLearningPatterns,
      TwoDimensionalCounter<CandidatePhrase, E> wordsPatExtracted, Counter<E> allSelectedPatterns) throws IOException, ClassNotFoundException {
    phraseScoresRaw.clear();
    learnedScores.clear();
    
    if(Data.domainNGramsFile != null)
      Data.loadDomainNGrams();
    
    RVFDataset<String, ScorePhraseMeasures> dataset = new RVFDataset<String, ScorePhraseMeasures>();
    
    boolean computeRawFreq = false;
    if (Data.rawFreq == null) {
      Data.rawFreq = new ClassicCounter<CandidatePhrase>();
      computeRawFreq = true;
    }

    ConstantsAndVariables.DataSentsIterator sentsIter = new ConstantsAndVariables.DataSentsIterator(constVars.batchProcessSents);
    while(sentsIter.hasNext()) {
      Pair<Map<String, DataInstance>, File> sentsf = sentsIter.next();
      Redwood.log(Redwood.DBG,"Sampling sentences from " + sentsf.second());
      if(computeRawFreq)
        Data.computeRawFreqIfNull(sentsf.first(), PatternFactory.numWordsCompound);
      dataset.addAll(choosedatums(label, forLearningPatterns, sentsf.first(), constVars.getAnswerClass().get(label), label,
        constVars.getIgnoreWordswithClassesDuringSelection().get(label), constVars.perSelectRand, constVars.perSelectNeg, wordsPatExtracted,
        allSelectedPatterns));
    }

    /*
      if(constVars.batchProcessSents){
      
      for(File f: Data.sentsFiles){
        Redwood.log(Redwood.DBG,"Sampling sentences from " + f);
        Map<String, List<CoreLabel>> sents = IOUtils.readObjectFromFile(f);
        if(computeRawFreq)
          Data.computeRawFreqIfNull(sents, constVars.numWordsCompound);
        dataset.addAll(choosedatums(label, forLearningPatterns, sents, constVars.getAnswerClass().get(label), label,
            constVars.getOtherSemanticClassesWords(), constVars.getIgnoreWordswithClassesDuringSelection().get(label), constVars.perSelectRand, constVars.perSelectNeg, wordsPatExtracted,
            allSelectedPatterns));
      }
    } else{
      if(computeRawFreq)
        Data.computeRawFreqIfNull(Data.sents, constVars.numWordsCompound);
      dataset.addAll(choosedatums(label, forLearningPatterns, Data.sents, constVars.getAnswerClass().get(label), label,
        constVars.getOtherSemanticClassesWords(), constVars.getIgnoreWordswithClassesDuringSelection().get(label), constVars.perSelectRand, constVars.perSelectNeg, wordsPatExtracted,
        allSelectedPatterns));
    }*/
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
    for (CandidatePhrase s : phraseScoresRaw.firstKeySet()) {
      w.write(s + "\t" + phraseScoresRaw.getCounter(s) + "\n");
    }
    w.close();

    return classifier;

  }

  @Override
  public Counter<CandidatePhrase> scorePhrases(String label, TwoDimensionalCounter<CandidatePhrase, E> terms,
      TwoDimensionalCounter<CandidatePhrase, E> wordsPatExtracted, Counter<E> allSelectedPatterns,
      Set<CandidatePhrase> alreadyIdentifiedWords, boolean forLearningPatterns) throws IOException, ClassNotFoundException {
    getAllLabeledWordsCluster();
    Counter<CandidatePhrase> scores = new ClassicCounter<CandidatePhrase>();
    edu.stanford.nlp.classify.Classifier classifier = learnClassifier(label, forLearningPatterns, wordsPatExtracted, allSelectedPatterns);
    for (Entry<CandidatePhrase, ClassicCounter<E>> en : terms.entrySet()) {
      double score = this.scoreUsingClassifer(classifier, en.getKey(), label, forLearningPatterns, en.getValue(), allSelectedPatterns);
      scores.setCount(en.getKey(), score);
    }
    return scores;
  }
  
  @Override
  public Counter<CandidatePhrase> scorePhrases(String label, Set<CandidatePhrase> terms, boolean forLearningPatterns) throws IOException, ClassNotFoundException {
    getAllLabeledWordsCluster();
    Counter<CandidatePhrase> scores = new ClassicCounter<CandidatePhrase>();
    edu.stanford.nlp.classify.Classifier classifier = learnClassifier(label, forLearningPatterns, null, null);
    for (CandidatePhrase en : terms) {
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

  Map<CandidatePhrase, Counter<Integer>> wordClassClustersForPhrase = new HashMap<CandidatePhrase, Counter<Integer>>();

  Counter<Integer> wordClass(String phrase, String phraseLemma){
    Counter<Integer> cl = new ClassicCounter<Integer>();
    String[] phl = null;
    if(phraseLemma!=null)
      phl = phraseLemma.split("\\s+");
    int i =0;
    for(String w: phrase.split("\\s+")) {
      Integer cluster = constVars.getWordClassClusters().get(w);
      if (cluster == null && phl!=null)
          cluster = constVars.getWordClassClusters().get(phl[i]);
      if(cluster != null)
        cl.incrementCount(cluster);
      i++;
    }
    return cl;
  }

  void getAllLabeledWordsCluster(){
    for(String label: constVars.getLabels()){
    for(Map.Entry<CandidatePhrase, Double> p : constVars.getLearnedWords(label).entrySet()){
      wordClassClustersForPhrase.put(p.getKey(), wordClass(p.getKey().getPhrase(), p.getKey().getPhraseLemma()));
    }

    for(CandidatePhrase p : constVars.getSeedLabelDictionary().get(label)){
      wordClassClustersForPhrase.put(p, wordClass(p.getPhrase(), p.getPhraseLemma()));
    }
    }
  }

  Set<CandidatePhrase> chooseNegatives(Set<CandidatePhrase> candidatePhrases, String label, int maxNum){
    Counter<CandidatePhrase> sims = new ClassicCounter<CandidatePhrase>();
    for(CandidatePhrase p : candidatePhrases) {
    Counter<Integer> feat = wordClassClustersForPhrase.get(p);
    if(feat == null){
      feat = wordClass(p.getPhrase(), p.getPhraseLemma());
      wordClassClustersForPhrase.put(p, feat);
    }
    double maxSim = Double.MIN_VALUE;
    for(CandidatePhrase pos: CollectionUtils.union(constVars.getLearnedWords(label).keySet(), constVars.getSeedLabelDictionary().get(label))){
        double j = Counters.jaccardCoefficient(wordClassClustersForPhrase.get(pos), feat);
      if(j  >maxSim)
        maxSim = j;
    }
      sims.setCount(p, maxSim);
    }
    Counters.retainBottom(sims, Math.min((int) (sims.size() * 0.8), maxNum));
    System.out.println("choosing " + sims + " as the negative phrases");
    return sims.keySet();
  }



  Set<CandidatePhrase> chooseNegativePhrases(DataInstance sent, Random random, double perSelect, Class positiveClass, String label, int maxNum){

    Set<CandidatePhrase> negativeSamples = new HashSet<CandidatePhrase>();

    if(maxNum == 0)
      return negativeSamples;

    Function<CoreLabel, Boolean> acceptWord = coreLabel -> {
      if(coreLabel.get(positiveClass).equals(label))
        return false;
      else
        return true;
    };

    Random r = new Random(0);
    List<Integer> lengths = new ArrayList<Integer>();
    for(int i = 1;i <= PatternFactory.numWordsCompound; i++)
      lengths.add(i);
    int length = CollectionUtils.sample(lengths, r);

    if(constVars.patternType.equals(PatternFactory.PatternType.DEP)){

    ExtractPhraseFromPattern extract = new ExtractPhraseFromPattern(true, length);
    SemanticGraph g = ((DataInstanceDep) sent).getGraph();
    Collection<CoreLabel> sampledHeads = CollectionUtils.sampleWithoutReplacement(sent.getTokens(), Math.min(maxNum, (int) (perSelect * sent.getTokens().size())), random);

    //TODO: change this for more efficient implementation
    List<String> textTokens = sent.getTokens().stream().map(x -> x.word()).collect(Collectors.toList());

    for(CoreLabel l: sampledHeads) {
      if(!acceptWord.apply(l))
        continue;
      IndexedWord w = g.getNodeByIndex(l.index());
      List<String> outputPhrases = new ArrayList<String>();
      List<ExtractedPhrase> extractedPhrases = new ArrayList<ExtractedPhrase>();
      List<IntPair> outputIndices = new ArrayList<IntPair>();

      extract.printSubGraph(g, w, new ArrayList<String>(), textTokens, outputPhrases, outputIndices, new ArrayList<IndexedWord>(), new ArrayList<IndexedWord>(),
        false, extractedPhrases, null, acceptWord);
      for(ExtractedPhrase p :extractedPhrases){
        negativeSamples.add(new CandidatePhrase(p.getValue(), null, p.getFeatures()));
      }
    }

    }else if(constVars.patternType.equals(PatternFactory.PatternType.SURFACE)){
      CoreLabel[] tokens = sent.getTokens().toArray(new CoreLabel[0]);
      for(int i =0; i < tokens.length; i++){
        if(random.nextDouble() < 0.5){
          int left = (int)((length -1) /2.0);
          int right = length -1 -left;
          String ph = "";
          boolean haspositive = false;
          for(int j = Math.max(0, i - left); j < tokens.length && j <= i+right; j++){
            if(tokens[j].get(positiveClass).equals(label)){
              haspositive = true;
              ph += " " + tokens[j].word();
              break;
            }
          }
          if(!haspositive){
            negativeSamples.add(CandidatePhrase.createOrGet(ph.trim()));
          }
        }
      }

    } else
    throw new RuntimeException("not yet implemented");
    return chooseNegatives(negativeSamples, label, maxNum);
  }



  public RVFDataset<String, ScorePhraseMeasures> choosedatums(String label, boolean forLearningPattern, Map<String, DataInstance> sents, Class answerClass, String answerLabel,
      Map<Class, Object> otherIgnoreClasses, double perSelectRand, double perSelectNeg, TwoDimensionalCounter<CandidatePhrase, E> wordsPatExtracted,
      Counter<E> allSelectedPatterns) {
    // TODO: check whats happening with candidate terms for this iteration. do
    // not count them as negative!!! -- I think this comment is not valid anymore.
    Random r = new Random(10);
    Random rneg = new Random(10);
    RVFDataset<String, ScorePhraseMeasures> dataset = new RVFDataset<String, ScorePhraseMeasures>();
    int numpos = 0;//, numneg = 0;
    List<Pair<String, Integer>> chosen = new ArrayList<Pair<String, Integer>>();
    List<CandidatePhrase> allNegativePhrases = new ArrayList<CandidatePhrase>();

    for (Entry<String, DataInstance> en : sents.entrySet()) {
      List<CoreLabel> value = en.getValue().getTokens();
      CoreLabel[] sent = value.toArray(new CoreLabel[value.size()]);

      for (int i = 0; i < sent.length; i++) {
        CoreLabel l = sent[i];


        if (l.get(answerClass).equals(answerLabel)) {
          CandidatePhrase candidate = l.get(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class).get(label);

          if (candidate == null) {
            System.out.println("candidate null for " + l.word() + " and longest matching" + l.get(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class) + " and hash amp is " + CandidatePhrase.candidatePhraseMap);
            throw new RuntimeException("");
            //candidate = CandidatePhrase.createOrGet(l.word());
          }

          numpos++;
          chosen.add(new Pair<String, Integer>(en.getKey(), i));

          Counter<ScorePhraseMeasures> feat = null;
          //CandidatePhrase candidate = new CandidatePhrase(l.word());
          if (forLearningPattern) {
            feat = getPhraseFeaturesForPattern(label, candidate);
          } else {
            feat = getFeatures(label, candidate, wordsPatExtracted.getCounter(candidate), allSelectedPatterns);
          }
          RVFDatum<String, ScorePhraseMeasures> datum = new RVFDatum<String, ScorePhraseMeasures>(feat, "true");
          dataset.add(datum);
        } else {
          boolean ignoreclass = false;
          for (Class cl : otherIgnoreClasses.keySet()) {
            if ((Boolean) l.get(cl)) {
              ignoreclass = true;
            }
          }
          CandidatePhrase candidate = null;

          boolean negative = false;
          boolean add= false;
          Map<String, CandidatePhrase> longestMatching = l.get(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class);
          for (Map.Entry<String, CandidatePhrase> lo : longestMatching.entrySet()) {
            if (!lo.getKey().equals(label) && lo.getValue() != null) {
              negative = true;
              add = true;
              candidate = lo.getValue();
            }
          }
          if (!negative && ignoreclass) {
            candidate = longestMatching.get("OTHERSEM");
            add = true;
          }
          if(add && rneg.nextDouble() < perSelectNeg){
            allNegativePhrases.add(candidate);
          }
        }
      }
      allNegativePhrases.addAll(this.chooseNegativePhrases(en.getValue(), r, perSelectRand, constVars.getAnswerClass().get(label), label,Math.max(0, Integer.MAX_VALUE)));
//
//        if (negative && getRandomBoolean(rneg, perSelectNeg)) {
//          numneg++;
//        } else if (getRandomBoolean(r, perSelectRand)) {
//          candidate = CandidatePhrase.createOrGet(l.word());
//          numneg++;
//        } else {
//          continue;
//        }
//
//
//          chosen.add(new Pair<String, Integer>(en.getKey(), i));
    }

    for(CandidatePhrase negative: allNegativePhrases){
      Counter<ScorePhraseMeasures> feat;
      //CandidatePhrase candidate = new CandidatePhrase(l.word());
      if (forLearningPattern) {
        feat = getPhraseFeaturesForPattern(label, negative);
      } else {
        feat = getFeatures(label, negative, wordsPatExtracted.getCounter(negative), allSelectedPatterns);
      }
      RVFDatum<String, ScorePhraseMeasures> datum = new RVFDatum<String, ScorePhraseMeasures>(feat, "false");
      dataset.add(datum);
    }

    System.out.println("size of the dataset is ");
    dataset.summaryStatistics();
    System.out.println("number of positive datums:  " + numpos + " and number of negative datums: " + allNegativePhrases.size());
    return dataset;
  }

  Counter<ScorePhraseMeasures> getPhraseFeaturesForPattern(String label, CandidatePhrase word) {

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
      Double gscore = getDomainNgramScore(word.getPhrase());
      if (gscore.isInfinite() || gscore.isNaN()) {
        throw new RuntimeException("how is the domain ngrams score " + gscore + " for " + word + " when domain raw freq is " + Data.domainNGramRawFreq.getCount(word)
            + " and raw freq is " + Data.rawFreq.getCount(word));

      }
      gscore = logistic(gscore);
      scoreslist.setCount(ScorePhraseMeasures.DOMAINNGRAM, gscore);
    }

    if (constVars.usePatternEvalWordClass) {
      double distSimWt = getDistSimWtScore(word.getPhrase(), label);
      distSimWt = logistic(distSimWt);
      scoreslist.setCount(ScorePhraseMeasures.DISTSIM, distSimWt);
    }

    if (constVars.usePatternEvalEditDistOther) {
      scoreslist.setCount(ScorePhraseMeasures.EDITDISTSAME, constVars.getEditDistanceScoresThisClass(label, word.getPhrase()));
    }
    if (constVars.usePatternEvalEditDistSame)
      scoreslist.setCount(ScorePhraseMeasures.EDITDISTOTHER, constVars.getEditDistanceScoresOtherClass(word.getPhrase()));
    
    if(constVars.usePatternEvalWordShape){
      scoreslist.setCount(ScorePhraseMeasures.WORDSHAPE, this.getWordShapeScore(word.getPhrase(), label));
    }
    
    phraseScoresRaw.setCounter(word, scoreslist);
    return scoreslist;
  }

  public double scoreUsingClassifer(edu.stanford.nlp.classify.Classifier classifier, CandidatePhrase word, String label, boolean forLearningPatterns,
      Counter<E> patternsThatExtractedPat, Counter<E> allSelectedPatterns) {

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

  Counter<ScorePhraseMeasures> getFeatures(String label, CandidatePhrase word, Counter<E> patThatExtractedWord, Counter<E> allSelectedPatterns) {

    if (phraseScoresRaw.containsFirstKey(word))
      return phraseScoresRaw.getCounter(word);

    Counter<ScorePhraseMeasures> scoreslist = new ClassicCounter<ScorePhraseMeasures>();

    //Add features on the word, if any!
    if(word.getFeatures()!= null){
      scoreslist.addAll(Counters.transform(word.getFeatures(), x -> ScorePhraseMeasures.create(x)));
    } else{
      System.out.println("features are null for " + word);
    }


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
      Double gscore = getDomainNgramScore(word.getPhrase());
      if (gscore.isInfinite() || gscore.isNaN()) {
        throw new RuntimeException("how is the domain ngrams score " + gscore + " for " + word + " when domain raw freq is " + Data.domainNGramRawFreq.getCount(word)
            + " and raw freq is " + Data.rawFreq.getCount(word));

      }
      scoreslist.setCount(ScorePhraseMeasures.DOMAINNGRAM, gscore);
    }

    if (constVars.usePhraseEvalWordClass) {
//      double distSimWt = getDistSimWtScore(word.getPhrase(), label);
//      scoreslist.setCount(ScorePhraseMeasures.DISTSIM, distSimWt);
      Integer wordclass = constVars.getWordClassClusters().get(word.getPhrase());
      scoreslist.setCount(ScorePhraseMeasures.create(ScorePhraseMeasures.DISTSIM.toString()+"-"+wordclass), 1.0);
    }

    if (constVars.usePhraseEvalEditDistOther) {
      scoreslist.setCount(ScorePhraseMeasures.EDITDISTSAME, constVars.getEditDistanceScoresThisClass(label, word.getPhrase()));
    }
    if (constVars.usePhraseEvalEditDistSame)
      scoreslist.setCount(ScorePhraseMeasures.EDITDISTOTHER, constVars.getEditDistanceScoresOtherClass(word.getPhrase()));
    
    if(constVars.usePhraseEvalWordShape){
      scoreslist.setCount(ScorePhraseMeasures.WORDSHAPE, this.getWordShapeScore(word.getPhrase(), label));
    }
    
    phraseScoresRaw.setCounter(word, scoreslist);
    return scoreslist;
  }

}
