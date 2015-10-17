package edu.stanford.nlp.neural;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.math.ArrayMath;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * A serializer for reading / writing word vectors.
 * This is used to read word2vec in hcoref, and is primarily here
 * for its efficient serialization / deserialization protocol, which
 * saves/loads the vectors as 16 bit floats.
 *
 * @author Gabor Angeli
 */
public class VectorMap extends HashMap<String, float[]>{

  /**
   * The integer type (i.e., number of bits per integer).
   */
  private enum itype {
    INT8,
    INT16,
    INT32;

    /**
     * Get the minimum integer type that will fit this number.
     */
    static itype getType(int num) {
      itype t = itype.INT32;
      if (num < Short.MAX_VALUE) {
        t = itype.INT16;
      }
      if (num < Byte.MAX_VALUE) {
        t = itype.INT8;
      }
      return t;
    }

    /**
     * Read an integer of this type from the given input stream
     */
    public int read(DataInputStream in) throws IOException {
      switch (this) {
        case INT8:
          return in.readByte();
        case INT16:
          return in.readShort();
        case INT32:
          return in.readInt();
        default:
          throw new RuntimeException("Unknown itype: " + this);
      }
    }

    /**
     * Write an integer of this type to the given output stream
     */
    public void write(DataOutputStream out, int value) throws IOException {
      switch (this) {
        case INT8:
          out.writeByte(value);
          break;
        case INT16:
          out.writeShort(value);
          break;
        case INT32:
          out.writeInt(value);
          break;
        default:
          throw new RuntimeException("Unknown itype: " + this);
      }
    }

  }

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

    // Write some length statistics
    int maxKeyLength = 0;
    int vectorLength = 0;
    for (Entry<String, float[]> entry : this.entrySet()) {
      maxKeyLength = Math.max(entry.getKey().getBytes().length, maxKeyLength);
      vectorLength = entry.getValue().length;
    }
    itype keyIntType = itype.getType(maxKeyLength);
    // Write the key length
    dataOut.writeInt(maxKeyLength);
    // Write the vector dim
    dataOut.writeInt(vectorLength);


    // Write the size of the dataset
    dataOut.writeInt(this.size());

    for (Map.Entry<String, float[]> entry : this.entrySet()) {
      // Write the length of the key
      byte[] key = entry.getKey().getBytes();
      keyIntType.write(dataOut, key.length);
      dataOut.write(key);
      // Write the vector
      for (float v : entry.getValue()) {
        dataOut.writeShort(fromFloat(v));
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

    // Read the max key length
    itype keyIntType = itype.getType(dataIn.readInt());
    // Read the vector dimensionality
    int dim = dataIn.readInt();
    // Read the size of the dataset
    int size = dataIn.readInt();

    // Read the vectors
    VectorMap vectors = new VectorMap();
    for (int i = 0; i < size; ++i) {
      // Read the key
      int strlen = keyIntType.read(dataIn);
      byte[] buffer = new byte[strlen];
      if (dataIn.read(buffer, 0, strlen) != strlen) {
        throw new IOException("Could not read string buffer fully!");
      }
      String key = new String(buffer);
      // Read the vector
      float[] vector = new float[dim];
      for (int k = 0; k < vector.length; ++k) {
        vector[k] = toFloat(dataIn.readShort());
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
            // Vectors are the same length
            if (entry.getValue().length != otherValue.length) {
              return false;
            }
            // Vectors are the same value
            for (int i = 0; i < otherValue.length; ++i) {
              if (!sameFloat(entry.getValue()[i], otherValue[i])) {
                return false;
              }
            }
          }
        }
        return true;
      } catch (ClassCastException e) {
        e.printStackTrace();
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

  /**
   * The check to see if two floats are "close enough."
   */
  private static boolean sameFloat(float a, float b) {
    float absDiff = Math.abs(a - b);
    float absA = Math.abs(a);
    float absB = Math.abs(b);
    return absDiff < 1e-10 ||
           absDiff < Math.max(absA, absB) / 100.0f ||
           (absA < 1e-5 && absB < 1e-5);
  }

  /**
   * From  http://stackoverflow.com/questions/6162651/half-precision-floating-point-in-java
   */
  private static float toFloat( short hbits ) {
    int mant = hbits & 0x03ff;            // 10 bits mantissa
    int exp =  hbits & 0x7c00;            // 5 bits exponent
    if( exp == 0x7c00 )                   // NaN/Inf
      exp = 0x3fc00;                    // -> NaN/Inf
    else if( exp != 0 )                   // normalized value
    {
      exp += 0x1c000;                   // exp - 15 + 127
      if( mant == 0 && exp > 0x1c400 )  // smooth transition
        return Float.intBitsToFloat( ( hbits & 0x8000 ) << 16
            | exp << 13 | 0x3ff );
    }
    else if( mant != 0 )                  // && exp==0 -> subnormal
    {
      exp = 0x1c400;                    // make it normal
      do {
        mant <<= 1;                   // mantissa * 2
        exp -= 0x400;                 // decrease exp by 1
      } while( ( mant & 0x400 ) == 0 ); // while not normal
      mant &= 0x3ff;                    // discard subnormal bit
    }                                     // else +/-0 -> +/-0
    return Float.intBitsToFloat(          // combine all parts
        ( hbits & 0x8000 ) << 16          // sign  << ( 31 - 15 )
            | ( exp | mant ) << 13 );         // value << ( 23 - 10 )
  }

  /**
   * From  http://stackoverflow.com/questions/6162651/half-precision-floating-point-in-java
   */
  private static short fromFloat( float fval ) {
    int fbits = Float.floatToIntBits( fval );
    int sign = fbits >>> 16 & 0x8000;          // sign only
    int val = ( fbits & 0x7fffffff ) + 0x1000; // rounded value

    if( val >= 0x47800000 )               // might be or become NaN/Inf
    {                                     // avoid Inf due to rounding
      if( ( fbits & 0x7fffffff ) >= 0x47800000 )
      {                                 // is or must become NaN/Inf
        if( val < 0x7f800000 )        // was value but too large
          return (short) (sign | 0x7c00);     // make it +/-Inf
        return (short) (sign | 0x7c00 |        // remains +/-Inf or NaN
            ( fbits & 0x007fffff ) >>> 13); // keep NaN (and Inf) bits
      }
      return (short) (sign | 0x7bff);             // unrounded not quite Inf
    }
    if( val >= 0x38800000 )               // remains normalized value
      return (short) (sign | val - 0x38000000 >>> 13); // exp - 127 + 15
    if( val < 0x33000000 )                // too small for subnormal
      return (short) sign;                      // becomes +/-0
    val = ( fbits & 0x7fffffff ) >>> 23;  // tmp exp for subnormal calc
    return (short) (sign | ( ( fbits & 0x7fffff | 0x800000 ) // add subnormal bit
        + ( 0x800000 >>> val - 102 )     // round depending on cut off
        >>> 126 - val ));   // div by 2^(1-(exp-127+15)) and >> 13 | exp=0
  }
}
