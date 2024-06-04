package edu.stanford.nlp.coref.hybrid;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import edu.stanford.nlp.coref.CorefAlgorithm;
import edu.stanford.nlp.coref.CorefPrinter;
import edu.stanford.nlp.coref.CorefProperties;
import edu.stanford.nlp.coref.CorefScorer;
import edu.stanford.nlp.coref.CorefUtils;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.coref.data.CorefCluster;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.data.DocumentMaker;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.coref.hybrid.sieve.Sieve;
import edu.stanford.nlp.coref.hybrid.sieve.Sieve.ClassifierType;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;
import edu.stanford.nlp.util.logging.Redwood;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;

public class HybridCorefSystem implements CorefAlgorithm {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(HybridCorefSystem.class);

  public Properties props;
  public List<Sieve> sieves;
  public Dictionaries dictionaries;
  public DocumentMaker docMaker = null;

  public HybridCorefSystem(Properties props, Dictionaries dictionaries) throws Exception {
    this.props = props;
    this.dictionaries = dictionaries;
    sieves = Sieve.loadSieves(props);

    // set semantics loading
    for(Sieve sieve : sieves) {
      if(sieve.classifierType == ClassifierType.RULE) continue;
      if(HybridCorefProperties.useWordEmbedding(props, sieve.sievename)) {
        props.setProperty(HybridCorefProperties.LOAD_WORD_EMBEDDING_PROP, "true");
      }
    }
  }

  public HybridCorefSystem(Properties props) throws Exception {
    this.props = props;
    sieves = Sieve.loadSieves(props);

    // set semantics loading
    for(Sieve sieve : sieves) {
      if(sieve.classifierType == ClassifierType.RULE) continue;
      if(HybridCorefProperties.useWordEmbedding(props, sieve.sievename)) {
        props.setProperty(HybridCorefProperties.LOAD_WORD_EMBEDDING_PROP, "true");
      }
    }
    dictionaries = new Dictionaries(props);

    docMaker = new DocumentMaker(props, dictionaries);
  }

  public Dictionaries dictionaries() { return dictionaries; }


  public static void runCoref(String[] args) throws Exception {
      runCoref(StringUtils.argsToProperties(args));
  }

