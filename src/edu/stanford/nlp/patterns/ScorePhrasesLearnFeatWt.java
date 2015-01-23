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
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.patterns.ConstantsAndVariables.ScorePhraseMeasures;
import edu.stanford.nlp.patterns.dep.DataInstanceDep;
import edu.stanford.nlp.patterns.dep.ExtractPhraseFromPattern;
import edu.stanford.nlp.patterns.dep.ExtractedPhrase;
import edu.stanford.nlp.semgraph.ISemanticGraphEdgeEql;
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

  static Map<String, double[]> wordVectors = null;

  public ScorePhrasesLearnFeatWt(ConstantsAndVariables constvar) {
    super(constvar);
    if(constvar.useWordVectorsToComputeSim && (constvar.subsampleUnkAsNegUsingSim|| constvar.expandPositivesWhenSampling) && wordVectors == null) {
      if(Data.rawFreq == null){
          Data.rawFreq = new ClassicCounter<CandidatePhrase>();
          Data.computeRawFreqIfNull(PatternFactory.numWordsCompound, constvar.batchProcessSents);
      }
      Redwood.log(Redwood.DBG, "Reading word vectors");
      wordVectors = new HashMap<String, double[]>();
      for (String line : IOUtils.readLines(constVars.wordVectorFile)) {
        String[] tok = line.split("\\s+");
        String word = tok[0];
        CandidatePhrase p = CandidatePhrase.createOrGet(word);

        //save the vector if it occurs in the rawFreq, seed set, stop words, english words
        if (Data.rawFreq.containsKey(p) || constvar.getStopWords().contains(p) || constvar.getEnglishWords().contains(word) || constvar.hasSeedWordOrOtherSem(p)) {
          double[] d = new double[tok.length - 1];
          for (int i = 1; i < tok.length; i++) {
            d[i - 1] = Double.valueOf(tok[i]);
          }
          wordVectors.put(word, d);
        } else
          CandidatePhrase.deletePhrase(p);
      }
      Redwood.log(Redwood.DBG, "Read " + wordVectors.size() + " word vectors");
    }
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

  ConcurrentHashMap<CandidatePhrase, Counter<Integer>> wordClassClustersForPhrase = new ConcurrentHashMap<CandidatePhrase, Counter<Integer>>();



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

  private Counter<CandidatePhrase> computeSimWithWordVectors(Collection<CandidatePhrase> candidatePhrases, Collection<CandidatePhrase> otherPhrases){
    Counter<CandidatePhrase> sims = new ClassicCounter<CandidatePhrase>(candidatePhrases.size());
    for(CandidatePhrase p : candidatePhrases) {
      if(wordVectors.containsKey(p.getPhrase())){
        double[] d1 = wordVectors.get(p.getPhrase());

        double avgSim = 0;// Double.MIN_VALUE;
        boolean donotuse = false;
        for (CandidatePhrase pos : otherPhrases) {

          if (p.equals(pos)) {
            donotuse = true;
            break;
          }
          if (!wordVectors.containsKey(pos.getPhrase()))
            continue;

          PhrasePair pair = new PhrasePair(p.getPhrase(), pos.getPhrase());
          if (cacheSimilarities.containsKey(pair))
            avgSim = cacheSimilarities.getCount(pair);
          else {
            double[] d2 = wordVectors.get(pos.getPhrase());

            double sum = 0;
            double d1sq = 0;
            double d2sq = 0;
            for (int i = 0; i < d1.length; i++) {
              sum += d1[i] * d2[i];
              d1sq += d1[i] * d1[i];
              d2sq += d2[i] * d2[i];
            }
            double sim = sum / (Math.sqrt(d1sq) * Math.sqrt(d2sq));
            avgSim += sim;
          }

          avgSim /= otherPhrases.size();
          cacheSimilarities.setCount(pair, avgSim);
        }
        if(!donotuse){
          sims.setCount(p, avgSim);
        }
      }else{
        sims.setCount(p, Double.MIN_VALUE);
      }
    }
    return sims;
  }

  private Counter<CandidatePhrase> computeSimWithWordVectors(List<CandidatePhrase> candidatePhrases, Collection<CandidatePhrase> positivePhrases, Collection<CandidatePhrase> allPossibleNegativePhrases, AtomicDouble allMaxSim) {
    //TODO: check this
    assert wordVectors != null : "Why are word vectors null?";
    Counter<CandidatePhrase> posSims = computeSimWithWordVectors(candidatePhrases, positivePhrases);
    Redwood.log(Redwood.DBG, "Computed similarities with positive phrases");
    Counter<CandidatePhrase> negSims = computeSimWithWordVectors(posSims.keySet(), allPossibleNegativePhrases);
    Redwood.log(Redwood.DBG, "Computed similarties with negative phrases");
    Function<CandidatePhrase, Boolean> retainPhrasesNotCloseToNegative = candidatePhrase -> {
      if(negSims.getCount(candidatePhrase) > posSims.getCount(candidatePhrase))
        return false;
      else
        return true;
    };
    Counters.retainKeys(posSims, retainPhrasesNotCloseToNegative);
    return posSims;
  }

  Counter<CandidatePhrase> computeSimWithWordCluster(Collection<CandidatePhrase> candidatePhrases, Collection<CandidatePhrase> positivePhrases, AtomicDouble allMaxSim){

    Counter<CandidatePhrase> sims = new ClassicCounter<CandidatePhrase>(candidatePhrases.size());

    for(CandidatePhrase p : candidatePhrases) {
      Counter<Integer> feat = wordClassClustersForPhrase.get(p);
      if(feat == null){
        feat = wordClass(p.getPhrase(), p.getPhraseLemma());
        wordClassClustersForPhrase.put(p, feat);
      }

      double avgSim = 0;// Double.MIN_VALUE;
      if(feat.size() > 0) {
        for (CandidatePhrase pos : positivePhrases) {

          if(p.equals(pos))
            continue;

          Counter<Integer> posfeat = wordClassClustersForPhrase.get(pos);

          if(posfeat == null){
            posfeat = wordClass(pos.getPhrase(), pos.getPhraseLemma());
            wordClassClustersForPhrase.put(pos, feat);
          }

          if(posfeat.size() > 0){
            Double j = Counters.jaccardCoefficient(posfeat, feat);
            //System.out.println("clusters for positive phrase " + pos + " is " +wordClassClustersForPhrase.get(pos) + " and the features for unknown are "  + feat + " for phrase " + p);
            if(!j.isInfinite() && !j.isNaN()){
              avgSim += j;
            }
            //if (j > maxSim)
            //  maxSim = j;
          }
        }
        avgSim /= positivePhrases.size();
      }

      sims.setCount(p, avgSim);
      if(allMaxSim.get() < avgSim)
        allMaxSim.set(avgSim);
    }
    return sims;
  }

  class ComputeSim implements Callable<Counter<CandidatePhrase>>{

    List<CandidatePhrase> candidatePhrases;
    String label;
    AtomicDouble allMaxSim;
    Collection<CandidatePhrase> positivePhrases;
    Collection<CandidatePhrase> knownNegativePhrases;

    public ComputeSim(String label, List<CandidatePhrase> candidatePhrases, AtomicDouble allMaxSim, Collection<CandidatePhrase> positivePhrases, Collection<CandidatePhrase> knownNegativePhrases){
      this.label = label;
      this.candidatePhrases = candidatePhrases;
      this.allMaxSim = allMaxSim;
      this.positivePhrases = positivePhrases;
      this.knownNegativePhrases = knownNegativePhrases;
    }

    @Override
    public Counter<CandidatePhrase> call() throws Exception {

      if(constVars.useWordVectorsToComputeSim)
        return computeSimWithWordVectors(candidatePhrases, positivePhrases, knownNegativePhrases, allMaxSim);
      else
      //TODO: knownnegaitvephrases
        return computeSimWithWordCluster(candidatePhrases, positivePhrases, allMaxSim);
    }
  }



  //this chooses the ones that are not close to the positive phrases!
  Set<CandidatePhrase> chooseUnknownAsNegatives(Set<CandidatePhrase> candidatePhrases, String label, double percentage, Collection<CandidatePhrase> positivePhrases, Collection<CandidatePhrase> knownNegativePhrases, BufferedWriter logFile) throws IOException {

    List<List<CandidatePhrase>> threadedCandidates = GetPatternsFromDataMultiClass.getThreadBatches(CollectionUtils.toList(candidatePhrases), constVars.numThreads);

    Counter<CandidatePhrase> sims = new ClassicCounter<CandidatePhrase>();

    AtomicDouble allMaxSim = new AtomicDouble(Double.MIN_VALUE);

    ExecutorService executor = Executors.newFixedThreadPool(constVars.numThreads);
    List<Future<Counter<CandidatePhrase>>> list = new ArrayList<Future<Counter<CandidatePhrase>>>();

    //multi-threaded choose positive, negative and unknown
    for (List<CandidatePhrase> keys : threadedCandidates) {
      Callable<Counter<CandidatePhrase>> task = new ComputeSim(label, keys, allMaxSim, positivePhrases, knownNegativePhrases);
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

    CandidatePhrase k = Counters.argmax(sims);
    System.out.println("Maximum similarity was " + sims.getCount(k) + " for word " + k);

    Counter<CandidatePhrase> removed = Counters.retainBelow(sims, constVars.positiveSimilarityThresholdLowPrecision);
    System.out.println("removing phrases as negative phrases that were higher that positive similarity threshold of " + constVars.positiveSimilarityThresholdLowPrecision + removed);
    if(logFile != null){
      for(Entry<CandidatePhrase, Double> en: removed.entrySet())
        if(wordVectors.containsKey(en.getKey().getPhrase()))
          logFile.write(en.getKey()+"-PN " + ArrayUtils.toString(wordVectors.get(en.getKey().getPhrase()), " ")+"\n");
    }
    //Collection<CandidatePhrase> removed = Counters.retainBottom(sims, (int) (sims.size() * percentage));
    //System.out.println("not choosing " + removed + " as the negative phrases. percentage is " + percentage + " and allMaxsim was " + allMaxSim);
    return sims.keySet();
  }



  Set<CandidatePhrase> chooseUnknownPhrases(DataInstance sent, Random random, double perSelect, Class positiveClass, String label, int maxNum){

    Set<CandidatePhrase> unknownSamples = new HashSet<CandidatePhrase>();

    if(maxNum == 0)
      return unknownSamples;

    Function<CoreLabel, Boolean> acceptWord = coreLabel -> {
      if(coreLabel.get(positiveClass).equals(label) || constVars.functionWords.contains(coreLabel.word()))
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
          ph = ph.trim();
          if(!haspositive && !ph.trim().isEmpty() && !constVars.functionWords.contains(ph)){
            unknownSamples.add(CandidatePhrase.createOrGet(ph));
          }
        }
      }

    } else
    throw new RuntimeException("not yet implemented");


    return unknownSamples;

  }

  public class ChooseDatumsThread implements Callable {

    Collection<String> keys;
    Map<String, DataInstance> sents;
    Class answerClass;
    String answerLabel;
    boolean forLearningPattern;
    TwoDimensionalCounter<CandidatePhrase, E> wordsPatExtracted;
    Counter<E> allSelectedPatterns;
    Counter<Integer> wordClassClustersOfPositive;
    Collection<CandidatePhrase> allPossibleNegativePhrases;

    public ChooseDatumsThread(String label, Map<String, DataInstance> sents, Collection<String> keys, boolean forLearningPattern, TwoDimensionalCounter<CandidatePhrase, E> wordsPatExtracted, Counter<E> allSelectedPatterns, Counter<Integer> wordClassClustersOfPositive, Collection<CandidatePhrase> allPossibleNegativePhrases){
      this.answerLabel = label;
      this.sents = sents;
      this.keys = keys;
      this.forLearningPattern = forLearningPattern;
      this.wordsPatExtracted = wordsPatExtracted;
      this.allSelectedPatterns = allSelectedPatterns;
      this.wordClassClustersOfPositive = wordClassClustersOfPositive;
      this.allPossibleNegativePhrases = allPossibleNegativePhrases;
      answerClass = constVars.getAnswerClass().get(answerLabel);
    }

    @Override
    public Quadruple<Set<CandidatePhrase>, Set<CandidatePhrase>, Set<CandidatePhrase>, Counter<CandidatePhrase>> call() throws Exception {

      Random r = new Random(10);
      Random rneg = new Random(10);
      Set<CandidatePhrase> allPositivePhrases = new HashSet<CandidatePhrase>();
      Set<CandidatePhrase> allNegativePhrases = new HashSet<CandidatePhrase>();
      Set<CandidatePhrase> allUnknownPhrases = new HashSet<CandidatePhrase>();
      Counter<CandidatePhrase> allCloseToPositivePhrases = new ClassicCounter<CandidatePhrase>();

      Set<CandidatePhrase> knownPositivePhrases = CollectionUtils.unionAsSet(constVars.getLearnedWords().get(answerLabel).keySet(), constVars.getSeedLabelDictionary().get(answerLabel));

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

            allPositivePhrases.add(candidate);

          } else {

            Map<String, CandidatePhrase> longestMatching = l.get(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class);

            boolean ignoreclass = false;
            CandidatePhrase candidate =  CandidatePhrase.createOrGet(l.word());

            for (Class cl : otherIgnoreClasses.keySet()) {
              if ((Boolean) l.get(cl)) {
                ignoreclass = true;
                candidate = longestMatching.containsKey("OTHERSEM")? longestMatching.get("OTHERSEM") : candidate;
                break;
              }
            }

            if(!ignoreclass) {
              ignoreclass = constVars.functionWords.contains(l.word());
            }


            boolean negative = false;
            boolean add= false;
            for (Map.Entry<String, CandidatePhrase> lo : longestMatching.entrySet()) {
              //assert !lo.getValue().getPhrase().isEmpty() : "How is the longestmatching phrase for " + l.word() + " empty ";
              if (!lo.getKey().equals(answerLabel) && lo.getValue() != null) {
                negative = true;
                add = true;
                //If the phrase does not exist in its form in the datset (happens when fuzzy matching etc).
                if(Data.rawFreq.containsKey(lo.getValue())){
                  candidate = lo.getValue();
                }
              }
            }


            if (!negative && ignoreclass) {
              add = true;
            }

            if(add && rneg.nextDouble() < constVars.perSelectNeg){
              assert !candidate.getPhrase().isEmpty();
              allNegativePhrases.add(candidate);
            }

            if(!negative && !ignoreclass && constVars.expandPositivesWhenSampling) {
              if (!allCloseToPositivePhrases.containsKey(candidate)) {
                Counter<CandidatePhrase> sims;
                assert candidate != null;
                if(constVars.useWordVectorsToComputeSim)
                  sims =computeSimWithWordVectors(Arrays.asList(candidate), knownPositivePhrases, allPossibleNegativePhrases, new AtomicDouble());
                else
                  sims = computeSimWithWordCluster(Arrays.asList(candidate), knownPositivePhrases, new AtomicDouble());

                double sim = sims.getCount(candidate);
                if (sim > constVars.positiveSimilarityThresholdHighPrecision)
                  allCloseToPositivePhrases.setCount(candidate, sim);
              }
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
    return new Quadruple(allPositivePhrases, allNegativePhrases, allUnknownPhrases, allCloseToPositivePhrases);
    }
  }

  static private class PhrasePair{
    final String p1;
    final String p2;
    final int hashCode;

    public PhrasePair(String p1, String p2) {
      if(p1.compareTo(p2) <=0)
      {
        this.p1 = p1;
        this.p2 = p2;
      }else
      {
        this.p1 = p2;
        this.p2 = p1;
      }

      this.hashCode = p1.hashCode() + p2.hashCode() + 331;
    }

    @Override
    public int hashCode(){
      return hashCode;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof PhrasePair))
        return false;
      PhrasePair p = (PhrasePair) o;
      if (p.getPhrase1().equals(this.getPhrase1()) && p.getPhrase2().equals(this.getPhrase2()))
        return true;
      return false;
    }

    public String getPhrase1() {
      return p1;
    }


    public String getPhrase2() {
      return p2;
    }
  }

  static Counter<PhrasePair> cacheSimilarities = new ConcurrentHashCounter<PhrasePair>();

  public RVFDataset<String, ScorePhraseMeasures> choosedatums(boolean forLearningPattern, String answerLabel,
      TwoDimensionalCounter<CandidatePhrase, E> wordsPatExtracted,
      Counter<E> allSelectedPatterns, boolean computeRawFreq) throws IOException {

    Counter<Integer> distSimClustersOfPositive = new ClassicCounter<Integer>();
    if(constVars.expandPositivesWhenSampling && !constVars.useWordVectorsToComputeSim){
      for(CandidatePhrase s: CollectionUtils.union(constVars.getLearnedWords(answerLabel).keySet(), constVars.getSeedLabelDictionary().get(answerLabel))){
        String[] toks = s.getPhrase().split("\\s+");
        if(!constVars.getWordClassClusters().containsKey(s.getPhrase())){
          for(String tok: toks){
            if(constVars.getWordClassClusters().containsKey(tok)){
              distSimClustersOfPositive.incrementCount(constVars.getWordClassClusters().get(tok));
            }
          }
        } else
        distSimClustersOfPositive.incrementCount(constVars.getWordClassClusters().get(s.getPhrase()));
      }
    }

    Collection<CandidatePhrase> allPossibleNegativePhrases = null;

    if(constVars.expandPositivesWhenSampling || constVars.subsampleUnkAsNegUsingSim){
      allPossibleNegativePhrases = new HashSet<CandidatePhrase>();
      allPossibleNegativePhrases.addAll(constVars.getOtherSemanticClassesWords());
      allPossibleNegativePhrases.addAll(constVars.getStopWords());
      allPossibleNegativePhrases.addAll(CandidatePhrase.convertStringPhrases(constVars.getEnglishWords()));
      for(Entry<String, Counter<CandidatePhrase>> en: constVars.getLearnedWords().entrySet()) {
        if (!en.getKey().equals(answerLabel)){
          allPossibleNegativePhrases.addAll(en.getValue().keySet());
          allPossibleNegativePhrases.addAll(constVars.getSeedLabelDictionary().get(en.getKey()));
        }
      }
    }

    RVFDataset<String, ScorePhraseMeasures> dataset = new RVFDataset<String, ScorePhraseMeasures>();
    int numpos = 0;
    Set<CandidatePhrase> allNegativePhrases = new HashSet<CandidatePhrase>();
    Set<CandidatePhrase> allUnknownPhrases = new HashSet<CandidatePhrase>();
    Set<CandidatePhrase> allPositivePhrases = new HashSet<CandidatePhrase>();
    Counter<CandidatePhrase> allCloseToPositivePhrases = new ClassicCounter<CandidatePhrase>();

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
      List<Future<Quadruple<Set<CandidatePhrase>, Set<CandidatePhrase>, Set<CandidatePhrase>, Counter<CandidatePhrase>>>> list = new ArrayList<Future<Quadruple<Set<CandidatePhrase>, Set<CandidatePhrase>, Set<CandidatePhrase>, Counter<CandidatePhrase>>>>();

      //multi-threaded choose positive, negative and unknown
      for (List<String> keys : threadedSentIds) {
        Callable<Quadruple<Set<CandidatePhrase>, Set<CandidatePhrase>, Set<CandidatePhrase>, Counter<CandidatePhrase>>> task = new ChooseDatumsThread(answerLabel, sents, keys, forLearningPattern, wordsPatExtracted, allSelectedPatterns, distSimClustersOfPositive, allPossibleNegativePhrases);
        Future<Quadruple<Set<CandidatePhrase>, Set<CandidatePhrase>, Set<CandidatePhrase>, Counter<CandidatePhrase>>> submit = executor.submit(task);
        list.add(submit);
      }

      // Now retrieve the result
      for (Future<Quadruple<Set<CandidatePhrase>, Set<CandidatePhrase>, Set<CandidatePhrase>, Counter<CandidatePhrase>>> future : list) {
        try {
          Quadruple<Set<CandidatePhrase>, Set<CandidatePhrase>, Set<CandidatePhrase>, Counter<CandidatePhrase>> result = future.get();
          allPositivePhrases.addAll(result.first());
          allNegativePhrases.addAll(result.second());
          allUnknownPhrases.addAll(result.third());
          allCloseToPositivePhrases.addAll(result.fourth());
        } catch (Exception e) {
          executor.shutdownNow();
          throw new RuntimeException(e);
        }
      }
      executor.shutdown();
    }

    //Set<CandidatePhrase> knownPositivePhrases = CollectionUtils.unionAsSet(constVars.getLearnedWords().get(answerLabel).keySet(), constVars.getSeedLabelDictionary().get(answerLabel));
    //TODO: this is kinda not nice; how is allpositivephrases different from positivephrases again?
    allPositivePhrases.addAll(constVars.getLearnedWords().get(answerLabel).keySet());
    //allPositivePhrases.addAll(knownPositivePhrases);

    if(constVars.expandPositivesWhenSampling){
      Counters.retainTop(allCloseToPositivePhrases, (int) (allCloseToPositivePhrases.size()*constVars.subSampleUnkAsPosUsingSimPercentage));
      Redwood.log("Expanding positives by adding " + allCloseToPositivePhrases + " phrases");
      allPositivePhrases.addAll(allCloseToPositivePhrases.keySet());
    }

    BufferedWriter logFile = null;
    if(constVars.logFileVectorSimilarity != null)
      logFile = new BufferedWriter(new FileWriter(constVars.logFileVectorSimilarity));

    System.out.println("all positive phrases are  " + allPositivePhrases);
    for(CandidatePhrase candidate: allPositivePhrases) {
      Counter<ScorePhraseMeasures> feat = null;
      //CandidatePhrase candidate = new CandidatePhrase(l.word());
      if (forLearningPattern) {
        feat = getPhraseFeaturesForPattern(answerLabel, candidate);
      } else {
        feat = getFeatures(answerLabel, candidate, wordsPatExtracted.getCounter(candidate), allSelectedPatterns);
      }
      RVFDatum<String, ScorePhraseMeasures> datum = new RVFDatum<String, ScorePhraseMeasures>(feat, "true");
      dataset.add(datum);
      numpos += 1;
      if(logFile!=null && wordVectors.containsKey(candidate.getPhrase())){
        logFile.write(candidate.getPhrase()+"-P"+" " + ArrayUtils.toString(wordVectors.get(candidate.getPhrase()), " ")+"\n");
      }
    }

    Redwood.log(Redwood.DBG, "Number of pure negative phrases is " + allNegativePhrases.size());
    Redwood.log(Redwood.DBG, "Number of unknown phrases is " + allUnknownPhrases.size());

    if(constVars.subsampleUnkAsNegUsingSim){
      Set<CandidatePhrase> chosenUnknown = chooseUnknownAsNegatives(allUnknownPhrases, answerLabel, constVars.subSampleUnkAsNegUsingSimPercentage, allPositivePhrases, allPossibleNegativePhrases, logFile);
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
      int i = 0;
      Set<CandidatePhrase> selectedNegPhrases = new HashSet<CandidatePhrase>();
      for(CandidatePhrase p : allNegativePhrases){
        if (i >= numpos)
          break;
        selectedNegPhrases.add(p);
      }
      allNegativePhrases.clear();
      allNegativePhrases = selectedNegPhrases;
    }

    System.out.println("all negative phrases are " + allNegativePhrases);
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
      if(logFile!=null && wordVectors.containsKey(negative.getPhrase())){
        logFile.write(negative.getPhrase()+"-N"+" " + ArrayUtils.toString(wordVectors.get(negative.getPhrase()), " ")+"\n");
      }
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
