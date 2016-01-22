package edu.stanford.nlp.patterns.surface;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import edu.stanford.nlp.patterns.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

public class CreatePatterns<E> {

  //String channelNameLogger = "createpatterns";

  ConstantsAndVariables constVars;

  public CreatePatterns(Properties props, ConstantsAndVariables constVars)
      throws IOException {
    this.constVars = constVars;
    ArgumentParser.fillOptions(ConstantsAndVariables.class, props);
    constVars.setUp(props);
    setUp(props);
  }

  void setUp(Properties props) {
    ArgumentParser.fillOptions(this, props);
  }


  /**
   * creates all patterns and saves them in the correct PatternsForEachToken* class appropriately
   * @param sents
   * @param props
   * @param storePatsForEachTokenWay
   */
  public void getAllPatterns(Map<String, DataInstance> sents, Properties props, ConstantsAndVariables.PatternForEachTokenWay storePatsForEachTokenWay) {

//    this.patternsForEachToken = new HashMap<String, Map<Integer, Triple<Set<Integer>, Set<Integer>, Set<Integer>>>>();
   // this.patternsForEachToken = new HashMap<String, Map<Integer, Set<Integer>>>();

    Date startDate = new Date();
    List<String> keyset = new ArrayList<>(sents.keySet());

    int num;
    if (constVars.numThreads == 1)
      num = keyset.size();
    else
      num = keyset.size() / (constVars.numThreads);
    ExecutorService executor = Executors
        .newFixedThreadPool(constVars.numThreads);

    Redwood.log(ConstantsAndVariables.extremedebug, "Computing all patterns. keyset size is " + keyset.size() + ". Assigning " + num + " values to each thread");
    List<Future<Boolean>> list = new ArrayList<>();
    for (int i = 0; i < constVars.numThreads; i++) {

      int from = i * num;
      int to = -1;
      if(i == constVars.numThreads -1)
        to = keyset.size();
      else
       to =Math.min(keyset.size(), (i + 1) * num);
//
//      Redwood.log(ConstantsAndVariables.extremedebug, "assigning from " + i * num
//          + " till " + Math.min(keyset.size(), (i + 1) * num));

      List<String> ids = keyset.subList(from ,to);
      Callable<Boolean> task = new CreatePatternsThread(sents, ids, props, storePatsForEachTokenWay);

      Future<Boolean> submit = executor
          .submit(task);
      list.add(submit);
    }

    // Now retrieve the result

    for (Future<Boolean> future : list) {
      try{
        future.get();
        //patternsForEachToken.putAll(future.get());
      } catch(Exception e){
        executor.shutdownNow();
        throw new RuntimeException(e);
      }
    }
    executor.shutdown();

    Date endDate = new Date();

    String timeTaken = GetPatternsFromDataMultiClass.elapsedTime(startDate, endDate);
    Redwood.log(Redwood.DBG, "Done computing all patterns ["+timeTaken+"]");
    //return patternsForEachToken;
  }

//  /**
//   * Returns null if using DB backed!!
//   * @return
//   */
//  public Map<String, Map<Integer, Set<Integer>>> getPatternsForEachToken() {
//    return patternsForEachToken;
//  }

  public class CreatePatternsThread
      implements
      Callable<Boolean> {

    //String label;
    // Class otherClass;
    Map<String, DataInstance> sents;
    List<String> sentIds;
    PatternsForEachToken<E> patsForEach;

    public CreatePatternsThread(Map<String, DataInstance> sents, List<String> sentIds, Properties props, ConstantsAndVariables.PatternForEachTokenWay storePatsForEachToken) {

      //this.label = label;
      // this.otherClass = otherClass;
      this.sents = sents;
      this.sentIds = sentIds;
      this.patsForEach = PatternsForEachToken.getPatternsInstance(props, storePatsForEachToken);
    }

    @Override
    public Boolean call() throws Exception {
      Map<String, Map<Integer, Set<E>>> tempPatternsForTokens = new HashMap<>();
      int numSentencesInOneCommit = 0;

      for (String id : sentIds) {
        DataInstance sent = sents.get(id);

        if(!constVars.storePatsForEachToken.equals(ConstantsAndVariables.PatternForEachTokenWay.MEMORY))
          tempPatternsForTokens.put(id, new HashMap<>());

        Map<Integer, Set<E>> p  = (Map) PatternFactory.getPatternsAroundTokens(constVars.patternType, sent, constVars.getStopWords());
        //to save number of commits to the database
        if(!constVars.storePatsForEachToken.equals(ConstantsAndVariables.PatternForEachTokenWay.MEMORY)){
          tempPatternsForTokens.put(id, p);
          numSentencesInOneCommit++;
          if(numSentencesInOneCommit % 1000 == 0){
            patsForEach.addPatterns(tempPatternsForTokens);
            tempPatternsForTokens.clear();
            numSentencesInOneCommit = 0;
          }
//          patsForEach.addPatterns(id, p);

        }
        else
          patsForEach.addPatterns(id, p);

      }

      //For the remaining sentences
      if(!constVars.storePatsForEachToken.equals(ConstantsAndVariables.PatternForEachTokenWay.MEMORY))
        patsForEach.addPatterns(tempPatternsForTokens);

      return true;
    }

  }
}