  public static void runCoref(Properties props) throws Exception {
   /*
    * property, environment setting
    */
    Redwood.hideChannelsEverywhere(
            "debug-cluster", "debug-mention", "debug-preprocessor", "debug-docreader", "debug-mergethres",
            "debug-featureselection", "debug-md"
            );
    int nThreads = HybridCorefProperties.getThreadCounts(props);
    String timeStamp = Calendar.getInstance().getTime().toString().replaceAll("\\s", "-").replaceAll(":", "-");

    Logger logger = Logger.getLogger(HybridCorefSystem.class.getName());

    // set log file path
    if(props.containsKey(HybridCorefProperties.LOG_PROP)){
      File logFile = new File(props.getProperty(HybridCorefProperties.LOG_PROP));
      RedwoodConfiguration.current().handlers(
      RedwoodConfiguration.Handlers.file(logFile)).apply();
      Redwood.log("Starting coref log");
    }

    log.info(props.toString());

    if(HybridCorefProperties.checkMemory(props)) checkMemoryUsage();

    HybridCorefSystem cs = new HybridCorefSystem(props);

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
    if (HybridCorefProperties.doScore(props)) {
      String pathOutput = CorefProperties.conllOutputPath(props);
      (new File(pathOutput)).mkdir();
      goldOutput = pathOutput + "output-" + timeStamp + ".gold.txt";
      beforeCorefOutput = pathOutput + "output-" + timeStamp + ".predicted.txt";
      afterCorefOutput = pathOutput + "output-" + timeStamp + ".coref.predicted.txt";
      writerGold = new PrintWriter(new FileOutputStream(goldOutput));
      writerBeforeCoref = new PrintWriter(new FileOutputStream(beforeCorefOutput));
      writerAfterCoref = new PrintWriter(new FileOutputStream(afterCorefOutput));
    }

    // run coref
    MulticoreWrapper<Pair<Document, HybridCorefSystem>, StringBuilder[]> wrapper = new MulticoreWrapper<>(
            nThreads, new ThreadsafeProcessor<Pair<Document, HybridCorefSystem>, StringBuilder[]>() {
      @Override
      public StringBuilder[] process(Pair<Document, HybridCorefSystem> input) {
        try {
          Document document = input.first;
          HybridCorefSystem cs = input.second;

          StringBuilder[] outputs = new StringBuilder[4];    // conll output and logs

          cs.coref(document, outputs);

          return outputs;

        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public ThreadsafeProcessor<Pair<Document, HybridCorefSystem>, StringBuilder[]> newInstance() {
        return this;
      }
    });

    Date startTime = null;
    if(HybridCorefProperties.checkTime(props)) {
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
    IOUtils.closeIgnoringExceptions(writerGold);
    IOUtils.closeIgnoringExceptions(writerBeforeCoref);
    IOUtils.closeIgnoringExceptions(writerAfterCoref);

    if(HybridCorefProperties.checkTime(props)) {
      System.err.printf("END-TO-END COREF Elapsed time: %.3f seconds\n", (((new Date()).getTime() - startTime.getTime()) / 1000F));
//      System.err.printf("CORENLP PROCESS TIME TOTAL: %.3f seconds\n", cs.mentionExtractor.corenlpProcessTime);
    }
    if(HybridCorefProperties.checkMemory(props)) checkMemoryUsage();

    // scoring
    if (HybridCorefProperties.doScore(props)) {
      String scorerPath = CorefProperties.getScorerPath(props);
      String summary;
      try {
        summary = CorefScorer.getEvalSummary(scorerPath, goldOutput, beforeCorefOutput);
      } catch (CorefScorer.ScorerMissingException e) {
        throw new RuntimeException("Missing scorer!  Properties were:\n" + props);
      }
      CorefScorer.printScoreSummary(summary, logger, false);

      summary = CorefScorer.getEvalSummary(scorerPath, goldOutput, afterCorefOutput);
      CorefScorer.printScoreSummary(summary, logger, true);
      CorefScorer.printFinalConllScore(summary, logger);
    }
  }

  /**
   *  Write output of coref system in conll format, and log.
   */
  private static int logOutput(MulticoreWrapper<Pair<Document, HybridCorefSystem>, StringBuilder[]> wrapper,
                               PrintWriter writerGold,
                               PrintWriter writerBeforeCoref,
                               PrintWriter writerAfterCoref,
                               int docCnt) {
    while (wrapper.peek()) {
      StringBuilder[] output = wrapper.poll();
      writerGold.print(output[0]);
      writerBeforeCoref.print(output[1]);
      writerAfterCoref.print(output[2]);
      if (output[3].length() > 0) {
        log.info(output[3]);
      }
      if ((++docCnt) % 10 == 0) log.info(docCnt + " document(s) processed");
    }
    return docCnt;
  }

  @Override
  public void runCoref(Document document) {
    try {
      coref(document);
    } catch (Exception e) {
      throw new RuntimeException("Error running hybrid coref system", e);
    }
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
    if(HybridCorefProperties.printMDLog(props)) {
      Redwood.log(HybridCorefPrinter.printMentionDetectionLog(document));
    }

    if(HybridCorefProperties.doScore(props)) {
      output[0] = (new StringBuilder()).append(CorefPrinter.printConllOutput(document, true));  // gold
      output[1] = (new StringBuilder()).append(CorefPrinter.printConllOutput(document, false)); // before coref
    }
    output[3] = new StringBuilder();  // log from sieves

    for(Sieve sieve : sieves){
      CorefUtils.checkForInterrupt();
      output[3].append(sieve.resolveMention(document, dictionaries, props));
    }

    // post processing
    if(HybridCorefProperties.doPostProcessing(props)) postProcessing(document);

    if(HybridCorefProperties.doScore(props)) {

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

  /** Extract final coreference output from coreference document format. */
  private static Map<Integer, CorefChain> makeCorefOutput(Document document) {
    Map<Integer, CorefChain> result = Generics.newHashMap();
    for(CorefCluster c : document.corefClusters.values()) {
      result.put(c.clusterID, new CorefChain(c, document.positions));
    }
    return result;
  }

  /** Remove singletons, appositive, predicate nominatives, relative pronouns. */
  private static void postProcessing(Document document) {
    Set<Mention> removeSet = Generics.newHashSet();
    Set<Integer> removeClusterSet = Generics.newHashSet();

    for(CorefCluster c : document.corefClusters.values()){
      Set<Mention> removeMentions = Generics.newHashSet();
      for(Mention m : c.getCorefMentions()) {
        if(HybridCorefProperties.REMOVE_APPOSITION_PREDICATENOMINATIVES
            && ((m.appositions!=null && m.appositions.size() > 0)
                || (m.predicateNominatives!=null && m.predicateNominatives.size() > 0)
                || (m.relativePronouns!=null && m.relativePronouns.size() > 0))){
          removeMentions.add(m);
          removeSet.add(m);
          m.corefClusterID = m.mentionID;
        }
      }

      c.corefMentions.removeAll(removeMentions);
      if(HybridCorefProperties.REMOVE_SINGLETONS && c.getCorefMentions().size()==1) {
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
    log.info("USED MEMORY (bytes): " + memory);
  }

  public static void main(String[] args) throws Exception {
    Date startTime = new Date();
    System.err.printf("Start time: %s\n", startTime);
    runCoref(args);
    System.err.printf("Elapsed time: %.3f seconds\n", (((new Date()).getTime() - startTime.getTime()) / 1000F));
  }
}
