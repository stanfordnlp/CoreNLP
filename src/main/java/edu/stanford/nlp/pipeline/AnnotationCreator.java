package edu.stanford.nlp.pipeline;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

/**
 * Creates a annotation from an input source
 *
 * @author Angel Chang
 */
public interface AnnotationCreator {
  public Annotation createFromText(String text) throws IOException;

  public Annotation createFromFile(String filename) throws IOException;
  public Annotation createFromFile(File file) throws IOException;

  public Annotation create(InputStream stream) throws IOException;
  public Annotation create(InputStream stream, java.lang.String encoding) throws IOException;
  public Annotation create(Reader reader)  throws IOException;
}
