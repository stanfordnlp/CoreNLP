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

    log.info("Precision: " + correctSystemMentions + " / " + systemMentions + " = " +
        String.format("%.4f", correctSystemMentions / (double) systemMentions));
    log.info("Recall: " + correctSystemMentions + " / " + goldMentions + " = " +
        String.format("%.4f", correctSystemMentions / (double) goldMentions));
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
