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
 * Outputs document back into text format, with tags added if available.
 * If tags are not available, they are quietly ignored.
 * Created by michaelf on 7/15/15.
 */
public class TaggedTextOutputter extends AnnotationOutputter{
  public TaggedTextOutputter() {}

  @Override
  public void print(Annotation doc, OutputStream target, Options options) throws IOException {
    PrintWriter os = new PrintWriter(IOUtils.encodedOutputStreamWriter(target, options.encoding));
    print(doc, os, options);
    os.flush();
  }


  private static void print(Annotation annotation, PrintWriter pw, Options options) throws IOException {
    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
    if(sentences != null) {
      for (CoreMap sentence : sentences) {
        StringBuilder sentenceToWrite = new StringBuilder();
        for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
          if (sentenceToWrite.length() > 0) {
            sentenceToWrite.append(" ");
          }
          sentenceToWrite.append(token.value());
          String tag = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
          if (tag != null) {
            sentenceToWrite.append("_" + tag);
          }
        }
        pw.print(sentenceToWrite);
        pw.print(System.lineSeparator());
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
