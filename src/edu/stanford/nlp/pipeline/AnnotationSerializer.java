package edu.stanford.nlp.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import edu.stanford.nlp.util.Pair;

public abstract class AnnotationSerializer {
  /**
   * Append a single object to this stream. Subsequent calls to append on the same stream must supply the returned
   * output stream; furthermore, implementations of this function must be prepared to handle
   * the same output stream being passed in as it returned on the previous write.
   *
   * @param corpus  The document to serialize to the stream.
   * @param os The output stream to serialize to.
   * @return The output stream which should be closed when done writing, and which should be passed into subsequent
   *         calls to write() on this serializer.
   * @throws IOException Thrown if the underlying output stream throws the exception.
   */
  public abstract OutputStream write(Annotation corpus, OutputStream os) throws IOException;

  /**
   * Read a single object from this stream. Subsequent calls to read on the same input stream must supply the
   * returned input stream; furthermore, implementations of this function must be prepared to handle the same
   * input stream being passed to it as it returned on the previous read.
   *
   * @param is The input stream to read a document from.
   * @return A pair of the read document, and the implementation-specific input stream which it was actually read from.
   *         This stream should be passed to subsequent calls to read on the same stream, and should be closed when reading
   *         completes.
   * @throws IOException Thrown if the underlying stream throws the exception.
   * @throws ClassNotFoundException Thrown if an object was read that does not exist in the classpath.
   * @throws ClassCastException Thrown if the signature of a class changed in way that was incompatible with the serialized document.
   */
  public abstract Pair<Annotation, InputStream> read(InputStream is) throws IOException, ClassNotFoundException, ClassCastException;

}
