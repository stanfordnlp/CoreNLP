package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.ReaderInputStream;

import java.io.*;

/**
 * Creates a stub implementation for creating annotation from
 *   various input sources using InputStream as the main input source
 *
 * @author Angel Chang
 */
public abstract class AbstractInputStreamAnnotationCreator implements AnnotationCreator {
  @Override
  public Annotation createFromText(String text) throws IOException {
    return create(new StringReader(text));
  }

  @Override
  public Annotation createFromFile(String filename) throws IOException {
    InputStream stream = new BufferedInputStream(new FileInputStream(filename));
    Annotation anno = create(stream);
    IOUtils.closeIgnoringExceptions(stream);
    return anno;
  }

  @Override
  public Annotation createFromFile(File file) throws IOException {
    return createFromFile(file.getAbsolutePath());
  }

  @Override
  public Annotation create(InputStream stream) throws IOException {
    return create(stream, "UTF-8");
  }

  @Override
  public Annotation create(Reader reader) throws IOException {
    // TODO: Is this okay?  If we are using this class, maybe we want byte-level stuff
    //  not character level
    return create(new ReaderInputStream(reader));
  }
}
