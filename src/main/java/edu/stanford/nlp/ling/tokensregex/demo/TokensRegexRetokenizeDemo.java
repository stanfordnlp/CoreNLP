package edu.stanford.nlp.ling.tokensregex.demo;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Properties;

/**
 * Demo illustrating how to use TokensRegexAnnotator for tweaks to tokenization
 */
public class TokensRegexRetokenizeDemo {

  private static void runPipeline(StanfordCoreNLP pipeline, String text, PrintWriter out) {
    Annotation annotation = new Annotation(text);
    pipeline.annotate(annotation);

    // An Annotation is a Map and you can get and use the various analyses individually.
    out.println();
    // The toString() method on an Annotation just prints the text of the Annotation
    // But you can see what is in it with other methods like toShorterString()
    out.println("The top level annotation");
    out.println(annotation.toShorterString());
    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);

    for (CoreMap sentence : sentences) {
      // Print out token annotations
      for (CoreLabel token:sentence.get(CoreAnnotations.TokensAnnotation.class)) {
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

  public static void main(String[] args) throws IOException {
    PrintWriter out;

    String rules;
    if (args.length > 0) {
      rules = args[0];
    } else {
      rules = "edu/stanford/nlp/ling/tokensregex/demo/rules/retokenize.rules.txt";
    }
    if (args.length > 2) {
      out = new PrintWriter(args[2]);
    } else {
      out = new PrintWriter(System.out);
    }

    String text;
    if (args.length > 1) {
      text = IOUtils.slurpFileNoExceptions(args[1]);
    } else {
      text = "Do we tokenize on hyphens? one-two-three-four.  How about dates? 03-16-2015.";
    }

    Properties propertiesDefaultTokenize = new Properties();
    propertiesDefaultTokenize.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    StanfordCoreNLP pipelineDefaultRetokenize = new StanfordCoreNLP();
    out.println("Default tokenization: ");
    runPipeline(pipelineDefaultRetokenize, text, out);

    Properties properties = new Properties();
    properties.setProperty("annotators", "tokenize,retokenize,ssplit,pos,lemma,ner");
    properties.setProperty("customAnnotatorClass.retokenize", "edu.stanford.nlp.pipeline.TokensRegexAnnotator");
    properties.setProperty("retokenize.rules", rules);
    StanfordCoreNLP pipelineWithRetokenize = new StanfordCoreNLP(properties);
    out.println();
    out.println("Always tokenize hyphens: ");
    runPipeline(pipelineWithRetokenize, text, out);
  }

}
