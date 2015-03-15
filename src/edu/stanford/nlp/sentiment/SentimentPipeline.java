package edu.stanford.nlp.sentiment;

import java.io.BufferedReader;
import java.io.IOException;
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
  public static void main(String[] args) throws IOException {
    String parserModel = null;
    String sentimentModel = null;

    String filename = null;
    boolean stdin = false;

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
      } else if (args[argIndex].equalsIgnoreCase("-stdin")) {
        stdin = true;
        argIndex++;
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

    if (filename != null && stdin) {
      throw new IllegalArgumentException("Please only specify one of -file or -stdin");
    }
    if (filename == null && !stdin) {
      throw new IllegalArgumentException("Please specify either -file or -stdin");
    }

    if (stdin) {
      props.setProperty("ssplit.eolonly", "true");
    }
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    if (filename != null) {
      String text = IOUtils.slurpFileNoExceptions(filename);
      Annotation annotation = new Annotation(text);
      pipeline.annotate(annotation);

      for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
        Tree tree = sentence.get(SentimentCoreAnnotations.AnnotatedTree.class);
        int sentiment = RNNCoreAnnotations.getPredictedClass(tree);
        System.err.println(sentence);
        System.err.println("  Predicted sentiment: " + SentimentUtils.sentimentString(sentiment));
      }
    } else {
      System.err.println("Reading in text from stdin.");
      System.err.println("Please enter one sentence per line.");
      System.err.println("Processing will end when EOF is reached.");
      BufferedReader reader = new BufferedReader(IOUtils.encodedInputStreamReader(System.in, "utf-8"));
      while (true) {
        String line = reader.readLine();
        if (line == null) {
          break;
        }
        line = line.trim();
        if (line.length() > 0) {
          Annotation annotation = pipeline.process(line);
          for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
            Tree tree = sentence.get(SentimentCoreAnnotations.AnnotatedTree.class);
            int sentiment = RNNCoreAnnotations.getPredictedClass(tree);
            System.err.println("  " + SentimentUtils.sentimentString(sentiment));
          }
        } else {
          // Output blank lines for blank lines so the tool can be
          // used for line-by-line text processing
          System.err.println("");
        }
      }
      
    }
  }
}
