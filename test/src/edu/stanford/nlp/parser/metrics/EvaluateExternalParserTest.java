package edu.stanford.nlp.parser.metrics;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.pipeline.CoreNLPProtos;
import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.ScoredObject;


public class EvaluateExternalParserTest {
  /**
   * Build a single parse result out of the given data.
   */
  public static CoreNLPProtos.EvaluateParserRequest.ParseResult buildFakeParseResult(String gold, String ... results) {
    CoreNLPProtos.EvaluateParserRequest.ParseResult.Builder result = CoreNLPProtos.EvaluateParserRequest.ParseResult.newBuilder();
    Tree tree = Tree.valueOf(gold);
    result.setGold(ProtobufAnnotationSerializer.toFlattenedTree(tree));
    if (results.length == 0) {
      tree.setScore(2.0);
      result.addPredicted(ProtobufAnnotationSerializer.toFlattenedTree(tree));
    } else {
      for (int i = 0; i < results.length; ++i) {
        tree = Tree.valueOf(results[i]);
        tree.setScore(2.0 / (i + 1));
        result.addPredicted(ProtobufAnnotationSerializer.toFlattenedTree(tree));
      }
    }
    return result.build();
  }

  public static CoreNLPProtos.EvaluateParserRequest buildFakeRequest(CoreNLPProtos.EvaluateParserRequest.ParseResult ... results) {
    CoreNLPProtos.EvaluateParserRequest.Builder request = CoreNLPProtos.EvaluateParserRequest.newBuilder();
    for (CoreNLPProtos.EvaluateParserRequest.ParseResult parse : results) {
      request.addTreebank(parse);
    }
    return request.build();
  }  

  public byte[] requestBytes(CoreNLPProtos.EvaluateParserRequest ... requests) throws IOException {
    return requestBytes(Arrays.asList(requests));
  }

  public byte[] requestBytes(List<CoreNLPProtos.EvaluateParserRequest> requests) throws IOException {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    DataOutputStream dout = new DataOutputStream(bout);

    for (CoreNLPProtos.EvaluateParserRequest request : requests) {
      ByteArrayOutputStream singleBout = new ByteArrayOutputStream();
      request.writeTo(singleBout);
      byte[] singleBytes = singleBout.toByteArray();

      dout.writeInt(singleBytes.length);
      dout.write(singleBytes, 0, singleBytes.length);
    }

    dout.writeInt(0);
    dout.close();

    return bout.toByteArray();
  }

  @Test
  public void testBuildRequest() {
    String t1 = "((VP (VB Unban) (NP (NNP Mox) (NNP Opal))))";
    CoreNLPProtos.EvaluateParserRequest o1 = buildFakeRequest(buildFakeParseResult(t1));
    String t2 = "(ROOT (S (NP (PRP$ My) (NN dog)) (ADVP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))";
    String t2b = "(ROOT (S (NP (PRP$ My) (NN dog)) (VP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))";
    CoreNLPProtos.EvaluateParserRequest o2 = buildFakeRequest(buildFakeParseResult(t2, t2b, t2));

    Assert.assertEquals("Expected 1 item in o1.treebank", 1, o1.getTreebankList().size());
    Assert.assertEquals("Expected 1 item in o1.treebank(0).predicted", 1, o1.getTreebank(0).getPredictedList().size());
    Assert.assertEquals("Expected 1 item in o2.treebank", 1, o2.getTreebankList().size());
    Assert.assertEquals("Expected 2 items in o2.treebank(0).predicted", 2, o2.getTreebank(0).getPredictedList().size());
    Assert.assertEquals("Expected the same tree", Tree.valueOf(t2b), ProtobufAnnotationSerializer.fromProto(o2.getTreebank(0).getPredictedList().get(0)));
    Assert.assertEquals("Expected the same tree", Tree.valueOf(t2), ProtobufAnnotationSerializer.fromProto(o2.getTreebank(0).getPredictedList().get(1)));
  }

  public void verifyResults(byte[] arr, double ... results) throws IOException {
    ByteArrayInputStream bin = new ByteArrayInputStream(arr);
    DataInputStream din = new DataInputStream(bin);
    for (int i = 0; i < results.length; ++i) {
      int len = din.readInt();
      Assert.assertNotEquals("Expected more repeated results", 0, len);
      byte[] responseBytes = new byte[len];
      din.read(responseBytes, 0, len);
      CoreNLPProtos.EvaluateParserResponse response = CoreNLPProtos.EvaluateParserResponse.parseFrom(responseBytes);
      double f1 = response.getF1();
      Assert.assertEquals("Expected the f1 to be roughly " + results[i], results[i], f1, 0.00001);
    }
    int len = din.readInt();
    Assert.assertEquals("Repeated results should be over", 0, len);
  }

  @Test
  public void testSingleMultiRequest() throws IOException {
    CoreNLPProtos.EvaluateParserRequest gold = buildFakeRequest(buildFakeParseResult("((VP (VB Unban) (NP (NNP Mox) (NNP Opal))))"));
    byte[] bytes = requestBytes(gold);
    ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    EvaluateExternalParser evaluator = new EvaluateExternalParser();
    evaluator.processMultipleInputs(bin, bout);
    verifyResults(bout.toByteArray(), 1.0);
  }

