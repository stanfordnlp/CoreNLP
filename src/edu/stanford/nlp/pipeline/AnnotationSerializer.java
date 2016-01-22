package edu.stanford.nlp.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import edu.stanford.nlp.pipeline.Annotation;

public interface AnnotationSerializer {
  public void save(Annotation corpus, OutputStream os) throws IOException;
  public Annotation load(InputStream is) throws IOException, ClassNotFoundException, ClassCastException;
}
