package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ReflectionLoading;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads from serialized annotations
 *
 * @author Angel Chang
 */
public class SerializedAnnotationCreator extends AbstractInputStreamAnnotationCreator {
  AnnotationSerializer serializer;

  public SerializedAnnotationCreator(AnnotationSerializer serializer) {
    this.serializer = serializer;
  }

  public SerializedAnnotationCreator(String name, Properties props) {
    String serializerClass = props.getProperty(name + ".serializer");
    serializer = ReflectionLoading.loadByReflection(serializerClass);
  }

  @Override
  public Annotation create(InputStream stream, String encoding) throws IOException {
    try {
      Pair<Annotation, InputStream> pair = serializer.read(stream);
      pair.second.close();
      Annotation annotation = pair.first;
      return annotation;
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

}
