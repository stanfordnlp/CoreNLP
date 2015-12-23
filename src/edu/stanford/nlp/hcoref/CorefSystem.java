package edu.stanford.nlp.hcoref;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import edu.stanford.nlp.hcoref.data.CorefChain;
import edu.stanford.nlp.hcoref.data.CorefCluster;
import edu.stanford.nlp.hcoref.data.Dictionaries;
import edu.stanford.nlp.hcoref.data.Document;
import edu.stanford.nlp.hcoref.data.Mention;
import edu.stanford.nlp.hcoref.sieve.Sieve;
import edu.stanford.nlp.hcoref.sieve.Sieve.ClassifierType;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.RuntimeInterruptedException;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;
import edu.stanford.nlp.util.logging.Redwood;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;

public class CorefSystem {
  
  public Properties props;
  public List<Sieve> sieves;
  public Dictionaries dictionaries;
  public CorefDocMaker docMaker = null;
  
  public CorefSystem(Properties props) throws Exception {
    this.props = props;
    sieves = Sieve.loadSieves(props);
    
    // set semantics loading
    for(Sieve sieve : sieves) {
      if(sieve.classifierType == ClassifierType.RULE) continue;
      if(CorefProperties.useWordEmbedding(props, sieve.sievename)) {
        props.setProperty(CorefProperties.LOAD_WORD_EMBEDDING_PROP, "true");
      }
    }
    dictionaries = new Dictionaries(props);

    docMaker = new CorefDocMaker(props, dictionaries);
  }
  
  public Dictionaries dictionaries() { return dictionaries; }


