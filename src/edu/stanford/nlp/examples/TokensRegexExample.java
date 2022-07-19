package edu.stanford.nlp.examples;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;

import java.io.File;
import java.util.Properties;

public class TokensRegexExample {

  public static void main(String[] args) {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,tokensregex");
    // The .../ling/tokensregex/demo directory has a larger example
    File rules = new File("src/edu/stanford/nlp/ling/tokensregex/demo/rules/colors.rules.txt");
    if ( ! rules.isFile()) {
      throw new RuntimeIOException("Unable to find colors.rules.txt");
    }
    props.setProperty("tokensregex.rules", rules.getPath());
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    Annotation ann = new Annotation("My favorite color is green.");
    pipeline.annotate(ann);
    for (CoreLabel token : ann.get(CoreAnnotations.TokensAnnotation.class)) {
      System.out.println(token.word() + " " + token.ner());
    }
  }

}
