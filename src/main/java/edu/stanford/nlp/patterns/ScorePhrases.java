package edu.stanford.nlp.patterns;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.Env;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.patterns.dep.ApplyDepPatterns;
import edu.stanford.nlp.patterns.surface.*;
import edu.stanford.nlp.patterns.GetPatternsFromDataMultiClass.WordScoring;
import edu.stanford.nlp.patterns.PhraseScorer.Normalization;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.ArgumentParser.Option;
import edu.stanford.nlp.util.logging.Redwood;

public class ScorePhrases<E extends Pattern>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(ScorePhrases.class);

  private Map<String, Boolean> writtenInJustification = new HashMap<>();

  ConstantsAndVariables constVars = null;

  @Option(name = "phraseScorerClass")
  Class<? extends PhraseScorer> phraseScorerClass = ScorePhrasesAverageFeatures.class;
  PhraseScorer phraseScorer = null;

  public ScorePhrases(Properties props, ConstantsAndVariables cv){
    ArgumentParser.fillOptions(this, props);
    this.constVars = cv;
    try {
      phraseScorer = phraseScorerClass
          .getConstructor(ConstantsAndVariables.class).newInstance(constVars);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
    ArgumentParser.fillOptions(phraseScorer, props);
  }

  public Counter<CandidatePhrase> chooseTopWords(Counter<CandidatePhrase> newdt,
                                                 TwoDimensionalCounter<CandidatePhrase, E> terms,
                                                 Counter<CandidatePhrase> useThresholdNumPatternsForTheseWords,
                                                 Set<CandidatePhrase> ignoreWords, double thresholdWordExtract) {

    Iterator<CandidatePhrase> termIter = Counters.toPriorityQueue(newdt).iterator();
    Counter<CandidatePhrase> finalwords = new ClassicCounter<>();

    while (termIter.hasNext()) {

      if (finalwords.size() >= constVars.numWordsToAdd){
        break;
      }
      CandidatePhrase w = termIter.next();
      if (newdt.getCount(w) < thresholdWordExtract) {
        Redwood.log(ConstantsAndVariables.extremedebug,"not adding word " + w + " and any later words because the score " + newdt.getCount(w) + " is less than the threshold of  " + thresholdWordExtract);
        break;
      }
      assert (newdt.getCount(w) != Double.POSITIVE_INFINITY);
      if (useThresholdNumPatternsForTheseWords.containsKey(w)
          && numNonRedundantPatterns(terms, w) < constVars.thresholdNumPatternsApplied) {
        Redwood
            .log(
                "extremePatDebug",
                "Not adding "
                    + w
                    + " because the number of non redundant patterns are below threshold of " +  constVars.thresholdNumPatternsApplied + ":"
                    + terms.getCounter(w).keySet());
        continue;
      }
      CandidatePhrase matchedFuzzy = null;
      if (constVars.minLen4FuzzyForPattern > 0 && ignoreWords != null)
        matchedFuzzy = ConstantsAndVariables.containsFuzzy(ignoreWords, w, constVars.minLen4FuzzyForPattern);
      if (matchedFuzzy == null) {
        Redwood.log("extremePatDebug", "adding word " + w);
        finalwords.setCount(w, newdt.getCount(w));
      } else {
        Redwood
            .log("extremePatDebug", "not adding " + w
                + " because it matched " + matchedFuzzy
                + " in common English word");
        ignoreWords.add(w);
      }
    }
     String nextTen = "";
     int n = 0;
     while (termIter.hasNext()) {
     n++;
     if (n > 10)
     break;
       CandidatePhrase w = termIter.next();
     nextTen += ";\t" + w + ":" + newdt.getCount(w);
     }
     Redwood.log(Redwood.DBG, "Next ten phrases were " + nextTen);
    return finalwords;
  }

  public static <E, F> void removeKeys(TwoDimensionalCounter<E, F> counter,
      Collection<E> removeKeysCollection) {

    for (E key : removeKeysCollection)
      counter.remove(key);
  }

  private double numNonRedundantPatterns(
      TwoDimensionalCounter<CandidatePhrase, E> terms, CandidatePhrase w) {
    Object[] pats = terms.getCounter(w).keySet()
        .toArray();
    int numPat = 0;
    for (int i = 0; i < pats.length; i++) {
      //String pati = constVars.getPatternIndex().get(pats[i]).toString();
      String pati = pats[i].toString();
      boolean contains = false;
      for (int j = i + 1; j < pats.length; j++) {
        //String patj = constVars.getPatternIndex().get(pats[j]).toString();
        String patj = pats[j].toString();
        if (patj.contains(pati) || pati.contains(patj)) {
          contains = true;
          break;
        }
      }
      if (!contains)
        numPat++;
    }
    return numPat;
  }

  public Counter<CandidatePhrase> learnNewPhrases(
    String label,
    PatternsForEachToken patternsForEachToken,
    Counter<E> patternsLearnedThisIter,
    Counter<E> allSelectedPatterns,
    CollectionValuedMap<E, Triple<String, Integer, Integer>> tokensMatchedPatterns,
    Counter<CandidatePhrase> scoreForAllWordsThisIteration,
    TwoDimensionalCounter<CandidatePhrase, E> terms,
    TwoDimensionalCounter<CandidatePhrase, E> wordsPatExtracted,
    TwoDimensionalCounter<E, CandidatePhrase> patternsAndWords4Label,
    String identifier, Set<CandidatePhrase> ignoreWords) throws IOException, ClassNotFoundException {

    boolean computeProcDataFreq = false;
    if (Data.processedDataFreq == null) {
      computeProcDataFreq = true;
      Data.processedDataFreq = new ClassicCounter<>();
      assert Data.rawFreq != null;
    }

    Set<CandidatePhrase> alreadyIdentifiedWords = new HashSet<>(constVars.getLearnedWords(label).keySet());
    alreadyIdentifiedWords.addAll(constVars.getSeedLabelDictionary().get(label));
    Counter<CandidatePhrase> words = learnNewPhrasesPrivate(label,
        patternsForEachToken, patternsLearnedThisIter, allSelectedPatterns, alreadyIdentifiedWords,
        tokensMatchedPatterns, scoreForAllWordsThisIteration, terms,
        wordsPatExtracted,  patternsAndWords4Label,
        identifier, ignoreWords, computeProcDataFreq);

    //constVars.addLabelDictionary(label, words.keySet());


    return words;
  }

  private void runParallelApplyPats(Map<String, DataInstance> sents, String label, E pattern, TwoDimensionalCounter<CandidatePhrase, E> wordsandLemmaPatExtracted,
                            CollectionValuedMap<E, Triple<String, Integer, Integer>> matchedTokensByPat, Set<CandidatePhrase> alreadyLabeledWords) {

    Redwood.log(Redwood.DBG, "Applying pattern " + pattern + " to a total of " + sents.size() + " sentences ");
    List<String> notAllowedClasses = new ArrayList<>();
    List<String> sentids = CollectionUtils.toList(sents.keySet());
    if(constVars.doNotExtractPhraseAnyWordLabeledOtherClass){
      for(String l: constVars.getAnswerClass().keySet()){
        if(!l.equals(label)){
          notAllowedClasses.add(l);
        }
      }
      notAllowedClasses.add("OTHERSEM");
    }


    Map<TokenSequencePattern, E> surfacePatternsLearnedThisIterConverted = null;
    Map<SemgrexPattern, E> depPatternsLearnedThisIterConverted = null;

    if(constVars.patternType.equals(PatternFactory.PatternType.SURFACE)) {
      surfacePatternsLearnedThisIterConverted = new HashMap<>();
      String patternStr = null;
      try{
        patternStr = pattern.toString(notAllowedClasses);
        TokenSequencePattern pat = TokenSequencePattern.compile(constVars.env.get(label), patternStr);
        surfacePatternsLearnedThisIterConverted.put(pat, pattern);
      }catch(Exception e){
        log.info("Error applying pattern " + patternStr + ". Probably an ill formed pattern (can be because of special symbols in label names). Contact the software developer.");
        throw e;
      }
    } else if(constVars.patternType.equals(PatternFactory.PatternType.DEP)) {
      depPatternsLearnedThisIterConverted = new HashMap<>();
      SemgrexPattern pat = SemgrexPattern.compile(pattern.toString(notAllowedClasses), new edu.stanford.nlp.semgraph.semgrex.Env(constVars.env.get(label).getVariables()));
      depPatternsLearnedThisIterConverted.put(pat, pattern);
    } else {
      throw new UnsupportedOperationException();
    }

    //Apply the patterns and extract candidate phrases
    int num;
    int numThreads = constVars.numThreads;

    //If number of sentences is less, do not create so many threads
    if(sents.size() < 50)
      numThreads = 1;

    if (numThreads == 1)
      num = sents.size();
    else
      num = sents.size() / (numThreads - 1);

    ExecutorService executor = Executors.newFixedThreadPool(constVars.numThreads);
    List<Future<Triple<TwoDimensionalCounter<CandidatePhrase, E>, CollectionValuedMap<E, Triple<String, Integer, Integer>>, Set<CandidatePhrase>>>> list = new ArrayList<>();


    for (int i = 0; i < numThreads; i++) {

      Callable<Triple<TwoDimensionalCounter<CandidatePhrase, E>, CollectionValuedMap<E, Triple<String, Integer, Integer>>, Set<CandidatePhrase>>> task = null;

      if(pattern.type.equals(PatternFactory.PatternType.SURFACE))
        //Redwood.log(Redwood.DBG, "Applying pats: assigning sentences " + i*num + " to " +Math.min(sentids.size(), (i + 1) * num) + " to thread " + (i+1));
        task = new ApplyPatterns(sents, num == sents.size() ? sentids : sentids.subList(i * num,
          Math.min(sentids.size(), (i + 1) * num)), surfacePatternsLearnedThisIterConverted, label,
          constVars.removeStopWordsFromSelectedPhrases,
          constVars.removePhrasesWithStopWords, constVars);
      else
        task = new ApplyDepPatterns(sents, num == sents.size() ? sentids : sentids.subList(i * num,
          Math.min(sentids.size(), (i + 1) * num)), depPatternsLearnedThisIterConverted, label,
          constVars.removeStopWordsFromSelectedPhrases,
          constVars.removePhrasesWithStopWords, constVars);


      Future<Triple<TwoDimensionalCounter<CandidatePhrase, E>, CollectionValuedMap<E, Triple<String, Integer, Integer>>, Set<CandidatePhrase>>> submit = executor
          .submit(task);
      list.add(submit);
    }

    // Now retrieve the result
    for (Future<Triple<TwoDimensionalCounter<CandidatePhrase, E>, CollectionValuedMap<E, Triple<String, Integer, Integer>>,Set<CandidatePhrase>>> future : list) {
      try{
        Triple<TwoDimensionalCounter<CandidatePhrase, E>, CollectionValuedMap<E, Triple<String, Integer, Integer>>,Set<CandidatePhrase>> result = future
            .get();
        Redwood.log(ConstantsAndVariables.extremedebug, "Pattern " + pattern + " extracted phrases " + result.first());
        wordsandLemmaPatExtracted.addAll(result.first());
        matchedTokensByPat.addAll(result.second());
        alreadyLabeledWords.addAll(result.third());
      }catch(Exception e){
        executor.shutdownNow();
        throw new RuntimeException(e);
      }
    }
    executor.shutdown();
  }

  /*
  void runParallelApplyPats(Map<String, List<CoreLabel>> sents, Set<String> sentIds, String label, Counter<E> patternsLearnedThisIter,  TwoDimensionalCounter<Pair<String, String>, Integer> wordsandLemmaPatExtracted,
                            CollectionValuedMap<Integer, Triple<String, Integer, Integer>> matchedTokensByPat) throws InterruptedException, ExecutionException {
    List<String> keyset = new ArrayList<String>(sentIds);
    List<String> notAllowedClasses = new ArrayList<String>();

    if(constVars.doNotExtractPhraseAnyWordLabeledOtherClass){
      for(String l: constVars.getAnswerClass().keySet()){
        if(!l.equals(label)){
          notAllowedClasses.add(l+":"+l);
        }
      }
      notAllowedClasses.add("OTHERSEM:OTHERSEM");
    }

    //Apply the patterns and extract candidate phrases
    int num = 0;
    if (constVars.numThreads == 1)
      num = keyset.size();
    else
      num = keyset.size() / (constVars.numThreads - 1);
    ExecutorService executor = Executors.newFixedThreadPool(constVars.numThreads);
    List<Future<Pair<TwoDimensionalCounter<Pair<String, String>, Integer>, CollectionValuedMap<Integer, Triple<String, Integer, Integer>>>>> list = new ArrayList<Future<Pair<TwoDimensionalCounter<Pair<String, String>, Integer>, CollectionValuedMap<Integer, Triple<String, Integer, Integer>>>>>();
    for (int i = 0; i < constVars.numThreads; i++) {

      Callable<Pair<TwoDimensionalCounter<Pair<String, String>, Integer>, CollectionValuedMap<Integer, Triple<String, Integer, Integer>>>> task = null;
      Map<TokenSequencePattern, Integer> patternsLearnedThisIterConverted = new HashMap<TokenSequencePattern , Integer>();
      for (Integer pindex : patternsLearnedThisIter.keySet()){
        SurfacePattern p = constVars.getPatternIndex().get(pindex);
        TokenSequencePattern pat = TokenSequencePattern.compile(constVars.env.get(label), p.toString(notAllowedClasses));
        patternsLearnedThisIterConverted.put(pat, pindex);
      }

      task = new ApplyPatternsMulti(sents, keyset.subList(i * num,
        Math.min(keyset.size(), (i + 1) * num)), patternsLearnedThisIterConverted, label,
        constVars.removeStopWordsFromSelectedPhrases,
        constVars.removePhrasesWithStopWords, constVars);

      Future<Pair<TwoDimensionalCounter<Pair<String, String>, Integer>, CollectionValuedMap<Integer, Triple<String, Integer, Integer>>>> submit = executor
        .submit(task);
      list.add(submit);
    }

    // Now retrieve the result
    for (Future<Pair<TwoDimensionalCounter<Pair<String, String>, Integer>, CollectionValuedMap<Integer, Triple<String, Integer, Integer>>>> future : list) {
      try{
        Pair<TwoDimensionalCounter<Pair<String, String>, Integer>, CollectionValuedMap<Integer, Triple<String, Integer, Integer>>> result = future
          .get();
        wordsandLemmaPatExtracted.addAll(result.first());
        matchedTokensByPat.addAll(result.second());
      }catch(Exception e){
        executor.shutdownNow();
        throw new RuntimeException(e);
      }
    }
    executor.shutdown();
  }
  */

  protected Map<E, Map<String, DataInstance>> getSentences(Map<E, Set<String>> sentids) {
    try{

      Set<File> files = new HashSet<>();

      Map<E, Map<String, DataInstance>> sentsAll  = new HashMap<>();
      CollectionValuedMap<String, E> sentIds2Pats = new CollectionValuedMap<>();
      for(Map.Entry<E, Set<String>> setEn: sentids.entrySet()){
        if(!sentsAll.containsKey(setEn.getKey()))
          sentsAll.put(setEn.getKey(), new HashMap<>());
        for(String s: setEn.getValue()){
          sentIds2Pats.add(s, setEn.getKey());
          if(constVars.batchProcessSents){
            File f = Data.sentId2File.get(s);
            assert f!= null : "How come no file for sentence " + s;
            files.add(f);
          }
        }
      }

      if(constVars.batchProcessSents){
        for(File f: files){
          Map<String, DataInstance> sentsf = IOUtils.readObjectFromFile(f);
          for(Map.Entry<String, DataInstance> s: sentsf.entrySet()){
            for(E pat: sentIds2Pats.get(s.getKey()))
              sentsAll.get(pat).put(s.getKey(), s.getValue());
          }
        }
      }else{
        for(Map.Entry<String, DataInstance> s: Data.sents.entrySet()){
          for(E pat: sentIds2Pats.get(s.getKey()))
            sentsAll.get(pat).put(s.getKey(), s.getValue());
        }
      }

//      /System.out.println("All sentences are " + sentsAll.entrySet().stream().map( x -> constVars.patternIndex.get(x.getKey())+":"+x.getValue()).collect(Collectors.toList()));
      return sentsAll;
    }catch(ClassNotFoundException e){
      throw new RuntimeException(e);
    }catch(IOException e1){
      throw new RuntimeException(e1);

    }
  }


  public void applyPats(Counter<E> patterns, String label, TwoDimensionalCounter<CandidatePhrase, E> wordsandLemmaPatExtracted,
                        CollectionValuedMap<E, Triple<String, Integer, Integer>> matchedTokensByPat, Set<CandidatePhrase> alreadyLabeledWords){
 //   Counter<E> patternsLearnedThisIterConsistsOnlyGeneralized = new ClassicCounter<E>();
 //   Counter<E> patternsLearnedThisIterRest = new ClassicCounter<E>();
//    Set<String> specialWords = constVars.invertedIndex.getSpecialWordsList();

    for(Map.Entry<String, Env> en: constVars.env.entrySet()){
      en.getValue().getVariables().putAll(ConstantsAndVariables.globalEnv.getVariables());
    }

    Map<E, Map<String, DataInstance>> sentencesForPatterns = getSentences(constVars.invertedIndex.queryIndex(patterns.keySet()));

    for(Map.Entry<E, Map<String, DataInstance>> en: sentencesForPatterns.entrySet()){
      runParallelApplyPats(en.getValue(), label, en.getKey(), wordsandLemmaPatExtracted, matchedTokensByPat, alreadyLabeledWords);
    }

    Redwood.log(Redwood.DBG, "# words/lemma and pattern pairs are " + wordsandLemmaPatExtracted.size());
  }
  /*
  public void applyPats(Counter<E> patterns, String label, boolean computeDataFreq,  TwoDimensionalCounter<Pair<String, String>, Integer> wordsandLemmaPatExtracted,
                        CollectionValuedMap<Integer, Triple<String, Integer, Integer>> matchedTokensByPat) throws ClassNotFoundException, IOException, InterruptedException, ExecutionException{
    Counter<E> patternsLearnedThisIterConsistsOnlyGeneralized = new ClassicCounter<E>();
    Counter<E> patternsLearnedThisIterRest = new ClassicCounter<E>();
    Set<String> specialWords = constVars.invertedIndex.getSpecialWordsList();
    List<String> extremelySmallStopWordsList = Arrays.asList(".",",","in","on","of","a","the","an");

    for(Entry<Integer, Double> en: patterns.entrySet()){
      Integer pindex = en.getKey();
      SurfacePattern p = constVars.getPatternIndex().get(pindex);
      String[] n = p.getSimplerTokensNext();
      String[] pr = p.getSimplerTokensPrev();
      boolean rest = false;
      if(n!=null){
        for(String e: n){
          if(!specialWords.contains(e)){
            rest = true;
            break;
          }
        }
      }
      if(rest == false && pr!=null){
        for(String e: pr){
          if(!specialWords.contains(e) && !extremelySmallStopWordsList.contains(e)){
            rest = true;
            break;
          }
        }
      }
      if(rest)
        patternsLearnedThisIterRest.setCount(en.getKey(), en.getValue());
      else
        patternsLearnedThisIterConsistsOnlyGeneralized.setCount(en.getKey(), en.getValue());
    }



    Map<String, Set<String>> sentidswithfilerest = constVars.invertedIndex.getFileSentIdsFromPats(patternsLearnedThisIterRest.keySet(), constVars.getPatternIndex());

    if (constVars.batchProcessSents) {
      List<File> filesToLoad;
      if(patternsLearnedThisIterConsistsOnlyGeneralized.size() > 0)
        filesToLoad = Data.sentsFiles;
      else{
        filesToLoad = new ArrayList<File>();
        for (String fname : sentidswithfilerest.keySet()) {
          String filename;
//          if(!constVars.usingDirForSentsInIndex)
//            filename = constVars.saveSentencesSerDir+"/"+fname;
//          else
            filename = fname;
          filesToLoad.add(new File(filename));
        }
      }

      for (File fname : filesToLoad) {
        Redwood.log(Redwood.DBG, "Applying patterns to sents from " + fname);
        Map<String, List<CoreLabel>> sents = IOUtils.readObjectFromFile(fname);

        if(sentidswithfilerest != null && !sentidswithfilerest.isEmpty()){

          String filename;
//          if(constVars.usingDirForSentsInIndex)
//            filename = constVars.saveSentencesSerDir+"/"+fname.getName();
//          else
            filename = fname.getAbsolutePath();

          Set<String> sentIDs = sentidswithfilerest.get(filename);
          if (sentIDs != null){
            this.runParallelApplyPats(sents, sentIDs, label, patternsLearnedThisIterRest, wordsandLemmaPatExtracted, matchedTokensByPat);
          } else
            Redwood.log(Redwood.DBG, "No sentIds for " + filename  + " in the index for the keywords from the patterns! The index came up with these files: " + sentidswithfilerest.keySet());
        }
        if(patternsLearnedThisIterConsistsOnlyGeneralized.size() > 0){
          this.runParallelApplyPats(sents, sents.keySet(), label, patternsLearnedThisIterConsistsOnlyGeneralized, wordsandLemmaPatExtracted, matchedTokensByPat);
        }

        if (computeDataFreq){
          Data.computeRawFreqIfNull(sents, constVars.numWordsCompound);
          Data.fileNamesUsedToComputeRawFreq.add(fname.getName());
        }
      }

      //Compute Frequency from the files not loaded using the invertedindex query. otherwise, later on there is an error.
      if(computeDataFreq){
        for(File f: Data.sentsFiles){
          if(!Data.fileNamesUsedToComputeRawFreq.contains(f.getName())){
            Map<String, List<CoreLabel>> sents = IOUtils.readObjectFromFile(f);
            Data.computeRawFreqIfNull(sents, constVars.numWordsCompound);
            Data.fileNamesUsedToComputeRawFreq.add(f.getName());
          }
        }
      }

    } else {

      if (sentidswithfilerest != null && !sentidswithfilerest.isEmpty()) {
        String filename = CollectionUtils.toList(sentidswithfilerest.keySet()).get(0);
        Set<String> sentids = sentidswithfilerest.get(filename);
        if (sentids != null) {
          this.runParallelApplyPats(Data.sents, sentids, label, patternsLearnedThisIterRest, wordsandLemmaPatExtracted, matchedTokensByPat);
        } else
          throw new RuntimeException("How come no sentIds for " + filename  + ". Index keyset is " + constVars.invertedIndex.getKeySet());
      }
      if(patternsLearnedThisIterConsistsOnlyGeneralized.size() > 0){
        this.runParallelApplyPats(Data.sents, Data.sents.keySet(), label, patternsLearnedThisIterConsistsOnlyGeneralized, wordsandLemmaPatExtracted, matchedTokensByPat);
      }
      Data.computeRawFreqIfNull(Data.sents, constVars.numWordsCompound);
    }
    Redwood.log(Redwood.DBG, "# words/lemma and pattern pairs are " + wordsandLemmaPatExtracted.size());
  }
  */

  private void statsWithoutApplyingPatterns(Map<String, DataInstance> sents, PatternsForEachToken patternsForEachToken,
      Counter<E> patternsLearnedThisIter, TwoDimensionalCounter<CandidatePhrase, E> wordsandLemmaPatExtracted){
    for (Entry<String, DataInstance> sentEn : sents.entrySet()) {
      Map<Integer, Set<E>> pat4Sent = patternsForEachToken.getPatternsForAllTokens(sentEn.getKey());

      if (pat4Sent == null) {
        throw new RuntimeException("How come there are no patterns for "
            + sentEn.getKey());
      }
      for (Entry<Integer, Set<E>> en : pat4Sent
          .entrySet()) {
        CoreLabel token = null;
        Set<E> p1 = en.getValue();

//        Set<Integer> p1 = en.getValue().first();
//        Set<Integer> p2 = en.getValue().second();
//        Set<Integer> p3 = en.getValue().third();
        for (E index : patternsLearnedThisIter.keySet()) {

          if (p1.contains(index)) {
            if (token == null)
              token = sentEn.getValue().getTokens().get(en.getKey());
            wordsandLemmaPatExtracted.incrementCount(CandidatePhrase.createOrGet(token.word(), token.lemma()), index);
          }
        }
      }
    }
  }

  private Counter<CandidatePhrase> learnNewPhrasesPrivate(
    String label,
    PatternsForEachToken patternsForEachToken,
    Counter<E> patternsLearnedThisIter,
    Counter<E> allSelectedPatterns,
    Set<CandidatePhrase> alreadyIdentifiedWords, CollectionValuedMap<E, Triple<String, Integer, Integer>> matchedTokensByPat,
    Counter<CandidatePhrase> scoreForAllWordsThisIteration,
    TwoDimensionalCounter<CandidatePhrase, E> terms,
    TwoDimensionalCounter<CandidatePhrase, E> wordsPatExtracted,
    TwoDimensionalCounter<E, CandidatePhrase> patternsAndWords4Label,
    String identifier, Set<CandidatePhrase> ignoreWords, boolean computeProcDataFreq) throws IOException, ClassNotFoundException {

    Set<CandidatePhrase> alreadyLabeledWords = new HashSet<>();
    if (constVars.doNotApplyPatterns) {
      // if want to get the stats by the lossy way of just counting without
      // applying the patterns
      ConstantsAndVariables.DataSentsIterator sentsIter = new ConstantsAndVariables.DataSentsIterator(constVars.batchProcessSents);
      while(sentsIter.hasNext()) {
        Pair<Map<String, DataInstance>, File> sentsf = sentsIter.next();
        this.statsWithoutApplyingPatterns(sentsf.first(), patternsForEachToken, patternsLearnedThisIter, wordsPatExtracted);
      }
    } else {
      if (patternsLearnedThisIter.size() > 0) {
        this.applyPats(patternsLearnedThisIter, label, wordsPatExtracted, matchedTokensByPat, alreadyLabeledWords);

      }
    }
    if(computeProcDataFreq){
      if (!phraseScorer.wordFreqNorm.equals(Normalization.NONE)) {
        Redwood.log(Redwood.DBG, "computing processed freq");
        for (Entry<CandidatePhrase, Double> fq : Data.rawFreq.entrySet()) {
          Double in = fq.getValue();
          if (phraseScorer.wordFreqNorm.equals(Normalization.SQRT))
            in = Math.sqrt(in);

          else if (phraseScorer.wordFreqNorm.equals(Normalization.LOG))
            in = 1 + Math.log(in);
          else
            throw new RuntimeException("can't understand the normalization");

          assert !in.isNaN(): "Why is processed freq nan when rawfreq is " + in;

          Data.processedDataFreq.setCount(fq.getKey(), in);
        }
      } else
        Data.processedDataFreq = Data.rawFreq;
    }

    if (constVars.wordScoring.equals(WordScoring.WEIGHTEDNORM)) {

      for (CandidatePhrase en : wordsPatExtracted.firstKeySet()) {

        if (!constVars.getOtherSemanticClassesWords().contains(en) && (en.getPhraseLemma() ==null || !constVars.getOtherSemanticClassesWords().contains(CandidatePhrase.createOrGet(en.getPhraseLemma()))) && !alreadyLabeledWords.contains(en)){
          terms.addAll(en, wordsPatExtracted.getCounter(en));
        }
      }
      removeKeys(terms, constVars.getStopWords());

      Counter<CandidatePhrase> phraseScores = phraseScorer.scorePhrases(label,
          terms, wordsPatExtracted, allSelectedPatterns,
          alreadyIdentifiedWords, false);
      System.out.println("count for word U.S. is " + phraseScores.getCount(CandidatePhrase.createOrGet("U.S.")));
      Set<CandidatePhrase> ignoreWordsAll ;
      if(ignoreWords !=null && !ignoreWords.isEmpty()){
        ignoreWordsAll = CollectionUtils.unionAsSet(ignoreWords, constVars.getOtherSemanticClassesWords());
      }
      else
        ignoreWordsAll = new HashSet<>(constVars.getOtherSemanticClassesWords());

      ignoreWordsAll.addAll(constVars.getSeedLabelDictionary().get(label));
      ignoreWordsAll.addAll(constVars.getLearnedWords(label).keySet());
      System.out.println("ignoreWordsAll contains word U.S. is " + ignoreWordsAll.contains(CandidatePhrase.createOrGet("U.S.")));

      Counter<CandidatePhrase> finalwords = chooseTopWords(phraseScores, terms,
          phraseScores, ignoreWordsAll, constVars.thresholdWordExtract);

      phraseScorer.printReasonForChoosing(finalwords);

      scoreForAllWordsThisIteration.clear();
      Counters.addInPlace(scoreForAllWordsThisIteration, phraseScores);

      Redwood.log(
          ConstantsAndVariables.minimaldebug,
          "\n\n## Selected Words for " + label + " : "
              + Counters.toSortedString(finalwords, finalwords.size(),
                  "%1$s:%2$.2f", "\t"));

      if(constVars.goldEntities != null){
        Map<String, Boolean> goldEntities4Label = constVars.goldEntities.get(label);
        if(goldEntities4Label != null) {
          StringBuffer s = new StringBuffer();
          finalwords.keySet().stream().forEach(x ->
            s.append(x.getPhrase() + (goldEntities4Label.containsKey(x.getPhrase()) ? ":"+goldEntities4Label.get(x.getPhrase()) : ":UKNOWN")+"\n"));

          Redwood.log(ConstantsAndVariables.minimaldebug,
            "\n\n## Gold labels for selected words for label " + label + " : " + s.toString());
        } else
          Redwood.log(Redwood.DBG, "No gold entities provided for label " + label);
      }

      if (constVars.outDir != null && !constVars.outDir.isEmpty()) {
        String outputdir = constVars.outDir + "/" + identifier +"/"+ label;
        IOUtils.ensureDir(new File(outputdir));
        TwoDimensionalCounter<CandidatePhrase, CandidatePhrase> reasonForWords = new TwoDimensionalCounter<>();
        for (CandidatePhrase word : finalwords.keySet()) {
          for (E l : wordsPatExtracted.getCounter(word).keySet()) {
            for (CandidatePhrase w2 : patternsAndWords4Label.getCounter(l)) {
              reasonForWords.incrementCount(word, w2);
            }
          }
        }
        Redwood.log(ConstantsAndVariables.minimaldebug, "Saving output in " + outputdir);
        String filename = outputdir + "/words.json";

        // the json object is an array corresponding to each iteration - of list
        // of objects,
        // each of which is a bean of entity and reasons

        JsonArrayBuilder obj = Json.createArrayBuilder();
        if (writtenInJustification.containsKey(label)
            && writtenInJustification.get(label)) {
          JsonReader jsonReader = Json.createReader(new BufferedInputStream(
              new FileInputStream(filename)));
          JsonArray objarr = jsonReader.readArray();
          for (JsonValue o : objarr)
            obj.add(o);
          jsonReader.close();

        }
        JsonArrayBuilder objThisIter = Json.createArrayBuilder();

        for (CandidatePhrase w : reasonForWords.firstKeySet()) {
          JsonObjectBuilder objinner = Json.createObjectBuilder();

          JsonArrayBuilder l = Json.createArrayBuilder();
          for (CandidatePhrase w2 : reasonForWords.getCounter(w).keySet()) {
            l.add(w2.getPhrase());
          }
          JsonArrayBuilder pats = Json.createArrayBuilder();
          for (E p : wordsPatExtracted.getCounter(w)) {
            pats.add(p.toStringSimple());
          }
          objinner.add("reasonwords", l);
          objinner.add("patterns", pats);
          objinner.add("score", finalwords.getCount(w));
          objinner.add("entity", w.getPhrase());
          objThisIter.add(objinner.build());
        }
        obj.add(objThisIter);

        // Redwood.log(ConstantsAndVariables.minimaldebug, channelNameLogger,
        // "Writing justification at " + filename);
        IOUtils.writeStringToFile(StringUtils.normalize(StringUtils.toAscii(obj.build().toString())), filename, "ASCII");
        writtenInJustification.put(label, true);
      }
      if (constVars.justify) {
        Redwood.log(Redwood.DBG, "\nJustification for phrases:\n");
        for (CandidatePhrase word : finalwords.keySet()) {
          Redwood.log(
              Redwood.DBG, "Phrase " +
              word
                  + " extracted because of patterns: \t"
                  + Counters.toSortedString(wordsPatExtracted.getCounter(word),
                      wordsPatExtracted.getCounter(word).size(), "%1$s:%2$f",
                      "\n"));
        }
      }
      // if (usePatternResultAsLabel)
      // if (answerLabel != null)
      // labelWords(sents, commonEngWords, finalwords.keySet(),
      // patterns.keySet(), outFile);
      // else
      // throw new RuntimeException("why is the answer label null?");

      return finalwords;
    } else if (constVars.wordScoring.equals(WordScoring.BPB)) {
      Counters.addInPlace(terms, wordsPatExtracted);
      Counter<CandidatePhrase> maxPatWeightTerms = new ClassicCounter<>();
      Map<CandidatePhrase, E> wordMaxPat = new HashMap<>();
      for (Entry<CandidatePhrase, ClassicCounter<E>> en : terms.entrySet()) {
        Counter<E> weights = new ClassicCounter<>();
        for (E k : en.getValue().keySet())
          weights.setCount(k, patternsLearnedThisIter.getCount(k));
        maxPatWeightTerms.setCount(en.getKey(), Counters.max(weights));
        wordMaxPat.put(en.getKey(), Counters.argmax(weights));
      }
      Counters.removeKeys(maxPatWeightTerms, alreadyIdentifiedWords);
      double maxvalue = Counters.max(maxPatWeightTerms);
      Set<CandidatePhrase> words = Counters.keysAbove(maxPatWeightTerms,
          maxvalue - 1e-10);
      CandidatePhrase bestw = null;
      if (words.size() > 1) {
        double max = Double.NEGATIVE_INFINITY;
        for (CandidatePhrase w : words) {
          if (terms.getCount(w, wordMaxPat.get(w)) > max) {
            max = terms.getCount(w, wordMaxPat.get(w));
            bestw = w;
          }
        }
      } else if (words.size() == 1)
        bestw = words.iterator().next();
      else
        return new ClassicCounter<>();

      Redwood.log(ConstantsAndVariables.minimaldebug, "Selected Words: " + bestw);

      return Counters.asCounter(Arrays.asList(bestw));
    }

    else
      throw new RuntimeException("wordscoring " + constVars.wordScoring
          + " not identified");
  }

  // private void combineExternalFeatures(Counter<String> words) {
  //
  // for (Entry<String, Double> en : words.entrySet()) {
  // Integer num = constVars.distSimClusters.get(en.getKey());
  // if (num == null)
  // num = -1;
  // // Double score = externalWeights.getCount(num);
  // // if not present in the clusters, take minimum of the scores of the
  // // individual words
  // // if (num == null) {
  // // for (String w : en.getKey().split("\\s+")) {
  // // Integer n = constVars.distSimClusters.get(w);
  // // if (n == null)
  // // continue;
  // // score = Math.min(score, externalWeights.getCount(n));
  // // }
  // // }
  // words.setCount(en.getKey(), en.getValue() *
  // constVars.distSimWeights.getCount(num));
  // }
  // }

  Counter<String> getLearnedScores() {
    return phraseScorer.getLearnedScores();
  }

  // private Counter<String> getLookAheadWeights(Counter<String> words,
  // Counter<String> externalWordWeights, Set<String> alreadyIdentifiedWords,
  // String label,
  // Counter<SurfacePattern> currentAllPatternWeights,
  // TwoDimensionalCounter<SurfacePattern, String> allPatternsandWords) throws
  // IOException {
  // System.out.println("size of patterns weight counter is " +
  // currentAllPatternWeights.size());
  //
  // DirectedWeightedMultigraph<String, DefaultWeightedEdge> graph = new
  // DirectedWeightedMultigraph<String,
  // DefaultWeightedEdge>(org.jgrapht.graph.DefaultWeightedEdge.class);
  //
  // if (Data.googleNGram.size() == 0) {
  // Data.loadGoogleNGrams();
  // }
  //
  // TwoDimensionalCounter<String, SurfacePattern> allPatsAndWords =
  // TwoDimensionalCounter.reverseIndexOrder(allPatternsandWords);
  // System.out.println("We have patterns for " + allPatsAndWords.size() +
  // " words ");
  // TwoDimensionalCounter<String, String> lookaheadweights = new
  // TwoDimensionalCounter<String, String>();
  // // Counter<String> weights = new ClassicCounter<String>();
  //
  // for (Entry<String, Double> en : words.entrySet()) {
  // Counter<SurfacePattern> pats = new
  // ClassicCounter<SurfacePattern>(allPatsAndWords.getCounter(en.getKey()));
  // for (SurfacePattern p : pats.keySet()) {
  // pats.setCount(p, pats.getCount(p) * currentAllPatternWeights.getCount(p));
  // }
  //
  // for (Pair<SurfacePattern, Double> p : Counters.topKeysWithCounts(pats, 10))
  // {
  //
  // for (Entry<String, Double> pen :
  // allPatternsandWords.getCounter(p.first()).entrySet()) {
  // if (pen.getKey().equals(en.getKey()) ||
  // alreadyIdentifiedWords.contains(pen.getKey()) ||
  // constVars.otherSemanticClasses.contains(pen.getKey()))
  // continue;
  //
  // double ngramWt = 1.0;
  // if (Data.googleNGram.containsKey(pen.getKey())) {
  // assert (Data.rawFreq.containsKey(pen.getKey()));
  // ngramWt = (1 + Data.rawFreq.getCount(pen.getKey())) / (Data.rawFreq.size()
  // + Data.googleNGram.getCount(pen.getKey()));
  // }
  // double wordweight = ngramWt;// (minExternalWordWeight +
  // // externalWordWeights.getCount(pen.getKey()))
  // // * p.second() * (0.1 +
  // // currentAllPatternWeights.getCount(p.first()))
  // // * ;
  // // if (wordweight != 0)
  // if (wordweight == 0) {
  // // System.out.println("word weight is zero for " + pen.getKey() +
  // // " and the weights were " +
  // // externalWordWeights.getCount(pen.getKey()) + ";" + p.second() +
  // // ";"
  // // + (0.1 + currentPatternWeights.getCount(p.first())) + ";" +
  // // ngramWt);
  // } else {
  // lookaheadweights.setCount(en.getKey(), pen.getKey(), Math.log(wordweight));
  // graph.addVertex(en.getKey());
  // graph.addVertex(pen.getKey());
  // DefaultWeightedEdge e = graph.addEdge(en.getKey(), pen.getKey());
  // graph.setEdgeWeight(e, lookaheadweights.getCount(en.getKey(),
  // pen.getKey()));
  // }
  //
  // }
  //
  // }
  // // weights.setCount(en.getKey(),
  // // Math.exp(Counters(lookaheadweights.getCounter(en.getKey()))));
  //
  // }
  // Counter<String> weights = new ClassicCounter<String>();
  // for (Entry<String, ClassicCounter<String>> en :
  // lookaheadweights.entrySet()) {
  // // List<Pair<String, Double>> sorted =
  // // Counters.toSortedListWithCounts(en.getValue());
  // // double val = sorted.get((int) Math.floor(sorted.size() / 2)).second();
  // double wt = Math.exp(en.getValue().totalCount() / en.getValue().size());
  //
  // weights.setCount(en.getKey(), wt);
  // }
  // // Counters.expInPlace(weights);
  // // List<String> tk = Counters.topKeys(weights, 50);
  // // BufferedWriter w = new BufferedWriter(new FileWriter("lookahead_" +
  // // answerLabel, true));
  // // for (String s : tk) {
  // // w.write(s + "\t" + weights.getCount(s) + "\t" +
  // // lookaheadweights.getCounter(s) + "\n");
  // // }
  // // w.close();
  // // BufferedWriter writer = new BufferedWriter(new FileWriter("graph.gdf"));
  // // writeGraph(writer, graph);
  // System.out.println("done writing graph");
  // Redwood.log(ConstantsAndVariables.minimaldebug, "calculated look ahead weights for " +
  // weights.size() + " words");
  //
  // return weights;
  // }

  // void writeGraph(BufferedWriter w, DirectedWeightedMultigraph<String,
  // DefaultWeightedEdge> g) throws IOException {
  // w.write("nodedef>name VARCHAR\n");
  // for (String n : g.vertexSet()) {
  // w.write(n + "\n");
  // }
  // w.write("edgedef>node1 VARCHAR,node2 VARCHAR, weight DOUBLE\n");
  // for (DefaultWeightedEdge e : g.edgeSet()) {
  // w.write(g.getEdgeSource(e) + "," + g.getEdgeTarget(e) + "," +
  // g.getEdgeWeight(e) + "\n");
  // }
  // w.close();
  // }

}