  @Test
  public void testMultiRequest() throws IOException {
    CoreNLPProtos.EvaluateParserRequest o1 = buildFakeRequest(buildFakeParseResult("((VP (VB Unban) (NP (NNP Mox) (NNP Opal))))"));
    String t2 = "(ROOT (S (NP (PRP$ My) (NN dog)) (ADVP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))";
    String t2b = "(ROOT (S (NP (PRP$ My) (NN dog)) (VP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))";
    CoreNLPProtos.EvaluateParserRequest o2 = buildFakeRequest(buildFakeParseResult(t2, t2b, t2));

    byte[] bytes = requestBytes(o1, o2);
    ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    EvaluateExternalParser evaluator = new EvaluateExternalParser();
    evaluator.processMultipleInputs(bin, bout);
    verifyResults(bout.toByteArray(), 1.0, 0.8571428);
    // TODO: check that there are 2 results with scores of 1.0 and something
  }

  @Test
  public void testGoldTrees() {
    String t1 = "((VP (VB Unban) (NP (NNP Mox) (NNP Opal))))";
    String t2 = "(ROOT (S (NP (PRP$ My) (NN dog)) (ADVP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))";
    String t2b = "(ROOT (S (NP (PRP$ My) (NN dog)) (VP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))";
    CoreNLPProtos.EvaluateParserRequest request = buildFakeRequest(buildFakeParseResult(t1),
                                                                   buildFakeParseResult(t2, t2b, t2));

    List<Tree> trees = EvaluateExternalParser.getGoldTrees(request);
    Assert.assertEquals("Expected exactly 2 trees", 2, trees.size());
    Assert.assertEquals("Expected the same tree back", Tree.valueOf(t1), trees.get(0));
    Assert.assertEquals("Expected the same tree back", Tree.valueOf(t2), trees.get(1));
  }

  @Test
  public void testGetResults() {
    String t1 = "((VP (VB Unban) (NP (NNP Mox) (NNP Opal))))";
    String t2 = "(ROOT (S (NP (PRP$ My) (NN dog)) (ADVP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))";
    String t2b = "(ROOT (S (NP (PRP$ My) (NN dog)) (VP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))";
    CoreNLPProtos.EvaluateParserRequest request = buildFakeRequest(buildFakeParseResult(t1),
                                                                   buildFakeParseResult(t2, t2b, t2));

    List<List<Tree>> predicted = EvaluateExternalParser.getResults(request);

    Assert.assertEquals("Expected exactly 2 lists", 2, predicted.size());
    Assert.assertEquals("Expected exactly 1 tree in the first list", 1, predicted.get(0).size());
    Assert.assertEquals("Expected the same tree back", Tree.valueOf(t1), predicted.get(0).get(0));
    Assert.assertEquals("Expected exactly 2 trees in the seconds list", 2, predicted.get(1).size());
    Assert.assertEquals("Expected the same tree back", Tree.valueOf(t2b), predicted.get(1).get(0));
    Assert.assertEquals("Expected the same tree back", Tree.valueOf(t2), predicted.get(1).get(1));
  }

  @Test
  public void testScoreDataset() {
    String t1 = "((VP (VB Unban) (NP (NNP Mox) (NNP Opal))))";
    String t2 = "(ROOT (S (NP (PRP$ My) (NN dog)) (ADVP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))";
    String t2b = "(ROOT (S (NP (PRP$ My) (NN dog)) (VP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))";
    CoreNLPProtos.EvaluateParserRequest request = buildFakeRequest(buildFakeParseResult(t1),
                                                                   buildFakeParseResult(t2, t2b, t2));

    EvaluateExternalParser evaluator = new EvaluateExternalParser();
    List<Tree> gold = evaluator.getGoldTrees(request);
    List<List<Tree>> predicted = evaluator.getResults(request);
    CoreNLPProtos.EvaluateParserResponse response = evaluator.scoreDataset(gold, predicted);
    double f1 = response.getF1();
    Assert.assertEquals("Expected the f1 to be roughly 88.888", 0.88888888, f1, 0.00001);
  }

  @Test
  public void testScoreKBest() {
    String t1 = "((VP (VB Unban) (NP (NNP Mox) (NNP Opal))))";
    String t2 = "(ROOT (S (NP (PRP$ My) (NN dog)) (ADVP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))";
    String t2b = "(ROOT (S (NP (PRP$ My) (NN dog)) (VP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))";
    CoreNLPProtos.EvaluateParserRequest request = buildFakeRequest(buildFakeParseResult(t1),
                                                                   buildFakeParseResult(t2, t2b, t2));
    EvaluateExternalParser evaluator = new EvaluateExternalParser("-evalPCFGkBest", "10", "-evals", "pcfgTopK");
    List<Tree> gold = evaluator.getGoldTrees(request);
    List<List<Tree>> predicted = evaluator.getResults(request);
    CoreNLPProtos.EvaluateParserResponse response = evaluator.scoreDataset(gold, predicted);
    Assert.assertEquals("Expected the k=2 kbest f1 to be 1.0", 1.0, response.getKbestF1(), 0.00001);

    evaluator = new EvaluateExternalParser("-evalPCFGkBest", "1", "-evals", "pcfgTopK");
    response = evaluator.scoreDataset(gold, predicted);
    Assert.assertEquals("Expected the f1 to be roughly 88.888", 0.88888888, response.getF1(), 0.00001);
    Assert.assertEquals("Expected the k=1 kbest f1 to be roughly 88.888", 0.88888888, response.getKbestF1(), 0.00001);
  }
}
