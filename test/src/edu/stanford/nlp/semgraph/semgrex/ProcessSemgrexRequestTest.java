package edu.stanford.nlp.semgraph.semgrex;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.pipeline.CoreNLPProtos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ProcessSemgrexRequestTest {
  /**
   * Build a fake request.  The same query will be repeated N times
   */
  public static CoreNLPProtos.SemgrexRequest buildFakeRequest(int numQueries, int numSemgrex) {
    return buildFakeRequest(numQueries, numSemgrex, "{}=source >dobj=foo {}=target");
  }

  public static CoreNLPProtos.SemgrexRequest buildFakeRequest(int numQueries, int numSemgrex, String semgrexPattern) {
    CoreNLPProtos.SemgrexRequest.Builder request = CoreNLPProtos.SemgrexRequest.newBuilder();
    for (int i = 0; i < numSemgrex; ++i) {
      request.addSemgrex(semgrexPattern);
    }

    for (int i = 0; i < numQueries; ++i) {
      CoreNLPProtos.SemgrexRequest.Dependencies.Builder queryBuilder = CoreNLPProtos.SemgrexRequest.Dependencies.newBuilder();
      CoreNLPProtos.DependencyGraph.Builder graphBuilder = CoreNLPProtos.DependencyGraph.newBuilder();

      String[] words = {"Unban", "Mox", "Opal"};
      int index = 1;
      for (String word : words) {
        CoreNLPProtos.Token.Builder tokenBuilder = CoreNLPProtos.Token.newBuilder();
        tokenBuilder.setWord(word);
        tokenBuilder.setValue(word);
        queryBuilder.addToken(tokenBuilder.build());

        CoreNLPProtos.DependencyGraph.Node.Builder nodeBuilder = CoreNLPProtos.DependencyGraph.Node.newBuilder();
        nodeBuilder.setSentenceIndex(1);
        nodeBuilder.setIndex(index);
        graphBuilder.addNode(nodeBuilder.build());

        ++index;
      }

      CoreNLPProtos.DependencyGraph.Edge.Builder edgeBuilder = CoreNLPProtos.DependencyGraph.Edge.newBuilder();
      edgeBuilder.setSource(1);
      edgeBuilder.setTarget(2);
      edgeBuilder.setDep("dobj");
      graphBuilder.addEdge(edgeBuilder.build());

      edgeBuilder = CoreNLPProtos.DependencyGraph.Edge.newBuilder();
      edgeBuilder.setSource(2);
      edgeBuilder.setTarget(3);
      edgeBuilder.setDep("nn");
      graphBuilder.addEdge(edgeBuilder.build());

      queryBuilder.setGraph(graphBuilder.build());
      request.addQuery(queryBuilder.build());
    }
    return request.build();
  }

    /**
     * Result should look like this:
     * <code>
result {
  match {
    index: 1
    node {
      name: "source"
      index: 1
    }
    node {
      name: "target"
      index: 2
    }
    reln {
      name: "foo"
      reln: "dobj"
    }
  }
}
    * </code>
    */
  @Test
  public void testSimpleRequest() {
    CoreNLPProtos.SemgrexRequest request = buildFakeRequest(1, 1);
    CoreNLPProtos.SemgrexResponse response = ProcessSemgrexRequest.processRequest(request);

    Assert.assertEquals("Expected exactly 1 reply", 1, response.getResultList().size());
    checkResult(response, 1, 0, true);
  }

  @Test
  public void testTwoSemgrex() {
    CoreNLPProtos.SemgrexRequest request = buildFakeRequest(1, 2);
    CoreNLPProtos.SemgrexResponse response = ProcessSemgrexRequest.processRequest(request);

    Assert.assertEquals("Expected exactly 1 reply", 1, response.getResultList().size());
    checkResult(response, 2, 0, true);
  }

  public static void checkResult(CoreNLPProtos.SemgrexResponse response, int numSemgrex, int graphIdx, boolean shouldMatch) {
    CoreNLPProtos.SemgrexResponse.GraphResult result = response.getResultList().get(graphIdx);

    Assert.assertEquals("Expected exactly " + numSemgrex + " semgrex result(s)", numSemgrex, result.getResultList().size());

    int semgrexIdx = 0;
    for (CoreNLPProtos.SemgrexResponse.SemgrexResult semgrexResult : result.getResultList()) {
      if (shouldMatch) {
        Assert.assertEquals("Expected exactly 1 match", 1, semgrexResult.getMatchList().size());
        CoreNLPProtos.SemgrexResponse.Match match = semgrexResult.getMatchList().get(0);

        Assert.assertEquals("Match is supposed to be at the root", 1, match.getMatchIndex());
        Assert.assertEquals("Expected exactly 2 named nodes", 2, match.getNodeList().size());
        Assert.assertEquals("Expected exactly 1 named reln", 1, match.getRelnList().size());
        Assert.assertEquals("Expected exactly 1 named edge", 1, match.getEdgeList().size());

        Assert.assertEquals("Node 1 should be source", 1, match.getNodeList().get(0).getMatchIndex());
        Assert.assertEquals("Node 1 should be source", "source", match.getNodeList().get(0).getName());
        Assert.assertEquals("Node 2 should be target", 2, match.getNodeList().get(1).getMatchIndex());
        Assert.assertEquals("Node 2 should be target", "target", match.getNodeList().get(1).getName());

        Assert.assertEquals("Reln dobj should be named foo", "foo", match.getRelnList().get(0).getName());
        Assert.assertEquals("Reln dobj should be have reln dobj", "dobj", match.getRelnList().get(0).getReln());

        Assert.assertEquals("Edge dobj should be named foo", "foo", match.getEdgeList().get(0).getName());
        Assert.assertEquals("Edge dobj should have reln dobj", "dobj", match.getEdgeList().get(0).getReln());
        Assert.assertEquals("Edge dobj source should be 1", 1, match.getEdgeList().get(0).getSource());
        Assert.assertEquals("Edge dobj source should be 2", 2, match.getEdgeList().get(0).getTarget());

        Assert.assertEquals("Graph count was off", graphIdx, match.getGraphIndex());
        Assert.assertEquals("Semgrex pattern count was off", semgrexIdx, match.getSemgrexIndex());
      } else {
        Assert.assertEquals("Expected exactly 0 match", 0, semgrexResult.getMatchList().size());
      }
      ++semgrexIdx;
    }
  }

  @Test
  public void testEmptyRequest() {
    CoreNLPProtos.SemgrexRequest request = buildFakeRequest(0, 1);
    CoreNLPProtos.SemgrexResponse response = ProcessSemgrexRequest.processRequest(request);

    Assert.assertEquals("Expected exactly 0 replies", 0, response.getResultList().size());
  }

  @Test
  public void testTwoGraphs() {
    CoreNLPProtos.SemgrexRequest request = buildFakeRequest(2, 1);
    CoreNLPProtos.SemgrexResponse response = ProcessSemgrexRequest.processRequest(request);

    Assert.assertEquals("Expected exactly 2 replies", 2, response.getResultList().size());
    checkResult(response, 1, 0, true);
    checkResult(response, 1, 1, true);
  }

  /**
   * For this test, only the first graph should have any results for the given pattern
   *<br>
   * The uniq operator in the SemgrexPattern will remove the match from the second graph,
   * since the second graph is identical
   */
  @Test
  public void testTwoGraphsUniq() {
    CoreNLPProtos.SemgrexRequest request = buildFakeRequest(2, 1, "{}=source >dobj=foo {}=target :: uniq source");
    CoreNLPProtos.SemgrexResponse response = ProcessSemgrexRequest.processRequest(request);

    Assert.assertEquals("Expected exactly 2 replies", 2, response.getResultList().size());
    checkResult(response, 1, 0, true);
    checkResult(response, 1, 1, false);
  }

  public byte[] buildRepeatedRequest(int count, boolean closingLength) throws IOException {
    ByteArrayOutputStream singleBout = new ByteArrayOutputStream();
    CoreNLPProtos.SemgrexRequest singleRequest = buildFakeRequest(1, 1);
    singleRequest.writeTo(singleBout);
    byte[] singleBytes = singleBout.toByteArray();

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    DataOutputStream dout = new DataOutputStream(bout);
    for (int i = 0; i < count; ++i) {
      dout.writeInt(singleBytes.length);
      dout.write(singleBytes, 0, singleBytes.length);
    }
    if (closingLength) {
      dout.writeInt(0);
    }
    dout.close();

    return bout.toByteArray();
  }

  public void checkRepeatedResults(byte[] arr, int count) throws IOException {
    ByteArrayInputStream bin = new ByteArrayInputStream(arr);
    DataInputStream din = new DataInputStream(bin);
    for (int i = 0; i < count; ++i) {
      int len = din.readInt();
      byte[] responseBytes = new byte[len];
      din.read(responseBytes, 0, len);
      CoreNLPProtos.SemgrexResponse response = CoreNLPProtos.SemgrexResponse.parseFrom(responseBytes);
      checkResult(response, 1, 0, true);
    }
    int len = din.readInt();
    Assert.assertEquals("Repeated results should be over", 0, len);
  }

  /**
   * Test that the multiple request pathway works with 1 request
   */
  @Test
  public void testSingleMultiRequest() throws IOException {
    byte[] request = buildRepeatedRequest(1, true);
    ByteArrayInputStream bin = new ByteArrayInputStream(request);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();

    ProcessSemgrexRequest processor = new ProcessSemgrexRequest();
    processor.processMultipleInputs(bin, bout);
    checkRepeatedResults(bout.toByteArray(), 1);
  }

  /**
   * Test that the multiple request pathway works with 2 requests
   */
  @Test
  public void testDoubleMultiRequest() throws IOException {
    byte[] request = buildRepeatedRequest(2, true);
    ByteArrayInputStream bin = new ByteArrayInputStream(request);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();

    ProcessSemgrexRequest processor = new ProcessSemgrexRequest();
    processor.processMultipleInputs(bin, bout);
    checkRepeatedResults(bout.toByteArray(), 2);
  }

  /**
   * Test that the multiple request pathway works even when the
   * input stream hits EOF
   */
  @Test
  public void testUnclosedMultiRequest() throws IOException {
    byte[] request = buildRepeatedRequest(1, false);
    ByteArrayInputStream bin = new ByteArrayInputStream(request);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();

    ProcessSemgrexRequest processor = new ProcessSemgrexRequest();
    processor.processMultipleInputs(bin, bout);
    checkRepeatedResults(bout.toByteArray(), 1);
  }
}
