package edu.stanford.nlp.neural;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.math.ArrayMath;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * A serializer for reading / writing word vectors.
 * This is used to read word2vec in hcoref, and is primarily here
 * for its efficient serialization / deserialization protocol.
 *
 * @author Gabor Angeli
 */
public class VectorMap extends HashMap<String, float[]>{

  /**
   * Create an empty word vector storage.
   */
  public VectorMap() {
    super(1024);
  }

  /**
   * Initialize word vectors from a given map.
   * @param vectors The word vectors as a simple map.
   */
  public VectorMap(Map<String, float[]> vectors) {
    super(vectors);
  }


  /**
   * Write the word vectors to a file.
   *
   * @param file The file to write to.
   * @throws IOException Thrown if the file could not be written to.
   */
  public void serialize(String file) throws IOException {
    try (OutputStream output = new BufferedOutputStream(new FileOutputStream(new File(file)))) {
      if (file.endsWith(".gz")) {
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
          serialize(gzip);
        }
      } else {
        serialize(output);
      }
    }
  }

  /**
   * Write the word vectors to an output stream. The stream is not closed on finishing
   * the function.
   *
   * @param out The stream to write to.
   * @throws IOException Thrown if the stream could not be written to.
   */
  public void serialize(OutputStream out) throws IOException {
    DataOutputStream dataOut = new DataOutputStream(out);
    // Write the size
    dataOut.writeInt(this.size());
    int dim = -1;
    for (Map.Entry<String, float[]> entry : this.entrySet()) {
      // Write the dimension
      if (dim == -1) {
        dim = entry.getValue().length;
        dataOut.writeInt(dim);
      }
      // Write the length of the key
      byte[] key = entry.getKey().getBytes();
      dataOut.writeInt(key.length);
      dataOut.write(key);
      // Write the vector
      for (float v : entry.getValue()) {
        dataOut.writeFloat(v);
      }
    }
  }


  /**
   * Read word vectors from a file or classpath or url.
   *
   * @param file The file to read from.
   * @return The vectors in the file.
   * @throws IOException Thrown if we could not read from the resource
   */
  public static VectorMap deserialize(String file) throws IOException {
    try (InputStream input = IOUtils.getInputStreamFromURLOrClasspathOrFileSystem(file)) {
      return deserialize(input);
    }
  }

  /**
   * Read word vectors from an input stream. The stream is not closed on finishing the function.
   *
   * @param in The stream to read from. This is not closed.
   * @return The word vectors encoded on the stream.
   * @throws IOException Thrown if we could not read from the stream.
   */
  public static VectorMap deserialize(InputStream in) throws IOException {
    DataInputStream dataIn = new DataInputStream(in);
    int size = dataIn.readInt();
    int dim = dataIn.readInt();
    VectorMap vectors = new VectorMap();
    for (int i = 0; i < size; ++i) {
      // Read the key
      int strlen = dataIn.readInt();
      byte[] buffer = new byte[strlen];
      if (dataIn.read(buffer, 0, strlen) != strlen) {
        throw new IOException("Could not read string buffer fully!");
      }
      String key = new String(buffer);
      // Read the vector
      float[] vector = new float[dim];
      for (int k = 0; k < vector.length; ++k) {
        vector[k] = dataIn.readFloat();
      }
      // Add the key/value
      vectors.put(key, vector);
    }
    return vectors;
  }


  /**
   * Read the Word2Vec word vector flat txt file.
   *
   * @param file The word2vec text file.
   * @return The word vectors in the file.
   */
  public static VectorMap readWord2Vec(String file) {
    VectorMap vectors = new VectorMap();
    int dim = -1;
    for(String line : IOUtils.readLines(file)){
      String[] split = line.toLowerCase().split("\\s+");
      if(split.length < 100) continue;
      float[] vector = new float[split.length-1];
      if (dim == -1) {
        dim = vector.length;
      }
      assert dim == vector.length;
      for(int i=1; i < split.length ; i++) {
        vector[i-1] = Float.parseFloat(split[i]);
      }
      ArrayMath.L2normalize(vector);
      vectors.put(split[0], vector);
    }

    return vectors;
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object other) {
    if (other instanceof Map) {
      try {
        Map<String, float[]> otherMap = (Map<String, float[]>) other;
        // Key sets have the same size
        if (this.keySet().size() != otherMap.keySet().size()) {
          return false;
        }
        // Entries are the same
        for (Entry<String, float[]> entry : this.entrySet()) {
          float[] otherValue = otherMap.get(entry.getKey());
          // Null checks
          if (otherValue == null && entry.getValue() != null) {
            return false;
          }
          if (otherValue != null && entry.getValue() == null) {
            return false;
          }
          // Entries are the same
          //noinspection ConstantConditions
          if (entry.getValue() != null && otherValue != null) {
            if (!Arrays.equals(entry.getValue(), otherValue)) {
              return false;
            }
          }
        }
        return true;
      } catch (ClassCastException e) {
        return false;
      }
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return keySet().hashCode();
  }

  @Override
  public String toString() {
    return "VectorMap[" + this.size() + "]";
  }

}
