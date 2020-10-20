package edu.stanford.nlp.semgraph.semgrex;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.pipeline.CoreNLPProtos;

public class ProcessSemgrexRequestTest {
  /**
   * Build a fake request.  The same query will be repeated N times
   */
  public static CoreNLPProtos.SemgrexRequest buildFakeRequest(int numQueries, int numSemgrex) {
    CoreNLPProtos.SemgrexRequest.Builder request = CoreNLPProtos.SemgrexRequest.newBuilder();
    for (int i = 0; i < numSemgrex; ++i) {
      request.addSemgrex("{}=source >dobj=zzz {}=target");
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
      name: "zzz"
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
    checkResult(response.getResultList().get(0), 1);
  }

  @Test
  public void testMultiSemgrex() {
    CoreNLPProtos.SemgrexRequest request = buildFakeRequest(1, 2);
    CoreNLPProtos.SemgrexResponse response = ProcessSemgrexRequest.processRequest(request);

    Assert.assertEquals("Expected exactly 1 reply", 1, response.getResultList().size());
    checkResult(response.getResultList().get(0), 2);
  }

  public static void checkResult(CoreNLPProtos.SemgrexResponse.GraphResult result, int numSemgrex) {
    Assert.assertEquals("Expected exactly " + numSemgrex + " semgrex result(s)", numSemgrex, result.getResultList().size());

    for (CoreNLPProtos.SemgrexResponse.SemgrexResult semgrexResult : result.getResultList()) {
      Assert.assertEquals("Expected exactly 1 match", 1, semgrexResult.getMatchList().size());
      CoreNLPProtos.SemgrexResponse.Match match = semgrexResult.getMatchList().get(0);

      Assert.assertEquals("Match is supposed to be at the root", 1, match.getMatchIndex());
      Assert.assertEquals("Expected exactly 2 named nodes", 2, match.getNodeList().size());
      Assert.assertEquals("Expected exactly 1 named reln", 1, match.getRelnList().size());

      Assert.assertEquals("Node 1 should be source", 1, match.getNodeList().get(0).getMatchIndex());
      Assert.assertEquals("Node 1 should be source", "source", match.getNodeList().get(0).getName());
      Assert.assertEquals("Node 2 should be target", 2, match.getNodeList().get(1).getMatchIndex());
      Assert.assertEquals("Node 2 should be target", "target", match.getNodeList().get(1).getName());

      Assert.assertEquals("Reln dobj should be named zzz", "zzz", match.getRelnList().get(0).getName());
      Assert.assertEquals("Reln dobj should be named zzz", "dobj", match.getRelnList().get(0).getReln());
    }
  }

  @Test
  public void testEmptyRequest() {
    CoreNLPProtos.SemgrexRequest request = buildFakeRequest(0, 1);
    CoreNLPProtos.SemgrexResponse response = ProcessSemgrexRequest.processRequest(request);

    Assert.assertEquals("Expected exactly 0 replies", 0, response.getResultList().size());
  }

  @Test
  public void testMultiRequest() {
    CoreNLPProtos.SemgrexRequest request = buildFakeRequest(2, 1);
    CoreNLPProtos.SemgrexResponse response = ProcessSemgrexRequest.processRequest(request);

    Assert.assertEquals("Expected exactly 2 replies", 2, response.getResultList().size());
    checkResult(response.getResultList().get(0), 1);
    checkResult(response.getResultList().get(1), 1);
  }
}
