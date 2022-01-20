package edu.stanford.nlp.trees.tregex;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.pipeline.CoreNLPProtos;
import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;
import edu.stanford.nlp.trees.Tree;

public class ProcessTregexRequestTest {
  /**
   * Build a fake request using the given tregex and trees
   */
  public CoreNLPProtos.TregexRequest buildRequest(List<String> tregex, List<String> trees) {
    CoreNLPProtos.TregexRequest.Builder builder = CoreNLPProtos.TregexRequest.newBuilder();
    for (String query : tregex) {
      builder.addTregex(query);
    }

    for (String tree : trees) {
      Tree t = Tree.valueOf(tree);
      builder.addTrees(ProtobufAnnotationSerializer.toFlattenedTree(t));
    }

    return builder.build();
  }

  public CoreNLPProtos.TregexRequest buildRequest(String tregex, String ... trees) {
    return buildRequest(Collections.singletonList(tregex), Arrays.asList(trees));
  }

  /**
   * The results need to be quite nested since the query is tregex X trees
   */
  public void checkResults(CoreNLPProtos.TregexResponse response, int[][][] matchPositions,
                           String[][][][] nodeNames, int[][][][] nodePositions,
                           String[][][][] varNames, String[][][][] varValues) {
    Assert.assertEquals(matchPositions.length, response.getMatchesList().size());
    for (int i = 0; i < matchPositions.length; ++i) {
      Assert.assertEquals(matchPositions[i].length, response.getMatches(i).getMatchesList().size());
      for (int j = 0; j < matchPositions[i].length; ++j) {
        Assert.assertEquals(matchPositions[i][j].length, response.getMatches(i).getMatches(j).getMatchesList().size());
        for (int k = 0; k < matchPositions[i][j].length; ++k) {
          Assert.assertEquals(matchPositions[i][j][k], response.getMatches(i).getMatches(j).getMatches(k).getPosition());

          Assert.assertEquals(nodeNames[i][j][k].length, response.getMatches(i).getMatches(j).getMatches(k).getNodesList().size());
          Assert.assertEquals(nodePositions[i][j][k].length, response.getMatches(i).getMatches(j).getMatches(k).getNodesList().size());
          for (int m = 0; m < nodePositions[i][j][k].length; ++m) {
            Assert.assertEquals(nodeNames[i][j][k][m], response.getMatches(i).getMatches(j).getMatches(k).getNodes(m).getName());
            Assert.assertEquals(nodePositions[i][j][k][m], response.getMatches(i).getMatches(j).getMatches(k).getNodes(m).getPosition());
          }

          Assert.assertEquals(varNames[i][j][k].length, response.getMatches(i).getMatches(j).getMatches(k).getVariablesList().size());
          Assert.assertEquals(varValues[i][j][k].length, response.getMatches(i).getMatches(j).getMatches(k).getVariablesList().size());
          for (int m = 0; m < varNames[i][j][k].length; ++m) {
            Assert.assertEquals(varNames[i][j][k][m], response.getMatches(i).getMatches(j).getMatches(k).getVariables(m).getName());
            Assert.assertEquals(varValues[i][j][k][m], response.getMatches(i).getMatches(j).getMatches(k).getVariables(m).getValue());
          }
        }
      }
    }
  }

  /** Test a single Tregex on a single tree */
  @Test
  public void testTregex() {
    CoreNLPProtos.TregexRequest request = buildRequest("A=bar < B=foo", "(A (B w) (B x))");
    CoreNLPProtos.TregexResponse response = ProcessTregexRequest.processRequest(request);

    int[][][] matchPositions = {{{0, 0}}};
    // node names should be in alphabetical order
    String[][][][] nodeNames = {{{{"bar", "foo"}, {"bar", "foo"}}}};
    int[][][][] nodePositions = {{{{0, 1}, {0, 3}}}};
    String[][][][] varNames = {{{{}, {}}}};
    String[][][][] varValues = {{{{}, {}}}};
    checkResults(response, matchPositions, nodeNames, nodePositions, varNames, varValues);
  }

  /** Test a single Tregex on a single tree with variables */
  @Test
  public void testTregexVariables() {
    CoreNLPProtos.TregexRequest request = buildRequest("/(A)/#1%unban < /(B)/#1%mox=opal", "(A (B w) (B x))");
    CoreNLPProtos.TregexResponse response = ProcessTregexRequest.processRequest(request);

    int[][][] matchPositions = {{{0, 0}}};
    String[][][][] nodeNames = {{{{"opal"}, {"opal"}}}};
    int[][][][] nodePositions = {{{{1}, {3}}}};
    // varstrings will be alphabetical
    String[][][][] varNames = {{{{"mox", "unban"}, {"mox", "unban"}}}};
    String[][][][] varValues = {{{{"B", "A"}, {"B", "A"}}}};
    checkResults(response, matchPositions, nodeNames, nodePositions, varNames, varValues);
  }


  /** Test a single Tregex on two trees */
  @Test
  public void testTwoTrees() {
    CoreNLPProtos.TregexRequest request = buildRequest("A < B=foo", "(A (B w) (B x))", "(Z (A (B unban) (C mox) (D opal)))");
    CoreNLPProtos.TregexResponse response = ProcessTregexRequest.processRequest(request);

    int[][][] matchPositions = {{{0, 0}}, {{1}}};
    String[][][][] nodeNames = {{{{"foo"}, {"foo"}}}, {{{"foo"}}}};
    int[][][][] nodePositions = {{{{1}, {3}}}, {{{2}}}};
    String[][][][] varNames = {{{{}, {}}}, {{{}}}};
    String[][][][] varValues = {{{{}, {}}}, {{{}}}};
    checkResults(response, matchPositions, nodeNames, nodePositions, varNames, varValues);
  }  

  /** Test two Tregex on one tree */
  @Test
  public void testTwoTregex() {
    String[] tregex = {"A < B=foo", "B < w=foo"};
    String[] trees = {"(A (B w) (B x))"};
    CoreNLPProtos.TregexRequest request = buildRequest(Arrays.asList(tregex), Arrays.asList(trees));
    CoreNLPProtos.TregexResponse response = ProcessTregexRequest.processRequest(request);

    int[][][] matchPositions = {{{0, 0}, {1}}};
    String[][][][] nodeNames = {{{{"foo"}, {"foo"}}, {{"foo"}}}};
    int[][][][] nodePositions = {{{{1}, {3}}, {{2}}}};
    String[][][][] varNames = {{{{}, {}}, {{}}}};
    String[][][][] varValues = {{{{}, {}}, {{}}}};
    checkResults(response, matchPositions, nodeNames, nodePositions, varNames, varValues);
  }  
}
