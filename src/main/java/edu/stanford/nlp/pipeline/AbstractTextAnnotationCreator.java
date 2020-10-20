package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.IOUtils;

import java.io.*;

/**
 * Creates a stub implementation for creating annotation from
 *   various input sources using String as the main input source
 *
 * @author Angel Chang
 */
abstract public class AbstractTextAnnotationCreator implements AnnotationCreator {
  @Override
  public Annotation createFromFile(String filename) throws IOException {
    Reader r = IOUtils.readerFromString(filename);
    Annotation anno = create(r);
    IOUtils.closeIgnoringExceptions(r);
    return anno;
  }

  @Override
  public Annotation createFromFile(File file) throws IOException {
    return createFromFile(file.getAbsolutePath());
  }

  @Override
  public Annotation create(InputStream stream) throws IOException {
    return create(new InputStreamReader(stream));
  }

  @Override
  public Annotation create(InputStream stream, String encoding) throws IOException {
    return create(new InputStreamReader(stream, encoding));
  }

  @Override
  public Annotation create(Reader reader) throws IOException {
    String text = IOUtils.slurpReader(reader);
    return createFromText(text);
  }
}
