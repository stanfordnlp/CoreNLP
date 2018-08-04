package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;

/**
 * Outputs document back into text format, with verbs and nouns tagged as such (_V or _N) and also lemmatized.
 * Created by michaelf on 7/15/15.
 */
public class TaggedTextOutputter extends AnnotationOutputter{
  public TaggedTextOutputter() {}

  @Override
  public void print(Annotation doc, OutputStream target, Options options) throws IOException {
    PrintWriter os = new PrintWriter(IOUtils.encodedOutputStreamWriter(target, options.encoding));
    print(doc, os, options);
  }


  private static void print(Annotation annotation, PrintWriter pw, Options options) throws IOException {
    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
    if(sentences != null) {
      for (CoreMap sentence : sentences) {
        StringBuilder sentenceToWrite = new StringBuilder();
        for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
          sentenceToWrite.append(" ");
          sentenceToWrite.append(token.lemma().toLowerCase());
          if (token.get(CoreAnnotations.PartOfSpeechAnnotation.class).startsWith("V")) //verb
            sentenceToWrite.append("_V");
          else if (token.get(CoreAnnotations.PartOfSpeechAnnotation.class).startsWith("N")) //noun
            sentenceToWrite.append("_N");
        }
        pw.print(sentenceToWrite); //omit first space
      }
    }
  }

  //from TextOutputter

  /** Static helper */
  public static void prettyPrint(Annotation annotation, OutputStream stream, StanfordCoreNLP pipeline) {
    prettyPrint(annotation, new PrintWriter(stream), pipeline);
  }

  /** Static helper */
  public static void prettyPrint(Annotation annotation, PrintWriter pw, StanfordCoreNLP pipeline) {
    try {
      TaggedTextOutputter.print(annotation, pw, getOptions(pipeline.getProperties()));
      // already flushed
      // don't close, might not want to close underlying stream
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

}
