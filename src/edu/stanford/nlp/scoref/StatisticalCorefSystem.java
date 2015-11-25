package edu.stanford.nlp.scoref;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import edu.stanford.nlp.hcoref.CorefCoreAnnotations;
import edu.stanford.nlp.hcoref.CorefDocMaker;
import edu.stanford.nlp.hcoref.CorefPrinter;
import edu.stanford.nlp.hcoref.CorefProperties;
import edu.stanford.nlp.hcoref.CorefSystem;
import edu.stanford.nlp.hcoref.Scorer;
import edu.stanford.nlp.hcoref.data.CorefChain;
import edu.stanford.nlp.hcoref.data.CorefCluster;
import edu.stanford.nlp.hcoref.data.Dictionaries;
import edu.stanford.nlp.hcoref.data.Document;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

public abstract class StatisticalCorefSystem {
  public final Dictionaries dictionaries;
  private final Properties props;
  private final CorefDocMaker docMaker;

  public StatisticalCorefSystem(Properties props) {
    this.props = StatisticalCorefProperties.addHcorefProps(props);
    try {
      dictionaries = new Dictionaries(this.props);
      docMaker = new CorefDocMaker(this.props, dictionaries);
    } catch (Exception e) {
      throw new RuntimeException("Error initializing coref system", e);
    }
  }

  public static StatisticalCorefSystem fromProps(Properties props) {
     try {
       if (StatisticalCorefProperties.cluster(props)) {
         return new ClusteringCorefSystem(props,
             StatisticalCorefProperties.clusteringModelPath(props),
             StatisticalCorefProperties.classificationModelPath(props),
             StatisticalCorefProperties.rankingModelPath(props),
             StatisticalCorefProperties.anaphoricityModelPath(props),
             StatisticalCorefProperties.wordCountsPath(props));
       } else {
         return new BestFirstCorefSystem(props,
             StatisticalCorefProperties.rankingModelPath(props),
             StatisticalCorefProperties.wordCountsPath(props),
             StatisticalCorefProperties.maxMentionDistance(props),
             StatisticalCorefProperties.pairwiseScoreThresholds(props));
       }
     } catch (Exception e) {
       throw new RuntimeException("Error creating coreference system", e);
     }
  }

  public void annotate(Annotation ann) {
    annotate(ann, true);
  }

  public void annotate(Annotation ann, boolean removeSingletonClusters) {
    try {
      Document document = docMaker.makeDocument(ann);
      runCoref(document);
      if (removeSingletonClusters) {
        StatisticalCorefUtils.removeSingletonClusters(document);
      }

      Map<Integer, CorefChain> result = Generics.newHashMap();
      for(CorefCluster c : document.corefClusters.values()) {
        result.put(c.clusterID, new CorefChain(c, document.positions));
      }
      ann.set(CorefCoreAnnotations.CorefChainAnnotation.class, result);
    } catch (Exception e) {
      throw new RuntimeException("Error annotating document with coref", e);
    }
  }

  public void runOnConll() throws Exception {
    String baseName = StatisticalCorefProperties.conllOutputPath(props) +
        Calendar.getInstance().getTime().toString().replaceAll("\\s", "-").replaceAll(":", "-");
    String goldOutput = baseName + ".gold.txt";
    String beforeCorefOutput = baseName + ".predicted.txt";
    String afterCorefOutput = baseName + ".coref.predicted.txt";
    PrintWriter writerGold = new PrintWriter(new FileOutputStream(goldOutput));
    PrintWriter writerBeforeCoref = new PrintWriter(new FileOutputStream(beforeCorefOutput));
    PrintWriter writerAfterCoref = new PrintWriter(new FileOutputStream(afterCorefOutput));

    (new DocumentProcessor() {
      @Override
      public void process(int id, Document document) {
        writerGold.print(CorefPrinter.printConllOutput(document, true));
        writerBeforeCoref.print(CorefPrinter.printConllOutput(document, false));
        runCoref(document);
        StatisticalCorefUtils.removeSingletonClusters(document);
        writerAfterCoref.print(CorefPrinter.printConllOutput(document, false, true));
      }

      @Override
      public void finish() throws Exception {}

      @Override
      public String getName() {
        return StatisticalCorefSystem.this.getClass().getSimpleName();
      }
    }).run(docMaker);

    Logger logger = Logger.getLogger(CorefSystem.class.getName());
    String summary = Scorer.getEvalSummary(CorefProperties.getPathScorer(props),
        goldOutput, beforeCorefOutput);
    CorefPrinter.printScoreSummary(summary, logger, false);
    summary = Scorer.getEvalSummary(CorefProperties.getPathScorer(props), goldOutput,
        afterCorefOutput);
    CorefPrinter.printScoreSummary(summary, logger, true);
    CorefPrinter.printFinalConllScore(summary);

    writerGold.close();
    writerBeforeCoref.close();
    writerAfterCoref.close();
  }

  public abstract void runCoref(Document document);

  public static void main(String[] args) throws Exception {
    Properties props = StringUtils.argsToProperties(new String[] {"-props", args[0]});
    StatisticalCorefSystem coref = StatisticalCorefSystem.fromProps(props);
    coref.runOnConll();
  }
}
