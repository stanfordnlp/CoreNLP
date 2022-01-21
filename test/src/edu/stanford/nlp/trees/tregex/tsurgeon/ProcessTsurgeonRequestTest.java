package edu.stanford.nlp.trees.tregex.tsurgeon;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.pipeline.CoreNLPProtos;
import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;
import edu.stanford.nlp.trees.Tree;

public class ProcessTsurgeonRequestTest {
  /**
   * Build a fake request using the given tregex, tsurgeons, and trees
   */
  public CoreNLPProtos.TsurgeonRequest buildRequest(String tregex, List<String> tsurgeons, List<String> trees) {
    CoreNLPProtos.TsurgeonRequest.Builder builder = CoreNLPProtos.TsurgeonRequest.newBuilder();

    CoreNLPProtos.TsurgeonRequest.Operation.Builder opBuilder = CoreNLPProtos.TsurgeonRequest.Operation.newBuilder();
    opBuilder.setTregex(tregex);
    for (String ts : tsurgeons) {
      opBuilder.addTsurgeon(ts);
    }
    builder.addOperations(opBuilder.build());

    for (String tree : trees) {
      Tree t = Tree.valueOf(tree);
      builder.addTrees(ProtobufAnnotationSerializer.toFlattenedTree(t));
    }

    return builder.build();
  }

  public CoreNLPProtos.TsurgeonRequest buildRequest(String tregex, String tsurgeon, String ... trees) {
    return buildRequest(tregex, Collections.singletonList(tsurgeon), Arrays.asList(trees));
  }

  public void checkResults(CoreNLPProtos.TsurgeonResponse response, String ... expectedResults) {
    String reply = (expectedResults.length == 1) ? "reply" : "replies";
    Assert.assertEquals("Expected exactly " + expectedResults.length + " " + reply, expectedResults.length, response.getTreesList().size());
    for (int i = 0; i < expectedResults.length; ++i) {
      Tree result = ProtobufAnnotationSerializer.fromProto(response.getTreesList().get(i));
      Assert.assertEquals(expectedResults[i], result.toString());
    }
  }
  
  /** Test a single Tsurgeon on a single tree */
  @Test
  public void testTsurgeon() {
    CoreNLPProtos.TsurgeonRequest request = buildRequest("__ <1 B=n <2 ~n", "relabel n X", "(A (B w) (B x))");
    CoreNLPProtos.TsurgeonResponse response = ProcessTsurgeonRequest.processRequest(request);

    checkResults(response, "(A (X w) (B x))");
  }

  /** Test a Tsurgeon on two trees */
  @Test
  public void testTwoTrees() {
    CoreNLPProtos.TsurgeonRequest request = buildRequest("__ < B=n <2 (B=m !== =n)", "relabel n X", "(A (B w) (B x))", "(A (B w) (B x) (B y))");
    CoreNLPProtos.TsurgeonResponse response = ProcessTsurgeonRequest.processRequest(request);

    checkResults(response, "(A (X w) (B x))", "(A (X w) (B x) (X y))");
  }

  /** Test a double Tsurgeon on one tree */
  @Test
  public void testTwoTsurgeons() {
    String[] tsurgeons = { "adjoinF (D (E=target foot@)) bar", "insert (G 1) $+ target" };
    String tregex = "B=bar !>> D";
    CoreNLPProtos.TsurgeonRequest request = buildRequest(tregex, Arrays.asList(tsurgeons), Collections.singletonList("(A (B C))"));
    CoreNLPProtos.TsurgeonResponse response = ProcessTsurgeonRequest.processRequest(request);
    checkResults(response, "(A (D (G 1) (E (B C))))");
  }
}