  public static void runCoref(String[] args) throws Exception {
    Redwood.hideChannelsEverywhere(
        "debug-cluster", "debug-mention", "debug-preprocessor", "debug-docreader", "debug-mergethres",
        "debug-featureselection", "debug-md"
        );
    
    /*
       property, environment setting
    */
    Properties props = StringUtils.argsToProperties(args);
    int nThreads = CorefProperties.getThreadCounts(props); 
    String timeStamp = Calendar.getInstance().getTime().toString().replaceAll("\\s", "-").replaceAll(":", "-");
    props.put(CorefProperties.PATH_INPUT_PROP, CorefProperties.getPathEvalData(props));
    
    Logger logger = Logger.getLogger(CorefSystem.class.getName());
    
    // set log file path
    if(props.containsKey(CorefProperties.LOG_PROP)){
      File logFile = new File(props.getProperty(CorefProperties.LOG_PROP));
      RedwoodConfiguration.current().handlers(
      RedwoodConfiguration.Handlers.file(logFile)).apply();
      Redwood.log("Starting coref log");
    }
    
    System.err.println(props.toString());
    
    if(CorefProperties.checkMemory(props)) checkMemoryUsage();

    CorefSystem cs = new CorefSystem(props);

    /*
       output setting
    */
    // prepare conll output
    String goldOutput = null;
    String beforeCorefOutput = null;
    String afterCorefOutput = null;
    PrintWriter writerGold = null;
    PrintWriter writerBeforeCoref = null;
    PrintWriter writerAfterCoref = null;
    if(CorefProperties.doScore(props)) {
      String pathOutput = CorefProperties.getPathOutput(props);
      (new File(pathOutput)).mkdir();
      goldOutput = pathOutput + "output-" + timeStamp + ".gold.txt";
      beforeCorefOutput = pathOutput + "output-" + timeStamp + ".predicted.txt";
      afterCorefOutput = pathOutput + "output-" + timeStamp + ".coref.predicted.txt";
      writerGold = new PrintWriter(new FileOutputStream(goldOutput));
      writerBeforeCoref = new PrintWriter(new FileOutputStream(beforeCorefOutput));
      writerAfterCoref = new PrintWriter(new FileOutputStream(afterCorefOutput));
    }
    
    
    /*
       run coref
    */
    MulticoreWrapper<Pair<Document, CorefSystem>, StringBuilder[]> wrapper = new MulticoreWrapper<>(
            nThreads, new ThreadsafeProcessor<Pair<Document, CorefSystem>, StringBuilder[]>() {
      @Override
      public StringBuilder[] process(Pair<Document, CorefSystem> input) {
        try {
          Document document = input.first;
          CorefSystem cs = input.second;

          StringBuilder[] outputs = new StringBuilder[4];    // conll output and logs

          cs.coref(document, outputs);

          return outputs;

        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public ThreadsafeProcessor<Pair<Document, CorefSystem>, StringBuilder[]> newInstance() {
        return this;
      }
    });
    
    Date startTime = null;
    if(CorefProperties.checkTime(props)) {
      startTime = new Date();
      System.err.printf("END-TO-END COREF Start time: %s\n", startTime);
    }
    
    // run processes
    int docCnt = 0;
    while (true) {
      Document document = cs.docMaker.nextDoc();
      if (document == null) break;
      wrapper.put(Pair.makePair(document, cs));
      docCnt = logOutput(wrapper, writerGold, writerBeforeCoref, writerAfterCoref, docCnt);
    }
    
    // Finished reading the input. Wait for jobs to finish
    wrapper.join();
    docCnt = logOutput(wrapper, writerGold, writerBeforeCoref, writerAfterCoref, docCnt);
    writerGold.close();
    writerBeforeCoref.close();
    writerAfterCoref.close();
    
    if(CorefProperties.checkTime(props)) {
      System.err.printf("END-TO-END COREF Elapsed time: %.3f seconds\n", (((new Date()).getTime() - startTime.getTime()) / 1000F));
//      System.err.printf("CORENLP PROCESS TIME TOTAL: %.3f seconds\n", cs.mentionExtractor.corenlpProcessTime);
    }
    if(CorefProperties.checkMemory(props)) checkMemoryUsage();
    
    
    /*
       scoring
    */
    if(CorefProperties.doScore(props)) {
      String summary = Scorer.getEvalSummary(CorefProperties.getPathScorer(props), goldOutput, beforeCorefOutput);
      CorefPrinter.printScoreSummary(summary, logger, false);
      
      summary = Scorer.getEvalSummary(CorefProperties.getPathScorer(props), goldOutput, afterCorefOutput);
      CorefPrinter.printScoreSummary(summary, logger, true);
      CorefPrinter.printFinalConllScore(summary);
    }
  }
  
  /**
   *  write output of coref system in conll format, and log. 
   */
  private static int logOutput(
      MulticoreWrapper<Pair<Document, CorefSystem>, StringBuilder[]> wrapper,
      PrintWriter writerGold, 
      PrintWriter writerBeforeCoref,
      PrintWriter writerAfterCoref, 
      int docCnt) {
    while (wrapper.peek()) {
      StringBuilder[] output = wrapper.poll();
      writerGold.print(output[0]);
      writerBeforeCoref.print(output[1]);
      writerAfterCoref.print(output[2]);
      System.err.println(output[3]);
      if ((docCnt++) % 10 == 0) System.err.println(docCnt + " document(s) processed");
    }
    return docCnt;
  }

  /** 
   * main entry of coreference system.
   * 
   * @param document Input document for coref format (Annotation and optional information)
   * @param output For output of coref system (conll format and log. list size should be 4.)
   * @return Map of coref chain ID and corresponding chain
   * @throws Exception
   */
  public Map<Integer, CorefChain> coref(Document document, StringBuilder[] output) throws Exception {

    if(CorefProperties.printMDLog(props)) {
      Redwood.log(CorefPrinter.printMentionDetectionLog(document));
    }
    
    if(CorefProperties.doScore(props)) {
      output[0] = (new StringBuilder()).append(CorefPrinter.printConllOutput(document, true));  // gold
      output[1] = (new StringBuilder()).append(CorefPrinter.printConllOutput(document, false)); // before coref
    }
    output[3] = new StringBuilder();  // log from sieves
    
    for(Sieve sieve : sieves){
      if (Thread.interrupted()) {  // Allow interrupting
        throw new RuntimeInterruptedException();
      }
      output[3].append(sieve.resolveMention(document, dictionaries, props));
    }

    // post processing
    if(CorefProperties.doPostProcessing(props)) postProcessing(document);

    if(CorefProperties.doScore(props)) {
      output[2] = (new StringBuilder()).append(CorefPrinter.printConllOutput(document, false, true)); // after coref
    }
    
    return makeCorefOutput(document);
  }
  
  /** 
   * main entry of coreference system.
   * 
   * @param document Input document for coref format (Annotation and optional information)
   * @return Map of coref chain ID and corresponding chain
   * @throws Exception
   */
  public Map<Integer, CorefChain> coref(Document document) throws Exception {
    return coref(document, new StringBuilder[4]);
  }

  /** 
   * main entry of coreference system.
   * 
   * @param anno Input annotation.
   * @return Map of coref chain ID and corresponding chain
   * @throws Exception
   */
  public Map<Integer, CorefChain> coref(Annotation anno) throws Exception {
    return coref(docMaker.makeDocument(anno));
  }
  
  /** extract final coreference output from coreference document format */
  public Map<Integer, CorefChain> makeCorefOutput(Document document) {
    Map<Integer, CorefChain> result = Generics.newHashMap();
    for(CorefCluster c : document.corefClusters.values()) {
      result.put(c.clusterID, new CorefChain(c, document.positions));
    }
    return result;
  }

  /** Remove singletons, appositive, predicate nominatives, relative pronouns */
  public void postProcessing(Document document) {
    Set<Mention> removeSet = Generics.newHashSet();
    Set<Integer> removeClusterSet = Generics.newHashSet();

    for(CorefCluster c : document.corefClusters.values()){
      Set<Mention> removeMentions = Generics.newHashSet();
      for(Mention m : c.getCorefMentions()) {
        if(CorefProperties.REMOVE_APPOSITION_PREDICATENOMINATIVES
            && ((m.appositions!=null && m.appositions.size() > 0)
                || (m.predicateNominatives!=null && m.predicateNominatives.size() > 0)
                || (m.relativePronouns!=null && m.relativePronouns.size() > 0))){
          removeMentions.add(m);
          removeSet.add(m);
          m.corefClusterID = m.mentionID;
        }
      }
      
      c.corefMentions.removeAll(removeMentions);
      if(CorefProperties.REMOVE_SINGLETONS && c.getCorefMentions().size()==1) {
        removeClusterSet.add(c.clusterID);
      }
    }
    for (int removeId : removeClusterSet){
      document.corefClusters.remove(removeId);
    }
    for(Mention m : removeSet){
      document.positions.remove(m);
    }
  }

  private static void checkMemoryUsage() {
    Runtime runtime = Runtime.getRuntime();
    runtime.gc();
    long memory = runtime.totalMemory() - runtime.freeMemory();
    System.err.println("USED MEMORY (bytes): " + memory);
  }
  /** Remove singleton clusters */
  public static List<List<Mention>> filterMentionsWithSingletonClusters(Document document, List<List<Mention>> mentions)
  {

    List<List<Mention>> res = new ArrayList<>(mentions.size());
    for (List<Mention> ml:mentions) {
      List<Mention> filtered = new ArrayList<>();
      for (Mention m:ml) {
        CorefCluster cluster = document.corefClusters.get(m.corefClusterID);
        if (cluster != null && cluster.getCorefMentions().size() > 1) {
          filtered.add(m);
        }
      }
      res.add(filtered);
    }
    return res;
  }
  public static void main(String[] args) throws Exception {
    Date startTime = new Date();
    System.err.printf("Start time: %s\n", startTime);
    runCoref(args);
    System.err.printf("Elapsed time: %.3f seconds\n", (((new Date()).getTime() - startTime.getTime()) / 1000F));
  }
}
