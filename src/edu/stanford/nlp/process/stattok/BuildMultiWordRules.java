package edu.stanford.nlp.process.stattok;

/**
 * Builds a MultiWordRules table for the StatTokSent splitter.
 */

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

public class BuildMultiWordRules {
  private static final Redwood.RedwoodChannels logger = Redwood.channels(StatTokSentTrainer.class);

  // disallow making this class
  private BuildMultiWordRules() {}

  public static void main(String[] args) throws IOException {
    Properties properties 	= StringUtils.argsToProperties(args);
    String trainFile 		= properties.getProperty("trainFile", null);
    String multiWordRulesFile 	= properties.getProperty("multiWordRulesFile", null);

    if (trainFile == null){
      logger.err("Error: No training file provided in properties or via command line --trainFile");
      return;
    }

    if (multiWordRulesFile == null){
      logger.err("Error: No dest file provided in properties or via command line --multiWordRulesFile");
      return;
    }

    Map<String, String[]> rules = StatTokSentTrainer.inferMultiWordRules(trainFile);
    StatTokSentTrainer.writeMultiWordRules(multiWordRulesFile, rules);
  }
}