package edu.stanford.nlp.patterns.surface;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.patterns.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

public class CreatePatterns<E> {





  //String channelNameLogger = "createpatterns";

  ConstantsAndVariables constVars;
  private Map<String, Map<Integer, Set<Integer>>> patternsForEachToken;

  //Map<String, Map<Integer, Set<Integer>>> patternsForEachToken ;

  public CreatePatterns(Properties props, ConstantsAndVariables constVars)
      throws IOException {
    this.constVars = constVars;
    Execution.fillOptions(ConstantsAndVariables.class, props);
    constVars.setUp(props);
    setUp(props);
  }

  void setUp(Properties props) {
    Execution.fillOptions(this, props);
  }



//  Triple<Boolean, String, String> getContextTokenStr(CoreLabel tokenj) {
//    String strgeneric = "";
//    String strOriginal = "";
//    boolean isLabeledO = true;
//    for (Entry<String, Class<? extends TypesafeMap.Key<String>>> e : constVars.getAnswerClass().entrySet()) {
//      if (!tokenj.get(e.getValue()).equals(constVars.backgroundSymbol)) {
//        isLabeledO = false;
//        if (strgeneric.isEmpty()) {
//          strgeneric = "{" + e.getKey() + ":" + e.getKey() + "}";
//          strOriginal = e.getKey();
//        } else {
//          strgeneric += " | " + "{" + e.getKey() + ":" + e.getKey() + "}";
//          strOriginal += "|" + e.getKey();
//        }
//      }
//    }
//
//    for (Entry<String, Class> e : constVars.getGeneralizeClasses().entrySet()) {
//      if (!tokenj.get(e.getValue()).equals(constVars.backgroundSymbol)) {
//        isLabeledO = false;
//        if (strgeneric.isEmpty()) {
//          strgeneric = "{" + e.getKey() + ":" + tokenj.get(e.getValue()) + "}";
//          strOriginal = e.getKey();
//        } else {
//          strgeneric += " | " + "{" + e.getKey() + ":"
//              + tokenj.get(e.getValue()) + "}";
//          strOriginal += "|" + e.getKey();
//        }
//      }
//    }
//
//    if (constVars.useContextNERRestriction) {
//      String nerTag = tokenj
//          .get(CoreAnnotations.NamedEntityTagAnnotation.class);
//      if (nerTag != null
//          && !nerTag.equals(SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL)) {
//        isLabeledO = false;
//        if (strgeneric.isEmpty()) {
//          strgeneric = "{ner:" + nerTag + "}";
//          strOriginal = nerTag;
//        } else {
//          strgeneric += " | " + "{ner:" + nerTag + "}";
//          strOriginal += "|" + nerTag;
//        }
//      }
//    }
//
//    return new Triple<Boolean, String, String>(isLabeledO, strgeneric,
//        strOriginal);
//  }



//  public Map<String, Map<Integer, Set<Integer>>> getPatternsForEachToken(){
//    return patternsForEachToken;
//  }

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
    List<String> keyset = new ArrayList<String>(sents.keySet());

    int num;
    if (constVars.numThreads == 1)
      num = keyset.size();
    else
      num = keyset.size() / (constVars.numThreads);
    ExecutorService executor = Executors
        .newFixedThreadPool(constVars.numThreads);

    Redwood.log(ConstantsAndVariables.extremedebug, "Computing all patterns. keyset size is " + keyset.size() + ". Assigning " + num + " values to each thread");
    List<Future<Map<String, Map<Integer, Set<Integer>>>>> list = new ArrayList<Future<Map<String, Map<Integer, Set<Integer>>>>>();
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

      Callable<Map<String, Map<Integer, Set<Integer>>>> task = null;
      List<String> ids = keyset.subList(from ,to);
      task = new CreatePatternsThread(sents, ids, props, storePatsForEachTokenWay);

      Future<Map<String, Map<Integer, Set<Integer>>>> submit = executor
          .submit(task);
      list.add(submit);
    }

    // Now retrieve the result

    for (Future<Map<String, Map<Integer, Set<Integer>>>> future : list) {
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

  /**
   * Returns null if using DB backed!!
   * @return
   */
  public Map<String, Map<Integer, Set<Integer>>> getPatternsForEachToken() {
    return patternsForEachToken;
  }

  public class CreatePatternsThread
      implements
      Callable<Map<String, Map<Integer, Set<Integer>>>> {

    //String label;
    // Class otherClass;
    Map<String, DataInstance> sents;
    List<String> sentIds;
    PatternsForEachToken patsForEach;

    public CreatePatternsThread(Map<String, DataInstance> sents, List<String> sentIds, Properties props, ConstantsAndVariables.PatternForEachTokenWay storePatsForEachToken) {

      //this.label = label;
      // this.otherClass = otherClass;
      this.sents = sents;
      this.sentIds = sentIds;
      this.patsForEach = PatternsForEachToken.getPatternsInstance(props, storePatsForEachToken);
    }

    @Override
    public Map<String, Map<Integer, Set<Integer>>> call() throws Exception {
      Map<String, Map<Integer, Set<E>>> tempPatternsForTokens = new HashMap<String, Map<Integer, Set<E>>>();
      int numSentencesInOneCommit = 0;

      for (String id : sentIds) {
        DataInstance sent = sents.get(id);
        List<CoreLabel> tokens = sent.getTokens();
        if(!constVars.storePatsForEachToken.equals(ConstantsAndVariables.PatternForEachTokenWay.MEMORY))
          tempPatternsForTokens.put(id, new HashMap<Integer, Set<E>>());

        Map<Integer, Set<E>> p = new HashMap<Integer, Set<E>>();
        for (int i = 0; i < tokens.size(); i++) {
//          p.put(
//              i,
//              new Triple<Set<Integer>, Set<Integer>, Set<Integer>>(
//                  new HashSet<Integer>(), new HashSet<Integer>(),
//                  new HashSet<Integer>()));
          p.put(i, new HashSet<E>());
          CoreLabel token = tokens.get(i);
          // do not create patterns around stop words!
          if (PatternFactory.doNotUse(token.word(), constVars.getStopWords())) {
            continue;
          }

          Set<E> pat = Pattern.getContext(constVars.patternType, sent, i);
          p.put(i, pat);

        }

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

      return null;
    }

  }
}