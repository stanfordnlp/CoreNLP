package edu.stanford.nlp.coref.misc;

import java.util.Properties;

import edu.stanford.nlp.coref.CorefDocumentProcessor;
import edu.stanford.nlp.coref.CorefProperties;
import edu.stanford.nlp.coref.CorefProperties.Dataset;
import edu.stanford.nlp.coref.data.CorefCluster;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Evaluates the accuracy of mention detection.
 * @author Kevin Clark
 */
public class MentionDetectionEvaluator implements CorefDocumentProcessor {
  private static Redwood.RedwoodChannels log = Redwood.channels(MentionDetectionEvaluator.class);
  private int correctSystemMentions = 0;
  private int systemMentions = 0;
  private int goldMentions = 0;

  @Override
  public void process(int id, Document document) {
    for (CorefCluster gold : document.goldCorefClusters.values()) {
      for (Mention m : gold.corefMentions) {
        if (document.predictedMentionsByID.containsKey(m.mentionID)) {
          correctSystemMentions += 1;
        }
        goldMentions += 1;
      }
    }
    systemMentions += document.predictedMentionsByID.size();

    double precision = correctSystemMentions / (double) systemMentions;
    double recall = correctSystemMentions / (double) goldMentions;
    log.info("Precision: " + correctSystemMentions + " / " + systemMentions + " = " +
        String.format("%.4f", precision));
    log.info("Recall: " + correctSystemMentions + " / " + goldMentions + " = " +
        String.format("%.4f", recall));
    log.info(String.format("F1: %.4f", 2 * precision * recall / (precision + recall)));
  }

  @Override
  public void finish() throws Exception {}

  public static void main(String[] args) throws Exception {
    Properties props = StringUtils.argsToProperties(new String[] {"-props", args[0]});
    Dictionaries dictionaries = new Dictionaries(props);
    CorefProperties.setInput(props, Dataset.TRAIN);
    new MentionDetectionEvaluator().run(props, dictionaries);
  }
}
