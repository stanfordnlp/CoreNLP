package edu.stanford.nlp.pipeline;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;

/**
 * A test of a few specific methods in the ProtobufAnnotationSerializer
 *
 * @author John Bauer
 */
public class ProtobufAnnotationSerializerTest {
  /** Test serializing a graph and deserializing it */
  @Test
  public void testDependencySerialization() {
    checkGraphReversible("[A/foo-3 obj> B/bar-1 obj> C-4 nsubj> [D-2 obj> E-0]]", false);
  }

  /** Test that it still works if one of the nodes has an emptyIndex */
  @Test
  public void testDependencySerializationWithEmpty() {
    checkGraphReversible("[A/foo-3 obj> B/bar-1 obj> C-4 nsubj> [D-1.1 obj> E-0]]", false);
  }

  /**
   * Test that the legacy version of passing around root information still works.
   * After all, there may be old versions of software or old serialized graphs out there.
   */
  @Test
  public void testDependencySerializationLegacyRoots() {
    checkGraphReversible("[A/foo-3 obj> B/bar-1 obj> C-4 nsubj> [D-2 obj> E-0]]", true);
  }

  private void checkGraphReversible(String rawGraph, boolean legacyRoots) {
    ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer();

    // test some with tags and some without
    SemanticGraph sg = SemanticGraph.valueOf(rawGraph);

    CoreNLPProtos.DependencyGraph graphProto = serializer.toProto(sg, true);
    List<CoreLabel> labels = new ArrayList<>();
    for (CoreNLPProtos.Token tokenProto : graphProto.getTokenList()) {
      CoreLabel nextToken = serializer.fromProto(tokenProto);
      labels.add(nextToken);
    }

    if (legacyRoots) {
      CoreNLPProtos.DependencyGraph.Builder builder = CoreNLPProtos.DependencyGraph.newBuilder();
      builder.mergeFrom(graphProto);
      builder.clearRootNode();
      graphProto = builder.build();
    }

    SemanticGraph unpacked = serializer.fromProto(graphProto, labels, null);
    Assert.assertEquals(sg, unpacked);
  }

}
