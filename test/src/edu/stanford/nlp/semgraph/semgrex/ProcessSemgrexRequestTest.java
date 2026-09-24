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
   * <pre>
sentence {
  pattern {
    match {
      matchIndex: 1
      node {
        name: "source"
        matchIndex: 1
      }
      node {
        name: "target"
        matchIndex: 2
      }
      reln {
        name: "foo"
        reln: "dobj"
      }
      edge {
        name: "foo"
        source: 1
        target: 2
        reln: "dobj"
        isExtra: false
        graph: BASIC
      }
      sentenceIndex: 0
      semgrexIndex: 0
    }
    semgrexIndex: 0
  }
  sentenceIndex: 0
}
   * </pre>
   */
  @Test
  public void testSimpleRequest() {
    CoreNLPProtos.SemgrexRequest request = buildFakeRequest(1, 1);
    CoreNLPProtos.SemgrexResponse response = ProcessSemgrexRequest.processRequest(request);

    Assert.assertEquals("Expected exactly 1 reply", 1, response.getSentenceList().size());
    checkResult(response, 1, 0, true);
  }

  @Test
  public void testTwoSemgrex() {
    CoreNLPProtos.SemgrexRequest request = buildFakeRequest(1, 2);
    CoreNLPProtos.SemgrexResponse response = ProcessSemgrexRequest.processRequest(request);

    Assert.assertEquals("Expected exactly 1 reply", 1, response.getSentenceList().size());
    checkResult(response, 2, 0, true);
  }

  public static void checkResult(CoreNLPProtos.SemgrexResponse response, int numSemgrex, int sentenceIdx, boolean shouldMatch) {
    CoreNLPProtos.SemgrexResponse.SentenceResult result = response.getSentenceList().get(sentenceIdx);
    Assert.assertEquals("Sentence result has the wrong index", sentenceIdx, result.getSentenceIndex());

    Assert.assertEquals("Expected exactly " + numSemgrex + " semgrex result(s)", numSemgrex, result.getPatternList().size());

    int semgrexIdx = 0;
    for (CoreNLPProtos.SemgrexResponse.PatternResult patternResult : result.getPatternList()) {
      Assert.assertEquals("Pattern result has the wrong index", semgrexIdx, patternResult.getSemgrexIndex());
      if (shouldMatch) {
        Assert.assertEquals("Expected exactly 1 match", 1, patternResult.getMatchList().size());
        CoreNLPProtos.SemgrexResponse.Match match = patternResult.getMatchList().get(0);

        Assert.assertEquals("Match is supposed to be at the root", 1, match.getMatchIndex());
        Assert.assertFalse("Match root is not a copy", match.hasMatchCopy());
        Assert.assertFalse("Match root is not an empty node", match.hasMatchEmptyIndex());
        Assert.assertEquals("Expected exactly 2 named nodes", 2, match.getNodeList().size());
        Assert.assertEquals("Expected exactly 1 named reln", 1, match.getRelnList().size());
        Assert.assertEquals("Expected exactly 1 named edge", 1, match.getEdgeList().size());

        Assert.assertEquals("Node 1 should be source", 1, match.getNodeList().get(0).getMatchIndex());
        Assert.assertEquals("Node 1 should be source", "source", match.getNodeList().get(0).getName());
        Assert.assertEquals("Node 2 should be target", 2, match.getNodeList().get(1).getMatchIndex());
        Assert.assertEquals("Node 2 should be target", "target", match.getNodeList().get(1).getName());
        for (CoreNLPProtos.SemgrexResponse.NamedNode node : match.getNodeList()) {
          Assert.assertFalse("Named node is not a copy", node.hasCopy());
          Assert.assertFalse("Named node is not an empty node", node.hasEmptyIndex());
        }

        Assert.assertEquals("Reln dobj should be named foo", "foo", match.getRelnList().get(0).getName());
        Assert.assertEquals("Reln dobj should be have reln dobj", "dobj", match.getRelnList().get(0).getReln());

        CoreNLPProtos.SemgrexResponse.NamedEdge edge = match.getEdgeList().get(0);
        Assert.assertEquals("Edge dobj should be named foo", "foo", edge.getName());
        Assert.assertEquals("Edge dobj should have reln dobj", "dobj", edge.getReln());
        Assert.assertEquals("Edge dobj source should be 1", 1, edge.getSource());
        Assert.assertEquals("Edge dobj source should be 2", 2, edge.getTarget());
        Assert.assertTrue("Edge dobj should say which graph it is in", edge.hasGraph());
        Assert.assertEquals("Edge dobj is in the basic graph", CoreNLPProtos.SemgrexResponse.GraphName.BASIC, edge.getGraph());

        Assert.assertEquals("Sentence idx was off", sentenceIdx, match.getSentenceIndex());
        Assert.assertEquals("Semgrex pattern count was off", semgrexIdx, match.getSemgrexIndex());
      } else {
        Assert.assertEquals("Expected exactly 0 match", 0, patternResult.getMatchList().size());
      }
      ++semgrexIdx;
    }
  }

  @Test
  public void testEmptyRequest() {
    CoreNLPProtos.SemgrexRequest request = buildFakeRequest(0, 1);
    CoreNLPProtos.SemgrexResponse response = ProcessSemgrexRequest.processRequest(request);

    Assert.assertEquals("Expected exactly 0 replies", 0, response.getSentenceList().size());
  }

  @Test
  public void testTwoGraphs() {
    CoreNLPProtos.SemgrexRequest request = buildFakeRequest(2, 1);
    CoreNLPProtos.SemgrexResponse response = ProcessSemgrexRequest.processRequest(request);

    Assert.assertEquals("Expected exactly 2 replies", 2, response.getSentenceList().size());
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

    Assert.assertEquals("Expected exactly 2 replies", 2, response.getSentenceList().size());
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

  /**
   * Adds a token for each word to a query.  An index with a decimal
   * part, such as 5.1, is an empty node
   */
  private static void addTokens(CoreNLPProtos.SemgrexRequest.Dependencies.Builder queryBuilder, String[] indices, String[] words) {
    for (int i = 0; i < words.length; ++i) {
      CoreNLPProtos.Token.Builder tokenBuilder = CoreNLPProtos.Token.newBuilder();
      tokenBuilder.setWord(words[i]);
      tokenBuilder.setValue(words[i]);
      String[] pieces = indices[i].split("[.]");
      tokenBuilder.setIndex(Integer.parseInt(pieces[0]));
      if (pieces.length > 1) {
        tokenBuilder.setEmptyIndex(Integer.parseInt(pieces[1]));
      }
      queryBuilder.addToken(tokenBuilder.build());
    }
  }

  /**
   * Builds a graph from nodes such as "5" or "5.1" and edges such as
   * {"5.1", "6", "obj"}.  The root is always node 2
   */
  private static CoreNLPProtos.DependencyGraph buildGraph(String[] nodes, String[][] edges) {
    CoreNLPProtos.DependencyGraph.Builder graphBuilder = CoreNLPProtos.DependencyGraph.newBuilder();
    for (String node : nodes) {
      String[] pieces = node.split("[.]");
      CoreNLPProtos.DependencyGraph.Node.Builder nodeBuilder = CoreNLPProtos.DependencyGraph.Node.newBuilder();
      nodeBuilder.setSentenceIndex(0);
      nodeBuilder.setIndex(Integer.parseInt(pieces[0]));
      if (pieces.length > 1) {
        nodeBuilder.setEmptyIndex(Integer.parseInt(pieces[1]));
      }
      graphBuilder.addNode(nodeBuilder.build());
    }
    for (String[] edge : edges) {
      String[] source = edge[0].split("[.]");
      String[] target = edge[1].split("[.]");
      CoreNLPProtos.DependencyGraph.Edge.Builder edgeBuilder = CoreNLPProtos.DependencyGraph.Edge.newBuilder();
      edgeBuilder.setSource(Integer.parseInt(source[0]));
      edgeBuilder.setTarget(Integer.parseInt(target[0]));
      if (source.length > 1) {
        edgeBuilder.setSourceEmpty(Integer.parseInt(source[1]));
      }
      if (target.length > 1) {
        edgeBuilder.setTargetEmpty(Integer.parseInt(target[1]));
      }
      edgeBuilder.setDep(edge[2]);
      graphBuilder.addEdge(edgeBuilder.build());
    }
    graphBuilder.addRoot(2);
    return graphBuilder.build();
  }

  /**
   * "Sue likes coffee and Bill tea", where the enhanced graph has an
   * empty node 5.1 for the elided "likes"
   */
  public static CoreNLPProtos.SemgrexRequest buildEnhancedRequest(String ... semgrexPatterns) {
    CoreNLPProtos.SemgrexRequest.Builder request = CoreNLPProtos.SemgrexRequest.newBuilder();
    for (String semgrex : semgrexPatterns) {
      request.addSemgrex(semgrex);
    }

    CoreNLPProtos.SemgrexRequest.Dependencies.Builder queryBuilder = CoreNLPProtos.SemgrexRequest.Dependencies.newBuilder();
    addTokens(queryBuilder,
              new String[] {"1", "2", "3", "4", "5", "5.1", "6"},
              new String[] {"Sue", "likes", "coffee", "and", "Bill", "likes", "tea"});
    queryBuilder.setGraph(buildGraph(new String[] {"1", "2", "3", "4", "5", "6"},
                                     new String[][] {{"2", "1", "nsubj"},
                                                     {"2", "3", "obj"},
                                                     {"5", "4", "cc"},
                                                     {"2", "5", "conj"},
                                                     {"5", "6", "orphan"}}));
    queryBuilder.setEnhancedGraph(buildGraph(new String[] {"1", "2", "3", "4", "5", "5.1", "6"},
                                             new String[][] {{"2", "1", "nsubj"},
                                                             {"2", "3", "obj"},
                                                             {"5.1", "4", "cc"},
                                                             {"5.1", "5", "nsubj"},
                                                             {"5.1", "6", "obj"},
                                                             {"2", "5.1", "conj:and"}}));
    request.addQuery(queryBuilder.build());
    return request.build();
  }

  private static CoreNLPProtos.SemgrexResponse.Match onlyMatch(CoreNLPProtos.SemgrexResponse response, int semgrexIdx) {
    Assert.assertEquals(1, response.getSentenceCount());
    CoreNLPProtos.SemgrexResponse.PatternResult result = response.getSentence(0).getPattern(semgrexIdx);
    Assert.assertEquals(semgrexIdx, result.getSemgrexIndex());
    Assert.assertEquals("Expected exactly 1 match", 1, result.getMatchCount());
    return result.getMatch(0);
  }

  private static CoreNLPProtos.SemgrexResponse.NamedNode namedNode(CoreNLPProtos.SemgrexResponse.Match match, String name) {
    for (CoreNLPProtos.SemgrexResponse.NamedNode node : match.getNodeList()) {
      if (node.getName().equals(name)) {
        return node;
      }
    }
    throw new AssertionError("No node named " + name);
  }

  private static CoreNLPProtos.SemgrexResponse.NamedEdge namedEdge(CoreNLPProtos.SemgrexResponse.Match match, String name) {
    for (CoreNLPProtos.SemgrexResponse.NamedEdge edge : match.getEdgeList()) {
      if (edge.getName().equals(name)) {
        return edge;
      }
    }
    throw new AssertionError("No edge named " + name);
  }

  /**
   * A match can start at a node which is only in the enhanced graph,
   * and the response has to say it is 5.1 rather than 5
   */
  @Test
  public void testEnhancedOnlyRoot() {
    CoreNLPProtos.SemgrexRequest request = buildEnhancedRequest("{} !< {} <@enhanced {}");
    CoreNLPProtos.SemgrexResponse response = ProcessSemgrexRequest.processRequest(request);

    CoreNLPProtos.SemgrexResponse.Match match = onlyMatch(response, 0);
    Assert.assertEquals(5, match.getMatchIndex());
    Assert.assertEquals(1, match.getMatchEmptyIndex());
    Assert.assertFalse(match.hasMatchCopy());
  }

  /**
   * Named nodes and edges in the enhanced graph report their empty
   * nodes, and each edge says which graph it was matched in
   */
  @Test
  public void testEnhancedNodesAndEdges() {
    CoreNLPProtos.SemgrexRequest request = buildEnhancedRequest("{word:likes}=l >nsubj=e1 {}=s >/conj.*/@enhanced=e2 {}=c");
    CoreNLPProtos.SemgrexResponse response = ProcessSemgrexRequest.processRequest(request);

    CoreNLPProtos.SemgrexResponse.Match match = onlyMatch(response, 0);
    Assert.assertEquals(2, match.getMatchIndex());
    Assert.assertFalse(match.hasMatchEmptyIndex());

    CoreNLPProtos.SemgrexResponse.NamedNode subject = namedNode(match, "s");
    Assert.assertEquals(1, subject.getMatchIndex());
    Assert.assertFalse(subject.hasEmptyIndex());

    CoreNLPProtos.SemgrexResponse.NamedNode conj = namedNode(match, "c");
    Assert.assertEquals(5, conj.getMatchIndex());
    Assert.assertEquals(1, conj.getEmptyIndex());

    CoreNLPProtos.SemgrexResponse.NamedEdge nsubj = namedEdge(match, "e1");
    Assert.assertEquals("nsubj", nsubj.getReln());
    Assert.assertEquals(CoreNLPProtos.SemgrexResponse.GraphName.BASIC, nsubj.getGraph());

    CoreNLPProtos.SemgrexResponse.NamedEdge conjEdge = namedEdge(match, "e2");
    Assert.assertEquals("conj:and", conjEdge.getReln());
    Assert.assertEquals(2, conjEdge.getSource());
    Assert.assertFalse(conjEdge.hasSourceEmpty());
    Assert.assertEquals(5, conjEdge.getTarget());
    Assert.assertEquals(1, conjEdge.getTargetEmpty());
    Assert.assertEquals(CoreNLPProtos.SemgrexResponse.GraphName.ENHANCED, conjEdge.getGraph());
  }

  /**
   * nsubj(likes, Sue) is in both graphs.  The edge is reported as
   * being in the graph the pattern searched, not the first graph
   * which happens to have an equal edge
   */
  @Test
  public void testEdgeInBothGraphs() {
    CoreNLPProtos.SemgrexRequest request = buildEnhancedRequest("{word:Sue} <nsubj@enhanced=e {}",
                                                                "{word:Sue} <nsubj=e {}");
    CoreNLPProtos.SemgrexResponse response = ProcessSemgrexRequest.processRequest(request);

    Assert.assertEquals(CoreNLPProtos.SemgrexResponse.GraphName.ENHANCED, namedEdge(onlyMatch(response, 0), "e").getGraph());
    Assert.assertEquals(CoreNLPProtos.SemgrexResponse.GraphName.BASIC, namedEdge(onlyMatch(response, 1), "e").getGraph());
  }

  /**
   * When one sentence of many is missing the enhanced graph, the error
   * says which one, by its position in the request and its words
   */
  @Test
  public void testMissingGraphNamesTheSentence() {
    CoreNLPProtos.SemgrexRequest.Builder request = buildEnhancedRequest("{} <@enhanced {}").toBuilder();
    CoreNLPProtos.SemgrexRequest.Dependencies.Builder queryBuilder = CoreNLPProtos.SemgrexRequest.Dependencies.newBuilder();
    addTokens(queryBuilder, new String[] {"1", "2", "3"}, new String[] {"Unban", "Mox", "Opal"});
    queryBuilder.setGraph(buildGraph(new String[] {"1", "2", "3"},
                                     new String[][] {{"2", "1", "amod"}, {"2", "3", "flat"}}));
    request.addQuery(queryBuilder.build());

    IllegalStateException e = Assert.assertThrows(IllegalStateException.class,
                                                  () -> ProcessSemgrexRequest.processRequest(request.build()));
    Assert.assertTrue(e.getMessage(), e.getMessage().contains("the sentence at index 1, |Unban Mox Opal|,"));
  }

  /**
   * A sentence which was sent with its sent_id is named by that instead
   */
  @Test
  public void testMissingGraphNamesTheSentId() {
    CoreNLPProtos.SemgrexRequest.Builder request = buildEnhancedRequest("{} <@enhanced {}").toBuilder();
    request.setQuery(0, request.getQuery(0).toBuilder().setSentenceID("sue-1").build());
    CoreNLPProtos.SemgrexRequest.Dependencies.Builder queryBuilder = CoreNLPProtos.SemgrexRequest.Dependencies.newBuilder();
    addTokens(queryBuilder, new String[] {"1", "2", "3"}, new String[] {"Unban", "Mox", "Opal"});
    queryBuilder.setGraph(buildGraph(new String[] {"1", "2", "3"},
                                     new String[][] {{"2", "1", "amod"}, {"2", "3", "flat"}}));
    queryBuilder.setSentenceID("mox-opal");
    request.addQuery(queryBuilder.build());

    IllegalStateException e = Assert.assertThrows(IllegalStateException.class,
                                                  () -> ProcessSemgrexRequest.processRequest(request.build()));
    Assert.assertTrue(e.getMessage(), e.getMessage().contains("the sentence with sent_id |mox-opal|"));
  }

  /**
   * A pattern which needs the enhanced graph fails loudly on a
   * request which only sent the basic graph
   */
  @Test
  public void testEnhancedPatternWithoutEnhancedGraph() {
    CoreNLPProtos.SemgrexRequest request = buildFakeRequest(1, 1, "{}=source >dobj@enhanced=foo {}=target");
    Assert.assertThrows(IllegalStateException.class, () -> ProcessSemgrexRequest.processRequest(request));
  }

  /**
   * With a sorted pattern, only sentences with matches come back, and
   * each only has the patterns which matched it, so the indices on the
   * containers are what say which is which.
   *<br>
   * The sentences come back in the order the matches were found,
   * the sorted pattern first
   */
  @Test
  public void testSortedIndices() {
    CoreNLPProtos.SemgrexRequest.Builder request = CoreNLPProtos.SemgrexRequest.newBuilder();
    request.addSemgrex("{word:Diamond}=x :: sort x");
    request.addSemgrex("{word:Opal}=y");
    String[][] sentences = {{"Unban", "Mox", "Opal"}, {"Unban", "Mox", "Diamond"}};
    for (String[] words : sentences) {
      CoreNLPProtos.SemgrexRequest.Dependencies.Builder queryBuilder = CoreNLPProtos.SemgrexRequest.Dependencies.newBuilder();
      addTokens(queryBuilder, new String[] {"1", "2", "3"}, words);
      queryBuilder.setGraph(buildGraph(new String[] {"1", "2", "3"},
                                       new String[][] {{"2", "1", "amod"}, {"2", "3", "flat"}}));
      request.addQuery(queryBuilder.build());
    }
    CoreNLPProtos.SemgrexResponse response = ProcessSemgrexRequest.processRequest(request.build());

    Assert.assertEquals(2, response.getSentenceCount());

    CoreNLPProtos.SemgrexResponse.SentenceResult first = response.getSentence(0);
    Assert.assertEquals(1, first.getSentenceIndex());
    Assert.assertEquals(1, first.getPatternCount());
    Assert.assertEquals(0, first.getPattern(0).getSemgrexIndex());
    Assert.assertEquals(3, first.getPattern(0).getMatch(0).getMatchIndex());

    CoreNLPProtos.SemgrexResponse.SentenceResult second = response.getSentence(1);
    Assert.assertEquals(0, second.getSentenceIndex());
    Assert.assertEquals(1, second.getPatternCount());
    Assert.assertEquals(1, second.getPattern(0).getSemgrexIndex());
    Assert.assertEquals(3, second.getPattern(0).getMatch(0).getMatchIndex());
  }

  /**
   * Every graph semgrex knows about has a proto GraphName of the same name
   */
  @Test
  public void testGraphNamesToProto() {
    for (SemgrexGraphName name : SemgrexGraphName.values()) {
      Assert.assertEquals(name.name(), ProcessSemgrexRequest.toProto(name).name());
    }
  }
}
