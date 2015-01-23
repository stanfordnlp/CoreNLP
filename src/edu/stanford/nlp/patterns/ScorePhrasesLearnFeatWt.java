package edu.stanford.nlp.patterns;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
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
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.Execution.Option;
import edu.stanford.nlp.util.concurrent.AtomicDouble;
import edu.stanford.nlp.util.concurrent.ConcurrentHashCounter;
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

    boolean computeRawFreq = false;
    if (Data.rawFreq == null) {
      Data.rawFreq = new ClassicCounter<CandidatePhrase>();
      computeRawFreq = true;
    }

    RVFDataset<String, ScorePhraseMeasures> dataset = choosedatums(forLearningPatterns, label, wordsPatExtracted, allSelectedPatterns, computeRawFreq);


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
      Redwood.log(ConstantsAndVariables.minimaldebug, "The weights are " + StringUtils.join(wtd.subList(0, Math.min(wtd.size(), 600)), "\n"));
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

  class ComputeSim implements Callable<Counter<CandidatePhrase>>{

    List<CandidatePhrase> candidatePhrases;
    String label;
    AtomicDouble allMaxSim;

    public ComputeSim(String label, List<CandidatePhrase> candidatePhrases, AtomicDouble allMaxSim){
      this.label = label;
      this.candidatePhrases = candidatePhrases;
      this.allMaxSim = allMaxSim;
    }

    @Override
    public Counter<CandidatePhrase> call() throws Exception {
      Counter<CandidatePhrase> sims = new ClassicCounter<CandidatePhrase>(candidatePhrases.size());

      for(CandidatePhrase p : candidatePhrases) {
        Counter<Integer> feat = wordClassClustersForPhrase.get(p);
        if(feat == null){
          feat = wordClass(p.getPhrase(), p.getPhraseLemma());
          wordClassClustersForPhrase.put(p, feat);
        }

        double maxSim = Double.MIN_VALUE;
        if(feat.size() > 0) {
          for (CandidatePhrase pos : CollectionUtils.union(constVars.getLearnedWords(label).keySet(), constVars.getSeedLabelDictionary().get(label))) {
            Counter<Integer> posfeat = wordClassClustersForPhrase.get(pos);
            if(posfeat.size() > 0){
              double j = Counters.jaccardCoefficient(posfeat, feat);
              //System.out.println("clusters for positive phrase " + pos + " is " +wordClassClustersForPhrase.get(pos) + " and the features for unknown are "  + feat + " for phrase " + p);
              if (j > maxSim)
                maxSim = j;
            }
          }
        }

        sims.setCount(p, maxSim);
        if(allMaxSim.get() < maxSim)
          allMaxSim.set(maxSim);
      }
      return sims;
    }
  }

  //this chooses the ones that are not close to the positive phrases!
  Set<CandidatePhrase> chooseUnknownAsNegatives(List<CandidatePhrase> candidatePhrases, String label, double percentage){

    List<List<CandidatePhrase>> threadedCandidates = GetPatternsFromDataMultiClass.getThreadBatches(candidatePhrases, constVars.numThreads);

    Counter<CandidatePhrase> sims = new ClassicCounter<CandidatePhrase>();

    AtomicDouble allMaxSim = new AtomicDouble(Double.MIN_VALUE);

    ExecutorService executor = Executors.newFixedThreadPool(constVars.numThreads);
    List<Future<Counter<CandidatePhrase>>> list = new ArrayList<Future<Counter<CandidatePhrase>>>();

    //multi-threaded choose positive, negative and unknown
    for (List<CandidatePhrase> keys : threadedCandidates) {
      Callable<Counter<CandidatePhrase>> task = new ComputeSim(label, keys, allMaxSim);
      Future<Counter<CandidatePhrase>> submit = executor.submit(task);
      list.add(submit);
    }

    // Now retrieve the result
    for (Future<Counter<CandidatePhrase>> future : list) {
      try {
        sims.addAll(future.get());
      } catch (Exception e) {
        executor.shutdownNow();
        throw new RuntimeException(e);
      }
    }
    executor.shutdown();


    if(allMaxSim.get() == Double.MIN_VALUE){
      Redwood.log(Redwood.DBG, "No similarity recorded between the positives and the unknown!");
    }

    Collection<CandidatePhrase> removed = Counters.retainBottom(sims, (int) (sims.size() * percentage));
    System.out.println("not choosing " + removed + " as the negative phrases. percentage is " + percentage + " and allMaxsim was " + allMaxSim);
    return sims.keySet();
  }



  Set<CandidatePhrase> chooseUnknownPhrases(DataInstance sent, Random random, double perSelect, Class positiveClass, String label, int maxNum){

    Set<CandidatePhrase> unknownSamples = new HashSet<CandidatePhrase>();

    if(maxNum == 0)
      return unknownSamples;

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
        unknownSamples.add(CandidatePhrase.createOrGet(p.getValue(), null, p.getFeatures()));
      }
    }

    }else if(constVars.patternType.equals(PatternFactory.PatternType.SURFACE)){
      CoreLabel[] tokens = sent.getTokens().toArray(new CoreLabel[0]);
      for(int i =0; i < tokens.length; i++){

        if(random.nextDouble() < perSelect){

          int left = (int)((length -1) /2.0);
          int right = length -1 -left;
          String ph = "";
          boolean haspositive = false;
          for(int j = Math.max(0, i - left); j < tokens.length && j <= i+right; j++){
            if(tokens[j].get(positiveClass).equals(label)){
              haspositive = true;
              break;
            }
            ph += " " + tokens[j].word();
          }

          if(!haspositive && !ph.trim().isEmpty() && !constVars.getStopWords().contains(CandidatePhrase.createOrGet(ph))){
            unknownSamples.add(CandidatePhrase.createOrGet(ph.trim()));
          }
        }
      }

    } else
    throw new RuntimeException("not yet implemented");


    return unknownSamples;

  }

  public class chooseDatumsThread implements Callable {

    Collection<String> keys;
    Map<String, DataInstance> sents;
    Class answerClass;
    String answerLabel;
    boolean forLearningPattern;
    TwoDimensionalCounter<CandidatePhrase, E> wordsPatExtracted;
    Counter<E> allSelectedPatterns;

    public chooseDatumsThread(String label, Map<String, DataInstance> sents, Collection<String> keys, boolean forLearningPattern, TwoDimensionalCounter<CandidatePhrase, E> wordsPatExtracted, Counter<E> allSelectedPatterns){
      this.answerLabel = label;
      this.sents = sents;
      this.keys = keys;
      this.forLearningPattern = forLearningPattern;
      this.wordsPatExtracted = wordsPatExtracted;
      this.allSelectedPatterns = allSelectedPatterns;

      answerClass = constVars.getAnswerClass().get(answerLabel);
    }

    @Override
    public Triple<List<RVFDatum<String, ScorePhraseMeasures>>, List<CandidatePhrase>, List<CandidatePhrase>> call() throws Exception {

      Random r = new Random(10);
      Random rneg = new Random(10);
      List<RVFDatum<String, ScorePhraseMeasures>> datums = new ArrayList<RVFDatum<String, ScorePhraseMeasures>>();
      List<CandidatePhrase> allNegativePhrases = new ArrayList<CandidatePhrase>();
      List<CandidatePhrase> allUnknownPhrases = new ArrayList<CandidatePhrase>();

      Map<Class, Object> otherIgnoreClasses = constVars.getIgnoreWordswithClassesDuringSelection().get(answerLabel);
      for (String sentid : keys) {
        DataInstance sentInst = sents.get(sentid);
        List<CoreLabel> value = sentInst.getTokens();
        CoreLabel[] sent = value.toArray(new CoreLabel[value.size()]);

        for (int i = 0; i < sent.length; i++) {
          CoreLabel l = sent[i];

          if (l.get(answerClass).equals(answerLabel)) {
            CandidatePhrase candidate = l.get(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class).get(answerLabel);

            if (candidate == null) {
              throw new RuntimeException("for sentence id " + sentid + " and token id " + i + " candidate is null for " + l.word() + " and longest matching" + l.get(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class) + " and matched phrases are " + l.get(PatternsAnnotations.MatchedPhrases.class));
              //candidate = CandidatePhrase.createOrGet(l.word());
            }

            //If the phrase does not exist in its form in the datset (happens when fuzzy matching etc).
            if(!Data.rawFreq.containsKey(candidate)){
              candidate = CandidatePhrase.createOrGet(l.word());
            }


            Counter<ScorePhraseMeasures> feat = null;
            //CandidatePhrase candidate = new CandidatePhrase(l.word());
            if (forLearningPattern) {
              feat = getPhraseFeaturesForPattern(answerLabel, candidate);
            } else {
              feat = getFeatures(answerLabel, candidate, wordsPatExtracted.getCounter(candidate), allSelectedPatterns);
            }
            RVFDatum<String, ScorePhraseMeasures> datum = new RVFDatum<String, ScorePhraseMeasures>(feat, "true");
            datums.add(datum);

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
              //assert !lo.getValue().getPhrase().isEmpty() : "How is the longestmatching phrase for " + l.word() + " empty ";
              if (!lo.getKey().equals(answerLabel) && lo.getValue() != null) {
                negative = true;
                add = true;
                //If the phrase does not exist in its form in the datset (happens when fuzzy matching etc).
                if(!Data.rawFreq.containsKey(lo.getValue())){
                  candidate = CandidatePhrase.createOrGet(l.word());
                } else
                  candidate = lo.getValue();
              }
            }
            if (!negative && ignoreclass) {
              candidate = longestMatching.get("OTHERSEM");
              add = true;
            }
            if(add && rneg.nextDouble() < constVars.perSelectNeg){
              assert !candidate.getPhrase().isEmpty();
              allNegativePhrases.add(candidate);
            }
          }
        }

        allUnknownPhrases.addAll(chooseUnknownPhrases(sentInst, r, constVars.perSelectRand, constVars.getAnswerClass().get(answerLabel), answerLabel, Math.max(0, Integer.MAX_VALUE)));
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
    return new Triple(datums, allNegativePhrases, allUnknownPhrases);
    }
  }


  public RVFDataset<String, ScorePhraseMeasures> choosedatums(boolean forLearningPattern, String answerLabel,
      TwoDimensionalCounter<CandidatePhrase, E> wordsPatExtracted,
      Counter<E> allSelectedPatterns, boolean computeRawFreq) {


    RVFDataset<String, ScorePhraseMeasures> dataset = new RVFDataset<String, ScorePhraseMeasures>();
    int numpos = 0;
    List<CandidatePhrase> allNegativePhrases = new ArrayList<CandidatePhrase>();
    List<CandidatePhrase> allUnknownPhrases = new ArrayList<CandidatePhrase>();

    //for all sentences brtch
    ConstantsAndVariables.DataSentsIterator sentsIter = new ConstantsAndVariables.DataSentsIterator(constVars.batchProcessSents);
    while(sentsIter.hasNext()) {
      Pair<Map<String, DataInstance>, File> sentsf = sentsIter.next();
      Map<String, DataInstance> sents = sentsf.first();
      Redwood.log(Redwood.DBG, "Sampling datums from " + sentsf.second());
      if (computeRawFreq)
        Data.computeRawFreqIfNull(sents, PatternFactory.numWordsCompound);

      List<List<String>> threadedSentIds = GetPatternsFromDataMultiClass.getThreadBatches(new ArrayList<String>(sents.keySet()), constVars.numThreads);
      ExecutorService executor = Executors.newFixedThreadPool(constVars.numThreads);
      List<Future<Triple<List<RVFDatum<String, ScorePhraseMeasures>>, List<CandidatePhrase>, List<CandidatePhrase>>>> list = new ArrayList<Future<Triple<List<RVFDatum<String, ScorePhraseMeasures>>, List<CandidatePhrase>, List<CandidatePhrase>>>>();

      //multi-threaded choose positive, negative and unknown
      for (List<String> keys : threadedSentIds) {
        Callable<Triple<List<RVFDatum<String, ScorePhraseMeasures>>, List<CandidatePhrase>, List<CandidatePhrase>>> task = new chooseDatumsThread(answerLabel, sents, keys, forLearningPattern, wordsPatExtracted, allSelectedPatterns);
        Future<Triple<List<RVFDatum<String, ScorePhraseMeasures>>, List<CandidatePhrase>, List<CandidatePhrase>>> submit = executor.submit(task);
        list.add(submit);
      }

      // Now retrieve the result
      for (Future<Triple<List<RVFDatum<String, ScorePhraseMeasures>>, List<CandidatePhrase>, List<CandidatePhrase>>> future : list) {
        try {
          Triple<List<RVFDatum<String, ScorePhraseMeasures>>, List<CandidatePhrase>, List<CandidatePhrase>> result = future.get();
          List<RVFDatum<String, ScorePhraseMeasures>> posdatums = result.first();
          dataset.addAll(posdatums);
          numpos += posdatums.size();
          allNegativePhrases.addAll(result.second());
          allUnknownPhrases.addAll(result.third());
        } catch (Exception e) {
          executor.shutdownNow();
          throw new RuntimeException(e);
        }
      }
      executor.shutdown();
    }

    Redwood.log(Redwood.DBG, "Number of pure negative phrases is " + allNegativePhrases.size());
    Redwood.log(Redwood.DBG, "Number of unknown phrases is " + allUnknownPhrases.size());

    if(constVars.subsampleUnkAsNegUsingSim){
      Set<CandidatePhrase> chosenUnknown = chooseUnknownAsNegatives(allUnknownPhrases, answerLabel, constVars.subSampleUnkAsNegUsingSimPercentage);
      Redwood.log(Redwood.DBG, "Choosing " + chosenUnknown.size() + " unknowns as negative based to their similarity to the positive phrases");
      allNegativePhrases.addAll(chosenUnknown);
    }
    else{
        allNegativePhrases.addAll(allUnknownPhrases);
    }

    if(allNegativePhrases.size() > numpos) {
      Redwood.log(Redwood.WARN, "Num of negative (" + allNegativePhrases.size() + ") is higher than number of positive phrases (" + numpos + ") = " +
        (allNegativePhrases.size() / (double)numpos) + ". " +
        "Capping the number by taking the first numPositives as negative. Consider decreasing perSelectNeg and perSelectRand");
      allNegativePhrases = allNegativePhrases.subList(0, numpos);
    }

    for(CandidatePhrase negative: allNegativePhrases){
      Counter<ScorePhraseMeasures> feat;
      //CandidatePhrase candidate = new CandidatePhrase(l.word());
      if (forLearningPattern) {
        feat = getPhraseFeaturesForPattern(answerLabel, negative);
      } else {
        feat = getFeatures(answerLabel, negative, wordsPatExtracted.getCounter(negative), allSelectedPatterns);
      }
      RVFDatum<String, ScorePhraseMeasures> datum = new RVFDatum<String, ScorePhraseMeasures>(feat, "false");
      dataset.add(datum);
    }

    System.out.println("Before feature count threshold, dataset stats are ");
    dataset.summaryStatistics();

    int threshold = 2;
    dataset.applyFeatureCountThreshold(threshold);
    System.out.println("AFTER feature count threshold of " + threshold + ", dataset stats are ");
    dataset.summaryStatistics();

    Redwood.log(Redwood.DBG, "Eventually, number of positive datums:  " + numpos + " and number of negative datums: " + allNegativePhrases.size());
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
      Redwood.log(ConstantsAndVariables.extremedebug, "features are null for " + word);
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
    //System.out.println("scores for " + word + " are " + scoreslist);
    return scoreslist;
  }

}
