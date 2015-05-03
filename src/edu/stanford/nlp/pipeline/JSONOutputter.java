package edu.stanford.nlp.pipeline;


import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.Timex;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;

import java.io.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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


  /** {@inheritDoc} */
  @SuppressWarnings("RedundantCast")  // It's lying; we need the "redundant" casts (as of 2014-09-08)
  @Override
  public void print(Annotation doc, OutputStream target, Options options) throws IOException {
    JSONWriter l0 = new JSONWriter(new PrintWriter(target));

    l0.object(l1 -> {

      // Add annotations attached to a Document
      l1.set("docId", doc.get(CoreAnnotations.DocIDAnnotation.class));
      l1.set("docDate", doc.get(CoreAnnotations.DocDateAnnotation.class));
      l1.set("docSourceType", doc.get(CoreAnnotations.DocSourceTypeAnnotation.class));
      l1.set("docType", doc.get(CoreAnnotations.DocTypeAnnotation.class));
      l1.set("author", doc.get(CoreAnnotations.AuthorAnnotation.class));
      l1.set("location", doc.get(CoreAnnotations.LocationAnnotation.class));
      if (options.includeText) {
        l1.set("text", doc.get(CoreAnnotations.TextAnnotation.class));
      }

      // Add sentences
      if (doc.get(CoreAnnotations.SentencesAnnotation.class) != null) {
        l1.set("sentences", doc.get(CoreAnnotations.SentencesAnnotation.class).stream().map(sentence -> (Consumer<Writer>) (Writer l2) -> {
          // Add a single sentence
          // (metadata)
          l2.set("id", sentence.get(CoreAnnotations.SentenceIDAnnotation.class));
          l2.set("index", sentence.get(CoreAnnotations.SentenceIndexAnnotation.class));
          l2.set("line", sentence.get(CoreAnnotations.LineNumberAnnotation.class));
          // (constituency tree)
          StringWriter treeStrWriter = new StringWriter();
          options.constituentTreePrinter.printTree(sentence.get(TreeCoreAnnotations.TreeAnnotation.class), new PrintWriter(treeStrWriter, true));
          l2.set("parse", treeStrWriter.toString());
          // (dependency trees)
          l2.set("basic-dependencies", buildDependencyTree(sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class)));
          l2.set("collapsed-dependencies", buildDependencyTree(sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class)));
          l2.set("collapsed-ccprocessed-dependencies", buildDependencyTree(sentence.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class)));
          // (sentiment)
          Tree sentimentTree = sentence.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class);
          if (sentimentTree != null) {
            int sentiment = RNNCoreAnnotations.getPredictedClass(sentimentTree);
            String sentimentClass = sentence.get(SentimentCoreAnnotations.SentimentClass.class);
            l2.set("sentimentValue", Integer.toString(sentiment));
            l2.set("sentiment", sentimentClass.replaceAll(" ", ""));
          }

          // (add tokens)
          if (sentence.get(CoreAnnotations.TokensAnnotation.class) != null) {
            l2.set("tokens", sentence.get(CoreAnnotations.TokensAnnotation.class).stream().map(token -> (Consumer<Writer>) (Writer l3) -> {
              // Add a single token
              l3.set("index", token.index());
              l3.set("word", token.word());
              l3.set("lemma", token.lemma());
              l3.set("characterOffsetBegin", token.beginPosition());
              l3.set("characterOffsetEnd", token.endPosition());
              l3.set("pos", token.tag());
              l3.set("ner", token.ner());
              l3.set("normalizedNER", token.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class));
              l3.set("speaker", token.get(CoreAnnotations.SpeakerAnnotation.class));
              l3.set("truecase", token.get(CoreAnnotations.TrueCaseAnnotation.class));
              l3.set("truecaseText", token.get(CoreAnnotations.TrueCaseTextAnnotation.class));
              // Timex
              Timex time = token.get(TimeAnnotations.TimexAnnotation.class);
              if (time != null) {
                l3.set("timex", (Consumer<Writer>) l4 -> {
                  l4.set("tid", time.tid());
                  l4.set("type", time.timexType());
                  l4.set("value", time.value());
                  l4.set("altValue", time.altVal());
                });
              }
            }));
          }
        }));
      }
    });

    l0.writer.flush();  // flush
  }

  /**
   * Convert a dependency graph to a format expected as input to {@link Writer#set(String, Object)}.
   */
  @SuppressWarnings("RedundantCast")  // It's lying; we need the "redundant" casts (as of 2014-09-08)
  private static Object buildDependencyTree(SemanticGraph graph) {
    if(graph != null) {
      return Stream.concat(
          // Roots
          graph.getRoots().stream().map( (IndexedWord root) -> (Consumer<Writer>) dep -> {
            dep.set("dep", "ROOT");
            dep.set("governor", "0");
            dep.set("governorGloss", "ROOT");
            dep.set("dependent", Integer.toString(root.index()));
            dep.set("dependentGloss", root.word());
          }),
          // Regular edges
          graph.edgeListSorted().stream().map( (SemanticGraphEdge edge) -> (Consumer<Writer>) (Writer dep) -> {
            dep.set("dep", edge.getRelation().toString());
            dep.set("governor", Integer.toString(edge.getGovernor().index()));
            dep.set("governorGloss", edge.getGovernor().word());
            dep.set("dependent", Integer.toString(edge.getDependent().index()));
            dep.set("dependentGloss", edge.getDependent().word());
          })
      );
    } else {
      return null;
    }
  }

  public static void jsonPrint(Annotation annotation, OutputStream os) throws IOException {
    new JSONOutputter().print(annotation, os);
  }

  public static void jsonPrint(Annotation annotation, OutputStream os, StanfordCoreNLP pipeline) throws IOException {
    new JSONOutputter().print(annotation, os, pipeline);
  }

  public static void jsonPrint(Annotation annotation, OutputStream os, Options options) throws IOException {
    new JSONOutputter().print(annotation, os, options);
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
      } else if (value instanceof Stream) {
        routeObject(indent, ((Stream) value).collect(Collectors.toList()));
      } else if (value.getClass().isArray()) {
        routeObject(indent, Arrays.asList((Object[]) value));
      } else if (value instanceof Integer) {
        routeObject(indent, Integer.toString((Integer) value));
      } else if (value instanceof Double) {
        routeObject(indent, Double.toString((Double) value));
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
