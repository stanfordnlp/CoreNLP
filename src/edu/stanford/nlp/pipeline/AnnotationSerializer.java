package edu.stanford.nlp.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.Pair;

public abstract class AnnotationSerializer {
  /**
   * Append a single object to this stream. Subsequent calls to append must supply the returned
   * output stream; furthermore, implementations of this function must be prepared to handle
   * the same output stream being passed in as it returned on the previous write.
   */
  public abstract OutputStream append(Annotation corpus, OutputStream os) throws IOException;
  public abstract Pair<Annotation, InputStream> read(InputStream is) throws IOException, ClassNotFoundException, ClassCastException;

  public void save(Annotation corpus, OutputStream os) throws IOException {
    append(corpus, os).close();
  }

  public Annotation load(InputStream is) throws IOException, ClassNotFoundException, ClassCastException {
    Pair<Annotation, InputStream> pair = read(is);
    pair.second.close();
    return pair.first;
  }
}
