package edu.stanford.nlp.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.TwoDimensionalMap;

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

  /**
   * Append a CoreDocument to this output stream.
   *
   * @param document The CoreDocument to serialize (its internal annotation is serialized)
   * @param os The output stream to serialize to
   * @return The output stream which should be closed
   * @throws IOException
   */
  public OutputStream writeCoreDocument(CoreDocument document, OutputStream os) throws IOException {
    Annotation wrappedAnnotation = document.annotation();
    return write(wrappedAnnotation, os);
  }

  /**
   * Read in a CoreDocument from this input stream.
   *
   * @param is The input stream to read a CoreDocument's annotation from
   * @return A pair with the CoreDocument and the input stream
   * @throws IOException
   * @throws ClassNotFoundException
   * @throws ClassCastException
   */

  public Pair<CoreDocument, InputStream> readCoreDocument(InputStream is)
      throws IOException, ClassNotFoundException, ClassCastException {
    Pair<Annotation, InputStream> readPair = read(is);
    CoreDocument readCoreDocument = new CoreDocument(readPair.first());
    return new Pair<CoreDocument, InputStream>(readCoreDocument, is);
  }


  public static class IntermediateNode {
    String docId;
    int sentIndex;
    int index;
    int copyAnnotation;
    boolean isRoot;
    public IntermediateNode(String docId, int sentIndex, int index, int copy, boolean isRoot) {
      this.docId = docId;
      this.sentIndex = sentIndex;
      this.index = index;
      this.copyAnnotation = copy;
      this.isRoot = isRoot;
    }
  }

  public static class IntermediateEdge {
    int source;
    int sourceCopy;
    int target;
    int targetCopy;
    String dep;
    boolean isExtra;
    public IntermediateEdge(String dep, int source, int sourceCopy, int target, int targetCopy, boolean isExtra) {
      this.dep = dep;
      this.source = source;
      this.sourceCopy = sourceCopy;
      this.target = target;
      this.targetCopy = targetCopy;
      this.isExtra = isExtra;
    }
  }

  public static class IntermediateSemanticGraph {
    public List<IntermediateNode> nodes;
    public List<IntermediateEdge> edges;
    public IntermediateSemanticGraph() {
      nodes = new ArrayList<>();
      edges = new ArrayList<>();
    }

    public IntermediateSemanticGraph(List<IntermediateNode> nodes, List<IntermediateEdge> edges) {
      this.nodes = new ArrayList<>(nodes);
      this.edges = new ArrayList<>(edges);
    }

    private static final Object LOCK = new Object();

    public SemanticGraph convertIntermediateGraph(List<CoreLabel> sentence) {
      SemanticGraph graph = new SemanticGraph();

      // First construct the actual nodes; keep them indexed by their index and copy count.
      // Sentences such as "I went over the river and through the woods" have
      // two copies for "went" in the collapsed dependencies.
      TwoDimensionalMap<Integer, Integer, IndexedWord> nodeMap = TwoDimensionalMap.hashMap();
      for (IntermediateNode in: nodes){
        CoreLabel token = sentence.get(in.index - 1); // index starts at 1!
        IndexedWord word;
        if (in.copyAnnotation > 0) {
          // TODO: if we make a copy wrapper CoreLabel, use it here instead
          word = new IndexedWord(new CoreLabel(token));
          word.setCopyCount(in.copyAnnotation);
        } else {
          word = new IndexedWord(token);
        }

        // for backwards compatibility - new annotations should have
        // these fields set, but annotations older than August 2014 might not
        if (word.docID() == null && in.docId != null) {
          word.setDocID(in.docId);
        }
        if (word.sentIndex() < 0 && in.sentIndex >= 0) {
          word.setSentIndex(in.sentIndex);
        }
        if (word.index() < 0 && in.index >= 0) {
          word.setIndex(in.index);
        }

        nodeMap.put(word.index(), word.copyCount(), word);
        graph.addVertex(word);
        if (in.isRoot) {
          graph.addRoot(word);
        }
      }

      // add all edges to the actual graph
      for (IntermediateEdge ie: edges) {
        IndexedWord source = nodeMap.get(ie.source, ie.sourceCopy);
        if (source == null) {
          throw new RuntimeIOException("Failed to find node " + ie.source + "-" + ie.sourceCopy);
        }
        IndexedWord target = nodeMap.get(ie.target, ie.targetCopy);
        if (target == null) {
          throw new RuntimeIOException("Failed to find node " + ie.target + "-" + ie.targetCopy);
        }
        // assert(target != null);
        synchronized (LOCK) {
          // this is not thread-safe: there are static fields in GrammaticalRelation
          GrammaticalRelation rel = GrammaticalRelation.valueOf(ie.dep);
          graph.addEdge(source, target, rel, 1.0, ie.isExtra);
        }
      }

      // compute root nodes if they weren't stored in the graph
      if ( ! graph.isEmpty() && graph.getRoots().isEmpty()) {
        graph.resetRoots();
      }

      return graph;
    }
  }

}
