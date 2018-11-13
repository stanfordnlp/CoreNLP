package edu.stanford.nlp.coref;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.Map;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.coref.data.CorefCluster;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.data.DocumentMaker;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.NewlineLogFormatter;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Class for running coreference algorithms
 * @author Kevin Clark
 */
public class CorefSystem {
  private final DocumentMaker docMaker;
  private final CorefAlgorithm corefAlgorithm;
  private final boolean removeSingletonClusters;
  private final boolean verbose;

  public CorefSystem(Properties props) {
    try {
      Dictionaries dictionaries = new Dictionaries(props);
      docMaker = new DocumentMaker(props, dictionaries);
      corefAlgorithm = CorefAlgorithm.fromProps(props, dictionaries);
      removeSingletonClusters = CorefProperties.removeSingletonClusters(props);
      verbose = CorefProperties.verbose(props);
    } catch (Exception e) {
      throw new RuntimeException("Error initializing coref system", e);
    }
  }

  public CorefSystem(DocumentMaker docMaker, CorefAlgorithm corefAlgorithm,
      boolean removeSingletonClusters, boolean verbose) {
    this.docMaker = docMaker;
    this.corefAlgorithm = corefAlgorithm;
    this.removeSingletonClusters = removeSingletonClusters;
    this.verbose = verbose;
  }

  public void annotate(Annotation ann) {
    Document document;
    try {
      document = docMaker.makeDocument(ann);
    } catch (Exception e) {
      throw new RuntimeException("Error making document", e);
    }

    CorefUtils.checkForInterrupt();
    corefAlgorithm.runCoref(document);
    if (removeSingletonClusters) {
      CorefUtils.removeSingletonClusters(document);
    }
    CorefUtils.checkForInterrupt();

    Map<Integer, CorefChain> result = Generics.newHashMap();
    for (CorefCluster c : document.corefClusters.values()) {
      result.put(c.clusterID, new CorefChain(c, document.positions));
    }
    ann.set(CorefCoreAnnotations.CorefChainAnnotation.class, result);
  }

  public void initLogger(Logger logger, String logFileName) {
      try {
          FileHandler fh = new FileHandler(logFileName, false);
          logger.addHandler(fh);
          logger.setLevel(Level.FINE);
          fh.setFormatter(new NewlineLogFormatter());
      } catch (SecurityException | IOException e) {
          throw new RuntimeException("Cannot initialize logger!", e);
      }
  }

  public void runOnConll(Properties props) throws Exception {
    File f = new File(CorefProperties.conllOutputPath(props));
    if (! f.exists()) {
      f.mkdirs();
    }
    String timestamp = Calendar.getInstance().getTime().toString().replaceAll("\\s", "-").replaceAll(":", "-");
    String baseName = CorefProperties.conllOutputPath(props) + timestamp;
    String goldOutput = baseName + ".gold.txt";
    String beforeCorefOutput = baseName + ".predicted.txt";
    String afterCorefOutput = baseName + ".coref.predicted.txt";
    PrintWriter writerGold = new PrintWriter(new FileOutputStream(goldOutput));
    PrintWriter writerBeforeCoref = new PrintWriter(new FileOutputStream(beforeCorefOutput));
    PrintWriter writerAfterCoref = new PrintWriter(new FileOutputStream(afterCorefOutput));

    Logger logger = Logger.getLogger(CorefSystem.class.getName());
    initLogger(logger,baseName + ".log");
    logger.info(timestamp);
    logger.info(props.toString());

    (new CorefDocumentProcessor() {
      @Override
      public void process(int id, Document document) {
        writerGold.print(CorefPrinter.printConllOutput(document, true));
        writerBeforeCoref.print(CorefPrinter.printConllOutput(document, false));
        long time = System.currentTimeMillis();
        corefAlgorithm.runCoref(document);
        if (verbose) {
          Redwood.log(getName(), "Coref took "
              + (System.currentTimeMillis() - time) / 1000.0 + "s");
        }
        CorefUtils.removeSingletonClusters(document);
        if (verbose) {
          CorefUtils.printHumanReadableCoref(document);
        }
        if (document.filterMentionSet != null) {
          Map<Integer,CorefCluster> filteredClusters = document.corefClusters
                  .values().stream().filter(x -> CorefUtils.filterClustersWithMentionSpans(x, document.filterMentionSet) )
                  .collect(Collectors.toMap(x -> x.clusterID, x -> x));
          writerAfterCoref.print(CorefPrinter.printConllOutput(document, false, true, filteredClusters));
        } else {
          writerAfterCoref.print(CorefPrinter.printConllOutput(document, false, true));
        }
      }

      @Override
      public void finish() throws Exception {}

      @Override
      public String getName() {
        return corefAlgorithm.getClass().getName();
      }
    }).run(docMaker);

    String summary = CorefScorer.getEvalSummary(CorefProperties.getScorerPath(props),
        goldOutput, beforeCorefOutput);

    logger.info("Before Coref");
    CorefScorer.printScoreSummary(summary, logger, false);
    CorefScorer.printScoreSummary(summary, logger, true);
    CorefScorer.printFinalConllScore(summary, logger);

    summary = CorefScorer.getEvalSummary(CorefProperties.getScorerPath(props), goldOutput,
        afterCorefOutput);
    logger.info("After Coref");
    CorefScorer.printScoreSummary(summary, logger, false);
    CorefScorer.printScoreSummary(summary, logger, true);
    CorefScorer.printFinalConllScore(summary, logger);

    writerGold.close();
    writerBeforeCoref.close();
    writerAfterCoref.close();
  }

  public static void main(String[] args) throws Exception {
    Properties props = StringUtils.argsToProperties(args);
    CorefSystem coref = new CorefSystem(props);
    coref.runOnConll(props);
  }
}
