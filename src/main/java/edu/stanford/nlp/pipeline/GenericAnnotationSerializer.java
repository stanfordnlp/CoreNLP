package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.util.Pair;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Serializes Annotation objects using the default Java serializer
 */
public class GenericAnnotationSerializer extends AnnotationSerializer {

  boolean compress = false;

  public GenericAnnotationSerializer(boolean compress) {
    this.compress = compress;
  }

  public GenericAnnotationSerializer() {
    this(false);
  }

  /** Turns out, an ObjectOutputStream cannot append to a file. This is dumb. */
  public static class AppendingObjectOutputStream extends ObjectOutputStream {
    public AppendingObjectOutputStream(OutputStream out) throws IOException {
      super(out);
    }
    @Override
    protected void writeStreamHeader() throws IOException {
      // do not write a header, but reset
      reset();
    }
  }

  @Override
  public OutputStream write(Annotation corpus, OutputStream os) throws IOException {
    if (os instanceof AppendingObjectOutputStream) {
      ((AppendingObjectOutputStream) os).writeObject(corpus);
      return os;
    } else if (os instanceof ObjectOutputStream) {
      ObjectOutputStream objectOutput = new AppendingObjectOutputStream(compress ? new GZIPOutputStream(os) : os);
      objectOutput.writeObject(corpus);
      return objectOutput;
    } else {
      ObjectOutputStream objectOutput = new ObjectOutputStream(compress ? new GZIPOutputStream(os) : os);
      objectOutput.writeObject(corpus);
      return objectOutput;
    }
  }

  @Override
  public Pair<Annotation, InputStream> read(InputStream is) throws IOException, ClassNotFoundException, ClassCastException {
    ObjectInputStream objectInput;
    if (is instanceof ObjectInputStream) {
      objectInput = (ObjectInputStream) is;
    } else {
      objectInput = new ObjectInputStream(compress ? new GZIPInputStream(is) : is);
    }
    Object annotation = objectInput.readObject();
    if(annotation == null) return null;
    if(! (annotation instanceof Annotation)){
      throw new ClassCastException("ERROR: Serialized data does not contain an Annotation!");
    }
    return Pair.makePair((Annotation) annotation, (InputStream) objectInput);
  }

}
