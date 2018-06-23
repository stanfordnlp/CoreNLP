package edu.stanford.nlp.ling.tokensregex.demo;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

/**
 * Demo illustrating how to use TokensRegexAnnotator.
 * Usage:
 * java edu.stanford.nlp.ling.tokensregex.demo.TokensRegexAnnotatorDemo rulesFile [inputFile [outputFile]]
 */
public class TokensRegexAnnotatorDemo {

  private TokensRegexAnnotatorDemo() { } // static main method

  public static void main(String[] args) throws IOException {
    PrintWriter out;

    String rules;
    if (args.length > 0) {
      rules = args[0];
    } else {
      rules = "edu/stanford/nlp/ling/tokensregex/demo/rules/colors.rules.txt";
    }
    if (args.length > 2) {
      out = new PrintWriter(args[2]);
    } else {
      out = new PrintWriter(System.out);
    }

    Properties properties = new Properties();
    properties.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,tokensregexdemo");
    properties.setProperty("customAnnotatorClass.tokensregexdemo", "edu.stanford.nlp.pipeline.TokensRegexAnnotator");
    properties.setProperty("tokensregexdemo.rules", rules);
    StanfordCoreNLP pipeline = new StanfordCoreNLP(properties);
    Annotation annotation;
    if (args.length > 1) {
      annotation = new Annotation(IOUtils.slurpFileNoExceptions(args[1]));
    } else {
      annotation = new Annotation("Both blue and light blue are nice colors.");
    }

    pipeline.annotate(annotation);

    // An Annotation is a Map and you can get and use the various analyses individually.
    // The toString() method on an Annotation just prints the text of the Annotation
    // But you can see what is in it with other methods like toShorterString()
    out.println();
    out.println("The top level annotation");
    out.println(annotation.toShorterString());

    out.println();
    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
    for (CoreMap sentence : sentences) {
      // NOTE: Depending on what tokensregex rules are specified, there are other annotations
      //       that are of interest other than just the tokens and what we print out here
      for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
        // Print out words, lemma, ne, and normalized ne
        String word = token.get(CoreAnnotations.TextAnnotation.class);
        String lemma = token.get(CoreAnnotations.LemmaAnnotation.class);
        String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
        String ne = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
        String normalized = token.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
        out.println("token: " + "word="+word + ", lemma="+lemma + ", pos=" + pos + ", ne=" + ne + ", normalized=" + normalized);
      }
    }
    out.flush();
  }

}
