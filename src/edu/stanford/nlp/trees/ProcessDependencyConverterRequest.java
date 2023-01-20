package edu.stanford.nlp.trees;

/**
 * A tool to turn Tree objects into dependencies
 *
 * Only works for English (at least for now)
 */

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Collectors;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.CoreNLPProtos;
import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.ProcessProtobufRequest;

public class ProcessDependencyConverterRequest extends ProcessProtobufRequest {
  /**
   * Convert a single Tree to basic dependencies
   */
  static SemanticGraph convert(Tree tree) {
    SemanticGraph uncollapsedDeps = SemanticGraphFactory.makeFromTree(tree,
                                                                      SemanticGraphFactory.Mode.BASIC,
                                                                      GrammaticalStructure.Extras.NONE,
                                                                      null,
                                                                      false,
                                                                      true);
    return uncollapsedDeps;
  }

  /**
   * Process a single request, responding with basic dependencies for each tree
   */
  static CoreNLPProtos.DependencyConverterResponse processRequest(CoreNLPProtos.DependencyConverterRequest request) {
    ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer();
    CoreNLPProtos.DependencyConverterResponse.Builder responseBuilder = CoreNLPProtos.DependencyConverterResponse.newBuilder();

    List<CoreNLPProtos.FlattenedParseTree> flattenedTrees = request.getTreesList();
    int treeIdx = 0;
    for (CoreNLPProtos.FlattenedParseTree flattenedTree : flattenedTrees) {
      Tree tree = ProtobufAnnotationSerializer.fromProto(flattenedTree);
      SemanticGraph graph = convert(tree);
      for (IndexedWord node : graph.vertexSet()) {
        node.set(CoreAnnotations.SentenceIndexAnnotation.class, treeIdx);
      }
      CoreNLPProtos.DependencyConverterResponse.DependencyConversion.Builder conversionBuilder = CoreNLPProtos.DependencyConverterResponse.DependencyConversion.newBuilder();
      conversionBuilder.setGraph(serializer.toProto(graph));
      conversionBuilder.setTree(flattenedTree);
      responseBuilder.addConversions(conversionBuilder.build());
      ++treeIdx;
    }
    return responseBuilder.build();
  }

  /**
   * Process a single request from a stream, responding with basic dependencies for each tree
   */
  @Override
  public void processInputStream(InputStream in, OutputStream out) throws IOException {
    CoreNLPProtos.DependencyConverterRequest request = CoreNLPProtos.DependencyConverterRequest.parseFrom(in);
    CoreNLPProtos.DependencyConverterResponse response = processRequest(request);
    response.writeTo(out);
  }

  /**
   * The inherited main program will either enhance a single document,
   * or will listen to stdin and enhance every document that comes in
   * until a terminator is sent or the stream closes
   */
  public static void main(String[] args) throws IOException {
    ProcessProtobufRequest.process(new ProcessDependencyConverterRequest(), args);
  }
}
