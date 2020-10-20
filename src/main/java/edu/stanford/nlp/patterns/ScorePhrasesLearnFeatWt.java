package edu.stanford.nlp.patterns;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.patterns.ConstantsAndVariables.ScorePhraseMeasures;
import edu.stanford.nlp.patterns.dep.DataInstanceDep;
import edu.stanford.nlp.patterns.dep.ExtractPhraseFromPattern;
import edu.stanford.nlp.patterns.dep.ExtractedPhrase;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.ArgumentParser.Option;
import edu.stanford.nlp.util.concurrent.AtomicDouble;
import edu.stanford.nlp.util.concurrent.ConcurrentHashCounter;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * Learn a logistic regression classifier to combine weights to score a phrase.
 *
 * @author Sonal Gupta (sonalg@stanford.edu)
 *
 */
public class ScorePhrasesLearnFeatWt<E extends Pattern> extends PhraseScorer<E> {

  @Option(name = "scoreClassifierType")
  private ClassifierType scoreClassifierType = ClassifierType.LR;


  private static Map<String, double[]> wordVectors = null;

  public ScorePhrasesLearnFeatWt(ConstantsAndVariables constvar) {
    super(constvar);
    if(constvar.useWordVectorsToComputeSim && (constvar.subsampleUnkAsNegUsingSim|| constvar.expandPositivesWhenSampling || constvar.expandNegativesWhenSampling || constVars.usePhraseEvalWordVector) && wordVectors == null) {
      if(Data.rawFreq == null){
          Data.rawFreq = new ClassicCounter<>();
          Data.computeRawFreqIfNull(PatternFactory.numWordsCompoundMax, constvar.batchProcessSents);
      }
      Redwood.log(Redwood.DBG, "Reading word vectors");
      wordVectors = new HashMap<>();
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
    OOVExternalFeatWt = 0;
    OOVdictOdds = 0;
    OOVDomainNgramScore = 0;
    OOVGoogleNgramScore = 0;
  }


  public enum ClassifierType {
    DT, LR, RF, SVM, SHIFTLR, LINEAR
  }

  public TwoDimensionalCounter<CandidatePhrase, ScorePhraseMeasures> phraseScoresRaw = new TwoDimensionalCounter<>();


  public edu.stanford.nlp.classify.Classifier learnClassifier(String label, boolean forLearningPatterns,
      TwoDimensionalCounter<CandidatePhrase, E> wordsPatExtracted, Counter<E> allSelectedPatterns) throws IOException, ClassNotFoundException {
    phraseScoresRaw.clear();
    learnedScores.clear();

    if(Data.domainNGramsFile != null)
      Data.loadDomainNGrams();

    boolean computeRawFreq = false;
    if (Data.rawFreq == null) {
      Data.rawFreq = new ClassicCounter<>();
      computeRawFreq = true;
    }

    GeneralDataset<String, ScorePhraseMeasures> dataset = choosedatums(forLearningPatterns, label, wordsPatExtracted, allSelectedPatterns, computeRawFreq);

    edu.stanford.nlp.classify.Classifier classifier;

    if (scoreClassifierType.equals(ClassifierType.LR)) {
      LogisticClassifierFactory<String, ScorePhraseMeasures> logfactory = new LogisticClassifierFactory<>();
      LogPrior lprior = new LogPrior();
      lprior.setSigma(constVars.LRSigma);
      classifier = logfactory.trainClassifier(dataset, lprior, false);
      LogisticClassifier logcl = ((LogisticClassifier) classifier);

      String l = (String) logcl.getLabelForInternalPositiveClass();
      Counter<String> weights = logcl.weightsAsCounter();
      if (l.equals(Boolean.FALSE.toString())) {
        Counters.multiplyInPlace(weights, -1);
      }
      List<Pair<String, Double>> wtd = Counters.toDescendingMagnitudeSortedListWithCounts(weights);
      Redwood.log(ConstantsAndVariables.minimaldebug, "The weights are " + StringUtils.join(wtd.subList(0, Math.min(wtd.size(), 600)), "\n"));
    } else if(scoreClassifierType.equals(ClassifierType.SVM)){
      SVMLightClassifierFactory<String, ScorePhraseMeasures> svmcf = new SVMLightClassifierFactory<>(true);
      classifier = svmcf.trainClassifier(dataset);
      Set<String> labels = Generics.newHashSet(Arrays.asList("true"));
      List<Triple<ScorePhraseMeasures, String, Double>> topfeatures = ((SVMLightClassifier<String, ScorePhraseMeasures>) classifier).getTopFeatures(labels, 0, true, 600, true);
      Redwood.log(ConstantsAndVariables.minimaldebug, "The weights are " + StringUtils.join(topfeatures, "\n"));
    }else if(scoreClassifierType.equals(ClassifierType.SHIFTLR)){

      //change the dataset to basic dataset because currently ShiftParamsLR doesn't support RVFDatum
      GeneralDataset<String, ScorePhraseMeasures> newdataset = new Dataset<>();
      Iterator<RVFDatum<String, ScorePhraseMeasures>> iter = dataset.iterator();
      while(iter.hasNext()){
        RVFDatum<String, ScorePhraseMeasures> inst = iter.next();
        newdataset.add(new BasicDatum<>(inst.asFeatures(), inst.label()));
      }
      ShiftParamsLogisticClassifierFactory<String, ScorePhraseMeasures> factory = new ShiftParamsLogisticClassifierFactory<>();
      classifier =  factory.trainClassifier(newdataset);

      //print weights
      MultinomialLogisticClassifier<String, ScorePhraseMeasures> logcl = ((MultinomialLogisticClassifier) classifier);
      Counter<ScorePhraseMeasures> weights = logcl.weightsAsGenericCounter().get("true");

      List<Pair<ScorePhraseMeasures, Double>> wtd = Counters.toDescendingMagnitudeSortedListWithCounts(weights);
      Redwood.log(ConstantsAndVariables.minimaldebug, "The weights are " + StringUtils.join(wtd.subList(0, Math.min(wtd.size(), 600)), "\n"));

    } else if(scoreClassifierType.equals(ClassifierType.LINEAR)){
      LinearClassifierFactory<String, ScorePhraseMeasures> lcf = new LinearClassifierFactory<>();
      classifier = lcf.trainClassifier(dataset);
      Set<String> labels = Generics.newHashSet(Arrays.asList("true"));
      List<Triple<ScorePhraseMeasures, String, Double>> topfeatures = ((LinearClassifier<String, ScorePhraseMeasures>) classifier).getTopFeatures(labels, 0, true, 600, true);
      Redwood.log(ConstantsAndVariables.minimaldebug, "The weights are " + StringUtils.join(topfeatures, "\n"));
    }else
      throw new RuntimeException("cannot identify classifier " + scoreClassifierType);

//    else if (scoreClassifierType.equals(ClassifierType.RF)) {
//      ClassifierFactory wekaFactory = new WekaDatumClassifierFactory<String, ScorePhraseMeasures>("weka.classifiers.trees.RandomForest", constVars.wekaOptions);
//      classifier = wekaFactory.trainClassifier(dataset);
//      Classifier cls = ((WekaDatumClassifier) classifier).getClassifier();
//      RandomForest rf = (RandomForest) cls;
//    }

    BufferedWriter w = new BufferedWriter(new FileWriter("tempscorestrainer.txt"));
    System.out.println("size of learned scores is " + phraseScoresRaw.size());
    for (CandidatePhrase s : phraseScoresRaw.firstKeySet()) {
      w.write(s + "\t" + phraseScoresRaw.getCounter(s) + "\n");
    }
    w.close();

    return classifier;

  }

  @Override
  public void printReasonForChoosing(Counter<CandidatePhrase> phrases){
    Redwood.log(Redwood.DBG, "Features of selected phrases");
    for(Entry<CandidatePhrase, Double> pEn: phrases.entrySet())
      Redwood.log(Redwood.DBG, pEn.getKey().getPhrase() + "\t" + pEn.getValue() + "\t" +  phraseScoresRaw.getCounter(pEn.getKey()));
  }

  @Override
  public Counter<CandidatePhrase> scorePhrases(String label, TwoDimensionalCounter<CandidatePhrase, E> terms,
      TwoDimensionalCounter<CandidatePhrase, E> wordsPatExtracted, Counter<E> allSelectedPatterns,
      Set<CandidatePhrase> alreadyIdentifiedWords, boolean forLearningPatterns) throws IOException, ClassNotFoundException {
    getAllLabeledWordsCluster();
    Counter<CandidatePhrase> scores = new ClassicCounter<>();
    edu.stanford.nlp.classify.Classifier classifier = learnClassifier(label, forLearningPatterns, wordsPatExtracted, allSelectedPatterns);
    for (Entry<CandidatePhrase, ClassicCounter<E>> en : terms.entrySet()) {
      Double score = this.scoreUsingClassifer(classifier, en.getKey(), label, forLearningPatterns, en.getValue(), allSelectedPatterns);
      if(!score.isNaN() && !score.isInfinite()){
        scores.setCount(en.getKey(), score);
      }else
       Redwood.log(Redwood.DBG, "Ignoring " + en.getKey() + " because score is " + score);
    }
    return scores;
  }

  @Override
  public Counter<CandidatePhrase> scorePhrases(String label, Set<CandidatePhrase> terms, boolean forLearningPatterns) throws IOException, ClassNotFoundException {
    getAllLabeledWordsCluster();
    Counter<CandidatePhrase> scores = new ClassicCounter<>();
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

  ConcurrentHashMap<CandidatePhrase, Counter<Integer>> wordClassClustersForPhrase = new ConcurrentHashMap<>();



  Counter<Integer> wordClass(String phrase, String phraseLemma){
    Counter<Integer> cl = new ClassicCounter<>();
    String[] phl = null;
    if(phraseLemma!=null)
      phl = phraseLemma.split("\\s+");
    int i =0;
    for(String w: phrase.split("\\s+")) {

      Integer cluster = constVars.getWordClassClusters().get(w);
      if (cluster == null && phl!=null)
          cluster = constVars.getWordClassClusters().get(phl[i]);

      //try lowercase
      if(cluster == null){
        cluster = constVars.getWordClassClusters().get(w.toLowerCase());
        if (cluster == null && phl!=null)
          cluster = constVars.getWordClassClusters().get(phl[i].toLowerCase());
      }


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

  private Counter<CandidatePhrase> computeSimWithWordVectors(Collection<CandidatePhrase> candidatePhrases, Collection<CandidatePhrase> otherPhrases, boolean ignoreWordRegex, String label){
    Counter<CandidatePhrase> sims = new ClassicCounter<>(candidatePhrases.size());
    for(CandidatePhrase p : candidatePhrases) {

      Map<String, double[]> simsAvgMaxAllLabels = similaritiesWithLabeledPhrases.get(p.getPhrase());
      if(simsAvgMaxAllLabels == null)
        simsAvgMaxAllLabels = new HashMap<>();
      double[] simsAvgMax = simsAvgMaxAllLabels.get(label);
      if (simsAvgMax == null) {
        simsAvgMax = new double[Similarities.values().length];
        // Arrays.fill(simsAvgMax, 0); // not needed; Java arrays zero initialized
      }

      if(wordVectors.containsKey(p.getPhrase()) && (! ignoreWordRegex || !PatternFactory.ignoreWordRegex.matcher(p.getPhrase()).matches())){

        double[] d1 = wordVectors.get(p.getPhrase());

        BinaryHeapPriorityQueue<CandidatePhrase> topSimPhs = new BinaryHeapPriorityQueue<>(constVars.expandPhrasesNumTopSimilar);
        double allsum = 0;
        double max = Double.MIN_VALUE;

        boolean donotuse = false;

        for (CandidatePhrase other : otherPhrases) {

          if (p.equals(other)) {
            donotuse = true;
            break;
          }

          if (!wordVectors.containsKey(other.getPhrase()))
            continue;

          double sim;

          PhrasePair pair = new PhrasePair(p.getPhrase(), other.getPhrase());
          if (cacheSimilarities.containsKey(pair))
            sim = cacheSimilarities.getCount(pair);
          else {
            double[] d2 = wordVectors.get(other.getPhrase());

            double sum = 0;
            double d1sq = 0;
            double d2sq = 0;
            for (int i = 0; i < d1.length; i++) {
              sum += d1[i] * d2[i];
              d1sq += d1[i] * d1[i];
              d2sq += d2[i] * d2[i];
            }
            sim = sum / (Math.sqrt(d1sq) * Math.sqrt(d2sq));
            cacheSimilarities.setCount(pair, sim);
          }

          topSimPhs.add(other, sim);
          if(topSimPhs.size() > constVars.expandPhrasesNumTopSimilar)
            topSimPhs.removeLastEntry();

          //avgSim /= otherPhrases.size();
          allsum += sim;
          if(sim > max)
            max = sim;
        }

        double finalSimScore = 0;
        int numEl = 0;
        while(topSimPhs.hasNext()) {
          finalSimScore += topSimPhs.getPriority();
          topSimPhs.next();
          numEl++;
        }
        finalSimScore /= numEl;

        double prevNumItems = simsAvgMax[Similarities.NUMITEMS.ordinal()];
        double prevAvg = simsAvgMax[Similarities.AVGSIM.ordinal()];
        double prevMax = simsAvgMax[Similarities.MAXSIM.ordinal()];
        double newNumItems = prevNumItems + otherPhrases.size();
        double newAvg = (prevAvg*prevNumItems + allsum) /(newNumItems);
        double newMax = prevMax > max ? prevMax: max;
        simsAvgMax[Similarities.NUMITEMS.ordinal()] = newNumItems;
        simsAvgMax[Similarities.AVGSIM.ordinal()] = newAvg;
        simsAvgMax[Similarities.MAXSIM.ordinal()] = newMax;

        if(!donotuse){
          sims.setCount(p, finalSimScore);
        }
      }else{
        sims.setCount(p, Double.MIN_VALUE);
      }
      simsAvgMaxAllLabels.put(label, simsAvgMax);
      similaritiesWithLabeledPhrases.put(p.getPhrase(), simsAvgMaxAllLabels);
    }
    return sims;
  }

  private Pair<Counter<CandidatePhrase>, Counter<CandidatePhrase>> computeSimWithWordVectors(List<CandidatePhrase> candidatePhrases, Collection<CandidatePhrase> positivePhrases,
                                                                                             Map<String, Collection<CandidatePhrase>> allPossibleNegativePhrases, String label) {
    assert wordVectors != null : "Why are word vectors null?";
    Counter<CandidatePhrase> posSims = computeSimWithWordVectors(candidatePhrases, positivePhrases, true, label);
    Counter<CandidatePhrase> negSims = new ClassicCounter<>();

    for(Map.Entry<String, Collection<CandidatePhrase>> en: allPossibleNegativePhrases.entrySet())
      negSims.addAll(computeSimWithWordVectors(candidatePhrases, en.getValue(), true, en.getKey()));

    Predicate<CandidatePhrase> retainPhrasesNotCloseToNegative = candidatePhrase -> {
      if(negSims.getCount(candidatePhrase) > posSims.getCount(candidatePhrase))
        return false;
      else
        return true;
    };
    Counters.retainKeys(posSims, retainPhrasesNotCloseToNegative);
    return new Pair(posSims, negSims);
  }

  Pair<Counter<CandidatePhrase>, Counter<CandidatePhrase>> computeSimWithWordCluster(Collection<CandidatePhrase> candidatePhrases, Collection<CandidatePhrase> positivePhrases, AtomicDouble allMaxSim){

    Counter<CandidatePhrase> sims = new ClassicCounter<>(candidatePhrases.size());

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
    //TODO: compute similarity with neg phrases
    return new Pair(sims, null);
  }

  class ComputeSim implements Callable<Pair<Counter<CandidatePhrase>, Counter<CandidatePhrase>>>{

    List<CandidatePhrase> candidatePhrases;
    String label;
    AtomicDouble allMaxSim;
    Collection<CandidatePhrase> positivePhrases;
    Map<String, Collection<CandidatePhrase>> knownNegativePhrases;

    public ComputeSim(String label, List<CandidatePhrase> candidatePhrases, AtomicDouble allMaxSim, Collection<CandidatePhrase> positivePhrases, Map<String, Collection<CandidatePhrase>> knownNegativePhrases){
      this.label = label;
      this.candidatePhrases = candidatePhrases;
      this.allMaxSim = allMaxSim;
      this.positivePhrases = positivePhrases;
      this.knownNegativePhrases = knownNegativePhrases;
    }

    @Override
    public Pair<Counter<CandidatePhrase>, Counter<CandidatePhrase>> call() throws Exception {

      if(constVars.useWordVectorsToComputeSim){
        Pair<Counter<CandidatePhrase>, Counter<CandidatePhrase>> phs = computeSimWithWordVectors(candidatePhrases, positivePhrases, knownNegativePhrases, label);
        Redwood.log(Redwood.DBG, "Computed similarities with positive and negative phrases");
        return phs;
      }
      else
      //TODO: knownnegaitvephrases
        return computeSimWithWordCluster(candidatePhrases, positivePhrases, allMaxSim);
    }
  }



  //this chooses the ones that are not close to the positive phrases!
  Set<CandidatePhrase> chooseUnknownAsNegatives(Set<CandidatePhrase> candidatePhrases, String label, Collection<CandidatePhrase> positivePhrases, Map<String,
    Collection<CandidatePhrase>> knownNegativePhrases, BufferedWriter logFile) throws IOException {

    List<List<CandidatePhrase>> threadedCandidates = GetPatternsFromDataMultiClass.getThreadBatches(CollectionUtils.toList(candidatePhrases), constVars.numThreads);

    Counter<CandidatePhrase> sims = new ClassicCounter<>();

    AtomicDouble allMaxSim = new AtomicDouble(Double.MIN_VALUE);

    ExecutorService executor = Executors.newFixedThreadPool(constVars.numThreads);
    List<Future<Pair<Counter<CandidatePhrase>, Counter<CandidatePhrase>>>> list = new ArrayList<>();

    //multi-threaded choose positive, negative and unknown
    for (List<CandidatePhrase> keys : threadedCandidates) {
      Callable<Pair<Counter<CandidatePhrase>, Counter<CandidatePhrase>>> task = new ComputeSim(label, keys, allMaxSim, positivePhrases, knownNegativePhrases);
      Future<Pair<Counter<CandidatePhrase>, Counter<CandidatePhrase>>> submit = executor.submit(task);
      list.add(submit);
    }

    // Now retrieve the result
    for (Future<Pair<Counter<CandidatePhrase>, Counter<CandidatePhrase>>> future : list) {
      try {
        sims.addAll(future.get().first());
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
    if(logFile != null && wordVectors != null){
      for(Entry<CandidatePhrase, Double> en: removed.entrySet())
        if(wordVectors.containsKey(en.getKey().getPhrase()))
          logFile.write(en.getKey()+"-PN " + ArrayUtils.toString(wordVectors.get(en.getKey().getPhrase()), " ")+"\n");
    }
    //Collection<CandidatePhrase> removed = Counters.retainBottom(sims, (int) (sims.size() * percentage));
    //System.out.println("not choosing " + removed + " as the negative phrases. percentage is " + percentage + " and allMaxsim was " + allMaxSim);

    return sims.keySet();
  }



  Set<CandidatePhrase> chooseUnknownPhrases(DataInstance sent, Random random, double perSelect, Class positiveClass, String label, int maxNum){

    Set<CandidatePhrase> unknownSamples = new HashSet<>();

    if(maxNum == 0)
      return unknownSamples;

    Predicate<CoreLabel> acceptWord = coreLabel -> {
      if(coreLabel.get(positiveClass).equals(label) || constVars.functionWords.contains(coreLabel.word()))
        return false;
      else
        return true;
    };

    Random r = new Random(0);
    List<Integer> lengths = new ArrayList<>();
    for(int i = 1;i <= PatternFactory.numWordsCompoundMapped.get(label); i++)
      lengths.add(i);
    int length = CollectionUtils.sample(lengths, r);

    if(constVars.patternType.equals(PatternFactory.PatternType.DEP)){

    ExtractPhraseFromPattern extract = new ExtractPhraseFromPattern(true, length);
    SemanticGraph g = ((DataInstanceDep) sent).getGraph();
    Collection<CoreLabel> sampledHeads = CollectionUtils.sampleWithoutReplacement(sent.getTokens(), Math.min(maxNum, (int) (perSelect * sent.getTokens().size())), random);

    //TODO: change this for more efficient implementation
    List<String> textTokens = sent.getTokens().stream().map(x -> x.word()).collect(Collectors.toList());

    for(CoreLabel l: sampledHeads) {
      if(!acceptWord.test(l))
        continue;
      IndexedWord w = g.getNodeByIndex(l.index());
      List<String> outputPhrases = new ArrayList<>();
      List<ExtractedPhrase> extractedPhrases = new ArrayList<>();
      List<IntPair> outputIndices = new ArrayList<>();

      extract.printSubGraph(g, w, new ArrayList<>(), textTokens, outputPhrases, outputIndices, new ArrayList<>(), new ArrayList<>(),
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

  private static<E,F> boolean hasElement(Map<E, Collection<F>> values, F value, E ignoreLabel){
    for(Map.Entry<E, Collection<F>> en: values.entrySet()){
      if(en.getKey().equals(ignoreLabel))
        continue;
      if(en.getValue().contains(value))
        return true;
    }
    return false;
  }

  Counter<String> numLabeledTokens(){
    Counter<String> counter = new ClassicCounter<>();
    ConstantsAndVariables.DataSentsIterator data = new ConstantsAndVariables.DataSentsIterator(constVars.batchProcessSents);
    while(data.hasNext()){
      Map<String, DataInstance> sentsf = data.next().first();
      for(Entry<String, DataInstance> en: sentsf.entrySet()){
        for(CoreLabel l : en.getValue().getTokens()){
          for(Entry<String, Class<? extends TypesafeMap.Key<String>>> enc: constVars.getAnswerClass().entrySet()){
            if(l.get(enc.getValue()).equals(enc.getKey())){
              counter.incrementCount(enc.getKey());
            }
          }
        }
      }
    }
    return counter;
  }

  Counter<CandidatePhrase> closeToPositivesFirstIter = null;
  Counter<CandidatePhrase> closeToNegativesFirstIter = null;

  public class ChooseDatumsThread implements Callable {

    Collection<String> keys;
    Map<String, DataInstance> sents;
    Class answerClass;
    String answerLabel;
    TwoDimensionalCounter<CandidatePhrase, E> wordsPatExtracted;
    Counter<E> allSelectedPatterns;
    Counter<Integer> wordClassClustersOfPositive;
    Map<String, Collection<CandidatePhrase>> allPossiblePhrases;
    boolean expandPos;
    boolean expandNeg;

    public ChooseDatumsThread(String label, Map<String, DataInstance> sents, Collection<String> keys, TwoDimensionalCounter<CandidatePhrase, E> wordsPatExtracted, Counter<E> allSelectedPatterns,
                              Counter<Integer> wordClassClustersOfPositive, Map<String, Collection<CandidatePhrase>> allPossiblePhrases, boolean expandPos, boolean expandNeg){
      this.answerLabel = label;
      this.sents = sents;
      this.keys = keys;
      this.wordsPatExtracted = wordsPatExtracted;
      this.allSelectedPatterns = allSelectedPatterns;
      this.wordClassClustersOfPositive = wordClassClustersOfPositive;
      this.allPossiblePhrases = allPossiblePhrases;
      answerClass = constVars.getAnswerClass().get(answerLabel);
      this.expandNeg = expandNeg;
      this.expandPos = expandPos;
    }

    @Override
    public Quintuple<Set<CandidatePhrase>, Set<CandidatePhrase>, Set<CandidatePhrase>, Counter<CandidatePhrase>, Counter<CandidatePhrase>> call() throws Exception {

      Random r = new Random(10);
      Random rneg = new Random(10);
      Set<CandidatePhrase> allPositivePhrases = new HashSet<>();
      Set<CandidatePhrase> allNegativePhrases = new HashSet<>();
      Set<CandidatePhrase> allUnknownPhrases = new HashSet<>();
      Counter<CandidatePhrase> allCloseToPositivePhrases = new ClassicCounter<>();
      Counter<CandidatePhrase> allCloseToNegativePhrases = new ClassicCounter<>();


      Set<CandidatePhrase> knownPositivePhrases = CollectionUtils.unionAsSet(constVars.getLearnedWords(answerLabel).keySet(), constVars.getSeedLabelDictionary().get(answerLabel));

      Set<CandidatePhrase> allConsideredPhrases = new HashSet<>();

      Map<Class, Object> otherIgnoreClasses = constVars.getIgnoreWordswithClassesDuringSelection().get(answerLabel);
      int numlabeled = 0;
      for (String sentid : keys) {
        DataInstance sentInst = sents.get(sentid);
        List<CoreLabel> value = sentInst.getTokens();
        CoreLabel[] sent = value.toArray(new CoreLabel[value.size()]);

        for (int i = 0; i < sent.length; i++) {
          CoreLabel l = sent[i];

          if (l.get(answerClass).equals(answerLabel)) {
            numlabeled++;
            CandidatePhrase candidate = l.get(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class).get(answerLabel);

            if (candidate == null) {
              throw new RuntimeException("for sentence id " + sentid + " and token id " + i + " candidate is null for " + l.word() + " and longest matching" + l.get(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class) + " and matched phrases are " + l.get(PatternsAnnotations.MatchedPhrases.class));
              //candidate = CandidatePhrase.createOrGet(l.word());
            }

            //If the phrase does not exist in its form in the datset (happens when fuzzy matching etc).
            if(!Data.rawFreq.containsKey(candidate)){
              candidate = CandidatePhrase.createOrGet(l.word());
            }

            //Do not add to positive if the word is a "negative" (stop word, english word, ...)

            if(hasElement(allPossiblePhrases, candidate, answerLabel) || PatternFactory.ignoreWordRegex.matcher(candidate.getPhrase()).matches())
              continue;

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

            if(!negative && !ignoreclass && (expandPos || expandNeg) && !hasElement(allPossiblePhrases, candidate, answerLabel) && !PatternFactory.ignoreWordRegex.matcher(candidate.getPhrase()).matches()) {
              if (!allConsideredPhrases.contains(candidate)) {
                Pair<Counter<CandidatePhrase>, Counter<CandidatePhrase>> sims;
                assert candidate != null;
                if(constVars.useWordVectorsToComputeSim)
                  sims = computeSimWithWordVectors(Arrays.asList(candidate), knownPositivePhrases, allPossiblePhrases, answerLabel);
                else
                  sims = computeSimWithWordCluster(Arrays.asList(candidate), knownPositivePhrases, new AtomicDouble());

                boolean addedAsPos = false;
                if(expandPos)
                {
                  double sim = sims.first().getCount(candidate);
                  if (sim > constVars.similarityThresholdHighPrecision){
                    allCloseToPositivePhrases.setCount(candidate, sim);
                    addedAsPos = true;
                  }
                }
                if(expandNeg &&  !addedAsPos) {
                  double simneg = sims.second().getCount(candidate);
                  if (simneg > constVars.similarityThresholdHighPrecision)
                    allCloseToNegativePhrases.setCount(candidate, simneg);
                }
                allConsideredPhrases.add(candidate);
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

    return new Quintuple(allPositivePhrases, allNegativePhrases, allUnknownPhrases, allCloseToPositivePhrases, allCloseToNegativePhrases);
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

  static Counter<PhrasePair> cacheSimilarities = new ConcurrentHashCounter<>();

  //First map is phrase, second map is label to similarity stats
  static Map<String, Map<String, double[]>> similaritiesWithLabeledPhrases = new ConcurrentHashMap<>();

  Map<String, Collection<CandidatePhrase>> getAllPossibleNegativePhrases(String answerLabel){

    //make all possible negative phrases
    Map<String, Collection<CandidatePhrase>> allPossiblePhrases = new HashMap<>();
    Collection<CandidatePhrase> negPhrases = new HashSet<>();
    //negPhrases.addAll(constVars.getOtherSemanticClassesWords());
    negPhrases.addAll(constVars.getStopWords());
    negPhrases.addAll(CandidatePhrase.convertStringPhrases(constVars.functionWords));
    negPhrases.addAll(CandidatePhrase.convertStringPhrases(constVars.getEnglishWords()));
    allPossiblePhrases.put("NEGATIVE", negPhrases);
    for(String label: constVars.getLabels()) {
      if (!label.equals(answerLabel)){
        allPossiblePhrases.put(label, new HashSet<>());

        if(constVars.getLearnedWordsEachIter().containsKey(label))
          allPossiblePhrases.get(label).addAll(constVars.getLearnedWords(label).keySet());
        allPossiblePhrases.get(label).addAll(constVars.getSeedLabelDictionary().get(label));
      }
    }
    allPossiblePhrases.put("OTHERSEM", constVars.getOtherSemanticClassesWords());
    return allPossiblePhrases;
  }

  public GeneralDataset<String, ScorePhraseMeasures> choosedatums(boolean forLearningPattern, String answerLabel,
      TwoDimensionalCounter<CandidatePhrase, E> wordsPatExtracted,
      Counter<E> allSelectedPatterns, boolean computeRawFreq) throws IOException {

    boolean expandNeg = false;
    if(closeToNegativesFirstIter == null){
      closeToNegativesFirstIter = new ClassicCounter<>();
      if(constVars.expandNegativesWhenSampling)
        expandNeg = true;
    }

    boolean expandPos = false;
    if(closeToPositivesFirstIter == null) {
      closeToPositivesFirstIter = new ClassicCounter<>();
      if(constVars.expandPositivesWhenSampling)
        expandPos = true;
    }


    Counter<Integer> distSimClustersOfPositive = new ClassicCounter<>();
    if((expandPos || expandNeg) && !constVars.useWordVectorsToComputeSim){
      for(CandidatePhrase s: CollectionUtils.union(constVars.getLearnedWords(answerLabel).keySet(), constVars.getSeedLabelDictionary().get(answerLabel))){
        String[] toks = s.getPhrase().split("\\s+");
        Integer num = constVars.getWordClassClusters().get(s.getPhrase());
        if(num  == null)
          num = constVars.getWordClassClusters().get(s.getPhrase().toLowerCase());
        if(num == null){
          for(String tok: toks){
            Integer toknum =constVars.getWordClassClusters().get(tok);
            if(toknum == null)
              toknum =constVars.getWordClassClusters().get(tok.toLowerCase());
            if(toknum != null){
              distSimClustersOfPositive.incrementCount(toknum);
            }
          }
        } else
        distSimClustersOfPositive.incrementCount(num);
      }
    }

    //computing this regardless of expandpos and expandneg because we reject all positive words that occur in negatives (can happen in multi word phrases etc)
    Map<String, Collection<CandidatePhrase>> allPossibleNegativePhrases  = getAllPossibleNegativePhrases(answerLabel);

    GeneralDataset<String, ScorePhraseMeasures> dataset = new RVFDataset<>();
    int numpos = 0;
    Set<CandidatePhrase> allNegativePhrases = new HashSet<>();
    Set<CandidatePhrase> allUnknownPhrases = new HashSet<>();
    Set<CandidatePhrase> allPositivePhrases = new HashSet<>();
    //Counter<CandidatePhrase> allCloseToPositivePhrases = new ClassicCounter<CandidatePhrase>();
    //Counter<CandidatePhrase> allCloseToNegativePhrases = new ClassicCounter<CandidatePhrase>();

    //for all sentences brtch
    ConstantsAndVariables.DataSentsIterator sentsIter = new ConstantsAndVariables.DataSentsIterator(constVars.batchProcessSents);
    while(sentsIter.hasNext()) {
      Pair<Map<String, DataInstance>, File> sentsf = sentsIter.next();
      Map<String, DataInstance> sents = sentsf.first();
      Redwood.log(Redwood.DBG, "Sampling datums from " + sentsf.second());
      if (computeRawFreq)
        Data.computeRawFreqIfNull(sents, PatternFactory.numWordsCompoundMax);

      List<List<String>> threadedSentIds = GetPatternsFromDataMultiClass.getThreadBatches(new ArrayList<>(sents.keySet()), constVars.numThreads);
      ExecutorService executor = Executors.newFixedThreadPool(constVars.numThreads);
      List<Future<Quintuple<Set<CandidatePhrase>, Set<CandidatePhrase>, Set<CandidatePhrase>, Counter<CandidatePhrase>,  Counter<CandidatePhrase>>>> list = new ArrayList<>();

      //multi-threaded choose positive, negative and unknown
      for (List<String> keys : threadedSentIds) {
        Callable<Quintuple<Set<CandidatePhrase>, Set<CandidatePhrase>, Set<CandidatePhrase>, Counter<CandidatePhrase>,  Counter<CandidatePhrase>>> task = new ChooseDatumsThread(answerLabel, sents, keys,
           wordsPatExtracted, allSelectedPatterns, distSimClustersOfPositive, allPossibleNegativePhrases, expandPos, expandNeg);
        Future<Quintuple<Set<CandidatePhrase>, Set<CandidatePhrase>, Set<CandidatePhrase>, Counter<CandidatePhrase>,  Counter<CandidatePhrase>>> submit = executor.submit(task);
        list.add(submit);
      }

      // Now retrieve the result
      for (Future<Quintuple<Set<CandidatePhrase>, Set<CandidatePhrase>, Set<CandidatePhrase>, Counter<CandidatePhrase>,  Counter<CandidatePhrase>>> future : list) {
        try {
          Quintuple<Set<CandidatePhrase>, Set<CandidatePhrase>, Set<CandidatePhrase>, Counter<CandidatePhrase>,  Counter<CandidatePhrase>> result = future.get();
          allPositivePhrases.addAll(result.first());
          allNegativePhrases.addAll(result.second());
          allUnknownPhrases.addAll(result.third());
          if(expandPos)
            for(Entry<CandidatePhrase, Double> en : result.fourth().entrySet())
              closeToPositivesFirstIter.setCount(en.getKey(), en.getValue());
          if(expandNeg)
            for(Entry<CandidatePhrase, Double> en : result.fifth().entrySet())
             closeToNegativesFirstIter.setCount(en.getKey(), en.getValue());

        } catch (Exception e) {
          executor.shutdownNow();
          throw new RuntimeException(e);
        }
      }
      executor.shutdown();
    }

    //Set<CandidatePhrase> knownPositivePhrases = CollectionUtils.unionAsSet(constVars.getLearnedWords().get(answerLabel).keySet(), constVars.getSeedLabelDictionary().get(answerLabel));
    //TODO: this is kinda not nice; how is allpositivephrases different from positivephrases again?
    allPositivePhrases.addAll(constVars.getLearnedWords(answerLabel).keySet());
    //allPositivePhrases.addAll(knownPositivePhrases);

    BufferedWriter logFile = null;
    BufferedWriter logFileFeat = null;

    if(constVars.logFileVectorSimilarity != null){
      logFile = new BufferedWriter(new FileWriter(constVars.logFileVectorSimilarity));
      logFileFeat = new BufferedWriter(new FileWriter(constVars.logFileVectorSimilarity+"_feat"));

      if(wordVectors != null){
      for(CandidatePhrase p : allPositivePhrases){
        if(wordVectors.containsKey(p.getPhrase())){
          logFile.write(p.getPhrase()+"-P " + ArrayUtils.toString(wordVectors.get(p.getPhrase()), " ")+"\n");
        }
      }
      }
    }

    if(constVars.expandPositivesWhenSampling){
      //TODO: patwtbyfrew
      //Counters.retainTop(allCloseToPositivePhrases, (int) (allCloseToPositivePhrases.size()*constVars.subSampleUnkAsPosUsingSimPercentage));
      Redwood.log("Expanding positives by adding " + Counters.toSortedString(closeToPositivesFirstIter, closeToPositivesFirstIter.size(),"%1$s:%2$f", "\t")+ " phrases");

      allPositivePhrases.addAll(closeToPositivesFirstIter.keySet());

      //write log
      if(logFile != null && wordVectors != null && expandNeg){
        for(CandidatePhrase p : closeToPositivesFirstIter.keySet()){
          if(wordVectors.containsKey(p.getPhrase())){
            logFile.write(p.getPhrase()+"-PP " + ArrayUtils.toString(wordVectors.get(p.getPhrase()), " ")+"\n");
          }
        }
      }
    }

    if(constVars.expandNegativesWhenSampling){
      //TODO: patwtbyfrew
      //Counters.retainTop(allCloseToPositivePhrases, (int) (allCloseToPositivePhrases.size()*constVars.subSampleUnkAsPosUsingSimPercentage));
      Redwood.log("Expanding negatives by adding " + Counters.toSortedString(closeToNegativesFirstIter , closeToNegativesFirstIter.size(), "%1$s:%2$f","\t")+ " phrases");
      allNegativePhrases.addAll(closeToNegativesFirstIter.keySet());

      //write log
      if(logFile != null && wordVectors != null && expandNeg){
        for(CandidatePhrase p : closeToNegativesFirstIter.keySet()){
          if(wordVectors.containsKey(p.getPhrase())){
            logFile.write(p.getPhrase()+"-NN " + ArrayUtils.toString(wordVectors.get(p.getPhrase()), " ")+"\n");
          }
        }
      }
    }




    System.out.println("all positive phrases of size " + allPositivePhrases.size() + " are  " + allPositivePhrases);
    for(CandidatePhrase candidate: allPositivePhrases) {
      Counter<ScorePhraseMeasures> feat;
      //CandidatePhrase candidate = new CandidatePhrase(l.word());
      if (forLearningPattern) {
        feat = getPhraseFeaturesForPattern(answerLabel, candidate);
      } else {
        feat = getFeatures(answerLabel, candidate, wordsPatExtracted.getCounter(candidate), allSelectedPatterns);
      }
      RVFDatum<String, ScorePhraseMeasures> datum = new RVFDatum<>(feat, "true");
      dataset.add(datum);
      numpos += 1;
      if(logFileFeat !=null){
        logFileFeat.write("POSITIVE " + candidate.getPhrase() +"\t" + Counters.toSortedByKeysString(feat,"%1$s:%2$.0f",";","%s")+"\n");
      }
    }

    Redwood.log(Redwood.DBG, "Number of pure negative phrases is " + allNegativePhrases.size());
    Redwood.log(Redwood.DBG, "Number of unknown phrases is " + allUnknownPhrases.size());

    if(constVars.subsampleUnkAsNegUsingSim){
      Set<CandidatePhrase> chosenUnknown = chooseUnknownAsNegatives(allUnknownPhrases, answerLabel, allPositivePhrases, allPossibleNegativePhrases, logFile);
      Redwood.log(Redwood.DBG, "Choosing " + chosenUnknown.size() + " unknowns as negative based to their similarity to the positive phrases");
      allNegativePhrases.addAll(chosenUnknown);
    }
    else{
        allNegativePhrases.addAll(allUnknownPhrases);
    }

    if(allNegativePhrases.size() > numpos) {
      Redwood.log(Redwood.WARN, "Num of negative (" + allNegativePhrases.size() + ") is higher than number of positive phrases (" + numpos + ") = " +
        (allNegativePhrases.size() / (double)numpos) + ". " +
        "Capping the number by taking the first numPositives as negative. Consider decreasing perSelectRand");
      int i = 0;
      Set<CandidatePhrase> selectedNegPhrases = new HashSet<>();
      for(CandidatePhrase p : allNegativePhrases){
        if (i >= numpos)
          break;
        selectedNegPhrases.add(p);
        i++;
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
      RVFDatum<String, ScorePhraseMeasures> datum = new RVFDatum<>(feat, "false");
      dataset.add(datum);

      if(logFile!=null && wordVectors != null && wordVectors.containsKey(negative.getPhrase())){
        logFile.write(negative.getPhrase()+"-N"+" " + ArrayUtils.toString(wordVectors.get(negative.getPhrase()), " ")+"\n");
      }

      if(logFileFeat !=null)
        logFileFeat.write("NEGATIVE " + negative.getPhrase() +"\t" + Counters.toSortedByKeysString(feat,"%1$s:%2$.0f",";","%s")+"\n");

    }

    if(logFile!=null){
      logFile.close();
    }
    if(logFileFeat != null){
      logFileFeat.close();
    }

    System.out.println("Before feature count threshold, dataset stats are ");
    dataset.summaryStatistics();


    dataset.applyFeatureCountThreshold(constVars.featureCountThreshold);
    System.out.println("AFTER feature count threshold of " + constVars.featureCountThreshold + ", dataset stats are ");
    dataset.summaryStatistics();

    Redwood.log(Redwood.DBG, "Eventually, number of positive datums:  " + numpos + " and number of negative datums: " + allNegativePhrases.size());
    return dataset;
  }


  //Map of label to an array of values -- num_items, avg similarity, max similarity
  private static Map<String, double[]> getSimilarities(String phrase) {
    return similaritiesWithLabeledPhrases.get(phrase);
  }


  Counter<ScorePhraseMeasures> getPhraseFeaturesForPattern(String label, CandidatePhrase word) {

    if (phraseScoresRaw.containsFirstKey(word))
      return phraseScoresRaw.getCounter(word);

    Counter<ScorePhraseMeasures> scoreslist = new ClassicCounter<>();

    //Add features on the word, if any!
    if(word.getFeatures()!= null){
      scoreslist.addAll(Counters.transform(word.getFeatures(), x -> ScorePhraseMeasures.create(x)));
    } else{
      Redwood.log(ConstantsAndVariables.extremedebug, "features are null for " + word);
    }


    if (constVars.usePatternEvalSemanticOdds) {
      double dscore = this.getDictOddsScore(word, label, 0);
      scoreslist.setCount(ScorePhraseMeasures.SEMANTICODDS, dscore);
    }

    if (constVars.usePatternEvalGoogleNgram) {
      Double gscore = getGoogleNgramScore(word);
      if (gscore.isInfinite() || gscore.isNaN()) {
        throw new RuntimeException("how is the google ngrams score " + gscore + " for " + word);
      }
      scoreslist.setCount(ScorePhraseMeasures.GOOGLENGRAM, gscore);
    }

    if (constVars.usePatternEvalDomainNgram) {
      Double gscore = getDomainNgramScore(word.getPhrase());
      if (gscore.isInfinite() || gscore.isNaN()) {
        throw new RuntimeException("how is the domain ngrams score " + gscore + " for " + word + " when domain raw freq is " + Data.domainNGramRawFreq.getCount(word)
          + " and raw freq is " + Data.rawFreq.getCount(word));

      }
      scoreslist.setCount(ScorePhraseMeasures.DOMAINNGRAM, gscore);
    }

    if (constVars.usePatternEvalWordClass) {
      Integer wordclass = constVars.getWordClassClusters().get(word.getPhrase());
      if(wordclass == null){
        wordclass = constVars.getWordClassClusters().get(word.getPhrase().toLowerCase());
      }
      scoreslist.setCount(ScorePhraseMeasures.create(ScorePhraseMeasures.DISTSIM.toString()+"-"+wordclass), 1.0);
    }

    if (constVars.usePatternEvalEditDistSame) {
      double ed = constVars.getEditDistanceScoresThisClass(label, word.getPhrase());
      assert ed <= 1 : " how come edit distance from the true class is " + ed  + " for word " + word;
      scoreslist.setCount(ScorePhraseMeasures.EDITDISTSAME,  ed);
    }
    if (constVars.usePatternEvalEditDistOther) {
      double ed = constVars.getEditDistanceScoresOtherClass(label, word.getPhrase());
      assert ed <= 1 : " how come edit distance from the true class is " + ed  + " for word " + word;;
      scoreslist.setCount(ScorePhraseMeasures.EDITDISTOTHER, ed);
    }

    if(constVars.usePatternEvalWordShape){
      scoreslist.setCount(ScorePhraseMeasures.WORDSHAPE, this.getWordShapeScore(word.getPhrase(), label));
    }

    if(constVars.usePatternEvalWordShapeStr){
      scoreslist.setCount(ScorePhraseMeasures.create(ScorePhraseMeasures.WORDSHAPESTR + "-" + this.wordShape(word.getPhrase())), 1.0);
    }

    if(constVars.usePatternEvalFirstCapital){
      scoreslist.setCount(ScorePhraseMeasures.ISFIRSTCAPITAL, StringUtils.isCapitalized(word.getPhrase())? 1.0 :0);
    }

    if(constVars.usePatternEvalBOW){
      for(String s: word.getPhrase().split("\\s+"))
        scoreslist.setCount(ScorePhraseMeasures.create(ScorePhraseMeasures.BOW +"-"+ s), 1.0);
    }

    phraseScoresRaw.setCounter(word, scoreslist);
    //System.out.println("scores for " + word + " are " + scoreslist);
    return scoreslist;
  }
/*
  Counter<ScorePhraseMeasures> getPhraseFeaturesForPattern(String label, CandidatePhrase word) {

    if (phraseScoresRaw.containsFirstKey(word))
      return phraseScoresRaw.getCounter(word);

    Counter<ScorePhraseMeasures> scoreslist = new ClassicCounter<ScorePhraseMeasures>();

    if (constVars.usePatternEvalSemanticOdds) {
      assert constVars.dictOddsWeights != null : "usePatternEvalSemanticOdds is true but dictOddsWeights is null for the label " + label;
      double dscore = this.getDictOddsScore(word, label, 0);
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

    if (constVars.usePatternEvalEditDistSame) {
      scoreslist.setCount(ScorePhraseMeasures.EDITDISTSAME, constVars.getEditDistanceScoresThisClass(label, word.getPhrase()));
    }
    if (constVars.usePatternEvalEditDistOther)
      scoreslist.setCount(ScorePhraseMeasures.EDITDISTOTHER, constVars.getEditDistanceScoresOtherClass(label, word.getPhrase()));

    if(constVars.usePatternEvalWordShape){
      scoreslist.setCount(ScorePhraseMeasures.WORDSHAPE, this.getWordShapeScore(word.getPhrase(), label));
    }

    if(constVars.usePatternEvalWordShapeStr){
      scoreslist.setCount(ScorePhraseMeasures.create(ScorePhraseMeasures.WORDSHAPE +"-"+ this.wordShape(word.getPhrase())), 1.0);
    }

    if(constVars.usePatternEvalFirstCapital){
      scoreslist.setCount(ScorePhraseMeasures.ISFIRSTCAPITAL, StringUtils.isCapitalized(word.getPhrase())?1.0:0.0);
    }

    if(constVars.usePatternEvalBOW){
      for(String s: word.getPhrase().split("\\s+"))
        scoreslist.setCount(ScorePhraseMeasures.create(ScorePhraseMeasures.BOW +"-"+ s.toLowerCase()), 1.0);
    }

    phraseScoresRaw.setCounter(word, scoreslist);
    return scoreslist;
  }
*/

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

      RVFDatum<String, ScorePhraseMeasures> d = new RVFDatum<>(feat, Boolean.FALSE.toString());
      Counter<String> sc = classifier.scoresOf(d);
      score = sc.getCount(Boolean.TRUE.toString());

    } else if (scoreClassifierType.equals(ClassifierType.LR)) {

      LogisticClassifier logcl = ((LogisticClassifier) classifier);

      String l = (String) logcl.getLabelForInternalPositiveClass();
      Counter<ScorePhraseMeasures> feat;
      if (forLearningPatterns)
        feat = getPhraseFeaturesForPattern(label, word);
      else
        feat = this.getFeatures(label, word, patternsThatExtractedPat, allSelectedPatterns);

      RVFDatum<String, ScorePhraseMeasures> d = new RVFDatum<>(feat, Boolean.TRUE.toString());
      score = logcl.probabilityOf(d);

    } else if( scoreClassifierType.equals(ClassifierType.SHIFTLR)){
      //convert to basicdatum -- restriction of ShiftLR right now
      Counter<ScorePhraseMeasures> feat;
      if (forLearningPatterns)
        feat = getPhraseFeaturesForPattern(label, word);
      else
        feat = this.getFeatures(label, word, patternsThatExtractedPat, allSelectedPatterns);
      BasicDatum<String, ScorePhraseMeasures> d = new BasicDatum<>(feat.keySet(), Boolean.FALSE.toString());
      Counter<String> sc = ((MultinomialLogisticClassifier)classifier).probabilityOf(d);
      score = sc.getCount(Boolean.TRUE.toString());

    }else if (scoreClassifierType.equals(ClassifierType.SVM) || scoreClassifierType.equals(ClassifierType.RF) ||scoreClassifierType.equals(ClassifierType.LINEAR)) {

      Counter<ScorePhraseMeasures> feat = null;
      if (forLearningPatterns)
        feat = getPhraseFeaturesForPattern(label, word);
      else
        feat = this.getFeatures(label, word, patternsThatExtractedPat, allSelectedPatterns);

      RVFDatum<String, ScorePhraseMeasures> d = new RVFDatum<>(feat, Boolean.FALSE.toString());
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

    Counter<ScorePhraseMeasures> scoreslist = new ClassicCounter<>();

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
      double dscore = this.getDictOddsScore(word, label, 0);
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
      Integer wordclass = constVars.getWordClassClusters().get(word.getPhrase());
      if(wordclass == null){
        wordclass = constVars.getWordClassClusters().get(word.getPhrase().toLowerCase());
      }
      scoreslist.setCount(ScorePhraseMeasures.create(ScorePhraseMeasures.DISTSIM.toString()+"-"+wordclass), 1.0);
    }

    if(constVars.usePhraseEvalWordVector){
      Map<String, double[]> sims = getSimilarities(word.getPhrase());
      if(sims == null){
        //TODO: make more efficient
        Map<String, Collection<CandidatePhrase>> allPossibleNegativePhrases = getAllPossibleNegativePhrases(label);
        Set<CandidatePhrase> knownPositivePhrases = CollectionUtils.unionAsSet(constVars.getLearnedWords(label).keySet(), constVars.getSeedLabelDictionary().get(label));
        computeSimWithWordVectors(Arrays.asList(word), knownPositivePhrases, allPossibleNegativePhrases, label);
        sims = getSimilarities(word.getPhrase());
      }
      assert sims != null : " Why are there no similarities for " + word;

      double avgPosSim = sims.get(label)[Similarities.AVGSIM.ordinal()];
      double maxPosSim = sims.get(label)[Similarities.MAXSIM.ordinal()];
      double sumNeg = 0, maxNeg = Double.MIN_VALUE;
      double allNumItems =0;
      for(Entry<String, double[]> simEn: sims.entrySet()){
        if(simEn.getKey().equals(label))
          continue;
        double numItems = simEn.getValue()[Similarities.NUMITEMS.ordinal()];
        sumNeg += simEn.getValue()[Similarities.AVGSIM.ordinal()]*numItems;
        allNumItems += numItems;
        double maxNegLabel =simEn.getValue()[Similarities.MAXSIM.ordinal()];
        if(maxNeg < maxNegLabel)
          maxNeg = maxNegLabel;
      }
      double avgNegSim = sumNeg / allNumItems;
      scoreslist.setCount(ScorePhraseMeasures.WORDVECPOSSIMAVG, avgPosSim);
      scoreslist.setCount(ScorePhraseMeasures.WORDVECPOSSIMMAX, maxPosSim);
      scoreslist.setCount(ScorePhraseMeasures.WORDVECNEGSIMAVG, avgNegSim);
      scoreslist.setCount(ScorePhraseMeasures.WORDVECNEGSIMAVG, maxNeg);
    }

    if (constVars.usePhraseEvalEditDistSame) {
      double ed = constVars.getEditDistanceScoresThisClass(label, word.getPhrase());
      assert ed <= 1 : " how come edit distance from the true class is " + ed  + " for word " + word;
      scoreslist.setCount(ScorePhraseMeasures.EDITDISTSAME,  ed);
    }
    if (constVars.usePhraseEvalEditDistOther) {
      double ed = constVars.getEditDistanceScoresOtherClass(label, word.getPhrase());
      assert ed <= 1 : " how come edit distance from the true class is " + ed  + " for word " + word;
      scoreslist.setCount(ScorePhraseMeasures.EDITDISTOTHER, ed);
    }

    if(constVars.usePhraseEvalWordShape){
      scoreslist.setCount(ScorePhraseMeasures.WORDSHAPE, this.getWordShapeScore(word.getPhrase(), label));
    }

    if(constVars.usePhraseEvalWordShapeStr){
      scoreslist.setCount(ScorePhraseMeasures.create(ScorePhraseMeasures.WORDSHAPESTR + "-" + this.wordShape(word.getPhrase())), 1.0);
    }

    if(constVars.usePhraseEvalFirstCapital){
      scoreslist.setCount(ScorePhraseMeasures.ISFIRSTCAPITAL, StringUtils.isCapitalized(word.getPhrase())? 1.0 :0);
    }

    if(constVars.usePhraseEvalBOW){
      for(String s: word.getPhrase().split("\\s+"))
        scoreslist.setCount(ScorePhraseMeasures.create(ScorePhraseMeasures.BOW +"-"+ s), 1.0);
    }

    phraseScoresRaw.setCounter(word, scoreslist);
    //System.out.println("scores for " + word + " are " + scoreslist);
    return scoreslist;
  }

}
