package edu.stanford.nlp.pipeline;


import edu.stanford.nlp.ling.CoreAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Consumer;

/**
 * Output an Annotation to human readable JSON.
 * This is not a lossless operation; for more strict serialization,
 * see {@link edu.stanford.nlp.pipeline.AnnotationSerializer}; e.g.,
 * {@link edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer}.
 *
 * @author Gabor Angeli
 */
public class JSONOutputter extends AnnotationOutputter {

  protected static final String INDENT_CHAR = "  ";


  @Override
  public void print(Annotation doc, OutputStream target, Options options) throws IOException {
    JSONWriter l0 = new JSONWriter(new PrintWriter(target));

    l0.object(l1 -> {
      l1.set("docId", doc.get(CoreAnnotations.DocIDAnnotation.class));
      l1.set("docDate", doc.get(CoreAnnotations.DocDateAnnotation.class));
      l1.set("docSourceType", doc.get(CoreAnnotations.DocSourceTypeAnnotation.class));
      l1.set("docType", doc.get(CoreAnnotations.DocTypeAnnotation.class));
      l1.set("author", doc.get(CoreAnnotations.AuthorAnnotation.class));
      l1.set("location", doc.get(CoreAnnotations.LocationAnnotation.class));
    });

  }


  /**
   * <p>Our very own little JSON writing class.
   * For usage, see the test cases in JSONOutputterTest.</p>
   *
   * <p>For the love of all that is holy, don't try to write JSON multithreaded.
   * It should go without saying that this is not threadsafe.</p>
   */
  protected static class JSONWriter {
    private final PrintWriter writer;
    private JSONWriter(PrintWriter writer) {
      this.writer = writer;
    }

    protected static String cleanJSON(String s) {
      return s
          .replace("\\", "\\\\")
          .replace("\b", "\\b")
          .replace("\f", "\\f")
          .replace("\n", "\\n")
          .replace("\r", "\\r")
          .replace("\t", "\\t")
          .replace("'", "\\'")
          .replace("\"", "\\\"");
    }

    @SuppressWarnings("unchecked")
    private void routeObject(int indent, Object value) {
      if (value instanceof String) {
        // Case: simple string (this is easy!)
        writer.write("\"");
        writer.write(cleanJSON(value.toString()));
        writer.write("\"");
      } else if (value instanceof Collection) {
        // Case: collection
        writer.write("[\n");
        Iterator<Object> elems = ((Collection<Object>) value).iterator();
        while (elems.hasNext()) {
          indent(indent + 1);
          routeObject(indent + 1, elems.next());
          if (elems.hasNext()) {
            writer.write(",");
          }
          writer.write("\n");
        }
        indent(indent);
        writer.write("]");
      } else if (value instanceof Consumer) {
        object(indent, (Consumer<Writer>) value);
      } else {
        throw new RuntimeException("Unknown object to serialize: " + value);
      }
    }

    private void indent(int num) {
      for (int i = 0; i < num; ++i) {
        writer.write(INDENT_CHAR);
      }
    }

    public void object(int indent, Consumer<Writer> callback) {
      writer.write("{");
      final boolean[] firstCall = new boolean[]{ true }; // Array is a poor man's pointer
      callback.accept((key, value) -> {
        if (key != null && value != null) {
          // First call overhead
          if (!firstCall[0]) {
            writer.write(",");
          }
          firstCall[0] = false;
          // Write the key
          writer.write("\n");
          indent(indent + 1);
          writer.write("\"");
          writer.write(cleanJSON(key));
          writer.write("\": ");
          // Write the value
          routeObject(indent + 1, value);
        }
      });
      writer.write("\n"); indent(indent); writer.write("}");
    }

    public void object(Consumer<Writer> callback) {
      object(0, callback);
    }

    public static String objectToJSON(Consumer<Writer> callback) {
      OutputStream os = new ByteArrayOutputStream();
      PrintWriter out = new PrintWriter(os);
      new JSONWriter(out).object(callback);
      out.close();
      return os.toString();
    }
  }

  /**
   * A tiny little functional interface for writing a (key, value) pair.
   * The key should always be a String, the value can be either a String,
   * a Collection of valid values, or a Callback taking a Writer (this is how
   * we represent objects while creating JSON).
   */
  @FunctionalInterface
  protected interface Writer {
    /**
     * Set a (key, value) pair in a JSON object.
     * Note that if either the key or the value is null, nothing will be set.
     * @param key The key of the object.
     * @param value The value of the object.
     */
    public void set(String key, Object value);
  }

}
