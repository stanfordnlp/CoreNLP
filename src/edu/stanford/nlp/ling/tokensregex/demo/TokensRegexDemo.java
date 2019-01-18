package edu.stanford.nlp.ling.tokensregex.demo;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.CoreMapExpressionExtractor;
import edu.stanford.nlp.ling.tokensregex.MatchedExpression;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * Demo illustrating how to use CoreMapExtractor.
 * Usage:
 * java edu.stanford.nlp.ling.tokensregex.demo.TokensRegexDemo rulesFile [inputFile [outputFile]]
 * <p>
 * This is a good example to run to show things.
 */
public class TokensRegexDemo {

  private TokensRegexDemo() { } // only static main

  public static void main(String[] args) throws IOException {
    String rules;
    if (args.length > 0) {
      rules = args[0];
    } else {
      rules = "edu/stanford/nlp/ling/tokensregex/demo/rules/expr.rules.txt";
    }
    PrintWriter out;
    if (args.length > 2) {
      out = new PrintWriter(args[2]);
    } else {
      out = new PrintWriter(System.out);
    }

    CoreMapExpressionExtractor<MatchedExpression> extractor = CoreMapExpressionExtractor
            .createExtractorFromFiles(TokenSequencePattern.getNewEnv(), rules);

    StanfordCoreNLP pipeline = new StanfordCoreNLP(
            PropertiesUtils.asProperties("annotators", "tokenize,ssplit,pos,lemma,ner"));
    Annotation annotation;
    if (args.length > 1) {
      annotation = new Annotation(IOUtils.slurpFileNoExceptions(args[1]));
    } else {
      annotation = new Annotation("( ( five plus three plus four ) * 2 ) divided by three");
    }

    pipeline.annotate(annotation);

    // An Annotation is a Map and you can get and use the various analyses individually.
    out.println();
    // The toString() method on an Annotation just prints the text of the Annotation
    // But you can see what is in it with other methods like toShorterString()
    out.println("The top level annotation");
    out.println(annotation.toShorterString());
    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);

    int i = 0;
    for (CoreMap sentence : sentences) {
      out.println("Sentence #" + ++i);
      for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
        out.println("  Token: " + "word="+ token.get(CoreAnnotations.TextAnnotation.class) + ",  pos=" +
                token.get(CoreAnnotations.PartOfSpeechAnnotation.class) + ", ne=" + token.get(CoreAnnotations.NamedEntityTagAnnotation.class));
      }
      List<MatchedExpression> matchedExpressions = extractor.extractExpressions(sentence);
      for (MatchedExpression matched : matchedExpressions) {
        // Print out matched text and value
        out.println("Matched expression: " + matched.getText() + " with value " + matched.getValue());
        // Print out token information
        CoreMap cm = matched.getAnnotation();

        for (CoreLabel token : cm.get(CoreAnnotations.TokensAnnotation.class)) {
          String word = token.get(CoreAnnotations.TextAnnotation.class);
          String lemma = token.get(CoreAnnotations.LemmaAnnotation.class);
          String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
          String ne = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
          out.println("  Matched token: " + "word="+word + ", lemma="+lemma + ", pos=" + pos + ", ne=" + ne);
        }
      }
    }
    out.flush();
  }

}
