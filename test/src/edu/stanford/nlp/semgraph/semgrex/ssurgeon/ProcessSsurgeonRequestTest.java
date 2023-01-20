package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import org.junit.Test;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;
import edu.stanford.nlp.semgraph.SemanticGraph;

import static edu.stanford.nlp.pipeline.CoreNLPProtos.DependencyGraph;
import static edu.stanford.nlp.pipeline.CoreNLPProtos.SsurgeonRequest;
import static edu.stanford.nlp.pipeline.CoreNLPProtos.SsurgeonResponse;

public class ProcessSsurgeonRequestTest {
  public static SsurgeonRequest buildRequest(List<SsurgeonRequest.Ssurgeon> operations,
                                             List<DependencyGraph> graphs) {
    SsurgeonRequest.Builder builder = SsurgeonRequest.newBuilder();
    for (SsurgeonRequest.Ssurgeon operation : operations) {
      builder.addSsurgeon(operation);
    }
    for (DependencyGraph graph : graphs) {
      builder.addGraph(graph);
    }
    return builder.build();
  }

  public static List<DependencyGraph> buildGraphs(String ... graphs) {
    ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer();
    List<SemanticGraph> semgraphs = Arrays.asList(graphs).stream().map(SemanticGraph::valueOf).collect(Collectors.toList());;
    List<DependencyGraph> dgraphs = semgraphs.stream().map((x) -> serializer.toProto(x, true)).collect(Collectors.toList());;
    return dgraphs;
  }

  public static List<SsurgeonRequest.Ssurgeon> buildOperationList(SsurgeonRequest.Ssurgeon ... operations) {
    return Arrays.asList(operations);
  }

  public SsurgeonRequest.Ssurgeon buildOperation(String uid,
                                                 String notes,
                                                 String semgrex,
                                                 String ... edits) {
    SsurgeonRequest.Ssurgeon.Builder builder = SsurgeonRequest.Ssurgeon.newBuilder();
    builder.setId(uid);
    builder.setNotes(notes);
    builder.setSemgrex(semgrex);
    for (String edit : edits) {
      builder.addOperation(edit);
    }
    return builder.build();
  }

  /**
   * Test one graph with one operation
   */
  @Test
  public void testSimpleRequest() {
    List<SsurgeonRequest.Ssurgeon> operations = buildOperationList(buildOperation("1",
                                                                                  "Test of addEdge",
                                                                                  "{}=a1 >obj {}=a2",
                                                                                  "addEdge -gov a1 -dep a2 -reln dep -weight 0.5"));
    ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer();

    List<DependencyGraph> graphs = buildGraphs("[A-1 obj> B-2 obj> C-3 nsubj> [D-4 obj> E-5]]");
    SsurgeonRequest request = buildRequest(operations, graphs);
    SsurgeonResponse response = ProcessSsurgeonRequest.processRequest(request);
    assertEquals(1, response.getResultList().size());
    assertTrue(response.getResultList().get(0).getChanged());

    DependencyGraph inputGraph = response.getResultList().get(0).getGraph();
    List<CoreLabel> tokens = inputGraph.getTokenList().stream().map(serializer::fromProto).collect(Collectors.toList());
    SemanticGraph resultGraph = ProtobufAnnotationSerializer.fromProto(inputGraph, tokens, null);
    SemanticGraph expected = SemanticGraph.valueOf("[A-1 obj> B-2 dep> B-2 obj> C-3 dep> C-3 nsubj> [D-4 obj> E-5 dep> E-5]]");
    assertEquals(expected, resultGraph);
  }
}
