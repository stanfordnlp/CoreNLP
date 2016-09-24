package edu.stanford.nlp.coref;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.*;
import java.util.logging.Logger;

import edu.stanford.nlp.coref.data.*;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

/**
 * Class for running coreference algorithms
 * @author Kevin Clark
 */
public class CorefSystem {
  public final Dictionaries dictionaries;
  public final DocumentMaker docMaker;
  private final CorefAlgorithm corefAlgorithm;

  public CorefSystem(Properties props) {
    try {
      dictionaries = new Dictionaries(props);
      docMaker = new DocumentMaker(props, dictionaries);
      corefAlgorithm = CorefAlgorithm.fromProps(props, dictionaries);
    } catch (Exception e) {
      throw new RuntimeException("Error initializing coref system", e);
    }
  }

  public void annotate(Annotation ann) {
    annotate(ann, true);
  }

  public void annotate(Annotation ann, boolean removeSingletonClusters) {
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
    for(CorefCluster c : document.corefClusters.values()) {
      result.put(c.clusterID, new CorefChain(c, document.positions));
    }
    ann.set(CorefCoreAnnotations.CorefChainAnnotation.class, result);
  }

  public void runOnConll(Properties props) throws Exception {
    String baseName = CorefProperties.conllOutputPath(props) +
        Calendar.getInstance().getTime().toString().replaceAll("\\s", "-").replaceAll(":", "-");
    String goldOutput = baseName + ".gold.txt";
    String beforeCorefOutput = baseName + ".predicted.txt";
    String afterCorefOutput = baseName + ".coref.predicted.txt";
    PrintWriter writerGold = new PrintWriter(new FileOutputStream(goldOutput));
    PrintWriter writerBeforeCoref = new PrintWriter(new FileOutputStream(beforeCorefOutput));
    PrintWriter writerAfterCoref = new PrintWriter(new FileOutputStream(afterCorefOutput));

    (new CorefDocumentProcessor() {
      @Override
      public void process(int id, Document document) {
        writerGold.print(CorefPrinter.printConllOutput(document, true));
        writerBeforeCoref.print(CorefPrinter.printConllOutput(document, false));
        corefAlgorithm.runCoref(document);
        CorefUtils.removeSingletonClusters(document);
        writerAfterCoref.print(CorefPrinter.printConllOutput(document, false, true));
      }

      @Override
      public void finish() throws Exception {}

      @Override
      public String getName() {
        return corefAlgorithm.getClass().getName();
      }
    }).run(docMaker);

    Logger logger = Logger.getLogger(CorefSystem.class.getName());
    String summary = CorefScorer.getEvalSummary(CorefProperties.getScorerPath(props),
        goldOutput, beforeCorefOutput);
    CorefScorer.printScoreSummary(summary, logger, false);
    summary = CorefScorer.getEvalSummary(CorefProperties.getScorerPath(props), goldOutput,
        afterCorefOutput);
    CorefScorer.printScoreSummary(summary, logger, true);
    CorefScorer.printFinalConllScore(summary);

    writerGold.close();
    writerBeforeCoref.close();
    writerAfterCoref.close();
  }

  /** Remove singleton clusters */
  public static List<List<Mention>> filterMentionsWithSingletonClusters(
          Document document, List<List<Mention>> mentions) {

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
    Properties props = StringUtils.argsToProperties(new String[] {"-props", args[0]});
    System.out.println("---\ncoref props");
    System.out.println(props);
    CorefSystem coref = new CorefSystem(props);
    coref.runOnConll(props);
  }
}
