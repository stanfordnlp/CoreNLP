package edu.stanford.nlp.sentiment;

import java.util.Properties;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;

/**
 * A wrapper class which creates a suitable pipeline for the sentiment
 * model and processes raw text.
 *
 * @author John Bauer
 */
public class SentimentPipeline {
  public static String sentimentString(int sentiment) {
    switch(sentiment) {
    case 0:
      return "Very negative";
    case 1:
      return "Negative";
    case 2:
      return "Neutral";
    case 3:
      return "Positive";
    case 4:
      return "Very positive";
    default:
      return "Unknown sentiment label " + sentiment;
    }
  }

  public static void main(String[] args) {
    String parserModel = null;
    String sentimentModel = null;

    String filename = null;

    for (int argIndex = 0; argIndex < args.length; ) {
      if (args[argIndex].equalsIgnoreCase("-sentimentModel")) {
        sentimentModel = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-parserModel")) {
        parserModel = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-file")) {
        filename = args[argIndex + 1];
        argIndex += 2;
      } else {
        System.err.println("Unknown argument " + args[argIndex + 1]);
        throw new IllegalArgumentException("Unknown argument " + args[argIndex + 1]);
      }
    }

    Properties props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit, parse, sentiment");
    if (sentimentModel != null) {
      props.setProperty("sentiment.model", sentimentModel);
    }
    if (parserModel != null) {
      props.setProperty("parse.model", parserModel);
    }
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    String text = IOUtils.slurpFileNoExceptions(filename);
    Annotation annotation = new Annotation(text);
    pipeline.annotate(annotation);

    for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      Tree tree = sentence.get(SentimentCoreAnnotations.AnnotatedTree.class);
      int sentiment = RNNCoreAnnotations.getPredictedClass(tree);
      System.err.println(sentence);
      System.err.println("  Predicted sentiment: " + sentimentString(sentiment));
    }
  }
}
