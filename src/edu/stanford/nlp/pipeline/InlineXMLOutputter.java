package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.*;

/**
 * Outputter for printing NER/Entity Mention annotations in inlineXML style. This outputter will implicitly
 * show tokenization and sentence splitting by printing out sentences one per line with tokens separated by whitespace.
 * The NER/Entity Mention annotations will be shown inline via XML tags.
 */

public class InlineXMLOutputter extends AnnotationOutputter {

  public InlineXMLOutputter() {}

  /** {@inheritDoc} */
  @Override
  public void print(Annotation annotation, OutputStream stream, Options options) throws IOException {
    PrintWriter os = new PrintWriter(IOUtils.encodedOutputStreamWriter(stream, options.encoding));
    print(annotation, os, options);
  }

  /**
   * The meat of the outputter.
   */
  private static void print(Annotation annotation, PrintWriter pw, Options options) {
    CoreDocument doc = new CoreDocument(annotation);
    for (CoreSentence sentence : doc.sentences()) {
      int entityIdx = 0;
      int tokenIdx = 0;
      List<CoreEntityMention> entities = sentence.entityMentions();
      int numEntities = entities.size();
      for (CoreLabel token : sentence.tokens()) {
        if (tokenIdx != 0) {
          pw.printf(" ");
        }
        if (numEntities > entityIdx) {
          if (entities.get(entityIdx).charOffsets().first() == token.beginPosition()) {
            pw.printf("<%s>%s", entities.get(entityIdx).entityType(), token.word());
            // handle single token entities
            if (entities.get(entityIdx).charOffsets().second() == token.endPosition()) {
              pw.printf("</%s>", entities.get(entityIdx).entityType());
              entityIdx++;
            }
          } else if (entities.get(entityIdx).charOffsets().second() == token.endPosition()) {
            pw.printf("%s</%s>", token.word(), entities.get(entityIdx).entityType());
            entityIdx++;
          } else {
            pw.printf("%s", token.word());
          }
        } else {
          pw.printf("%s", token.word());
        }
        tokenIdx++;
      }
      pw.print("\n");
    }
    pw.flush();
  }
}
